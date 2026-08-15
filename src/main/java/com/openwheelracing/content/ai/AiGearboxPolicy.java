package com.openwheelracing.content.ai;

public final class AiGearboxPolicy {
    public static final int PRE_REV_TICKS = 12;
    public static final int SHIFT_COOLDOWN_TICKS = 8;
    public static final double UPSHIFT_RPM = 0.90;
    public static final double DOWNSHIFT_RPM = 0.56;
    public static final double DOWNSHIFT_GUARD_RPM = 0.90;

    public Decision decide(State state) {
        int cooldown = Math.max(0, state.cooldownTicks());
        if (state.launchTicks() > 0) {
            return new Decision(Action.PRE_REV, cooldown == 0 ? 0 : cooldown - 1);
        }
        if (cooldown > 0) {
            return new Decision(Action.HOLD, cooldown - 1);
        }
        int gear = Math.max(1, Math.min(state.maxForwardGear(), state.gear()));
        if (state.throttle() > 0.05 && state.brake() < 0.05 && gear < state.maxForwardGear()
            && state.rpm() >= state.redlineRpm() * UPSHIFT_RPM) {
            return new Decision(Action.SHIFT_UP, SHIFT_COOLDOWN_TICKS);
        }
        if (gear > 1 && state.rpm() <= state.redlineRpm() * DOWNSHIFT_RPM
            && state.projectedLowerGearRpm() <= state.redlineRpm() * DOWNSHIFT_GUARD_RPM) {
            return new Decision(Action.SHIFT_DOWN, SHIFT_COOLDOWN_TICKS);
        }
        return new Decision(Action.HOLD, 0);
    }

    public record State(int gear, int maxForwardGear, int rpm, int redlineRpm, float throttle, float brake,
                        int launchTicks, int cooldownTicks, int projectedLowerGearRpm) {
    }

    public record Decision(Action action, int cooldownTicks) {
    }

    public enum Action {
        PRE_REV,
        SHIFT_UP,
        SHIFT_DOWN,
        HOLD
    }
}
