package com.openwheelracing.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.content.track.survey.SurveyRoute;
import com.openwheelracing.content.track.survey.SurveyRouteLocalizer;
import com.openwheelracing.content.track.survey.SurveyRouteModel;
import com.openwheelracing.network.OWRNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;
import java.util.UUID;

public final class SurveyRouteOverlay {
    private static boolean visible;
    private static String dimensionId = "";
    private static UUID trackId = new UUID(0, 0);
    private static String trackName = "";
    private static boolean recording;
    private static List<SurveyRoute.Sample> rawSamples = List.of();
    private static List<SurveyRoute.Node> nodes = List.of();
    private static double length;
    private static double spacing;
    private static final SurveyRouteLocalizer.State LOCALIZER_STATE = new SurveyRouteLocalizer.State();
    private static SurveyRouteLocalizer.Result localization;

    private SurveyRouteOverlay() {}

    public static void apply(OWRNetwork.SurveyRouteOverlayMessage message) {
        if (!message.visible()) {
            clear();
            return;
        }
        visible = true;
        dimensionId = message.dimensionId();
        trackId = message.trackId();
        trackName = message.trackName();
        recording = message.recording();
        rawSamples = List.copyOf(message.rawSamples());
        nodes = List.copyOf(message.nodes());
        length = message.length();
        spacing = message.spacing();
        LOCALIZER_STATE.reset();
        localization = null;
    }

    public static void clear() {
        visible = false;
        dimensionId = "";
        trackId = new UUID(0, 0);
        trackName = "";
        recording = false;
        rawSamples = List.of();
        nodes = List.of();
        length = 0.0;
        spacing = 0.0;
        LOCALIZER_STATE.reset();
        localization = null;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!visible || mc.level == null || !mc.level.dimension().identifier().toString().equals(dimensionId)) {
            if (visible && mc.level != null) clear();
            return;
        }
        if (nodes.size() < 2 || mc.player == null || !(mc.player.getVehicle() instanceof OpenwheelCarEntity car)) {
            localization = null;
            return;
        }
        SurveyRouteModel route = model();
        localization = SurveyRouteLocalizer.locate(route, new SurveyRouteModel.Point(car.getX(), car.getY(), car.getZ()), Math.toRadians(car.getYRot() + 90.0F), LOCALIZER_STATE);
    }

    public static void render(RenderLevelStageEvent.AfterEntities event) {
        if (!visible || Minecraft.getInstance().level == null || rawSamples.isEmpty() && nodes.isEmpty()) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        if (camera == null) return;
        VertexConsumer consumer = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderTypes.lines());
        PoseStack.Pose pose = event.getPoseStack().last();
        drawRaw(consumer, pose, camera);
        drawProcessed(consumer, pose, camera);
        drawLocalization(consumer, pose, camera);
    }

    private static void drawRaw(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera) {
        for (int i = 1; i < rawSamples.size(); i++) drawLine(consumer, pose, camera, rawSamples.get(i - 1).position(), rawSamples.get(i).position(), 0x99B8C2CC, 0.12);
        for (int i = 0; i < rawSamples.size(); i += 20) drawCross(consumer, pose, camera, rawSamples.get(i).position(), 0.3, 0xFFB8C2CC, 0.15);
    }

    private static void drawProcessed(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera) {
        if (nodes.size() < 2) return;
        for (int i = 0; i < nodes.size(); i++) {
            SurveyRoute.Node start = nodes.get(i);
            SurveyRoute.Node end = nodes.get((i + 1) % nodes.size());
            drawLine(consumer, pose, camera, start.position(), end.position(), 0xFF00E5A8, 0.22);
            int roundedDistance = (int) Math.round(start.distanceAlongRoute());
            if (roundedDistance % 100 < Math.max(1, Math.round(spacing))) drawPerpendicular(consumer, pose, camera, start, 3.0, 0xFFFFFF66);
            else if (roundedDistance % 20 < Math.max(1, Math.round(spacing))) drawPerpendicular(consumer, pose, camera, start, 1.5, 0xFF79C0FF);
        }
        drawCross(consumer, pose, camera, nodes.getFirst().position(), 1.0, 0xFFFF66FF, 0.35);
    }

    private static void drawLocalization(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera) {
        if (localization == null || Minecraft.getInstance().player == null || !(Minecraft.getInstance().player.getVehicle() instanceof OpenwheelCarEntity car)) return;
        int color = switch (localization.status()) {
            case TRACKED -> 0xFF34D058;
            case LOW_CONFIDENCE -> 0xFFFFD044;
            case AMBIGUOUS -> 0xFFD65CFF;
            case UNTRACKED -> 0xFFDA1A20;
        };
        localization.best().ifPresent(candidate -> {
            SurveyRoute.Point carPoint = new SurveyRoute.Point(car.getX(), car.getY() + 0.4, car.getZ());
            SurveyRoute.Point projected = point(candidate.projectedPosition());
            drawLine(consumer, pose, camera, carPoint, projected, color, 0.4);
            drawCross(consumer, pose, camera, projected, 0.7, color, 0.4);
            double dx = Math.cos(nodes.get(candidate.segmentIndex()).headingRadians()) * 2.0;
            double dz = Math.sin(nodes.get(candidate.segmentIndex()).headingRadians()) * 2.0;
            drawLine(consumer, pose, camera, new SurveyRoute.Point(projected.x() - dx, projected.y(), projected.z() - dz), new SurveyRoute.Point(projected.x() + dx, projected.y(), projected.z() + dz), color, 0.4);
        });
        localization.second().ifPresent(candidate -> drawCross(consumer, pose, camera, point(candidate.projectedPosition()), 0.8, 0xFF9B59FF, 0.45));
    }

    private static void drawPerpendicular(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, SurveyRoute.Node node, double width, int color) {
        double sideX = -Math.sin(node.headingRadians()) * width * 0.5;
        double sideZ = Math.cos(node.headingRadians()) * width * 0.5;
        SurveyRoute.Point p = node.position();
        drawLine(consumer, pose, camera, new SurveyRoute.Point(p.x() - sideX, p.y(), p.z() - sideZ), new SurveyRoute.Point(p.x() + sideX, p.y(), p.z() + sideZ), color, 0.3);
    }

    private static void drawCross(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, SurveyRoute.Point point, double radius, int color, double yOffset) {
        drawLine(consumer, pose, camera, new SurveyRoute.Point(point.x() - radius, point.y(), point.z()), new SurveyRoute.Point(point.x() + radius, point.y(), point.z()), color, yOffset);
        drawLine(consumer, pose, camera, new SurveyRoute.Point(point.x(), point.y(), point.z() - radius), new SurveyRoute.Point(point.x(), point.y(), point.z() + radius), color, yOffset);
    }

    private static void drawLine(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, SurveyRoute.Point start, SurveyRoute.Point end, int color, double yOffset) {
        float x1 = (float) (start.x() - camera.x), y1 = (float) (start.y() - camera.y + yOffset), z1 = (float) (start.z() - camera.z);
        float x2 = (float) (end.x() - camera.x), y2 = (float) (end.y() - camera.y + yOffset), z2 = (float) (end.z() - camera.z);
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float magnitude = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (magnitude < 0.001f) return;
        addVertex(consumer, pose, x1, y1, z1, color, dx / magnitude, dy / magnitude, dz / magnitude);
        addVertex(consumer, pose, x2, y2, z2, color, dx / magnitude, dy / magnitude, dz / magnitude);
    }

    private static void addVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, int color, float nx, float ny, float nz) {
        consumer.addVertex(pose, x, y, z).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(3.0f);
    }

    private static SurveyRouteModel model() {
        return new SurveyRouteModel(UUID.randomUUID(), trackId, rawSamples.stream().map(sample -> new SurveyRouteModel.Sample(modelPoint(sample.position()), sample.headingRadians())).toList(),
            nodes.stream().map(node -> new SurveyRouteModel.Node(node.index(), modelPoint(node.position()), node.headingRadians(), node.distanceAlongRoute())).toList(), length, spacing);
    }

    private static SurveyRouteModel.Point modelPoint(SurveyRoute.Point point) { return new SurveyRouteModel.Point(point.x(), point.y(), point.z()); }
    private static SurveyRoute.Point point(SurveyRouteModel.Point point) { return new SurveyRoute.Point(point.x(), point.y(), point.z()); }

    public static boolean visible() { return visible; }
    public static boolean recording() { return recording; }
    public static String trackName() { return trackName; }
    public static int rawCount() { return rawSamples.size(); }
    public static int nodeCount() { return nodes.size(); }
    public static double length() { return length; }
    public static SurveyRouteLocalizer.Result localization() { return localization; }
}
