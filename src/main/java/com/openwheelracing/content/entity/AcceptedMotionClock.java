package com.openwheelracing.content.entity;

/** Server-paced admission of client frames; burst delivery does not collapse lap time. */
final class AcceptedMotionClock {
    static final long STEP_NANOS = 50_000_000L;
    static final int BURST_FRAMES = 12;
    record Interval(double start, double end) {}
    private boolean initialized, resuming;
    void resumeAfterHold() { resuming = true; }
    private int firstSequence, lastSequence;
    private double firstTime, timeOffset, tokens = BURST_FRAMES;
    private long lastBudget, anchoredAt;

    boolean ready(long now) {
        if (!initialized) return true;
        tokens = Math.min(BURST_FRAMES, tokens + Math.max(0, now - lastBudget) / (double) STEP_NANOS);
        lastBudget = now;
        return tokens >= 1;
    }

    Interval accept(int sequence, double serverTick, long now) {
        if (!initialized) {
            initialized = true; firstSequence = sequence; lastSequence = sequence - 1;
            firstTime = serverTick; anchoredAt = now; lastBudget = now;
        }
        if (sequence - lastSequence != 1) throw new IllegalArgumentException("Non-contiguous movement");
        if (!ready(now)) throw new IllegalStateException("Frame pacing budget exhausted");
        tokens--;
        lastSequence = sequence;
        double sequenceEnd = firstTime + (sequence - firstSequence);
        if (resuming) {
            timeOffset = Math.max(timeOffset, firstTime + Math.max(0, now - anchoredAt) / (double) STEP_NANOS - sequenceEnd);
            resuming = false;
        }
        // Bounded jitter grace must not let a frozen/slowed client stop the race clock.
        double earliest = firstTime + Math.max(0, now - anchoredAt) / (double) STEP_NANOS - BURST_FRAMES;
        timeOffset = Math.max(timeOffset, earliest - sequenceEnd);
        double end = sequenceEnd + timeOffset;
        return new Interval(end - 1, end);
    }

    double now(long nanos, double fallback) {
        return initialized ? Math.max(firstTime + (lastSequence - firstSequence) + timeOffset,
            firstTime + Math.max(0, nanos - anchoredAt) / (double) STEP_NANOS) : fallback;
    }
}
