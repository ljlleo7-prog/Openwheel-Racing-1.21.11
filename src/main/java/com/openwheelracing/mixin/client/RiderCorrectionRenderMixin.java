package com.openwheelracing.mixin.client;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class RiderCorrectionRenderMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
        at = @At("TAIL"))
    private void openwheelracing$smoothRider(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        if (entity == Minecraft.getInstance().player && entity.getVehicle() instanceof OpenwheelCarEntity car) {
            var offset = car.correctionRenderOffset();
            state.x += offset.x; state.y += offset.y; state.z += offset.z;
        }
    }
}
