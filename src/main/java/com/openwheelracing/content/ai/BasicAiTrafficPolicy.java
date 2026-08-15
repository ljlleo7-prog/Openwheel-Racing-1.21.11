package com.openwheelracing.content.ai;

public final class BasicAiTrafficPolicy {
    public static final double MIN_LEADER_SPEED_KMH = 10.0;
    public static final double MIN_FORWARD_SPEED_MPS = 1.0;
    private static final double MAX_LATERAL_RATIO = 0.5;

    private BasicAiTrafficPolicy() {
    }

    public static boolean healthyMovingAi(boolean aiOwned, boolean autonomous, boolean passengerControlled,
                                          double speedKmh, double velocityLong, double velocityLat) {
        if (!aiOwned || !autonomous || passengerControlled || speedKmh < MIN_LEADER_SPEED_KMH || velocityLong < MIN_FORWARD_SPEED_MPS) {
            return false;
        }
        return Math.abs(velocityLat) <= Math.max(1.0, velocityLong * MAX_LATERAL_RATIO);
    }

    public static boolean shouldEscapeAiObstacle(boolean initiatorHealthy, boolean targetAiOwned,
                                                  boolean targetPassengerControlled, boolean targetHealthy) {
        return initiatorHealthy && targetAiOwned && !targetPassengerControlled && !targetHealthy;
    }
}
