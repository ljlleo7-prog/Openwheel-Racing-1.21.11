package com.openwheelracing.content.race;

import com.openwheelracing.content.track.TrackDefinition;
import com.openwheelracing.content.track.TrackGeometry;
import com.openwheelracing.content.track.survey.SurveyRouteLocalizer;
import com.openwheelracing.content.track.survey.SurveyRouteModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Accuracy experiment against the existing 20 Hz geometric clock, not just self-consistency. */
class LapRecoveryAccuracyTest {
    @Test void benchmarkSurveySeamTimingAcrossSamplingRatesAndFinishGeometries() throws Exception {
        List<String> report = new ArrayList<>();
        report.add("shape,sample_ticks,speed_kmh,lateral_m,line_skew_degrees,acceleration_mps2,trials,recoveries,mean_abs_error_ms,p95_abs_error_ms,max_abs_error_ms,bias_ms");
        double straightMax = 0, overallMax = 0;
        int trials = 0;
        for (String shape : List.of("straight", "curve_r30", "curve_r100")) {
            SurveyRouteModel route = route(shape);
            for (int sampling : new int[] {1, 2, 5, 10}) for (double speed : new double[] {50, 100, 200, 360})
                for (double lateral : new double[] {-3, 0, 3}) for (double skew : new double[] {0, 15})
                    for (double acceleration : new double[] {0, 5}) {
                        List<Double> errorList = new ArrayList<>();
                        for (int phase = 0; phase < 64; phase++) {
                            double duration = sampling / 20.0;
                            double beforeTime = -(phase + .5) / 64 * duration;
                            double afterTime = beforeTime + duration;
                            var state = new SurveyRouteLocalizer.State();
                            SurveyRouteLocalizer.Result previous = null;
                            Vec3 previousPosition = null;
                            double previousTime = 0, recovered = Double.NaN;
                            for (int offset = -2; offset <= 2; offset++) {
                                double time = beforeTime + offset * duration;
                                Vec3 position = position(shape, speed / 3.6, lateral, acceleration, time);
                                var current = SurveyRouteLocalizer.locate(route, point(position), heading(shape, speed / 3.6, lateral, acceleration, time), state);
                                if (previous != null && previous.best().isPresent() && current.best().isPresent()
                                    && previous.status().ordinal() <= 1 && current.status().ordinal() <= 1) {
                                    double a = previous.best().orElseThrow().distanceAlongRoute();
                                    double b = current.best().orElseThrow().distanceAlongRoute();
                                    double delta = route.length() - a + b;
                                    double movement = position.distanceTo(previousPosition);
                                    if (a > route.length() * .9 && b < route.length() * .1
                                        && delta <= Math.min(route.length() / 4, Math.min(80, movement * 2 + 1))) {
                                        double candidate = previousTime + duration * (route.length() - a) / delta;
                                        if (!Double.isFinite(recovered) || Math.abs(candidate) < Math.abs(recovered)) recovered = candidate;
                                    }
                                }
                                previous = current; previousPosition = position; previousTime = time;
                            }
                            trials++;
                            if (!Double.isFinite(recovered)) continue;
                            double truth = trueCrossing(shape, speed / 3.6, lateral, skew, acceleration);
                            // Use the same underlying motion and tick phase, at the normal 20 Hz
                            // rate, to obtain the primary detector's interpolated reference time.
                            double tickBefore = beforeTime + Math.floor((truth - beforeTime) * 20) / 20.0;
                            Vec3 p = position(shape, speed / 3.6, lateral, acceleration, tickBefore);
                            Vec3 q = position(shape, speed / 3.6, lateral, acceleration, tickBefore + .05);
                            double radians = Math.toRadians(skew);
                            double radius = radius(shape);
                            TrackDefinition.Point3 left = shape.equals("straight")
                                ? new TrackDefinition.Point3(-20 * Math.sin(radians), 0, -20 * Math.cos(radians))
                                : new TrackDefinition.Point3(radius - 20 * Math.cos(radians), 0, -20 * Math.sin(radians));
                            TrackDefinition.Point3 right = shape.equals("straight")
                                ? new TrackDefinition.Point3(20 * Math.sin(radians), 0, 20 * Math.cos(radians))
                                : new TrackDefinition.Point3(radius + 20 * Math.cos(radians), 0, 20 * Math.sin(radians));
                            double fraction = TrackGeometry.crossing(p, q, left, right).orElseThrow().movementT();
                            double primary = tickBefore + .05 * fraction;
                            double error = (recovered - primary) * 1000;
                            errorList.add(error);
                            if (shape.equals("straight") && sampling == 1 && skew == 0) straightMax = Math.max(straightMax, Math.abs(error));
                            overallMax = Math.max(overallMax, Math.abs(error));
                        }
                        double bias = errorList.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
                        double[] absolute = errorList.stream().mapToDouble(Math::abs).sorted().toArray();
                        report.add(String.format(Locale.ROOT, "%s,%d,%.0f,%.0f,%.0f,%.0f,%d,%d,%.6f,%.6f,%.6f,%.6f", shape,
                            sampling, speed, lateral, skew, acceleration, 64, absolute.length, Arrays.stream(absolute).average().orElse(Double.NaN),
                            absolute.length == 0 ? Double.NaN : absolute[(int) Math.ceil(.95 * absolute.length) - 1],
                            absolute.length == 0 ? Double.NaN : absolute[absolute.length - 1], bias));
                    }
        }
        Path output = Path.of("build/reports/lap-recovery-accuracy.csv");
        Files.createDirectories(output.getParent());
        Files.write(output, report);
        System.out.printf(Locale.ROOT, "Recovery timing: %d trials; straight 20Hz perpendicular max %.6f ms; overall max %.6f ms%n", trials, straightMax, overallMax);
        assertTrue(straightMax < .001, "Straight perpendicular 20Hz recovery should agree with the primary interpolation");
        assertTrue(overallMax > 1, "The experiment must expose configurations that do not support millisecond qualifying precision");
    }

    @Test void movementBackedRecoveryMatchesThePrimaryClockAcrossFiftyThousandCrossings() throws Exception {
        Random random = new Random(0x51A7E570L);
        double maximumFractionError = 0;
        int maximumMillisError = 0;
        for (int i = 0; i < 50_000; i++) {
            double x = random.nextDouble(-29_000_000, 29_000_000), z = random.nextDouble(-29_000_000, 29_000_000);
            double angle = random.nextDouble(-Math.PI, Math.PI), nx = Math.cos(angle), nz = Math.sin(angle);
            double tx = -nz, tz = nx, width = random.nextDouble(4, 40);
            double lateral = random.nextDouble(-width * .49, width * .49);
            double distance = random.nextDouble(.05, 6), fraction = random.nextDouble(.001, .999);
            Vec3 p = new Vec3(x + tx * lateral - nx * distance * fraction, 64, z + tz * lateral - nz * distance * fraction);
            Vec3 q = new Vec3(x + tx * lateral + nx * distance * (1 - fraction), 64, z + tz * lateral + nz * distance * (1 - fraction));
            var left = new TrackDefinition.Point3(x - tx * width / 2, 63, z - tz * width / 2);
            var right = new TrackDefinition.Point3(x + tx * width / 2, 63, z + tz * width / 2);
            // Ordinary capture extends the finite line for car-body overlap; exact-backed
            // recovery uses the original endpoints and the very same committed X/Z segment.
            var extendedLeft = new TrackDefinition.Point3(left.x() - tx * 3, 63, left.z() - tz * 3);
            var extendedRight = new TrackDefinition.Point3(right.x() + tx * 3, 63, right.z() + tz * 3);
            double ordinary = TrackGeometry.crossing(p, q, extendedLeft, extendedRight).orElseThrow().movementT();
            double recovered = TrackGeometry.crossing(p.add(0, 2, 0), q.add(0, 2, 0), left, right).orElseThrow().movementT();
            maximumFractionError = Math.max(maximumFractionError, Math.abs(ordinary - recovered));
            double start = 2_800_000 - random.nextDouble(500, 2000);
            int originalMillis = LapTimingClock.elapsedMillis(start, LapTimingClock.crossingTime(2_800_000, ordinary));
            int recoveredMillis = LapTimingClock.elapsedMillis(start, LapTimingClock.crossingTime(2_800_000, recovered));
            maximumMillisError = Math.max(maximumMillisError, Math.abs(originalMillis - recoveredMillis));
        }
        assertTrue(maximumMillisError <= 1);
        assertTrue(maximumFractionError * 50 < .001, "Numeric intersection error must remain below one microsecond");
        Path output = Path.of("build/reports/lap-geometric-recovery-accuracy.csv");
        Files.createDirectories(output.getParent());
        Files.write(output, List.of("trials,max_saved_lap_difference_ms,max_boundary_difference_ms",
            "50000," + maximumMillisError + "," + maximumFractionError * 50));
    }

    private static double radius(String shape) { return shape.equals("curve_r30") ? 30 : 100; }
    private static SurveyRouteModel.Point point(Vec3 p) { return new SurveyRouteModel.Point(p.x, p.y, p.z); }
    private static Vec3 position(String shape, double speed, double lateral, double acceleration, double seconds) {
        double distance = speed * seconds + .5 * acceleration * seconds * seconds;
        if (shape.equals("straight")) return new Vec3(distance, 0, lateral);
        double r = radius(shape) + lateral;
        return new Vec3(r * Math.cos(distance / r), 0, r * Math.sin(distance / r));
    }
    private static double heading(String shape, double speed, double lateral, double acceleration, double time) {
        return shape.equals("straight") ? 0 : Math.PI / 2 + (speed * time + .5 * acceleration * time * time) / (radius(shape) + lateral);
    }
    private static double trueCrossing(String shape, double speed, double lateral, double skew, double acceleration) {
        double angle = Math.toRadians(skew);
        double distance = shape.equals("straight") ? lateral * Math.tan(angle)
            : (radius(shape) + lateral) * (angle - Math.asin(radius(shape) * Math.sin(angle) / (radius(shape) + lateral)));
        return acceleration == 0 ? distance / speed : 2 * distance / (speed + Math.sqrt(speed * speed + 2 * acceleration * distance));
    }
    private static SurveyRouteModel route(String shape) {
        List<SurveyRouteModel.Node> nodes = new ArrayList<>();
        double length;
        if (shape.equals("straight")) {
            double[][] xy = {{0, 0, 0}, {100, 0, 100}, {100, 200, 300}, {-100, 200, 500}, {-100, 0, 700}};
            for (int i = 0; i < xy.length; i++) nodes.add(new SurveyRouteModel.Node(i, new SurveyRouteModel.Point(xy[i][0], 0, xy[i][1]), 0, xy[i][2]));
            length = 800;
        } else {
            double r = radius(shape);
            int count = (int) Math.ceil(2 * Math.PI * r / 4);
            double step = 2 * r * Math.sin(Math.PI / count);
            length = count * step;
            for (int i = 0; i < count; i++) {
                double a = i * Math.PI * 2 / count;
                nodes.add(new SurveyRouteModel.Node(i, new SurveyRouteModel.Point(r * Math.cos(a), 0, r * Math.sin(a)), a + Math.PI / 2, i * step));
            }
        }
        return new SurveyRouteModel(UUID.randomUUID(), UUID.randomUUID(), List.of(), nodes, length, 4);
    }
}
