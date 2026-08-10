package com.openwheelracing.content.track;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record TrackMapSnapshot(boolean present, String source, String name, String dimensionId, int revision, int minX, int minZ, int maxX, int maxZ, List<CellRun> asphaltRuns, List<CellRun> pitRuns, List<MapPoint> startFinishMarkers, List<MapPoint> checkpointMarkers) {
    public static final int MAX_RUNS_PER_SURFACE = 16_384;
    public static final int MAX_MARKERS = 512;
    public static final TrackMapSnapshot EMPTY = new TrackMapSnapshot(false, "none", "", "", 0, 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of());

    public TrackMapSnapshot {
        source = source == null ? "none" : source;
        name = name == null ? "" : name;
        dimensionId = dimensionId == null ? "" : dimensionId;
        asphaltRuns = boundedRuns(asphaltRuns == null ? List.of() : asphaltRuns);
        pitRuns = boundedRuns(pitRuns == null ? List.of() : pitRuns);
        startFinishMarkers = boundedMarkers(startFinishMarkers == null ? List.of() : startFinishMarkers);
        checkpointMarkers = boundedMarkers(checkpointMarkers == null ? List.of() : checkpointMarkers);
    }

    public boolean contains(int x, int z) {
        return contains(asphaltRuns, x, z) || contains(pitRuns, x, z);
    }

    public static void encode(TrackMapSnapshot snapshot, FriendlyByteBuf buffer) {
        buffer.writeBoolean(snapshot.present);
        if (!snapshot.present) {
            return;
        }
        buffer.writeUtf(snapshot.source, 32);
        buffer.writeUtf(snapshot.name, 80);
        buffer.writeUtf(snapshot.dimensionId, 128);
        buffer.writeInt(snapshot.revision);
        buffer.writeInt(snapshot.minX);
        buffer.writeInt(snapshot.minZ);
        buffer.writeInt(snapshot.maxX);
        buffer.writeInt(snapshot.maxZ);
        writeRuns(buffer, snapshot.asphaltRuns);
        writeRuns(buffer, snapshot.pitRuns);
        writeMarkers(buffer, snapshot.startFinishMarkers);
        writeMarkers(buffer, snapshot.checkpointMarkers);
    }

    public static TrackMapSnapshot decode(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return EMPTY;
        }
        String source = buffer.readUtf(32);
        String name = buffer.readUtf(80);
        String dimensionId = buffer.readUtf(128);
        int revision = buffer.readInt();
        int minX = buffer.readInt();
        int minZ = buffer.readInt();
        int maxX = buffer.readInt();
        int maxZ = buffer.readInt();
        List<CellRun> asphaltRuns = readRuns(buffer);
        List<CellRun> pitRuns = readRuns(buffer);
        List<MapPoint> startFinishMarkers = readMarkers(buffer);
        List<MapPoint> checkpointMarkers = readMarkers(buffer);
        return new TrackMapSnapshot(true, source, name, dimensionId, revision, minX, minZ, maxX, maxZ, asphaltRuns, pitRuns, startFinishMarkers, checkpointMarkers);
    }

    private static boolean contains(List<CellRun> runs, int x, int z) {
        for (CellRun run : runs) {
            if (run.z() == z && x >= run.startX() && x <= run.endX()) {
                return true;
            }
        }
        return false;
    }

    private static List<CellRun> boundedRuns(List<CellRun> runs) {
        return runs.size() <= MAX_RUNS_PER_SURFACE ? List.copyOf(runs) : List.copyOf(runs.subList(0, MAX_RUNS_PER_SURFACE));
    }

    private static List<MapPoint> boundedMarkers(List<MapPoint> markers) {
        return markers.size() <= MAX_MARKERS ? List.copyOf(markers) : List.copyOf(markers.subList(0, MAX_MARKERS));
    }

    private static void writeRuns(FriendlyByteBuf buffer, List<CellRun> runs) {
        int size = Math.min(runs.size(), MAX_RUNS_PER_SURFACE);
        buffer.writeVarInt(size);
        for (int index = 0; index < size; index++) {
            CellRun run = runs.get(index);
            buffer.writeInt(run.z());
            buffer.writeInt(run.startX());
            buffer.writeInt(run.endX());
        }
    }

    private static List<CellRun> readRuns(FriendlyByteBuf buffer) {
        int size = Math.min(buffer.readVarInt(), MAX_RUNS_PER_SURFACE);
        ArrayList<CellRun> runs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            runs.add(new CellRun(buffer.readInt(), buffer.readInt(), buffer.readInt()));
        }
        return runs;
    }

    private static void writeMarkers(FriendlyByteBuf buffer, List<MapPoint> markers) {
        int size = Math.min(markers.size(), MAX_MARKERS);
        buffer.writeVarInt(size);
        for (int index = 0; index < size; index++) {
            MapPoint marker = markers.get(index);
            buffer.writeInt(marker.x());
            buffer.writeInt(marker.z());
        }
    }

    private static List<MapPoint> readMarkers(FriendlyByteBuf buffer) {
        int size = Math.min(buffer.readVarInt(), MAX_MARKERS);
        ArrayList<MapPoint> markers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            markers.add(new MapPoint(buffer.readInt(), buffer.readInt()));
        }
        return markers;
    }

    public record CellRun(int z, int startX, int endX) {
    }

    public record MapPoint(int x, int z) {
    }
}
