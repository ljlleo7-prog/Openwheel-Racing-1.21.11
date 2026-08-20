package com.openwheelracing.content.block;

import com.openwheelracing.content.car.TyreType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackVehicleDryingModelTest {
    @Test
    void coldTyresStillHaveAVisibleDryingChance() {
        double chance = TrackVehicleDryingModel.dryingChance(1, TyreType.SLICK, 25.0, 80.0, 0.0);
        assertTrue(chance >= 0.10, "cold passing chance=" + chance);
    }

    @Test
    void heatSpeedAndScrubIncreaseDryingContinuously() {
        double coldSlow = TrackVehicleDryingModel.dryingChance(2, TyreType.INTERMEDIATE, 30.0, 40.0, 0.0);
        double hotFast = TrackVehicleDryingModel.dryingChance(2, TyreType.INTERMEDIATE, 100.0, 250.0, 0.0);
        double hotFastScrubbing = TrackVehicleDryingModel.dryingChance(2, TyreType.INTERMEDIATE, 100.0, 250.0, 0.7);
        assertTrue(hotFast > coldSlow);
        assertTrue(hotFastScrubbing > hotFast);
    }

    @Test
    void deeperWaterIsHarderToRemoveOneStage() {
        double damp = TrackVehicleDryingModel.dryingChance(1, TyreType.WET, 70.0, 150.0, 0.2);
        double soaking = TrackVehicleDryingModel.dryingChance(3, TyreType.WET, 70.0, 150.0, 0.2);
        assertTrue(damp > soaking);
    }

    @Test
    void chanceRemainsBoundedUnderExtremeInputs() {
        double chance = TrackVehicleDryingModel.dryingChance(1, TyreType.WET, 200.0, 500.0, 5.0);
        assertTrue(chance <= 0.95);
    }

    @Test
    void tyresAtBoilingTemperatureAreNearlyCertainToDryContact() {
        double chance = TrackVehicleDryingModel.dryingChance(2, TyreType.SLICK, 105.0, 120.0, 0.0);
        assertTrue(chance >= 0.94, "hot contact chance=" + chance);
    }
}
