package com.openwheelracing.content.ai;

public final class BasicAiRouteSafety {
    public static final double BODY_HALF_WIDTH = 0.95;
    public static final double SAFETY_MARGIN = 0.35;
    public static final double RECOVERY_MARGIN = 1.5;

    private BasicAiRouteSafety() {
    }

    public static Assessment assess(double signedLateralDistance, double widthLeft, double widthRight,
                                    double previewSignedLateralDistance, double previewWidthLeft, double previewWidthRight) {
        double usableLeft = Math.max(0.0, widthLeft - BODY_HALF_WIDTH - SAFETY_MARGIN);
        double usableRight = Math.max(0.0, widthRight - BODY_HALF_WIDTH - SAFETY_MARGIN);
        double previewUsableLeft = Math.max(0.0, previewWidthLeft - BODY_HALF_WIDTH - SAFETY_MARGIN);
        double previewUsableRight = Math.max(0.0, previewWidthRight - BODY_HALF_WIDTH - SAFETY_MARGIN);
        State current = state(signedLateralDistance, usableLeft, usableRight);
        State preview = state(previewSignedLateralDistance, previewUsableLeft, previewUsableRight);
        if (current == State.UNSAFE || preview == State.UNSAFE) {
            int direction = recoveryDirection(signedLateralDistance, usableLeft, usableRight);
            if (direction == 0) {
                direction = recoveryDirection(previewSignedLateralDistance, previewUsableLeft, previewUsableRight);
            }
            return new Assessment(State.UNSAFE, direction);
        }
        if (current == State.RECOVERING || preview == State.RECOVERING) {
            return new Assessment(State.RECOVERING, recoveryDirection(signedLateralDistance, usableLeft, usableRight));
        }
        return new Assessment(State.SAFE, 0);
    }

    private static State state(double lateral, double usableLeft, double usableRight) {
        if (lateral > usableLeft + RECOVERY_MARGIN || lateral < -usableRight - RECOVERY_MARGIN) {
            return State.UNSAFE;
        }
        if (lateral > usableLeft || lateral < -usableRight) {
            return State.RECOVERING;
        }
        return State.SAFE;
    }

    private static int recoveryDirection(double lateral, double usableLeft, double usableRight) {
        if (lateral > usableLeft) {
            return -1;
        }
        if (lateral < -usableRight) {
            return 1;
        }
        return 0;
    }

    public record Assessment(State state, int recoveryDirection) {
        public boolean permitsFullThrottle() {
            return state == State.SAFE;
        }
    }

    public enum State {
        SAFE,
        RECOVERING,
        UNSAFE
    }
}
