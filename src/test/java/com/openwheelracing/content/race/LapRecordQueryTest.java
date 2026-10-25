package com.openwheelracing.content.race;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LapRecordQueryTest {
    private static final UUID DRIVER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final long CREATED = Instant.parse("2026-10-24T12:30:00Z").toEpochMilli();

    private static RaceDirectorLapRow lap(long id, String player, int millis, long created, long session) {
        return new RaceDirectorLapRow(id, player, millis, created, 0, false, "", 0,
            1, 2, 3, 4, 0, 10, true, session, "Same name", DRIVER.toString(), "", 0, 0, -1);
    }

    private static LapRecordQuery query(String player, String session, String min, String max, String from, String to, int sort, boolean desc, int page, int size) {
        return new LapRecordQuery(player, session, min, max, from, to, sort, desc, page, size);
    }

    @Test void sessionIdDistinguishesIdenticalSessionNames() {
        var result = query("", "#2", "", "", "", "", 0, false, 0, 12).apply(List.of(
            lap(1, "Alice", 1000, CREATED, 1), lap(2, "Alice", 2000, CREATED, 2)));
        assertEquals(List.of(2L), result.rows().stream().map(RaceDirectorLapRow::id).toList());
    }

    @Test void playerDurationAndInclusiveUtcDateFiltersCombineBeforePagination() {
        var result = query("ALICE", "", "1", "2", "2026-10-24", "2026-10-24", 0, false, 0, 1).apply(List.of(
            lap(1, "Alice", 2000, CREATED, 1), lap(2, "Alice", 1000, CREATED, 2),
            lap(3, "Bob", 1500, CREATED, 1), lap(4, "Alice", 500, CREATED, 1),
            lap(5, "Alice", 1500, CREATED + 86_400_000, 1)));
        assertEquals(2, result.total());
        assertEquals(2L, result.rows().getFirst().id());
    }

    @Test void creationSortUsesTimestampRatherThanRecordId() {
        var result = query("", "", "", "", "", "", 1, true, 0, 12).apply(List.of(
            lap(7, "A", 1000, CREATED, 1), lap(2, "B", 1000, CREATED + 1000, 1)));
        assertEquals(2L, result.rows().getFirst().id());
    }

    @Test void clampsPageAfterDeletingLastResultAndSupportsExactUuid() {
        var result = query(DRIVER.toString(), "", "", "", "", "", 0, false, 99, 1).apply(List.of(lap(1, "A", 1000, CREATED, 1)));
        assertEquals(0, result.page());
        assertEquals(1, result.total());
    }

    @Test void rejectsInvalidRangesAndNonFiniteDurations() {
        assertThrows(IllegalArgumentException.class, () -> query("", "", "NaN", "", "", "", 0, false, 0, 12).apply(List.of()));
        assertThrows(IllegalArgumentException.class, () -> query("", "", "2", "1", "", "", 0, false, 0, 12).apply(List.of()));
    }

}
