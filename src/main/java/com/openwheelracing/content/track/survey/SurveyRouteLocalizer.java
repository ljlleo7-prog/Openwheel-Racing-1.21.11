package com.openwheelracing.content.track.survey;

import java.util.List;
import java.util.Optional;

public final class SurveyRouteLocalizer {
    private static final int LOCAL_WINDOW = 12;
    private static final double TRACKED_HORIZONTAL = 8.0;
    private static final double LOW_CONFIDENCE_HORIZONTAL = 18.0;
    private static final double MAX_HORIZONTAL = 25.0;
    private static final double TRACKED_VERTICAL = 3.0;
    private static final double LOW_CONFIDENCE_VERTICAL = 5.0;
    private static final double AMBIGUITY_SCORE_MARGIN = 4.0;
    private static final double AMBIGUITY_ROUTE_SEPARATION = 20.0;

    private SurveyRouteLocalizer() {
    }

    public static Result locate(SurveyRouteModel route, SurveyRouteModel.Point position, double headingRadians, State state) {
        List<SurveyRouteGeometry.Candidate> candidates = SurveyRouteGeometry.candidates(route, position, headingRadians, state.segmentIndex, LOCAL_WINDOW);
        SurveyRouteGeometry.Candidate best = candidates.isEmpty() ? null : candidates.getFirst();
        if (best == null || best.horizontalDistance() > MAX_HORIZONTAL || Math.abs(best.verticalDelta()) > LOW_CONFIDENCE_VERTICAL) {
            candidates = SurveyRouteGeometry.candidates(route, position, headingRadians, -1, route.nodes().size());
            best = candidates.isEmpty() ? null : candidates.getFirst();
        }
        if (best == null || best.horizontalDistance() > MAX_HORIZONTAL || Math.abs(best.verticalDelta()) > LOW_CONFIDENCE_VERTICAL) {
            return new Result(Status.UNTRACKED, Optional.ofNullable(best), Optional.empty(), 0.0, "outside route envelope");
        }

        SurveyRouteGeometry.Candidate second = distinctSecond(route, candidates, best);
        if (second != null && second.score() - best.score() <= AMBIGUITY_SCORE_MARGIN) {
            return new Result(Status.AMBIGUOUS, Optional.of(best), Optional.of(second), confidence(best), "multiple route positions match");
        }

        Status status = best.horizontalDistance() <= TRACKED_HORIZONTAL && Math.abs(best.verticalDelta()) <= TRACKED_VERTICAL
            ? Status.TRACKED
            : Status.LOW_CONFIDENCE;
        state.segmentIndex = best.segmentIndex();
        state.distanceAlongRoute = best.distanceAlongRoute();
        state.status = status;
        return new Result(status, Optional.of(best), Optional.ofNullable(second), confidence(best), status == Status.TRACKED ? "tracked" : "near route envelope");
    }

    private static SurveyRouteGeometry.Candidate distinctSecond(SurveyRouteModel route, List<SurveyRouteGeometry.Candidate> candidates, SurveyRouteGeometry.Candidate best) {
        for (SurveyRouteGeometry.Candidate candidate : candidates) {
            if (candidate == best) {
                continue;
            }
            double separation = Math.abs(candidate.distanceAlongRoute() - best.distanceAlongRoute());
            separation = Math.min(separation, route.length() - separation);
            if (separation >= AMBIGUITY_ROUTE_SEPARATION) {
                return candidate;
            }
        }
        return null;
    }

    private static double confidence(SurveyRouteGeometry.Candidate candidate) {
        double distanceConfidence = 1.0 - candidate.horizontalDistance() / LOW_CONFIDENCE_HORIZONTAL;
        double headingConfidence = 1.0 - candidate.headingDeltaRadians() / Math.PI;
        return Math.max(0.0, Math.min(1.0, distanceConfidence * 0.75 + headingConfidence * 0.25));
    }

    public enum Status {
        TRACKED,
        LOW_CONFIDENCE,
        AMBIGUOUS,
        UNTRACKED
    }

    public static final class State {
        private int segmentIndex = -1;
        private double distanceAlongRoute;
        private Status status = Status.UNTRACKED;

        public void reset() {
            segmentIndex = -1;
            distanceAlongRoute = 0.0;
            status = Status.UNTRACKED;
        }
    }

    public record Result(Status status, Optional<SurveyRouteGeometry.Candidate> best, Optional<SurveyRouteGeometry.Candidate> second,
                         double confidence, String reason) {
    }
}
