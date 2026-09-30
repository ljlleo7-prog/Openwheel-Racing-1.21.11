package com.openwheelracing.content.race;

import net.minecraft.network.FriendlyByteBuf;

public record LapTelemetryData(long lapId, String driverName, int lapMillis, double routeLength,
                               int[] elapsedMillis, int[] routeDistanceCm, int[] coveredDistanceCm,
                               int[] speedCmps, int[] worldXCm, int[] worldYCm, int[] worldZCm,
                               byte[] trackingStatus) {
    public static final int MAX_SAMPLES = 1024;

    public LapTelemetryData {
        int count = elapsedMillis == null ? 0 : elapsedMillis.length;
        if (count > MAX_SAMPLES || routeDistanceCm.length != count || coveredDistanceCm.length != count
            || speedCmps.length != count || worldXCm.length != count || worldYCm.length != count
            || worldZCm.length != count || trackingStatus.length != count) {
            throw new IllegalArgumentException("Invalid lap telemetry samples");
        }
        driverName = driverName == null ? "" : driverName;
        elapsedMillis = elapsedMillis.clone();
        routeDistanceCm = routeDistanceCm.clone();
        coveredDistanceCm = coveredDistanceCm.clone();
        speedCmps = speedCmps.clone();
        worldXCm = worldXCm.clone();
        worldYCm = worldYCm.clone();
        worldZCm = worldZCm.clone();
        trackingStatus = trackingStatus.clone();
    }

    public static void encode(LapTelemetryData data, FriendlyByteBuf buffer) {
        buffer.writeLong(data.lapId());
        buffer.writeUtf(data.driverName(), 64);
        buffer.writeVarInt(data.lapMillis());
        buffer.writeDouble(data.routeLength());
        buffer.writeVarInt(data.elapsedMillis().length);
        for (int i = 0; i < data.elapsedMillis().length; i++) {
            buffer.writeVarInt(data.elapsedMillis()[i]);
            buffer.writeInt(data.routeDistanceCm()[i]);
            buffer.writeInt(data.coveredDistanceCm()[i]);
            buffer.writeVarInt(data.speedCmps()[i]);
            buffer.writeInt(data.worldXCm()[i]);
            buffer.writeInt(data.worldYCm()[i]);
            buffer.writeInt(data.worldZCm()[i]);
            buffer.writeByte(data.trackingStatus()[i]);
        }
    }

    public static LapTelemetryData decode(FriendlyByteBuf buffer) {
        long lapId = buffer.readLong();
        String driverName = buffer.readUtf(64);
        int lapMillis = buffer.readVarInt();
        double routeLength = buffer.readDouble();
        int count = Math.min(MAX_SAMPLES, buffer.readVarInt());
        int[] elapsed = new int[count];
        int[] route = new int[count];
        int[] covered = new int[count];
        int[] speed = new int[count];
        int[] x = new int[count];
        int[] y = new int[count];
        int[] z = new int[count];
        byte[] status = new byte[count];
        for (int i = 0; i < count; i++) {
            elapsed[i] = buffer.readVarInt();
            route[i] = buffer.readInt();
            covered[i] = buffer.readInt();
            speed[i] = buffer.readVarInt();
            x[i] = buffer.readInt();
            y[i] = buffer.readInt();
            z[i] = buffer.readInt();
            status[i] = buffer.readByte();
        }
        return new LapTelemetryData(lapId, driverName, lapMillis, routeLength, elapsed, route, covered, speed, x, y, z, status);
    }
}
