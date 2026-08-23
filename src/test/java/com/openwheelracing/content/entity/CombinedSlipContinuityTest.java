package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CombinedSlipContinuityTest {
    @Test
    void crossingGripLimitDoesNotAbruptlyDropResultantTyreForce() {
        double demandBelowLimit = 1.0 - 1.0E-6;
        double demandAboveLimit = 1.0 + 1.0E-6;

        double forceBelowLimit = resultingNormalizedForce(demandBelowLimit);
        double forceAboveLimit = resultingNormalizedForce(demandAboveLimit);

        assertEquals(forceBelowLimit, forceAboveLimit, 1.0E-4,
            "Infinitesimally crossing the grip limit must not create a discontinuous tyre-force drop");
    }

    @Test
    void demandBelowGripLimitIsNotScaled() {
        assertEquals(0.75, resultingNormalizedForce(0.75), 1.0E-12);
    }

    @Test
    void demandAboveGripLimitFallsSmoothlyTowardSlidingForce() {
        double mildSlide = resultingNormalizedForce(1.25);
        double severeSlide = resultingNormalizedForce(2.0);

        org.junit.jupiter.api.Assertions.assertTrue(mildSlide < 1.0 && mildSlide > 0.95);
        org.junit.jupiter.api.Assertions.assertTrue(severeSlide < mildSlide && severeSlide >= 0.88);
    }

    private static double resultingNormalizedForce(double normalizedDemand) {
        return normalizedDemand * VehiclePhysics.combinedSlipScale(normalizedDemand);
    }
}
