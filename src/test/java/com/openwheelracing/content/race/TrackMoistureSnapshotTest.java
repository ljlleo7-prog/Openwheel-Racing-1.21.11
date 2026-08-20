package com.openwheelracing.content.race;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackMoistureSnapshotTest {
    @Test
    void percentagesAndWeightedConditionUseAllRouteSamples() {
        TrackMoistureSnapshot snapshot = new TrackMoistureSnapshot(1, 4, 17, 9, 2, 26, 6, 0, List.of(), List.of());
        assertEquals(32, snapshot.totalSamples());
        assertEquals(1, snapshot.conditionLevel());
        assertEquals(13, snapshot.percent(0));
        assertEquals(53, snapshot.percent(1));
        assertEquals(28, snapshot.percent(2));
        assertEquals(6, snapshot.percent(3));
    }

    @Test
    void halfDryAndHalfWetIsReportedAsDamp() {
        TrackMoistureSnapshot snapshot = new TrackMoistureSnapshot(1, 16, 0, 16, 0, 32, 0, 0, List.of(), List.of());
        assertEquals(1.0, snapshot.averageLevel(), 1.0E-9);
        assertEquals(1, snapshot.conditionLevel());
    }

    @Test
    void soakingWaterCarriesThreeTimesTheDryToDampWeight() {
        TrackMoistureSnapshot snapshot = new TrackMoistureSnapshot(1, 24, 0, 0, 8, 32, 0, 0, List.of(), List.of());
        assertEquals(0.75, snapshot.averageLevel(), 1.0E-9);
        assertEquals(1, snapshot.conditionLevel());
    }

    @Test
    void sectorPayloadIsBounded() {
        List<TrackMoistureSnapshot.Sector> sectors = java.util.stream.IntStream.range(0, 80)
            .mapToObj(index -> new TrackMoistureSnapshot.Sector(index, index, index % 4, false))
            .toList();
        TrackMoistureSnapshot snapshot = new TrackMoistureSnapshot(1, 0, 0, 0, 0, 0, 0, 0, sectors, List.of());
        assertEquals(TrackMoistureSnapshot.MAX_SECTORS, snapshot.sectors().size());
    }

    @Test
    void packedTileRetainsExactObservedMoisture() {
        byte[] observed = new byte[32];
        byte[] levels = new byte[64];
        int index = 7 * 16 + 11;
        observed[index >>> 3] |= (byte) (1 << (index & 7));
        levels[index >>> 2] |= (byte) (3 << ((index & 3) * 2));
        TrackMoistureSnapshot.Tile tile = new TrackMoistureSnapshot.Tile(12, -4, observed, levels);
        assertEquals(true, tile.observed(11, 7));
        assertEquals(3, tile.level(11, 7));
        assertEquals(false, tile.observed(10, 7));
    }
}
