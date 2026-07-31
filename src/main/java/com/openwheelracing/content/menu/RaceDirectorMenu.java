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

public class RaceDirectorMenu extends AbstractContainerMenu {
    public static final int LAPS_PER_PAGE = 7;

    private final ContainerLevelAccess access;
    private final Player player;
    private final RaceMonitorType monitorType;
    private final RaceDirectorBlockEntity raceDirector;
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
        this.raceDirector = null;
        this.access = ContainerLevelAccess.NULL;
    }

    public RaceDirectorMenu(int containerId, Inventory playerInventory, RaceDirectorBlockEntity raceDirector) {
        super(OWRMenus.typeFor(raceDirector.getMonitorType()).get(), containerId);
        this.player = playerInventory.player;
        this.monitorType = raceDirector.getMonitorType();
        this.raceDirector = raceDirector;
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
        if (!showsTeamTerminal() && controlState.getRevision() == lastRaceControlRevision && records.getRevision() == lastLapRecordsRevision) {
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
            leftTeamCarId(),
            rightTeamCarId(),
            laps,
            senseTeamCars(level)
        );
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public void bindTeamCar(int side, int entityId) {
        if (!showsTeamTerminal() || raceDirector == null || side < 0 || side > 1) {
            return;
        }
        raceDirector.setTeamCarId(side, entityId);
    }

    public int leftTeamCarId() {
        return raceDirector == null ? -1 : raceDirector.getLeftTeamCarId();
    }

    public int rightTeamCarId() {
        return raceDirector == null ? -1 : raceDirector.getRightTeamCarId();
    }

    private List<TeamCarRow> senseTeamCars(ServerLevel level) {
        if (!showsTeamTerminal()) {
            return List.of();
        }
        java.util.ArrayList<OpenwheelCarEntity> cars = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (entity instanceof OpenwheelCarEntity car) {
                cars.add(car);
            }
        }
        return cars.stream()
            .sorted(java.util.Comparator.comparingInt(car -> selectionRank(car.getId())))
            .map(TeamCarRow::fromCar)
            .limit(24)
            .toList();
    }

    private int selectionRank(int entityId) {
        if (entityId == leftTeamCarId()) {
            return 0;
        }
        if (entityId == rightTeamCarId()) {
            return 1;
        }
        return 2;
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
