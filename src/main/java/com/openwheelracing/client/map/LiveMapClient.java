package com.openwheelracing.client.map;

import com.openwheelracing.content.race.map.LiveMapSnapshot;
import com.openwheelracing.content.race.map.LiveMapSnapshot.Geometry;
import com.openwheelracing.network.LiveMapNetwork;
import com.openwheelracing.network.OWRNetwork;
import net.minecraft.client.Minecraft;

public final class LiveMapClient {
    private static boolean enabled;
    private static boolean openScreen;
    private static int generation;
    private static int ticks;
    private static String dimension = "";
    private static LiveMapSnapshot snapshot;
    private static Geometry geometry;
    private static long receivedAt;
    public static final LiveMapDriverColors COLORS = new LiveMapDriverColors();

    private LiveMapClient() {}

    public static boolean enabled() { return enabled; }
    public static LiveMapSnapshot snapshot() { return snapshot; }
    public static Geometry geometry() { return geometry; }
    public static boolean stale() { return snapshot == null || System.nanoTime() - receivedAt > 2_000_000_000L; }

    public static void setEnabled(boolean value) {
        enabled = value;
        openScreen = false;
        resetView();
    }

    public static void requestScreen() {
        setEnabled(true);
        openScreen = true;
    }

    private static void resetView() {
        generation++;
        ticks = 0;
        snapshot = null;
        geometry = null;
        LiveMapBackground.clear();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getConnection() == null) {
            if (enabled || snapshot != null || !dimension.isEmpty()) {
                setEnabled(false);
                dimension = "";
                COLORS.clear();
            }
            return;
        }
        String next = mc.level.dimension().identifier().toString();
        if (!dimension.equals(next)) {
            dimension = next;
            resetView();
        }
        if (!enabled) return;
        // Wait until the chat command has closed its screen before opening the expanded map.
        if (openScreen && mc.screen == null) {
            openScreen = false;
            mc.setScreen(new com.openwheelracing.client.screen.LiveMapScreen());
        }
        // Ten server snapshots per second; static geometry refreshes every five seconds.
        if (ticks % 2 == 0) OWRNetwork.sendToServer(new LiveMapNetwork.Request(generation, geometry == null || ticks % 100 == 0));
        ticks++;
    }

    public static void accept(LiveMapSnapshot next) {
        if (!enabled || next.generation() != generation || !next.dimension().equals(dimension)) return;
        if (snapshot != null && next.tick() < snapshot.tick()) return;
        snapshot = next;
        if (next.geometry() != null && !next.geometry().equals(geometry)) geometry = next.geometry();
        next.cars().stream().map(LiveMapSnapshot.Car::driverId).distinct().sorted().forEach(COLORS::identity);
        receivedAt = System.nanoTime();
    }
}
