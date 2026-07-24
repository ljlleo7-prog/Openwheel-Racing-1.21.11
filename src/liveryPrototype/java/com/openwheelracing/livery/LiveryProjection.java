package com.openwheelracing.livery;

public enum LiveryProjection {
    TOP {
        @Override
        double u(float x, float y, float z) {
            return x;
        }

        @Override
        double v(float x, float y, float z) {
            return z;
        }
    },
    SIDE {
        @Override
        double u(float x, float y, float z) {
            return z;
        }

        @Override
        double v(float x, float y, float z) {
            return -y;
        }
    };

    abstract double u(float x, float y, float z);

    abstract double v(float x, float y, float z);
}
