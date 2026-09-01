package com.openwheelracing.content.track.survey;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.race.PitLaneSpeedMath;
import com.openwheelracing.content.track.TrackDefinition;
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
        recorder.add(car.getX(), car.getY(), car.getZ());
        RECORDERS.put(player.getUUID(), recorder);
        return true;
    }

    public static void recordMovement(OpenwheelCarEntity car) {
        if (!(car.level() instanceof ServerLevel) || !(car.getControllingPassenger() instanceof ServerPlayer player)) return;
        Recorder recorder = RECORDERS.get(player.getUUID());
        if (recorder == null || !recorder.dimensionId.equals(player.level().dimension().identifier().toString())) return;
        recorder.add(car.getX(), car.getY(), car.getZ());
    }

    public static Finish finish(ServerPlayer player) {
        Recorder recorder = RECORDERS.get(player.getUUID());
        if (recorder == null) return new Finish(false, "no active pit-lane survey", 0);
        if (recorder.points.size() < 2) return new Finish(false, "pit-lane survey needs at least two samples", recorder.points.size());
        PitLaneSurveyData.get((ServerLevel) player.level()).put(new PitLaneSurveyData.Route(recorder.trackId, recorder.points));
        RECORDERS.remove(player.getUUID());
        return new Finish(true, "saved pit-lane projection route", recorder.points.size());
    }

    public static boolean cancel(ServerPlayer player) { return RECORDERS.remove(player.getUUID()) != null; }
    public static Optional<Status> status(ServerPlayer player) {
        Recorder recorder = RECORDERS.get(player.getUUID());
        return recorder == null ? Optional.empty() : Optional.of(new Status(recorder.trackName, recorder.points.size()));
    }

    public record Finish(boolean success, String message, int samples) {}
    public record Status(String trackName, int samples) {}

    private static final class Recorder {
        private final UUID trackId;
        private final String trackName;
        private final String dimensionId;
        private final List<PitLaneSpeedMath.Point> points = new ArrayList<>();

        private Recorder(UUID trackId, String trackName, String dimensionId) {
            this.trackId = trackId;
            this.trackName = trackName;
            this.dimensionId = dimensionId;
        }

        private void add(double x, double y, double z) {
            PitLaneSpeedMath.Point point = new PitLaneSpeedMath.Point(x, y, z);
            if (!points.isEmpty()) {
                PitLaneSpeedMath.Point previous = points.getLast();
                if (Math.hypot(x - previous.x(), z - previous.z()) < SAMPLE_DISTANCE) return;
            }
            points.add(point);
        }
    }
}
