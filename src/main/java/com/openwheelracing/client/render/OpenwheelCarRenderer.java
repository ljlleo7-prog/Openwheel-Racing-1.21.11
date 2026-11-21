package com.openwheelracing.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.openwheelracing.client.livery.ClientLiveryTextures;
import com.openwheelracing.client.livery.LiveryUvMapping;
import com.openwheelracing.content.car.CarLiveryColors;
import com.openwheelracing.content.car.CarLiveryTexture;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
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

public class OpenwheelCarRenderer extends EntityRenderer<OpenwheelCarEntity, OpenwheelCarRenderer.CarRenderState> {
    private static final Identifier CAR_OBJ = Identifier.fromNamespaceAndPath("openwheelracing", "objmodels/f1_car_2026.obj");
    private static final Identifier WHITE_TEX = Identifier.fromNamespaceAndPath("openwheelracing", "textures/entity/car_white.png");
    private static final RenderType RT_CAR = RenderTypes.entityCutoutNoCull(WHITE_TEX);

    private static final float SOURCE_MIN_X = -0.806891f;
    private static final float SOURCE_MAX_X = 0.806891f;
    private static final float SOURCE_MIN_Y = -0.341034f;
    private static final float SOURCE_MAX_Y = 0.639583f;
    private static final float SOURCE_MIN_Z = -5.237548f;
    private static final float SOURCE_MAX_Z = -0.467113f;
    private static final float TARGET_WIDTH = 1.9f;
    private static final float UNIFORM_RENDER_SCALE = TARGET_WIDTH / (SOURCE_MAX_X - SOURCE_MIN_X);
    private static final float MODEL_X_SCALE = UNIFORM_RENDER_SCALE;
    private static final float MODEL_Y_SCALE = UNIFORM_RENDER_SCALE;
    private static final float MODEL_Z_SCALE = UNIFORM_RENDER_SCALE;
    private static final float MODEL_Z_CENTER = (SOURCE_MIN_Z + SOURCE_MAX_Z) * 0.5f;
    private static final float FRONT_WHEEL_LEFT_PIVOT_X = -0.647447f;
    private static final float FRONT_WHEEL_RIGHT_PIVOT_X = 0.647447f;
    private static final float FRONT_WHEEL_PIVOT_Z = -1.556256f;

    private static final int CARBON = rgb(34, 38, 48);
    private static final int TYRE = rgb(42, 46, 54);
    private static final int METAL = rgb(155, 155, 150);

    private static ColoredObjModel carModel;
    private static float[] frontWheelPivots;
    private static float[][] pictureUvs;
    // Pre-baked color array per livery index — populated once per livery, reused every frame.
    private static final java.util.HashMap<String, int[]> BAKED_COLORS = new java.util.HashMap<>();

    public static void invalidateLiveryCache(String textureId) {
        BAKED_COLORS.keySet().removeIf(key -> key.endsWith(":" + textureId));
    }

    public OpenwheelCarRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 1.2f;
    }

    @Override
    public CarRenderState createRenderState() {
        return new CarRenderState();
    }

    @Override
    public void extractRenderState(OpenwheelCarEntity car, CarRenderState state, float partialTick) {
        super.extractRenderState(car, state, partialTick);
        Minecraft minecraft = Minecraft.getInstance();
        boolean localCar = minecraft.player != null && minecraft.player.getVehicle() == car;
        state.yRot = RemoteCarRenderPose.apply(car, state, partialTick, localCar);
        state.frontWheelSteerDegrees = car.getFrontWheelSteerDegrees();
        state.lightCoords = 15728880;
        state.tyreCompound = car.getTyreCompound();
        state.tyresFitted = car.hasTyres();
        state.liveryColors = car.getLiveryColors();
        state.liveryTexture = car.getLiveryTexture();
        state.jackLift = car.getJackLift(partialTick);
        state.bumpPitch = car.getTerrainBumpPitch(partialTick);
    }

    @Override
    public void submit(CarRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        super.submit(state, poseStack, nodeCollector, cameraState);
        loadModel();

        NativeImage liveryImage = state.liveryTexture.isPresent() ? ClientLiveryTextures.loadExisting(Minecraft.getInstance(), state.liveryTexture) : null;
        String liveryColorKey = liveryColorKey(state.liveryColors, state.liveryTexture);
        int[] bakedColors = BAKED_COLORS.computeIfAbsent(liveryColorKey, ignored -> carModel.bakeColors(face -> state.liveryTexture.isPicture() && liveryImage != null && isLiveryPaintable(face)
            ? 0xFFFFFFFF : liveryColor(face, state.liveryColors, state.liveryTexture.isPicture() ? null : liveryImage)));

        poseStack.pushPose();
        poseStack.translate(0.0f, state.jackLift, 0.0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot));
        // Rock around the chassis rather than its ground-level model origin.
        poseStack.translate(0.0f, 0.35f, 0.0f);
        poseStack.mulPose(Axis.XP.rotationDegrees(state.bumpPitch));
        poseStack.translate(0.0f, -0.35f, 0.0f);

        int light = state.lightCoords;
        boolean tyresFitted = state.tyresFitted;
        float frontWheelSteerDegrees = state.frontWheelSteerDegrees;
        boolean picture = state.liveryTexture.isPicture() && liveryImage != null;
        RenderType renderType = picture
            ? RenderTypes.entityCutoutNoCull(ClientLiveryTextures.textureLocation(state.liveryTexture).orElse(WHITE_TEX)) : RT_CAR;
        nodeCollector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> drawModel(consumer, pose, carModel, bakedColors, light, frontWheelSteerDegrees, tyresFitted, picture));

        poseStack.popPose();
    }

    private static void loadModel() {
        if (carModel == null) {
            carModel = ColoredObjModel.load(Minecraft.getInstance().getResourceManager(), CAR_OBJ);
            frontWheelPivots = bakeFrontWheelPivots(carModel);
            pictureUvs = new float[carModel.faces.size()][];
            for (int i = 0; i < carModel.faces.size(); i++) {
                pictureUvs[i] = bakePictureUvs(carModel.faces.get(i));
            }
        }
    }

    /** Shared by the renderer and offline HTML exporter; no second UV implementation. */
    public static float[] bakePictureUvs(ColoredObjModel.Face face) {
        float[] uv = new float[8];
        if (isLiveryPaintable(face)) {
            float[] xyz = {face.x0(), face.y0(), face.z0(), face.x1(), face.y1(), face.z1(),
                face.x2(), face.y2(), face.z2(), face.x3(), face.y3(), face.z3()};
            for (int v = 0; v < 4; v++) {
                uv[v * 2] = (face.liveryPixelX(xyz[v * 3], xyz[v * 3 + 1], xyz[v * 3 + 2]) + 0.5f) / ClientLiveryTextures.SIZE;
                uv[v * 2 + 1] = (face.liveryPixelY(xyz[v * 3], xyz[v * 3 + 1], xyz[v * 3 + 2]) + 0.5f) / ClientLiveryTextures.SIZE;
            }
        } else {
            java.util.Arrays.fill(uv, 0.5f / ClientLiveryTextures.SIZE);
        }
        return uv;
    }

    public static float modelX(float x) { return x * MODEL_X_SCALE; }
    public static float modelY(float y) { return (y - SOURCE_MIN_Y) * MODEL_Y_SCALE; }
    public static float modelZ(float z) { return (z - MODEL_Z_CENTER) * MODEL_Z_SCALE; }

    public static int[] legacySamplePixel(ColoredObjModel.Face face) {
        float x = (face.x0() + face.x1() + face.x2()) / 3.0f;
        float y = (face.y0() + face.y1() + face.y2()) / 3.0f;
        float z = (face.z0() + face.z1() + face.z2()) / 3.0f;
        int u = Math.max(0, Math.min(ClientLiveryTextures.SIZE - 1, Math.round(face.liveryPixelX(x, y, z))));
        int v = Math.max(0, Math.min(ClientLiveryTextures.SIZE - 1, Math.round(face.liveryPixelY(x, y, z))));
        return new int[] {u, v};
    }

    private static float[] bakeFrontWheelPivots(ColoredObjModel model) {
        float[] pivots = new float[model.faces.size()];
        List<ColoredObjModel.Face> faces = model.faces;
        for (int i = 0; i < faces.size(); i++) {
            String group = faces.get(i).group();
            pivots[i] = group.equals("Wheel_Front_Left") ? FRONT_WHEEL_LEFT_PIVOT_X : group.equals("Wheel_Front_Right") ? FRONT_WHEEL_RIGHT_PIVOT_X : Float.NaN;
        }
        return pivots;
    }

    private static void drawModel(VertexConsumer consumer, PoseStack.Pose pose, ColoredObjModel model, int[] bakedColors, int light, float frontWheelSteerDegrees, boolean tyresFitted, boolean picture) {
        float steerRadians = (float) Math.toRadians(-frontWheelSteerDegrees);
        float steerSin = (float) Math.sin(steerRadians);
        float steerCos = (float) Math.cos(steerRadians);
        List<ColoredObjModel.Face> faces = model.faces;
        for (int i = 0; i < faces.size(); i++) {
            ColoredObjModel.Face face = faces.get(i);
            if (!tyresFitted && face.group().startsWith("Wheel_")) continue;
            int color = bakedColors[i];
            float pivotX = frontWheelPivots[i];
            float[] uv = pictureUvs[i];
            if (Float.isNaN(pivotX)) {
                vertex(consumer, pose, face.x0(), face.y0(), face.z0(), face.nx(), face.ny(), face.nz(), color, light, picture ? uv[0] : 0.0f, picture ? uv[1] : 0.0f);
                vertex(consumer, pose, face.x1(), face.y1(), face.z1(), face.nx(), face.ny(), face.nz(), color, light, picture ? uv[2] : 0.0f, picture ? uv[3] : 0.0f);
                vertex(consumer, pose, face.x2(), face.y2(), face.z2(), face.nx(), face.ny(), face.nz(), color, light, picture ? uv[4] : 0.0f, picture ? uv[5] : 0.0f);
                vertex(consumer, pose, face.x3(), face.y3(), face.z3(), face.nx(), face.ny(), face.nz(), color, light, picture ? uv[6] : 0.0f, picture ? uv[7] : 0.0f);
            } else {
                steeredVertex(consumer, pose, face.x0(), face.y0(), face.z0(), face.nx(), face.ny(), face.nz(), color, light, picture ? uv[0] : 0.0f, picture ? uv[1] : 0.0f, pivotX, steerSin, steerCos);
                steeredVertex(consumer, pose, face.x1(), face.y1(), face.z1(), face.nx(), face.ny(), face.nz(), color, light, picture ? uv[2] : 0.0f, picture ? uv[3] : 0.0f, pivotX, steerSin, steerCos);
                steeredVertex(consumer, pose, face.x2(), face.y2(), face.z2(), face.nx(), face.ny(), face.nz(), color, light, picture ? uv[4] : 0.0f, picture ? uv[5] : 0.0f, pivotX, steerSin, steerCos);
                steeredVertex(consumer, pose, face.x3(), face.y3(), face.z3(), face.nx(), face.ny(), face.nz(), color, light, picture ? uv[6] : 0.0f, picture ? uv[7] : 0.0f, pivotX, steerSin, steerCos);
            }
        }
    }

    private static String liveryColorKey(CarLiveryColors colors, CarLiveryTexture texture) {
        return colors.body() + ":" + colors.accent1() + ":" + colors.accent2() + ":" + texture.id();
    }

    public static int liveryColor(ColoredObjModel.Face face, CarLiveryColors colors, NativeImage liveryImage) {
        int materialRgb = face.materialRgb();
        int r = (materialRgb >> 16) & 255;
        int g = (materialRgb >> 8) & 255;
        int b = materialRgb & 255;
        int brightness = Math.max(r, Math.max(g, b));
        String group = face.group();

        if (isRubber(face)) {
            return TYRE;
        }
        if (isCarbonFibre(face)) {
            return CARBON;
        }
        if (liveryImage != null && isLiveryPaintable(face)) {
            int sampled = sampleLivery(face, liveryImage);
            if (((sampled >>> 24) & 255) > 0) {
                return sampled;
            }
        }
        if (group.endsWith("-FW-Endplate") || group.startsWith("RW-")) {
            return fixedAeroDetailColor(r, g, b, brightness);
        }
        if (group.endsWith("-Front-Connector")) {
            return materialDetailColor(r, g, b, brightness, METAL, colors.accent1(), colors.accent2());
        }
        if (group.equals("FW-Tip")) {
            return materialDetailColor(r, g, b, brightness, colors.accent2(), colors.accent1(), METAL);
        }
        if (group.equals("Upper-Body") || group.equals("Chassis")) {
            return materialDetailColor(r, g, b, brightness, colors.body(), colors.accent1(), colors.accent2());
        }
        if (group.equals("LeftSection") || group.equals("RightSection")) {
            return materialDetailColor(r, g, b, brightness, colors.body(), colors.accent1(), colors.accent2());
        }
        if (group.equals("MidSection")) {
            return materialDetailColor(r, g, b, brightness, colors.body(), colors.accent1(), colors.accent2());
        }
        return materialDetailColor(r, g, b, brightness, colors.body(), colors.accent1(), colors.accent2());
    }

    private static boolean isCarbonFibre(ColoredObjModel.Face face) {
        int materialRgb = face.materialRgb() & 0x00FFFFFF;
        String group = face.group();
        return materialRgb == 0x00222630 || group.equals("Underfloor-mid") || group.equals("Underfloor") || group.equals("Diffuser");
    }

    private static boolean isRubber(ColoredObjModel.Face face) {
        return (face.materialRgb() & 0x00FFFFFF) == 0x002A2E36;
    }

    public static boolean isLiveryPaintable(ColoredObjModel.Face face) {
        return face.hasLiveryRegion() && LiveryUvMapping.isPaintableMaterial(face.materialRgb());
    }

    private static int sampleLivery(ColoredObjModel.Face face, NativeImage image) {
        int[] pixel = legacySamplePixel(face);
        return image.getPixel(pixel[0], pixel[1]);
    }

    private static int fixedAeroDetailColor(int r, int g, int b, int brightness) {
        if (r == 0 && g == 0 && b == 0) {
            return CARBON;
        }
        if (brightness > 120) {
            return METAL;
        }
        return CARBON;
    }

    private static int materialDetailColor(int r, int g, int b, int brightness, int baseColor, int accent1, int accent2) {
        if (r == 0 && g == 0 && b == 0) {
            return CARBON;
        }
        if (r > 180 && g < 90 && b < 80) {
            return accent2;
        }
        if (r > 220 && g > 180 && b > 120) {
            return accent2;
        }
        if (brightness > 120 && Math.abs(r - g) < 18 && Math.abs(g - b) < 18) {
            return accent1;
        }
        if (brightness > 120) {
            return METAL;
        }
        return baseColor;
    }

    private static void steeredVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float normalX, float normalY, float normalZ, int color, int light, float u, float v, float pivotX, float steerSin, float steerCos) {
        float dx = x - pivotX;
        float dz = z - FRONT_WHEEL_PIVOT_Z;
        float steeredX = pivotX + dx * steerCos + dz * steerSin;
        float steeredZ = FRONT_WHEEL_PIVOT_Z - dx * steerSin + dz * steerCos;
        float steeredNormalX = normalX * steerCos + normalZ * steerSin;
        float steeredNormalZ = -normalX * steerSin + normalZ * steerCos;
        vertex(consumer, pose, steeredX, y, steeredZ, steeredNormalX, normalY, steeredNormalZ, color, light, u, v);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float normalX, float normalY, float normalZ, int color, int light, float u, float v) {
        consumer.addVertex(pose, modelX(x), modelY(y), modelZ(z))
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, normalX, normalY, normalZ);
    }

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static class CarRenderState extends EntityRenderState {
        public float yRot;
        public float frontWheelSteerDegrees;
        public int lightCoords;
        public int tyreCompound;
        public boolean tyresFitted = true;
        public float jackLift;
        public float bumpPitch;
        public CarLiveryColors liveryColors = CarLiveryColors.DEFAULT;
        public CarLiveryTexture liveryTexture = CarLiveryTexture.NONE;
    }
}
