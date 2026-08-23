package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LoadTransferForceInputTest {
    @Test
    void powerRequestCannotTransferMoreLoadThanDrivenTyresCanSupport() {
        assertEquals(10_000.0,
            VehiclePhysics.tractionLimitedDriveForceForLoadTransfer(156_000.0, 10_000.0),
            1.0E-12);
    }

    @Test
    void ordinaryDriveRequestIsUnchanged() {
        assertEquals(6_000.0,
            VehiclePhysics.tractionLimitedDriveForceForLoadTransfer(6_000.0, 10_000.0),
            1.0E-12);
    }

    @Test
    void reverseRequestUsesSamePhysicalTractionBound() {
        assertEquals(-10_000.0,
            VehiclePhysics.tractionLimitedDriveForceForLoadTransfer(-30_000.0, 10_000.0),
            1.0E-12);
    }
}
