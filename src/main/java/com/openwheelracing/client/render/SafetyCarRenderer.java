package com.openwheelracing.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.openwheelracing.content.entity.SafetyCarEntity;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

public class SafetyCarRenderer extends EntityRenderer<SafetyCarEntity, SafetyCarRenderer.SafetyCarRenderState> {
    private static final Identifier SAFETY_CAR_OBJ = Identifier.fromNamespaceAndPath("openwheelracing", "objmodels/amg-sc.obj");
    private static final Identifier WHITE_TEX = Identifier.fromNamespaceAndPath("openwheelracing", "textures/entity/car_white.png");
    private static final RenderType RT_CAR = RenderTypes.entityCutoutNoCull(WHITE_TEX);
    private static final RenderType RT_GLASS = RenderTypes.entityTranslucent(WHITE_TEX);

    private static final int RED = 0xFFE02020;
    private static final int WHITE = 0xFFF2F2F2;
    private static final int BLACK = 0xFF07080A;
    private static final int DARK_TRIM = 0xFF171A20;
    private static final int GLASS = 0x3338A8FF;

    private static final float SOURCE_MIN_X = -105.744179f;
    private static final float SOURCE_MAX_X = 105.655052f;
    private static final float SOURCE_MIN_Y = -13.990366f;
    private static final float SOURCE_MIN_Z = -234.306274f;
    private static final float SOURCE_MAX_Z = 225.613449f;
    private static final float TARGET_WIDTH = 2.05f;
    private static final float UNIFORM_RENDER_SCALE = TARGET_WIDTH / (SOURCE_MAX_X - SOURCE_MIN_X);
    private static final float MODEL_Z_CENTER = (SOURCE_MIN_Z + SOURCE_MAX_Z) * 0.5f;

    private static ColoredObjModel model;
    private static int[] bakedColors;

    public SafetyCarRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 1.35f;
    }

    @Override
    public SafetyCarRenderState createRenderState() {
        return new SafetyCarRenderState();
    }

    @Override
    public void extractRenderState(SafetyCarEntity car, SafetyCarRenderState state, float partialTick) {
        super.extractRenderState(car, state, partialTick);
        state.yRot = car.getYRot(partialTick);
        state.lightCoords = 15728880;
    }

    @Override
    public void submit(SafetyCarRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        super.submit(state, poseStack, nodeCollector, cameraState);
        loadModel();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot));
        int light = state.lightCoords;
        nodeCollector.submitCustomGeometry(poseStack, RT_CAR, (pose, consumer) -> drawModel(consumer, pose, model, bakedColors, light, false));
        nodeCollector.submitCustomGeometry(poseStack, RT_GLASS, (pose, consumer) -> drawModel(consumer, pose, model, bakedColors, light, true));
        poseStack.popPose();
    }

    private static void loadModel() {
        if (model == null) {
            model = ColoredObjModel.load(Minecraft.getInstance().getResourceManager(), SAFETY_CAR_OBJ, false);
            bakedColors = model.bakeColors(SafetyCarRenderer::safetyCarColor);
        }
    }

    private static void drawModel(VertexConsumer consumer, PoseStack.Pose pose, ColoredObjModel model, int[] colors, int light, boolean glassPass) {
        List<ColoredObjModel.Face> faces = model.faces;
        for (int i = 0; i < faces.size(); i++) {
            ColoredObjModel.Face face = faces.get(i);
            int color = colors[i];
            if (isGlassColor(color) != glassPass) {
                continue;
            }
            vertex(consumer, pose, face.x0(), face.y0(), face.z0(), face.nx(), face.ny(), face.nz(), color, light);
            vertex(consumer, pose, face.x1(), face.y1(), face.z1(), face.nx(), face.ny(), face.nz(), color, light);
            vertex(consumer, pose, face.x2(), face.y2(), face.z2(), face.nx(), face.ny(), face.nz(), color, light);
            vertex(consumer, pose, face.x3(), face.y3(), face.z3(), face.nx(), face.ny(), face.nz(), color, light);
        }
    }

    private static int safetyCarColor(ColoredObjModel.Face face) {
        int rgb = face.materialRgb() & 0x00FFFFFF;
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        if (isGlassMaterial(r, g, b)) {
            return GLASS;
        }
        if (isBlackMaterial(r, g, b)) {
            return BLACK;
        }
        if (isWhiteMaterial(r, g, b)) {
            return WHITE;
        }
        if (isTrimMaterial(r, g, b)) {
            return DARK_TRIM;
        }
        return redWhitePattern(face) ? RED : WHITE;
    }

    private static boolean isGlassColor(int color) {
        return (color >>> 24) < 255;
    }

    private static boolean isGlassMaterial(int r, int g, int b) {
        return r > 185 && g > 120 && b < 100;
    }

    private static boolean isBlackMaterial(int r, int g, int b) {
        return r < 35 && g < 35 && b < 45;
    }

    private static boolean isWhiteMaterial(int r, int g, int b) {
        return r > 230 && g > 230 && b > 220;
    }

    private static boolean isTrimMaterial(int r, int g, int b) {
        return Math.abs(r - g) < 12 && Math.abs(g - b) < 12 && r < 185;
    }

    private static boolean redWhitePattern(ColoredObjModel.Face face) {
        float x = (face.x0() + face.x1() + face.x2()) / 3.0f;
        float z = (face.z0() + face.z1() + face.z2()) / 3.0f;
        return Math.abs(x) > 38.0f || z > 95.0f || z < -145.0f;
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float normalX, float normalY, float normalZ, int color, int light) {
        consumer.addVertex(pose, x * UNIFORM_RENDER_SCALE, (y - SOURCE_MIN_Y) * UNIFORM_RENDER_SCALE, (z - MODEL_Z_CENTER) * UNIFORM_RENDER_SCALE)
            .setColor(color)
            .setUv(0.0f, 0.0f)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, normalX, normalY, normalZ);
    }

    public static class SafetyCarRenderState extends EntityRenderState {
        public float yRot;
        public int lightCoords;
    }
}
