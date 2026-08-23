package com.openwheelracing.client.input;

public enum KeyboardDrivingMode {
    ASSISTED_KEYBOARD,
    DIRECT_JOYSTICK;

    public KeyboardDrivingMode toggled() {
        return this == ASSISTED_KEYBOARD ? DIRECT_JOYSTICK : ASSISTED_KEYBOARD;
    }

    public boolean usesKeyboardAssistance() {
        return this == ASSISTED_KEYBOARD;
    }
}
