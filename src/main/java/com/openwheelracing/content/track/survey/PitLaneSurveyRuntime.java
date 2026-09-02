package com.openwheelracing.content.track.survey;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.race.PitLaneSpeedMath;
import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.network.OWRNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PitLaneSurveyRuntime {
    private static final double SAMPLE_DISTANCE = 0.75;
    private static final Map<UUID, Recorder> RECORDERS = new HashMap<>();

    private PitLaneSurveyRuntime() {
    }

    public static boolean start(ServerPlayer player, OpenwheelCarEntity car, TrackDefinition track) {
        if (RECORDERS.containsKey(player.getUUID())) return false;
        Recorder recorder = new Recorder(track.trackId(), track.name(), player.level().dimension().identifier().toString());
        RECORDERS.put(player.getUUID(), recorder);
        sendOverlay(player, recorder, false);
        return true;
    }

    public static void onEntry(OpenwheelCarEntity car) {
        if (!(car.getControllingPassenger() instanceof ServerPlayer player)) return;
        Recorder recorder = RECORDERS.get(player.getUUID());
        if (recorder == null || recorder.recording) return;
        recorder.recording = true;
        recorder.add(car.getX(), car.getY(), car.getZ());
        sendOverlay(player, recorder, true);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("Pit-lane survey recording started at entry"), true);
    }

    public static void onExit(OpenwheelCarEntity car) {
        if (!(car.getControllingPassenger() instanceof ServerPlayer player)) return;
        Recorder recorder = RECORDERS.get(player.getUUID());
        if (recorder == null || !recorder.recording) return;
        recorder.add(car.getX(), car.getY(), car.getZ());
        Finish finish = save(player, recorder);
        if (finish.success()) RECORDERS.remove(player.getUUID());
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("Pit-lane survey " + finish.message()), true);
    }

    public static void recordMovement(OpenwheelCarEntity car) {
        if (!(car.level() instanceof ServerLevel) || !(car.getControllingPassenger() instanceof ServerPlayer player)) return;
        Recorder recorder = RECORDERS.get(player.getUUID());
        if (recorder == null || !recorder.recording || !recorder.dimensionId.equals(player.level().dimension().identifier().toString())) return;
        if (recorder.add(car.getX(), car.getY(), car.getZ()) && recorder.points.size() % 10 == 0) sendOverlay(player, recorder, true);
    }

    public static Finish finish(ServerPlayer player) {
        Recorder recorder = RECORDERS.get(player.getUUID());
        if (recorder == null) return new Finish(false, "no active pit-lane survey", 0);
        if (!recorder.recording) return new Finish(false, "armed; cross the pit entry line first", 0);
        Finish finish = save(player, recorder);
        if (finish.success()) RECORDERS.remove(player.getUUID());
        return finish;
    }

    public static boolean cancel(ServerPlayer player) { return RECORDERS.remove(player.getUUID()) != null; }
    public static Optional<Status> status(ServerPlayer player) {
        Recorder recorder = RECORDERS.get(player.getUUID());
        return recorder == null ? Optional.empty() : Optional.of(new Status(recorder.trackName, recorder.recording, recorder.points.size()));
    }

    public record Finish(boolean success, String message, int samples) {}
    public record Status(String trackName, boolean recording, int samples) {}

    private static Finish save(ServerPlayer player, Recorder recorder) {
        if (recorder.points.size() < 2) return new Finish(false, "needs at least two samples", recorder.points.size());
        PitLaneSurveyData.Route route = new PitLaneSurveyData.Route(recorder.trackId, recorder.points);
        PitLaneSurveyData.get((ServerLevel) player.level()).put(route);
        sendSavedOverlay(player, recorder, route);
        return new Finish(true, "saved automatically at exit", recorder.points.size());
    }

    private static void sendOverlay(ServerPlayer player, Recorder recorder, boolean recording) {
        List<SurveyRouteModel.Sample> samples = recorder.points.stream()
            .map(point -> new SurveyRouteModel.Sample(new SurveyRouteModel.Point(point.x(), point.y(), point.z()), 0.0)).toList();
        SurveyRoute route = SurveyRoute.fromModel(new SurveyRouteModel(UUID.randomUUID(), recorder.trackId, samples, List.of(), recorder.distance, 0.75));
        OWRNetwork.sendSurveyRouteOverlay(player, true, recorder.dimensionId, recorder.trackId, "PIT " + recorder.trackName, recording, route);
    }

    private static void sendSavedOverlay(ServerPlayer player, Recorder recorder, PitLaneSurveyData.Route saved) {
        List<SurveyRouteModel.Sample> samples = saved.points().stream()
            .map(point -> new SurveyRouteModel.Sample(new SurveyRouteModel.Point(point.x(), point.y(), point.z()), 0.0)).toList();
        double distance = 0.0;
        for (int index = 1; index < saved.points().size(); index++) {
            PitLaneSpeedMath.Point previous = saved.points().get(index - 1), current = saved.points().get(index);
            distance += Math.hypot(current.x() - previous.x(), current.z() - previous.z());
        }
        SurveyRoute route = SurveyRoute.fromModel(new SurveyRouteModel(UUID.randomUUID(), recorder.trackId, samples, List.of(), distance, 0.75));
        OWRNetwork.sendSurveyRouteOverlay(player, true, recorder.dimensionId, recorder.trackId, "PIT " + recorder.trackName, false, route);
    }

    private static final class Recorder {
        private final UUID trackId;
        private final String trackName;
        private final String dimensionId;
        private final List<PitLaneSpeedMath.Point> points = new ArrayList<>();
        private boolean recording;
        private double distance;

        private Recorder(UUID trackId, String trackName, String dimensionId) {
            this.trackId = trackId;
            this.trackName = trackName;
            this.dimensionId = dimensionId;
        }

        private boolean add(double x, double y, double z) {
            PitLaneSpeedMath.Point point = new PitLaneSpeedMath.Point(x, y, z);
            if (!points.isEmpty()) {
                PitLaneSpeedMath.Point previous = points.getLast();
                if (Math.hypot(x - previous.x(), z - previous.z()) < SAMPLE_DISTANCE) return false;
                distance += Math.hypot(x - previous.x(), z - previous.z());
            }
            points.add(point);
            return true;
        }
    }
}
