package com.openwheelracing.content.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrackMoistureFieldTest {
    @Test
    void transitionTimesHaveGaussianRatherThanUniformDistribution() {
        int samples = 0;
        int early = 0;
        int middle = 0;
        int withinOneSigma = 0;
        double sum = 0;
        double squared = 0;
        for (int z = -2048; z < 2048; z += 16) {
            for (int x = -2048; x < 2048; x += 16) {
                double value = TrackMoistureField.gaussian(x, z, 12345);
                double threshold = TrackMoistureField.threshold(x, z, 12345);
                sum += value;
                squared += value * value;
                if (threshold < 0.25) early++;
                if (threshold < 0.5) middle++;
                if (Math.abs(value) < 1) withinOneSigma++;
                samples++;
            }
        }
        double mean = sum / samples;
        assertEquals(0, mean, 0.06);
        assertEquals(1, squared / samples - mean * mean, 0.12);
        assertEquals(0.048, (double) early / samples, 0.025);
        assertEquals(0.5, (double) middle / samples, 0.04);
        assertEquals(0.683, (double) withinOneSigma / samples, 0.04);
    }

    @Test
    void neighboringBlocksHaveMuchCloserTransitionTimesThanDistantBlocks() {
        double adjacentDifference = 0;
        double distantDifference = 0;
        for (int z = -256; z < 256; z += 3) {
            for (int x = -256; x < 256; x += 3) {
                double current = TrackMoistureField.threshold(x, z, 400);
                adjacentDifference += Math.pow(current - TrackMoistureField.threshold(x + 1, z, 400), 2);
                distantDifference += Math.pow(current - TrackMoistureField.threshold(x + 128, z + 96, 400), 2);
            }
        }
        assertTrue(adjacentDifference < distantDifference * 0.02);
    }

    @Test
    void fieldIsContinuousAcrossPositiveAndNegativeChunkBoundaries() {
        for (int boundary : new int[] {-32, -16, 0, 16, 32}) {
            for (int coordinate = -32; coordinate < 32; coordinate++) {
                assertEquals(TrackMoistureField.gaussian(boundary - 0.001, coordinate, 42),
                    TrackMoistureField.gaussian(boundary + 0.001, coordinate, 42), 0.002);
                assertEquals(TrackMoistureField.gaussian(coordinate, boundary - 0.001, 42),
                    TrackMoistureField.gaussian(coordinate, boundary + 0.001, 42), 0.002);
            }
        }
    }

    @Test
    void patchesExtendBeyondAChunkButKeepVariationWithinEachChunk() {
        double chunkDistance = 0;
        double broadDistance = 0;
        int variedChunks = 0;
        for (int z = -512; z < 512; z += 32) {
            for (int x = -512; x < 512; x += 32) {
                double current = TrackMoistureField.threshold(x, z, 801);
                chunkDistance += Math.pow(current - TrackMoistureField.threshold(x + 16, z, 801), 2);
                broadDistance += Math.pow(current - TrackMoistureField.threshold(x + 192, z + 128, 801), 2);
                double min = 1;
                double max = 0;
                for (int dz = 0; dz < 16; dz += 3) {
                    for (int dx = 0; dx < 16; dx += 3) {
                        double threshold = TrackMoistureField.threshold(x + dx, z + dz, 801);
                        min = Math.min(min, threshold);
                        max = Math.max(max, threshold);
                    }
                }
                if (max - min > 0.01) variedChunks++;
            }
        }
        assertTrue(chunkDistance < broadDistance * 0.25);
        assertTrue(variedChunks > 950, "Most chunks should contain varying transition deadlines");
    }

    @Test
    void lazyEvaluationUsesElapsedTimeRatherThanNumberOfUpdates() {
        for (int x = -64; x < 64; x++) {
            double threshold = TrackMoistureField.threshold(x, 10, 500);
            int crossing = -1;
            for (int tick = 0; tick <= 4800; tick += 20) {
                if (TrackWeatherResponseModel.targetLevel(TrackWeatherProgression.rainingProgress(tick, false), threshold) == 1) {
                    crossing = tick;
                    break;
                }
            }
            // The deadline is set by the Gaussian field, even if no updates ran before it.
            int deadline = (int) Math.floor(threshold * 4800) + 1;
            assertEquals((int) (Math.ceil(deadline / 20.0) * 20), crossing);
            assertEquals(0, TrackWeatherResponseModel.targetLevel(
                TrackWeatherProgression.rainingProgress(deadline - 1, false), threshold));
            assertEquals(1, TrackWeatherResponseModel.targetLevel(
                TrackWeatherProgression.rainingProgress(deadline, false), threshold));
        }
    }

    @Test
    void naturalDryingUsesTheGaussianDeadlineEvenWhenUpdatesAreDelayed() {
        for (int x = -64; x < 64; x++) {
            double threshold = TrackMoistureField.threshold(x, 10, 500);
            int deadline = (int) Math.ceil((1.5 - threshold) * 8400);
            int before = TrackWeatherResponseModel.targetLevel(
                TrackWeatherProgression.naturalDryingProgress(1, deadline - 1), threshold, true);
            int after = TrackWeatherResponseModel.targetLevel(
                TrackWeatherProgression.naturalDryingProgress(1, deadline), threshold, true);
            assertEquals(1, TrackWeatherResponseModel.transitionLevel(1, before, false, true));
            assertEquals(0, TrackWeatherResponseModel.transitionLevel(1, after, false, true));
            assertEquals(0, TrackWeatherResponseModel.transitionLevel(1,
                TrackWeatherResponseModel.targetLevel(TrackWeatherProgression.naturalDryingProgress(1, 12600), threshold, true), false, true));
        }
    }

    @Test
    void naturalDryingPopulationIsCenteredOnSevenMinutesWithEarlyAndLateBlocks() {
        int samples = 0;
        int dryAtMean = 0;
        int dryHalfway = 0;
        double totalDeadlineSeconds = 0;
        for (int z = -2048; z < 2048; z += 16) {
            for (int x = -2048; x < 2048; x += 16) {
                double threshold = TrackMoistureField.threshold(x, z, 12345);
                totalDeadlineSeconds += (1.5 - threshold) * 420;
                if (TrackWeatherResponseModel.targetLevel(
                    TrackWeatherProgression.naturalDryingProgress(1, 8400), threshold, true) == 0) dryAtMean++;
                if (TrackWeatherResponseModel.targetLevel(
                    TrackWeatherProgression.naturalDryingProgress(1, 4200), threshold, true) == 0) dryHalfway++;
                assertEquals(0, TrackWeatherResponseModel.targetLevel(
                    TrackWeatherProgression.naturalDryingProgress(1, 12600), threshold, true));
                samples++;
            }
        }
        assertEquals(420, totalDeadlineSeconds / samples, 5);
        assertEquals(0.5, (double) dryAtMean / samples, 0.04);
        assertTrue(dryHalfway < samples * 0.01);
    }

    @Test
    void thresholdsRespectTheOriginalStageEndpoints() {
        for (int x = -1000; x < 1000; x++) {
            double threshold = TrackMoistureField.threshold(x, 0, 1);
            assertTrue(threshold > 0 && threshold < 1);
            assertEquals(0, TrackWeatherResponseModel.targetLevel(0, threshold));
            assertEquals(1, TrackWeatherResponseModel.targetLevel(1, threshold));
            assertEquals(3, TrackWeatherResponseModel.targetLevel(3, threshold));
        }
    }
}
