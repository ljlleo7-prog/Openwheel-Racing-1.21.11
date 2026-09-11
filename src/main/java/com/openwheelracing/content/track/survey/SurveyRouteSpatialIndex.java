package com.openwheelracing.content.track.survey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable horizontal cell index for cheap nearby-route candidate lookup. */
public final class SurveyRouteSpatialIndex {
    public static final int CELL_SIZE = 16;
    private final Map<Long, List<Integer>> segmentsByCell;

    public SurveyRouteSpatialIndex(SurveyRouteModel route, double margin) {
        Map<Long, List<Integer>> cells = new HashMap<>();
        int count = route.nodes().size();
        for (int segment = 0; segment < count; segment++) {
            SurveyRouteModel.Point first = route.nodes().get(segment).position();
            SurveyRouteModel.Point second = route.nodes().get((segment + 1) % count).position();
            int minCellX = cell(Math.min(first.x(), second.x()) - margin);
            int maxCellX = cell(Math.max(first.x(), second.x()) + margin);
            int minCellZ = cell(Math.min(first.z(), second.z()) - margin);
            int maxCellZ = cell(Math.max(first.z(), second.z()) + margin);
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                    cells.computeIfAbsent(key(cellX, cellZ), ignored -> new ArrayList<>()).add(segment);
                }
            }
        }
        Map<Long, List<Integer>> immutable = new HashMap<>();
        cells.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
        segmentsByCell = Map.copyOf(immutable);
    }

    public List<Integer> candidateSegments(double x, double z) {
        return segmentsByCell.getOrDefault(key(cell(x), cell(z)), List.of());
    }

    private static int cell(double coordinate) { return (int) Math.floor(coordinate / CELL_SIZE); }
    private static long key(int x, int z) { return (long) x << 32 | z & 0xffffffffL; }
}
