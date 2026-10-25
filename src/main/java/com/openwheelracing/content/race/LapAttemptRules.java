package com.openwheelracing.content.race;

import com.openwheelracing.content.track.survey.SurveyRouteModel;
import com.openwheelracing.content.track.survey.SurveyRouteGeometry;

/** Timing eligibility must not depend on racing-line localization confidence. */
public final class LapAttemptRules {
    private LapAttemptRules() { }

    public static boolean withinRouteZone(SurveyRouteModel route,
                                          SurveyRouteModel.Point position) {
        for (int index = 0; index < route.nodes().size(); index++) {
            var candidate = SurveyRouteGeometry.project(route, position, 0.0, index);
            if (withinRouteZone(candidate.horizontalDistance(), candidate.verticalDelta())) return true;
        }
        return false;
    }

    public static boolean withinRouteZone(double horizontalDistance, double verticalDelta) {
        return Double.isFinite(horizontalDistance) && horizontalDistance >= 0.0
            && horizontalDistance <= 25.0 && Math.abs(verticalDelta) <= 5.0;
    }

    public static boolean shouldDiscard(int elapsedMillis, double traveledMeters) {
        return elapsedMillis < 200 || !Double.isFinite(traveledMeters) || traveledMeters < 10.0;
    }
}
