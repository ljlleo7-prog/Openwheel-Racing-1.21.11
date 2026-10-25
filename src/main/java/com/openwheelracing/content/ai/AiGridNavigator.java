package com.openwheelracing.content.ai;

import java.util.List;

/** Searches and follows a local paved route to the slot even when survey localization is unavailable. */
public final class AiGridNavigator {
    public record Guidance(boolean searching, boolean blocked, double speed, float steering, int consumedBudget) { }
    private AiGridPathSearch search;
    private AiGridApproach.Target target;
    private List<AiGridPathSearch.Pose> path = List.of();
    private int waypoint;
    private long plannedAt;

    public Guidance update(AiGridApproach.Target goal, double x, double y, double z, double heading, double speed,
                           long tick, int budget, AiGridPathSearch.Road road) {
        var pose = new AiGridPathSearch.Pose(x, z, heading);
        var finalApproach = AiGridApproach.evaluate(goal, x, y, z, heading, speed);
        if (finalApproach.ready()) return new Guidance(false, false, 0.0, 0.0f, 0);
        if (!goal.equals(target) || search == null || search.done() && path.isEmpty() && tick - plannedAt >= 100L) {
            target = goal;
            search = new AiGridPathSearch(pose, goal, road);
            path = List.of();
            waypoint = 1;
            plannedAt = tick;
        }
        search.updateRoad(road);
        int used = search.advance(Math.max(0, budget));
        if (!search.done()) return new Guidance(true, false, 0.0, 0.0f, used);
        if (path.isEmpty()) path = search.path();
        if (path.isEmpty()) return new Guidance(false, true, 0.0, 0.0f, used);
        double forward = (goal.x() - x) * Math.cos(goal.heading()) + (goal.z() - z) * Math.sin(goal.heading());
        if (finalApproach.close() && forward >= 0.0 && forward < 8.0
            && Math.abs(wrap(goal.heading() - heading)) < Math.toRadians(24)) {
            return new Guidance(false, false, finalApproach.targetSpeed(), finalApproach.steering(), used);
        }
        while (waypoint < path.size() - 1 && Math.hypot(path.get(waypoint).x() - x, path.get(waypoint).z() - z) < 3.5) waypoint++;
        var next = path.get(Math.min(waypoint, path.size() - 1));
        double distance = Math.hypot(next.x() - x, next.z() - z);
        double error = wrap(Math.atan2(next.z() - z, next.x() - x) - heading);
        if (distance > 12.0 || Math.abs(error) > Math.toRadians(100)) {
            search = null; // Recovery is another bounded forward search, never a permanent missed-box latch.
            path = List.of();
            return new Guidance(true, false, 0.0, 0.0f, used);
        }
        if (!road.clear(next.x(), next.z(), next.heading())) {
            search = null;
            path = List.of();
            return new Guidance(true, false, 0.0, 0.0f, used);
        }
        float steering = (float) Math.clamp(Math.atan2(2.0 * 3.6 * Math.sin(error), Math.max(3.0, distance)) / Math.toRadians(34), -0.70, 0.70);
        double targetSpeed = Math.min(8.0, 4.0 / Math.max(0.5, Math.abs(error) * 4.0));
        return new Guidance(false, false, targetSpeed, steering, used);
    }

    private static double wrap(double angle) { return Math.atan2(Math.sin(angle), Math.cos(angle)); }
}
