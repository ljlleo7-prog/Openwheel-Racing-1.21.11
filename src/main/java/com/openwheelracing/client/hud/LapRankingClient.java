package com.openwheelracing.client.hud;

import com.openwheelracing.content.race.OWRLapRecords;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class LapRankingClient {
    private static volatile String sessionName = OWRLapRecords.DEFAULT_SESSION_NAME;
    private static volatile List<OWRLapRecords.DriverBest> ranking = List.of();

    private LapRankingClient() {
    }

    public static void setRanking(String activeSessionName, List<OWRLapRecords.DriverBest> entries) {
        sessionName = activeSessionName;
        ranking = List.copyOf(entries);
    }

    public static String getSessionName() {
        return sessionName;
    }

    public static List<OWRLapRecords.DriverBest> getRanking() {
        return ranking;
    }
}
