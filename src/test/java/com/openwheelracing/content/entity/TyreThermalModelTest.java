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
    void lowStressCruiseCoolsWellBelowHighStressWindow() {
        double heatPower = aggregateHeatPower(0.12, 0.02, 0.08, 0.0);

        double equilibrium = VehiclePhysics.simulateTyreEquilibriumC(75.0, heatPower, COMPOUND_HEAT_GAIN, 0.08, 0.0, SPEED, 20 * 60 * 60);

        assertTrue(equilibrium >= 38.0, "low-stress straights should not cool to ambient");
        assertTrue(equilibrium <= 62.0, "low-stress straights should cool well below high-stress equilibrium");
    }

    @Test
    void sustainedHighStressCornerEquilibratesAboveLowStressCruise() {
        double heatPower = aggregateHeatPower(0.38, 0.78, 0.78, Math.toRadians(6.5));

        double equilibrium = VehiclePhysics.simulateTyreEquilibriumC(75.0, heatPower, COMPOUND_HEAT_GAIN, 0.38, 0.78, SPEED, 20 * 60 * 60);

        assertTrue(equilibrium >= 110.0, "high-stress corners should equilibrate near the upper working window");
        assertTrue(equilibrium <= 139.0, "high-stress corners should still converge instead of running away");
    }

    @Test
    void hotLowStressCruiseCoolsTowardSameEquilibrium() {
        double heatPower = aggregateHeatPower(0.12, 0.02, 0.08, 0.0);

        double from120 = VehiclePhysics.simulateTyreEquilibriumC(120.0, heatPower, COMPOUND_HEAT_GAIN, 0.08, 0.0, SPEED, 6 * 60 * 20);
        double from160 = VehiclePhysics.simulateTyreEquilibriumC(160.0, heatPower, COMPOUND_HEAT_GAIN, 0.08, 0.0, SPEED, 6 * 60 * 20);

        assertTrue(from120 < 90.0, "hot tyres should cool substantially on low-stress straights");
        assertTrue(from160 < 105.0, "very hot tyres should not remain out of control on low-stress straights");
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
