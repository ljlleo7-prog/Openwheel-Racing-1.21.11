package com.openwheelracing.mixin.client;

import com.openwheelracing.client.debug.LocalVehicleNetworkTest;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class VehiclePacketDelayMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At("HEAD"), cancellable = true)
    private void openwheelracing$delayLocalVehicleTraffic(Packet<?> packet, ChannelFutureListener listener,
                                                         boolean flush, CallbackInfo ci) {
        if (LocalVehicleNetworkTest.defer((Connection) (Object) this, packet, listener, flush)) ci.cancel();
    }
}
