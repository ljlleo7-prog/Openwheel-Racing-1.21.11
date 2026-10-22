package com.openwheelracing.content.weather;

/** Stateless schedule: forecasts and live weather always evaluate the same function. */
public final class WeatherSchedule {
    private static final long SLOT_SALT = 0x9E3779B97F4A7C15L;
    private static final long REFRESH_SALT = 0xD1B54A32D192ED03L;
    private static final double PERSISTENCE = 0.8;

    private WeatherSchedule() {
    }

    public enum Condition { CLEAR, RAIN, THUNDER, SNOW }

    public record Sample(Condition condition, long startsAt, long endsAt) {
        public boolean wet() { return condition != Condition.CLEAR; }
        public boolean thunder() { return condition == Condition.THUNDER; }
    }

    public static Sample sample(WeatherRegion region, long gameTime) {
        long slot = Math.floorDiv(gameTime, region.periodTicks());
        // Each boundary retains the previous condition 80% of the time; otherwise
        // it draws from the regional climate. Find the latest draw directly so
        // forecasts can query any time without mutable RNG state or replaying history.
        long drawSlot = slot;
        while (unit(region.seed() ^ drawSlot * SLOT_SALT ^ REFRESH_SALT) < PERSISTENCE) drawSlot--;
        long salt = region.seed() ^ drawSlot * SLOT_SALT;
        Condition condition = Condition.CLEAR;
        if (unit(salt) < region.wetChance()) {
            condition = unit(salt + 1) < region.snowChance() ? Condition.SNOW
                : unit(salt + 2) < region.stormChance() ? Condition.THUNDER : Condition.RAIN;
        }
        long start = slot * region.periodTicks();
        return new Sample(condition, start, start + region.periodTicks());
    }

    private static double unit(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return ((value ^ value >>> 31) >>> 11) * 0x1.0p-53;
    }
}
