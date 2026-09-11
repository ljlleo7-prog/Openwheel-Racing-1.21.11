package com.openwheelracing.content.race.weekend;

import com.openwheelracing.content.race.session.RaceSessionState;
import com.openwheelracing.content.race.session.RaceSessionSuspensionReason;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrandPrixWeekendTest {
    private static final UUID TRACK = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DIRECTOR = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID DRIVER_A = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID DRIVER_B = UUID.fromString("30000000-0000-0000-0000-000000000002");

    @Test
    void blankBuilderSupportsOrderedSessionsAndLocksOnOpen() {
        GrandPrixWeekend weekend = weekend();
        weekend.addSession(practice(1L, "FP1"), 1L, DIRECTOR, "Director");
        weekend.addSession(race(2L, "Grand Prix", 12), 2L, DIRECTOR, "Director");
        weekend.moveSession(1, 0, 3L, DIRECTOR, "Director");
        registerDrivers(weekend);

        weekend.open(10L, DIRECTOR, "Director");

        assertEquals(GrandPrixWeekend.EventState.OPEN, weekend.state());
        assertEquals("Grand Prix", weekend.sessions(10L).getFirst().config().name());
        assertThrows(IllegalStateException.class,
            () -> weekend.addSession(practice(3L, "FP2"), 11L, DIRECTOR, "Director"));
    }

    @Test
    void invalidCompositionExplainsWhyItCannotOpen() {
        GrandPrixWeekend empty = weekend();
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> empty.open(1L, DIRECTOR, "Director"));

        assertTrue(error.getMessage().contains("no sessions"));
        assertTrue(error.getMessage().contains("no entries"));
    }

    @Test
    void raceRequiresGridProcedureAndManualAdvancement() {
        GrandPrixWeekend weekend = weekend();
        weekend.addSession(race(1L, "Race", 5), 1L, DIRECTOR, "Director");
        registerDrivers(weekend);
        weekend.open(2L, DIRECTOR, "Director");

        weekend.advance(3L, DIRECTOR, "Director");
        assertThrows(IllegalStateException.class, () -> weekend.start(4L, DIRECTOR, "Director"));
        weekend.materializeGrid(weekend.suggestedGridOrder(), 4L, DIRECTOR, "Director");
        weekend.stage(4L, DIRECTOR, "Director");
        weekend.countdown(5L, DIRECTOR, "Director");
        assertThrows(IllegalStateException.class, () -> weekend.start(104L, DIRECTOR, "Director"));
        weekend.start(105L, DIRECTOR, "Director");

        assertEquals(RaceSessionState.RUNNING, weekend.activeSession(115L).orElseThrow().state());
        assertEquals(10L, weekend.activeSession(115L).orElseThrow().elapsedTicks());
        assertThrows(IllegalStateException.class, () -> weekend.advance(116L, DIRECTOR, "Director"));
    }

    @Test
    void emptyServerSuspendsOnceAndRecoveryNeverConsumesOfflineTime() {
        GrandPrixWeekend weekend = runningPractice();

        assertTrue(weekend.onConnectedPlayerCountChanged(0, 40L));
        assertFalse(weekend.onConnectedPlayerCountChanged(0, 80L));
        GrandPrixWeekend restored = GrandPrixWeekend.restore(weekend.snapshot(100L));

        GrandPrixWeekend.SessionView suspended = restored.activeSession(20_000L).orElseThrow();
        assertEquals(RaceSessionState.SUSPENDED, suspended.state());
        assertEquals(RaceSessionSuspensionReason.EMPTY_SERVER, suspended.suspensionReason());
        assertEquals(30L, suspended.elapsedTicks());
        restored.resume(20_000L, DIRECTOR, "Director");
        assertEquals(50L, restored.activeSession(20_020L).orElseThrow().elapsedTicks());
    }

    @Test
    void processRecoveryConvertsRunningSessionToSafeSuspension() {
        GrandPrixWeekend original = runningPractice();

        GrandPrixWeekend restored = GrandPrixWeekend.restore(original.snapshot(40L));

        GrandPrixWeekend.SessionView session = restored.activeSession(99_000L).orElseThrow();
        assertEquals(RaceSessionState.SUSPENDED, session.state());
        assertEquals(RaceSessionSuspensionReason.SERVER_RECOVERY, session.suspensionReason());
        assertEquals(30L, session.elapsedTicks());
    }

    @Test
    void countdownFreezesAcrossEmptyServerAndRestart() {
        GrandPrixWeekend weekend = weekend();
        weekend.addSession(race(1L, "Race", 5), 1L, DIRECTOR, "Director");
        registerDrivers(weekend);
        weekend.open(2L, DIRECTOR, "Director");
        weekend.advance(3L, DIRECTOR, "Director");
        weekend.materializeGrid(weekend.suggestedGridOrder(), 4L, DIRECTOR, "Director");
        weekend.stage(4L, DIRECTOR, "Director");
        weekend.countdown(10L, DIRECTOR, "Director");
        weekend.onConnectedPlayerCountChanged(0, 35L);

        GrandPrixWeekend restored = GrandPrixWeekend.restore(weekend.snapshot(1_000L));
        assertEquals(75L, restored.activeSession(50_000L).orElseThrow().countdownRemainingTicks());
        restored.resume(50_000L, DIRECTOR, "Director");
        assertEquals(50L, restored.activeSession(50_025L).orElseThrow().countdownRemainingTicks());
    }

    @Test
    void onlyRegisteredDriversCanEnterAResultAndOfficialResultIsImmutable() {
        GrandPrixWeekend weekend = runningPractice();
        weekend.finish(50L, DIRECTOR, "Director");
        UUID stranger = UUID.fromString("40000000-0000-0000-0000-000000000001");
        assertThrows(IllegalArgumentException.class, () -> weekend.publishProvisional(List.of(
            new GrandPrixWeekend.ResultRow(1, stranger, "Stranger", GrandPrixWeekend.ResultStatus.FINISHED, 1, 60_000, 50L, 0)
        ), 50L, DIRECTOR, "Director"));

        weekend.publishProvisional(List.of(
            new GrandPrixWeekend.ResultRow(2, DRIVER_B, "Bravo", GrandPrixWeekend.ResultStatus.FINISHED, 3, 61_000, 45L, 0),
            new GrandPrixWeekend.ResultRow(1, DRIVER_A, "Alpha", GrandPrixWeekend.ResultStatus.FINISHED, 3, 60_000, 44L, 0)
        ), 51L, DIRECTOR, "Director");
        weekend.officialize(52L, DIRECTOR, "Director");

        assertTrue(weekend.activeSession(52L).orElseThrow().result().official());
        assertEquals(List.of(DRIVER_A, DRIVER_B), weekend.activeSession(52L).orElseThrow().result().rows().stream()
            .map(GrandPrixWeekend.ResultRow::driverId).toList());
        assertThrows(IllegalStateException.class, () -> weekend.reviseProvisional(List.of(), 53L, DIRECTOR, "Director"));
    }

    @Test
    void duplicateCodesAndNonPracticeDuplicatesAreRejected() {
        GrandPrixWeekend weekend = weekend();
        weekend.addSession(qualifying(1L), 1L, DIRECTOR, "Director");
        assertThrows(IllegalStateException.class,
            () -> weekend.addSession(qualifying(2L), 2L, DIRECTOR, "Director"));
        weekend.register(DRIVER_A, "Alpha", "44", 3L, DIRECTOR, "Director");

        GrandPrixWeekend.RegistrationResult collision = weekend.register(DRIVER_B, "Bravo", "44", 4L, DIRECTOR, "Director");

        assertFalse(collision.registered());
        assertEquals(DRIVER_A, collision.entry().driverId());
    }

    @Test
    void directorInfoExposesSessionIdentityClockAndOnlyValidControls() {
        GrandPrixWeekend weekend = runningPractice();

        GrandPrixWeekendInfo info = GrandPrixWeekendInfo.from(weekend, 30L);

        assertTrue(info.present());
        assertEquals("Test GP", info.eventName());
        assertEquals("FP1", info.activeSessionName());
        assertEquals("PRACTICE", info.activeSessionType());
        assertEquals(20L, info.elapsedTicks());
        assertTrue(info.can("suspend"));
        assertTrue(info.can("finish"));
        assertFalse(info.can("stage"));
        assertFalse(info.can("official"));
    }

    private static GrandPrixWeekend runningPractice() {
        GrandPrixWeekend weekend = weekend();
        weekend.addSession(practice(1L, "FP1"), 1L, DIRECTOR, "Director");
        registerDrivers(weekend);
        weekend.open(2L, DIRECTOR, "Director");
        weekend.advance(5L, DIRECTOR, "Director");
        weekend.start(10L, DIRECTOR, "Director");
        return weekend;
    }

    private static GrandPrixWeekend weekend() {
        return new GrandPrixWeekend(UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "Test GP", TRACK, "minecraft:overworld");
    }

    private static void registerDrivers(GrandPrixWeekend weekend) {
        weekend.register(DRIVER_A, "Alpha", "44", 1L, DIRECTOR, "Director");
        weekend.register(DRIVER_B, "Bravo", "63", 2L, DIRECTOR, "Director");
    }

    private static GrandPrixWeekend.SessionConfig practice(long id, String name) {
        return new GrandPrixWeekend.SessionConfig(id, name, GrandPrixWeekend.SessionType.PRACTICE,
            GrandPrixWeekend.SessionFormat.TIMED_PRACTICE, 1_200L, 0, 0L, 0L, 1_000L,
            GrandPrixWeekend.GridSource.CONFIGURED_ENTRY_ORDER);
    }

    private static GrandPrixWeekend.SessionConfig qualifying(long id) {
        return new GrandPrixWeekend.SessionConfig(id, "Qualifying", GrandPrixWeekend.SessionType.QUALIFYING,
            GrandPrixWeekend.SessionFormat.TWO_SHOT_QUALIFYING, 1_200L, 0, 0L, 200L, 6_000L,
            GrandPrixWeekend.GridSource.CONFIGURED_ENTRY_ORDER);
    }

    private static GrandPrixWeekend.SessionConfig race(long id, String name, int laps) {
        return new GrandPrixWeekend.SessionConfig(id, name, GrandPrixWeekend.SessionType.RACE,
            GrandPrixWeekend.SessionFormat.LAP_COUNT_RACE, 0L, laps, 100L, 0L, 12_000L,
            GrandPrixWeekend.GridSource.CONFIGURED_ENTRY_ORDER);
    }
}
