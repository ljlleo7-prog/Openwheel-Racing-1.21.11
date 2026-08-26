package com.openwheelracing.content.car;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CarSetupPhysicsTest {
    @Test
    void moreWingAddsDownforceAndDrag() {
        assertTrue(CarSetupPhysics.downforceCoefficient(7, 15) > CarSetupPhysics.downforceCoefficient(3, 9));
        assertTrue(CarSetupPhysics.dragCoefficient(7, 15) > CarSetupPhysics.dragCoefficient(3, 9));
    }

    @Test
    void frontWingHasMoreBalanceAuthorityAndRearWingHasMoreDragAuthority() {
        double neutralBalance = CarSetupPhysics.frontAeroBalanceAdjustment(5, 12);
        double frontBalanceChange = Math.abs(CarSetupPhysics.frontAeroBalanceAdjustment(7, 12) - neutralBalance);
        double rearBalanceChange = Math.abs(CarSetupPhysics.frontAeroBalanceAdjustment(5, 15) - neutralBalance);
        double neutralDrag = CarSetupPhysics.dragCoefficient(5, 12);
        double frontDragChange = CarSetupPhysics.dragCoefficient(7, 12) - neutralDrag;
        double rearDragChange = CarSetupPhysics.dragCoefficient(5, 15) - neutralDrag;

        assertTrue(frontBalanceChange > rearBalanceChange);
        assertTrue(rearDragChange > frontDragChange);
    }

    @Test
    void antiRollAndBrakeBiasMapToPhysicalShares() {
        assertTrue(CarSetupPhysics.frontRollStiffnessShare(9) > CarSetupPhysics.frontRollStiffnessShare(1));
        assertEquals(0.52, CarSetupPhysics.brakeFrontBias(52), 1.0e-9);
        assertEquals(0.63, CarSetupPhysics.brakeFrontBias(63), 1.0e-9);
    }
}
