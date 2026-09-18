package com.openwheelracing.content.entity;

final class ModularCollisionGeometry {
    private static final double EPSILON = 1.0E-9;

    private ModularCollisionGeometry() {
    }

    static double firstContactTime(Rectangle moving, double movementX, double movementZ, Rectangle stationary) {
        Contact contact = firstContact(moving, movementX, movementZ, stationary);
        return contact == null ? Double.NaN : contact.time;
    }

    static Contact firstContact(Rectangle moving, double movementX, double movementZ, Rectangle stationary) {
        double entry = 0.0;
        double exit = 1.0;
        double minimumInitialOverlap = Double.POSITIVE_INFINITY;
        double initialNormalX = 0.0;
        double initialNormalZ = 0.0;
        double entryNormalX = 0.0;
        double entryNormalZ = 0.0;
        boolean initiallyOverlapping = true;
        double[][] axes = {
            {moving.rightX, moving.rightZ},
            {moving.forwardX, moving.forwardZ},
            {stationary.rightX, stationary.rightZ},
            {stationary.forwardX, stationary.forwardZ}
        };
        for (double[] axis : axes) {
            double movingCenter = moving.centerX * axis[0] + moving.centerZ * axis[1];
            double stationaryCenter = stationary.centerX * axis[0] + stationary.centerZ * axis[1];
            double movingRadius = moving.projectedRadius(axis[0], axis[1]);
            double stationaryRadius = stationary.projectedRadius(axis[0], axis[1]);
            double separation = stationaryCenter - movingCenter;
            double combinedRadius = movingRadius + stationaryRadius;
            double projectedMovement = movementX * axis[0] + movementZ * axis[1];
            double initialOverlap = combinedRadius - Math.abs(separation);

            if (initialOverlap < -EPSILON) {
                initiallyOverlapping = false;
            } else if (initialOverlap < minimumInitialOverlap) {
                minimumInitialOverlap = initialOverlap;
                double movingSide = movingCenter - stationaryCenter;
                double sign = Math.abs(movingSide) > EPSILON
                    ? Math.signum(movingSide)
                    : (Math.abs(projectedMovement) > EPSILON ? -Math.signum(projectedMovement) : 1.0);
                initialNormalX = axis[0] * sign;
                initialNormalZ = axis[1] * sign;
            }

            if (Math.abs(projectedMovement) <= EPSILON) {
                if (Math.abs(separation) > combinedRadius + EPSILON) {
                    return null;
                }
                continue;
            }

            double axisEntry = (separation - combinedRadius) / projectedMovement;
            double axisExit = (separation + combinedRadius) / projectedMovement;
            if (axisEntry > axisExit) {
                double swap = axisEntry;
                axisEntry = axisExit;
                axisExit = swap;
            }
            if (axisEntry > entry) {
                entry = axisEntry;
                double movingCenterAtEntry = movingCenter + projectedMovement * axisEntry;
                double sign = Math.signum(movingCenterAtEntry - stationaryCenter);
                if (sign == 0.0) {
                    sign = -Math.signum(projectedMovement);
                }
                entryNormalX = axis[0] * sign;
                entryNormalZ = axis[1] * sign;
            }
            exit = Math.min(exit, axisExit);
            if (entry - exit > EPSILON) {
                return null;
            }
        }
        if (initiallyOverlapping) {
            return new Contact(0.0, initialNormalX, initialNormalZ);
        }
        return exit >= -EPSILON && entry <= 1.0 + EPSILON
            ? new Contact(Math.max(0.0, entry), entryNormalX, entryNormalZ)
            : null;
    }

    record Contact(double time, double normalX, double normalZ) {
    }

    record Rectangle(double centerX, double centerZ,
                     double rightX, double rightZ, double forwardX, double forwardZ,
                     double halfWidth, double halfLength) {
        private double projectedRadius(double axisX, double axisZ) {
            return Math.abs(rightX * axisX + rightZ * axisZ) * halfWidth
                + Math.abs(forwardX * axisX + forwardZ * axisZ) * halfLength;
        }
    }
}
