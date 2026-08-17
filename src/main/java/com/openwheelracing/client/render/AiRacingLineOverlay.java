package com.openwheelracing.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.openwheelracing.network.OWRNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class AiRacingLineOverlay {
    private static boolean visible;
    private static String dimensionId = "";
    private static List<OWRNetwork.AiRacingLineStrip> strips = List.of();

    private AiRacingLineOverlay() {
    }

    public static void apply(OWRNetwork.AiRacingLineOverlayMessage message) {
        visible = message.visible();
        dimensionId = message.dimensionId();
        strips = message.strips();
    }

    public static void clear() {
        visible = false;
        strips = List.of();
    }

    public static void render(RenderLevelStageEvent.AfterEntities event) {
        Minecraft mc = Minecraft.getInstance();
        if (!visible || mc.level == null || !mc.level.dimension().identifier().toString().equals(dimensionId)) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(RenderTypes.lines());
        PoseStack.Pose pose = event.getPoseStack().last();
        for (OWRNetwork.AiRacingLineStrip strip : strips) {
            double[] x = strip.x(), y = strip.y(), z = strip.z();
            int segmentCount = strip.closed() ? x.length : x.length - 1;
            for (int index = 0; index < segmentCount; index++) {
                int next = (index + 1) % x.length;
                line(consumer, pose, camera, strip.color(), x[index], y[index], z[index], x[next], y[next], z[next]);
            }
        }
    }

    private static void line(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, int color,
                             double x1, double y1, double z1, double x2, double y2, double z2) {
        float ax = (float) (x1 - camera.x), ay = (float) (y1 - camera.y + 0.18), az = (float) (z1 - camera.z);
        float bx = (float) (x2 - camera.x), by = (float) (y2 - camera.y + 0.18), bz = (float) (z2 - camera.z);
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.001f) return;
        consumer.addVertex(pose, ax, ay, az).setColor(color).setNormal(pose, dx / length, dy / length, dz / length).setLineWidth(4.0f);
        consumer.addVertex(pose, bx, by, bz).setColor(color).setNormal(pose, dx / length, dy / length, dz / length).setLineWidth(4.0f);
    }
}
