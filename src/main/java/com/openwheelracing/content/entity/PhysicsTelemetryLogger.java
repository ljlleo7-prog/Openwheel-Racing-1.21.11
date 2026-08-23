package com.openwheelracing.content.entity;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

final class PhysicsTelemetryLogger implements AutoCloseable {
    private static final int CACHE_FLUSH_CHARS = 64 * 1024;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String HEADER = "tick,speed_kmh,velocity_long_mps,velocity_lat_mps,yaw_rate_radps,steer_input,steer_deg,throttle,brake,brake_direction,longitudinal_load_transfer_n,front_yaw_moment_nm,rear_yaw_moment_nm,downforce_n,abs_enabled,tc_enabled,abs_envelope,tc_envelope,"
        + wheelHeader("fl") + ',' + wheelHeader("fr") + ',' + wheelHeader("rl") + ',' + wheelHeader("rr") + '\n';

    private final Path path;
    private final BufferedWriter writer;
    private final StringBuilder cache = new StringBuilder(CACHE_FLUSH_CHARS + 4096);
    private long samples;

    private PhysicsTelemetryLogger(Path path, BufferedWriter writer) throws IOException {
        this.path = path;
        this.writer = writer;
        writer.write(HEADER);
    }

    static PhysicsTelemetryLogger start(MinecraftServer server, UUID carId) throws IOException {
        Path directory = server.getWorldPath(LevelResource.ROOT).resolve("openwheelracing").resolve("physics-logs");
        Files.createDirectories(directory);
        String fileName = "car-physics-" + FILE_TIME.format(LocalDateTime.now()) + '-' + carId.toString().substring(0, 8) + ".csv";
        Path path = directory.resolve(fileName);
        return create(path);
    }

    static PhysicsTelemetryLogger create(Path path) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return new PhysicsTelemetryLogger(path, writer);
    }

    Path path() {
        return path;
    }

    long samples() {
        return samples;
    }

    void append(CarSample car, WheelSample fl, WheelSample fr, WheelSample rl, WheelSample rr) throws IOException {
        append(cache, car.tick());
        append(cache, car.speedKmh());
        append(cache, car.velocityLong());
        append(cache, car.velocityLat());
        append(cache, car.yawRate());
        append(cache, car.steeringInput());
        append(cache, car.steerDegrees());
        append(cache, car.throttle());
        append(cache, car.brake());
        append(cache, car.brakeDirection());
        append(cache, car.longitudinalLoadTransfer());
        append(cache, car.frontYawMoment());
        append(cache, car.rearYawMoment());
        append(cache, car.downforce());
        append(cache, car.absEnabled());
        append(cache, car.tcEnabled());
        append(cache, car.absEnvelope());
        append(cache, car.tcEnvelope());
        append(cache, fl);
        append(cache, fr);
        append(cache, rl);
        appendLast(cache, rr);
        cache.append('\n');
        samples++;
        if (cache.length() >= CACHE_FLUSH_CHARS) {
            flushCache(false);
        }
    }

    void flush() throws IOException {
        flushCache(true);
    }

    private void flushCache(boolean flushWriter) throws IOException {
        if (!cache.isEmpty()) {
            writer.write(cache.toString());
            cache.setLength(0);
        }
        if (flushWriter) {
            writer.flush();
        }
    }

    @Override
    public void close() throws IOException {
        flushCache(false);
        writer.close();
    }

    private static String wheelHeader(String wheel) {
        return wheel + "_load_n," + wheel + "_mu_long," + wheel + "_mu_lat," + wheel + "_long_limit_n," + wheel + "_lat_limit_n,"
            + wheel + "_raw_long_request_n," + wheel + "_assisted_long_request_n," + wheel + "_raw_lateral_state_n," + wheel + "_long_force_n," + wheel + "_lat_force_n," + wheel + "_demand," + wheel + "_slip_angle_deg,"
            + wheel + "_angular_speed_radps," + wheel + "_surface_temp_c," + wheel + "_carcass_temp_c";
    }

    private static void append(StringBuilder target, WheelSample wheel) {
        append(target, wheel.load());
        append(target, wheel.muLong());
        append(target, wheel.muLat());
        append(target, wheel.longLimit());
        append(target, wheel.latLimit());
        append(target, wheel.rawLongRequest());
        append(target, wheel.longRequest());
        append(target, wheel.rawLateralState());
        append(target, wheel.longForce());
        append(target, wheel.latForce());
        append(target, wheel.demand());
        append(target, wheel.slipAngleDegrees());
        append(target, wheel.angularSpeed());
        append(target, wheel.surfaceTemperature());
        append(target, wheel.carcassTemperature());
    }

    private static void appendLast(StringBuilder target, WheelSample wheel) {
        append(target, wheel.load());
        append(target, wheel.muLong());
        append(target, wheel.muLat());
        append(target, wheel.longLimit());
        append(target, wheel.latLimit());
        append(target, wheel.rawLongRequest());
        append(target, wheel.longRequest());
        append(target, wheel.rawLateralState());
        append(target, wheel.longForce());
        append(target, wheel.latForce());
        append(target, wheel.demand());
        append(target, wheel.slipAngleDegrees());
        append(target, wheel.angularSpeed());
        append(target, wheel.surfaceTemperature());
        target.append(Double.toString(wheel.carcassTemperature()));
    }

    private static void append(StringBuilder target, double value) {
        target.append(Double.toString(value)).append(',');
    }

    private static void append(StringBuilder target, long value) {
        target.append(value).append(',');
    }

    private static void append(StringBuilder target, boolean value) {
        target.append(value ? '1' : '0').append(',');
    }

    record CarSample(long tick, double speedKmh, double velocityLong, double velocityLat,
                     double yawRate, double steeringInput, double steerDegrees, double throttle, double brake,
                     double brakeDirection, double longitudinalLoadTransfer,
                     double frontYawMoment, double rearYawMoment, double downforce,
                     boolean absEnabled, boolean tcEnabled,
                     double absEnvelope, double tcEnvelope) {
    }

    record WheelSample(double load, double muLong, double muLat, double longLimit, double latLimit,
                       double rawLongRequest, double longRequest, double rawLateralState,
                       double longForce, double latForce, double demand,
                       double slipAngleDegrees, double angularSpeed, double surfaceTemperature,
                       double carcassTemperature) {
    }
}
