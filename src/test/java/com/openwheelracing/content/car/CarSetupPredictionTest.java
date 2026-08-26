package com.openwheelracing.content.car;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class CarSetupPredictionTest {
    @Test
    void colorContinuouslyInterpolatesBetweenBlueAndRedEnds() {
        int blue = CarSetupPrediction.color(0.0);
        int middle = CarSetupPrediction.color(0.5);
        int red = CarSetupPrediction.color(1.0);

        assertEquals(0xFF4AA3FF, blue);
        assertEquals(0xFF55D66B, middle);
        assertEquals(0xFFFF5C5C, red);
        assertNotEquals(blue, middle);
        assertNotEquals(red, middle);
    }

    @Test
    void eachAdjustmentDescribesItsTradeoff() {
        CarSetupPrediction.Tradeoff lowGear = CarSetupPrediction.tradeoff(3, 0.0);
        CarSetupPrediction.Tradeoff highWing = CarSetupPrediction.tradeoff(1, 1.0);

        assertEquals("top speed - low", lowGear.primary());
        assertEquals("accel - high", lowGear.secondary());
        assertEquals("front grip - high", highWing.primary());
        assertEquals("top speed - low", highWing.secondary());
    }

    @Test
    void combinedBalanceAccountsForMultipleControls() {
        CarSetupPrediction.Summary frontGripBiased = CarSetupPrediction.combined(0.5, 0.5, 1.0, 0.0, 0.0, 0.0);
        CarSetupPrediction.Summary rearStable = CarSetupPrediction.combined(0.5, 0.5, 0.0, 1.0, 1.0, 1.0);

        assertEquals("balance - oversteer", CarSetupPrediction.balanceTerm(frontGripBiased.balance()));
        assertEquals("balance - understeer", CarSetupPrediction.balanceTerm(rearStable.balance()));
    }

    @Test
    void gearingAndWingTradeAccelerationGripAgainstSpeed() {
        CarSetupPrediction.Summary accelerationSetup = CarSetupPrediction.combined(1.0, 0.0, 0.0, 0.0, 0.5, 0.5);
        CarSetupPrediction.Summary speedSetup = CarSetupPrediction.combined(1.0, 1.0, 0.0, 0.0, 0.5, 0.5);
        CarSetupPrediction.Summary wingSetup = CarSetupPrediction.combined(1.0, 1.0, 1.0, 1.0, 0.5, 0.5);

        org.junit.jupiter.api.Assertions.assertTrue(accelerationSetup.acceleration() > speedSetup.acceleration());
        org.junit.jupiter.api.Assertions.assertTrue(speedSetup.topSpeed() > wingSetup.topSpeed());
        org.junit.jupiter.api.Assertions.assertTrue(wingSetup.grip() > speedSetup.grip());
    }

    @Test
    void lockedPowerDoesNotChangeSetupPrediction() {
        CarSetupPrediction.Summary lowArgument = CarSetupPrediction.combined(0.0, 0.5, 0.5, 0.5, 0.5, 0.5);
        CarSetupPrediction.Summary highArgument = CarSetupPrediction.combined(1.0, 0.5, 0.5, 0.5, 0.5, 0.5);

        assertEquals(lowArgument.acceleration(), highArgument.acceleration(), 1.0e-9);
    }
}
