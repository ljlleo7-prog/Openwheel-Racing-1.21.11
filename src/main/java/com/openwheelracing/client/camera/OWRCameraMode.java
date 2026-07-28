package com.openwheelracing.client.camera;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class OWRCameraMode {
    private static final double T_CAMERA_HEIGHT = 1.22;
    private static final double T_CAMERA_FORWARD_OFFSET = -0.28;
    private static boolean tCamera;

    private OWRCameraMode() {
    }

    public static boolean isTCamera() {
        Minecraft mc = Minecraft.getInstance();
        return tCamera && mc.player != null && mc.player.getVehicle() instanceof OpenwheelCarEntity;
    }

    public static boolean cycleOnboardPerspective() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !(mc.player.getVehicle() instanceof OpenwheelCarEntity)) {
            tCamera = false;
            return false;
        }
        CameraType current = mc.options.getCameraType();
        if (tCamera) {
            tCamera = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        } else if (current == CameraType.FIRST_PERSON) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (current == CameraType.THIRD_PERSON_BACK) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        } else {
            tCamera = true;
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (mc.levelRenderer != null) {
            mc.levelRenderer.needsUpdate();
        }
        return true;
    }

    public static void clearIfNotOnboard() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof OpenwheelCarEntity)) {
            tCamera = false;
        }
    }

    public static Vec3 tCameraPosition(OpenwheelCarEntity car, Entity viewEntity, float partialTick) {
        double x = lerp(partialTick, car.xOld, car.getX());
        double y = lerp(partialTick, car.yOld, car.getY());
        double z = lerp(partialTick, car.zOld, car.getZ());
        float yaw = car.getYRot();
        double radians = Math.toRadians(yaw);
        double forwardX = -Math.sin(radians);
        double forwardZ = Math.cos(radians);
        return new Vec3(x + forwardX * T_CAMERA_FORWARD_OFFSET, y + T_CAMERA_HEIGHT, z + forwardZ * T_CAMERA_FORWARD_OFFSET);
    }

    private static double lerp(float partialTick, double start, double end) {
        return start + (end - start) * partialTick;
    }
}
