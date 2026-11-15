package com.openwheelracing.mixin.client;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class VehicleCorrectionEpochMixin {
    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void openwheelracing$useFramedCorrections(ClientboundMoveVehiclePacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread() && mc.player != null
                && mc.player.getVehicle() instanceof OpenwheelCarEntity car && car.usesFramedVehicleMovement()) {
            // This legacy packet has no epoch; framed cars use the custom authoritative snapshots.
            ci.cancel();
        }
    }
}
