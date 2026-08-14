package com.openwheelracing.content.track.survey;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyRouteProcessorTest {
    @Test
    void resamplesClosedSquareAtFixedSpacing() {
        SurveyRouteProcessor.Result result = SurveyRouteProcessor.build(UUID.randomUUID(), UUID.randomUUID(), squareSamples(25, 0.1), 2.0);

        SurveyRouteModel route = assertInstanceOf(SurveyRouteProcessor.Success.class, result).route();
        assertEquals(400.0, route.length(), 1.0);
        assertEquals(200, route.nodes().size());
        assertEquals(0.0, route.nodes().getFirst().distanceAlongRoute());
        assertTrue(route.nodes().getLast().distanceAlongRoute() < route.length());
    }

    @Test
    void rejectsOpenTrace() {
        List<SurveyRouteModel.Sample> samples = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            samples.add(sample(i, 0));
        }
        SurveyRouteProcessor.Failure failure = assertInstanceOf(SurveyRouteProcessor.Failure.class,
            SurveyRouteProcessor.build(UUID.randomUUID(), UUID.randomUUID(), samples, 2.0));
        assertTrue(failure.reason().contains("not closed"));
    }

    @Test
    void dropsDuplicateSamples() {
        List<SurveyRouteModel.Sample> samples = squareSamples(25, 0.1);
        List<SurveyRouteModel.Sample> duplicated = new ArrayList<>();
        for (SurveyRouteModel.Sample sample : samples) {
            duplicated.add(sample);
            duplicated.add(sample);
        }
        SurveyRouteModel route = assertInstanceOf(SurveyRouteProcessor.Success.class,
            SurveyRouteProcessor.build(UUID.randomUUID(), UUID.randomUUID(), duplicated, 2.0)).route();
        assertEquals(samples.size(), route.rawSamples().size());
    }

    private static List<SurveyRouteModel.Sample> squareSamples(int perSide, double closeOffset) {
        List<SurveyRouteModel.Sample> samples = new ArrayList<>();
        for (int i = 0; i < perSide; i++) samples.add(sample(i * 4.0, 0));
        for (int i = 0; i < perSide; i++) samples.add(sample(100, i * 4.0));
        for (int i = 0; i < perSide; i++) samples.add(sample(100 - i * 4.0, 100));
        for (int i = 0; i < perSide; i++) samples.add(sample(0, 100 - i * 4.0));
        samples.add(sample(closeOffset, 0));
        return samples;
    }

    private static SurveyRouteModel.Sample sample(double x, double z) {
        return new SurveyRouteModel.Sample(new SurveyRouteModel.Point(x, 64, z), 0.0);
    }
}
