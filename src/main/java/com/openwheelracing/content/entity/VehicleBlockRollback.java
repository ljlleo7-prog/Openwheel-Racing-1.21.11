package com.openwheelracing.content.entity;

import com.openwheelracing.network.VehicleRollbackNetwork;
import com.openwheelracing.network.VehicleRollbackNetwork.Frame;
import com.openwheelracing.network.VehicleRollbackNetwork.Update;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

/** One bounded, block-only adjudication per vehicle. All methods run on the server thread. */
final class VehicleBlockRollback {
    private static final int HISTORY = 64;
    private final OpenwheelCarEntity car;
    private final BlockCollisionRollbackPolicy policy = new BlockCollisionRollbackPolicy();
    private final Map<Integer, Frame> frames = new LinkedHashMap<>();
    private final Map<Integer, OpenwheelCarEntity.RollbackCheckpoint> checkpoints = new LinkedHashMap<>();
    private UUID driver;
    private boolean active;
    private boolean enabled;
    private int simulatedFrame;
    private Frame latest;
    private Frame step;
    private long lastFrameAt;
    private long lastSyncAt = Long.MIN_VALUE;
    private OpenwheelCarEntity.RollbackCheckpoint anchor;
    private int anchorFrame;
    private boolean finishing;
    private long lastIncidentId;
    private final ConservativeMotionSync motionSync = new ConservativeMotionSync();
    private int ordinaryCorrections;
    private BlockPos geometryMin;
    private BlockPos geometryMax;
    private long impactGeometry;
    private Integer unconfirmedFrom;
    private int repairedInputs;
    private String lastImpactDecision = "no eligible block impact observed";

    String diagnostics() {
        return "OWR server: framed=" + active + ", auto-rewind=" + enabled + ", epoch=" + epoch()
            + ", simulated-frame=" + simulatedFrame + ", received-frame=" + (latest == null ? "none" : latest.sequence())
            + ", packet-age-ticks=" + (latest == null ? "n/a" : now() - lastFrameAt)
            + ", pending=" + pending() + ", unconfirmed-from=" + unconfirmedFrom
            + ", late-input-repairs=" + repairedInputs + ", ordinary-corrections=" + ordinaryCorrections + ", last-impact=" + lastImpactDecision;
    }


    VehicleBlockRollback(OpenwheelCarEntity car) { this.car = car; }
    int epoch() { return policy.epoch(); }
    boolean active() { return active; }
    boolean pending() { return policy.pending() != null; }
    Frame inputForStep() { return active ? step : null; }
    private long now() { return car.level().getGameTime(); }

    void accept(Frame frame) {
        if (!(car.getControllingPassenger() instanceof ServerPlayer player) || car.isRecoveryActive() || !frame.finite()
                || !car.getUUID().equals(frame.vehicleUuid())
                || !car.level().dimension().identifier().toString().equals(frame.dimension())) return;
        if (driver != null && !driver.equals(player.getUUID())) resetContext();
        if (driver == null) driver = player.getUUID();
        if (frame.epoch() != epoch()) {
            if (now() != lastSyncAt) { send(VehicleRollbackNetwork.SYNC, 0, car.captureRollbackMotion()); lastSyncAt = now(); }
            return;
        }
        if (latest != null && !VehiclePhysics.isNewerSequence(frame.sequence(), latest.sequence())) return;
        if (active && Math.abs((long) (frame.sequence() - simulatedFrame)) > HISTORY) {
            // A long client/server tick-rate skew must not leave the stream permanently unusable.
            if (pending()) finish(false, true);
            policy.resetContext(now());
            motionSync.reset();
            simulatedFrame = frame.sequence() - 1;
            unconfirmedFrom = null;
            frames.clear(); checkpoints.clear(); latest = null; step = null;
            send(VehicleRollbackNetwork.SYNC, 0, car.captureRollbackMotion());
            return;
        }
        if (latest != null && frame.sequence() - latest.sequence() > HISTORY) return;
        if (!active) {
            simulatedFrame = frame.sequence() - 1;
            checkpoints.put(simulatedFrame, car.captureRollbackCheckpoint());
            active = true;
        }
        frames.put(frame.sequence(), frame);
        trim(frames);
        latest = frame;
        enabled = frame.enabled();
        lastFrameAt = now();
    }

    boolean beforeMovement() {
        if (driver != null && (!(car.getControllingPassenger() instanceof ServerPlayer player)
                || !driver.equals(player.getUUID()) || !player.isAlive() || car.isRecoveryActive())) {
            if (pending()) finish(false, true);
            resetContext();
        }
        if (!active) return true;
        if (latest == null && !pending()) return false;
        if (pending()) {
            adjudicate();
            return false; // A resolution resumes on the following tick, never twice in the same tick.
        }
        repairLateInputs();
        if (latest == null) return false;
        Frame matching = frames.get(simulatedFrame);
        policy.observe(now(), car.getX(), car.getZ(), car.horizontalCollision,
            matching != null && now() - lastFrameAt < 5 && matching.position().distanceToSqr(car.position()) < 1.0);
        simulatedFrame++;
        Frame exact = frames.get(simulatedFrame);
        if (exact != null) step = exact;
        else {
            step = latest;
            if (unconfirmedFrom == null) unconfirmedFrom = simulatedFrame;
        }
        if (!checkpoints.containsKey(simulatedFrame - 1)) {
            checkpoints.put(simulatedFrame - 1, car.captureRollbackCheckpoint());
        }
        trim(checkpoints);
        return true;
    }

    void afterMovement(boolean moved) {
        if (active && moved && !pending()) {
            checkpoints.put(simulatedFrame, car.captureRollbackCheckpoint());
            trim(checkpoints);
        }
    }

    void afterImpact() {
        Frame matched = frames.get(simulatedFrame);
        boolean confirmed = active && !pending() && latest != null && unconfirmedFrom == null && matched != null;
        double error = matched == null ? 0.0 : matched.position().distanceToSqr(car.position());
        if (motionSync.shouldCorrect(now(), confirmed, error)) {
            send(VehicleRollbackNetwork.RECONCILE, 0, car.captureRollbackMotion());
            ordinaryCorrections++;
        }
    }

    private void repairLateInputs() {
        if (unconfirmedFrom == null) return;
        int count = simulatedFrame - unconfirmedFrom + 1;
        if (count > 10) {
            // A real stream gap gets one explicit resync, not a perpetual correction loop.
            policy.resetContext(now());
            motionSync.reset();
            frames.clear(); checkpoints.clear(); latest = null; step = null;
            unconfirmedFrom = null;
            send(VehicleRollbackNetwork.SYNC, 0, car.captureRollbackMotion());
            return;
        }
        var base = checkpoints.get(unconfirmedFrom - 1);
        if (base == null || !base.clean() || !car.rollbackTimingCompatible(base)) return;
        for (int i = 0; i < count; i++) {
            int frame = unconfirmedFrom + i;
            var old = checkpoints.get(frame);
            if (!frames.containsKey(frame) || old == null || !old.clean()) return;
        }
        OpenwheelCarEntity replay = car.replayVehicle(base);
        if (!loaded(replay)) return;
        double distance = base.lapDistance();
        var replacements = new LinkedHashMap<Integer, OpenwheelCarEntity.RollbackCheckpoint>();
        for (int i = 0; i < count; i++) {
            int sequence = unconfirmedFrom + i;
            Frame input = frames.get(sequence);
            var before = replay.position();
            if (input.collision() || !loaded(replay) || !replay.validRollbackGear(input)
                    || !replay.replayRollbackFrame(input)
                    || !car.safeLateInputSegment(before, replay.position())) return;
            distance += replay.position().subtract(before).horizontalDistance();
            replacements.put(sequence, car.replayedCheckpoint(replay, base, distance));
        }
        if (!clearCheckpoint(replay) || !car.rollbackTimingCompatible(base)) return;
        car.restoreLateInputMotion(replacements.get(simulatedFrame));
        checkpoints.putAll(replacements);
        unconfirmedFrom = null;
        repairedInputs++;
    }

    private boolean skipImpact(String reason) {
        lastImpactDecision = reason;
        return false;
    }

    boolean tryBeginImpact() {
        if (!active) return skipImpact("framed protocol not active");
        if (!enabled) return skipImpact("driver disabled automatic rewind");
        if (finishing || pending()) return false;
        if (now() - lastFrameAt > 20) return skipImpact("client evidence stale");
        if (!(car.level() instanceof ServerLevel)) return false;
        Frame observed = frames.get(simulatedFrame);
        if (observed != null && observed.collision()) return skipImpact("client also reported collision");
        if (observed != null && observed.position().distanceToSqr(car.position()) < 0.0625)
            return skipImpact("no positional crash discrepancy");
        anchorFrame = simulatedFrame - BlockCollisionRollbackPolicy.MAX_REWIND_TICKS;
        anchor = checkpoints.get(anchorFrame);
        if (anchor == null || !anchor.clean()) return skipImpact("no clean three-tick checkpoint");
        if (!car.rollbackTimingCompatible(anchor)) return skipImpact("timing boundary or recovery");
        if (!car.level().getEntities(car, car.getBoundingBox().inflate(1.0),
                entity -> entity != car.getControllingPassenger() && entity.isAlive() && entity.isPickable()).isEmpty())
            return skipImpact("nearby entity involvement");
        AABB geometry = car.getBoundingBox().inflate(4.0, 2.0, 4.0);
        geometryMin = BlockPos.containing(geometry.minX, geometry.minY, geometry.minZ);
        geometryMax = BlockPos.containing(geometry.maxX, geometry.maxY, geometry.maxZ);
        if (!car.level().hasChunksAt(geometryMin, geometryMax)) return skipImpact("impact chunks unavailable");
        impactGeometry = geometryHash();
        if (!policy.begin(now(), now() - 3, simulatedFrame, car.getX(), car.getZ(), true, true, true))
            return skipImpact("cooldown or not rearmed");
        lastImpactDecision = "checking";
        lastIncidentId = policy.pending().id();
        send(VehicleRollbackNetwork.CHECKING, 3, null);
        return true;
    }

    private void adjudicate() {
        var incident = policy.pending();
        if (incident == null) return;
        if (!enabled) { finish(false, true); return; }
        if (now() - incident.startedAt() >= BlockCollisionRollbackPolicy.EVIDENCE_TIMEOUT_TICKS) {
            policy.evaluate(now(), null);
            finishResolved(false, true, incident.id());
            return;
        }
        if (!car.level().hasChunksAt(geometryMin, geometryMax) || geometryHash() != impactGeometry) {
            finish(false, true);
            return;
        }
        Frame first = frames.get(anchorFrame);
        if (first == null) return;
        int last = incident.impactSequence() + BlockCollisionRollbackPolicy.LOOKAHEAD_TICKS;
        boolean ready = true;
        for (int sequence = anchorFrame; sequence != last + 1; sequence++) {
            Frame frame = frames.get(sequence);
            if (frame == null) { ready = false; break; }
            if (frame.collision()) { finish(false, false); return; }
            if (frame.correctionSerial() != first.correctionSerial()) { finish(false, true); return; }
        }
        if (!ready) return;
        if (first.position().distanceToSqr(anchor.motion().position()) > 0.5625
                || !car.rollbackTimingCompatible(anchor)) { finish(false, true); return; }
        Frame impact = frames.get(incident.impactSequence());
        if (impact.position().distanceToSqr(car.position()) < 0.0625) { finish(false, false); return; }
        OpenwheelCarEntity ghost = car.replayVehicle(anchor);
        boolean collision = false;
        boolean valid = loaded(ghost) && clearCheckpoint(ghost);
        for (int sequence = anchorFrame + 1; valid && sequence != last + 1; sequence++) {
            Frame frame = frames.get(sequence);
            if (!loaded(ghost)) { valid = false; break; }
            if (!ghost.validRollbackGear(frame)) { valid = false; break; }
            if (!ghost.replayRollbackFrame(frame)) {
                collision = ghost.horizontalCollision;
                if (!collision) valid = false;
                break;
            }
            if (ghost.position().distanceToSqr(frame.position()) > 1.0
                    || Math.abs(net.minecraft.util.Mth.wrapDegrees(ghost.getYRot() - frame.yaw())) > 15.0) valid = false;
        }
        // Recheck destination now, including entities, before restoring an occupied vehicle.
        boolean destinationClear = clearCheckpoint(car.replayVehicle(anchor));
        if (valid && !collision && destinationClear) {
            // The driver is now holding a later input than the checkpoint's next recorded input.
            // Do not restore into an immediate crash under that current control.
            OpenwheelCarEntity reaction = car.replayVehicle(anchor);
            Frame currentControl = frames.get(last);
            for (int i = 0; i < BlockCollisionRollbackPolicy.MAX_REWIND_TICKS; i++) {
                if (!loaded(reaction) || !reaction.validRollbackGear(currentControl)
                        || !reaction.replayRollbackFrame(currentControl)) {
                    valid = false;
                    break;
                }
            }
        }
        var evidence = new BlockCollisionRollbackPolicy.Evidence(incident.id(), incident.epoch(),
            anchorFrame, last, true, valid && destinationClear, valid, false, collision, true);
        var result = policy.evaluate(now(), evidence);
        if (result == BlockCollisionRollbackPolicy.Decision.REWIND) finishResolved(true, false, incident.id());
        else if (result == BlockCollisionRollbackPolicy.Decision.COMMIT) finishResolved(false, !valid, incident.id());
    }

    private long geometryHash() {
        long hash = 0xcbf29ce484222325L;
        for (BlockPos pos : BlockPos.betweenClosed(geometryMin, geometryMax)) {
            hash = (hash ^ net.minecraft.world.level.block.Block.getId(car.level().getBlockState(pos)))
                * 0x100000001b3L;
        }
        return hash;
    }

    private boolean loaded(OpenwheelCarEntity ghost) {
        AABB area = ghost.getBoundingBox().inflate(9.0, 2.0, 9.0);
        return ghost.level().hasChunksAt(BlockPos.containing(area.minX, area.minY, area.minZ),
            BlockPos.containing(area.maxX, area.maxY, area.maxZ));
    }

    private boolean clearCheckpoint(OpenwheelCarEntity ghost) {
        if (!loaded(ghost)) return false;
        // Check blocks separately so the driver's own live car does not invalidate a near checkpoint.
        for (var shape : ghost.level().getBlockCollisions(ghost, ghost.getBoundingBox().deflate(1.0E-5))) {
            if (!shape.isEmpty()) return false;
        }
        return ghost.level().getEntities(car, ghost.getBoundingBox(),
            entity -> entity != car.getControllingPassenger() && entity.isAlive() && entity.isPickable()).isEmpty();
    }

    private void finish(boolean rewind, boolean uncertain) {
        var incident = policy.pending();
        if (incident == null) return;
        policy.cancel(now());
        finishResolved(rewind, uncertain, incident.id());
    }

    private void finishResolved(boolean rewind, boolean uncertain, long incidentId) {
        lastImpactDecision = rewind ? "rewound: replay clear" : uncertain
            ? "committed: evidence incomplete or unsafe" : "committed: collision confirmed";
        finishing = true;
        motionSync.reset();
        try {
            if (rewind) {
                car.restoreRollbackCheckpoint(anchor);
                simulatedFrame = anchorFrame;
            } else {
                car.applyBlockImpactNow();
            }
            frames.clear();
            checkpoints.clear();
            unconfirmedFrom = null;
            latest = null;
            step = null;
            // Keep the bridge active, rejecting vanilla movement throughout the epoch transition.
            send(rewind ? VehicleRollbackNetwork.REWOUND
                : uncertain ? VehicleRollbackNetwork.UNVERIFIED : VehicleRollbackNetwork.CONFIRMED,
                rewind ? 3 : 0, car.captureRollbackMotion(), incidentId);
        } finally {
            finishing = false;
        }
    }

    void cancelForRecovery() {
        if (pending()) finish(false, true);
        resetContext();
        send(VehicleRollbackNetwork.SYNC, 0, car.captureRollbackMotion());
    }

    private void resetContext() {
        motionSync.reset();
        policy.resetContext(now());
        frames.clear(); checkpoints.clear(); latest = null; step = null;
        active = false; enabled = false; driver = null; anchor = null; unconfirmedFrom = null;
    }

    private void send(int status, int ticks, OpenwheelCarEntity.RollbackMotion motion) {
        send(status, ticks, motion, policy.pending() == null ? lastIncidentId : policy.pending().id());
    }
    private void send(int status, int ticks, OpenwheelCarEntity.RollbackMotion motion, long incidentId) {
        if (car.getControllingPassenger() instanceof ServerPlayer player) {
            VehicleRollbackNetwork.send(player, new Update(car.getId(), incidentId, epoch(), simulatedFrame, status, ticks, motion,
                car.getUUID(), car.level().dimension().identifier().toString()));
        }
    }
    private static void trim(Map<Integer, ?> map) {
        while (map.size() > HISTORY) map.remove(map.keySet().iterator().next());
    }
}
