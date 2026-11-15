package com.openwheelracing.client.debug;

import java.util.random.RandomGenerator;

/** Irregular loss/retransmission-like stalls, with normal delivery for most ticks. */
public final class IntermittentVehicleLag {
    private final RandomGenerator random;
    private int maximumStall;
    private long release;
    private long nextStall;

    public IntermittentVehicleLag(RandomGenerator random) { this.random = random; }

    public void configure(int maximumTicks, long now) {
        maximumStall = Math.max(0, Math.min(40, maximumTicks));
        release = now;
        nextStall = maximumStall == 0 ? Long.MAX_VALUE : now + quietTicks();
    }

    public long releaseAt(long now) {
        if (maximumStall == 0) return now;
        if (now < release) return release;
        if (now >= nextStall) {
            int minimum = Math.max(1, (maximumStall + 1) / 2);
            release = now + random.nextInt(minimum, maximumStall + 1);
            nextStall = release + quietTicks();
            return release;
        }
        return now;
    }

    private int quietTicks() { return random.nextInt(60, 141); }
}
