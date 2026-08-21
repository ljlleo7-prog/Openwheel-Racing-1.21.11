package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openwheelracing.content.car.TyreType;
import org.junit.jupiter.api.Test;

class WetTyrePhysicsTest {
    @Test
    void absoluteGripAlwaysFallsAsTrackGetsWetter() {
        for (TyreType type : TyreType.values()) {
            double previous = Double.POSITIVE_INFINITY;
            for (int moisture = 0; moisture <= 3; moisture++) {
                double grip = WetTyrePhysics.wetGrip(type, moisture);
                assertTrue(grip <= previous);
                previous = grip;
            }
        }
    }

    @Test
    void specialistTyresAreOnlyRelativelyBestInTheirConditions() {
        assertTrue(WetTyrePhysics.wetGrip(TyreType.INTERMEDIATE, 1) > WetTyrePhysics.wetGrip(TyreType.SLICK, 1));
        assertTrue(WetTyrePhysics.wetGrip(TyreType.WET, 2) > WetTyrePhysics.wetGrip(TyreType.INTERMEDIATE, 2));
        assertEquals(1.0, WetTyrePhysics.wetGrip(TyreType.WET, 0));
    }

    @Test
    void hydroplaningOnsetOrdersSlickThenIntermediateThenWet() {
        double speed = 120.0;
        double slick = WetTyrePhysics.hydroplaningSeverity(TyreType.SLICK, 1.0, speed, 0.0);
        double intermediate = WetTyrePhysics.hydroplaningSeverity(TyreType.INTERMEDIATE, 1.0, speed, 0.0);
        double wet = WetTyrePhysics.hydroplaningSeverity(TyreType.WET, 1.0, speed, 0.0);
        assertTrue(slick > intermediate);
        assertTrue(intermediate > wet);
        assertTrue(WetTyrePhysics.hydroplaningSeverity(TyreType.INTERMEDIATE, 1.0, speed, 1.0) > intermediate);
    }

    @Test
    void hotWaterCoolingBoostsSurfaceMuchMoreThanCarcass() {
        double surface = WetTyrePhysics.coolingMultiplier(2, 105.0);
        double carcass = WetTyrePhysics.carcassCoolingMultiplier(2, 105.0);
        assertEquals(8.0, surface, 1.0E-9);
        assertEquals(1.0 + (surface - 1.0) * 0.35, carcass, 1.0E-9);
    }

    @Test
    void slickCoolingCurveIsUnchangedByTypeAwarePath() {
        for (int moisture = 0; moisture <= 3; moisture++) {
            for (double temperature : new double[]{50.0, 75.0, 95.0, 105.0}) {
                assertEquals(WetTyrePhysics.coolingMultiplier(moisture, temperature),
                    WetTyrePhysics.coolingMultiplier(TyreType.SLICK, moisture, temperature), 1.0E-12);
            }
        }
    }

    @Test
    void hotGroovedCarcassActivatesCoolingEvenWhenSurfaceIsCool() {
        double intermediateSurface = WetTyrePhysics.coolingMultiplier(TyreType.INTERMEDIATE, 2, 75.0);
        double intermediateCarcass = WetTyrePhysics.carcassCoolingMultiplier(TyreType.INTERMEDIATE, 2, 105.0);
        double wetSurface = WetTyrePhysics.coolingMultiplier(TyreType.WET, 2, 50.0);
        double wetCarcass = WetTyrePhysics.carcassCoolingMultiplier(TyreType.WET, 2, 90.0);
        assertTrue(intermediateCarcass > intermediateSurface);
        assertTrue(wetCarcass > wetSurface);
    }

    @Test
    void representativeGroovedTyreTraceKeepsBothLayersBounded() {
        assertGroovedTraceBounded(TyreType.INTERMEDIATE, 1, 98.0, 100.0);
        assertGroovedTraceBounded(TyreType.WET, 2, 72.0, 82.0);
    }

    @Test
    void lapLikeWetTraceSettlesInsteadOfRunningAway() {
        double temperature = 40.0;
        for (int lapTick = 0; lapTick < 3 * 1800; lapTick++) {
            boolean corner = lapTick % 300 < 110;
            double heat = corner ? 0.055 : 0.006;
            double cooling = (temperature - 20.0) * 0.00042
                * WetTyrePhysics.coolingMultiplier(2, temperature);
            temperature += heat - cooling;
        }
        assertTrue(temperature > 40.0 && temperature < 82.0, "wet tyre trace ended at " + temperature);
    }

    @Test
    void specialistTyresWearRapidlyPastTheirOwnHeatLimit() {
        assertEquals(1.0, WetTyrePhysics.temperatureWear(TyreType.INTERMEDIATE, 80.0), 1.0E-9);
        assertTrue(WetTyrePhysics.temperatureWear(TyreType.INTERMEDIATE, 105.0) > 2.0);
        assertTrue(WetTyrePhysics.temperatureWear(TyreType.WET, 80.0) > 2.0);
    }

    private static void assertGroovedTraceBounded(TyreType type, int moisture, double expectedSurfaceMax, double carcassMax) {
        VehiclePhysics.TyreThermalState state = new VehiclePhysics.TyreThermalState(75.0, 75.0, 0.0);
        for (int tick = 0; tick < 3 * 1800; tick++) {
            boolean loaded = tick % 300 >= 150;
            double frictionPower = loaded ? 28_000.0 * type.deformationHeatMultiplier() : 7_000.0;
            double brakePower = tick % 300 >= 150 && tick % 300 < 190 ? 3_500.0 : 0.0;
            double surfaceCooling = WetTyrePhysics.coolingMultiplier(type, moisture, state.surfaceTemperatureC());
            double carcassCooling = WetTyrePhysics.carcassCoolingMultiplier(type, moisture, state.carcassTemperatureC());
            state = VehiclePhysics.nextTyreThermalState(state.surfaceTemperatureC(), state.carcassTemperatureC(), state.slipExposure(),
                frictionPower, brakePower, 1.0, loaded ? 32.0 : 55.0, surfaceCooling, carcassCooling,
                1.0, 1.0, loaded ? 0.9 : 0.35, loaded ? Math.toRadians(3.0) : 0.0, 0.05, true);
        }
        assertTrue(state.surfaceTemperatureC() < expectedSurfaceMax, type + " surface=" + state.surfaceTemperatureC());
        assertTrue(state.carcassTemperatureC() < carcassMax, type + " carcass=" + state.carcassTemperatureC());
        assertTrue(state.carcassTemperatureC() - state.surfaceTemperatureC() < 18.0,
            type + " thermal split surface=" + state.surfaceTemperatureC() + " carcass=" + state.carcassTemperatureC());
    }
}
