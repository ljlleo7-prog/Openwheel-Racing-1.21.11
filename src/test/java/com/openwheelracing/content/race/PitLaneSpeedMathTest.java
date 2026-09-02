package com.openwheelracing.content.race;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PitLaneSpeedMathTest {
    private static final List<PitLaneSpeedMath.Point> ROUTE = List.of(
        new PitLaneSpeedMath.Point(0.0, 0.0, 0.0),
        new PitLaneSpeedMath.Point(10.0, 0.0, 0.0),
        new PitLaneSpeedMath.Point(10.0, 0.0, 10.0)
    );

    @Test
    void projectsToNearestSegmentAndReportsDistanceAlongRoute() {
        PitLaneSpeedMath.Projection projection = PitLaneSpeedMath.project(ROUTE,
            new PitLaneSpeedMath.Point(11.5, 0.0, 4.0)).orElseThrow();

        assertEquals(1, projection.segmentIndex());
        assertEquals(14.0, projection.distanceAlong(), 1.0E-9);
        assertEquals(1.5, projection.horizontalDistance(), 1.0E-9);
    }

    @Test
    void garageRecoveryEnvelopeIsThreeBlocksFromProjection() {
        assertTrue(PitLaneSpeedMath.project(ROUTE, new PitLaneSpeedMath.Point(5.0, 0.0, 2.9)).orElseThrow().horizontalDistance()
            <= PitLaneSpeedMath.RECOVERY_DISTANCE_METERS);
        assertTrue(PitLaneSpeedMath.project(ROUTE, new PitLaneSpeedMath.Point(5.0, 0.0, 3.1)).orElseThrow().horizontalDistance()
            > PitLaneSpeedMath.RECOVERY_DISTANCE_METERS);
    }

    @Test
    void speedUsesOnlyForwardProjectionAlongSurvey() {
        PitLaneSpeedMath.Projection projection = PitLaneSpeedMath.project(ROUTE,
            new PitLaneSpeedMath.Point(5.0, 0.0, 0.0)).orElseThrow();

        assertEquals(72.0, PitLaneSpeedMath.projectedSpeedKmh(projection, 1.0, 0.0), 1.0E-9);
        assertEquals(0.0, PitLaneSpeedMath.projectedSpeedKmh(projection, 0.0, 1.0), 1.0E-9);
        assertEquals(0.0, PitLaneSpeedMath.projectedSpeedKmh(projection, -1.0, 0.0), 1.0E-9);
    }

    @Test
    void configuredThresholdsAllowPrecisionMarginOnlyInstantaneously() {
        assertEquals(85.0, PitLaneSpeedMath.INSTANT_LIMIT_KMH);
        assertEquals(80.0, PitLaneSpeedMath.AVERAGE_LIMIT_KMH);
    }

    @Test
    void locatesStartFinishIntersectionAlongOpenPitRoute() {
        double distance = PitLaneSpeedMath.intersectionDistance(ROUTE,
            new PitLaneSpeedMath.Point(8.0, 0.0, -2.0),
            new PitLaneSpeedMath.Point(8.0, 0.0, 2.0)).orElseThrow();

        assertEquals(8.0, distance, 1.0E-9);
    }

    @Test
    void predictsEntryArrivalFromHorizontalDirectionalSpeed() {
        PitLaneSpeedMath.Approach approach = PitLaneSpeedMath.entryApproach(ROUTE,
            new PitLaneSpeedMath.Point(-40.0, 50.0, 25.0), 1.0, 0.0).orElseThrow();

        assertEquals(2.0, approach.secondsToEntry(), 1.0E-9);
        assertEquals(72.0, approach.projectedSpeedKmh(), 1.0E-9);
    }

    @Test
    void doesNotPredictEntryWhenMovingAway() {
        assertTrue(PitLaneSpeedMath.entryApproach(ROUTE,
            new PitLaneSpeedMath.Point(-10.0, 0.0, 0.0), -1.0, 0.0).isEmpty());
    }

    @Test
    void threeSecondVisibilityBoundaryUsesDirectionalProjection() {
        PitLaneSpeedMath.Approach atBoundary = PitLaneSpeedMath.entryApproach(ROUTE,
            new PitLaneSpeedMath.Point(-60.0, 100.0, 100.0), 1.0, 0.0).orElseThrow();
        PitLaneSpeedMath.Approach outside = PitLaneSpeedMath.entryApproach(ROUTE,
            new PitLaneSpeedMath.Point(-61.0, -100.0, -100.0), 1.0, 0.0).orElseThrow();

        assertEquals(3.0, atBoundary.secondsToEntry(), 1.0E-9);
        assertTrue(outside.secondsToEntry() > 3.0);
    }
}
