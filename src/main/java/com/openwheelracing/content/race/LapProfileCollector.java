package com.openwheelracing.content.race;

import com.openwheelracing.content.track.survey.SurveyRoute;
import com.openwheelracing.content.track.survey.SurveyRouteLocalizer;
import com.openwheelracing.content.track.survey.SurveyRouteModel;
import com.openwheelracing.content.ai.BasicAiRacingLineProfile;
import com.openwheelracing.content.ai.SurveyRouteSampler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class LapProfileCollector {
    public static final double DEFAULT_SPACING = 4.0;
    private static final double MIN_COVERAGE = 0.97;
    private static final double MAX_FORWARD_STEP = 80.0;

    private SurveyRoute route;
    private SurveyRouteModel model;
    private UUID driverId;
    private double lapStartGameTime;
    private double spacing;
    private int[] timeMillis = new int[0];
    private int[] speedCmps = new int[0];
    private int[] lateralOffsetCm = new int[0];
    private int[] headingResidualMilliRad = new int[0];
    private boolean[] filled = new boolean[0];
    private final SurveyRouteLocalizer.State localizerState = new SurveyRouteLocalizer.State();
    private SurveyRouteLocalizer.Result previous;
    private double previousSampleGameTime = Double.NaN;
    private SurveyRouteLapRepair.Candidate pendingLapRepair;
    private Latest latest = Latest.inactive();
    private double prefixStartDistance = Double.NaN;
    private double unwrappedProgress;
    private final List<TracePoint> actualTrace = new ArrayList<>();

    public void start(SurveyRoute route, UUID driverId, double lapStartGameTime) {
        reset();
        this.route = route;
        this.model = route.toModel();
        this.driverId = driverId;
        this.lapStartGameTime = lapStartGameTime;
        spacing = Math.max(DEFAULT_SPACING, route.length() / OWRLapProfiles.MAX_PROFILE_SAMPLES);
        int count = Math.max(1, Math.min(OWRLapProfiles.MAX_PROFILE_SAMPLES, (int) Math.ceil(route.length() / spacing)));
        timeMillis = new int[count];
        speedCmps = new int[count];
        lateralOffsetCm = new int[count];
        headingResidualMilliRad = new int[count];
        filled = new boolean[count];
    }

    public void sample(SurveyRouteModel.Point position, double headingRadians, double gameTime, double speedKmh) {
        if (route == null) return;
        SurveyRouteLocalizer.Result current = SurveyRouteLocalizer.locate(model, position, headingRadians, localizerState);
        int elapsed = elapsedMillis(gameTime);
        appendActualTrace(position);
        if (current.best().isEmpty() || current.status() == SurveyRouteLocalizer.Status.AMBIGUOUS || current.status() == SurveyRouteLocalizer.Status.UNTRACKED) {
            latest = new Latest(true, current.status(), elapsed, previous != null && previous.best().isPresent() ? previous.best().get().distanceAlongRoute() : 0.0);
            return;
        }
        double distance = current.best().get().distanceAlongRoute();
        latest = new Latest(true, current.status(), elapsed, distance);
        if (previous == null || previous.best().isEmpty()) {
            prefixStartDistance = distance;
            unwrappedProgress = 0.0;
            int startIndex = Math.floorMod((int) Math.floor(distance / spacing), filled.length);
            timeMillis[startIndex] = elapsed;
            speedCmps[startIndex] = Math.max(0, Math.min(65535, (int) Math.round(speedKmh / 0.036)));
            lateralOffsetCm[startIndex] = clampSigned((int) Math.round(current.best().orElseThrow().signedLateralDistance() * 100.0), 250);
            double routeHeading = SurveyRouteSampler.sample(model, distance).headingRadians();
            headingResidualMilliRad[startIndex] = clampSigned((int) Math.round(wrapRadians(headingRadians - routeHeading) * 1000.0), 700);
            filled[startIndex] = true;
            previous = current;
            previousSampleGameTime = gameTime;
            return;
        }
        double start = previous.best().get().distanceAlongRoute();
        double delta = distance - start;
        boolean wrapped = delta < -route.length() * 0.5;
        if (wrapped) delta += route.length();
        if (delta <= 0.0 || delta > MAX_FORWARD_STEP) {
            previous = current;
            previousSampleGameTime = gameTime;
            return;
        }
        if (wrapped && Double.isFinite(previousSampleGameTime)) {
            double maximumRoundTripDistance = Math.min(MAX_FORWARD_STEP, Math.max(3.5, speedKmh / 72.0 * 2.0 + 1.0));
            SurveyRouteLapRepair.detect(start, previousSampleGameTime, distance, gameTime, route.length(), unwrappedProgress,
                maximumRoundTripDistance).ifPresent(candidate -> pendingLapRepair = candidate);
        }
        unwrappedProgress += delta;
        int startElapsed = timeMillisAtPrevious(gameTime, elapsed);
        int firstIndex = (int) Math.floor(start / spacing) + 1;
        int lastIndex = (int) Math.floor((start + delta) / spacing);
        for (int unwrappedIndex = firstIndex; unwrappedIndex <= lastIndex; unwrappedIndex++) {
            int index = Math.floorMod(unwrappedIndex, timeMillis.length);
            double sampleDistance = unwrappedIndex * spacing;
            double fraction = (sampleDistance - start) / delta;
            if (fraction < 0.0 || fraction > 1.0) continue;
            timeMillis[index] = (int) Math.round(startElapsed + (elapsed - startElapsed) * fraction);
            speedCmps[index] = Math.max(0, Math.min(65535, (int) Math.round(speedKmh / 0.036)));
            lateralOffsetCm[index] = clampSigned((int) Math.round(current.best().orElseThrow().signedLateralDistance() * 100.0), 250);
            double routeHeading = SurveyRouteSampler.sample(model, sampleDistance).headingRadians();
            headingResidualMilliRad[index] = clampSigned((int) Math.round(wrapRadians(headingRadians - routeHeading) * 1000.0), 700);
            filled[index] = true;
        }
        previous = current;
        previousSampleGameTime = gameTime;
    }

    public OWRLapProfiles.BestLapProfile finish(String dimensionId, UUID trackId, String driverName, long lapRecordId, int lapMillis, long gameTime) {
        return finish(dimensionId, trackId, driverName, OWRLapProfiles.Origin.PLAYER, lapRecordId, 0L, lapMillis, gameTime);
    }

    public OWRLapProfiles.BestLapProfile finish(String dimensionId, UUID trackId, String driverName, OWRLapProfiles.Origin origin,
                                                long lapRecordId, int lapMillis, long gameTime) {
        return finish(dimensionId, trackId, driverName, origin, lapRecordId, 0L, lapMillis, gameTime);
    }

    public OWRLapProfiles.BestLapProfile finish(String dimensionId, UUID trackId, String driverName, OWRLapProfiles.Origin origin,
                                                long lapRecordId, long sessionId, int lapMillis, long gameTime) {
        if (route == null || coverage() < MIN_COVERAGE) return null;
        fillSmallGaps();
        return new OWRLapProfiles.BestLapProfile(dimensionId, trackId, route.routeId(), driverId, driverName, origin, lapRecordId, sessionId, lapMillis,
            route.length(), spacing, timeMillis, speedCmps, lateralOffsetCm, headingResidualMilliRad, gameTime);
    }

    private void fillSmallGaps() {
        if (filled.length == 0) return;
        for (int index = 0; index < filled.length; index++) {
            if (filled[index]) continue;
            int before = index;
            int after = index;
            do before = Math.floorMod(before - 1, filled.length); while (!filled[before] && before != index);
            do after = (after + 1) % filled.length; while (!filled[after] && after != index);
            int source = filled[before] ? before : after;
            timeMillis[index] = timeMillis[source];
            speedCmps[index] = speedCmps[source];
            lateralOffsetCm[index] = lateralOffsetCm[source];
            headingResidualMilliRad[index] = headingResidualMilliRad[source];
            filled[index] = true;
        }
    }

    public PrefixSnapshot safePrefix() {
        if (route == null || filled.length == 0 || !Double.isFinite(prefixStartDistance)) return PrefixSnapshot.empty();
        int startIndex = Math.floorMod((int) Math.floor(prefixStartDistance / spacing), filled.length);
        int maximumSamples = Math.min(filled.length, Math.max(0, (int) Math.floor(unwrappedProgress / spacing) - 2));
        int end = 0;
        double speedTotal = 0.0;
        int speedCount = 0;
        int lastOffset = 0;
        int lastHeading = 0;
        int[] offsets = new int[maximumSamples];
        int[] headings = new int[maximumSamples];
        int[] observed = new int[maximumSamples];
        for (int relative = 0; relative < maximumSamples; relative++) {
            int index = Math.floorMod(startIndex + relative, filled.length);
            if (filled[index]) {
                int candidateOffset = lateralOffsetCm[index];
                int candidateHeading = headingResidualMilliRad[index];
                if (Math.abs(candidateOffset) <= BasicAiRacingLineProfile.MAX_OFFSET_METERS * 100.0) {
                    lastOffset = candidateOffset;
                    lastHeading = candidateHeading;
                } else {
                    lastOffset = 0;
                    lastHeading = 0;
                }
                speedTotal += speedCmps[index] * 0.036;
                speedCount++;
                observed[end] = 1;
            }
            offsets[end] = lastOffset;
            headings[end] = lastHeading;
            end++;
        }
        if (end < 3) return PrefixSnapshot.empty();
        return new PrefixSnapshot(prefixStartDistance, (end - 1) * spacing, spacing, Arrays.copyOf(offsets, end),
            Arrays.copyOf(headings, end), Arrays.copyOf(observed, end), speedCount == 0 ? 0.0 : speedTotal / speedCount);
    }

    public List<TracePoint> tracePoints() {
        return List.copyOf(actualTrace);
    }

    public Latest latest() { return latest; }
    public java.util.Optional<SurveyRouteLapRepair.Candidate> pollLapRepair() {
        SurveyRouteLapRepair.Candidate candidate = pendingLapRepair;
        pendingLapRepair = null;
        return java.util.Optional.ofNullable(candidate);
    }
    public SurveyRoute route() { return route; }
    public double coverage() {
        if (filled.length == 0) return 0.0;
        int count = 0;
        for (boolean value : filled) if (value) count++;
        return count / (double) filled.length;
    }

    public void reset() {
        route = null;
        model = null;
        driverId = null;
        timeMillis = new int[0];
        speedCmps = new int[0];
        lateralOffsetCm = new int[0];
        headingResidualMilliRad = new int[0];
        filled = new boolean[0];
        previous = null;
        previousSampleGameTime = Double.NaN;
        pendingLapRepair = null;
        latest = Latest.inactive();
        prefixStartDistance = Double.NaN;
        unwrappedProgress = 0.0;
        actualTrace.clear();
        localizerState.reset();
    }

    private void appendActualTrace(SurveyRouteModel.Point position) {
        if (!actualTrace.isEmpty()) {
            TracePoint previous = actualTrace.getLast();
            double dx = position.x() - previous.x();
            double dz = position.z() - previous.z();
            if (dx * dx + dz * dz < 0.01) return;
        }
        actualTrace.add(new TracePoint(position.x(), position.y(), position.z()));
        if (actualTrace.size() > 2048) actualTrace.removeFirst();
    }

    private static int clampSigned(int value, int limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    private static double wrapRadians(double radians) {
        double wrapped = radians;
        while (wrapped <= -Math.PI) wrapped += Math.PI * 2.0;
        while (wrapped > Math.PI) wrapped -= Math.PI * 2.0;
        return wrapped;
    }

    private int elapsedMillis(double gameTime) { return Math.max(0, (int) Math.round((gameTime - lapStartGameTime) * 50.0)); }
    private int timeMillisAtPrevious(double gameTime, int elapsed) { return Math.max(0, elapsed - 50); }

    public record PrefixSnapshot(double startDistance, double distance, double spacing, int[] lateralOffsetCm, int[] headingResidualMilliRad,
                                 int[] observed, double averageSpeedKmh) {
        public PrefixSnapshot {
            lateralOffsetCm = lateralOffsetCm.clone();
            headingResidualMilliRad = headingResidualMilliRad.clone();
            observed = observed.clone();
        }
        static PrefixSnapshot empty() { return new PrefixSnapshot(0.0, 0.0, 0.0, new int[0], new int[0], new int[0], 0.0); }
    }

    public record TracePoint(double x, double y, double z) {
    }

    public record Latest(boolean active, SurveyRouteLocalizer.Status status, int elapsedMillis, double routeDistance) {
        static Latest inactive() { return new Latest(false, SurveyRouteLocalizer.Status.UNTRACKED, 0, 0.0); }
    }
}
