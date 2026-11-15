package com.openwheelracing.content.track;

import java.util.Random;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LapCaptureGeometryStressTest {
    private static final long SEED = 0x51A7E570L;

    @Test void sweptCrossingsSurviveWorldCoordinatesHeadingsSpeedsAndTickPhases() {
        Random random = new Random(SEED);
        for (int i = 0; i < 50_000; i++) {
            double cx = random.nextDouble(-29_000_000, 29_000_000);
            double cz = random.nextDouble(-29_000_000, 29_000_000);
            double heading = random.nextDouble(-Math.PI, Math.PI);
            double nx = Math.cos(heading), nz = Math.sin(heading), tx = -nz, tz = nx;
            double width = random.nextDouble(4, 40);
            double lateral = random.nextDouble(-width * 0.49, width * 0.49);
            double speed = random.nextDouble(0.05, 6); // 3.6 to 432 km/h at 20 Hz.
            double fraction = random.nextDouble(0.001, 0.999);
            double y = random.nextDouble(-60, 300);
            var left = new TrackDefinition.Point3(cx - tx * width / 2, y, cz - tz * width / 2);
            var right = new TrackDefinition.Point3(cx + tx * width / 2, y + 1, cz + tz * width / 2);
            Vec3 crossing = new Vec3(cx + tx * lateral, y + random.nextDouble(0, 2), cz + tz * lateral);
            Vec3 before = crossing.add(-nx * speed * fraction, -0.1, -nz * speed * fraction);
            Vec3 after = crossing.add(nx * speed * (1 - fraction), 0.1, nz * speed * (1 - fraction));
            int sample = i;
            var hit = TrackGeometry.crossing(before, after, left, right)
                .orElseThrow(() -> new AssertionError("Missed sample " + sample + ", seed=" + SEED));
            assertEquals(fraction, hit.movementT(), 1e-6, "seed=" + SEED + " sample=" + i);
            assertEquals(0.5 + lateral / width, hit.lineT(), 1e-6);
            assertTrue(TrackGeometry.crossing(before, before.add(-nx * speed, 0, -nz * speed), left, right).isEmpty(),
                "Moving away on the approach side must not become a crossing");
        }
    }
}
