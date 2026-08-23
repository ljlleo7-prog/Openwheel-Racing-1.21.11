package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BaseGripEnvelopeTest {
    @Test
    void disabledAssistStillUsesPermissiveBaseEnvelope() {
        assertEquals(1.06, VehiclePhysics.activeGripEnvelope(false, 0.98), 1.0E-12);
    }

    @Test
    void enabledAssistUsesConfiguredEnvelopeImmediately() {
        assertEquals(0.98, VehiclePhysics.activeGripEnvelope(true, 0.98), 1.0E-12);
        assertEquals(0.94, VehiclePhysics.activeGripEnvelope(true, 0.94), 1.0E-12);
    }

    @Test
    void baseAbsEnvelopeLimitsFirstTickBrakeStep() {
        double force = VehiclePhysics.absLimitedBrakeForce(
            -20_000.0, 6_000.0, 10_000.0, 10_000.0,
            VehiclePhysics.activeGripEnvelope(false, 0.98));
        double demand = Math.hypot(force / 10_000.0, 0.6);

        assertEquals(1.06, demand, 1.0E-12);
    }

    @Test
    void directJoystickBypassesRequestEnvelopes() {
        assertEquals(Double.POSITIVE_INFINITY,
            VehiclePhysics.gripEnvelopeForInputSource(false, 0.98));
        assertEquals(1.06, VehiclePhysics.gripEnvelopeForInputSource(true, 1.06), 1.0E-12);
    }
}
