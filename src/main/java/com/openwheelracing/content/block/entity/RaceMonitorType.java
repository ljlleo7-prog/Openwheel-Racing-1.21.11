package com.openwheelracing.content.block.entity;

public enum RaceMonitorType {
    DIRECTOR("race_director", "container.openwheelracing.race_director"),
    BOARD("race_board_terminal", "container.openwheelracing.race_board_terminal"),
    TEAM("team_terminal", "container.openwheelracing.team_terminal");

    private final String id;
    private final String containerKey;

    RaceMonitorType(String id, String containerKey) {
        this.id = id;
        this.containerKey = containerKey;
    }

    public String id() {
        return id;
    }

    public String containerKey() {
        return containerKey;
    }

    public boolean isRaceControl() {
        return this == DIRECTOR || this == BOARD;
    }
}
