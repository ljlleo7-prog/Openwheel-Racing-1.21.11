package com.openwheelracing.content.menu;

import com.openwheelracing.content.block.entity.CarPartsReplacementWorkstationBlockEntity;
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

public class CarPartsReplacementMenu extends AbstractContainerMenu {
    private static final int STATION_SLOTS = CarPartsReplacementWorkstationBlockEntity.SLOT_COUNT;
    private static final int PLAYER_START = STATION_SLOTS;
    private static final int PLAYER_END = PLAYER_START + 27;
    private static final int HOTBAR_START = PLAYER_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final Container container;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public CarPartsReplacementMenu(int id, Inventory inventory, FriendlyByteBuf extraData) {
        this(id, inventory, new SimpleContainer(STATION_SLOTS), new SimpleContainerData(3));
    }

    public CarPartsReplacementMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(OWRMenus.CAR_PARTS_REPLACEMENT.get(), id);
        checkContainerSize(container, STATION_SLOTS);
        checkContainerDataCount(data, 3);
        this.container = container;
        this.data = data;
        this.access = container instanceof CarPartsReplacementWorkstationBlockEntity station && station.getLevel() != null
            ? ContainerLevelAccess.create(station.getLevel(), station.getBlockPos()) : ContainerLevelAccess.NULL;

        addSlot(new LockedInputSlot(container, CarPartsReplacementWorkstationBlockEntity.SLOT_CAR, 44, 48, true));
        addSlot(new LockedInputSlot(container, CarPartsReplacementWorkstationBlockEntity.SLOT_NEW_PART, 80, 48, false));
        addSlot(new OutputSlot(container, CarPartsReplacementWorkstationBlockEntity.SLOT_REMOVED_PART, 134, 48));
        addPlayerInventory(inventory);
        addPlayerHotbar(inventory);
        addDataSlots(data);
    }

    public boolean queueReplacement() {
        return container instanceof CarPartsReplacementWorkstationBlockEntity station && station.queueReplacement();
    }

    public boolean canStartReplacement() {
        return container instanceof CarPartsReplacementWorkstationBlockEntity station ? station.canReplace() : !isReplacing()
            && container.getItem(CarPartsReplacementWorkstationBlockEntity.SLOT_CAR).is(OWRItems.PROTOTYPE_CAR_SPAWN.get())
            && !container.getItem(CarPartsReplacementWorkstationBlockEntity.SLOT_NEW_PART).isEmpty()
            && container.getItem(CarPartsReplacementWorkstationBlockEntity.SLOT_REMOVED_PART).isEmpty();
    }

    public boolean isReplacing() {
        return data.get(2) != 0;
    }

    public int getScaledProgress() {
        int max = data.get(1);
        return max <= 0 ? 0 : data.get(0) * 32 / max;
    }

    public Container getContainer() {
        return container;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < STATION_SLOTS) {
            if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_END, true)) return ItemStack.EMPTY;
        } else if (stack.is(OWRItems.PROTOTYPE_CAR_SPAWN.get())) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (container instanceof CarPartsReplacementWorkstationBlockEntity station && station.isReplacementPart(stack)) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else if (index < PLAYER_END) {
            if (!moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> level.getBlockState(pos).is(OWRBlocks.CAR_PARTS_REPLACEMENT_STATION.get())
            && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0, true);
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 94 + row * 18));
    }

    private void addPlayerHotbar(Inventory inventory) {
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 152));
    }

    private class LockedInputSlot extends Slot {
        private final boolean car;

        LockedInputSlot(Container container, int slot, int x, int y, boolean car) {
            super(container, slot, x, y);
            this.car = car;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (isReplacing()) return false;
            return car ? stack.is(OWRItems.PROTOTYPE_CAR_SPAWN.get()) && stack.getCount() == 1
                : container instanceof CarPartsReplacementWorkstationBlockEntity station && station.isReplacementPart(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return !isReplacing();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private class OutputSlot extends Slot {
        OutputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
