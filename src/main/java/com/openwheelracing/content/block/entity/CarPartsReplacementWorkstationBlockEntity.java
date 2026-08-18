package com.openwheelracing.content.block.entity;

import com.openwheelracing.content.block.CarPartsReplacementWorkstationBlock;
import com.openwheelracing.content.car.CarComponentDamage;
import com.openwheelracing.content.item.PrototypeCarItem;
import com.openwheelracing.content.item.TyreItem;
import com.openwheelracing.content.menu.CarPartsReplacementMenu;
import com.openwheelracing.registry.OWRBlockEntities;
import com.openwheelracing.registry.OWRItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class CarPartsReplacementWorkstationBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int SLOT_CAR = 0;
    public static final int SLOT_NEW_PART = 1;
    public static final int SLOT_REMOVED_PART = 2;
    public static final int SLOT_COUNT = 3;
    private static final int REPLACEMENT_TICKS = 100;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int progress;
    private boolean replacing;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> REPLACEMENT_TICKS;
                case 2 -> replacing ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                progress = value;
            } else if (index == 2) {
                replacing = value != 0;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public CarPartsReplacementWorkstationBlockEntity(BlockPos pos, BlockState state) {
        super(OWRBlockEntities.CAR_PARTS_REPLACEMENT_STATION.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CarPartsReplacementWorkstationBlockEntity station) {
        if (!station.replacing) {
            station.setWorkingState(level, pos, state, false);
            return;
        }
        if (!station.canReplace()) {
            station.cancelReplacement();
            station.setWorkingState(level, pos, state, false);
            return;
        }
        station.progress++;
        station.setWorkingState(level, pos, state, true);
        if (station.progress >= REPLACEMENT_TICKS) {
            station.finishReplacement();
            station.setWorkingState(level, pos, state, false);
        }
        station.setChanged();
    }

    public boolean queueReplacement() {
        if (replacing || !canReplace()) {
            return false;
        }
        replacing = true;
        progress = 0;
        setChanged();
        return true;
    }

    public boolean isReplacing() {
        return replacing;
    }

    public boolean canReplace() {
        ItemStack car = getItem(SLOT_CAR);
        ItemStack part = getItem(SLOT_NEW_PART);
        return car.is(OWRItems.PROTOTYPE_CAR_SPAWN.get()) && car.getCount() == 1
            && replacementKind(part) != ReplacementKind.NONE && getItem(SLOT_REMOVED_PART).isEmpty();
    }

    public boolean isReplacementPart(ItemStack stack) {
        return replacementKind(stack) != ReplacementKind.NONE;
    }

    private void finishReplacement() {
        if (!canReplace()) {
            cancelReplacement();
            return;
        }
        ItemStack car = getItem(SLOT_CAR);
        ItemStack newPart = getItem(SLOT_NEW_PART);
        ReplacementKind kind = replacementKind(newPart);
        CarComponentDamage damage = PrototypeCarItem.getComponentDamage(car);
        ItemStack removed = removedPart(car, kind);
        PrototypeCarItem.setComponentDamage(car, replacedDamage(damage, kind));
        if (kind == ReplacementKind.TIRES) {
            int compound = TyreItem.getCompound(newPart);
            int remaining = TyreItem.getRemainingPercent(newPart);
            car.set(com.openwheelracing.registry.OWRDataComponents.CAR_SETUP.get(),
                new com.openwheelracing.content.car.PrototypeCarSetup(PrototypeCarItem.getSetup(car).power(), compound, PrototypeCarItem.getSetup(car).aero(), PrototypeCarItem.getSetup(car).gearing()));
            car.set(com.openwheelracing.registry.OWRDataComponents.TYRE_WEAR.get(), 100 - remaining);
            car.set(com.openwheelracing.registry.OWRDataComponents.TYRE_TYPE.get(), TyreItem.getType(newPart).id());
        }
        newPart.shrink(1);
        setItem(SLOT_REMOVED_PART, removed);
        replacing = false;
        progress = 0;
        setChanged();
    }

    private ItemStack removedPart(ItemStack car, ReplacementKind kind) {
        return switch (kind) {
            case CHASSIS -> new ItemStack(OWRItems.CHASSIS.get());
            case ENGINE -> new ItemStack(OWRItems.ENGINE.get());
            case FRONT_WING -> new ItemStack(OWRItems.FRONT_WING.get());
            case REAR_WING -> new ItemStack(OWRItems.REAR_WING.get());
            case TIRES -> TyreItem.create(PrototypeCarItem.getSetup(car).grip(), PrototypeCarItem.getTyreType(car), 1, Math.max(0, 100 - PrototypeCarItem.getTyreWear(car)));
            case GEARBOX -> new ItemStack(OWRItems.GEARBOX.get());
            case STEERING_CONTROLS -> new ItemStack(OWRItems.STEERING_CONTROLS.get());
            case NONE -> ItemStack.EMPTY;
        };
    }

    private static CarComponentDamage replacedDamage(CarComponentDamage damage, ReplacementKind kind) {
        return new CarComponentDamage(
            kind == ReplacementKind.FRONT_WING ? 0 : damage.frontEnd(),
            kind == ReplacementKind.REAR_WING ? 0 : damage.rearEnd(),
            kind == ReplacementKind.CHASSIS ? 0 : damage.chassis(),
            kind == ReplacementKind.ENGINE ? 0 : damage.engine(),
            kind == ReplacementKind.TIRES ? 0 : damage.frontLeftWheel(),
            kind == ReplacementKind.TIRES ? 0 : damage.frontRightWheel(),
            kind == ReplacementKind.TIRES ? 0 : damage.rearLeftWheel(),
            kind == ReplacementKind.TIRES ? 0 : damage.rearRightWheel()
        );
    }

    private static ReplacementKind replacementKind(ItemStack stack) {
        if (stack.is(OWRItems.CHASSIS.get())) return ReplacementKind.CHASSIS;
        if (stack.is(OWRItems.ENGINE.get())) return ReplacementKind.ENGINE;
        if (stack.is(OWRItems.FRONT_WING.get())) return ReplacementKind.FRONT_WING;
        if (stack.is(OWRItems.REAR_WING.get())) return ReplacementKind.REAR_WING;
        if (stack.is(OWRItems.TIRES.get())) return ReplacementKind.TIRES;
        if (stack.is(OWRItems.GEARBOX.get())) return ReplacementKind.GEARBOX;
        if (stack.is(OWRItems.STEERING_CONTROLS.get())) return ReplacementKind.STEERING_CONTROLS;
        return ReplacementKind.NONE;
    }

    private void cancelReplacement() {
        replacing = false;
        progress = 0;
        setChanged();
    }

    private void setWorkingState(Level level, BlockPos pos, BlockState state, boolean running) {
        if (state.hasProperty(CarPartsReplacementWorkstationBlock.LIT) && state.getValue(CarPartsReplacementWorkstationBlock.LIT) != running) {
            level.setBlock(pos, state.setValue(CarPartsReplacementWorkstationBlock.LIT, running), 3);
        }
    }

    public ContainerData getData() {
        return data;
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = ContainerHelper.removeItem(items, slot, amount);
        if (!stack.isEmpty()) setChanged();
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
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (replacing || slot == SLOT_REMOVED_PART) return false;
        if (slot == SLOT_CAR) return stack.is(OWRItems.PROTOTYPE_CAR_SPAWN.get()) && stack.getCount() == 1;
        return slot == SLOT_NEW_PART && isReplacementPart(stack);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.openwheelracing.car_parts_replacement_station");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CarPartsReplacementMenu(containerId, inventory, this, data);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Progress", progress);
        output.putBoolean("Replacing", replacing);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        progress = input.getIntOr("Progress", 0);
        replacing = input.getBooleanOr("Replacing", false);
    }

    private enum ReplacementKind {
        NONE, CHASSIS, ENGINE, FRONT_WING, REAR_WING, TIRES, GEARBOX, STEERING_CONTROLS
    }
}
