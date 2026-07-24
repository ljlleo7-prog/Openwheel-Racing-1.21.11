package com.openwheelracing.livery;

import java.util.function.Predicate;

public record LiveryTemplateRegion(
    String name,
    int x,
    int y,
    int width,
    int height,
    LiveryProjection projection,
    double sourceMinU,
    double sourceMaxU,
    double sourceMinV,
    double sourceMaxV,
    Predicate<LiveryFace> faceFilter
) {
    public boolean includes(LiveryFace face) {
        return faceFilter.test(face) && LiveryFaceColor.channelFor(face) != null;
    }

    public double pixelX(float modelX, float modelY, float modelZ) {
        double t = (projection.u(modelX, modelY, modelZ) - sourceMinU) / (sourceMaxU - sourceMinU);
        return x + t * width;
    }

    public double pixelY(float modelX, float modelY, float modelZ) {
        double t = (projection.v(modelX, modelY, modelZ) - sourceMinV) / (sourceMaxV - sourceMinV);
        return y + t * height;
    }
}
