package com.openwheelracing.client.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeyboardPedalResponseTest {
    @Test
    void pressHasImmediateBiteAndReachesFullAuthority() {
        float value = KeyboardPedalResponse.next(0.0f, true, 0.30f, 0.30f, 0.08f, 0.05);
        assertTrue(value >= 0.30f);
        for (int tick = 0; tick < 6; tick++) {
            value = KeyboardPedalResponse.next(value, true, 0.30f, 0.30f, 0.08f, 0.05);
        }
        assertEquals(1.0f, value, 1.0E-6f);
    }

    @Test
    void releaseReturnsAuthorityQuickly() {
        float value = KeyboardPedalResponse.next(1.0f, false, 0.30f, 0.30f, 0.08f, 0.05);
        value = KeyboardPedalResponse.next(value, false, 0.30f, 0.30f, 0.08f, 0.05);
        assertEquals(0.0f, value, 1.0E-6f);
    }
}
