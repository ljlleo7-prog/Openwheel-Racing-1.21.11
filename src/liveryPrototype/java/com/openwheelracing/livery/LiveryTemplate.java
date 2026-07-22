package com.openwheelracing.livery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        }
    }

    public static LiveryTemplate prototypeCar512() {
        return new LiveryTemplate(512, 512, List.of(
            new LiveryTemplateRegion("nose_top", 24, 24, 144, 92, LiveryColorChannel.BODY),
            new LiveryTemplateRegion("engine_cover", 184, 24, 160, 92, LiveryColorChannel.BODY),
            new LiveryTemplateRegion("halo_top", 360, 24, 128, 92, LiveryColorChannel.ACCENT_1),
            new LiveryTemplateRegion("left_sidepod", 24, 148, 216, 108, LiveryColorChannel.BODY),
            new LiveryTemplateRegion("right_sidepod", 272, 148, 216, 108, LiveryColorChannel.BODY),
            new LiveryTemplateRegion("front_wing_left", 24, 288, 216, 48, LiveryColorChannel.ACCENT_1),
            new LiveryTemplateRegion("front_wing_right", 272, 288, 216, 48, LiveryColorChannel.ACCENT_1),
            new LiveryTemplateRegion("rear_wing_left", 24, 368, 216, 56, LiveryColorChannel.ACCENT_2),
            new LiveryTemplateRegion("rear_wing_right", 272, 368, 216, 56, LiveryColorChannel.ACCENT_2),
            new LiveryTemplateRegion("number_panel", 184, 448, 144, 40, LiveryColorChannel.ACCENT_2)
        ));
    }
}
