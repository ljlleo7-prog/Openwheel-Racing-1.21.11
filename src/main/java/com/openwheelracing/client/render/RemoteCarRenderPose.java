package com.openwheelracing.client.render;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Keeps remote car smoothing strictly in render state. The entity's logical position remains
 * authoritative client/server state and is never changed by this class.
 */
final class RemoteCarRenderPose {
    private static final double SNAP_DISTANCE_SQUARED = 16.0 * 16.0;
    private static final Map<OpenwheelCarEntity, Pose> POSES = new WeakHashMap<>();

    private RemoteCarRenderPose() {
    }

    static float apply(OpenwheelCarEntity car, EntityRenderState state, float partialTick, boolean localCar) {
        if (localCar) {
            POSES.remove(car);
            return car.getYRot(partialTick);
        }

        Pose pose = POSES.computeIfAbsent(car, ignored -> new Pose());
        pose.observe(car);
        double alpha = Math.max(0.0, Math.min(1.0, partialTick));
        state.x = lerp(pose.previousX, pose.targetX, alpha);
        state.y = lerp(pose.previousY, pose.targetY, alpha);
        state.z = lerp(pose.previousZ, pose.targetZ, alpha);
        return lerpDegrees(pose.previousYaw, pose.targetYaw, alpha);
    }

    private static double lerp(double from, double to, double alpha) {
        return from + (to - from) * alpha;
    }

    private static float lerpDegrees(float from, float to, double alpha) {
        float delta = ((to - from + 540.0f) % 360.0f) - 180.0f;
        return from + delta * (float) alpha;
    }

    private static final class Pose {
        private boolean initialized;
        private int observedTick;
        private double previousX;
        private double previousY;
        private double previousZ;
        private double targetX;
        private double targetY;
        private double targetZ;
        private float previousYaw;
        private float targetYaw;

        private void observe(OpenwheelCarEntity car) {
            double x = car.getX();
            double y = car.getY();
            double z = car.getZ();
            float yaw = car.getYRot();
            int tick = car.tickCount;
            if (!initialized) {
                initialized = true;
                observedTick = tick;
                previousX = targetX = x;
                previousY = targetY = y;
                previousZ = targetZ = z;
                previousYaw = targetYaw = yaw;
                return;
            }

            double dx = x - targetX;
            double dy = y - targetY;
            double dz = z - targetZ;
            boolean changed = tick != observedTick || dx != 0.0 || dy != 0.0 || dz != 0.0
                || Math.abs(((yaw - targetYaw + 540.0f) % 360.0f) - 180.0f) > 0.001f;
            if (!changed) {
                return;
            }
            observedTick = tick;
            if (dx * dx + dy * dy + dz * dz > SNAP_DISTANCE_SQUARED) {
                previousX = targetX = x;
                previousY = targetY = y;
                previousZ = targetZ = z;
                previousYaw = targetYaw = yaw;
                return;
            }
            previousX = targetX;
            previousY = targetY;
            previousZ = targetZ;
            previousYaw = targetYaw;
            targetX = x;
            targetY = y;
            targetZ = z;
            targetYaw = yaw;
        }
    }
}
