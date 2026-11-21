package com.openwheelracing.network;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EffectiveControlFrameTest {
    @Test
    void assistedValuesSurviveTheExistingMotionFrameWithoutReplacingRequestedPedals() {
        double[] state = new double[VehicleRollbackNetwork.MOTION_STATE_SIZE];
        int[] counters = new int[VehicleRollbackNetwork.MOTION_COUNTER_SIZE];
        state[78] = .35;
        state[79] = .6;
        var motion = new OpenwheelCarEntity.RollbackMotion(Vec3.ZERO, Vec3.ZERO, 0, true, 12, state, counters);
        var frame = new VehicleRollbackNetwork.Frame(1, 2, 3, 4, Vec3.ZERO, 0,
            1, 1, 0, true, false, 1, true, 0, new UUID(1, 2), "minecraft:overworld", motion);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            VehicleRollbackNetwork.Frame.STREAM_CODEC.encode(buffer, frame);
            var decoded = VehicleRollbackNetwork.Frame.STREAM_CODEC.decode(buffer);
            assertEquals(1, decoded.throttle());
            assertEquals(1, decoded.brake());
            assertEquals(.35, decoded.motion().state()[78], 1e-9);
            assertEquals(.6, decoded.motion().state()[79], 1e-9);
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
