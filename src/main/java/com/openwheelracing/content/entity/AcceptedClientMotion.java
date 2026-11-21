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
    private int lastPublishedStatus = VehicleRollbackNetwork.DRIVER_CONTROL;
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
                || !TechnicalRulesNetwork.verified(player.getUUID()) || car.isDrivingSuspended()) return;
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
        if (!TechnicalRulesNetwork.verified(player.getUUID()) || car.isDrivingSuspended()) return;
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
            if (motion.counters()[13] != car.getTyreCompound() || motion.counters()[14] != car.getTyreType().id()
                    || motion.counters()[15] != (car.hasTyres() ? 1 : 0)) {
                TechnicalRulesNetwork.violation(player, "tyre configuration differs from the server"); return;
            }
            if (motion.counters()[17] < 0 || motion.counters()[17] > 1
                    || motion.counters()[16] < 0 || motion.counters()[16] > 1
                    || Math.hypot(motion.state()[71], motion.state()[72]) > 6.251) {
                TechnicalRulesNetwork.violation(player, "invalid car-contact telemetry"); return;
            }
            if (!car.clientMotionAreaLoaded(delta)) { reject("unloaded or out-of-border movement"); return; }
            var interval = clock.accept(frame.sequence(), car.level().getGameTime(), nanos.getAsLong());
            boolean contact = car.applyAcceptedClientMotion(frame, interval.start(), interval.end());
            lastSequence = frame.sequence(); accepted++; decision = "accepted";
            if (contact) { carContactBoundary(); return; }
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
        lastPublishedStatus = rejectionStreak >= 2 ? VehicleRollbackNetwork.BLOCKED : VehicleRollbackNetwork.SYNC;
        if (car.getControllingPassenger() instanceof ServerPlayer player) {
            VehicleRollbackNetwork.send(player, new VehicleRollbackNetwork.Update(car.getId(), epoch, epoch,
                lastSequence, lastPublishedStatus,
                0, car.captureRollbackMotion(), car.getUUID(), car.level().dimension().identifier().toString()));
        }
    }

    private void synchronize() {
        long now = nanos.getAsLong();
        if (lastSyncEpoch == epoch && now - lastSyncAt < 500_000_000L) return;
        lastSyncEpoch = epoch; lastSyncAt = now;
        if (car.getControllingPassenger() instanceof ServerPlayer player) {
            VehicleRollbackNetwork.send(player, new VehicleRollbackNetwork.Update(car.getId(), epoch, epoch,
                lastSequence, lastPublishedStatus, 0, car.captureRollbackMotion(),
                car.getUUID(), car.level().dimension().identifier().toString()));
        }
    }

    void carContactBoundary() {
        queue.clear(); lastQueued = lastSequence; epoch++;
        clock.resumeAfterHold();
        decision = "car contact";
        rejectionStreak = 0; rejectedPosition = null;
        publish(car.isDrivingSuspended() ? VehicleRollbackNetwork.SERVER_CONTROL : VehicleRollbackNetwork.CAR_CONTACT);
    }

    void driverChanged() {
        reset();
        if (!(car.getControllingPassenger() instanceof ServerPlayer player)) return;
        engaged = true; driver = player.getUUID(); lastSequence = lastQueued = 0;
        epoch++;
        publish(VehicleRollbackNetwork.DRIVER_CONTROL);
        // If a mounting snapshot arrived before entity tracking, the first epoch mismatch may retry immediately.
        lastSyncEpoch = -1;
    }

    void serverOperation(boolean release) {
        engaged = true;
        queue.clear(); lastQueued = lastSequence; epoch++;
        rejectionStreak = 0; rejectedPosition = null;
        car.stopRejectedClientMotion();
        if (release) clock.resumeAfterHold();
        publish(release ? VehicleRollbackNetwork.DRIVER_CONTROL : VehicleRollbackNetwork.SERVER_CONTROL);
    }

    private void publish(int status) {
        lastPublishedStatus = status;
        lastSyncEpoch = epoch; lastSyncAt = nanos.getAsLong();
        if (car.getControllingPassenger() instanceof ServerPlayer player) {
            VehicleRollbackNetwork.send(player, new VehicleRollbackNetwork.Update(car.getId(), epoch, epoch,
                lastSequence, status, 0, car.captureRollbackMotion(), car.getUUID(),
                car.level().dimension().identifier().toString()));
        }
    }

    void reset() {
        if (!active && driver == null && queue.isEmpty()) return;
        rejectionStreak = 0; rejectedPosition = null;
        queue.clear(); active = false; driver = null; epoch++;
        clock = new AcceptedMotionClock();
    }
}
