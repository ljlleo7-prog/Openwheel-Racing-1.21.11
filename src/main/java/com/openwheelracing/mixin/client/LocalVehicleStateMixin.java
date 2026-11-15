package com.openwheelracing.mixin.client;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ordinary observer updates must not feed delayed driving state back into the local solver. */
@Mixin(ClientPacketListener.class)
public abstract class LocalVehicleStateMixin {
    @Unique
    private static OpenwheelCarEntity localCar(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.player == null
                || !(mc.player.getVehicle() instanceof OpenwheelCarEntity car)
                || car.getId() != entityId || !car.usesFramedVehicleMovement()
                || car.isRecoveryActive()) return null;
        return car;
    }

    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"), cancellable = true)
    private void openwheelracing$keepLocalVelocity(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        if (localCar(packet.getId()) != null) ci.cancel();
    }

    @Inject(method = "handleSetEntityData", at = @At("HEAD"), cancellable = true)
    private void openwheelracing$keepLocalDrivingState(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        OpenwheelCarEntity car = localCar(packet.id());
        if (car == null) return;
        car.applyLocalDrivingMetadata(packet.packedItems());
        ci.cancel();
    }
}
