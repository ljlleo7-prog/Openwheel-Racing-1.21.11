package com.openwheelracing.content.ai;

import com.openwheelracing.content.track.survey.SurveyRouteModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicAiControllerMathTest {
    @Test
    void straightRouteUsesHighSpeedTargetAndFullThrottle() {
        BasicAiGripModel.State grip = BasicAiGripModel.build(new BasicAiGripModel.Input(95.0, 88.0, 104.0, 0.0, 0.0,
            1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0));
        assertEquals(BasicAiCarController.MAX_TARGET_SPEED_MPS,
            BasicAiSpeedPlanner.targetSpeed(straightRoute(), 0.0, grip, BasicAiTrafficMode.RACE), 1.0E-6);
        BasicAiDriveCommand command = BasicAiCarController.speedCommand(10.0, BasicAiCarController.MAX_TARGET_SPEED_MPS, 0.0f, BasicAiNearbyAvoidance.Decision.NONE);
        assertEquals(1.0f, command.throttle());
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

    private static SurveyRouteModel straightRoute() {
        return new SurveyRouteModel(UUID.randomUUID(), UUID.randomUUID(), List.of(), List.of(
            SurveyRouteSamplerTest.node(0, 0, 0, 0, 0),
            SurveyRouteSamplerTest.node(1, 20, 0, 0, 20),
            SurveyRouteSamplerTest.node(2, 40, 0, 0, 40),
            SurveyRouteSamplerTest.node(3, 60, 0, 0, 60)
        ), 80, 20);
    }
}
