package com.openwheelracing.content.entity;

final class LooseSurfacePhysics {
    private LooseSurfacePhysics() {
    }

    /** Rolling resistance plus speed-dependent energy lost displacing loose grains. */
    static double resistanceCoefficient(boolean sand, boolean gravel, boolean grass, double speedMetersPerSecond) {
        double speed = Math.max(0.0, speedMetersPerSecond);
        double impact = Math.min(1.0, speed * speed / 900.0);
        if (sand) return 0.035 + 0.10 * impact;
        if (gravel) return 0.025 + 0.08 * impact;
        if (grass) return 0.015 + 0.025 * impact;
        return 0.0;
    }

    /** Seeded bump pulses vary in timing, duration, and strength without frame-to-frame jitter. */
    static float bumpPitchDegrees(boolean loose, double speedMetersPerSecond, double tick, int seed) {
        if (!loose || speedMetersPerSecond <= 2.0) return 0.0f;
        int cycle = (int) Math.floor(tick / 12.0);
        double phase = tick - cycle * 12.0 - bumpOffset(cycle, seed);
        double duration = 7.0 + 2.0 * variation(cycle, seed, 1);
        if (phase < 0.0 || phase >= duration) return 0.0f;
        double amplitude = (1.0 + 1.5 * Math.min(1.0, (speedMetersPerSecond - 2.0) / 18.0))
            * (0.6 + 0.4 * variation(cycle, seed, 2));
        return (float) (amplitude * Math.sin(phase * Math.PI * 2.0 / duration));
    }

    /** Blocks/tick: bounded hops share the pitch pulse's seeded timing and strength. */
    static double bumpVelocity(boolean loose, double speedMetersPerSecond, int tick, int seed) {
        int cycle = Math.floorDiv(tick, 12);
        if (!loose || speedMetersPerSecond <= 2.0
                || Math.floorMod(tick, 12) != bumpOffset(cycle, seed)) return 0.0;
        return (0.025 + 0.025 * Math.min(1.0, (speedMetersPerSecond - 2.0) / 18.0))
            * (0.6 + 0.4 * variation(cycle, seed, 2));
    }

    private static int bumpOffset(int cycle, int seed) {
        return (int) (variation(cycle, seed, 0) * 4.0);
    }

    private static double variation(int cycle, int seed, int channel) {
        int value = seed ^ cycle * 0x9e3779b9 ^ channel * 0x632be5ab;
        value = (value ^ (value >>> 16)) * 0x7feb352d;
        value = (value ^ (value >>> 15)) * 0x846ca68b;
        value ^= value >>> 16;
        return Integer.toUnsignedLong(value) / 4294967296.0;
    }
}
