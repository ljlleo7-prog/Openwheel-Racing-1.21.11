package com.openwheelracing.content.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffectiveControlTelemetryTest {
    @Test
    void tractionCutReducesDisplayedThrottleEvenWithFullPedal() {
        var telemetry = new EffectiveControlTelemetry();
        double limited = VehiclePhysics.tractionControlledDriveRequest(10000, 4000, 1.0, false);
        telemetry.sample(1.0, 0.0, 10000, limited, 0, 0);
        assertEquals(0.4, telemetry.throttle(), 1e-9);
        assertEquals(0.0, telemetry.brake(), 1e-9);
    }

    @Test
    void absReductionIsAppliedToTheActualPartialPedalDemand() {
        var telemetry = new EffectiveControlTelemetry();
        double limited = Math.abs(VehiclePhysics.absLimitedBrakeForce(-10000, 0, 4000, 4000, 1.0));
        assertTrue(limited < 10000);
        telemetry.sample(0, 0.8, 0, 0, 10000, limited);
        assertEquals(0.8 * limited / 10000, telemetry.brake(), 1e-9);
    }

    @Test
    void powertrainBrakingWithoutPedalAndNeutralWithoutDriveReadZero() {
        var telemetry = new EffectiveControlTelemetry();
        telemetry.sample(1, 0, 0, 0, 8000, 5000);
        assertEquals(0, telemetry.throttle());
        assertEquals(0, telemetry.brake());
    }

    @Test
    void averagesAssistPulsesAcrossSubstepsAndClearsThePreviousTick() {
        var telemetry = new EffectiveControlTelemetry();
        telemetry.sample(1, 1, 1000, 1000, 1000, 1000);
        telemetry.sample(1, 1, 1000, 0, 1000, 200);
        assertEquals(0.5, telemetry.throttle(), 1e-9);
        assertEquals(0.6, telemetry.brake(), 1e-9);
        telemetry.reset();
        assertEquals(0, telemetry.throttle());
        assertEquals(0, telemetry.brake());
        telemetry.sample(0.3, 0.2, 1000, 1000, 1000, 1000);
        assertEquals(0.3, telemetry.throttle(), 1e-9);
        assertEquals(0.2, telemetry.brake(), 1e-9);
    }

    @Test
    void outputIsBoundedAndInvalidSamplesDoNotLeakNanIntoFrames() {
        var telemetry = new EffectiveControlTelemetry();
        telemetry.sample(1, 1, 1000, 2000, 1000, -200);
        assertEquals(1, telemetry.throttle());
        assertEquals(0, telemetry.brake());
        telemetry.reset();
        telemetry.sample(Double.NaN, 1, 1000, 1000, Double.POSITIVE_INFINITY, 200);
        assertEquals(0, telemetry.throttle());
        assertEquals(0, telemetry.brake());
    }
}
