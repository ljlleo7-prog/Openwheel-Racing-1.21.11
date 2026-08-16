package com.openwheelracing.content.ai;

import com.openwheelracing.content.race.RaceFlagMode;

import java.util.Locale;
import java.util.Optional;

public enum BasicAiTrafficMode {
    AUTO(0.92, Double.POSITIVE_INFINITY, false),
    RACE(0.92, Double.POSITIVE_INFINITY, false),
    FORMATION(0.62, 35.0, true),
    VSC(0.68, 55.0, true),
    SAFETY_CAR(0.58, 35.0, true),
    HOLD(0.0, 0.0, true);

    private final double gripUtilization;
    private final double speedCapMetersPerSecond;
    private final boolean queueing;

    BasicAiTrafficMode(double gripUtilization, double speedCapMetersPerSecond, boolean queueing) {
        this.gripUtilization = gripUtilization;
        this.speedCapMetersPerSecond = speedCapMetersPerSecond;
        this.queueing = queueing;
    }

    public double gripUtilization() {
        return gripUtilization;
    }

    public double speedCapMetersPerSecond() {
        return speedCapMetersPerSecond;
    }

    public boolean queueing() {
        return queueing;
    }

    public static BasicAiTrafficMode resolve(BasicAiTrafficMode override, RaceFlagMode flag) {
        if (override != null && override != AUTO) {
            return override;
        }
        return switch (flag == null ? RaceFlagMode.DEFAULT : flag) {
            case GREEN -> RACE;
            case YELLOW -> FORMATION;
            case VIRTUAL_SAFETY_CAR -> VSC;
            case SAFETY_CAR -> SAFETY_CAR;
            case RED -> HOLD;
        };
    }

    public static Optional<BasicAiTrafficMode> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
