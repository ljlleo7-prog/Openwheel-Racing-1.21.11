package com.openwheelracing.livery;

public record LiveryFace(
    float x0, float y0, float z0,
    float x1, float y1, float z1,
    float x2, float y2, float z2,
    float x3, float y3, float z3,
    int vertexCount,
    int materialRgb,
    String group
) {
    public LiveryFace(
        float x0, float y0, float z0,
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        int materialRgb,
        String group
    ) {
        this(x0, y0, z0, x1, y1, z1, x2, y2, z2, x2, y2, z2, 3, materialRgb, group);
    }

    public float centroidX() {
        return vertexCount == 4 ? (x0 + x1 + x2 + x3) / 4.0f : (x0 + x1 + x2) / 3.0f;
    }

    public float centroidY() {
        return vertexCount == 4 ? (y0 + y1 + y2 + y3) / 4.0f : (y0 + y1 + y2) / 3.0f;
    }

    public float centroidZ() {
        return vertexCount == 4 ? (z0 + z1 + z2 + z3) / 4.0f : (z0 + z1 + z2) / 3.0f;
    }
}
