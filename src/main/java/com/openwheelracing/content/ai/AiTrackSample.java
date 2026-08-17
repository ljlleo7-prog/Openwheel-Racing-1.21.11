package com.openwheelracing.content.ai;

import com.openwheelracing.content.track.survey.SurveyRouteModel;

public record AiTrackSample(double routeDistance, SurveyRouteModel.Point position, double tangentRadians,
                            double curvature, double lateralOffset, double targetSpeedMetersPerSecond,
                            AiTrackPlan.ReferenceSource referenceSource) {
}
