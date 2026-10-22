package com.openwheelracing.content.weather;

/** Inclusive rectangular climate region. Later configured regions take priority. */
public record WeatherRegion(String name, int minX, int minZ, int maxX, int maxZ,
                            double wetChance, double stormChance, double snowChance,
                            int periodTicks, long seed) {
    public WeatherRegion {
        if (name == null || name.isBlank() || name.length() > 64 || minX > maxX || minZ > maxZ
            || !probability(wetChance) || !probability(stormChance) || !probability(snowChance)
            || periodTicks < 1200 || periodTicks > 72000) {
            throw new IllegalArgumentException("Invalid weather region");
        }
    }

    private static boolean probability(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}
