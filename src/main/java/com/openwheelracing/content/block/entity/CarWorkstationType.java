package com.openwheelracing.content.block.entity;

public enum CarWorkstationType {
    CONSTRUCTION("car_construction_station", "container.openwheelracing.car_construction_station", 160),
    SETUP("car_setup_station", "container.openwheelracing.car_setup_station", 80),
    LIVERY("car_livery_station", "container.openwheelracing.car_livery_station", 60),
    LEGACY("car_assembly_workstation", "container.openwheelracing.car_assembly_workstation", 100);

    private final String id;
    private final String containerKey;
    private final int progressTicks;

    CarWorkstationType(String id, String containerKey, int progressTicks) {
        this.id = id;
        this.containerKey = containerKey;
        this.progressTicks = progressTicks;
    }

    public String id() {
        return id;
    }

    public String containerKey() {
        return containerKey;
    }

    public int progressTicks() {
        return progressTicks;
    }

    public boolean allowsConstruction() {
        return this == CONSTRUCTION || this == LEGACY;
    }

    public boolean allowsSetup() {
        return this == SETUP || this == LEGACY;
    }

    public boolean allowsLivery() {
        return this == LIVERY || this == LEGACY;
    }
}
