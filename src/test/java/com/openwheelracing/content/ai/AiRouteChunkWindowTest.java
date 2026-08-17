package com.openwheelracing.content.ai;

import com.openwheelracing.content.track.survey.SurveyRouteModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRouteChunkWindowTest {
    @Test
    void corridorIsBoundedAndDeduplicated() {
        SurveyRouteModel route = new SurveyRouteModel(UUID.randomUUID(), UUID.randomUUID(), List.of(), List.of(
            node(0, 0, 0, 0), node(1, 80, 0, 80), node(2, 80, 80, 160), node(3, 0, 80, 240)
        ), 320, 80);
        Set<AiRouteChunkWindow.ChunkCoordinate> chunks = AiRouteChunkWindow.around(route, 0.0);
        assertTrue(chunks.size() <= AiRouteChunkWindow.MAX_CHUNKS_PER_CAR);
        assertTrue(chunks.size() >= 1);
        for (int z = -1; z <= 1; z++) {
            for (int x = -1; x <= 1; x++) assertTrue(chunks.contains(new AiRouteChunkWindow.ChunkCoordinate(x, z)));
        }
    }

    private static SurveyRouteModel.Node node(int index, double x, double z, double distance) {
        return new SurveyRouteModel.Node(index, new SurveyRouteModel.Point(x, 64, z), 0.0, distance);
    }
}
