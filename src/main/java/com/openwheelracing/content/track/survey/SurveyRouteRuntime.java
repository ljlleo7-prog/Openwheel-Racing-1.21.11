package com.openwheelracing.content.track.survey;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.network.OWRNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SurveyRouteRuntime {
    private static final double SAMPLE_DISTANCE = 0.75;
    private static final Map<UUID, Recorder> RECORDERS = new HashMap<>();

    private SurveyRouteRuntime() {
    }

    public static boolean start(ServerPlayer player, OpenwheelCarEntity car, TrackDefinition track) {
        if (RECORDERS.containsKey(player.getUUID())) return false;
        Recorder recorder = new Recorder(track.trackId(), track.name(), player.level().dimension().identifier().toString());
        RECORDERS.put(player.getUUID(), recorder);
        sendOverlay(player, recorder, null);
        return true;
    }

    public static void onLapStart(OpenwheelCarEntity car, ServerPlayer player) {
        Recorder recorder = RECORDERS.get(player.getUUID());
        if (recorder == null || recorder.recording || !recorder.dimensionId.equals(player.level().dimension().identifier().toString())) return;
        recorder.recording = true;
        recorder.add(car.position(), carHeading(car));
        sendOverlay(player, recorder, null);
    }

    public static void recordMovement(OpenwheelCarEntity car) {
        if (!(car.level() instanceof ServerLevel) || !(car.getControllingPassenger() instanceof ServerPlayer player)) return;
        Recorder recorder = RECORDERS.get(player.getUUID());
        if (recorder == null || !recorder.recording || !recorder.dimensionId.equals(player.level().dimension().identifier().toString())) return;
        if (recorder.add(car.position(), carHeading(car)) && recorder.samples.size() % 20 == 0) sendOverlay(player, recorder, null);
    }

    public static Optional<Status> status(ServerPlayer player) {
        Recorder recorder = RECORDERS.get(player.getUUID());
        return recorder == null ? Optional.empty() : Optional.of(recorder.status());
    }

    public static Optional<FinishResult> onLapFinish(OpenwheelCarEntity car, ServerPlayer player) {
        Recorder recorder = RECORDERS.get(player.getUUID());
        if (recorder == null || !recorder.recording) return Optional.empty();
        recorder.add(car.position(), carHeading(car));
        FinishResult result = buildAndSave(player, recorder);
        RECORDERS.remove(player.getUUID());
        if (result instanceof FinishFailure) {
            OWRNetwork.sendSurveyRouteOverlay(player, false, "", new UUID(0, 0), "", false, null);
        }
        return Optional.of(result);
    }

    public static FinishResult finish(ServerPlayer player) {
        Recorder recorder = RECORDERS.get(player.getUUID());
        if (recorder == null) return new FinishFailure("no active survey recording");
        if (!recorder.recording) return new FinishFailure("survey is armed; cross the start/finish line first");
        FinishResult result = buildAndSave(player, recorder);
        if (result instanceof FinishSuccess) RECORDERS.remove(player.getUUID());
        return result;
    }

    private static FinishResult buildAndSave(ServerPlayer player, Recorder recorder) {
        SurveyRouteProcessor.Result result = SurveyRouteProcessor.build(UUID.randomUUID(), recorder.trackId, recorder.samples, SurveyRouteProcessor.DEFAULT_SPACING);
        if (result instanceof SurveyRouteProcessor.Failure failure) return new FinishFailure(failure.reason());
        SurveyRoute route = SurveyRoute.fromModel(((SurveyRouteProcessor.Success) result).route());
        TrackSurveyData.get((ServerLevel) player.level()).put(route);
        OWRNetwork.sendSurveyRouteOverlay(player, true, recorder.dimensionId, recorder.trackId, recorder.trackName, false, route);
        return new FinishSuccess(route);
    }

    public static boolean cancel(ServerPlayer player) {
        boolean removed = RECORDERS.remove(player.getUUID()) != null;
        if (removed) OWRNetwork.sendSurveyRouteOverlay(player, false, "", new UUID(0, 0), "", false, null);
        return removed;
    }

    public static Optional<RecorderView> recorder(ServerPlayer player) {
        Recorder recorder = RECORDERS.get(player.getUUID());
        return recorder == null ? Optional.empty() : Optional.of(new RecorderView(recorder.trackId, recorder.trackName, recorder.dimensionId, recorder.recording, List.copyOf(recorder.samples), recorder.distance, recorder.closureGap()));
    }

    public static void clearPlayer(UUID playerId) {
        RECORDERS.remove(playerId);
    }

    public static void clearAll() {
        RECORDERS.clear();
    }

    private static void sendOverlay(ServerPlayer player, Recorder recorder, SurveyRoute route) {
        OWRNetwork.sendSurveyRouteOverlay(player, true, recorder.dimensionId, recorder.trackId, recorder.trackName, recorder.recording,
            route == null ? SurveyRoute.fromModel(new SurveyRouteModel(UUID.randomUUID(), recorder.trackId, recorder.samples, List.of(), recorder.distance, SurveyRouteProcessor.DEFAULT_SPACING)) : route);
    }

    private static double carHeading(OpenwheelCarEntity car) {
        return Math.toRadians(car.getYRot() + 90.0F);
    }

    public record Status(UUID trackId, String trackName, boolean recording, int samples, double distance, double closureGap) {}
    public record RecorderView(UUID trackId, String trackName, String dimensionId, boolean recording, List<SurveyRouteModel.Sample> samples, double distance, double closureGap) {}
    public sealed interface FinishResult permits FinishSuccess, FinishFailure {}
    public record FinishSuccess(SurveyRoute route) implements FinishResult {}
    public record FinishFailure(String reason) implements FinishResult {}

    private static final class Recorder {
        private final UUID trackId;
        private final String trackName;
        private final String dimensionId;
        private final List<SurveyRouteModel.Sample> samples = new ArrayList<>();
        private boolean recording;
        private double distance;

        private Recorder(UUID trackId, String trackName, String dimensionId) {
            this.trackId = trackId;
            this.trackName = trackName;
            this.dimensionId = dimensionId;
        }

        private boolean add(Vec3 position, double heading) {
            SurveyRouteModel.Point point = new SurveyRouteModel.Point(position.x, position.y, position.z);
            if (!samples.isEmpty()) {
                double step = SurveyRouteProcessor.horizontalDistance(samples.getLast().position(), point);
                if (step < SAMPLE_DISTANCE || samples.size() >= SurveyRouteModel.MAX_POINTS) return false;
                distance += step;
            }
            samples.add(new SurveyRouteModel.Sample(point, heading));
            return true;
        }

        private double closureGap() {
            return samples.size() < 2 ? 0.0 : SurveyRouteProcessor.horizontalDistance(samples.getFirst().position(), samples.getLast().position());
        }

        private Status status() {
            return new Status(trackId, trackName, recording, samples.size(), distance, closureGap());
        }
    }
}
