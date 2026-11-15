package com.openwheelracing.client.debug;

import com.openwheelracing.client.hud.RewindNotification;
import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.network.VehicleRollbackNetwork;
import net.minecraft.client.Minecraft;

/** Client-only presentation and preference bridge for the common vehicle protocol. */
public final class VehicleRollbackClient {
    private VehicleRollbackClient() {}

    public static boolean autoRewindEnabled() {
        return LocalVehicleNetworkTest.autoRewind();
    }

    public static void notifyOrdinaryCorrection() {
        RewindNotification.status(VehicleRollbackNetwork.SYNC);
    }

    public static void receive(VehicleRollbackNetwork.Update update) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof OpenwheelCarEntity car)
                || car.getId() != update.entityId() || !car.getUUID().equals(update.vehicleUuid())
                || !car.level().dimension().identifier().toString().equals(update.dimension())) return;
        if (update.status() == VehicleRollbackNetwork.RECONCILE) {
            car.applyRollbackReconciliation(update);
            return;
        }
        int actualRewindTicks = car.getRollbackFrameSequence() - update.frame();
        var previous = car.position();
        if (!car.applyRollbackUpdate(update)) return;
        if (update.status() == VehicleRollbackNetwork.CHECKING) {
            RewindNotification.status(VehicleRollbackNetwork.CHECKING);
        } else {
            LocalVehicleNetworkTest.discardBufferedVehiclePackets();
            if (update.status() == VehicleRollbackNetwork.REWOUND) {
                RewindNotification.show(Math.max(update.rewindTicks(), actualRewindTicks) / 20.0, false);
                RewindNotification.displacement(previous.subtract(car.position()), car.getYRot());
            } else if (update.status() != VehicleRollbackNetwork.SYNC) {
                RewindNotification.status(update.status());
            } else {
                RewindNotification.status(VehicleRollbackNetwork.SYNC);
            }
        }
    }
}
