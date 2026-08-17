package com.openwheelracing.content.race;

import com.openwheelracing.content.ai.AiTrackPlan;
import com.openwheelracing.content.ai.AiTrackPlanCompiler;
import com.openwheelracing.content.ai.BasicAiGripModel;
import com.openwheelracing.content.track.survey.SurveyRouteModel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AiTrackPlanCompilerTest {
    @Test
    void selectsFastestValidPlayerProfile() {
        SurveyRouteModel route = squareRoute();
        OWRLapProfiles.BestLapProfile slow = profile(route, 60_000, 100);
        OWRLapProfiles.BestLapProfile fast = profile(route, 50_000, 120);
        assertSame(fast, AiTrackPlanCompiler.fastestValidPlayerProfile(List.of(slow, fast), route).orElseThrow());
    }

    @Test
    void compilesDeterministicSeamSafeImmutablePlan() {
        SurveyRouteModel route = squareRoute();
        BasicAiGripModel.State grip = nominalGrip();
        AiTrackPlan first = AiTrackPlanCompiler.compile(route, List.of(profile(route, 50_000, 250)), grip);
        AiTrackPlan second = AiTrackPlanCompiler.compile(route, List.of(profile(route, 50_000, 250)), grip);
        assertEquals(first, second);
        assertEquals(2.0, first.spacing(), 1.0E-9);
        assertEquals(first.sample(0.25).targetSpeedMetersPerSecond(), first.sample(route.length() + 0.25).targetSpeedMetersPerSecond(), 1.0E-9);
        assertTrue(first.samples().stream().allMatch(sample -> Math.abs(sample.lateralOffset()) <= 2.0));
        assertThrows(UnsupportedOperationException.class, () -> first.samples().clear());
    }

    @Test
    void fallsBackToSurveyWithoutHumanProfile() {
        AiTrackPlan plan = AiTrackPlanCompiler.compile(squareRoute(), List.of(), nominalGrip());
        assertEquals(AiTrackPlan.ReferenceSource.SURVEY, plan.referenceSource());
        assertTrue(plan.samples().stream().allMatch(sample -> sample.lateralOffset() == 0.0));
        assertEquals(0.0, plan.sample(5.0).tangentRadians(), 1.0E-9);
        assertEquals(Math.PI / 2.0, plan.sample(15.0).tangentRadians(), 1.0E-9);
    }

    private static OWRLapProfiles.BestLapProfile profile(SurveyRouteModel route, int millis, int offsetCm) {
        int count = 20;
        int[] times = new int[count];
        int[] speeds = new int[count];
        int[] offsets = new int[count];
        int[] headings = new int[count];
        Arrays.setAll(times, i -> i * millis / count);
        Arrays.fill(speeds, 1500);
        Arrays.fill(offsets, offsetCm);
        return new OWRLapProfiles.BestLapProfile("test", route.trackId(), route.routeId(), UUID.randomUUID(), "driver",
            OWRLapProfiles.Origin.PLAYER, 1, millis, route.length(), route.length() / count, times, speeds, offsets, headings, 1);
    }

    private static BasicAiGripModel.State nominalGrip() {
        return BasicAiGripModel.build(new BasicAiGripModel.Input(95, 88, 104, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1));
    }

    private static SurveyRouteModel squareRoute() {
        UUID routeId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID trackId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        return new SurveyRouteModel(routeId, trackId, List.of(), List.of(
            node(0, 0, 0, 0, 0), node(1, 10, 0, Math.PI / 2, 10),
            node(2, 10, 10, Math.PI, 20), node(3, 0, 10, -Math.PI / 2, 30)), 40, 10);
    }

    private static SurveyRouteModel.Node node(int index, double x, double z, double heading, double distance) {
        return new SurveyRouteModel.Node(index, new SurveyRouteModel.Point(x, 64, z), heading, distance);
    }
}
