package com.openwheelracing.content.race;

import com.openwheelracing.content.track.TrackDefinitionsData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class OWRLegacyDimensionDataImporter {
    private OWRLegacyDimensionDataImporter() {
    }

    public static void importOnServerStarted(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }
        OWRRaceControlState legacyRaceControl = OWRRaceControlState.getIfPresent(overworld);
        OWRLapRecords legacyLapRecords = OWRLapRecords.getIfPresent(overworld);
        TrackDefinitionsData legacyTracks = TrackDefinitionsData.getIfPresent(overworld);
        if (legacyRaceControl == null && legacyLapRecords == null && legacyTracks == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            String dimensionId = level.dimension().identifier().toString();
            OWRRaceControlState.importLegacy(level, legacyRaceControl);
            OWRLapRecords.importLegacy(level, legacyLapRecords, dimensionId);
            TrackDefinitionsData.importLegacy(level, legacyTracks, dimensionId);
        }
    }
}
