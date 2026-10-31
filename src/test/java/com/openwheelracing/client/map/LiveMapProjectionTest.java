package com.openwheelracing.client.map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveMapProjectionTest {
    @Test void northIsUpAndEastIsRightWithoutStretchingTheCircuit() {
        var p = LiveMapProjection.fit(-500, -1000, 500, 1000, 20, 30, 300, 160);
        assertTrue(p.screenY(-100) < p.screenY(0));
        assertTrue(p.screenX(100) > p.screenX(0));
        assertEquals(p.screenX(100) - p.screenX(0), p.screenY(100) - p.screenY(0));
        assertTrue(p.contains(-500, -1000));
        assertTrue(p.contains(500, 1000));
    }

    @Test void finishViewRemainsCenteredOnTheLineWhenTheViewerTravels() {
        var p = new LiveMapProjection(1200, -800, 1, 10, 20, 200, 200);
        assertEquals(110, p.screenX(1200));
        assertEquals(120, p.screenY(-800));
        assertTrue(p.contains(1101, -899));
        assertFalse(p.contains(1099, -800));
        assertFalse(p.contains(1200, -901));
    }

    @Test void emptyOrSinglePointBoundsStillProduceAFiniteProjection() {
        var p = LiveMapProjection.fit(1, 1, 1, 1, 0, 0, 200, 100);
        assertTrue(Double.isFinite(p.scale()));
        assertTrue(p.contains(1, 1));
    }
}
