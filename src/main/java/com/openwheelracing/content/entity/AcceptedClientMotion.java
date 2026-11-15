package com.openwheelracing.content.entity;

import com.openwheelracing.network.TechnicalRulesNetwork;
import com.openwheelracing.network.VehicleRollbackNetwork;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.server.level.ServerPlayer;

/** Validates client kinematics and dispatches each accepted segment exactly once. Never drives the car. */
final class AcceptedClientMotion {
    private final OpenwheelCarEntity car;
    private final LongSupplier nanos;
    private final ArrayDeque<VehicleRollbackNetwork.Frame> queue = new ArrayDeque<>();
    private AcceptedMotionClock clock = new AcceptedMotionClock();
    private UUID driver;
    private int epoch, lastSequence, lastQueued;
    private boolean active, engaged;
    private String decision = "not started";
    private long accepted, lastRejectedAt, lastSyncAt;
    private int rejectionStreak, lastSyncEpoch = -1;
    private net.minecraft.world.phys.Vec3 rejectedPosition;

    AcceptedClientMotion(OpenwheelCarEntity car, LongSupplier nanos) { this.car = car; this.nanos = nanos; }
    boolean active() { return active; }
    boolean engaged() { return engaged; }
    int epoch() { return epoch; }
    double time() { return clock.now(nanos.getAsLong(), car.level().getGameTime()); }
    String diagnostics() { return "OWR client-motion: active=" + active + ", epoch=" + epoch + ", last-frame="
        + lastSequence + ", queued=" + queue.size() + ", accepted=" + accepted + ", decision=" + decision; }

    void offer(VehicleRollbackNetwork.Frame frame) {
        if (!(car.getControllingPassenger() instanceof ServerPlayer player)
                || !TechnicalRulesNetwork.verified(player.getUUID()) || car.isRecoveryActive()) return;
        if (driver != null && !driver.equals(player.getUUID())) reset();
        driver = player.getUUID();
        if (frame.epoch() != epoch) { synchronize(); return; }
        if (!frame.finite() || frame.motion() == null || frame.motion().state().length != OpenwheelCarEntity.rollbackStateSize()
                || !PerformanceRules.legalControls(frame.motion().state())
                || frame.motion().counters().length != OpenwheelCarEntity.rollbackCounterSize()) {
            TechnicalRulesNetwork.violation(player, "invalid or modified performance parameters");
            return;
        }
        if (!active) { active = true; engaged = true; lastSequence = frame.sequence() - 1; lastQueued = lastSequence; }
        if (!VehiclePhysics.isNewerSequence(frame.sequence(), lastQueued)) return;
        if (frame.sequence() - lastQueued != 1 || queue.size() >= 64) { reject("movement stream gap"); return; }
        queue.addLast(frame); lastQueued = frame.sequence();
    }

    void drain() {
        if (!(car.getControllingPassenger() instanceof ServerPlayer player)
                || !player.getUUID().equals(driver)) { reset(); return; }
        if (!TechnicalRulesNetwork.verified(player.getUUID())) return;
        int work = 0;
        while (!queue.isEmpty() && work++ < AcceptedMotionClock.BURST_FRAMES && clock.ready(nanos.getAsLong())) {
            var frame = queue.removeFirst();
            var motion = frame.motion();
            var before = car.position();
            var delta = frame.position().subtract(before);
            if (delta.horizontalDistance() > 6.25 || Math.abs(delta.y) > 8
                    || motion.velocity().horizontalDistance() > 6.25 || Math.abs(motion.velocity().y) > 8
                    || motion.position().distanceToSqr(frame.position()) > 1e-10
                    || Math.abs(net.minecraft.util.Mth.wrapDegrees(motion.yaw() - frame.yaw())) > 0.001) {
                reject("movement envelope"); return;
            }
            if (motion.counters()[7] < 0 || motion.counters()[7] > 25000
                    || motion.counters()[8] < 0 || motion.counters()[8] > 1
                    || motion.counters()[9] < 0 || motion.counters()[9] > 1
                    || motion.counters()[10] < 0 || motion.counters()[10] > 1
                    || motion.counters()[11] < OpenwheelCarEntity.ERS_MODE_HARVEST
                    || motion.counters()[11] > OpenwheelCarEntity.ERS_MODE_ATTACK
                    || motion.counters()[12] < OpenwheelCarEntity.ERS_ACTIVITY_NEUTRAL
                    || motion.counters()[12] > OpenwheelCarEntity.ERS_ACTIVITY_NEGATIVE) {
                TechnicalRulesNetwork.violation(player, "invalid engine telemetry"); return;
            }
            if (!car.clientMotionPathClear(before, delta, frame.yaw())) { reject("blocked or unloaded movement"); return; }
            var interval = clock.accept(frame.sequence(), car.level().getGameTime(), nanos.getAsLong());
            car.applyAcceptedClientMotion(frame, interval.start(), interval.end());
            lastSequence = frame.sequence(); accepted++; decision = "accepted";
        }
    }

    void reject(String reason) {
        decision = "rejected: " + reason;
        long now = nanos.getAsLong();
        rejectionStreak = rejectedPosition != null && rejectedPosition.distanceToSqr(car.position()) < 16
            && now - lastRejectedAt < 2_000_000_000L ? rejectionStreak + 1 : 1;
        rejectedPosition = car.position(); lastRejectedAt = now;
        car.stopRejectedClientMotion();
        queue.clear(); epoch++; lastQueued = lastSequence;
        lastSyncEpoch = epoch; lastSyncAt = now;
        if (car.getControllingPassenger() instanceof ServerPlayer player) {
            VehicleRollbackNetwork.send(player, new VehicleRollbackNetwork.Update(car.getId(), epoch, epoch,
                lastSequence, rejectionStreak >= 2 ? VehicleRollbackNetwork.BLOCKED : VehicleRollbackNetwork.SYNC,
                0, car.captureRollbackMotion(), car.getUUID(), car.level().dimension().identifier().toString()));
        }
    }

    private void synchronize() {
        long now = nanos.getAsLong();
        if (lastSyncEpoch == epoch && now - lastSyncAt < 500_000_000L) return;
        lastSyncEpoch = epoch; lastSyncAt = now;
        if (car.getControllingPassenger() instanceof ServerPlayer player) {
            VehicleRollbackNetwork.send(player, new VehicleRollbackNetwork.Update(car.getId(), epoch, epoch,
                lastSequence, VehicleRollbackNetwork.SYNC, 0, car.captureRollbackMotion(),
                car.getUUID(), car.level().dimension().identifier().toString()));
        }
    }

    void beginRecovery() { queue.clear(); epoch++; clock = new AcceptedMotionClock(); synchronize(); }

    void reset() {
        if (!active && driver == null && queue.isEmpty()) return;
        rejectionStreak = 0; rejectedPosition = null;
        queue.clear(); active = false; driver = null; epoch++;
        clock = new AcceptedMotionClock();
    }
}
