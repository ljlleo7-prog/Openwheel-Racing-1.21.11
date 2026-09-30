package com.openwheelracing.content.race;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class LapTelemetryStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(256), runnable -> {
            Thread thread = new Thread(runnable, "OWR lap telemetry I/O");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());

    private LapTelemetryStore() {
    }

    public static void save(MinecraftServer server, LapTelemetryRecorder.Snapshot snapshot) {
        Path directory = directory(server);
        try {
            IO.execute(() -> write(directory, snapshot));
        } catch (RuntimeException exception) {
            LOGGER.error("Could not queue lap telemetry for lap {}", snapshot.lapId(), exception);
        }
    }

    public static void load(MinecraftServer server, OWRLapRecords.LapRecord lap, Consumer<Optional<LapTelemetryData>> callback) {
        Path telemetryDirectory = directory(server);
        String fileName = fileName(lap.id(), lap.driverId(), lap.dimensionId());
        Path file = telemetryDirectory.resolve(fileName);
        Path legacyFile = telemetryDirectory.getParent().resolve(fileName);
        try {
            IO.execute(() -> {
                Optional<LapTelemetryData> result;
                try {
                    result = read(file, lap);
                    if (result.isEmpty()) result = read(legacyFile, lap);
                    if (result.isEmpty()) {
                        for (Path candidate : findCandidates(telemetryDirectory, lap)) {
                            result = read(candidate, lap);
                            if (result.isPresent()) {
                                LOGGER.info("Loaded lap telemetry {} from fallback path {}", lap.id(), candidate);
                                break;
                            }
                        }
                    }
                    if (result.isEmpty()) {
                        LOGGER.warn("No readable telemetry for lap {}; expected {} (also checked {})",
                            lap.id(), file, legacyFile);
                    }
                } catch (Exception exception) {
                    LOGGER.warn("Could not load lap telemetry for lap {}", lap.id(), exception);
                    result = Optional.empty();
                }
                Optional<LapTelemetryData> finalResult = result;
                server.execute(() -> callback.accept(finalResult));
            });
        } catch (RuntimeException exception) {
            LOGGER.error("Could not queue lap telemetry load for lap {}", lap.id(), exception);
            callback.accept(Optional.empty());
        }
    }

    private static Path directory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("openwheelracing").resolve("lap-telemetry");
    }

    private static String fileName(long lapId, java.util.UUID driverId, String dimensionId) {
        String safeDimension = dimensionId == null ? "unknown" : dimensionId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return safeDimension + "-" + driverId + "-" + lapId + ".csv";
    }

    private static List<Path> findCandidates(Path directory, OWRLapRecords.LapRecord lap) throws IOException {
        String suffix = "-" + lap.id() + ".csv";
        String safeDimension = lap.dimensionId().replaceAll("[^a-zA-Z0-9._-]", "_");
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        for (Path root : List.of(directory, directory.getParent())) {
            if (!Files.isDirectory(root)) continue;
            try (var files = Files.list(root)) {
                files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .forEach(candidates::add);
            }
        }
        List<Path> dimensionMatches = candidates.stream()
            .filter(path -> path.getFileName().toString().startsWith(safeDimension + "-"))
            .sorted(Comparator.comparing(path -> path.getFileName().toString()))
            .toList();
        if (dimensionMatches.size() == 1) return dimensionMatches;
        if (dimensionMatches.size() > 1) return List.of();
        return candidates.size() == 1 ? new ArrayList<>(candidates) : List.of();
    }

    private static void write(Path directory, LapTelemetryRecorder.Snapshot data) {
        Path file = directory.resolve(fileName(data.lapId(), data.driverId(), data.dimensionId()));
        try {
            Files.createDirectories(directory);
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                writer.write("#track_id=" + data.trackId() + ",route_id=" + data.routeId() + ",route_length_m=" + data.routeLength());
                writer.newLine();
                writer.write("elapsed_ms,route_distance_cm,covered_distance_cm,speed_cmps,x_cm,y_cm,z_cm,tracking_status");
                writer.newLine();
                for (int index = 0; index < data.elapsedMillis().length; index++) {
                    writer.write(data.elapsedMillis()[index] + "," + data.routeDistanceCm()[index] + ","
                        + data.coveredDistanceCm()[index] + "," + data.speedCmps()[index] + ","
                        + data.worldXCm()[index] + "," + data.worldYCm()[index] + "," + data.worldZCm()[index]
                        + "," + Byte.toUnsignedInt(data.trackingStatus()[index]));
                    writer.newLine();
                }
            }
        } catch (IOException exception) {
            LOGGER.error("Could not save lap telemetry for lap {}", data.lapId(), exception);
        }
    }

    private static Optional<LapTelemetryData> read(Path file, OWRLapRecords.LapRecord lap) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > 256 * 1024L) return Optional.empty();
        double routeLength = 0.0;
        int capacity = LapTelemetryData.MAX_SAMPLES;
        int[] elapsed = new int[capacity];
        int[] route = new int[capacity];
        int[] covered = new int[capacity];
        int[] speed = new int[capacity];
        int[] x = new int[capacity];
        int[] y = new int[capacity];
        int[] z = new int[capacity];
        byte[] status = new byte[capacity];
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null && count <= capacity) {
                if (line.startsWith("#")) {
                    for (String part : line.substring(1).split(",")) {
                        if (part.startsWith("route_length_m=")) {
                            routeLength = Double.parseDouble(part.substring("route_length_m=".length()));
                        }
                    }
                    continue;
                }
                if (line.startsWith("elapsed_ms,")) continue;
                String[] values = line.split(",", -1);
                if (values.length != 8 || count == capacity) return Optional.empty();
                elapsed[count] = Integer.parseInt(values[0]);
                route[count] = Integer.parseInt(values[1]);
                covered[count] = Integer.parseInt(values[2]);
                speed[count] = Integer.parseInt(values[3]);
                x[count] = Integer.parseInt(values[4]);
                y[count] = Integer.parseInt(values[5]);
                z[count] = Integer.parseInt(values[6]);
                status[count] = (byte) Integer.parseInt(values[7]);
                count++;
            }
        }
        if (count < 2 || !(routeLength > 0.0) || !Double.isFinite(routeLength)) return Optional.empty();
        return Optional.of(new LapTelemetryData(lap.id(), lap.driverName(), lap.lapMillis(), routeLength,
            java.util.Arrays.copyOf(elapsed, count), java.util.Arrays.copyOf(route, count),
            java.util.Arrays.copyOf(covered, count), java.util.Arrays.copyOf(speed, count),
            java.util.Arrays.copyOf(x, count), java.util.Arrays.copyOf(y, count), java.util.Arrays.copyOf(z, count),
            java.util.Arrays.copyOf(status, count)));
    }
}
