package com.openwheelracing.client.telemetry;

import com.openwheelracing.content.track.survey.SurveyRouteLocalizer;
import com.openwheelracing.network.OWRNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MonitorTelemetryClient {
    private static final int MAX_CURRENT_SAMPLES = 2048;
    private static int carEntityId = -1;
    private static UUID driverId = new UUID(0L, 0L);
    private static float routeLength;
    private static float bestSpacing;
    private static int[] bestSpeeds = new int[0];
    private static final List<Sample> current = new ArrayList<>();
    private static float lastDistance = -1.0f;

    private MonitorTelemetryClient() {}

    public static void apply(OWRNetwork.MonitorTelemetryMessage message) {
        if (carEntityId != message.carEntityId() || !driverId.equals(message.driverId()) || message.routeDistance() + 20.0f < lastDistance) current.clear();
        carEntityId = message.carEntityId();
        driverId = message.driverId();
        routeLength = message.routeLength();
        if (message.profileUpdate()) {
            bestSpeeds = message.bestSpeedCmps().clone();
            bestSpacing = message.profileSpacing();
        }
        if (message.lapActive()) {
            current.add(new Sample(message.routeDistance(), message.speedKmh(), SurveyRouteLocalizer.Status.values()[Math.min(message.localizationStatus(), SurveyRouteLocalizer.Status.values().length - 1)]));
            if (current.size() > MAX_CURRENT_SAMPLES) current.removeFirst();
            lastDistance = message.routeDistance();
        }
    }

    public static void clear() {
        carEntityId = -1; driverId = new UUID(0L, 0L); routeLength = 0.0f; bestSpacing = 0.0f; bestSpeeds = new int[0]; current.clear(); lastDistance = -1.0f;
    }

    public static int carEntityId() { return carEntityId; }
    public static float routeLength() { return routeLength; }
    public static List<Sample> current() { return List.copyOf(current); }
    public static int[] bestSpeeds() { return bestSpeeds.clone(); }
    public static float bestSpacing() { return bestSpacing; }
    public record Sample(float routeDistance, float speedKmh, SurveyRouteLocalizer.Status status) {}
}
