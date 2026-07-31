package com.openwheelracing.content.block.entity;

import com.openwheelracing.content.block.CarAssemblyWorkstationBlock;
import com.openwheelracing.content.item.PrototypeCarItem;
import com.openwheelracing.content.item.TyreItem;
import com.openwheelracing.content.menu.CarAssemblyMenu;
import com.openwheelracing.registry.OWRDataComponents;
import com.openwheelracing.content.recipe.CarAssemblyRecipe;
import com.openwheelracing.registry.OWRBlockEntities;
import com.openwheelracing.content.car.CarLivery;
import com.openwheelracing.content.car.CarLiveryColors;
import com.openwheelracing.content.car.CarLiveryTexture;
import com.openwheelracing.content.car.PrototypeCarSetup;
import com.openwheelracing.registry.OWRItems;
import com.openwheelracing.registry.OWRRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public class CarAssemblyWorkstationBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int SLOT_CHASSIS = 0;
    public static final int SLOT_ENGINE = 1;
    public static final int SLOT_TIRES = 2;
    public static final int SLOT_AERO_KIT = 3;
    public static final int SLOT_GEARBOX = 4;
    public static final int SLOT_STEERING_CONTROLS = 5;
    public static final int SLOT_OUTPUT = 6;
    public static final int SLOT_COUNT = 7;
    private static final int MAX_PROGRESS = 100;
    private static final int OPERATION_NONE = 0;
    private static final int OPERATION_SETUP = 1;
    private static final int OPERATION_REPAIR = 2;
    private static final int OPERATION_LIVERY = 3;
    private static final int LIVERY_ACTION_PRESET = 0;
    private static final int LIVERY_ACTION_COLOR = 1;
    private static final int LIVERY_ACTION_TEXTURE = 2;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final CarWorkstationType workstationType;
    private int progress;
    private int pendingOperation;
    private int pendingAction;
    private int pendingValue;
    private int pendingExtra;
    private String pendingText = "";

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> getAssemblyTime();
                case 2 -> workstationType.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                progress = value;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public CarAssemblyWorkstationBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, CarWorkstationType.LEGACY);
    }

    public CarAssemblyWorkstationBlockEntity(BlockPos pos, BlockState state, CarWorkstationType workstationType) {
        super(OWRBlockEntities.typeFor(workstationType).get(), pos, state);
        this.workstationType = workstationType;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CarAssemblyWorkstationBlockEntity workstation) {
        boolean running = false;
        if (workstation.pendingOperation != OPERATION_NONE) {
            if (!workstation.canRunPendingOperation()) {
                workstation.clearPendingOperation();
            } else {
                running = true;
                workstation.progress++;
                if (workstation.progress >= workstation.getAssemblyTime()) {
                    workstation.applyPendingOperation();
                    workstation.clearPendingOperation();
                    running = false;
                }
            }
            workstation.setWorkingState(level, pos, state, running);
            workstation.setChanged();
            return;
        }
        if (!workstation.workstationType.allowsConstruction()) {
            workstation.progress = 0;
            workstation.setWorkingState(level, pos, state, false);
            return;
        }
        CarAssemblyRecipe recipe = workstation.getRecipe(level);
        if (workstation.canAssemble(recipe)) {
            running = true;
            workstation.progress++;
            if (workstation.progress >= workstation.getAssemblyTime()) {
                workstation.assembleCar(recipe);
                workstation.progress = 0;
                running = false;
            }
        } else {
            workstation.progress = 0;
        }
        workstation.setWorkingState(level, pos, state, running);
        workstation.setChanged();
    }

    public CarWorkstationType getWorkstationType() {
        return workstationType;
    }

    public ContainerData getData() {
        return data;
    }

    public boolean queueSetupTune(int slot, int delta) {
        if (!workstationType.allowsSetup() || pendingOperation != OPERATION_NONE || !hasCarOutput() || slot < 0 || slot > 3 || delta == 0) {
            return false;
        }
        pendingOperation = OPERATION_SETUP;
        pendingAction = slot;
        pendingValue = delta;
        progress = 0;
        setChanged();
        return true;
    }

    public boolean queueRepair() {
        ItemStack stack = getItem(SLOT_OUTPUT);
        if (!workstationType.allowsSetup() || pendingOperation != OPERATION_NONE || !hasCarOutput() || PrototypeCarItem.getCarDamage(stack) <= 0) {
            return false;
        }
        pendingOperation = OPERATION_REPAIR;
        progress = 0;
        setChanged();
        return true;
    }

    public boolean queueLiveryPreset(int delta) {
        if (!workstationType.allowsLivery() || pendingOperation != OPERATION_NONE || !hasCarOutput() || delta == 0) {
            return false;
        }
        pendingOperation = OPERATION_LIVERY;
        pendingAction = LIVERY_ACTION_PRESET;
        pendingValue = delta;
        progress = 0;
        setChanged();
        return true;
    }

    public boolean queueLiveryColor(int channel, int color) {
        if (!workstationType.allowsLivery() || pendingOperation != OPERATION_NONE || !hasCarOutput() || channel < 0 || channel > 2) {
            return false;
        }
        pendingOperation = OPERATION_LIVERY;
        pendingAction = LIVERY_ACTION_COLOR;
        pendingValue = channel;
        pendingExtra = color;
        progress = 0;
        setChanged();
        return true;
    }

    public boolean queueLiveryTexture(String textureId) {
        if (!workstationType.allowsLivery() || pendingOperation != OPERATION_NONE || !hasCarOutput()) {
            return false;
        }
        pendingOperation = OPERATION_LIVERY;
        pendingAction = LIVERY_ACTION_TEXTURE;
        pendingText = CarLiveryTexture.sanitize(textureId);
        progress = 0;
        setChanged();
        return true;
    }

    public boolean isValidForSlot(int slot, ItemStack stack) {
        if (slot == SLOT_OUTPUT) {
            return workstationType != CarWorkstationType.CONSTRUCTION && stack.is(OWRItems.PROTOTYPE_CAR_SPAWN.get()) && stack.getCount() == 1;
        }
        return workstationType.allowsConstruction() && stack.is(requiredItemForSlot(slot));
    }

    private boolean canAssemble(CarAssemblyRecipe recipe) {
        if (recipe == null) {
            return false;
        }

        ItemStack output = getItem(SLOT_OUTPUT);
        ItemStack result = recipe.result();
        return output.isEmpty();
    }

    private void assembleCar(CarAssemblyRecipe recipe) {
        ItemStack tyreStack = getItem(SLOT_TIRES).copy();
        for (int slot = SLOT_CHASSIS; slot <= SLOT_STEERING_CONTROLS; slot++) {
            removeItem(slot, 1);
        }

        ItemStack result = recipe.result().copy();
        if (result.is(OWRItems.PROTOTYPE_CAR_SPAWN.get())) {
            PrototypeCarSetup setup = result.getOrDefault(OWRDataComponents.CAR_SETUP.get(), PrototypeCarSetup.DEFAULT);
            int compound = TyreItem.getCompound(tyreStack);
            int remainingPercent = TyreItem.getRemainingPercent(tyreStack);
            result.set(OWRDataComponents.CAR_SETUP.get(), new PrototypeCarSetup(setup.power(), compound, setup.aero(), setup.gearing()));
            result.set(OWRDataComponents.TYRE_WEAR.get(), 100 - remainingPercent);
            int livery = PrototypeCarItem.getLivery(result);
            CarLiveryColors colors = PrototypeCarItem.getLiveryColors(result);
            if (result.get(OWRDataComponents.CAR_LIVERY.get()) == null) {
                result.set(OWRDataComponents.CAR_LIVERY.get(), livery);
                colors = CarLiveryColors.fromPreset(CarLivery.fromIndex(livery));
            }
            result.set(OWRDataComponents.CAR_LIVERY_COLORS.get(), colors);
            PrototypeCarItem.applyLiveryItemDisplay(result, colors);
        }

        ItemStack output = getItem(SLOT_OUTPUT);
        if (output.isEmpty()) {
            setItem(SLOT_OUTPUT, result);
        } else {
            output.grow(result.getCount());
        }
    }

    private boolean hasCarOutput() {
        return getItem(SLOT_OUTPUT).is(OWRItems.PROTOTYPE_CAR_SPAWN.get());
    }

    private boolean canRunPendingOperation() {
        if (!hasCarOutput()) {
            return false;
        }
        return switch (pendingOperation) {
            case OPERATION_SETUP -> workstationType.allowsSetup();
            case OPERATION_REPAIR -> workstationType.allowsSetup() && PrototypeCarItem.getCarDamage(getItem(SLOT_OUTPUT)) > 0;
            case OPERATION_LIVERY -> workstationType.allowsLivery();
            default -> false;
        };
    }

    private void applyPendingOperation() {
        ItemStack stack = getItem(SLOT_OUTPUT);
        switch (pendingOperation) {
            case OPERATION_SETUP -> applySetupTune(stack);
            case OPERATION_REPAIR -> stack.set(OWRDataComponents.CAR_DAMAGE.get(), Math.max(0, PrototypeCarItem.getCarDamage(stack) - 25));
            case OPERATION_LIVERY -> applyLiveryOperation(stack);
            default -> {
            }
        }
    }

    private void applySetupTune(ItemStack stack) {
        PrototypeCarSetup setup = PrototypeCarItem.getSetup(stack);
        PrototypeCarSetup updated = switch (pendingAction) {
            case 0 -> new PrototypeCarSetup(setup.power() + pendingValue, setup.grip(), setup.aero(), setup.gearing());
            case 1 -> new PrototypeCarSetup(setup.power(), setup.grip() + pendingValue, setup.aero(), setup.gearing());
            case 2 -> new PrototypeCarSetup(setup.power(), setup.grip(), setup.aero() + pendingValue, setup.gearing());
            case 3 -> new PrototypeCarSetup(setup.power(), setup.grip(), setup.aero(), setup.gearing() + pendingValue);
            default -> setup;
        };
        stack.set(OWRDataComponents.CAR_SETUP.get(), updated);
    }

    private void applyLiveryOperation(ItemStack stack) {
        switch (pendingAction) {
            case LIVERY_ACTION_PRESET -> {
                int livery = CarLivery.wrapIndex(PrototypeCarItem.getLivery(stack) + pendingValue);
                CarLiveryColors colors = CarLiveryColors.fromPreset(CarLivery.fromIndex(livery));
                stack.set(OWRDataComponents.CAR_LIVERY.get(), livery);
                PrototypeCarItem.setLiveryColors(stack, colors);
            }
            case LIVERY_ACTION_COLOR -> PrototypeCarItem.setLiveryColors(stack, PrototypeCarItem.getLiveryColors(stack).withChannel(pendingValue, pendingExtra));
            case LIVERY_ACTION_TEXTURE -> PrototypeCarItem.setLiveryTexture(stack, new CarLiveryTexture(pendingText));
            default -> {
            }
        }
    }

    private void clearPendingOperation() {
        pendingOperation = OPERATION_NONE;
        pendingAction = 0;
        pendingValue = 0;
        pendingExtra = 0;
        pendingText = "";
        progress = 0;
    }

    private void setWorkingState(Level level, BlockPos pos, BlockState state, boolean running) {
        if (state.hasProperty(CarAssemblyWorkstationBlock.LIT) && state.getValue(CarAssemblyWorkstationBlock.LIT) != running) {
            level.setBlock(pos, state.setValue(CarAssemblyWorkstationBlock.LIT, running), 3);
        }
    }

    private int getAssemblyTime() {
        if (pendingOperation != OPERATION_NONE) {
            return pendingOperation == OPERATION_SETUP && workstationType.allowsSetup() || pendingOperation == OPERATION_REPAIR && workstationType.allowsSetup() || pendingOperation == OPERATION_LIVERY && workstationType.allowsLivery() ? workstationType.progressTicks() : MAX_PROGRESS;
        }
        return workstationType.allowsConstruction() ? workstationType.progressTicks() : MAX_PROGRESS;
    }

    private @Nullable CarAssemblyRecipe getRecipe(Level level) {
        CarAssemblyRecipe.Input input = new CarAssemblyRecipe.Input(
            getItem(SLOT_CHASSIS),
            getItem(SLOT_ENGINE),
            getItem(SLOT_TIRES),
            getItem(SLOT_AERO_KIT),
            getItem(SLOT_GEARBOX),
            getItem(SLOT_STEERING_CONTROLS)
        );
        return level.getServer() == null ? null : level.getServer().getRecipeManager().getRecipeFor(OWRRecipes.CAR_ASSEMBLY_TYPE.get(), input, level).map(holder -> holder.value()).orElse(null);
    }

    private Item requiredItemForSlot(int slot) {
        return switch (slot) {
            case SLOT_CHASSIS -> OWRItems.CHASSIS.get();
            case SLOT_ENGINE -> OWRItems.ENGINE.get();
            case SLOT_TIRES -> OWRItems.TIRES.get();
            case SLOT_AERO_KIT -> OWRItems.AERO_KIT.get();
            case SLOT_GEARBOX -> OWRItems.GEARBOX.get();
            case SLOT_STEERING_CONTROLS -> OWRItems.STEERING_CONTROLS.get();
            default -> OWRItems.PROTOTYPE_CAR_SPAWN.get();
        };
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = ContainerHelper.removeItem(items, slot, amount);
        if (!stack.isEmpty()) {
            setChanged();
        }
        return stack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(workstationType.containerKey());
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CarAssemblyMenu(containerId, playerInventory, this, data);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Progress", progress);
        output.putInt("PendingOperation", pendingOperation);
        output.putInt("PendingAction", pendingAction);
        output.putInt("PendingValue", pendingValue);
        output.putInt("PendingExtra", pendingExtra);
        output.putString("PendingText", pendingText);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        progress = input.getIntOr("Progress", 0);
        pendingOperation = input.getIntOr("PendingOperation", OPERATION_NONE);
        pendingAction = input.getIntOr("PendingAction", 0);
        pendingValue = input.getIntOr("PendingValue", 0);
        pendingExtra = input.getIntOr("PendingExtra", 0);
        pendingText = input.getStringOr("PendingText", "");
    }
}
