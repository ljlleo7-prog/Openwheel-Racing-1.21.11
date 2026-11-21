package com.openwheelracing.content.entity;

/** A repeated rejected path needs a fresh pedal action, rather than endlessly replaying held input. */
final class ClientMotionResumeGate {
    private boolean waiting;
    void block() { waiting = true; }
    void reset() { waiting = false; }
    boolean hold(float throttle, float brake) {
        if (waiting && throttle <= 0.05f && brake <= 0.05f) waiting = false;
        return waiting;
    }
}
