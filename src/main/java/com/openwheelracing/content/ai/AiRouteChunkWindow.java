package com.openwheelracing.content.ai;

import com.openwheelracing.content.track.survey.SurveyRouteModel;

import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class AiRouteChunkWindow {
    public static final int AHEAD_CHUNKS = 8;
    public static final int BEHIND_CHUNKS = 2;
    public static final int RADIUS = 1;
    public static final int MAX_CHUNKS_PER_CAR = 32;

    private AiRouteChunkWindow() {
    }

    public static Set<ChunkCoordinate> around(SurveyRouteModel route, double centerDistance) {
        LinkedHashSet<ChunkCoordinate> chunks = new LinkedHashSet<>();
        double length = Math.max(1.0, route.length());
        double spacing = Math.max(8.0, (AHEAD_CHUNKS + BEHIND_CHUNKS) * 16.0 / 2.0);
        // Insertion order is priority order: the current entity-ticking area must never
        // lose out to distant preview chunks when the fleet budget is under pressure.
        addAtDistance(chunks, route, centerDistance);
        for (double distance = spacing; distance <= AHEAD_CHUNKS * spacing; distance += spacing) {
            addAtDistance(chunks, route, centerDistance + distance);
            if (chunks.size() >= MAX_CHUNKS_PER_CAR) return Collections.unmodifiableSet(new LinkedHashSet<>(chunks));
        }
        for (double distance = spacing; distance <= BEHIND_CHUNKS * spacing; distance += spacing) {
            addAtDistance(chunks, route, centerDistance - distance);
            if (chunks.size() >= MAX_CHUNKS_PER_CAR) return Collections.unmodifiableSet(new LinkedHashSet<>(chunks));
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(chunks));
    }

    private static void addAtDistance(Set<ChunkCoordinate> chunks, SurveyRouteModel route, double distance) {
            SurveyRouteModel.Point point = SurveyRouteSampler.sample(route, distance).position();
            addRadius(chunks, Math.floorDiv((int) Math.floor(point.x()), 16), Math.floorDiv((int) Math.floor(point.z()), 16));
    }

    private static void addRadius(Set<ChunkCoordinate> chunks, int centerX, int centerZ) {
        for (int dz = -RADIUS; dz <= RADIUS && chunks.size() < MAX_CHUNKS_PER_CAR; dz++) {
            for (int dx = -RADIUS; dx <= RADIUS && chunks.size() < MAX_CHUNKS_PER_CAR; dx++) {
                chunks.add(new ChunkCoordinate(centerX + dx, centerZ + dz));
            }
        }
    }

    public record ChunkCoordinate(int x, int z) {
    }
}
