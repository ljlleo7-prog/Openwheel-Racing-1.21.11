package com.openwheelracing.content.race;

import com.openwheelracing.content.ai.SurveyRouteSampler;
import com.openwheelracing.content.track.survey.SurveyRouteModel;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LapProfileCollectorImpactTest {
    @Test
    void completedCleanTraceCanTeachAiButImpactedTraceCannot() {
        var route = route();
        var clean = collect(route, false);
        var crashed = collect(route, true);
        assertTrue(clean.coverage() >= 0.97);
        assertTrue(crashed.coverage() >= 0.97);
        assertNotNull(clean.finish("test", route.trackId(), "Driver", 1, 10_000, 200));
        assertNull(crashed.finish("test", route.trackId(), "Driver", 2, 10_000, 200));
    }

    @Test
    void routeMovementAccumulatesAndResetsForNextAttempt() {
        var model = route();
        var collector = collect(model, false);
        assertTrue(collector.movementAlongRoute() > model.length() * 0.95);
        collector.start(model, UUID.randomUUID(), 0);
        assertEquals(0.0, collector.movementAlongRoute());
        var point = SurveyRouteSampler.sample(model, 30);
        collector.sample(point.position(), point.headingRadians(), 1, 0);
        collector.sample(point.position(), point.headingRadians(), 2, 0);
        assertEquals(0.0, collector.movementAlongRoute());
    }

    @Test
    void nextLapDoesNotInheritPreviousImpactFlag() {
        var route = route();
        LapProfileCollector collector = collect(route, true);
        collector.start(route, UUID.randomUUID(), 0);
        sample(collector, route);
        assertNotNull(collector.finish("test", route.trackId(), "Driver", 3, 10_000, 200));
    }

    private static LapProfileCollector collect(SurveyRouteModel route, boolean impact) {
        LapProfileCollector collector = new LapProfileCollector();
        collector.start(route, UUID.randomUUID(), 0);
        sample(collector, route);
        if (impact) collector.recordImpact();
        return collector;
    }

    private static void sample(LapProfileCollector collector, SurveyRouteModel model) {
        double tick = 0;
        for (double d = 0; d < model.length(); d += 2) {
            var sample = SurveyRouteSampler.sample(model, d);
            collector.sample(sample.position(), sample.headingRadians(), tick++, 144);
        }
    }

    private static SurveyRouteModel route() {
        List<SurveyRouteModel.Node> nodes = new ArrayList<>();
        double length = 0;
        for (int i = 0; i < 64; i++) {
            double angle = 2.0 * Math.PI * i / 64;
            nodes.add(new SurveyRouteModel.Node(i, new SurveyRouteModel.Point(Math.cos(angle) * 50, 64, Math.sin(angle) * 50),
                angle + Math.PI / 2, length));
            length += 100 * Math.sin(Math.PI / 64);
        }
        return new SurveyRouteModel(UUID.randomUUID(), UUID.randomUUID(), List.of(), nodes, length, length / 64);
    }
}
