package com.openwheelracing.content.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicAiNearbyAvoidanceTest {
    @Test
    void highSpeedClosingThreatIsSeenBeyondOldFourteenMetreRange() {
        var subject = new BasicAiNearbyAvoidance.Car(1, 0, 0, 0, 55, 0);
        var parked = new BasicAiNearbyAvoidance.Car(2, 70, 0, 0, 0, 0);
        assertTrue(BasicAiNearbyAvoidance.choose(subject, List.of(parked)).threat());
        assertFalse(BasicAiNearbyAvoidance.choose(subject, List.of(
            new BasicAiNearbyAvoidance.Car(2, 70, 0, 0, 55, 0))).threat());
    }

    @Test
    void gridSearchRejectsParkedBodyButAllowsSafeAdjacentLane() {
        var parked = new BasicAiNearbyAvoidance.Car(2, 0, 0, 0, 0, 0);
        assertFalse(BasicAiNearbyAvoidance.parkingPoseClear(new BasicAiNearbyAvoidance.Car(1, 2, 0, 0, 0, 0), List.of(parked)));
        assertTrue(BasicAiNearbyAvoidance.parkingPoseClear(new BasicAiNearbyAvoidance.Car(1, 0, 2.5, 0, 0, 0), List.of(parked)));
        var search = new AiGridPathSearch(new AiGridPathSearch.Pose(-40, 0, 0), new AiGridApproach.Target(20, 64, 0, 0),
            (x, z, h) -> Math.abs(z) < 20 && BasicAiNearbyAvoidance.parkingPoseClear(
                new BasicAiNearbyAvoidance.Car(1, x, z, h, 0, 0), List.of(parked)));
        while (!search.done()) search.advance(64);
        assertFalse(search.path().isEmpty());
        assertTrue(search.path().stream().anyMatch(point -> Math.abs(point.z()) > 2.0));
    }

    @Test
    void predictsNearbyAiAheadAndSteersAway() {
        BasicAiNearbyAvoidance.Car subject = new BasicAiNearbyAvoidance.Car(1, 0, 0, 0, 4, 0);
        BasicAiNearbyAvoidance.Car target = new BasicAiNearbyAvoidance.Car(2, 5, 0, 0, 0, 0);
        BasicAiNearbyAvoidance.Decision decision = BasicAiNearbyAvoidance.choose(subject, List.of(target));
        assertTrue(decision.threat());
        assertTrue(decision.steeringBias() > 0.0);
        assertTrue(decision.brake() > 0.0);
    }

    @Test
    void ignoresCarsOutsideLocalEnvelope() {
        BasicAiNearbyAvoidance.Car subject = new BasicAiNearbyAvoidance.Car(1, 0, 0, 0, 4, 0);
        assertFalse(BasicAiNearbyAvoidance.choose(subject, List.of(
            new BasicAiNearbyAvoidance.Car(2, 0, 20, 0, 0, 0),
            new BasicAiNearbyAvoidance.Car(3, 5, 4, 0, 0, 0),
            new BasicAiNearbyAvoidance.Car(4, 0, -8, 0, 0, 0)
        )).threat());
    }

    @Test
    void centeredThreatChoosesDeterministicSide() {
        BasicAiNearbyAvoidance.Car target = new BasicAiNearbyAvoidance.Car(2, 5, 0, 0, 0, 0);
        BasicAiNearbyAvoidance.Decision first = BasicAiNearbyAvoidance.choose(new BasicAiNearbyAvoidance.Car(1, 0, 0, 0, 0, 0), List.of(target));
        BasicAiNearbyAvoidance.Decision second = BasicAiNearbyAvoidance.choose(new BasicAiNearbyAvoidance.Car(3, 0, 0, 0, 0, 0), List.of(target));
        assertEquals(-first.steeringBias(), second.steeringBias(), 1.0E-6);
    }
}
