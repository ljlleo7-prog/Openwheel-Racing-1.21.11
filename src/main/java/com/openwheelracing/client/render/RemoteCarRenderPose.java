package com.openwheelracing.client.render;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Renders the same remote pose used by local car-contact prediction.
 * Logical network poses remain untouched.
 */
final class RemoteCarRenderPose {
    private RemoteCarRenderPose() {}

    static float apply(OpenwheelCarEntity car, EntityRenderState state, float partialTick, boolean localCar) {
        if (localCar) {
            car.clearRemoteContactPose();
            var offset = car.correctionRenderOffset();
            state.x += offset.x; state.y += offset.y; state.z += offset.z;
            return car.getYRot(partialTick) + car.correctionRenderYaw();
        }
        var pose = car.remoteContactPose();
        state.x = pose.x(); state.y = pose.y(); state.z = pose.z();
        return pose.yaw();
    }
}
