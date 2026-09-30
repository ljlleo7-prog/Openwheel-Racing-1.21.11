package com.openwheelracing.content.race;

import com.openwheelracing.content.track.survey.SurveyRoute;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

public final class LapTelemetryRecorder {
    private static final int SAMPLE_INTERVAL_TICKS = 5;
    private static final int INITIAL_CAPACITY = 128;

    private UUID driverId;
    private String driverName = "";
    private String dimensionId = "";
    private UUID trackId;
    private UUID routeId;
    private double routeLength;
    private double lapStartGameTime;
    private long lastSampleTick = Long.MIN_VALUE;
    private double lastRouteDistance = Double.NaN;
    private double coveredDistance;
    private int count;
    private int[] elapsed = new int[0];
    private int[] routeDistance = new int[0];
    private int[] covered = new int[0];
    private int[] speed = new int[0];
    private int[] worldX = new int[0];
    private int[] worldY = new int[0];
    private int[] worldZ = new int[0];
    private byte[] status = new byte[0];

    public void start(SurveyRoute route, UUID driverId, String driverName, String dimensionId, double lapStartGameTime) {
        reset();
        this.driverId = driverId;
        this.driverName = driverName == null ? "" : driverName;
        this.dimensionId = dimensionId == null ? "" : dimensionId;
        this.trackId = route.trackId();
        this.routeId = route.routeId();
        this.routeLength = route.length();
        this.lapStartGameTime = lapStartGameTime;
        allocate(INITIAL_CAPACITY);
    }

    public void sample(long serverTick, double gameTime, LapProfileCollector.Latest localized,
                       Vec3 position, double speedKmh) {
        if (driverId == null || localized == null || !localized.active()
            || lastSampleTick != Long.MIN_VALUE && serverTick - lastSampleTick < SAMPLE_INTERVAL_TICKS) {
            return;
        }
        lastSampleTick = serverTick;
        appendSample(gameTime, localized, position, speedKmh, false);
    }

    public void sampleFinish(double gameTime, LapProfileCollector.Latest localized, Vec3 position, double speedKmh) {
        if (driverId == null || localized == null || !localized.active()) return;
        appendSample(gameTime, localized, position, speedKmh, true);
    }

    private void appendSample(double gameTime, LapProfileCollector.Latest localized, Vec3 position,
                              double speedKmh, boolean replaceIfTimeMovesBackward) {
        double distance = wrap(localized.routeDistance(), routeLength);
        if (Double.isFinite(lastRouteDistance)) {
            double delta = distance - lastRouteDistance;
            if (delta < -routeLength * 0.5) delta += routeLength;
            if (delta > 0.0 && delta <= 80.0) coveredDistance += delta;
        }
        lastRouteDistance = distance;
        int elapsedMillis = Math.max(0, (int) Math.round((gameTime - lapStartGameTime) * 50.0));
        int index = count;
        if (replaceIfTimeMovesBackward && count > 0 && elapsedMillis < elapsed[count - 1]) index = count - 1;
        else {
            if (count >= LapTelemetryData.MAX_SAMPLES) decimate();
            ensureCapacity(count + 1);
            index = count;
            count++;
        }
        elapsed[index] = elapsedMillis;
        routeDistance[index] = toCentimeters(distance);
        covered[index] = toCentimeters(coveredDistance);
        speed[index] = Math.max(0, Math.min(65_535, (int) Math.round(speedKmh / 0.036)));
        worldX[index] = toCentimeters(position.x);
        worldY[index] = toCentimeters(position.y);
        worldZ[index] = toCentimeters(position.z);
        status[index] = (byte) localized.status().ordinal();
    }

    public Optional<Snapshot> finish(long lapId, int lapMillis) {
        if (driverId == null || count < 2) {
            reset();
            return Optional.empty();
        }
        Snapshot snapshot = new Snapshot(lapId, driverId, driverName, dimensionId, trackId, routeId, lapMillis, routeLength,
            Arrays.copyOf(elapsed, count), Arrays.copyOf(routeDistance, count), Arrays.copyOf(covered, count),
            Arrays.copyOf(speed, count), Arrays.copyOf(worldX, count), Arrays.copyOf(worldY, count),
            Arrays.copyOf(worldZ, count), Arrays.copyOf(status, count));
        reset();
        return Optional.of(snapshot);
    }

    public boolean active() {
        return driverId != null;
    }

    public int sampleCount() {
        return count;
    }

    public UUID driverId() {
        return driverId;
    }

    public String driverName() {
        return driverName;
    }

    public void reset() {
        driverId = null;
        driverName = "";
        dimensionId = "";
        trackId = null;
        routeId = null;
        routeLength = 0.0;
        lapStartGameTime = 0.0;
        lastSampleTick = Long.MIN_VALUE;
        lastRouteDistance = Double.NaN;
        coveredDistance = 0.0;
        count = 0;
        elapsed = routeDistance = covered = speed = worldX = worldY = worldZ = new int[0];
        status = new byte[0];
    }

    private void allocate(int capacity) {
        elapsed = new int[capacity];
        routeDistance = new int[capacity];
        covered = new int[capacity];
        speed = new int[capacity];
        worldX = new int[capacity];
        worldY = new int[capacity];
        worldZ = new int[capacity];
        status = new byte[capacity];
    }

    private void ensureCapacity(int required) {
        if (required <= elapsed.length) return;
        int next = Math.min(LapTelemetryData.MAX_SAMPLES, Math.max(required, elapsed.length * 2));
        elapsed = Arrays.copyOf(elapsed, next);
        routeDistance = Arrays.copyOf(routeDistance, next);
        covered = Arrays.copyOf(covered, next);
        speed = Arrays.copyOf(speed, next);
        worldX = Arrays.copyOf(worldX, next);
        worldY = Arrays.copyOf(worldY, next);
        worldZ = Arrays.copyOf(worldZ, next);
        status = Arrays.copyOf(status, next);
    }

    private void decimate() {
        int retained = 0;
        for (int source = 0; source < count; source += 2) {
            if (retained != source) {
                elapsed[retained] = elapsed[source];
                routeDistance[retained] = routeDistance[source];
                covered[retained] = covered[source];
                speed[retained] = speed[source];
                worldX[retained] = worldX[source];
                worldY[retained] = worldY[source];
                worldZ[retained] = worldZ[source];
                status[retained] = status[source];
            }
            retained++;
        }
        count = retained;
    }

    private static int toCentimeters(double meters) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(meters * 100.0)));
    }

    private static double wrap(double distance, double length) {
        if (!(length > 0.0)) return 0.0;
        double wrapped = distance % length;
        return wrapped < 0.0 ? wrapped + length : wrapped;
    }

    public record Snapshot(long lapId, UUID driverId, String driverName, String dimensionId, UUID trackId, UUID routeId,
                          int lapMillis, double routeLength, int[] elapsedMillis, int[] routeDistanceCm,
                          int[] coveredDistanceCm, int[] speedCmps, int[] worldXCm, int[] worldYCm,
                          int[] worldZCm, byte[] trackingStatus) {
        public Snapshot {
            elapsedMillis = elapsedMillis.clone();
            routeDistanceCm = routeDistanceCm.clone();
            coveredDistanceCm = coveredDistanceCm.clone();
            speedCmps = speedCmps.clone();
            worldXCm = worldXCm.clone();
            worldYCm = worldYCm.clone();
            worldZCm = worldZCm.clone();
            trackingStatus = trackingStatus.clone();
        }

        public LapTelemetryData toData() {
            return new LapTelemetryData(lapId, driverName, lapMillis, routeLength, elapsedMillis, routeDistanceCm,
                coveredDistanceCm, speedCmps, worldXCm, worldYCm, worldZCm, trackingStatus);
        }
    }
}
