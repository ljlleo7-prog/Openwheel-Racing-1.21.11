package com.openwheelracing.content.track;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacedMarkerGateSmokeTest {
    private static final double HALF_HEIGHT = 0.5;
    private static final double START_FINISH_EXPANSION = 1.2;

    @Test
    void improvedGateCaptureOutperformsLegacyAcrossRaceMatrix() {
        List<Scenario> scenarios = scenarios();
        Score baseline = score(scenarios, false);
        Score improved = score(scenarios, true);
        System.out.printf(Locale.ROOT, "Placed marker capture smoke: baseline %d/%d (%.1f%%), improved %d/%d (%.1f%%)%n",
            baseline.passed(), baseline.total(), baseline.percentage(), improved.passed(), improved.total(), improved.percentage());
        for (String category : List.of("placement", "motion", "elevation", "negative")) {
            Score baselineCategory = score(scenarios.stream().filter(scenario -> scenario.category().equals(category)).toList(), false);
            Score improvedCategory = score(scenarios.stream().filter(scenario -> scenario.category().equals(category)).toList(), true);
            System.out.printf(Locale.ROOT, "  %s: baseline %.1f%%, improved %.1f%%%n", category, baselineCategory.percentage(), improvedCategory.percentage());
        }
        assertEquals(scenarios.size(), improved.total());
        assertEquals(scenarios.size(), improved.passed(), () -> "Improved detector failures: " + improved.failures());
        assertTrue(improved.percentage() > baseline.percentage(), "Improved capture rate must exceed legacy baseline");
    }

    @Test
    void alignedGateIsPlacementOrderInvariantAndSingle() {
        List<PlacedMarkerGateMath.Marker> ordered = markers(0, 1, 2, 3);
        List<PlacedMarkerGateMath.Marker> shuffled = List.of(ordered.get(2), ordered.get(0), ordered.get(3), ordered.get(1));
        List<PlacedMarkerGateMath.Gate> first = PlacedMarkerGateMath.merge(ordered);
        List<PlacedMarkerGateMath.Gate> second = PlacedMarkerGateMath.merge(shuffled);
        assertEquals(first, second);
        assertEquals(1, first.size());
        assertEquals(0, first.getFirst().lateralStart());
        assertEquals(3, first.getFirst().lateralEnd());
    }

    @Test
    void gapsAndDifferentHeightsDoNotMerge() {
        List<PlacedMarkerGateMath.Marker> markers = new ArrayList<>(markers(0, 1, 3));
        markers.add(new PlacedMarkerGateMath.Marker(PlacedMarkerGateMath.Type.START_FINISH, PlacedMarkerGateMath.Axis.Z, 1, 0, 4, 1));
        assertEquals(3, PlacedMarkerGateMath.merge(markers).size());
    }

    private static Score score(List<Scenario> scenarios, boolean improved) {
        int passed = 0;
        List<String> failures = new ArrayList<>();
        for (Scenario scenario : scenarios) {
            boolean actual = improved ? improvedCapture(scenario) : legacyCapture(scenario);
            if (actual == scenario.expected()) {
                passed++;
            } else {
                failures.add(scenario.name() + " expected=" + scenario.expected() + " actual=" + actual);
            }
        }
        return new Score(passed, scenarios.size(), failures);
    }

    private static boolean improvedCapture(Scenario scenario) {
        return PlacedMarkerGateMath.earliestCrossing(scenario.previousX(), scenario.previousZ(), scenario.currentX(), scenario.currentZ(),
            scenario.previousY(), scenario.currentY(), scenario.previousHalfHeight(), scenario.currentHalfHeight(),
            PlacedMarkerGateMath.merge(scenario.markers()), scenario.startFinish() ? START_FINISH_EXPANSION : 0.0).isPresent();
    }

    private static boolean legacyCapture(Scenario scenario) {
        for (PlacedMarkerGateMath.Marker marker : scenario.markers()) {
            PlacedMarkerGateMath.Gate gate = new PlacedMarkerGateMath.Gate(marker.type(), marker.axis(), marker.facingSign(), marker.plane(), marker.y(), marker.lateral(), marker.lateral());
            if (PlacedMarkerGateMath.earliestCrossing(scenario.previousX(), scenario.previousZ(), scenario.currentX(), scenario.currentZ(),
                scenario.currentY(), scenario.currentY(), scenario.currentHalfHeight(), scenario.currentHalfHeight(), List.of(gate), 0.0).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static List<Scenario> scenarios() {
        List<Scenario> scenarios = new ArrayList<>();
        List<PlacedMarkerGateMath.Marker> single = markers(0);
        List<PlacedMarkerGateMath.Marker> wide = markers(0, 1, 2, 3);
        scenarios.add(new Scenario("single perpendicular center", "placement", single, -1, 0.5, 1, 0.5, 0.5, 0.5, HALF_HEIGHT, HALF_HEIGHT, true, true));
        scenarios.add(new Scenario("wide outer edge", "placement", wide, -1, 4.5, 1, 4.5, 0.5, 0.5, HALF_HEIGHT, HALF_HEIGHT, true, true));
        scenarios.add(new Scenario("wide shallow angle", "motion", wide, -1, 3.8, 2, 4.4, 0.5, 0.5, HALF_HEIGHT, HALF_HEIGHT, true, true));
        scenarios.add(new Scenario("high speed one tick", "motion", wide, -8, 1.5, 8, 1.5, 0.5, 0.5, HALF_HEIGHT, HALF_HEIGHT, true, true));
        scenarios.add(new Scenario("drop after crossing", "elevation", single, -1, 0.5, 3, 0.5, 1.5, -2.5, HALF_HEIGHT, HALF_HEIGHT, true, true));
        scenarios.add(new Scenario("no plane crossing", "negative", wide, -1, 6, 1, 6, 0.5, 0.5, HALF_HEIGHT, HALF_HEIGHT, true, false));
        scenarios.add(new Scenario("outside expansion", "negative", wide, -1, 5.5, 1, 5.5, 0.5, 0.5, HALF_HEIGHT, HALF_HEIGHT, true, false));
        scenarios.add(new Scenario("unrelated elevation", "negative", wide, -1, 1.5, 1, 1.5, 4.5, 4.5, HALF_HEIGHT, HALF_HEIGHT, true, false));
        scenarios.add(new Scenario("parallel travel", "negative", wide, 0.25, -2, 0.25, 6, 0.5, 0.5, HALF_HEIGHT, HALF_HEIGHT, true, false));
        scenarios.add(new Scenario("checkpoint remains one block", "placement", List.of(new PlacedMarkerGateMath.Marker(PlacedMarkerGateMath.Type.CHECKPOINT, PlacedMarkerGateMath.Axis.X, 1, 0, 0, 0)), -1, 0.5, 1, 0.5, 0.5, 0.5, HALF_HEIGHT, HALF_HEIGHT, false, true));
        return scenarios;
    }

    private static List<PlacedMarkerGateMath.Marker> markers(int... lateralCoordinates) {
        List<PlacedMarkerGateMath.Marker> markers = new ArrayList<>();
        for (int lateral : lateralCoordinates) {
            markers.add(new PlacedMarkerGateMath.Marker(PlacedMarkerGateMath.Type.START_FINISH, PlacedMarkerGateMath.Axis.X, 1, 0, lateral, 0));
        }
        return markers;
    }

    private record Scenario(String name, String category, List<PlacedMarkerGateMath.Marker> markers, double previousX, double previousZ, double currentX, double currentZ, double previousY, double currentY, double previousHalfHeight, double currentHalfHeight, boolean startFinish, boolean expected) {
    }

    private record Score(int passed, int total, List<String> failures) {
        private double percentage() {
            return total == 0 ? 100.0 : passed * 100.0 / total;
        }
    }
}
