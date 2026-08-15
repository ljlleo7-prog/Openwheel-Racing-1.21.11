package com.openwheelracing.content.ai;

import com.openwheelracing.content.track.survey.SurveyRouteLocalizer;

import java.util.UUID;

public record BasicAiStatus(
    UUID driverId,
    UUID fleetId,
    UUID trackId,
    int gridIndex,
    String displayName,
    int entityId,
    boolean running,
    SurveyRouteLocalizer.Status localizationStatus,
    double confidence,
    double routeDistance,
    int routeLaps,
    long elapsedTicks,
    double speedKmh,
    double nearestAheadGap,
    String reason
) {
    public static BasicAiStatus stopped(BasicAiDriverIdentity identity, int entityId, double speedKmh, String reason) {
        return new BasicAiStatus(identity.driverId(), identity.fleetId(), identity.trackId(), identity.gridIndex(), identity.displayName(), entityId, false,
            SurveyRouteLocalizer.Status.UNTRACKED, 0.0, 0.0, 0, 0L, speedKmh, Double.POSITIVE_INFINITY, reason);
    }
}
