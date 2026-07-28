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

        assertTrue(equilibrium >= 52.0, "low-stress straights should not cool to ambient");
        assertTrue(equilibrium <= 68.0, "low-stress straights should cool well below high-stress equilibrium");
    }

    @Test
    void sustainedHighStressCornerEquilibratesAboveLowStressCruise() {
        double heatPower = aggregateHeatPower(0.38, 0.78, 0.78, Math.toRadians(6.5));

        double equilibrium = VehiclePhysics.simulateTyreEquilibriumC(75.0, heatPower, COMPOUND_HEAT_GAIN, 0.38, 0.78, SPEED, 20 * 60 * 60);

        assertTrue(equilibrium >= 76.0, "high-stress corners should heat far above low-stress straights");
        assertTrue(equilibrium <= 92.0, "high-stress corners should still converge instead of running away");
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

        assertTrue(slipAngleForce > straightForce * 1.2);
    }

    @Test
    void saturationHeatSpikesOnlyNearFullDemand() {
        double subLimit = wheelHeatPower(0.15, 0.50, 0.85, Math.toRadians(3.0));
        double saturated = wheelHeatPower(0.15, 0.50, 1.18, Math.toRadians(3.0));

        assertTrue(saturated > subLimit * 1.35);
    }

    @Test
    void waterSurfaceCoolingBoostsTemperatureDrop() {
        double dryCooling = VehiclePhysics.tyreCoolingDeltaC(95.0, SPEED, 0.05);
        double waterCooling = dryCooling * 4.0;

        assertTrue(waterCooling > dryCooling * 3.5);
    }

    @Test
    void exponentialCoolingMatchesNewtonDecay() {
        double rate = VehiclePhysics.TYRE_STATIONARY_COOLING_PER_SECOND + VehiclePhysics.TYRE_WIND_COOLING_PER_MPS_SECOND * SPEED;
        double expected = (115.0 - VehiclePhysics.TYRE_AMBIENT_TEMPERATURE_C) * (1.0 - Math.exp(-rate * 0.05));

        assertEquals(expected, VehiclePhysics.tyreCoolingDeltaC(115.0, SPEED, 0.05), 1.0E-12);
    }

    private static double aggregateHeatPower(double longitudinalG, double lateralG, double demand, double slipAngle) {
        return wheelHeatPower(longitudinalG, lateralG, demand, slipAngle);
    }

    private static double wheelHeatPower(double longitudinalG, double lateralG, double demand, double slipAngle) {
        return wheelHeatPower(longitudinalG, lateralG, demand, slipAngle, NORMAL_LOAD_PER_WHEEL);
    }

    private static double wheelHeatPower(double longitudinalG, double lateralG, double demand, double slipAngle, double normalLoad) {
        double longitudinalForce = longitudinalG * normalLoad;
        double lateralForce = lateralG * normalLoad;
        return VehiclePhysics.tyreRollingHeatPowerWatts(normalLoad, SPEED, ROLLING_RESISTANCE)
            + VehiclePhysics.tyreSlipHeatPowerWatts(longitudinalForce, lateralForce, normalLoad, SPEED, demand, slipAngle);
    }
}
