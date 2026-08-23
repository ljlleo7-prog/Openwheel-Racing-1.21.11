package com.openwheelracing.client.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeyboardDrivingModeTest {
    @Test
    void toggleAlternatesBetweenExclusiveInputModes() {
        assertEquals(KeyboardDrivingMode.DIRECT_JOYSTICK,
            KeyboardDrivingMode.ASSISTED_KEYBOARD.toggled());
        assertEquals(KeyboardDrivingMode.ASSISTED_KEYBOARD,
            KeyboardDrivingMode.DIRECT_JOYSTICK.toggled());
    }

    @Test
    void onlyAssistedKeyboardModeEnablesKeyboardInterventions() {
        assertTrue(KeyboardDrivingMode.ASSISTED_KEYBOARD.usesKeyboardAssistance());
        assertFalse(KeyboardDrivingMode.DIRECT_JOYSTICK.usesKeyboardAssistance());
    }
}
