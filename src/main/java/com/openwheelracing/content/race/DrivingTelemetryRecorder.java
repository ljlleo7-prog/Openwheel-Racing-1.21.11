package com.openwheelracing.content.race;

import java.util.UUID;
import java.util.function.Consumer;

/** Independent raw driving capture; lap state never starts, stops, or clears this recorder. */
public final class DrivingTelemetryRecorder {
    private static final long SEGMENT_TICKS = 200;
    private final LapTelemetryRecorder recorder = new LapTelemetryRecorder();
    private long startedTick;
    private long startedWorldTime;
    private UUID captureId;

    public void tick(UUID driverId, String driverName, String dimensionId, long tick, long worldTime,
                     double x, double y, double z, double speedKmh, Consumer<Segment> save) {
        if (recorder.active() && (!recorder.driverId().equals(driverId) || tick - startedTick >= SEGMENT_TICKS)) {
            flush(tick, save);
        }
        if (driverId == null) return;
        if (!recorder.active()) {
            startedTick = tick;
            startedWorldTime = worldTime;
            captureId = UUID.randomUUID();
            recorder.startUntracked(driverId, driverName, dimensionId, tick);
        }
        recorder.sampleUntracked(tick, x, y, z, speedKmh);
    }

    public void flush(long tick, Consumer<Segment> save) {
        if (!recorder.active()) return;
        int duration = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, tick - startedTick) * 50L);
        recorder.finish(0, duration).ifPresent(snapshot -> save.accept(new Segment(captureId, startedWorldTime, snapshot)));
    }

    public record Segment(UUID captureId, long startedWorldTime, LapTelemetryRecorder.Snapshot snapshot) { }
}
