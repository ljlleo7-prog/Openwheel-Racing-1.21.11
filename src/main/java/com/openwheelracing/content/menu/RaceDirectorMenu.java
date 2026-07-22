package com.openwheelracing.content.menu;

import com.openwheelracing.content.block.entity.RaceDirectorBlockEntity;
import com.openwheelracing.content.race.OWRLapRecords;
import com.openwheelracing.content.race.OWRRaceControlState;
import com.openwheelracing.content.race.RaceDirectorLapRow;
import com.openwheelracing.content.race.RaceDirectorSnapshot;
import com.openwheelracing.network.OWRNetwork;
import com.openwheelracing.registry.OWRBlocks;
import com.openwheelracing.registry.OWRMenus;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

public class RaceDirectorMenu extends AbstractContainerMenu {
    public static final int LAPS_PER_PAGE = 7;

    private final ContainerLevelAccess access;
    private final Player player;
    private int page;
    private int lastRaceControlRevision = Integer.MIN_VALUE;
    private int lastLapRecordsRevision = Integer.MIN_VALUE;
    private RaceDirectorSnapshot snapshot = RaceDirectorSnapshot.empty();

    public RaceDirectorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        super(OWRMenus.RACE_DIRECTOR.get(), containerId);
        this.player = playerInventory.player;
        this.access = ContainerLevelAccess.NULL;
    }

    public RaceDirectorMenu(int containerId, Inventory playerInventory, RaceDirectorBlockEntity raceDirector) {
        super(OWRMenus.RACE_DIRECTOR.get(), containerId);
        this.player = playerInventory.player;
        this.access = raceDirector.getLevel() != null
            ? ContainerLevelAccess.create(raceDirector.getLevel(), raceDirector.getBlockPos())
            : ContainerLevelAccess.NULL;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    public RaceDirectorSnapshot getSnapshot() {
        return snapshot;
    }

    public void applySnapshot(RaceDirectorSnapshot snapshot) {
        this.snapshot = snapshot;
        this.page = snapshot.page();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer serverPlayer) || !(serverPlayer.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        OWRRaceControlState controlState = OWRRaceControlState.get(serverLevel);
        OWRLapRecords records = OWRLapRecords.get(serverLevel);
        if (controlState.getRevision() == lastRaceControlRevision && records.getRevision() == lastLapRecordsRevision) {
            return;
        }
        lastRaceControlRevision = controlState.getRevision();
        lastLapRecordsRevision = records.getRevision();
        OWRNetwork.sendRaceDirectorSnapshot(serverPlayer, createSnapshot(serverLevel));
    }

    public RaceDirectorSnapshot createSnapshot(ServerLevel level) {
        OWRRaceControlState controlState = OWRRaceControlState.get(level);
        OWRLapRecords records = OWRLapRecords.get(level);
        int totalLaps = records.getLapCount();
        int maxPage = Math.max(0, (totalLaps - 1) / LAPS_PER_PAGE);
        page = Math.min(page, maxPage);
        List<RaceDirectorLapRow> laps = records.getRecentLaps(page, LAPS_PER_PAGE).stream()
            .map(RaceDirectorLapRow::fromRecord)
            .toList();
        return new RaceDirectorSnapshot(
            controlState.isCheckpointCheckEnabled(),
            controlState.isOffTrackCheckEnabled(),
            controlState.getMinimumValidLapTicks(),
            page,
            maxPage,
            controlState.getRevision(),
            records.getRevision(),
            controlState.getMaxErsCapacityMj(),
            controlState.getMaxBalancedDeployKw(),
            controlState.getMaxAttackDeployKw(),
            controlState.getMaxHarvestNegativeKw(),
            laps
        );
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, OWRBlocks.RACE_DIRECTOR.get());
    }
}
