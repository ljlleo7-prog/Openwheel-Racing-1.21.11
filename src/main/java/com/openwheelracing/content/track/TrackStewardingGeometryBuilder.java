package com.openwheelracing.content.track;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TrackStewardingGeometryBuilder {
    private TrackStewardingGeometryBuilder() {
    }

    public static TrackDefinition fromCenterlinePath(UUID trackId, String name, String dimensionId, List<BlockPos> points, int surfaceWidth) {
        List<TrackDefinition.CenterlineNode> centerline = centerlineFromPath(points, surfaceWidth);
        Optional<TrackDefinition.StartFinishLine> startFinish = centerline.size() >= 2 ? Optional.of(startFinishFromFirstSegment(centerline)) : Optional.empty();
        List<TrackDefinition.AiWaypoint> aiLine = aiLineFromCenterline(centerline);
        return new TrackDefinition(trackId, name, dimensionId, centerline, startFinish, List.of(), List.of(), List.of(), List.of(), aiLine, List.of(), TrackDefinition.CURRENT_SCHEMA);
    }

    public static List<TrackDefinition.CenterlineNode> centerlineFromPath(List<BlockPos> points, int surfaceWidth) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        double halfWidth = Math.max(1.0, surfaceWidth * 0.5);
        List<TrackDefinition.CenterlineNode> nodes = new ArrayList<>();
        double distance = 0.0;
        for (int i = 0; i < points.size(); i++) {
            BlockPos point = points.get(i);
            TrackDefinition.Point3 position = TrackDefinition.Point3.centerOf(point);
            double heading = headingAt(points, i);
            if (i > 0) {
                distance += horizontalDistance(points.get(i - 1), point);
            }
            if (nodes.isEmpty() || horizontalDistance(nodes.get(nodes.size() - 1).position(), position) > 0.25) {
                nodes.add(new TrackDefinition.CenterlineNode(position, halfWidth, halfWidth, heading, distance));
            }
        }
        return List.copyOf(nodes);
    }

    public static List<TrackDefinition.AiWaypoint> aiLineFromCenterline(List<TrackDefinition.CenterlineNode> centerline) {
        List<TrackDefinition.AiWaypoint> waypoints = new ArrayList<>();
        for (int i = 0; i < centerline.size(); i++) {
            double targetSpeed = targetSpeedForCurvature(centerline, i);
            waypoints.add(new TrackDefinition.AiWaypoint(i, centerline.get(i).position(), targetSpeed, 0.0));
        }
        return List.copyOf(waypoints);
    }

    public static TrackDefinition.StartFinishLine startFinishFromFirstSegment(List<TrackDefinition.CenterlineNode> centerline) {
        TrackDefinition.CenterlineNode first = centerline.get(0);
        TrackDefinition.CenterlineNode second = centerline.get(1);
        double heading = Math.atan2(second.position().z() - first.position().z(), second.position().x() - first.position().x());
        double leftX = -Math.sin(heading) * first.widthLeft();
        double leftZ = Math.cos(heading) * first.widthLeft();
        double rightX = Math.sin(heading) * first.widthRight();
        double rightZ = -Math.cos(heading) * first.widthRight();
        TrackDefinition.Point3 center = first.position();
        TrackDefinition.Point3 left = new TrackDefinition.Point3(center.x() + leftX, center.y(), center.z() + leftZ);
        TrackDefinition.Point3 right = new TrackDefinition.Point3(center.x() + rightX, center.y(), center.z() + rightZ);
        return new TrackDefinition.StartFinishLine(left, right, heading);
    }

    private static double headingAt(List<BlockPos> points, int index) {
        if (points.size() == 1) {
            return 0.0;
        }
        BlockPos start = points.get(Math.max(0, index - 1));
        BlockPos end = points.get(Math.min(points.size() - 1, index + 1));
        return Math.atan2(end.getZ() - start.getZ(), end.getX() - start.getX());
    }

    private static double horizontalDistance(BlockPos start, BlockPos end) {
        double dx = end.getX() - start.getX();
        double dz = end.getZ() - start.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double horizontalDistance(TrackDefinition.Point3 start, TrackDefinition.Point3 end) {
        double dx = end.x() - start.x();
        double dz = end.z() - start.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double targetSpeedForCurvature(List<TrackDefinition.CenterlineNode> centerline, int index) {
        if (index <= 0 || index >= centerline.size() - 1) {
            return 110.0;
        }
        double previous = centerline.get(index - 1).headingRadians();
        double next = centerline.get(index + 1).headingRadians();
        double change = Math.abs(TrackGeometry.wrapRadians(next - previous));
        return Math.max(45.0, 150.0 - Math.toDegrees(change) * 1.6);
    }
}
