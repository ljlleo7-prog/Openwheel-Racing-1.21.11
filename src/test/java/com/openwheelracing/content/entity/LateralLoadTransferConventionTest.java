package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LateralLoadTransferConventionTest {
    @Test
    void positiveLeftwardAccelerationLoadsNegativeXOutsideWheel() {
        VehiclePhysics.AxleWheelLoads loads = VehiclePhysics.lateralAxleLoads(8_000.0, 1_200.0);

        assertTrue(loads.negativeLocalX() > loads.positiveLocalX());
        assertEquals(8_000.0, loads.negativeLocalX() + loads.positiveLocalX(), 1.0E-12);
    }

    @Test
    void oppositeTurnReversesLoadedSide() {
        VehiclePhysics.AxleWheelLoads loads = VehiclePhysics.lateralAxleLoads(8_000.0, -1_200.0);

        assertTrue(loads.positiveLocalX() > loads.negativeLocalX());
        assertEquals(8_000.0, loads.negativeLocalX() + loads.positiveLocalX(), 1.0E-12);
    }
}
