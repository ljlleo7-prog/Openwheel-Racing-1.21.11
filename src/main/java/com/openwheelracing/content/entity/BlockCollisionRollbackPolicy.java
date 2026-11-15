package com.openwheelracing.content.entity;

/**
 * Bounded adjudication for one disputed block impact. This class never moves an entity.
 * Evidence must come from sequence-matched, server-validated replay, not client assertions.
 */
final class BlockCollisionRollbackPolicy {
    static final int LOOKAHEAD_TICKS = 10;
    static final int MAX_REWIND_TICKS = 3;
    static final int EVIDENCE_TIMEOUT_TICKS = 40;
    static final int COOLDOWN_TICKS = 100;
    static final int CLEAN_TICKS_TO_REARM = 20;
    static final double CLEARANCE_TO_REARM = 8.0;

    enum Decision { NONE, WAIT, REWIND, COMMIT }
    record Incident(long id, int epoch, long startedAt, long checkpointAt,
                    int impactSequence, double x, double z) {}
    record Evidence(long incidentId, int epoch, int firstSequence, int lastSequence,
                    boolean contiguous, boolean completeState, boolean worldAvailable,
                    boolean clientImpact, boolean replayImpact, boolean meaningfulDiscrepancy) {}

    private Incident pending;
    private int epoch;
    private long nextIncidentId;
    private long lastResolvedAt = Long.MIN_VALUE;
    private double lastImpactX;
    private double lastImpactZ;
    private boolean armed = true;
    private int cleanTicks;
    private long lastObservationAt = Long.MIN_VALUE;

    int epoch() { return epoch; }
    Incident pending() { return pending; }

    boolean begin(long now, long checkpointAt, int impactSequence, double x, double z,
                  boolean blockImpact, boolean checkpointValidated, boolean discrepancy) {
        if (!armed || pending != null || !blockImpact || !checkpointValidated || !discrepancy
                || !Double.isFinite(x) || !Double.isFinite(z)
                || checkpointAt >= now || now - checkpointAt > MAX_REWIND_TICKS) return false;
        pending = new Incident(++nextIncidentId, epoch, now, checkpointAt, impactSequence, x, z);
        armed = false;
        cleanTicks = 0;
        return true;
    }

    Decision evaluate(long now, Evidence evidence) {
        if (pending == null) return Decision.NONE;
        // Expiry wins over even an otherwise-valid late response.
        if (now - pending.startedAt >= EVIDENCE_TIMEOUT_TICKS) return resolve(now, Decision.COMMIT);
        if (evidence == null || evidence.incidentId != pending.id || evidence.epoch != pending.epoch) {
            return Decision.WAIT;
        }
        if (!evidence.contiguous || !evidence.completeState || !evidence.worldAvailable
                || evidence.clientImpact || evidence.replayImpact || !evidence.meaningfulDiscrepancy) {
            return resolve(now, Decision.COMMIT);
        }
        // Integer subtraction preserves the bounded sequence distance across signed wraparound.
        int startOffset = evidence.firstSequence - pending.impactSequence;
        int endOffset = evidence.lastSequence - pending.impactSequence;
        int checkpointAge = (int) (pending.startedAt - pending.checkpointAt);
        if (startOffset != -checkpointAge || endOffset < LOOKAHEAD_TICKS) return Decision.WAIT;
        return resolve(now, Decision.REWIND);
    }

    private Decision resolve(long now, Decision result) {
        lastResolvedAt = now;
        lastImpactX = pending.x;
        lastImpactZ = pending.z;
        pending = null;
        epoch++; // Invalidate evidence/packets belonging to the completed incident.
        cleanTicks = 0;
        return result;
    }

    void observe(long now, double x, double z, boolean blockContact, boolean synchronizedState) {
        if (now <= lastObservationAt) return; // Packet bursts cannot advance the clean-tick counter.
        boolean consecutive = lastObservationAt != Long.MIN_VALUE && now - lastObservationAt == 1;
        lastObservationAt = now;
        if (pending != null || armed) return;
        double distance = Math.hypot(x - lastImpactX, z - lastImpactZ);
        if (!consecutive || blockContact || !synchronizedState || !Double.isFinite(distance)
                || distance < CLEARANCE_TO_REARM) cleanTicks = 0;
        else cleanTicks++;
        if (now - lastResolvedAt >= COOLDOWN_TICKS && cleanTicks >= CLEAN_TICKS_TO_REARM) armed = true;
    }

    void resetContext(long now) {
        if (pending != null) resolve(now, Decision.COMMIT);
        else epoch++;
        armed = lastResolvedAt == Long.MIN_VALUE;
        cleanTicks = 0;
        lastObservationAt = Long.MIN_VALUE;
    }

    /** Driver/dimension changes discard evidence without carrying an incident into a new context. */
    Decision cancel(long now) {
        return pending == null ? Decision.NONE : resolve(now, Decision.COMMIT);
    }
}
