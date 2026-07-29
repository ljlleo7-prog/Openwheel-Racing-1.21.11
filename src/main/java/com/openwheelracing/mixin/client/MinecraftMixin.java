package com.openwheelracing.mixin.client;

import com.openwheelracing.client.camera.OWRCameraMode;
import com.openwheelracing.client.input.OWRClientInputHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow public net.minecraft.client.Options options;

    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
    private void openwheelracing$handleOnboardOverrides(CallbackInfo ci) {
        if (OWRClientInputHandler.onboardCar() == null) {
            return;
        }
        boolean handled = false;
        while (this.options.keyTogglePerspective.consumeClick()) {
            handled |= OWRCameraMode.cycleOnboardPerspective();
        }
        for (int i = 0; i < this.options.keyHotbarSlots.length; i++) {
            KeyMapping key = this.options.keyHotbarSlots[i];
            while (key.consumeClick()) {
                OWRClientInputHandler.handleOnboardNumberKey(org.lwjgl.glfw.GLFW.GLFW_KEY_1 + i);
                handled = true;
            }
        }
        if (handled) {
            ci.cancel();
        }
    }
}
