package com.openwheelracing.content.menu;

import com.openwheelracing.content.block.entity.RaceLightBlockEntity;
import com.openwheelracing.content.race.PitLightMode;
import com.openwheelracing.content.race.RaceLightType;
import com.openwheelracing.registry.OWRBlocks;
import com.openwheelracing.registry.OWRMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class RaceLightMenu extends AbstractContainerMenu {
    private final RaceLightBlockEntity light;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    public RaceLightMenu(int id, Inventory inventory, FriendlyByteBuf extra) {
        this(id, inventory, null, clientData(inventory, extra.readBlockPos()));
    }
    public RaceLightMenu(int id, Inventory inventory, RaceLightBlockEntity light) {
        this(id, inventory, light, serverData(light));
    }
    private RaceLightMenu(int id, Inventory inventory, RaceLightBlockEntity light, ContainerData data) {
        super(OWRMenus.RACE_LIGHT.get(), id);
        this.light = light;
        this.access = light != null && light.getLevel() != null ? ContainerLevelAccess.create(light.getLevel(), light.getBlockPos()) : ContainerLevelAccess.NULL;
        this.data = data;
        checkContainerDataCount(data, 9);
        addDataSlots(data);
    }
    private static ContainerData serverData(RaceLightBlockEntity light) {
        return new ContainerData() {
            public int get(int i) { return switch (i) {
                case 0 -> light.getLightType().ordinal();
                case 1 -> light.hasRouteAssignment() ? 1 : 0;
                case 2 -> (int) Math.round(light.getPrimaryRouteDistance() * 10.0);
                case 3 -> (int) Math.round(light.getSecondaryRouteDistance() * 10.0);
                case 4 -> (int) Math.round(light.getAssignedRouteDistance() * 10.0);
                case 5 -> light.getAssignmentConfidence();
                case 6 -> light.isManualRouteChoice() ? 1 : 0;
                case 7 -> light.getStartOrder();
                case 8 -> light.getPitMode().ordinal();
                default -> 0;
            }; }
            public void set(int i, int value) { }
            public int getCount() { return 9; }
        };
    }
    private static ContainerData clientData(Inventory inventory, BlockPos pos) {
        SimpleContainerData data = new SimpleContainerData(9);
        if (inventory.player.level().getBlockEntity(pos) instanceof RaceLightBlockEntity light) {
            data.set(0, light.getLightType().ordinal());
            data.set(1, light.hasRouteAssignment() ? 1 : 0);
            data.set(2, (int) Math.round(light.getPrimaryRouteDistance() * 10.0));
            data.set(3, (int) Math.round(light.getSecondaryRouteDistance() * 10.0));
            data.set(4, (int) Math.round(light.getAssignedRouteDistance() * 10.0));
            data.set(5, light.getAssignmentConfidence());
            data.set(6, light.isManualRouteChoice() ? 1 : 0);
            data.set(7, light.getStartOrder());
            data.set(8, light.getPitMode().ordinal());
        }
        return data;
    }
    public RaceLightType getLightType() { return RaceLightType.values()[Math.max(0, Math.min(2, data.get(0)))]; }
    public boolean hasRouteAssignment() { return data.get(1) != 0; }
    public double getPrimaryRouteDistance() { return data.get(2) / 10.0; }
    public double getSecondaryRouteDistance() { return data.get(3) / 10.0; }
    public double getAssignedRouteDistance() { return data.get(4) / 10.0; }
    public int getAssignmentConfidence() { return data.get(5); }
    public boolean isManualRouteChoice() { return data.get(6) != 0; }
    public boolean hasSecondaryRouteCandidate() { return data.get(3) >= 0; }
    public int getStartOrder() { return data.get(7); }
    public PitLightMode getPitMode() { return PitLightMode.fromOrdinal(data.get(8)); }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (light == null) return false;
        if (button == 0) light.autoDetectSector();
        else if (button == 1) light.chooseRouteCandidate(false); else if (button == 2) light.chooseRouteCandidate(true);
        else if (button == 5) light.setStartOrder(light.getStartOrder() - 1); else if (button == 6) light.setStartOrder(light.getStartOrder() + 1);
        else if (button >= 10 && button <= 12) light.setPitMode(PitLightMode.fromOrdinal(button - 10)); else return false;
        broadcastChanges(); return true;
    }
    @Override public boolean stillValid(Player player) {
        if (light == null) return true;
        return access.evaluate((level, pos) -> level.getBlockState(pos).is(OWRBlocks.FLAG_LIGHT.get()) || level.getBlockState(pos).is(OWRBlocks.STARTING_LIGHT.get()) || level.getBlockState(pos).is(OWRBlocks.PIT_LIGHT.get()), false);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
