package com.openwheelracing.content.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicAiGripModelTest {
    @Test
    void aeroRaisesHighSpeedLateralCapacity() {
        BasicAiGripModel.State state = state(95.0, 0.0, 0.0, 1.0, 1.0);
        assertTrue(state.lateralAcceleration(70.0) > state.lateralAcceleration(10.0));
    }

    @Test
    void coldWornDamagedTyresReduceCapacity() {
        BasicAiGripModel.State healthy = state(95.0, 0.0, 0.0, 1.0, 1.0);
        BasicAiGripModel.State weak = state(35.0, 70.0, 60.0, 0.8, 0.9);
        assertTrue(healthy.lateralAcceleration(30.0) > weak.lateralAcceleration(30.0));
        assertTrue(healthy.brakeAcceleration(30.0) > weak.brakeAcceleration(30.0));
    }

    private static BasicAiGripModel.State state(double temperature, double wear, double damage, double surface, double aero) {
        return BasicAiGripModel.build(new BasicAiGripModel.Input(temperature, 88.0, 104.0, wear, damage, 1.0,
            surface, aero, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0));
    }
}
