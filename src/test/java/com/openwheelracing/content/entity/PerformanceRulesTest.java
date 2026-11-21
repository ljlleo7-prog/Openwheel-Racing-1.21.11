package com.openwheelracing.content.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceRulesTest {
    @Test
    void legalPreferencesPassButModifiedGripAndNonFiniteParametersDoNot() {
        double[] state = new double[66];
        java.util.Arrays.fill(state, 0, 7, 1.0);
        state[8] = 1.02; state[9] = .98;
        assertTrue(PerformanceRules.legalControls(state));
        state[8] = 1.5;
        assertFalse(PerformanceRules.legalControls(state));
        state[8] = 1.02; state[16] = Double.NaN;
        assertFalse(PerformanceRules.legalControls(state));
    }

    @Test
    void effectiveControlsInMotionFramesMustBeFiniteUnitValues() {
        double[] state = new double[80];
        java.util.Arrays.fill(state, 0, 7, 1.0);
        state[8] = 1.02; state[9] = .98;
        state[78] = .35; state[79] = .6;
        assertTrue(PerformanceRules.legalControls(state));
        state[78] = 1.01;
        assertFalse(PerformanceRules.legalControls(state));
        state[78] = .35; state[79] = -.01;
        assertFalse(PerformanceRules.legalControls(state));
        state[79] = Double.NaN;
        assertFalse(PerformanceRules.legalControls(state));
    }

    @Test
    void probeIsRepeatableAndBoundToServerPreset() {
        assertEquals(PerformanceRules.probe(0), PerformanceRules.probe(0));
        assertNotEquals(PerformanceRules.probe(0), PerformanceRules.probe(1));
        assertEquals(64, PerformanceRules.fingerprint().length());
    }
}
