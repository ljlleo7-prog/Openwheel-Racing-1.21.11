package com.openwheelracing.content.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceAutoFlagServiceTest {
    @Test
    void warnsOnlyLightsWithinUpstreamWindow() {
        assertTrue(RaceAutoFlagLogic.isUpstreamWithin(1_500.0, 1_800.0, 4_000.0, 500.0));
        assertFalse(RaceAutoFlagLogic.isUpstreamWithin(1_200.0, 1_800.0, 4_000.0, 500.0));
        assertFalse(RaceAutoFlagLogic.isUpstreamWithin(1_900.0, 1_800.0, 4_000.0, 500.0));
    }

    @Test
    void warningWindowWrapsAcrossStartFinish() {
        assertTrue(RaceAutoFlagLogic.isUpstreamWithin(3_900.0, 100.0, 4_000.0, 500.0));
        assertFalse(RaceAutoFlagLogic.isUpstreamWithin(500.0, 100.0, 4_000.0, 500.0));
    }

    @Test
    void physicalScorePrefersActualNearbyRouteOverHistoricalBranch() {
        double landedOnNewBranch = RaceAutoFlagLogic.physicalScore(1.0, 0.0);
        double oldBranchAcrossGap = RaceAutoFlagLogic.physicalScore(5.0, 0.0);

        assertTrue(landedOnNewBranch < oldBranchAcrossGap);
    }

    @Test
    void nearRouteEnvelopeIncludesNearbyStoppedCarButHonorsVerticalLimit() {
        assertTrue(RaceAutoFlagLogic.isWithinRouteEnvelope(12.0, 2.0, 16.0, 4.0));
        assertFalse(RaceAutoFlagLogic.isWithinRouteEnvelope(17.0, 2.0, 16.0, 4.0));
        assertFalse(RaceAutoFlagLogic.isWithinRouteEnvelope(12.0, 5.0, 16.0, 4.0));
    }
}
