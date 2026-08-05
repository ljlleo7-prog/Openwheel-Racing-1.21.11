package com.openwheelracing.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.network.OWRNetwork;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class StewardLineOverlay {
    private static final double Y_OFFSET = 0.0;

    private static boolean visible;
    private static UUID trackId = new UUID(0L, 0L);
    private static String trackName = "";
    private static int revision;
    private static List<TrackDefinition.StewardLine> lines = List.of();

    private StewardLineOverlay() {
    }

    public static void apply(OWRNetwork.StewardLineOverlayMessage message) {
        visible = message.visible();
        if (!visible) {
            clear();
            return;
        }
        trackId = message.trackId();
        trackName = message.trackName();
        revision = message.revision();
        lines = List.copyOf(message.lines());
    }

    public static void clear() {
        visible = false;
        trackId = new UUID(0L, 0L);
        trackName = "";
        revision = 0;
        lines = List.of();
    }

    public static void render(RenderLevelStageEvent.AfterEntities event) {
        if (!visible || lines.isEmpty() || Minecraft.getInstance().level == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        if (camera == null) {
            return;
        }
        VertexConsumer consumer = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderTypes.lines());
        PoseStack poseStack = event.getPoseStack();
        for (TrackDefinition.StewardLine line : lines) {
            drawLine(consumer, poseStack.last(), camera, line, color(line.type()));
        }
    }

    public static boolean visible() {
        return visible;
    }

    public static UUID trackId() {
        return trackId;
    }

    public static String trackName() {
        return trackName;
    }

    public static int revision() {
        return revision;
    }

    private static void drawLine(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, TrackDefinition.StewardLine line, int color) {
        double x1 = line.left().x() - camera.x;
        double y1 = line.left().y() - camera.y + Y_OFFSET;
        double z1 = line.left().z() - camera.z;
        double x2 = line.right().x() - camera.x;
        double y2 = line.right().y() - camera.y + Y_OFFSET;
        double z2 = line.right().z() - camera.z;
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.001f) {
            return;
        }
        float nx = dx / length;
        float ny = dy / length;
        float nz = dz / length;
        addLineVertex(consumer, pose, (float) x1, (float) y1, (float) z1, color, nx, ny, nz);
        addLineVertex(consumer, pose, (float) x2, (float) y2, (float) z2, color, nx, ny, nz);
    }

    private static void addLineVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, int color, float normalX, float normalY, float normalZ) {
        consumer.addVertex(pose, x, y, z)
            .setColor(color)
            .setNormal(pose, normalX, normalY, normalZ)
            .setLineWidth(4.0f);
    }

    private static int color(TrackDefinition.StewardLineType type) {
        return switch (type) {
            case CHECKPOINT -> 0xFF00FFFF;
            case SECTOR_SPLIT -> 0xFFFFFF00;
            case PIT_LIMIT_START -> 0xFFFF8C00;
            case PIT_LIMIT_END -> 0xFFFF0000;
            case SAFETY_CAR_LINE -> 0xFF2A7FFF;
            case DRS_DETECTION -> 0xFFFF00FF;
            case DRS_ACTIVATION -> 0xFF00FF00;
        };
    }
}
