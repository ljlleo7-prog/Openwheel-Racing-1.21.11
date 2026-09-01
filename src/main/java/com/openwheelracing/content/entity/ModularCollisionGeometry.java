package com.openwheelracing.content.entity;

final class ModularCollisionGeometry {
    private static final double EPSILON = 1.0E-9;

    private ModularCollisionGeometry() {
    }

    static double firstContactTime(Rectangle moving, double movementX, double movementZ, Rectangle stationary) {
        double entry = 0.0;
        double exit = 1.0;
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

            if (Math.abs(projectedMovement) <= EPSILON) {
                if (Math.abs(separation) > combinedRadius + EPSILON) {
                    return Double.NaN;
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
            entry = Math.max(entry, axisEntry);
            exit = Math.min(exit, axisExit);
            if (entry - exit > EPSILON) {
                return Double.NaN;
            }
        }
        return exit >= -EPSILON && entry <= 1.0 + EPSILON ? Math.max(0.0, entry) : Double.NaN;
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
