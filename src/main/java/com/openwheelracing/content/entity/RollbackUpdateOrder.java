package com.openwheelracing.content.entity;

import com.openwheelracing.network.VehicleRollbackNetwork;

/** Prevent duplicate or stale correction traffic from repeatedly restoring a vehicle. */
final class RollbackUpdateOrder {
    private RollbackUpdateOrder() {}

    static boolean accepts(int currentEpoch, long currentIncident, int epoch, long incident, int status) {
        if (epoch < currentEpoch || incident < currentIncident) return false;
        if (status == VehicleRollbackNetwork.CHECKING) {
            return epoch == currentEpoch && incident > currentIncident;
        }
        // Every physical restore, including a SYNC reply to old traffic, must advance the epoch.
        return epoch > currentEpoch;
    }
}
