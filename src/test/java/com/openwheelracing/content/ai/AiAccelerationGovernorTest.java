package com.openwheelracing.content.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiAccelerationGovernorTest {
    @Test
    void reachesStraightTargetWhenActualPowerIsLowerThanGripPlannerAssumes() {
        AiAccelerationGovernor governor = new AiAccelerationGovernor();
        double speed = 0;
        // A fixed 4/20 throttle cap would settle around 105 km/h in this drivetrain.
        for (int tick = 0; tick < 800; tick++) {
            var command = governor.update(new BasicAiDriveCommand(0.2f, 0, 0), speed, 200.0 / 3.6, 4, tick);
            double acceleration = command.throttle() * 12.0 - 0.0028 * speed * speed;
            speed = Math.max(0, speed + acceleration * 0.05);
        }
        assertTrue(speed * 3.6 > 190, "speed=" + speed * 3.6);
        assertTrue(speed * 3.6 < 205);
    }

    @Test
    void preservesBrakingAndFullStopCommands() {
        AiAccelerationGovernor governor = new AiAccelerationGovernor();
        governor.update(new BasicAiDriveCommand(1, 0, 0), 50, 55, 4, 1);
        assertEquals(1.0f, governor.update(new BasicAiDriveCommand(0, 1, 0), 50, 20, 4, 2).brake());
        assertEquals(1.0f, governor.update(new BasicAiDriveCommand(0, 0, 0), 2, 0, 1, 3).brake());
    }
}
