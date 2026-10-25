package com.openwheelracing.content.ai;

import com.openwheelracing.content.track.survey.SurveyRouteModel;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AiFormationHeatingTest {
    private static BasicAiGripModel.State grip() {
        return BasicAiGripModel.build(new BasicAiGripModel.Input(95, 88, 104, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1));
    }

    private static AiTrackPlan plan(boolean corner) {
        List<AiTrackSample> samples = new ArrayList<>();
        for (int distance = 0; distance < 1_000; distance += 2) {
            samples.add(new AiTrackSample(distance, new SurveyRouteModel.Point(distance, 64, 0), 0,
                corner && distance >= 200 && distance <= 240 ? 0.04 : 0.0, 0, 10, AiTrackPlan.ReferenceSource.SURVEY));
        }
        return new AiTrackPlan(UUID.randomUUID(), UUID.randomUUID(), 1_000, 2, samples, AiTrackPlan.ReferenceSource.SURVEY, 0, 0, false);
    }

    @Test
    void heatingUsesMostAvailableLateralGripWithoutExceedingIt() {
        var plan = plan(false);
        for (double speed : new double[] {20, 40, 55}) {
            var sample = AiFormationHeating.sample(plan, 120, speed, grip(), 200, false, true, Math.PI / 2, 1.0);
            double lateral = speed * speed * Math.abs(sample.curvatureOffset());
            assertTrue(lateral <= grip().lateralAcceleration(speed) * 0.751);
            assertTrue(lateral >= grip().lateralAcceleration(speed) * 0.70, "lateral=" + lateral);
        }
    }

    @Test
    void learnedLineMicroCorrectionsAreNotTreatedAsRealCorners() {
        List<AiTrackSample> samples = new ArrayList<>();
        for (int distance = 0; distance < 1_200; distance += 2) {
            double curvature = 0.003 * Math.cos(distance * 2.0 * Math.PI / 24.0);
            samples.add(new AiTrackSample(distance, new SurveyRouteModel.Point(distance, 64, 0), 0, curvature,
                0, 80, AiTrackPlan.ReferenceSource.PLAYER));
        }
        var plan = new AiTrackPlan(UUID.randomUUID(), UUID.randomUUID(), 1_200, 2, samples,
            AiTrackPlan.ReferenceSource.PLAYER, 0, 0, false);
        var result = AiFormationHeating.sample(plan, 120, 50, grip(), 150, false, true);
        assertEquals(200.0 / 3.6, result.speed(), 1e-6);
    }

    @Test
    void openStraightTargetsTwoHundredAndAlternatesBoundedWeave() {
        var plan = plan(false);
        var left = AiFormationHeating.sample(plan, 45, 50, grip(), 150, false, true, Math.PI / 2, 1.0);
        var right = AiFormationHeating.sample(plan, 135, 50, grip(), 150, false, true, Math.PI * 1.5, 1.0);
        assertEquals(200.0 / 3.6, left.speed(), 1e-6);
        assertTrue(left.offset() > 0);
        assertTrue(right.offset() < 0);
        assertTrue(Math.abs(left.offset()) <= 0.8);
        assertTrue(Math.abs(right.offset()) <= 0.8);
    }

    @Test
    void cornerApproachBrakesAndDoesNotWeave() {
        var plan = plan(true);
        var approach = AiFormationHeating.sample(plan, 185, 55, grip(), 150, false, true);
        var corner = AiFormationHeating.sample(plan, 210, 30, grip(), 150, false, true);
        assertTrue(approach.braking());
        assertEquals(0.0, approach.offset(), 1e-9);
        assertTrue(corner.speed() < 30);
        assertTrue(corner.speed() * corner.speed() * 0.04 <= grip().lateralAcceleration(30) * 0.701);
        assertEquals(0.0, corner.offset(), 1e-9);
    }

    @Test
    void trafficNarrowRoadAndCloseFollowingDisableWeave() {
        var plan = plan(false);
        assertEquals(0, AiFormationHeating.sample(plan, 45, 50, grip(), 150, true, true).offset());
        assertEquals(0, AiFormationHeating.sample(plan, 45, 50, grip(), 150, false, false).offset());
        assertEquals(0, AiFormationHeating.sample(plan, 45, 50, grip(), 20, false, true).offset());
    }
}
