package com.openwheelracing.content.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockCollisionRollbackPolicyTest {
    private static BlockCollisionRollbackPolicy started() {
        var policy = new BlockCollisionRollbackPolicy();
        assertTrue(policy.begin(100, 97, 50, 0, 0, true, true, true));
        return policy;
    }

    private static BlockCollisionRollbackPolicy.Evidence evidence(BlockCollisionRollbackPolicy policy,
            int end, boolean clientCrash, boolean replayCrash) {
        var incident = policy.pending();
        return new BlockCollisionRollbackPolicy.Evidence(incident.id(), incident.epoch(), 47, end,
            true, true, true, clientCrash, replayCrash, true);
    }

    @Test
    void requiresFullHalfSecondOfCollisionFreeEvidence() {
        var policy = started();
        assertEquals(BlockCollisionRollbackPolicy.Decision.WAIT,
            policy.evaluate(109, evidence(policy, 59, false, false)));
        assertEquals(BlockCollisionRollbackPolicy.Decision.REWIND,
            policy.evaluate(110, evidence(policy, 60, false, false)));
        assertEquals(1, policy.epoch());
        assertEquals(BlockCollisionRollbackPolicy.Decision.NONE, policy.evaluate(111, null));
    }

    @Test
    void laterTrueCollisionIsPenalizedDespiteInitialDisagreement() {
        for (boolean clientCrash : new boolean[] {false, true}) {
            var policy = started();
            assertEquals(BlockCollisionRollbackPolicy.Decision.COMMIT,
                policy.evaluate(110, evidence(policy, 60, clientCrash, true)));
        }
    }

    @Test
    void clientCollisionCannotBeForgivenByClearServerReplay() {
        var policy = started();
        assertEquals(BlockCollisionRollbackPolicy.Decision.COMMIT,
            policy.evaluate(110, evidence(policy, 60, true, false)));
    }

    @Test
    void packetLossCannotLeaveVehiclePermanentlyProvisional() {
        var policy = started();
        assertEquals(BlockCollisionRollbackPolicy.Decision.WAIT, policy.evaluate(139, null));
        assertEquals(BlockCollisionRollbackPolicy.Decision.COMMIT, policy.evaluate(140, null));
        assertNull(policy.pending());
        assertEquals(BlockCollisionRollbackPolicy.Decision.NONE, policy.evaluate(141, null));
    }

    @Test
    void lateClearEvidenceCannotOverruleTimeout() {
        var policy = started();
        assertEquals(BlockCollisionRollbackPolicy.Decision.COMMIT,
            policy.evaluate(140, evidence(policy, 60, false, false)));
    }

    @Test
    void repeatedLagCannotRewindSameWallForThousandsOfTicks() {
        var policy = started();
        var oldEvidence = evidence(policy, 60, false, false);
        assertEquals(BlockCollisionRollbackPolicy.Decision.REWIND, policy.evaluate(110, oldEvidence));
        for (long tick = 111; tick < 10000; tick++) {
            policy.observe(tick, 0, 0, false, true);
            assertFalse(policy.begin(tick, tick - 3, 100, 0, 0, true, true, true));
            assertEquals(BlockCollisionRollbackPolicy.Decision.NONE, policy.evaluate(tick, oldEvidence));
        }
    }

    @Test
    void rearmRequiresCooldownDistanceAndSustainedSynchronization() {
        var policy = started();
        policy.evaluate(110, evidence(policy, 60, false, false));
        for (int tick = 111; tick <= 209; tick++) policy.observe(tick, 9, 0, false, true);
        assertFalse(policy.begin(209, 206, 100, 9, 0, true, true, true));
        policy.observe(210, 9, 0, false, true);
        assertTrue(policy.begin(210, 207, 100, 9, 0, true, true, true));
    }

    @Test
    void packetBurstsAndLongTickGapsDoNotCountAsCleanRecovery() {
        var policy = started();
        policy.evaluate(110, evidence(policy, 60, false, false));
        for (int i = 0; i < 1000; i++) policy.observe(300, 9, 0, false, true);
        assertFalse(policy.begin(300, 297, 100, 9, 0, true, true, true));
        for (int tick = 400; tick < 1000; tick += 20) policy.observe(tick, 9, 0, false, true);
        assertFalse(policy.begin(1000, 997, 100, 9, 0, true, true, true));
    }

    @Test
    void insufficientHistoryAndNonBlockImpactsAreNeverEligible() {
        var policy = new BlockCollisionRollbackPolicy();
        assertFalse(policy.begin(100, 96, 50, 0, 0, true, true, true));
        assertFalse(policy.begin(100, 100, 50, 0, 0, true, true, true));
        assertFalse(policy.begin(100, 97, 50, 0, 0, false, true, true));
        assertFalse(policy.begin(100, 97, 50, 0, 0, true, false, true));
        assertFalse(policy.begin(100, 97, 50, 0, 0, true, true, false));
    }

    @Test
    void missingReplayStateOrChunksCommitsImpact() {
        for (int missing = 0; missing < 3; missing++) {
            var policy = started();
            var incident = policy.pending();
            var evidence = new BlockCollisionRollbackPolicy.Evidence(incident.id(), incident.epoch(), 47, 60,
                missing != 0, missing != 1, missing != 2, false, false, true);
            assertEquals(BlockCollisionRollbackPolicy.Decision.COMMIT, policy.evaluate(110, evidence));
        }
    }

    @Test
    void oldEpochCannotResolveAnotherIncident() {
        var policy = started();
        var oldEvidence = evidence(policy, 60, false, false);
        policy.evaluate(110, oldEvidence);
        for (int tick = 111; tick <= 210; tick++) policy.observe(tick, 9, 0, false, true);
        assertTrue(policy.begin(211, 208, 100, 9, 0, true, true, true));
        assertEquals(BlockCollisionRollbackPolicy.Decision.WAIT, policy.evaluate(212, oldEvidence));
        assertEquals(BlockCollisionRollbackPolicy.Decision.COMMIT, policy.cancel(213));
    }

    @Test
    void dismountAndRemountCannotBypassIncidentCooldown() {
        var policy = started();
        policy.evaluate(110, evidence(policy, 60, false, false));
        policy.resetContext(111);
        assertFalse(policy.begin(112, 109, 100, 0, 0, true, true, true));
    }

    @Test
    void boundedSequenceArithmeticHandlesSignedWraparound() {
        var policy = new BlockCollisionRollbackPolicy();
        int sequence = Integer.MAX_VALUE - 2;
        assertTrue(policy.begin(100, 97, sequence, 0, 0, true, true, true));
        var incident = policy.pending();
        var evidence = new BlockCollisionRollbackPolicy.Evidence(incident.id(), 0, sequence - 3,
            sequence + 10, true, true, true, false, false, true);
        assertEquals(BlockCollisionRollbackPolicy.Decision.REWIND, policy.evaluate(110, evidence));
    }
}
