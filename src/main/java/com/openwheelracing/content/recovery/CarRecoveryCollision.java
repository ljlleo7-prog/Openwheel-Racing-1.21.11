package com.openwheelracing.content.recovery;

/** Existing overlap may shrink or slide, but a recovery step must not deepen it. */
public final class CarRecoveryCollision {
    public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Bounds move(double x, double y, double z) {
            return new Bounds(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
        }
    }

    private CarRecoveryCollision() {
    }

    public static boolean allows(Bounds before, Bounds after, Bounds obstacle) {
        double previous = overlapVolume(before, obstacle);
        double next = overlapVolume(after, obstacle);
        return next <= previous + 1.0E-9;
    }

    private static double overlapVolume(Bounds box, Bounds obstacle) {
        return Math.max(0, Math.min(box.maxX, obstacle.maxX) - Math.max(box.minX, obstacle.minX))
            * Math.max(0, Math.min(box.maxY, obstacle.maxY) - Math.max(box.minY, obstacle.minY))
            * Math.max(0, Math.min(box.maxZ, obstacle.maxZ) - Math.max(box.minZ, obstacle.minZ));
    }
}
