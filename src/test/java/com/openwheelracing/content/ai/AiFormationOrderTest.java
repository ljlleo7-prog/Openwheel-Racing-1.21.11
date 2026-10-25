package com.openwheelracing.content.ai;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AiFormationOrderTest {
    private final UUID leader = UUID.randomUUID();
    private final UUID follower = UUID.randomUUID();
    private final UUID third = UUID.randomUUID();
    private final List<UUID> order = List.of(leader, follower, third);

    @Test
    void followsAssignedPredecessorAcrossSeamRatherThanNearestArbitraryCar() {
        var distances = Map.of(leader, 5.0, follower, 95.0, third, 98.0);
        assertEquals(10.0, AiFormationOrder.gap(follower, order, distances, 100));
        assertEquals(Double.POSITIVE_INFINITY, AiFormationOrder.gap(leader, order, distances, 100));
    }

    @Test
    void missingPredecessorOrInvertedOrderStopsFollower() {
        assertEquals(0.0, AiFormationOrder.gap(follower, order, Map.of(follower, 20.0), 100));
        assertEquals(0.0, AiFormationOrder.gap(follower, order, Map.of(leader, 10.0, follower, 20.0), 100));
    }
}
