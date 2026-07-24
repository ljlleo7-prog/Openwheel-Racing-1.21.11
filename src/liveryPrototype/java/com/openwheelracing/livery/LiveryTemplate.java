package com.openwheelracing.livery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public final class LiveryTemplate {
    private final int width;
    private final int height;
    private final List<LiveryTemplateRegion> regions;

    public LiveryTemplate(int width, int height, List<LiveryTemplateRegion> regions) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Template dimensions must be positive");
        }
        this.width = width;
        this.height = height;
        this.regions = Collections.unmodifiableList(new ArrayList<>(regions));
        validateRegions();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public List<LiveryTemplateRegion> regions() {
        return regions;
    }

    private void validateRegions() {
        for (LiveryTemplateRegion region : regions) {
            if (region.x() < 0 || region.y() < 0 || region.width() <= 0 || region.height() <= 0) {
                throw new IllegalArgumentException("Invalid livery region: " + region.name());
            }
            if (region.x() + region.width() > width || region.y() + region.height() > height) {
                throw new IllegalArgumentException("Livery region outside template: " + region.name());
            }
            if (region.sourceMinU() >= region.sourceMaxU() || region.sourceMinV() >= region.sourceMaxV()) {
                throw new IllegalArgumentException("Invalid source bounds for livery region: " + region.name());
            }
        }
    }

    public static LiveryTemplate prototypeCar512() {
        return new LiveryTemplate(512, 512, List.of(
            top("rear_chassis_top", 24, 24, 216, 128, -0.45, 0.45, -5.05, -2.95, face -> face.group().equals("Chassis") && face.centroidZ() < -2.95f),
            top("front_chassis_top", 272, 24, 216, 128, -0.50, 0.50, -2.95, -1.45, face -> face.group().equals("Chassis") && face.centroidZ() >= -2.95f),
            side("front_wing_left", 24, 184, 216, 56, -1.20, -0.47, -0.28, 0.05, face -> face.group().equals("Left-FW-Endplate")),
            side("front_wing_right", 272, 184, 216, 56, -1.20, -0.47, -0.28, 0.05, face -> face.group().equals("Right-FW-Endplate")),
            side("front_wheel_left", 24, 288, 96, 64, -1.78, -1.32, -0.24, 0.22, face -> face.group().equals("Wheel_Front_Left")),
            side("front_wheel_right", 144, 288, 96, 64, -1.78, -1.32, -0.24, 0.22, face -> face.group().equals("Wheel_Front_Right")),
            side("rear_wheel_left", 272, 288, 96, 72, -4.78, -4.26, -0.24, 0.24, face -> face.group().equals("Wheel_Rear_Left")),
            side("rear_wheel_right", 392, 288, 96, 72, -4.78, -4.26, -0.24, 0.24, face -> face.group().equals("Wheel_Rear_Right"))
        ));
    }

    private static LiveryTemplateRegion top(String name, int x, int y, int width, int height, double minX, double maxX, double minZ, double maxZ, Predicate<LiveryFace> faceFilter) {
        return new LiveryTemplateRegion(name, x, y, width, height, LiveryProjection.TOP, minX, maxX, minZ, maxZ, faceFilter);
    }

    private static LiveryTemplateRegion side(String name, int x, int y, int width, int height, double minZ, double maxZ, double minNegY, double maxNegY, Predicate<LiveryFace> faceFilter) {
        return new LiveryTemplateRegion(name, x, y, width, height, LiveryProjection.SIDE, minZ, maxZ, minNegY, maxNegY, faceFilter);
    }
}
