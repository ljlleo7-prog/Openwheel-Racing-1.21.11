package com.openwheelracing.content.race;

import com.openwheelracing.content.track.survey.SurveyRoute;
import com.openwheelracing.content.track.survey.SurveyRouteLocalizer;
import com.openwheelracing.content.track.survey.SurveyRouteModel;

import java.util.Arrays;
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
    private boolean[] filled = new boolean[0];
    private final SurveyRouteLocalizer.State localizerState = new SurveyRouteLocalizer.State();
    private SurveyRouteLocalizer.Result previous;
    private Latest latest = Latest.inactive();

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
        filled = new boolean[count];
        timeMillis[0] = 0;
        filled[0] = true;
    }

    public void sample(SurveyRouteModel.Point position, double headingRadians, double gameTime, double speedKmh) {
        if (route == null) return;
        SurveyRouteLocalizer.Result current = SurveyRouteLocalizer.locate(model, position, headingRadians, localizerState);
        int elapsed = elapsedMillis(gameTime);
        if (current.best().isEmpty() || current.status() == SurveyRouteLocalizer.Status.AMBIGUOUS || current.status() == SurveyRouteLocalizer.Status.UNTRACKED) {
            latest = new Latest(true, current.status(), elapsed, previous != null && previous.best().isPresent() ? previous.best().get().distanceAlongRoute() : 0.0);
            return;
        }
        double distance = current.best().get().distanceAlongRoute();
        latest = new Latest(true, current.status(), elapsed, distance);
        if (previous == null || previous.best().isEmpty()) {
            previous = current;
            return;
        }
        double start = previous.best().get().distanceAlongRoute();
        double delta = distance - start;
        if (delta < -route.length() * 0.5) delta += route.length();
        if (delta <= 0.0 || delta > MAX_FORWARD_STEP) {
            previous = current;
            return;
        }
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
            filled[index] = true;
        }
        previous = current;
    }

    public OWRLapProfiles.BestLapProfile finish(String dimensionId, UUID trackId, String driverName, long lapRecordId, int lapMillis, long gameTime) {
        if (route == null || coverage() < MIN_COVERAGE) return null;
        int last = timeMillis.length - 1;
        if (!filled[last]) return null;
        return new OWRLapProfiles.BestLapProfile(dimensionId, trackId, route.routeId(), driverId, driverName, lapRecordId, lapMillis,
            route.length(), spacing, timeMillis, speedCmps, gameTime);
    }

    public Latest latest() { return latest; }
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
        filled = new boolean[0];
        previous = null;
        latest = Latest.inactive();
        localizerState.reset();
    }

    private int elapsedMillis(double gameTime) { return Math.max(0, (int) Math.round((gameTime - lapStartGameTime) * 50.0)); }
    private int timeMillisAtPrevious(double gameTime, int elapsed) { return Math.max(0, elapsed - 50); }

    public record Latest(boolean active, SurveyRouteLocalizer.Status status, int elapsedMillis, double routeDistance) {
        static Latest inactive() { return new Latest(false, SurveyRouteLocalizer.Status.UNTRACKED, 0, 0.0); }
    }
}
