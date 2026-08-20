package com.openwheelracing.content.race;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record TrackMoistureSnapshot(int revision, int drySamples, int dampSamples, int wetSamples,
                                    int soakingSamples, int loadedSamples, int estimatedSamples,
                                    int surfaceRevision, List<Sector> sectors, List<Tile> tiles) {
    public static final int MAX_SECTORS = 48;
    public static final int MAX_TILES = 4_096;
    public static final TrackMoistureSnapshot EMPTY = new TrackMoistureSnapshot(0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of());

    public TrackMoistureSnapshot {
        sectors = List.copyOf(sectors == null ? List.of() : sectors.subList(0, Math.min(MAX_SECTORS, sectors.size())));
        tiles = List.copyOf(tiles == null ? List.of() : tiles.subList(0, Math.min(MAX_TILES, tiles.size())));
    }

    public int totalSamples() {
        return drySamples + dampSamples + wetSamples + soakingSamples;
    }

    public double averageLevel() {
        int total = totalSamples();
        return total <= 0 ? 0.0 : (dampSamples + wetSamples * 2.0 + soakingSamples * 3.0) / total;
    }

    public int conditionLevel() {
        return Math.max(0, Math.min(3, (int) Math.round(averageLevel())));
    }

    public int percent(int level) {
        int total = totalSamples();
        if (total <= 0) return 0;
        int count = switch (level) {
            case 0 -> drySamples;
            case 1 -> dampSamples;
            case 2 -> wetSamples;
            default -> soakingSamples;
        };
        return Math.round(count * 100.0f / total);
    }

    public static void encode(TrackMoistureSnapshot snapshot, FriendlyByteBuf buffer) {
        buffer.writeVarInt(snapshot.revision);
        buffer.writeVarInt(snapshot.drySamples);
        buffer.writeVarInt(snapshot.dampSamples);
        buffer.writeVarInt(snapshot.wetSamples);
        buffer.writeVarInt(snapshot.soakingSamples);
        buffer.writeVarInt(snapshot.loadedSamples);
        buffer.writeVarInt(snapshot.estimatedSamples);
        buffer.writeVarInt(snapshot.surfaceRevision);
        buffer.writeVarInt(snapshot.sectors.size());
        for (Sector sector : snapshot.sectors) {
            buffer.writeInt(sector.x);
            buffer.writeInt(sector.z);
            buffer.writeByte(sector.moistureLevel);
            buffer.writeBoolean(sector.estimated);
        }
        buffer.writeVarInt(snapshot.tiles.size());
        for (Tile tile : snapshot.tiles) {
            buffer.writeInt(tile.chunkX);
            buffer.writeInt(tile.chunkZ);
            buffer.writeBytes(tile.observed);
            buffer.writeBytes(tile.levels);
        }
    }

    public static TrackMoistureSnapshot decode(FriendlyByteBuf buffer) {
        int revision = buffer.readVarInt();
        int dry = buffer.readVarInt();
        int damp = buffer.readVarInt();
        int wet = buffer.readVarInt();
        int soaking = buffer.readVarInt();
        int loaded = buffer.readVarInt();
        int estimated = buffer.readVarInt();
        int surfaceRevision = buffer.readVarInt();
        int count = Math.min(MAX_SECTORS, buffer.readVarInt());
        ArrayList<Sector> sectors = new ArrayList<>(count);
        for (int i = 0; i < count; i++) sectors.add(new Sector(buffer.readInt(), buffer.readInt(), buffer.readUnsignedByte(), buffer.readBoolean()));
        int tileCount = Math.min(MAX_TILES, buffer.readVarInt());
        ArrayList<Tile> tiles = new ArrayList<>(tileCount);
        for (int i = 0; i < tileCount; i++) {
            int chunkX = buffer.readInt();
            int chunkZ = buffer.readInt();
            byte[] observed = new byte[32];
            byte[] levels = new byte[64];
            buffer.readBytes(observed);
            buffer.readBytes(levels);
            tiles.add(new Tile(chunkX, chunkZ, observed, levels));
        }
        return new TrackMoistureSnapshot(revision, dry, damp, wet, soaking, loaded, estimated, surfaceRevision, sectors, tiles);
    }

    public record Sector(int x, int z, int moistureLevel, boolean estimated) {
        public Sector {
            moistureLevel = Math.max(0, Math.min(3, moistureLevel));
        }
    }

    public record Tile(int chunkX, int chunkZ, byte[] observed, byte[] levels) {
        public Tile {
            observed = observed == null || observed.length != 32 ? new byte[32] : observed.clone();
            levels = levels == null || levels.length != 64 ? new byte[64] : levels.clone();
        }

        public boolean observed(int localX, int localZ) {
            int index = (localZ & 15) * 16 + (localX & 15);
            return (observed[index >>> 3] & 1 << (index & 7)) != 0;
        }

        public int level(int localX, int localZ) {
            int index = (localZ & 15) * 16 + (localX & 15);
            return levels[index >>> 2] >>> ((index & 3) * 2) & 3;
        }
    }
}
