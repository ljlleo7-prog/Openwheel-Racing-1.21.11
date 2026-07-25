package com.openwheelracing.client.hud;

import com.openwheelracing.content.race.OWRLapRecords;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class LapRankingClient {
    private static volatile List<OWRLapRecords.DriverBest> ranking = List.of();

    private LapRankingClient() {
    }

    public static void setRanking(List<OWRLapRecords.DriverBest> entries) {
        ranking = List.copyOf(entries);
    }

    public static List<OWRLapRecords.DriverBest> getRanking() {
        return ranking;
    }
}
