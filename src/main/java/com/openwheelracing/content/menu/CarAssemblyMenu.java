package com.openwheelracing.content.menu;

import com.openwheelracing.content.block.entity.CarAssemblyWorkstationBlockEntity;
import com.openwheelracing.content.block.entity.CarWorkstationType;
import com.openwheelracing.content.car.PrototypeCarSetup;
import com.openwheelracing.registry.OWRBlocks;
import com.openwheelracing.registry.OWRItems;
import com.openwheelracing.registry.OWRMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CarAssemblyMenu extends AbstractContainerMenu {
    private static final int WORKSTATION_SLOT_COUNT = CarAssemblyWorkstationBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INVENTORY_START = WORKSTATION_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final Container container;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public CarAssemblyMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, new SimpleContainer(WORKSTATION_SLOT_COUNT), new SimpleContainerData(3));
    }

    public CarAssemblyMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(OWRMenus.CAR_ASSEMBLY.get(), containerId);
        checkContainerSize(container, WORKSTATION_SLOT_COUNT);
        checkContainerDataCount(data, 3);
        this.container = container;
        this.data = data;
        this.access = container instanceof CarAssemblyWorkstationBlockEntity workstation && workstation.getLevel() != null
            ? ContainerLevelAccess.create(workstation.getLevel(), workstation.getBlockPos())
            : ContainerLevelAccess.NULL;

        addWorkstationSlots(container);
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
        addDataSlots(data);
    }

    public int getScaledProgress() {
        int progress = data.get(0);
        int maxProgress = data.get(1);
        return maxProgress == 0 || progress == 0 ? 0 : progress * 24 / maxProgress;
    }

    public Container getContainer() {
        return container;
    }

    public ItemStack getOutputStack() {
        return container.getItem(CarAssemblyWorkstationBlockEntity.SLOT_OUTPUT);
    }

    public boolean queueSetupTune(int slot, int delta) {
        return container instanceof CarAssemblyWorkstationBlockEntity workstation && workstation.queueSetupTune(slot, delta);
    }

    public boolean queueSetup(PrototypeCarSetup setup) {
        return container instanceof CarAssemblyWorkstationBlockEntity workstation && workstation.queueSetup(setup);
    }

    public boolean queueRepair() {
        return container instanceof CarAssemblyWorkstationBlockEntity workstation && workstation.queueRepair();
    }

    public boolean queueLiveryPreset(int delta) {
        return container instanceof CarAssemblyWorkstationBlockEntity workstation && workstation.queueLiveryPreset(delta);
    }

    public boolean queueLiveryColor(int channel, int color) {
        return container instanceof CarAssemblyWorkstationBlockEntity workstation && workstation.queueLiveryColor(channel, color);
    }

    public boolean queueLiveryTexture(String textureId) {
        return container instanceof CarAssemblyWorkstationBlockEntity workstation && workstation.queueLiveryTexture(textureId);
    }

    public CarWorkstationType getWorkstationType() {
        return container instanceof CarAssemblyWorkstationBlockEntity workstation ? workstation.getWorkstationType() : CarWorkstationType.fromOrdinal(data.get(2));
    }

    public boolean allowsConstruction() {
        return getWorkstationType().allowsConstruction();
    }

    public boolean allowsSetup() {
        return getWorkstationType().allowsSetup();
    }

    public boolean allowsLivery() {
        return getWorkstationType().allowsLivery();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index == CarAssemblyWorkstationBlockEntity.SLOT_OUTPUT) {
                if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stack, result);
            } else if (index < WORKSTATION_SLOT_COUNT) {
                if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (moveToCarTuningSlot(stack)) {
            } else if (!moveToMatchingInput(stack)) {
                if (index < PLAYER_INVENTORY_END) {
                    if (!moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stack.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, stack);
        }

        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> level.getBlockState(pos).getBlock() == stationBlock() && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0, true);
    }

    private net.minecraft.world.level.block.Block stationBlock() {
        return switch (getWorkstationType()) {
            case CONSTRUCTION -> OWRBlocks.CAR_CONSTRUCTION_STATION.get();
            case SETUP -> OWRBlocks.CAR_SETUP_STATION.get();
            case LIVERY -> OWRBlocks.CAR_LIVERY_STATION.get();
            case LEGACY -> OWRBlocks.CAR_ASSEMBLY_WORKSTATION.get();
        };
    }

    private net.minecraft.world.item.Item requiredItemForSlot(int slot) {
        return switch (slot) {
            case CarAssemblyWorkstationBlockEntity.SLOT_CHASSIS -> OWRItems.CHASSIS.get();
            case CarAssemblyWorkstationBlockEntity.SLOT_ENGINE -> OWRItems.ENGINE.get();
            case CarAssemblyWorkstationBlockEntity.SLOT_TIRES -> OWRItems.TIRES.get();
            case CarAssemblyWorkstationBlockEntity.SLOT_AERO_KIT -> OWRItems.AERO_KIT.get();
            case CarAssemblyWorkstationBlockEntity.SLOT_GEARBOX -> OWRItems.GEARBOX.get();
            case CarAssemblyWorkstationBlockEntity.SLOT_STEERING_CONTROLS -> OWRItems.STEERING_CONTROLS.get();
            default -> OWRItems.PROTOTYPE_CAR_SPAWN.get();
        };
    }

    private void addWorkstationSlots(Container container) {
        addSlot(new ComponentSlot(container, CarAssemblyWorkstationBlockEntity.SLOT_CHASSIS, 52, 36));
        addSlot(new ComponentSlot(container, CarAssemblyWorkstationBlockEntity.SLOT_ENGINE, 52, 70));
        addSlot(new ComponentSlot(container, CarAssemblyWorkstationBlockEntity.SLOT_TIRES, 18, 53));
        addSlot(new ComponentSlot(container, CarAssemblyWorkstationBlockEntity.SLOT_AERO_KIT, 52, 16));
        addSlot(new ComponentSlot(container, CarAssemblyWorkstationBlockEntity.SLOT_GEARBOX, 86, 70));
        addSlot(new ComponentSlot(container, CarAssemblyWorkstationBlockEntity.SLOT_STEERING_CONTROLS, 86, 36));
        addSlot(new CarTuningSlot(container, CarAssemblyWorkstationBlockEntity.SLOT_OUTPUT, 130, 45));
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 46 + column * 18, 136 + row * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 46 + column * 18, 194));
        }
    }

    private boolean moveToCarTuningSlot(ItemStack stack) {
        if (!allowsSetup() && !allowsLivery()) {
            return false;
        }
        Slot tuningSlot = slots.get(CarAssemblyWorkstationBlockEntity.SLOT_OUTPUT);
        return tuningSlot.mayPlace(stack) && moveItemStackTo(stack, CarAssemblyWorkstationBlockEntity.SLOT_OUTPUT, CarAssemblyWorkstationBlockEntity.SLOT_OUTPUT + 1, false);
    }

    private boolean moveToMatchingInput(ItemStack stack) {
        if (!allowsConstruction()) {
            return false;
        }
        for (int slot = 0; slot <= CarAssemblyWorkstationBlockEntity.SLOT_STEERING_CONTROLS; slot++) {
            Slot inputSlot = slots.get(slot);
            if (inputSlot.mayPlace(stack) && moveItemStackTo(stack, slot, slot + 1, false)) {
                return true;
            }
        }
        return false;
    }

    private class ComponentSlot extends Slot {
        ComponentSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return allowsConstruction() && stack.is(requiredItemForSlot(getSlotIndex()));
        }

        @Override
        public boolean isActive() {
            return allowsConstruction();
        }
    }

    private class CarTuningSlot extends Slot {
        CarTuningSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return (allowsSetup() || allowsLivery()) && stack.is(OWRItems.PROTOTYPE_CAR_SPAWN.get()) && stack.getCount() == 1;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return stack.is(OWRItems.PROTOTYPE_CAR_SPAWN.get()) ? 1 : 0;
        }
    }
}
