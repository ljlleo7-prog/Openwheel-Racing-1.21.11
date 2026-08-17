package com.openwheelracing.content.ai;

import com.openwheelracing.content.track.survey.SurveyRouteModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicAiControllerMathTest {
    @Test
    void straightRouteUsesHighSpeedTargetAndProportionalThrottle() {
        BasicAiGripModel.State grip = BasicAiGripModel.build(new BasicAiGripModel.Input(95.0, 88.0, 104.0, 0.0, 0.0,
            1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0));
        assertEquals(BasicAiCarController.MAX_TARGET_SPEED_MPS,
            BasicAiSpeedPlanner.targetSpeed(straightRoute(), 0.0, grip, BasicAiTrafficMode.RACE), 1.0E-6);
        BasicAiDriveCommand command = BasicAiCarController.speedCommand(10.0, BasicAiCarController.MAX_TARGET_SPEED_MPS, 0.0f, BasicAiNearbyAvoidance.Decision.NONE);
        assertTrue(command.throttle() > 0.0f);
        assertEquals(0.0f, command.brake());
    }

    @Test
    void originalLateralLogicCorrectsEveryOffsetAndLimitsSteeringRate() {
        SurveyRouteModel route = straightRoute();
        float smallLeftCorrection = BasicAiCarController.steeringCommand(route, 5.0, new SurveyRouteModel.Point(5, 64, 1), 0.0, 1.0, 10.0, 0.0f);
        float smallRightCorrection = BasicAiCarController.steeringCommand(route, 5.0, new SurveyRouteModel.Point(5, 64, -1), 0.0, -1.0, 10.0, 0.0f);
        assertTrue(smallLeftCorrection < 0.0f);
        assertTrue(smallRightCorrection > 0.0f);
        float recovery = BasicAiCarController.steeringCommand(route, 5.0, new SurveyRouteModel.Point(5, 64, 3), 0.0, 3.0, 10.0, 0.0f);
        assertTrue(recovery < 0.0f);
        assertTrue(Math.abs(recovery) <= 0.0801f);
    }
    @Test
    void centeredStraightRouteNeedsNoSteering() {
        SurveyRouteModel route = straightRoute();
        float steering = BasicAiCarController.steeringCommand(route, 5.0, new SurveyRouteModel.Point(5, 64, 0), 0.0, 0.0, 10.0, 0.0f);
        assertEquals(0.0f, steering, 1.0E-4f);
    }

    @Test
    void lateralOffsetsSteerTowardRoute() {
        SurveyRouteModel route = straightRoute();
        float leftOfRoute = BasicAiCarController.steeringCommand(route, 5.0, new SurveyRouteModel.Point(5, 64, 2), 0.0, 2.0, 10.0, 0.0f);
        float rightOfRoute = BasicAiCarController.steeringCommand(route, 5.0, new SurveyRouteModel.Point(5, 64, -2), 0.0, -2.0, 10.0, 0.0f);
        assertTrue(leftOfRoute < 0.0f);
        assertTrue(rightOfRoute > 0.0f);
    }

    @Test
    void learnedLineGradientChangesSteeringTarget() {
        SurveyRouteModel route = straightRoute();
        float centered = BasicAiCarController.desiredSteering(route, 5.0, new SurveyRouteModel.Point(5, 64, 0), 0.0, 0.0, 10.0, 0.0, 0.0);
        float offsetLine = BasicAiCarController.desiredSteering(route, 5.0, new SurveyRouteModel.Point(5, 64, 0), 0.0, -1.0, 10.0, 1.0, 0.1);
        assertTrue(offsetLine > centered);
    }
    @Test
    void repeatedIncidentReducesApproachSpeed() {
        double target = BasicAiSpeedPlanner.applyIncidentLimit(80.0, 100.0, 1000.0,
            List.of(new OWRAiTrainingData.Incident(130.0, 4, 1, 0.5)));
        assertTrue(target < 40.0);
        assertEquals(80.0, BasicAiSpeedPlanner.applyIncidentLimit(80.0, 100.0, 1000.0,
            List.of(new OWRAiTrainingData.Incident(400.0, 4, 1, 0.5))), 1.0E-6);
    }

    @Test
    void curvatureReducesTargetSpeed() {
        BasicAiGripModel.State grip = BasicAiGripModel.build(new BasicAiGripModel.Input(95.0, 88.0, 104.0, 0.0, 0.0,
            1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0));
        double straight = BasicAiSpeedPlanner.targetSpeed(straightRoute(), 0.0, grip, BasicAiTrafficMode.RACE);
        double curved = BasicAiSpeedPlanner.targetSpeed(SurveyRouteSamplerTest.squareRoute(), 0.0, grip, BasicAiTrafficMode.RACE);
        assertTrue(curved < straight);
    }

    @Test
    void brakingStartsAboveCornerAllowance() {
        BasicAiDriveCommand command = BasicAiCarController.speedCommand(20.0, 10.0, 0.0f, BasicAiNearbyAvoidance.Decision.NONE);
        assertEquals(0.0f, command.throttle());
        assertTrue(command.brake() > 0.0f);
    }

    @Test
    void safetySupervisorCapsSpeedWellBeforeSlowCorner() {
        BasicAiGripModel.State grip = BasicAiGripModel.build(new BasicAiGripModel.Input(95, 88, 104, 0, 0,
            1, 1, 1, 1, 1, 1, 1, 1, 1));
        UUID routeId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        java.util.ArrayList<AiTrackSample> samples = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            double speed = i >= 50 && i < 65 ? 12.0 : 80.0;
            samples.add(new AiTrackSample(i * 2.0, new SurveyRouteModel.Point(i * 2.0, 64, 0), 0, 0, 0, speed,
                AiTrackPlan.ReferenceSource.SURVEY));
        }
        AiTrackPlan plan = new AiTrackPlan(trackId, routeId, 200, 2, samples, AiTrackPlan.ReferenceSource.SURVEY, 0, 0, false);
        double supervised = BasicAiCarController.supervisedTargetSpeed(plan, 0, 70, grip);
        assertTrue(supervised < 70.0, "braking supervisor must intervene before the corner");
    }

    private static SurveyRouteModel straightRoute() {
        return new SurveyRouteModel(UUID.randomUUID(), UUID.randomUUID(), List.of(), List.of(
            SurveyRouteSamplerTest.node(0, 0, 0, 0, 0),
            SurveyRouteSamplerTest.node(1, 20, 0, 0, 20),
            SurveyRouteSamplerTest.node(2, 40, 0, 0, 40),
            SurveyRouteSamplerTest.node(3, 60, 0, 0, 60)
        ), 80, 20);
    }
}
