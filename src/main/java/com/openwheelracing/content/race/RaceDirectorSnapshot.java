package com.openwheelracing.content.race;

import com.openwheelracing.content.track.TrackMapSnapshot;

import java.util.List;

public record RaceDirectorSnapshot(boolean checkpointCheckEnabled, boolean offTrackCheckEnabled, boolean autoShiftingAllowed, int minimumValidLapTicks, int raceLapLimit, int page, int maxPage, int raceControlRevision, int lapRecordsRevision,
        int maxErsCapacityMj, int maxBalancedDeployKw, int maxAttackDeployKw, int maxHarvestNegativeKw, RaceFlagMode globalFlag, double carDamageModifier, double tyreWearModifier,
        long activeSessionId, String activeSessionName, boolean archiveMode, int leftTeamCarId, int rightTeamCarId, TrackMapSnapshot trackMap,
        boolean trackMapScanRunning, int trackMapScanScannedChunks, int trackMapScanTotalChunks, int trackMapScanDetectedCells,
        TrackMoistureSnapshot trackMoisture, List<RaceDirectorLapRow> laps, List<TeamCarRow> teamCars) {
    public RaceDirectorSnapshot {
        trackMap = trackMap == null ? TrackMapSnapshot.EMPTY : trackMap;
        trackMoisture = trackMoisture == null ? TrackMoistureSnapshot.EMPTY : trackMoisture;
        laps = List.copyOf(laps == null ? List.of() : laps);
        teamCars = List.copyOf(teamCars == null ? List.of() : teamCars);
    }

    public static RaceDirectorSnapshot empty() {
        return new RaceDirectorSnapshot(false, true, true, OWRLapRecords.DEFAULT_MIN_VALID_LAP_TICKS, OWRRaceControlState.DEFAULT_RACE_LAP_LIMIT,
            0, 0, 0, 0, 4, 200, 350, 110, RaceFlagMode.DEFAULT, 1.0, 1.0, OWRLapRecords.DEFAULT_SESSION_ID,
            OWRLapRecords.DEFAULT_SESSION_NAME, false, -1, -1, TrackMapSnapshot.EMPTY, false, 0, 0, 0, TrackMoistureSnapshot.EMPTY, List.of(), List.of());
    }
}
