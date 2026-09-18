package com.openwheelracing.content.race;

import java.util.UUID;

public record BoPDriverRow(UUID driverId, String driverName, int averageLapMillis, int sampleLaps,
                           int bestLapMillis, String bestLapSessionName,
                           int weightPercent, int powerPercent, int estimatedLapMillis) {
}
