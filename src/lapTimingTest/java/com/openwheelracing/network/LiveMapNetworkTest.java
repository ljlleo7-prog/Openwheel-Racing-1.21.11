package com.openwheelracing.network;

import com.openwheelracing.content.race.map.LiveMapSnapshot;
import com.openwheelracing.content.race.map.LiveMapSnapshot.*;
import com.openwheelracing.content.track.TrackMapSnapshot;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveMapNetworkTest {
    @Test void distantCarsLabelsAndFinishGeometrySurviveTransport() {
        var surface = new TrackMapSnapshot(true, "test", "Circuit", "minecraft:overworld", 1,
            -100, -200, 100, 200, List.of(new TrackMapSnapshot.CellRun(0, -10, 10)), List.of(), List.of(), List.of());
        var geometry = new Geometry(surface, List.of(new Point(0, 0), new Point(1000, 2000)),
            new Finish(new Point(-10, 0), new Point(10, 0), Math.PI / 2));
        var nearby = new Car(new UUID(1, 1), new UUID(2, 1), "Driver A", 10, 20, 30);
        var distant = new Car(new UUID(1, 2), new UUID(2, 2), "车手 B", 30_000, -40_000, -90);
        var expected = new LiveMapSnapshot(7, "minecraft:overworld", 1234, geometry, List.of(nearby, distant));
        assertEquals(expected, roundTrip(expected));
    }

    @Test void positionOnlyUpdatesPreserveTheEntireRosterWithoutResendingStaticGeometry() {
        var car = new Car(new UUID(1, 1), new UUID(2, 1), "Driver", 10000, -10000, 20);
        var expected = new LiveMapSnapshot(3, "minecraft:the_nether", 999, null, List.of(car));
        assertEquals(expected, roundTrip(expected));
        assertEquals(new LiveMapSnapshot(3, "minecraft:the_nether", 1000, null, List.of()),
            roundTrip(new LiveMapSnapshot(3, "minecraft:the_nether", 1000, null, List.of())));
    }

    @Test void excessiveCarCountsAreRejectedBeforeAllocation() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buffer.writeInt(1); buffer.writeUtf("minecraft:overworld"); buffer.writeLong(1);
            buffer.writeBoolean(false); buffer.writeVarInt(8193);
            assertThrows(IllegalArgumentException.class, () -> LiveMapNetwork.Response.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static LiveMapSnapshot roundTrip(LiveMapSnapshot snapshot) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            LiveMapNetwork.Response.CODEC.encode(buffer, new LiveMapNetwork.Response(snapshot));
            var decoded = LiveMapNetwork.Response.CODEC.decode(buffer).snapshot();
            assertEquals(0, buffer.readableBytes());
            return decoded;
        } finally {
            buffer.release();
        }
    }
}
