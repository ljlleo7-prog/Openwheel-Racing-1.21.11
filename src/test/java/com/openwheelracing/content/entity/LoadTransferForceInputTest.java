package com.openwheelracing.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoadTransferForceInputTest {

    @Test
    void drivenAxleLimitIncludesItsOwnRearwardLoadTransfer() {
        double staticRearLimit = 2.475 * 769.0 * 9.81 * 0.54;
        double loadedRearLimit = VehiclePhysics.drivenAxleForceWithLongitudinalLoadTransfer(
            staticRearLimit, 2.475, 0.27, 3.60);

        assertEquals(staticRearLimit / (1.0 - 2.475 * 0.27 / 3.60), loadedRearLimit, 1.0E-9);
        assertTrue(loadedRearLimit > staticRearLimit * 1.22);
        assertTrue(loadedRearLimit < staticRearLimit * 1.23);
    }
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
