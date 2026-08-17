package com.openwheelracing.content.ai;

import com.openwheelracing.content.race.OWRLapProfiles;
import com.openwheelracing.content.track.TrackGeometry;
import com.openwheelracing.content.track.survey.SurveyRouteModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class AiTrackPlanCompiler {
    public static final double SPACING_METERS = 2.0;
    private static final double MAX_OFFSET = 2.0;
    private static final double HUMAN_OFFSET_SHARE = 0.80;
    private static final double MAX_OFFSET_CHANGE_PER_SAMPLE = 0.30;
    private static final double GRIP_UTILIZATION = 0.82;
    // The capability model is an instantaneous peak. Reserve margin for combined slip,
    // brake build-up, surface sampling differences, and controller latency.
    private static final double BRAKING_UTILIZATION = 0.52;

    private AiTrackPlanCompiler() {}

    public static Optional<OWRLapProfiles.BestLapProfile> fastestValidPlayerProfile(
        List<OWRLapProfiles.BestLapProfile> profiles, SurveyRouteModel route) {
        return profiles.stream()
            .filter(profile -> profile.origin() == OWRLapProfiles.Origin.PLAYER)
            .filter(profile -> profile.trackId().equals(route.trackId()) && profile.routeId().equals(route.routeId()))
            .filter(profile -> profile.lapMillis() > 0 && profile.routeLength() > 0.0)
            .filter(profile -> Math.abs(profile.routeLength() - route.length()) <= Math.max(2.0, route.length() * 0.01))
            .min(Comparator.comparingInt(OWRLapProfiles.BestLapProfile::lapMillis));
    }

    public static AiTrackPlan compile(SurveyRouteModel route, List<OWRLapProfiles.BestLapProfile> profiles,
                                      BasicAiGripModel.State capability) {
        OWRLapProfiles.BestLapProfile profile = fastestValidPlayerProfile(profiles, route).orElse(null);
        int count = Math.max(3, (int) Math.ceil(route.length() / SPACING_METERS));
        double spacing = route.length() / count;
        double[] offsets = new double[count];
        for (int i = 0; i < count; i++) {
            double distance = i * spacing;
            double candidate = profile == null ? 0.0 : profile.lateralOffsetMeters(distance) * HUMAN_OFFSET_SHARE;
            offsets[i] = Double.isFinite(candidate) ? clamp(candidate, -MAX_OFFSET, MAX_OFFSET) : 0.0;
        }
        smoothCircular(offsets);

        SurveyRouteModel.Point[] positions = new SurveyRouteModel.Point[count];
        for (int i = 0; i < count; i++) {
            SurveyRouteSampler.Sample survey = SurveyRouteSampler.sample(route, i * spacing);
            double offset = offsets[i];
            positions[i] = new SurveyRouteModel.Point(
                survey.position().x() - Math.sin(survey.headingRadians()) * offset,
                survey.position().y(),
                survey.position().z() + Math.cos(survey.headingRadians()) * offset);
        }
        double[] tangent = new double[count];
        double[] curvature = new double[count];
        for (int i = 0; i < count; i++) {
            SurveyRouteModel.Point before = positions[Math.floorMod(i - 1, count)];
            SurveyRouteModel.Point after = positions[(i + 1) % count];
            tangent[i] = Math.atan2(after.z() - before.z(), after.x() - before.x());
        }
        for (int i = 0; i < count; i++) {
            curvature[i] = TrackGeometry.wrapRadians(tangent[(i + 1) % count] - tangent[Math.floorMod(i - 1, count)]) / (2.0 * spacing);
        }

        double[] speed = new double[count];
        for (int i = 0; i < count; i++) {
            double human = profile == null ? BasicAiCarController.MAX_TARGET_SPEED_MPS : profile.speedKmh(i * spacing) / 3.6 / 1.20;
            if (!Double.isFinite(human) || human <= 0.0) human = BasicAiCarController.MIN_TARGET_SPEED_MPS;
            double lateralLimit = Math.abs(curvature[i]) < 1.0E-6 ? BasicAiCarController.MAX_TARGET_SPEED_MPS
                : Math.sqrt(Math.max(0.0, capability.lateralAcceleration(human) * GRIP_UTILIZATION / Math.abs(curvature[i])));
            speed[i] = clamp(Math.min(human, lateralLimit), BasicAiCarController.MIN_TARGET_SPEED_MPS, BasicAiCarController.MAX_TARGET_SPEED_MPS);
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int step = count - 1; step >= 0; step--) {
                int next = (step + 1) % count;
                double allowed = Math.sqrt(speed[next] * speed[next] + 2.0 * capability.brakeAcceleration(speed[next]) * BRAKING_UTILIZATION * spacing);
                speed[step] = Math.min(speed[step], allowed);
            }
            for (int step = 0; step < count; step++) {
                int next = (step + 1) % count;
                double allowed = Math.sqrt(speed[step] * speed[step] + 2.0 * capability.driveAcceleration(speed[step]) * GRIP_UTILIZATION * spacing);
                speed[next] = Math.min(speed[next], allowed);
            }
        }

        List<AiTrackSample> samples = new ArrayList<>(count);
        AiTrackPlan.ReferenceSource source = profile == null ? AiTrackPlan.ReferenceSource.SURVEY : AiTrackPlan.ReferenceSource.PLAYER;
        double seconds = 0.0;
        for (int i = 0; i < count; i++) {
            samples.add(new AiTrackSample(i * spacing, positions[i], tangent[i], curvature[i], offsets[i], speed[i], source));
            seconds += spacing / Math.max(1.0, speed[i]);
        }
        int predictedMillis = (int) Math.round(seconds * 1000.0);
        int referenceMillis = profile == null ? 0 : profile.lapMillis();
        return new AiTrackPlan(route.trackId(), route.routeId(), route.length(), spacing, samples, source,
            referenceMillis, predictedMillis, referenceMillis > 0 && predictedMillis > referenceMillis * 1.25);
    }

    private static void smoothCircular(double[] values) {
        double[] copy = values.clone();
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < values.length; i++) {
                values[i] = (copy[Math.floorMod(i - 1, values.length)] + copy[i] * 2.0 + copy[(i + 1) % values.length]) * 0.25;
            }
            copy = values.clone();
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 1; i < values.length; i++) values[i] = clamp(values[i], values[i - 1] - MAX_OFFSET_CHANGE_PER_SAMPLE, values[i - 1] + MAX_OFFSET_CHANGE_PER_SAMPLE);
            values[0] = clamp(values[0], values[values.length - 1] - MAX_OFFSET_CHANGE_PER_SAMPLE, values[values.length - 1] + MAX_OFFSET_CHANGE_PER_SAMPLE);
        }
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
