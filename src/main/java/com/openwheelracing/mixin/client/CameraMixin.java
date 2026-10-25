package com.openwheelracing.mixin.client;

import com.openwheelracing.client.camera.OWRCameraMode;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "setup", at = @At("TAIL"))
    private void openwheelracing$setupTCamera(Level level, Entity viewEntity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        com.openwheelracing.content.weather.RegionalWeather.updateCamera(viewEntity.blockPosition());
        if (!(viewEntity.getVehicle() instanceof OpenwheelCarEntity car)) {
            return;
        }
        boolean tCamera = OWRCameraMode.isTCamera();
        if (tCamera) {
            Vec3 position = OWRCameraMode.tCameraPosition(car, viewEntity, partialTick);
            this.setPosition(position);
        }
        if (!detached || tCamera) {
            // Reuse the interpolated chassis pitch, preserving the player's free look.
            float relativeYaw = this.yRot - car.getYRot(partialTick);
            float bumpPitch = car.getTerrainBumpPitch(partialTick)
                * (float) Math.cos(Math.toRadians(relativeYaw));
            this.setRotation(this.yRot, this.xRot + bumpPitch);
        }
    }

    @Shadow private float yRot;
    @Shadow private float xRot;

    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
}
