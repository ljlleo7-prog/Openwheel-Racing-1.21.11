package com.openwheelracing.content.entity;

import com.openwheelracing.network.VehicleRollbackNetwork;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RollbackUpdateOrderTest {
    @Test
    void delayedSyncAndDuplicateResolutionCannotRepeatARewind() {
        assertTrue(RollbackUpdateOrder.accepts(0, 1, 1, 1, VehicleRollbackNetwork.REWOUND));
        for (int i = 0; i < 1000; i++) {
            assertFalse(RollbackUpdateOrder.accepts(1, 1, 1, 1, VehicleRollbackNetwork.SYNC));
            assertFalse(RollbackUpdateOrder.accepts(1, 1, 1, 1, VehicleRollbackNetwork.REWOUND));
            assertFalse(RollbackUpdateOrder.accepts(1, 1, 0, 1, VehicleRollbackNetwork.CHECKING));
        }
    }

    @Test
    void checkingOnlyAppliesOnceAndCannotLeadTheEpoch() {
        assertTrue(RollbackUpdateOrder.accepts(1, 1, 1, 2, VehicleRollbackNetwork.CHECKING));
        assertFalse(RollbackUpdateOrder.accepts(1, 2, 1, 2, VehicleRollbackNetwork.CHECKING));
        assertFalse(RollbackUpdateOrder.accepts(1, 2, 2, 3, VehicleRollbackNetwork.CHECKING));
        assertTrue(RollbackUpdateOrder.accepts(1, 2, 2, 2, VehicleRollbackNetwork.CONFIRMED));
    }
}
