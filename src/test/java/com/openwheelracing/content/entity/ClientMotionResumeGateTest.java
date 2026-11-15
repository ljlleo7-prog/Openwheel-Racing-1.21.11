package com.openwheelracing.content.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientMotionResumeGateTest {
    @Test
    void normalDrivingNeverWaitsForAnAcknowledgment() {
        var gate = new ClientMotionResumeGate();
        for (int i = 0; i < 1000; i++) assertFalse(gate.hold(1, 0));
    }

    @Test
    void repeatedRejectionCannotReplayHeldPedalsIndefinitely() {
        var gate = new ClientMotionResumeGate();
        gate.block();
        for (int i = 0; i < 1000; i++) assertTrue(gate.hold(1, 0));
        assertTrue(gate.hold(0, 1));
        assertFalse(gate.hold(0, 0));
        assertFalse(gate.hold(1, 0));
    }
}
