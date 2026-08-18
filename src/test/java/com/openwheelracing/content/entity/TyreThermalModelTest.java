package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TyreThermalModelTest {
    private static final double NORMAL_LOAD_PER_WHEEL = 769.0 * 9.81 / 4.0;
    private static final double SPEED = 72.0;
    private static final double COMPOUND_HEAT_GAIN = 1.0;
    private static final double ROLLING_RESISTANCE = 0.014;

    @Test
    void lowStressCruiseCoolsBelowWorkingTemperature() {
        double heatPower = aggregateHeatPower(0.12, 0.02, 0.08, 0.0);

        double equilibrium = VehiclePhysics.simulateTyreEquilibriumC(75.0, heatPower, COMPOUND_HEAT_GAIN, 0.08, 0.0, SPEED, 20 * 60 * 60);

        assertTrue(equilibrium >= 38.0, "rolling deformation should keep the tyre above ambient");
        assertTrue(equilibrium <= 62.0, "clean straight-line running should cool below the working range");
    }

    @Test
    void sustainedHighStressCornerEquilibratesAboveLowStressCruise() {
        double heatPower = aggregateHeatPower(0.38, 0.78, 0.78, Math.toRadians(6.5));

        double equilibrium = VehiclePhysics.simulateTyreEquilibriumC(75.0, heatPower, COMPOUND_HEAT_GAIN, 0.38, 0.78, SPEED, 20 * 60 * 60);

        assertTrue(equilibrium >= 105.0, "high-stress corners should equilibrate near the upper working window");
        assertTrue(equilibrium <= 139.0, "high-stress corners should still converge instead of running away");
    }

    @Test
    void hotLowStressCruiseCoolsTowardSameEquilibrium() {
        double heatPower = aggregateHeatPower(0.12, 0.02, 0.08, 0.0);

        double from120 = VehiclePhysics.simulateTyreEquilibriumC(120.0, heatPower, COMPOUND_HEAT_GAIN, 0.08, 0.0, SPEED, 6 * 60 * 20);
        double from160 = VehiclePhysics.simulateTyreEquilibriumC(160.0, heatPower, COMPOUND_HEAT_GAIN, 0.08, 0.0, SPEED, 6 * 60 * 20);

        assertTrue(from120 < 120.0, "hot tyres should cool on low-stress straights");
        assertTrue(from160 < 125.0, "very hot tyres should recover below severe overheating on low-stress straights");
        assertTrue(from160 > from120, "higher starting temperature should cool toward, not jump past, the lower hot-start trace");
    }

    @Test
    void hotHighStressCornerConvergesInsteadOfRunningAway() {
        double heatPower = aggregateHeatPower(0.38, 0.78, 0.78, Math.toRadians(6.5));

        double from120 = VehiclePhysics.simulateTyreEquilibriumC(120.0, heatPower, COMPOUND_HEAT_GAIN, 0.38, 0.78, SPEED, 10 * 60 * 20);
        double from160 = VehiclePhysics.simulateTyreEquilibriumC(160.0, heatPower, COMPOUND_HEAT_GAIN, 0.38, 0.78, SPEED, 10 * 60 * 20);

        assertTrue(from120 <= 139.0, "120C high-stress starts should still converge instead of running away");
        assertTrue(from160 < 160.0, "very hot high-stress starts should cool, not keep climbing");
        assertTrue(Math.abs(from160 - from120) <= 18.0, "hot starts should converge toward the same high-stress window");
    }

    @Test
    void hotKineticSlipStillEventuallyFallsOnceDemandIsBackUnderLimit() {
        double controlledHeatPower = aggregateHeatPower(0.28, 0.72, 0.82, Math.toRadians(5.5));

        double cooled = VehiclePhysics.simulateTyreEquilibriumC(170.0, controlledHeatPower, COMPOUND_HEAT_GAIN, 0.28, 0.72, SPEED, 8 * 60 * 20);

        assertTrue(cooled < 150.0, "tyres recovering from a slide should cool once demand is back under limit");
    }
    @Test
    void severeOverLimitSlipFromHotStartDoesNotRunAwayUnbounded() {
        double heatPower = aggregateHeatPower(0.45, 0.85, 1.16, Math.toRadians(8.0));

        double hot = VehiclePhysics.simulateTyreEquilibriumC(150.0, heatPower, COMPOUND_HEAT_GAIN, 0.45, 0.85, SPEED, 4 * 60 * 20);

        assertTrue(hot <= 192.0, "severe over-limit slip can get very hot but should remain bounded");
    }

    @Test
    void steeringSlipHeatsFrontFasterThanRearWithinFiveSeconds() {
        double frontHeatPower = compoundWheelHeatPower(2, 0.18, 1.05, 0.98, Math.toRadians(12.0), NORMAL_LOAD_PER_WHEEL * 1.25);
        double rearHeatPower = compoundWheelHeatPower(2, 0.14, 0.40, 0.55, Math.toRadians(2.8), NORMAL_LOAD_PER_WHEEL * 0.90);

        double frontAfterFiveSeconds = simulateSeconds(100.0, frontHeatPower, 0.18, 1.05, SPEED, 5.0);
        double rearAfterFiveSeconds = simulateSeconds(100.0, rearHeatPower, 0.14, 0.40, SPEED, 5.0);

        assertTrue(frontAfterFiveSeconds - rearAfterFiveSeconds >= 1.2, "front slip angle should create visible heat response within 5s");
    }
    @Test
    void hotFrontRearImbalanceShowsShortTermRecoveryTrend() {
        double frontHeatPower = compoundWheelHeatPower(2, 0.72, 0.95, 1.02, Math.toRadians(9.0), NORMAL_LOAD_PER_WHEEL * 1.25);
        double rearHeatPower = compoundWheelHeatPower(2, 0.28, 0.45, 0.68, Math.toRadians(3.5), NORMAL_LOAD_PER_WHEEL * 0.85);

        double frontAfterTenSeconds = simulateSeconds(110.0, frontHeatPower, 0.72, 0.95, SPEED, 10.0);
        double rearAfterTenSeconds = simulateSeconds(140.0, rearHeatPower, 0.28, 0.45, SPEED, 10.0);

        assertTrue(frontAfterTenSeconds > 110.4, "front tyres at 110C should still respond within 10s under rapid cornering");
        assertTrue(rearAfterTenSeconds < 140.0, "rear tyres at 140C should cool when rear demand is not excessive");
        assertTrue(rearAfterTenSeconds - frontAfterTenSeconds < 29.0, "front/rear imbalance should begin closing within 10s");
    }

    @Test
    void harderCompoundsHeatSlowerFromRollingButNotFromPureKineticSlip() {
        double hardRolling = compoundWheelHeatPower(0, 0.08, 0.02, 0.10, 0.0);
        double softRolling = compoundWheelHeatPower(4, 0.08, 0.02, 0.10, 0.0);
        double hardKineticSlip = compoundWheelHeatPower(0, 0.35, 0.70, 1.18, Math.toRadians(5.0));
        double softKineticSlip = compoundWheelHeatPower(4, 0.35, 0.70, 1.18, Math.toRadians(5.0));

        assertTrue(softRolling > hardRolling * 1.45, "soft compounds should heat faster from rolling work");
        assertEquals(hardKineticSlip, softKineticSlip, hardKineticSlip * 0.03, "pure kinetic slip heat should be nearly compound-neutral");
    }

    @Test
    void softCompoundsPayMoreNearSaturationDistortionHeat() {
        double hard = compoundWheelHeatPower(0, 0.08, 0.98, 0.92, Math.toRadians(5.0));
        double soft = compoundWheelHeatPower(4, 0.08, 0.98, 0.92, Math.toRadians(5.0));

        assertTrue(soft > hard * 1.12, "soft compounds should suffer more heat from near-saturation distortion");
    }
    @Test
    void brakingAndAccelerationGenerateMoreHeatThanCruising() {
        double cruise = aggregateHeatPower(0.12, 0.02, 0.08, 0.0);
        double acceleration = aggregateHeatPower(0.72, 0.05, 0.72, Math.toRadians(0.8));
        double braking = aggregateHeatPower(0.95, 0.08, 0.95, Math.toRadians(1.2));

        assertTrue(acceleration > cruise * 1.25, "rapid acceleration should strongly out-heat cruising");
        assertTrue(braking > cruise * 1.55, "heavy braking should strongly out-heat cruising");
        assertTrue(braking > acceleration, "heavy braking should be the highest heat case here");
    }

    @Test
    void wheelLoadMultiplierUsesIndividualTyreForceNotTotalCarAcceleration() {
        double lightlyLoadedWheel = VehiclePhysics.tyreLoadHeatMultiplier(250.0, 200.0, NORMAL_LOAD_PER_WHEEL);
        double stressedWheel = VehiclePhysics.tyreLoadHeatMultiplier(1_600.0, 1_450.0, NORMAL_LOAD_PER_WHEEL);

        assertTrue(stressedWheel > lightlyLoadedWheel);
    }

    @Test
    void perWheelHeatDeltaDivergesWithEachTyresOwnLoadAndForce() {
        double lowLoadWheel = VehiclePhysics.tyreHeatDeltaC(
            wheelHeatPower(0.10, 0.05, 0.08, 0.0, NORMAL_LOAD_PER_WHEEL * 0.75),
            COMPOUND_HEAT_GAIN,
            0.10 * NORMAL_LOAD_PER_WHEEL * 0.75,
            0.05 * NORMAL_LOAD_PER_WHEEL * 0.75,
            NORMAL_LOAD_PER_WHEEL * 0.75,
            0.05
        );
        double outsideLoadedWheel = VehiclePhysics.tyreHeatDeltaC(
            wheelHeatPower(0.45, 0.95, 0.88, Math.toRadians(7.5), NORMAL_LOAD_PER_WHEEL * 1.35),
            COMPOUND_HEAT_GAIN,
            0.45 * NORMAL_LOAD_PER_WHEEL * 1.35,
            0.95 * NORMAL_LOAD_PER_WHEEL * 1.35,
            NORMAL_LOAD_PER_WHEEL * 1.35,
            0.05
        );

        assertTrue(outsideLoadedWheel > lowLoadWheel * 2.0);
    }

    @Test
    void slipAngleGeneratesHeatBeforeSaturation() {
        double straightForce = wheelHeatPower(0.05, 0.45, 0.60, Math.toRadians(0.5));
        double slipAngleForce = wheelHeatPower(0.05, 0.45, 0.60, Math.toRadians(8.0));

        assertTrue(slipAngleForce > straightForce * 1.8);
    }

    @Test
    void saturationHeatSpikesOnlyNearFullDemand() {
        double subLimit = wheelHeatPower(0.15, 0.50, 0.85, Math.toRadians(3.0));
        double saturated = wheelHeatPower(0.15, 0.50, 1.18, Math.toRadians(3.0));

        assertTrue(saturated > subLimit * 1.35);
    }

    @Test
    void brakeHeatBiasAddsFrontTemperatureResponse() {
        double baseFrontHeat = wheelHeatPower(0.72, 0.06, 0.92, Math.toRadians(0.8), NORMAL_LOAD_PER_WHEEL * 1.35);
        double baseRearHeat = wheelHeatPower(0.40, 0.03, 0.72, Math.toRadians(0.4), NORMAL_LOAD_PER_WHEEL * 0.65);
        double brakeHeat = 7_000.0;
        double frontHeat = baseFrontHeat + brakeHeat * 0.58;
        double rearHeat = baseRearHeat + brakeHeat * 0.42;

        assertTrue(frontHeat > rearHeat * 1.35, "brake plate heat should add a front-biased tyre temperature response");
    }

    @Test
    void brakingLoadTransferHeatsFrontMoreThanRear() {
        double frontNormalLoad = NORMAL_LOAD_PER_WHEEL * 1.35;
        double rearNormalLoad = NORMAL_LOAD_PER_WHEEL * 0.65;
        double frontHeat = wheelHeatPower(0.95, 0.08, 0.96, Math.toRadians(1.0), frontNormalLoad);
        double rearHeat = wheelHeatPower(0.42, 0.04, 0.94, Math.toRadians(0.5), rearNormalLoad);

        assertTrue(frontHeat > rearHeat * 1.3, "front tyres should generate at least 1.3x rear heat power under braking");
    }

    @Test
    void kineticSlipHeatScalesWithSpeedWhenTyreIsOverLimit() {
        double slow = wheelHeatPower(0.35, 0.70, 1.22, Math.toRadians(5.0), NORMAL_LOAD_PER_WHEEL, SPEED * 0.45);
        double fast = wheelHeatPower(0.35, 0.70, 1.22, Math.toRadians(5.0), NORMAL_LOAD_PER_WHEEL, SPEED * 1.25);

        assertTrue(fast > slow * 2.4, "kinetic slipping should convert significant speed into direct friction power");
    }

    @Test
    void oversteerPowerHeatsRearMoreThanFront() {
        double front = wheelHeatPower(0.12, 0.52, 0.72, Math.toRadians(5.0));
        double rear = wheelHeatPower(0.85, 0.72, 1.18, Math.toRadians(8.5));

        assertTrue(rear > front * 1.7, "rear tyres should heat quickly under oversteer and excessive power");
    }

    @Test
    void understeerAggressiveSteeringHeatsFrontMoreThanRear() {
        double front = wheelHeatPower(0.20, 0.92, 1.14, Math.toRadians(11.0));
        double rear = wheelHeatPower(0.18, 0.52, 0.72, Math.toRadians(4.0));

        assertTrue(front > rear * 1.75, "front tyres should heat quickly under understeer and excessive steering");
    }

    @Test
    void outsideLoadedTyreRunsMateriallyHotterThroughCorners() {
        double insideHeatPower = wheelHeatPower(0.22, 0.55, 0.62, Math.toRadians(4.0), NORMAL_LOAD_PER_WHEEL * 0.70);
        double outsideHeatPower = wheelHeatPower(0.38, 1.05, 1.08, Math.toRadians(8.0), NORMAL_LOAD_PER_WHEEL * 1.40);
        double inside = VehiclePhysics.simulateTyreEquilibriumC(75.0, insideHeatPower, COMPOUND_HEAT_GAIN, 0.22, 0.55, SPEED, 4 * 60 * 20);
        double outside = VehiclePhysics.simulateTyreEquilibriumC(75.0, outsideHeatPower, COMPOUND_HEAT_GAIN, 0.38, 1.05, SPEED, 4 * 60 * 20);

        assertTrue(outside > inside + 10.0, "outside tyres should be at least 10C hotter through sustained corners");
    }

    @Test
    void waterSurfaceCoolingBoostsTemperatureDrop() {
        double dryCooling = VehiclePhysics.tyreCoolingDeltaC(95.0, SPEED, 0.05);
        double waterCooling = dryCooling * 4.0;

        assertTrue(waterCooling > dryCooling * 3.5);
    }

    @Test
    void exponentialCoolingMatchesNewtonDecay() {
        double rate = VehiclePhysics.TYRE_STATIONARY_COOLING_PER_SECOND + VehiclePhysics.TYRE_WIND_COOLING_PER_MPS_SECOND * SPEED + VehiclePhysics.tyreHotCoolingRate(115.0);
        double expected = (115.0 - VehiclePhysics.TYRE_AMBIENT_TEMPERATURE_C) * (1.0 - Math.exp(-rate * 0.05));

        assertEquals(expected, VehiclePhysics.tyreCoolingDeltaC(115.0, SPEED, 0.05), 1.0E-12);
    }

    @Test
    void surfaceSpikeDoesNotInstantlyHeatCarcass() {
        VehiclePhysics.TyreThermalState state = new VehiclePhysics.TyreThermalState(75.0, 75.0, 0.0);
        for (int tick = 0; tick < 8; tick++) {
            state = VehiclePhysics.nextTyreThermalState(
                state.surfaceTemperatureC(), state.carcassTemperatureC(), state.slipExposure(),
                95_000.0, 0.0, 1.0, 38.0, 1.0, 1.0, 1.0, 1.22, Math.toRadians(9.0), 0.05, true
            );
        }

        assertTrue(state.surfaceTemperatureC() > state.carcassTemperatureC() + 0.15, "surface should respond faster than carcass: surface=" + state.surfaceTemperatureC() + " carcass=" + state.carcassTemperatureC());
        assertTrue(state.carcassTemperatureC() < 90.0, "one short slide should not make the carcass overheat");
    }

    @Test
    void frictionHeatRaisesSurfaceMateriallyFasterThanCarcass() {
        VehiclePhysics.TyreThermalState state = new VehiclePhysics.TyreThermalState(75.0, 75.0, 0.0);
        for (int tick = 0; tick < 20 * 5; tick++) {
            state = VehiclePhysics.nextTyreThermalState(
                state.surfaceTemperatureC(), state.carcassTemperatureC(), state.slipExposure(),
                24_000.0, 0.0, 1.0, 45.0, 1.0, 1.0, 1.0,
                0.72, Math.toRadians(3.0), 0.05, true
            );
        }

        double surfaceRise = state.surfaceTemperatureC() - 75.0;
        double carcassRise = state.carcassTemperatureC() - 75.0;
        assertTrue(surfaceRise > carcassRise * 1.5,
            "friction heat should lead at the surface: surfaceRise=" + surfaceRise + " carcassRise=" + carcassRise);
    }

    @Test
    void thermalPowerSplitConservesGeneratedEnergy() {
        assertEquals(1.0, VehiclePhysics.TYRE_SURFACE_FRICTION_HEAT_FRACTION
            + VehiclePhysics.TYRE_CARCASS_FRICTION_HEAT_FRACTION, 1.0E-12);
        assertEquals(0.90, VehiclePhysics.TYRE_BRAKE_TO_CARCASS_HEAT_FRACTION, 1.0E-12,
            "brake heat should remain strongly carcass-biased");
    }

    @Test
    void sustainedSlipEventuallyTransfersHeatIntoCarcass() {
        VehiclePhysics.TyreThermalState state = new VehiclePhysics.TyreThermalState(75.0, 75.0, 0.0);
        for (int tick = 0; tick < 20 * 45; tick++) {
            state = VehiclePhysics.nextTyreThermalState(
                state.surfaceTemperatureC(), state.carcassTemperatureC(), state.slipExposure(),
                95_000.0, 0.0, 1.0, 38.0, 1.0, 1.0, 1.0, 1.22, Math.toRadians(9.0), 0.05, true
            );
        }

        assertTrue(state.carcassTemperatureC() > 95.0, "sustained abuse should heat the carcass");
        assertTrue(state.slipExposure() > 0.8, "sustained abuse should maintain high exposure");
    }

    @Test
    void isolatedWheelExposureDoesNotHeatAnotherWheel() {
        VehiclePhysics.TyreThermalState rear = new VehiclePhysics.TyreThermalState(75.0, 75.0, 0.0);
        VehiclePhysics.TyreThermalState front = new VehiclePhysics.TyreThermalState(75.0, 75.0, 0.0);
        for (int tick = 0; tick < 20; tick++) {
            rear = VehiclePhysics.nextTyreThermalState(
                rear.surfaceTemperatureC(), rear.carcassTemperatureC(), rear.slipExposure(),
                85_000.0, 0.0, 1.0, 36.0, 1.0, 1.0, 1.0, 1.25, Math.toRadians(8.0), 0.05, true
            );
            front = VehiclePhysics.nextTyreThermalState(
                front.surfaceTemperatureC(), front.carcassTemperatureC(), front.slipExposure(),
                0.0, 0.0, 1.0, 36.0, 1.0, 1.0, 1.0, 0.45, Math.toRadians(0.2), 0.05, true
            );
        }

        assertTrue(rear.carcassTemperatureC() > front.carcassTemperatureC() + 0.03, "rear abuse must remain wheel-local: rear=" + rear.carcassTemperatureC() + " front=" + front.carcassTemperatureC());
        assertTrue(front.slipExposure() < 0.2, "clean front wheel must release exposure");
    }

    @Test
    void noGroundContactGeneratesNoHeat() {
        VehiclePhysics.TyreThermalState state = new VehiclePhysics.TyreThermalState(95.0, 90.0, 0.8);
        for (int tick = 0; tick < 20 * 30; tick++) {
            state = VehiclePhysics.nextTyreThermalState(
                state.surfaceTemperatureC(), state.carcassTemperatureC(), state.slipExposure(),
                200_000.0, 0.0, 1.4, 50.0, 1.0, 1.0, 1.0, 1.4, Math.toRadians(12.0), 0.05, false
            );
        }

        assertTrue(state.surfaceTemperatureC() < 95.0, "airborne tyre surface should cool");
        assertTrue(state.carcassTemperatureC() < 90.0, "airborne tyre carcass should cool");
        assertTrue(state.slipExposure() < 0.1, "airborne tyre should release slip exposure");
    }

    @Test
    void brakeHeatSplitConservesTotalPowerAndFrontBias() {
        double frontPerTyre = VehiclePhysics.tyreBrakeHeatPowerPerTyre(1.0, 7_000.0, 0.58);
        double rearPerTyre = VehiclePhysics.tyreBrakeHeatPowerPerTyre(1.0, 7_000.0, 0.42);

        assertEquals(7_000.0, frontPerTyre * 2.0 + rearPerTyre * 2.0, 1.0E-9);
        assertTrue(frontPerTyre > rearPerTyre, "front-biased braking must heat each front tyre more than each rear tyre");
    }

    @Test
    void brakeHeatPrimarilyWarmsCarcass() {
        VehiclePhysics.TyreThermalState state = new VehiclePhysics.TyreThermalState(75.0, 75.0, 0.0);
        for (int tick = 0; tick < 20 * 20; tick++) {
            state = VehiclePhysics.nextTyreThermalState(
                state.surfaceTemperatureC(), state.carcassTemperatureC(), state.slipExposure(),
                0.0, 12_000.0, 1.0, 45.0, 1.0, 1.0, 1.0, 0.90, Math.toRadians(1.0), 0.05, true
            );
        }

        assertTrue(state.carcassTemperatureC() > state.surfaceTemperatureC() + 1.0, "brake conduction should favor the carcass");
        assertTrue(state.carcassTemperatureC() > 79.0, "sustained braking should materially warm the carcass");
    }

    @Test
    void softerCompoundsHeatFasterButDoNotChangeThermalLayerInertia() {
        double[] finalSurface = new double[5];
        for (int compound = 0; compound < finalSurface.length; compound++) {
            VehiclePhysics.TyreThermalState state = new VehiclePhysics.TyreThermalState(75.0, 75.0, 0.0);
            double compoundGain = 0.78 + compound * 0.145;
            for (int tick = 0; tick < 20 * 30; tick++) {
                state = VehiclePhysics.nextTyreThermalState(
                    state.surfaceTemperatureC(), state.carcassTemperatureC(), state.slipExposure(),
                    22_000.0, 0.0, compoundGain, 42.0, 1.0, 1.0, 1.0, 0.72, Math.toRadians(2.0), 0.05, true
                );
            }
            finalSurface[compound] = state.surfaceTemperatureC();
        }

        for (int compound = 1; compound < finalSurface.length; compound++) {
            assertTrue(finalSurface[compound] > finalSurface[compound - 1], "softer compounds should warm faster in the same trace");
        }
    }

    @Test
    void representativeLapTraceHeatsUnderLoadCoolsOnStraightsAndStaysBounded() {
        VehiclePhysics.TyreThermalState state = new VehiclePhysics.TyreThermalState(75.0, 75.0, 0.0);
        double hottest = 0.0;

        for (int lap = 1; lap <= 12; lap++) {
            state = runLapSegment(state, 35.0, 75.0, 0.16, 0.03, 0.18, Math.toRadians(0.5), 0.0);
            double afterStraight = bulkTemperature(state);
            state = runLapSegment(state, 10.0, 58.0, 0.88, 0.10, 0.94, Math.toRadians(1.2), 7_000.0);
            state = runLapSegment(state, 35.0, 55.0, 0.24, 0.76, 0.80, Math.toRadians(5.0), 0.0);
            state = runLapSegment(state, 10.0, 52.0, 0.72, 0.14, 0.78, Math.toRadians(1.5), 0.0);
            double afterLoadedSections = bulkTemperature(state);
            assertTrue(afterLoadedSections > afterStraight,
                "braking and cornering must add heat relative to the preceding straight");
            hottest = Math.max(hottest, Math.max(state.surfaceTemperatureC(), state.carcassTemperatureC()));
        }

        double settled = bulkTemperature(state);
        assertTrue(settled >= VehiclePhysics.TYRE_AMBIENT_TEMPERATURE_C && settled < 120.0,
            "the synthetic trace must converge without dictating real-lap equilibrium: " + settled);
        assertTrue(hottest < 125.0, "smooth representative laps must not enter severe overheating: " + hottest);
    }

    @Test
    void representativeStraightCoolsAHotTyre() {
        VehiclePhysics.TyreThermalState state = new VehiclePhysics.TyreThermalState(118.0, 114.0, 0.0);
        double before = bulkTemperature(state);

        state = runLapSegment(state, 20.0, 75.0, 0.08, 0.01, 0.10, Math.toRadians(0.2), 0.0);

        assertTrue(bulkTemperature(state) < before - 0.5,
            "a hot tyre must cool during straight-line cruising: before=" + before + " after=" + bulkTemperature(state));
    }

    @Test
    void gripCurveHasBroadPeakAndProgressiveOverheatingLoss() {
        assertEquals(1.0, VehiclePhysics.tyreTemperatureGripMultiplier(4, 105.0), 1.0E-9);
        assertTrue(VehiclePhysics.tyreTemperatureGripMultiplier(4, 125.0) >= 0.93);
        assertTrue(VehiclePhysics.tyreTemperatureGripMultiplier(2, 125.0) >= 0.95);
        assertTrue(VehiclePhysics.tyreTemperatureGripMultiplier(0, 125.0) >= 0.98);
        assertTrue(VehiclePhysics.tyreTemperatureGripMultiplier(2, 140.0)
            < VehiclePhysics.tyreTemperatureGripMultiplier(2, 125.0));
        assertTrue(VehiclePhysics.tyreTemperatureGripMultiplier(2, 150.0)
            < VehiclePhysics.tyreTemperatureGripMultiplier(2, 140.0));
    }

    @Test
    void collinearTorqueCutAndReapplicationDoNotCountAsDirectionChange() {
        assertEquals(0.0, VehiclePhysics.tyreDirectionChangeSeverity(0.80, 0.0, 0.05, 0.0), 1.0E-12);
        assertEquals(0.0, VehiclePhysics.tyreDirectionChangeSeverity(0.05, 0.0, 0.80, 0.0), 1.0E-12);
    }

    @Test
    void loadedForceRotationAndReversalCountAsDirectionChange() {
        double cornerTransition = VehiclePhysics.tyreDirectionChangeSeverity(0.70, 0.05, 0.10, 0.75);
        double longitudinalReversal = VehiclePhysics.tyreDirectionChangeSeverity(-0.75, 0.0, 0.70, 0.0);

        assertTrue(cornerTransition > 0.5, "rapid loaded longitudinal-to-lateral transition should count");
        assertTrue(longitudinalReversal > 0.9, "loaded braking-to-power reversal should count");
    }

    @Test
    void unloadedDirectionChangeIsNaturallySuppressed() {
        double unloaded = VehiclePhysics.tyreDirectionChangeSeverity(0.04, 0.0, 0.0, 0.05);
        double loaded = VehiclePhysics.tyreDirectionChangeSeverity(0.70, 0.0, 0.0, 0.75);

        assertTrue(unloaded < 0.001);
        assertTrue(loaded > unloaded * 100.0);
    }

    private static VehiclePhysics.TyreThermalState runLapSegment(VehiclePhysics.TyreThermalState state,
            double seconds, double speed, double longitudinalG, double lateralG, double demand,
            double slipAngle, double brakeHeatPower) {
        double frictionHeatPower = wheelHeatPower(longitudinalG, lateralG, demand, slipAngle, NORMAL_LOAD_PER_WHEEL, speed);
        int ticks = (int) Math.round(seconds / 0.05);
        for (int tick = 0; tick < ticks; tick++) {
            state = VehiclePhysics.nextTyreThermalState(
                state.surfaceTemperatureC(), state.carcassTemperatureC(), state.slipExposure(),
                frictionHeatPower, brakeHeatPower, 1.0, speed, 1.0, 1.0, 1.0,
                demand, slipAngle, 0.05, true
            );
        }
        return state;
    }

    private static double bulkTemperature(VehiclePhysics.TyreThermalState state) {
        return state.surfaceTemperatureC() * 0.25 + state.carcassTemperatureC() * 0.75;
    }

    private static double compoundWheelHeatPower(int compound, double longitudinalG, double lateralG, double demand, double slipAngle) {
        return compoundWheelHeatPower(compound, longitudinalG, lateralG, demand, slipAngle, NORMAL_LOAD_PER_WHEEL);
    }

    private static double compoundWheelHeatPower(int compound, double longitudinalG, double lateralG, double demand, double slipAngle, double normalLoad) {
        double longitudinalForce = longitudinalG * normalLoad;
        double lateralForce = lateralG * normalLoad;
        double rollingHeat = VehiclePhysics.tyreRollingHeatPowerWatts(normalLoad, SPEED, ROLLING_RESISTANCE) * rollingHeatMultiplier(compound);
        double nearSaturation = VehiclePhysics.tyreLateralNearSaturation(lateralForce, normalLoad);
        double nearSaturationHeat = Math.abs(lateralForce) * nearSaturation * nearSaturation * SPEED * 0.55 * nearSaturationHeatMultiplier(compound) * VehiclePhysics.TYRE_SLIP_HEAT_FRACTION;
        double slipHeat = VehiclePhysics.tyreSlipHeatPowerWatts(longitudinalForce, lateralForce, normalLoad, SPEED, demand, slipAngle);
        return rollingHeat + nearSaturationHeat + slipHeat;
    }

    private static double simulateSeconds(double initialTemperatureC, double heatPowerWatts, double longitudinalG, double lateralG, double speedMetersPerSecond, double seconds) {
        return VehiclePhysics.simulateTyreEquilibriumC(initialTemperatureC, heatPowerWatts, COMPOUND_HEAT_GAIN, longitudinalG, lateralG, speedMetersPerSecond, (int) Math.round(seconds / 0.05));
    }

    private static double rollingHeatMultiplier(int compound) {
        return switch (compound) {
            case 0 -> 0.78;
            case 1 -> 0.90;
            case 2 -> 1.00;
            case 3 -> 1.18;
            default -> 1.36;
        };
    }

    private static double nearSaturationHeatMultiplier(int compound) {
        return switch (compound) {
            case 0 -> 0.82;
            case 1 -> 0.92;
            case 2 -> 1.00;
            case 3 -> 1.20;
            default -> 1.42;
        };
    }

    private static double aggregateHeatPower(double longitudinalG, double lateralG, double demand, double slipAngle) {
        return wheelHeatPower(longitudinalG, lateralG, demand, slipAngle);
    }

    private static double wheelHeatPower(double longitudinalG, double lateralG, double demand, double slipAngle) {
        return wheelHeatPower(longitudinalG, lateralG, demand, slipAngle, NORMAL_LOAD_PER_WHEEL);
    }

    private static double wheelHeatPower(double longitudinalG, double lateralG, double demand, double slipAngle, double normalLoad) {
        return wheelHeatPower(longitudinalG, lateralG, demand, slipAngle, normalLoad, SPEED);
    }

    private static double wheelHeatPower(double longitudinalG, double lateralG, double demand, double slipAngle, double normalLoad, double speed) {
        double longitudinalForce = longitudinalG * normalLoad;
        double lateralForce = lateralG * normalLoad;
        return VehiclePhysics.tyreRollingHeatPowerWatts(normalLoad, speed, ROLLING_RESISTANCE)
            + VehiclePhysics.tyreSlipHeatPowerWatts(longitudinalForce, lateralForce, normalLoad, speed, demand, slipAngle);
    }
}
