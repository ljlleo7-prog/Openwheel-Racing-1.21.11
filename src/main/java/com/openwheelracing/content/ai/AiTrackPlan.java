package com.openwheelracing.content.ai;

import com.openwheelracing.content.track.TrackGeometry;
import com.openwheelracing.content.track.survey.SurveyRouteModel;

import java.util.List;
import java.util.UUID;

public record AiTrackPlan(UUID trackId, UUID routeId, double routeLength, double spacing,
                          List<AiTrackSample> samples, ReferenceSource referenceSource,
                          int referenceLapMillis, int predictedLapMillis, boolean degraded) {
    public AiTrackPlan {
        samples = List.copyOf(samples);
        if (samples.isEmpty() || !(routeLength > 0.0) || !(spacing > 0.0)) {
            throw new IllegalArgumentException("invalid track plan");
        }
    }

    public AiTrackSample sample(double routeDistance) {
        double wrapped = SurveyRouteSampler.wrapDistance(routeDistance, routeLength);
        double scaled = wrapped / spacing;
        int lower = Math.min(samples.size() - 1, (int) Math.floor(scaled));
        int upper = (lower + 1) % samples.size();
        double fraction = scaled - Math.floor(scaled);
        AiTrackSample a = samples.get(lower);
        AiTrackSample b = samples.get(upper);
        SurveyRouteModel.Point position = new SurveyRouteModel.Point(
            lerp(a.position().x(), b.position().x(), fraction),
            lerp(a.position().y(), b.position().y(), fraction),
            lerp(a.position().z(), b.position().z(), fraction));
        double tangent = TrackGeometry.wrapRadians(a.tangentRadians()
            + TrackGeometry.wrapRadians(b.tangentRadians() - a.tangentRadians()) * fraction);
        return new AiTrackSample(wrapped, position, tangent, lerp(a.curvature(), b.curvature(), fraction),
            lerp(a.lateralOffset(), b.lateralOffset(), fraction),
            lerp(a.targetSpeedMetersPerSecond(), b.targetSpeedMetersPerSecond(), fraction), referenceSource);
    }

    public double predictedReferenceRatio() {
        return referenceLapMillis > 0 ? predictedLapMillis / (double) referenceLapMillis : Double.NaN;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public enum ReferenceSource { PLAYER, SURVEY }
}
