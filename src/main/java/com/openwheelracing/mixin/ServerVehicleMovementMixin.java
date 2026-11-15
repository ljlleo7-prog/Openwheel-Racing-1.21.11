package com.openwheelracing.mixin;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Framed cars use server physics instead of vanilla packet movement.
 * The legacy path observes only the final accepted position for race timing.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerVehicleMovementMixin {
    @Shadow public ServerPlayer player;
    @Unique private OpenwheelCarEntity openwheelracing$car;
    @Unique private Vec3 openwheelracing$before;

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void openwheelracing$beforeVehicleMove(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        if (!player.level().getServer().isSameThread()) return;
        openwheelracing$car = null;
        if (player.getRootVehicle() instanceof OpenwheelCarEntity car && car.getControllingPassenger() == player) {
            if (car.usesFramedVehicleMovement()) {
                ci.cancel(); // Framed inputs own motion; vanilla positions cannot bypass epochs.
                return;
            }
            openwheelracing$car = car;
            openwheelracing$before = car.position();
        }
    }

    @Inject(method = "handleMoveVehicle", at = @At("RETURN"))
    private void openwheelracing$afterVehicleMove(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        if (!player.level().getServer().isSameThread()) return;
        OpenwheelCarEntity car = openwheelracing$car;
        Vec3 before = openwheelracing$before;
        openwheelracing$car = null;
        openwheelracing$before = null;
        // Rejected movement is restored to the original position by vanilla. Never examine
        // its temporary collision-test move or count a rejected packet target as a crossing.
        if (car != null && !car.isRemoved() && car.getControllingPassenger() == player && car.position().distanceToSqr(before) > 1e-12) {
            car.onAcceptedVehiclePosition(before);
        }
    }
}
