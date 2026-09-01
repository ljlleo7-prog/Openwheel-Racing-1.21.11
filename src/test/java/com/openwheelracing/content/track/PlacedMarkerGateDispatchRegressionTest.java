package com.openwheelracing.content.track;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlacedMarkerGateDispatchRegressionTest {
    private static final double HALF_HEIGHT = 0.5;

    @Test
    void highSpeedTickDispatchesCheckpointAndStartFinishInTravelOrder() {
        List<PlacedMarkerGateMath.Gate> gates = List.of(
            gate(PlacedMarkerGateMath.Type.CHECKPOINT, 0),
            gate(PlacedMarkerGateMath.Type.START_FINISH, 2)
        );

        List<PlacedMarkerGateMath.Type> dispatched = dispatchAll(
            -2.0,
            4.0,
            gates
        );

        assertEquals(List.of(
            PlacedMarkerGateMath.Type.CHECKPOINT,
            PlacedMarkerGateMath.Type.START_FINISH
        ), dispatched, "all marker gates crossed during one server tick must be dispatched");
    }

    @Test
    void highSpeedTickDispatchesEveryCheckpointInTravelOrder() {
        List<PlacedMarkerGateMath.Gate> gates = List.of(
            gate(PlacedMarkerGateMath.Type.CHECKPOINT, 0),
            gate(PlacedMarkerGateMath.Type.CHECKPOINT, 2)
        );

        List<PlacedMarkerGateMath.Type> dispatched = dispatchAll(
            -2.0,
            4.0,
            gates
        );

        assertEquals(List.of(
            PlacedMarkerGateMath.Type.CHECKPOINT,
            PlacedMarkerGateMath.Type.CHECKPOINT
        ), dispatched, "a later checkpoint must not be discarded after an earlier crossing in the same tick");
    }

    /**
     * Uses the same ordered crossing API as OpenwheelCarEntity.
     */
    private static List<PlacedMarkerGateMath.Type> dispatchAll(
        double previousX,
        double currentX,
        List<PlacedMarkerGateMath.Gate> gates
    ) {
        return PlacedMarkerGateMath.crossings(previousX, 0.5, currentX, 0.5, 0.5, 0.5,
                HALF_HEIGHT, HALF_HEIGHT, gates, 0.0).stream()
            .map(crossing -> crossing.gate().type())
            .toList();
    }

    private static PlacedMarkerGateMath.Gate gate(PlacedMarkerGateMath.Type type, int x) {
        return new PlacedMarkerGateMath.Gate(type, PlacedMarkerGateMath.Axis.X, 1, x, 0, 0, 0);
    }
}
