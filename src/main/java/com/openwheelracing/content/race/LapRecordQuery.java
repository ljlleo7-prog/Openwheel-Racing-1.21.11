package com.openwheelracing.content.race;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Server-side filtering before pagination, across current and archived sessions. */
public record LapRecordQuery(String player, String session, String minSeconds, String maxSeconds,
                             String after, String before, int sort, boolean descending, int page, int pageSize) {
    public Result apply(List<RaceDirectorLapRow> records) {
        double min = seconds(minSeconds, 0), max = seconds(maxSeconds, Double.MAX_VALUE);
        long start = timestamp(after, Long.MIN_VALUE, false), end = timestamp(before, Long.MAX_VALUE, true);
        if (min > max || start > end) throw new IllegalArgumentException("Minimum must not exceed maximum");
        Long sessionId = session.isBlank() ? null : Long.parseLong(session.trim().replace("#", ""));
        String driver = player.trim().toLowerCase(Locale.ROOT);
        Comparator<RaceDirectorLapRow> comparator = switch (sort) {
            case 1 -> Comparator.comparingLong(RaceDirectorLapRow::completedWorldTime);
            case 2 -> Comparator.comparing(RaceDirectorLapRow::driverName, String.CASE_INSENSITIVE_ORDER);
            case 3 -> Comparator.comparingLong(RaceDirectorLapRow::sessionId);
            default -> Comparator.comparingInt(RaceDirectorLapRow::lapMillis);
        };
        if (descending) comparator = comparator.reversed();
        List<RaceDirectorLapRow> matches = records.stream()
            .filter(lap -> driver.isEmpty() || lap.driverName().toLowerCase(Locale.ROOT).contains(driver)
                || lap.driverId().equalsIgnoreCase(driver))
            .filter(lap -> sessionId == null || lap.sessionId() == sessionId)
            .filter(lap -> lap.lapMillis() / 1000.0 >= min && lap.lapMillis() / 1000.0 <= max)
            .filter(lap -> lap.completedWorldTime() >= start && lap.completedWorldTime() <= end)
            .sorted(comparator.thenComparingLong(RaceDirectorLapRow::id)).toList();
        int size = Math.clamp(pageSize, 1, 24);
        int currentPage = Math.clamp(page, 0, Math.max(0, (matches.size() - 1) / size));
        return new Result(matches.stream().skip((long) currentPage * size).limit(size).toList(),
            matches.size(), currentPage);
    }

    private static double seconds(String value, double fallback) {
        if (value.isBlank()) return fallback;
        double result = Double.parseDouble(value.trim());
        if (!Double.isFinite(result) || result < 0) throw new IllegalArgumentException("Use positive seconds");
        return result;
    }

    private static long timestamp(String value, long fallback, boolean endOfDay) {
        if (value.isBlank()) return fallback;
        String trimmed = value.trim();
        if (trimmed.length() == 10) {
            LocalDate date = LocalDate.parse(trimmed);
            return (endOfDay ? date.plusDays(1) : date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - (endOfDay ? 1 : 0);
        }
        return Instant.parse(trimmed).toEpochMilli();
    }

    public record Result(List<RaceDirectorLapRow> rows, int total, int page) { }
}
