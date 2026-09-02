package com.openwheelracing.content.block.entity;

import com.openwheelracing.content.block.RaceLightBlock;
import com.openwheelracing.content.menu.RaceLightMenu;
import com.openwheelracing.content.race.OWRRaceControlState;
import com.openwheelracing.content.race.PitLightMode;
import com.openwheelracing.content.race.RaceAutoFlagService;
import com.openwheelracing.content.race.RaceLightType;
import com.openwheelracing.content.race.RaceSignal;
import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.content.track.TrackDefinitionsData;
import com.openwheelracing.content.track.survey.SurveyRoute;
import com.openwheelracing.content.track.survey.SurveyRouteGeometry;
import com.openwheelracing.content.track.survey.SurveyRouteModel;
import com.openwheelracing.content.track.survey.TrackSurveyData;
import com.openwheelracing.registry.OWRBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class RaceLightBlockEntity extends BlockEntity implements MenuProvider {
    public static final int FLAG_FLASH_HALF_PERIOD_TICKS = 5;
    public static final int MIN_ROUTE_DETECTION_RANGE = 4;
    public static final int MAX_ROUTE_DETECTION_RANGE = 64;
    public static final int DEFAULT_ROUTE_DETECTION_RANGE = 16;
    private static final double MAX_ASSIGNMENT_VERTICAL = 4.0;
    private static final double DISTINCT_ROUTE_DISTANCE = 20.0;
    private final RaceLightType type;
    private int sector;
    private int minisector = -1;
    private boolean automaticSector = true;
    private double primaryRouteDistance = -1.0;
    private double secondaryRouteDistance = -1.0;
    private double assignedRouteDistance = -1.0;
    private double assignedRouteLength;
    private int assignmentConfidence;
    private boolean manualRouteChoice;
    private String assignedRouteId = "";
    private int routeDetectionRange = DEFAULT_ROUTE_DETECTION_RANGE;
    private int startOrder = 1;
    private PitLightMode pitMode = PitLightMode.ENTRY;

    public RaceLightBlockEntity(BlockPos pos, BlockState state, RaceLightType type) {
        super(OWRBlockEntities.typeFor(type).get(), pos, state);
        this.type = type;
    }

    public RaceLightType getLightType() { return type; }
    public int getSector() { return sector; }
    public int getMinisector() { return minisector; }
    public boolean isAutomaticSector() { return automaticSector; }
    public boolean hasRouteAssignment() { return assignedRouteDistance >= 0.0 && assignedRouteLength > 0.0; }
    public boolean hasSecondaryRouteCandidate() { return secondaryRouteDistance >= 0.0; }
    public double getPrimaryRouteDistance() { return primaryRouteDistance; }
    public double getSecondaryRouteDistance() { return secondaryRouteDistance; }
    public double getAssignedRouteDistance() { return assignedRouteDistance; }
    public int getAssignmentConfidence() { return assignmentConfidence; }
    public boolean isManualRouteChoice() { return manualRouteChoice; }
    public int getRouteDetectionRange() { return routeDetectionRange; }
    public int getStartOrder() { return startOrder; }
    public PitLightMode getPitMode() { return pitMode; }
    public void setSector(int value) { sector = Math.max(0, value); automaticSector = false; setChanged(); }
    public void setMinisector(int value) { minisector = Math.max(-1, value); automaticSector = false; setChanged(); }
    public void setStartOrder(int value) { startOrder = Math.max(1, Math.min(5, value)); setChanged(); }
    public void setPitMode(PitLightMode value) { pitMode = value == null ? PitLightMode.ENTRY : value; setChanged(); }
    public void setRouteDetectionRange(int value) {
        routeDetectionRange = Math.max(MIN_ROUTE_DETECTION_RANGE, Math.min(MAX_ROUTE_DETECTION_RANGE, value));
        setChanged();
    }

    public void autoDetectSector() {
        detectRouteCandidates(false);
        setChanged();
    }

    public void chooseRouteCandidate(boolean secondary) {
        double selected = secondary ? secondaryRouteDistance : primaryRouteDistance;
        if (selected < 0.0) return;
        assignedRouteDistance = selected;
        manualRouteChoice = hasSecondaryRouteCandidate();
        automaticSector = false;
        setChanged();
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.getGameTime() % FLAG_FLASH_HALF_PERIOD_TICKS != 0L) return;
        if (type == RaceLightType.FLAG && serverLevel.getGameTime() % 40L == 0L) validateOrDetectRoute();
        RaceSignal signal = resolveSignal(OWRRaceControlState.get(serverLevel));
        if (type == RaceLightType.FLAG && signal != RaceSignal.OFF
                && serverLevel.getGameTime() / FLAG_FLASH_HALF_PERIOD_TICKS % 2L != 0L) {
            signal = RaceSignal.OFF;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(RaceLightBlock.SIGNAL) && state.getValue(RaceLightBlock.SIGNAL) != signal) {
            serverLevel.setBlock(worldPosition, state.setValue(RaceLightBlock.SIGNAL, signal), 3);
        }
    }

    private RaceSignal resolveSignal(OWRRaceControlState control) {
        return switch (type) {
            case FLAG -> resolveFlagSignal(control);
            case START -> control.getStartPhase() >= startOrder && control.getStartPhase() <= 5 ? RaceSignal.RED : RaceSignal.OFF;
            case PIT -> control.getPitSignal(pitMode);
        };
    }

    private RaceSignal resolveFlagSignal(OWRRaceControlState control) {
        RaceSignal global = control.signalForGlobalFlag();
        if (global != RaceSignal.GREEN || !control.isAutoFlagging() || !(level instanceof ServerLevel serverLevel) || !hasRouteAssignment()) {
            return global;
        }
        RaceSignal automatic = RaceAutoFlagService.signalForLight(serverLevel, assignedRouteDistance, assignedRouteLength);
        return automatic == RaceSignal.YELLOW ? automatic : global;
    }

    private void validateOrDetectRoute() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        SurveyRoute route = activeRoute(serverLevel);
        if (route == null) return;
        if (!route.routeId().toString().equals(assignedRouteId) || !hasRouteAssignment()) detectRouteCandidates(false);
    }

    private void detectRouteCandidates(boolean preserveManualChoice) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        SurveyRoute route = activeRoute(serverLevel);
        if (route == null) {
            clearRouteAssignment();
            return;
        }
        SurveyRouteModel model = route.toModel();
        SurveyRouteModel.Point position = new SurveyRouteModel.Point(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        java.util.List<SurveyRouteGeometry.Candidate> candidates = SurveyRouteGeometry.candidates(model, position, 0.0, -1, model.nodes().size()).stream()
            .filter(candidate -> candidate.horizontalDistance() <= routeDetectionRange && Math.abs(candidate.verticalDelta()) <= MAX_ASSIGNMENT_VERTICAL)
            .sorted(java.util.Comparator.comparingDouble(candidate -> candidate.horizontalDistance() * candidate.horizontalDistance() + candidate.verticalDelta() * candidate.verticalDelta()))
            .toList();
        if (candidates.isEmpty()) {
            clearRouteAssignment();
            assignedRouteId = route.routeId().toString();
            return;
        }
        SurveyRouteGeometry.Candidate primary = candidates.getFirst();
        SurveyRouteGeometry.Candidate secondary = candidates.stream().skip(1)
            .filter(candidate -> circularSeparation(model.length(), primary.distanceAlongRoute(), candidate.distanceAlongRoute()) >= DISTINCT_ROUTE_DISTANCE)
            .findFirst().orElse(null);
        double previous = assignedRouteDistance;
        primaryRouteDistance = primary.distanceAlongRoute();
        secondaryRouteDistance = secondary == null ? -1.0 : secondary.distanceAlongRoute();
        assignedRouteLength = model.length();
        assignedRouteId = route.routeId().toString();
        assignmentConfidence = secondary == null ? 100 : Math.max(1, Math.min(99,
            (int) Math.round(100.0 * secondary.score() / Math.max(0.01, primary.score() + secondary.score()))));
        if (preserveManualChoice && previous >= 0.0 && secondary != null
                && circularSeparation(model.length(), previous, secondaryRouteDistance) < circularSeparation(model.length(), previous, primaryRouteDistance)) {
            assignedRouteDistance = secondaryRouteDistance;
        } else {
            assignedRouteDistance = primaryRouteDistance;
            manualRouteChoice = false;
            automaticSector = true;
        }
        setChanged();
    }

    private SurveyRoute activeRoute(ServerLevel serverLevel) {
        TrackDefinition track = TrackDefinitionsData.get(serverLevel).activeTrack(serverLevel.dimension().identifier().toString()).orElse(null);
        return track == null ? null : TrackSurveyData.get(serverLevel).get(track.trackId()).orElse(null);
    }

    private void clearRouteAssignment() {
        primaryRouteDistance = -1.0;
        secondaryRouteDistance = -1.0;
        assignedRouteDistance = -1.0;
        assignedRouteLength = 0.0;
        assignmentConfidence = 0;
        manualRouteChoice = false;
    }

    private static double circularSeparation(double length, double first, double second) {
        double separation = Math.abs(first - second);
        return length > 0.0 ? Math.min(separation, length - separation) : separation;
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Sector", sector); output.putInt("Minisector", minisector); output.putBoolean("AutomaticSector", automaticSector);
        output.putDouble("PrimaryRouteDistance", primaryRouteDistance); output.putDouble("SecondaryRouteDistance", secondaryRouteDistance);
        output.putDouble("AssignedRouteDistance", assignedRouteDistance); output.putDouble("AssignedRouteLength", assignedRouteLength);
        output.putInt("AssignmentConfidence", assignmentConfidence); output.putBoolean("ManualRouteChoice", manualRouteChoice);
        output.putString("AssignedRouteId", assignedRouteId);
        output.putInt("RouteDetectionRange", routeDetectionRange);
        output.putInt("StartOrder", startOrder); output.putInt("PitMode", pitMode.ordinal());
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        sector = Math.max(0, input.getIntOr("Sector", 0)); minisector = Math.max(-1, input.getIntOr("Minisector", -1));
        automaticSector = input.getBooleanOr("AutomaticSector", true); startOrder = Math.max(1, Math.min(5, input.getIntOr("StartOrder", 1)));
        primaryRouteDistance = input.getDoubleOr("PrimaryRouteDistance", -1.0); secondaryRouteDistance = input.getDoubleOr("SecondaryRouteDistance", -1.0);
        assignedRouteDistance = input.getDoubleOr("AssignedRouteDistance", -1.0); assignedRouteLength = input.getDoubleOr("AssignedRouteLength", 0.0);
        assignmentConfidence = input.getIntOr("AssignmentConfidence", 0); manualRouteChoice = input.getBooleanOr("ManualRouteChoice", false);
        assignedRouteId = input.getStringOr("AssignedRouteId", "");
        routeDetectionRange = Math.max(MIN_ROUTE_DETECTION_RANGE, Math.min(MAX_ROUTE_DETECTION_RANGE,
            input.getIntOr("RouteDetectionRange", DEFAULT_ROUTE_DETECTION_RANGE)));
        pitMode = PitLightMode.fromOrdinal(input.getIntOr("PitMode", 0));
    }
    @Override public Component getDisplayName() { return Component.translatable("container.openwheelracing." + type.name().toLowerCase() + "_light"); }
    @Override public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new RaceLightMenu(id, inventory, this); }
}
