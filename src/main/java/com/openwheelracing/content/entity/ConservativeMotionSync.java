package com.openwheelracing.content.entity;

/** Normal driving keeps client prediction unless confirmed position drift persists. */
final class ConservativeMotionSync {
    static final double POSITION_ERROR_SQUARED = 1.5 * 1.5;
    static final int SUSTAINED_TICKS = 6;
    static final int CORRECTION_INTERVAL_TICKS = 12;
    private long lastObserved = Long.MIN_VALUE;
    private long largeSince = Long.MIN_VALUE;
    private long lastCorrection = Long.MIN_VALUE;

    boolean shouldCorrect(long tick, boolean confirmed, double positionErrorSquared) {
        if (tick <= lastObserved) return false;
        boolean consecutive = lastObserved != Long.MIN_VALUE && tick - lastObserved == 1;
        lastObserved = tick;
        if (!confirmed || !Double.isFinite(positionErrorSquared) || positionErrorSquared <= POSITION_ERROR_SQUARED) {
            largeSince = Long.MIN_VALUE;
            return false;
        }
        if (!consecutive || largeSince == Long.MIN_VALUE) largeSince = tick;
        if (tick - largeSince < SUSTAINED_TICKS
                || lastCorrection != Long.MIN_VALUE && tick - lastCorrection < CORRECTION_INTERVAL_TICKS) return false;
        lastCorrection = tick;
        largeSince = tick;
        return true;
    }

    void reset() {
        lastObserved = Long.MIN_VALUE;
        largeSince = Long.MIN_VALUE;
        lastCorrection = Long.MIN_VALUE;
    }
}
