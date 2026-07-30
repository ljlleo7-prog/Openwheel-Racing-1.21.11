package com.openwheelracing.content.menu;

import com.openwheelracing.content.block.entity.RaceDirectorBlockEntity;
import com.openwheelracing.content.block.entity.RaceMonitorType;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.race.OWRLapRecords;
import com.openwheelracing.content.race.OWRRaceControlState;
import com.openwheelracing.content.race.RaceDirectorLapRow;
import com.openwheelracing.content.race.RaceDirectorSnapshot;
import com.openwheelracing.content.race.TeamCarRow;
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
import net.minecraft.world.phys.AABB;

public class RaceDirectorMenu extends AbstractContainerMenu {
    public static final int LAPS_PER_PAGE = 7;

    private final ContainerLevelAccess access;
    private final Player player;
    private final RaceMonitorType monitorType;
    private int page;
    private boolean archiveMode;
    private int lastRaceControlRevision = Integer.MIN_VALUE;
    private int lastLapRecordsRevision = Integer.MIN_VALUE;
    private RaceDirectorSnapshot snapshot = RaceDirectorSnapshot.empty();

    public RaceDirectorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, RaceMonitorType.DIRECTOR);
    }

    public RaceDirectorMenu(int containerId, Inventory playerInventory, RaceMonitorType monitorType) {
        super(OWRMenus.typeFor(monitorType).get(), containerId);
        this.player = playerInventory.player;
        this.monitorType = monitorType;
        this.access = ContainerLevelAccess.NULL;
    }

    public RaceDirectorMenu(int containerId, Inventory playerInventory, RaceDirectorBlockEntity raceDirector) {
        super(OWRMenus.typeFor(raceDirector.getMonitorType()).get(), containerId);
        this.player = playerInventory.player;
        this.monitorType = raceDirector.getMonitorType();
        this.access = raceDirector.getLevel() != null
            ? ContainerLevelAccess.create(raceDirector.getLevel(), raceDirector.getBlockPos())
            : ContainerLevelAccess.NULL;
    }

    public RaceMonitorType getMonitorType() {
        return monitorType;
    }

    public boolean allowsRaceControl() {
        return monitorType.isRaceControl();
    }

    public boolean showsBoard() {
        return monitorType == RaceMonitorType.DIRECTOR || monitorType == RaceMonitorType.BOARD;
    }

    public boolean showsTeamTerminal() {
        return monitorType == RaceMonitorType.TEAM;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    public boolean isArchiveMode() {
        return archiveMode;
    }

    public void setArchiveMode(boolean archiveMode) {
        if (this.archiveMode != archiveMode) {
            this.archiveMode = archiveMode;
            page = 0;
        }
    }

    public RaceDirectorSnapshot getSnapshot() {
        return snapshot;
    }

    public void applySnapshot(RaceDirectorSnapshot snapshot) {
        this.snapshot = snapshot;
        this.page = snapshot.page();
        this.archiveMode = snapshot.archiveMode();
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
        int totalLaps = records.getVisibleLapCount(archiveMode);
        int maxPage = Math.max(0, (totalLaps - 1) / LAPS_PER_PAGE);
        page = Math.min(page, maxPage);
        List<RaceDirectorLapRow> laps = records.getVisibleLaps(archiveMode, page, LAPS_PER_PAGE).stream()
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
            controlState.getGlobalFlag(),
            controlState.getCarDamageModifier(),
            controlState.getTyreWearModifier(),
            records.getActiveSessionId(),
            records.getActiveSessionName(),
            archiveMode,
            laps,
            senseTeamCars(level)
        );
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private List<TeamCarRow> senseTeamCars(ServerLevel level) {
        if (!showsTeamTerminal()) {
            return List.of();
        }
        AABB search = player.getBoundingBox().inflate(96.0);
        return level.getEntities(player, search, entity -> entity instanceof OpenwheelCarEntity)
            .stream()
            .sorted(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(player)))
            .map(entity -> TeamCarRow.fromCar((OpenwheelCarEntity) entity))
            .limit(8)
            .toList();
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, blockForMonitor());
    }

    private net.minecraft.world.level.block.Block blockForMonitor() {
        return switch (monitorType) {
            case DIRECTOR -> OWRBlocks.RACE_DIRECTOR.get();
            case BOARD -> OWRBlocks.RACE_BOARD_TERMINAL.get();
            case TEAM -> OWRBlocks.TEAM_TERMINAL.get();
        };
    }
}
