package com.openwheelracing.content.race;

import java.util.List;

public record RaceDirectorSnapshot(boolean checkpointCheckEnabled, boolean offTrackCheckEnabled, int minimumValidLapTicks, int page, int maxPage, int raceControlRevision, int lapRecordsRevision,
        int maxErsCapacityMj, int maxBalancedDeployKw, int maxAttackDeployKw, int maxHarvestNegativeKw, RaceFlagMode globalFlag, double carDamageModifier, double tyreWearModifier,
        long activeSessionId, String activeSessionName, boolean archiveMode, List<RaceDirectorLapRow> laps, List<TeamCarRow> teamCars) {
    public static RaceDirectorSnapshot empty() {
        return new RaceDirectorSnapshot(false, true, OWRLapRecords.DEFAULT_MIN_VALID_LAP_TICKS, 0, 0, 0, 0, 4, 200, 350, 110, RaceFlagMode.DEFAULT, 1.0, 1.0, OWRLapRecords.DEFAULT_SESSION_ID, OWRLapRecords.DEFAULT_SESSION_NAME, false, List.of(), List.of());
    }
}
