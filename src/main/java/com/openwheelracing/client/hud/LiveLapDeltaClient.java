package com.openwheelracing.client.hud;

import com.openwheelracing.content.track.survey.SurveyRouteLocalizer;
import com.openwheelracing.network.OWRNetwork;
import net.minecraft.client.Minecraft;

public final class LiveLapDeltaClient {
    private static boolean active;
    private static boolean hasReference;
    private static SurveyRouteLocalizer.Status status = SurveyRouteLocalizer.Status.UNTRACKED;
    private static int anchorElapsedMillis;
    private static int bestLapMillis;
    private static int anchorReferenceMillis;
    private static int anchorDeltaMillis;
    private static double deltaMillisPerMillisecond;
    private static long receivedAtNanos;

    private LiveLapDeltaClient() {}

    public static void apply(OWRNetwork.LiveLapDeltaHudMessage message) {
        active = message.lapActive();
        hasReference = message.hasReference();
        status = SurveyRouteLocalizer.Status.values()[Math.min(message.localizationStatus(), SurveyRouteLocalizer.Status.values().length - 1)];
        anchorElapsedMillis = message.elapsedMillis();
        bestLapMillis = message.bestLapMillis();
        anchorReferenceMillis = message.referenceMillis();
        long now = System.nanoTime();
        if (receivedAtNanos != 0L) {
            double elapsedMs = Math.max(1.0, (now - receivedAtNanos) / 1_000_000.0);
            deltaMillisPerMillisecond = Math.max(-0.25, Math.min(0.25, (message.deltaMillis() - anchorDeltaMillis) / elapsedMs));
        } else {
            deltaMillisPerMillisecond = 0.0;
        }
        anchorDeltaMillis = message.deltaMillis();
        receivedAtNanos = now;
    }

    public static int elapsedMillis() {
        if (!active) return anchorElapsedMillis;
        long since = Math.max(0L, System.nanoTime() - receivedAtNanos);
        return anchorElapsedMillis + (int) Math.min(250L, since / 1_000_000L);
    }

    public static int deltaMillis() {
        if (!active || !hasReference) return anchorDeltaMillis;
        long since = Math.max(0L, System.nanoTime() - receivedAtNanos);
        return anchorDeltaMillis + (int) Math.round(deltaMillisPerMillisecond * Math.min(250.0, since / 1_000_000.0));
    }
    public static int bestLapMillis() { return bestLapMillis; }
    public static boolean active() { return active; }
    public static boolean hasReference() { return hasReference; }
    public static SurveyRouteLocalizer.Status status() { return status; }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getVehicle() == null) clear();
    }

    public static void clear() {
        active = false;
        hasReference = false;
        status = SurveyRouteLocalizer.Status.UNTRACKED;
        anchorElapsedMillis = 0;
        bestLapMillis = 0;
        anchorReferenceMillis = 0;
        anchorDeltaMillis = 0;
        deltaMillisPerMillisecond = 0.0;
        receivedAtNanos = 0L;
    }
}
