package com.openwheelracing.content.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiGridNavigatorTest {
    private static final AiGridApproach.Target SLOT = new AiGridApproach.Target(0, 64, 3, 0);

    @Test
    void bahrainGridApproachClimbsSurveyRiseAndParksWithMeasuredThrottleFeedback() {
        var navigator = new AiGridNavigator();
        var governor = new AiAccelerationGovernor();
        var goal = new AiGridApproach.Target(68.0015, -57, 599.4693, -1.56626256);
        double x = 66.6556, z = 694.9215, heading = -1.52522767, speed = 0;
        boolean arrived = false;
        for (int tick = 0; tick < 1_800; tick++) {
            double y = bahrainFloor(z);
            var guidance = navigator.update(goal, x, y, z, heading, speed, tick, 64, (px, pz, ph) -> {
                if (px < 57 || px > 81 || pz < 590 || pz > 710) return false;
                double[] support = {bahrainFloor(pz - 2.6), bahrainFloor(pz), bahrainFloor(pz + 2.6)};
                return Double.isFinite(AiPavedFootprint.clearanceFloor(support));
            });
            var intent = new BasicAiDriveCommand(guidance.speed() > speed ? 0.1f : 0, guidance.speed() + 0.1 < speed ? 0.15f : 0, guidance.steering());
            var command = governor.update(intent, speed, guidance.speed(), 1.0, tick);
            speed = Math.max(0, speed + (command.throttle() * 12.0 - command.brake() * 20.0 - speed * speed * 0.0028) * 0.05);
            heading += speed * Math.tan(command.steering() * Math.toRadians(34)) / 3.6 * 0.05;
            x += Math.cos(heading) * speed * 0.05;
            z += Math.sin(heading) * speed * 0.05;
            if (AiGridApproach.evaluate(goal, x, bahrainFloor(z), z, heading, speed).ready()) { arrived = true; break; }
        }
        assertTrue(arrived, "position=" + x + "," + z + " speed=" + speed);
    }

    private static double bahrainFloor(double z) { return z > 650 ? -58 : z > 630 ? -57.5 : -57; }

    @Test
    void findsPathFromFinalHundredMetresToOffsetGridBox() {
        var search = new AiGridPathSearch(new AiGridPathSearch.Pose(-100, 0, 0), SLOT, (x, z, h) -> Math.abs(z) < 10);
        while (!search.done()) assertTrue(search.advance(64) <= 64);
        assertFalse(search.path().isEmpty());
        var end = search.path().getLast();
        assertEquals(SLOT.x(), end.x());
        assertEquals(SLOT.z(), end.z());
        assertEquals(SLOT.heading(), end.heading());
    }

    @Test
    void searchesAroundBlockedDirectPathWithBoundedExpansionBudget() {
        var search = new AiGridPathSearch(new AiGridPathSearch.Pose(-80, 0, 0), SLOT,
            (x, z, h) -> Math.abs(z) < 24 && !(x > -50 && x < -35 && Math.abs(z) < 4));
        int expanded = 0;
        while (!search.done()) expanded += search.advance(64);
        assertTrue(expanded <= 12_000);
        assertFalse(search.path().isEmpty());
        assertTrue(search.path().stream().anyMatch(point -> Math.abs(point.z()) > 4));
    }

    @Test
    void unreachableGridHoldsAndRetriesWithoutUnsafeStraightLineDrive() {
        var navigator = new AiGridNavigator();
        var result = navigator.update(SLOT, -80, 64, 0, 0, 0, 0, 64, (x, z, h) -> false);
        assertTrue(result.blocked());
        assertEquals(0, result.speed());
        var retry = navigator.update(SLOT, -80, 64, 0, 0, 0, 101, 64, (x, z, h) -> true);
        assertFalse(retry.blocked());
        assertTrue(retry.speed() > 0);
    }

    @Test
    void kinematicFollowerReachesGridInsteadOfParkingAtFormationHandover() {
        var navigator = new AiGridNavigator();
        double x = -100, z = 0, heading = 0, speed = 0;
        boolean arrived = false;
        for (int tick = 0; tick < 1_000; tick++) {
            var guidance = navigator.update(SLOT, x, 64, z, heading, speed, tick, 64, (px, pz, ph) -> Math.abs(pz) < 12);
            speed += Math.clamp(guidance.speed() - speed, -4.0 * 0.05, 1.0 * 0.05);
            heading += speed * Math.tan(guidance.steering() * Math.toRadians(34)) / 3.6 * 0.05;
            x += Math.cos(heading) * speed * 0.05;
            z += Math.sin(heading) * speed * 0.05;
            if (AiGridApproach.evaluate(SLOT, x, 64, z, heading, speed).ready()) { arrived = true; break; }
        }
        assertTrue(arrived, "position=" + x + "," + z + " heading=" + heading + " speed=" + speed);
    }
}
