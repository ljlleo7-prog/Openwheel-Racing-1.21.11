package com.openwheelracing.content.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackWeatherResponseModelTest {
    @Test
    void rainWetsEveryExposedBlockByTheFirstDampStageWithoutAnotherProbabilityGate() {
        double progress = TrackWeatherProgression.rainingProgress(4800, false);
        for (int i = 0; i < 1000; i++) {
            int target = TrackWeatherResponseModel.targetLevel(progress, i / 1000.0);
            assertEquals(1, TrackWeatherResponseModel.transitionLevel(0, target, true, true));
        }
    }

    @Test
    void twoMinutesOfRainMakesHalfTheSpatialTargetsDamp() {
        double progress = TrackWeatherProgression.rainingProgress(2400, false);
        int damp = 0;
        for (int i = 0; i < 1000; i++) {
            int target = TrackWeatherResponseModel.targetLevel(progress, i / 1000.0);
            damp += TrackWeatherResponseModel.transitionLevel(0, target, true, true);
        }
        assertEquals(500, damp);
    }

    @Test
    void roofsPreventWettingAndRainDoesNotEraseResidualMoisture() {
        assertEquals(0, TrackWeatherResponseModel.transitionLevel(0, 2, true, false));
        assertEquals(3, TrackWeatherResponseModel.transitionLevel(3, 1, true, true));
    }

    @Test
    void clearWeatherDriesToThePhaseTargetWithoutWettingDryBlocks() {
        assertEquals(2, TrackWeatherResponseModel.transitionLevel(3, 1, false, true));
        assertEquals(0, TrackWeatherResponseModel.transitionLevel(0, 1, false, true));
        assertEquals(2, TrackWeatherResponseModel.transitionLevel(3, 0, false, false));
    }

    @Test
    void naturalDryingPreservesAnAlreadyCarDriedLineBelowTheWeatherTarget() {
        int carDriedLevel = 0;
        for (int dryTicks = 0; dryTicks <= 25200; dryTicks += 100) {
            int naturalTarget = TrackWeatherResponseModel.targetLevel(
                TrackWeatherProgression.dryingProgress(3, dryTicks), 0.5);
            carDriedLevel = TrackWeatherResponseModel.transitionLevel(carDriedLevel, naturalTarget, false, true);
            assertEquals(0, carDriedLevel);
        }
    }

    @Test
    void anAdvancedTargetStillChangesOnlyOneMoistureStagePerUpdate() {
        assertEquals(1, TrackWeatherResponseModel.transitionLevel(0,
            TrackWeatherResponseModel.targetLevel(3, 0.99), true, true));
    }
}
