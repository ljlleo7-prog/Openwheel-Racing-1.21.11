package com.openwheelracing.content.ai;

import com.openwheelracing.content.track.survey.SurveyRouteModel;

public final class BasicAiSpeedPlanner {
    private static final double[] PREVIEW_DISTANCES = {8.0, 16.0, 28.0, 42.0, 60.0, 82.0, 108.0, 140.0};
    private static final double CURVATURE_WINDOW = 8.0;
    private static final double MAX_SPEED = 95.0;
    private static final double SPEED_STEP = 2.5;

    private BasicAiSpeedPlanner() {
    }

    public static double targetSpeed(SurveyRouteModel route, double routeDistance, BasicAiGripModel.State grip,
                                     BasicAiTrafficMode mode) {
        return targetSpeed(route, routeDistance, grip, mode, 1.0);
    }

    public static double targetSpeed(SurveyRouteModel route, double routeDistance, BasicAiGripModel.State grip,
                                     BasicAiTrafficMode mode, double brakingScale) {
        if (mode == BasicAiTrafficMode.HOLD) {
            return 0.0;
        }
        double target = Math.min(MAX_SPEED, mode.speedCapMetersPerSecond());
        for (double distance : PREVIEW_DISTANCES) {
            double curvature = SurveyRouteSampler.curvature(route, routeDistance + distance, CURVATURE_WINDOW);
            if (curvature < 1.0E-5) {
                continue;
            }
            double cornerSpeed = cornerSpeed(curvature, grip, mode.gripUtilization());
            double approachBrake = grip.brakeAcceleration(Math.max(cornerSpeed, target)) * mode.gripUtilization() * 0.85 * clamp(brakingScale, 0.75, 1.50);
            double entrySpeed = Math.sqrt(Math.max(0.0, cornerSpeed * cornerSpeed + 2.0 * approachBrake * distance));
            target = Math.min(target, entrySpeed);
        }
        return Math.max(0.0, target);
    }

    public static double applyIncidentLimit(double targetSpeed, double routeDistance, double routeLength,
                                            java.util.List<OWRAiTrainingData.Incident> incidents) {
        double limited = targetSpeed;
        for (OWRAiTrainingData.Incident incident : incidents) {
            double ahead = SurveyRouteSampler.forwardDelta(routeDistance, incident.routeDistance(), routeLength);
            if (ahead > 80.0) continue;
            double severity = Math.min(1.0, incident.count() / 4.0);
            double incidentSpeed = 22.0 - severity * 10.0;
            double approachAllowance = incidentSpeed + ahead * (0.35 + severity * 0.10);
            limited = Math.min(limited, approachAllowance);
        }
        return Math.max(0.0, limited);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    static double cornerSpeed(double curvature, BasicAiGripModel.State grip, double utilization) {
        for (double speed = MAX_SPEED; speed >= 0.0; speed -= SPEED_STEP) {
            double required = speed * speed * curvature;
            if (required <= grip.lateralAcceleration(speed) * utilization) {
                return speed;
            }
        }
        return 0.0;
    }
}
