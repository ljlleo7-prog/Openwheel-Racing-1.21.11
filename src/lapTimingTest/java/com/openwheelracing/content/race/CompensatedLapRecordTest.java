package com.openwheelracing.content.race;

import com.mojang.serialization.JsonOps;
import java.util.UUID;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompensatedLapRecordTest {
    @Test void recoveredMovementWithExactGeometryCanSetABestAndKeepsItsSourceOnDisk() {
        var records = new OWRLapRecords();
        UUID driver = UUID.randomUUID();
        var car = new OWRLapRecords.CarSnapshot(1, 2, 3, 4, 0, 0, true).withRecoveredTiming();
        var lap = records.recordLap(driver, "Driver", 60_001, 1200, "test", 0, 0, car);
        assertFalse(lap.car().estimatedTiming());
        assertTrue(lap.car().recoveredTiming());
        assertEquals(60_001, records.getBestLap(driver));
        assertEquals(1, records.getValidSessionLaps(records.getActiveSessionId()).size());
        var json = OWRLapRecords.LapRecord.CODEC.encodeStart(JsonOps.INSTANCE, lap).getOrThrow();
        assertEquals(lap, OWRLapRecords.LapRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertRowRoundTrip(lap);
    }

    @Test void recoveredLapIsRaceCreditWithPersistedEstimatedTimingButCannotSetAQualifyingBest() {
        var records = new OWRLapRecords();
        UUID driver = UUID.randomUUID();
        var row = records.recordCompensatedLap(driver, "Driver", 60_000, 1300, "test", 0,
            new OWRLapRecords.CarSnapshot(1, 2, 3, 4, 0, 0, true));
        assertEquals(1, records.getRaceSessionLaps(records.getActiveSessionId()).size());
        assertTrue(records.getValidSessionLaps(records.getActiveSessionId()).isEmpty());
        assertEquals(0, records.getBestLap(driver));
        assertTrue(row.car().estimatedTiming());
        assertTrue(row.invalidated());
        assertFalse(records.revalidateLap(row.id()));
        var json = OWRLapRecords.LapRecord.CODEC.encodeStart(JsonOps.INSTANCE, row).getOrThrow();
        var restored = OWRLapRecords.LapRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(row, restored);
        assertTrue(restored.car().estimatedTiming());
        assertRowRoundTrip(restored);
        records.recordLap(driver, "Driver", 65_000, 2600, "test", 0, 0, row.car().withEstimatedTiming());
        assertEquals(2, records.getRaceSessionLaps(records.getActiveSessionId()).size());
        assertTrue(records.getValidSessionLaps(records.getActiveSessionId()).isEmpty());
        assertEquals(0, records.getBestLap(driver));
    }
    private static void assertRowRoundTrip(OWRLapRecords.LapRecord lap) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var row = RaceDirectorLapRow.fromRecord(lap);
            RaceDirectorLapRow.encode(row, buffer);
            assertEquals(row, RaceDirectorLapRow.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

}
