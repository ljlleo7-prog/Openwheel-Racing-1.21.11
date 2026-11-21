package com.openwheelracing.content.entity;

/** Shared remote presentation/contact pose. No renderer state or entity mutation is involved. */
public final class RemoteCarPose {
    public static final double MAX_OFFSET = 16.0;
    private static final long BLEND_NANOS = 50_000_000L;

    public record Pose(double x, double y, double z, float yaw) {
        double distanceSquared(Pose other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private Pose from, target;
    private long changedAt;

    /** Both rendering and collision call this with the same monotonic clock and logical pose. */
    public Pose sample(long now, double x, double y, double z, float yaw) {
        Pose incoming = new Pose(x, y, z, yaw);
        if (target == null) {
            from = target = incoming;
            changedAt = now;
        } else if (!target.equals(incoming)) {
            Pose visible = interpolate(now);
            // A teleport must not leave a ghost collider along the old-to-new path.
            boolean snap = target.distanceSquared(incoming) > MAX_OFFSET * MAX_OFFSET
                || visible.distanceSquared(incoming) > MAX_OFFSET * MAX_OFFSET;
            from = snap ? incoming : visible;
            target = incoming;
            changedAt = now;
        }
        return interpolate(now);
    }

    public void clear() { from = target = null; }

    private Pose interpolate(long now) {
        double t = Math.clamp((double) (now - changedAt) / BLEND_NANOS, 0.0, 1.0);
        float yawDelta = wrapDegrees(target.yaw - from.yaw);
        return new Pose(from.x + (target.x - from.x) * t,
            from.y + (target.y - from.y) * t, from.z + (target.z - from.z) * t,
            from.yaw + yawDelta * (float) t);
    }

    private static float wrapDegrees(float angle) {
        float wrapped = angle % 360.0f;
        if (wrapped >= 180) wrapped -= 360;
        if (wrapped < -180) wrapped += 360;
        return wrapped;
    }
}
