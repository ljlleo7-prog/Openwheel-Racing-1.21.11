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
        if (!OWRCameraMode.isTCamera() || !(viewEntity.getVehicle() instanceof OpenwheelCarEntity car)) {
            return;
        }
        this.setRotation(car.getYRot(), 8.0F, 0.0F);
        Vec3 position = OWRCameraMode.tCameraPosition(car, viewEntity, partialTick);
        this.setPosition(position);
    }

    @Shadow protected abstract void setPosition(Vec3 position);

    @Shadow public abstract void setRotation(float yaw, float pitch, float roll);
}
