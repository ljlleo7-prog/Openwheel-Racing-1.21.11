package com.openwheelracing.content.race;

import java.util.List;

public record RaceDirectorSnapshot(boolean checkpointCheckEnabled, boolean offTrackCheckEnabled, int minimumValidLapTicks, int page, int maxPage, int raceControlRevision, int lapRecordsRevision, List<RaceDirectorLapRow> laps) {
    public static RaceDirectorSnapshot empty() {
        return new RaceDirectorSnapshot(false, true, OWRLapRecords.DEFAULT_MIN_VALID_LAP_TICKS, 0, 0, 0, 0, List.of());
    }
}
