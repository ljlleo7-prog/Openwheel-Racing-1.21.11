package com.openwheelracing.content.block;

/** Spatially correlated transition times for natural wetting and drying, independent of tyre contact. */
public final class TrackMoistureField {
    private static final double BROAD_SCALE_BLOCKS = 96.0;
    private static final double MEDIUM_SCALE_BLOCKS = 40.0;
    private static final double DETAIL_SCALE_BLOCKS = 12.0;
    private static final double MEDIUM_WEIGHT = 0.4;
    private static final double DETAIL_WEIGHT = 0.15;
    private static final double NORMALIZATION = Math.sqrt(1 + MEDIUM_WEIGHT * MEDIUM_WEIGHT + DETAIL_WEIGHT * DETAIL_WEIGHT);
    private static final double THRESHOLD_STANDARD_DEVIATION = 0.15;

    private TrackMoistureField() {
    }

    public static double threshold(int x, int z, long weatherEpoch) {
        // Bound the tiny Gaussian tails so every block still respects stage endpoints.
        return Math.clamp(0.5 + THRESHOLD_STANDARD_DEVIATION * gaussian(x, z, weatherEpoch), 1e-6, 1.0 - 1e-6);
    }

    public static double gaussian(double x, double z, long weatherEpoch) {
        // Independent, rotated octaves avoid a visible shared grid while preserving Gaussian timing.
        double broad = octave(0.8 * x + 0.6 * z, -0.6 * x + 0.8 * z,
            BROAD_SCALE_BLOCKS, weatherEpoch);
        double medium = octave(0.6 * x - 0.8 * z + 137.3, 0.8 * x + 0.6 * z - 91.7,
            MEDIUM_SCALE_BLOCKS, weatherEpoch ^ 0x632BE59BD9B4E019L);
        double detail = octave(x + 47.1, z - 183.9,
            DETAIL_SCALE_BLOCKS, weatherEpoch ^ 0x94D049BB133111EBL);
        return (broad + MEDIUM_WEIGHT * medium + DETAIL_WEIGHT * detail) / NORMALIZATION;
    }

    private static double octave(double x, double z, double scale, long salt) {
        double gridX = x / scale;
        double gridZ = z / scale;
        int cellX = (int) Math.floor(gridX);
        int cellZ = (int) Math.floor(gridZ);
        double tx = fade(gridX - cellX);
        double tz = fade(gridZ - cellZ);
        double w00 = (1 - tx) * (1 - tz);
        double w10 = tx * (1 - tz);
        double w01 = (1 - tx) * tz;
        double w11 = tx * tz;
        double value = w00 * node(cellX, cellZ, salt) + w10 * node(cellX + 1, cellZ, salt)
            + w01 * node(cellX, cellZ + 1, salt) + w11 * node(cellX + 1, cellZ + 1, salt);
        // A normalized linear combination of independent Gaussian nodes is Gaussian everywhere,
        // including between lattice points. Unnormalized interpolation would shrink its variance.
        return value / Math.sqrt(w00 * w00 + w10 * w10 + w01 * w01 + w11 * w11);
    }

    private static double fade(double value) {
        return value * value * value * (value * (value * 6 - 15) + 10);
    }

    private static double node(int x, int z, long salt) {
        long position = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        long seed = position ^ salt * 0x9E3779B97F4A7C15L;
        double u = unit(seed);
        double v = unit(seed + 0x9E3779B97F4A7C15L);
        return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
    }

    private static double unit(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return (((value ^ value >>> 31) >>> 11) + 0.5) * 0x1.0p-53;
    }
}
