package com.openwheelracing.content.race;

import java.util.Optional;

public final class SurveyRouteLapRepair {
    private static final double MIN_LAP_COVERAGE = 0.80;

    private SurveyRouteLapRepair() {
    }

    public static Optional<Candidate> detect(double previousDistance, double previousGameTime,
                                             double currentDistance, double currentGameTime,
                                             double routeLength, double coveredDistance,
                                             double maximumRoundTripDistance) {
        if (!(routeLength > 0.0) || !(currentGameTime > previousGameTime) || currentGameTime - previousGameTime > 1.5
            || coveredDistance < routeLength * MIN_LAP_COVERAGE
            || previousDistance - currentDistance <= routeLength * 0.5) {
            return Optional.empty();
        }
        double distanceToFinish = routeLength - previousDistance;
        double roundTripDistance = distanceToFinish + currentDistance;
        if (!(roundTripDistance > 0.0) || roundTripDistance > maximumRoundTripDistance) {
            return Optional.empty();
        }
        double fraction = distanceToFinish / roundTripDistance;
        double crossingGameTime = previousGameTime + (currentGameTime - previousGameTime) * fraction;
        return Optional.of(new Candidate(crossingGameTime, currentGameTime, roundTripDistance));
    }

    public record Candidate(double estimatedCrossingGameTime, double detectedGameTime, double roundTripDistance) {
    }
}
