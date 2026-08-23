package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ErsPowerFlowTest {
    @Test
    void superclippingConsumesIceBeforeApplyingWheelBraking() {
        VehiclePhysics.ErsWheelPower flow = VehiclePhysics.ersWheelPower(430_000.0, -350_000.0);

        assertEquals(80_000.0, flow.propulsionWatts(), 1.0E-9);
        assertEquals(0.0, flow.regenerativeBrakingWatts(), 1.0E-9);
    }

    @Test
    void regenBeyondAvailableIceAppliesOnlyTheRemainderToTheWheels() {
        VehiclePhysics.ErsWheelPower flow = VehiclePhysics.ersWheelPower(120_000.0, -350_000.0);

        assertEquals(0.0, flow.propulsionWatts(), 1.0E-9);
        assertEquals(230_000.0, flow.regenerativeBrakingWatts(), 1.0E-9);
    }

    @Test
    void unusedIceIsHarvestedWithoutChargingScheduledRegenTwice() {
        VehiclePhysics.ErsEnergyFlow flow = VehiclePhysics.reconcileErsEnergy(
            430_000.0, 0.0, 350_000.0, 40_000.0, 350_000.0);

        assertEquals(0.0, flow.positiveErsUsedJoules(), 1.0E-9);
        assertEquals(0.0, flow.positiveErsRefundJoules(), 1.0E-9);
        assertEquals(40_000.0, flow.additionalIceHarvestJoules(), 1.0E-9);
    }

    @Test
    void launchUsesErsEnergyThatActuallyReachesTheTyres() {
        VehiclePhysics.ErsEnergyFlow flow = VehiclePhysics.reconcileErsEnergy(
            20_000.0, 10_000.0, 0.0, 27_000.0, 20_000.0);

        assertEquals(7_000.0, flow.positiveErsUsedJoules(), 1.0E-9);
        assertEquals(3_000.0, flow.positiveErsRefundJoules(), 1.0E-9);
        assertEquals(0.0, flow.additionalIceHarvestJoules(), 1.0E-9);
    }
}
