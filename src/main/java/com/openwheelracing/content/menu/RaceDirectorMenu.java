package com.openwheelracing.content.menu;

import com.openwheelracing.content.block.entity.RaceDirectorBlockEntity;
import com.openwheelracing.content.block.entity.RaceMonitorType;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.race.OWRLapRecords;
import com.openwheelracing.content.race.OWRRaceControlState;
import com.openwheelracing.content.race.RaceDirectorLapRow;
import com.openwheelracing.content.race.RaceDirectorSnapshot;
import com.openwheelracing.content.race.TeamCarRow;
import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.content.track.TrackDefinitionsData;
import com.openwheelracing.content.track.TrackMapAutoDetector;
import com.openwheelracing.content.track.TrackMapData;
import com.openwheelracing.content.track.TrackMapSnapshot;
import com.openwheelracing.network.OWRNetwork;
import com.openwheelracing.registry.OWRBlocks;
import com.openwheelracing.registry.OWRMenus;
import java.util.List;
import java.util.UUID;
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
    private int lastMapRevision = Integer.MIN_VALUE;
    private int lastMapScanScannedChunks = -1;
    private int lastMapScanDetectedCells = -1;
    private int telemetryCarId = -1;
    private long lastTelemetrySendTick = Long.MIN_VALUE;
    private UUID lastTelemetryDriverId = new UUID(0L, 0L);
    private long lastTelemetryProfileLapId = -1L;
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
        sendTelemetry(serverPlayer, serverLevel);
        OWRRaceControlState controlState = OWRRaceControlState.get(serverLevel);
        OWRLapRecords records = OWRLapRecords.get(serverLevel);
        TrackMapSnapshot map = trackMap(serverLevel);
        TrackMapAutoDetector.Progress scanProgress = TrackMapAutoDetector.progress(serverLevel);
        boolean mapChanged = map.revision() != lastMapRevision;
        boolean scanUpdate = scanProgress.running()
            && (lastMapScanScannedChunks < 0
                || scanProgress.scannedChunks() == scanProgress.totalChunks()
                || scanProgress.scannedChunks() - lastMapScanScannedChunks >= 16
                || scanProgress.detectedCells() != lastMapScanDetectedCells);
        if (!showsTeamTerminal()
            && !mapChanged
            && !scanUpdate
            && controlState.getRevision() == lastRaceControlRevision
            && records.getRevision() == lastLapRecordsRevision) {
            return;
        }
        lastRaceControlRevision = controlState.getRevision();
        lastLapRecordsRevision = records.getRevision();
        lastMapRevision = map.revision();
        lastMapScanScannedChunks = scanProgress.running() ? scanProgress.scannedChunks() : -1;
        lastMapScanDetectedCells = scanProgress.running() ? scanProgress.detectedCells() : -1;
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
        TrackMapAutoDetector.Progress mapScan = TrackMapAutoDetector.progress(level);
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
            trackMap(level),
            mapScan.running(),
            mapScan.scannedChunks(),
            mapScan.totalChunks(),
            mapScan.detectedCells(),
            laps,
            senseTeamCars(level)
        );
    }

    public void setTelemetryCarId(int entityId) {
        if (monitorType != RaceMonitorType.BOARD && telemetryCarId != entityId) {
            telemetryCarId = entityId;
            lastTelemetryDriverId = new UUID(0L, 0L);
            lastTelemetryProfileLapId = -1L;
        }
    }

    private void sendTelemetry(ServerPlayer viewer, ServerLevel level) {
        if (telemetryCarId < 0 || monitorType == RaceMonitorType.BOARD || level.getGameTime() - lastTelemetrySendTick < 4L) return;
        lastTelemetrySendTick = level.getGameTime();
        if (!(level.getEntity(telemetryCarId) instanceof OpenwheelCarEntity car) || !(car.getControllingPassenger() instanceof ServerPlayer driver)) return;
        var latest = car.getLatestLapProfileTelemetry();
        var best = car.getCurrentBestLapProfile();
        boolean includeBest = !driver.getUUID().equals(lastTelemetryDriverId) || best != null && best.lapRecordId() != lastTelemetryProfileLapId;
        if (includeBest) {
            lastTelemetryDriverId = driver.getUUID();
            lastTelemetryProfileLapId = best == null ? -1L : best.lapRecordId();
        }
        OWRNetwork.sendMonitorTelemetry(viewer, car.getId(), driver.getUUID(), latest, car.getSpeedKmh(), car.getCurrentLapProfileRouteLength(), includeBest, best);
    }

    public void autoDetectTrackMap(ServerLevel level, int radiusBlocks) {
        TrackMapAutoDetector.begin(level, player.blockPosition(), radiusBlocks);
    }

    private TrackMapSnapshot trackMap(ServerLevel level) {
        TrackMapSnapshot base = TrackMapData.get(level).snapshot(level.dimension().identifier().toString()).orElse(TrackMapSnapshot.EMPTY);
        return withStewardTimingMarkers(level, base);
    }

    private TrackMapSnapshot withStewardTimingMarkers(ServerLevel level, TrackMapSnapshot base) {
        if (!base.present()) {
            return base;
        }
        java.util.ArrayList<TrackMapSnapshot.MapPoint> checkpoints = new java.util.ArrayList<>(base.checkpointMarkers());
        java.util.ArrayList<TrackMapSnapshot.MapPoint> startFinish = new java.util.ArrayList<>(base.startFinishMarkers());
        TrackDefinitionsData.get(level)
            .activeTrack(level.dimension().identifier().toString())
            .ifPresent(track -> {
                for (TrackDefinition.StewardLine line : track.stewardLines()) {
                    if (line.type() == TrackDefinition.StewardLineType.CHECKPOINT || line.type() == TrackDefinition.StewardLineType.SECTOR_SPLIT) {
                        checkpoints.add(midpoint(line));
                    }
                }
                track.startFinish().ifPresent(sf -> startFinish.add(midpoint(sf)));
            });
        return new TrackMapSnapshot(base.present(), base.source(), base.name(), base.dimensionId(), base.revision(), base.minX(), base.minZ(), base.maxX(), base.maxZ(), base.asphaltRuns(), base.pitRuns(), startFinish, checkpoints);
    }

    private TrackMapSnapshot.MapPoint midpoint(TrackDefinition.StewardLine line) {
        return new TrackMapSnapshot.MapPoint(
            (int) Math.round((line.left().x() + line.right().x()) * 0.5),
            (int) Math.round((line.left().z() + line.right().z()) * 0.5)
        );
    }

    private TrackMapSnapshot.MapPoint midpoint(TrackDefinition.StartFinishLine line) {
        return new TrackMapSnapshot.MapPoint(
            (int) Math.round((line.left().x() + line.right().x()) * 0.5),
            (int) Math.round((line.left().z() + line.right().z()) * 0.5)
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
        if (!showsTeamTerminal() && !showsBoard()) {
            return List.of();
        }
        TrackMapSnapshot map = trackMap(level);
        java.util.ArrayList<OpenwheelCarEntity> cars = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (entity instanceof OpenwheelCarEntity car) {
                cars.add(car);
            }
        }
        cars.sort(java.util.Comparator.comparingInt(car -> selectionRank(car.getId())));
        java.util.ArrayList<TeamCarRow> rows = new java.util.ArrayList<>();
        for (int index = 0; index < cars.size() && rows.size() < 24; index++) {
            rows.add(TeamCarRow.fromCar(cars.get(index), map, index + 1));
        }
        return rows;
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
