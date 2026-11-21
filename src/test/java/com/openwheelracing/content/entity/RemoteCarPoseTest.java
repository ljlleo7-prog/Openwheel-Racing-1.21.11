package com.openwheelracing.content.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RemoteCarPoseTest {
    @Test
    void renderAndCollisionSamplesAgreeWithoutAdvancingOnRepeatedQueries() {
        var poses = new RemoteCarPose();
        poses.sample(0, 0, 0, 0, 0);
        poses.sample(0, 4, 2, -2, 90);
        var rendered = poses.sample(25_000_000, 4, 2, -2, 90);
        var collision = poses.sample(25_000_000, 4, 2, -2, 90);
        assertEquals(rendered, collision);
        assertEquals(new RemoteCarPose.Pose(2, 1, -1, 45), collision);
    }

    @Test
    void packetBetweenRenderFramesRetargetsFromVisiblePoseWithoutJump() {
        var poses = new RemoteCarPose();
        poses.sample(0, 0, 0, 0, 0);
        poses.sample(0, 4, 0, 0, 90);
        var visible = poses.sample(20_000_000, 4, 0, 0, 90);
        assertEquals(visible, poses.sample(20_000_000, -4, 1, 0, -90));
        assertEquals(visible, poses.sample(20_000_000, -5, 2, 0, -120));
        assertEquals(-5, poses.sample(70_000_000, -5, 2, 0, -120).x());
    }

    @Test
    void renderFrameRateAndRepeatedUnchangedTicksDoNotRestartBlend() {
        var sparse = new RemoteCarPose();
        var frequent = new RemoteCarPose();
        for (var poses : new RemoteCarPose[] {sparse, frequent}) {
            poses.sample(0, 0, 0, 0, 0);
            poses.sample(0, 5, 0, 0, 0);
        }
        for (long time = 1_000_000; time < 50_000_000; time += 1_000_000)
            frequent.sample(time, 5, 0, 0, 0);
        assertEquals(sparse.sample(50_000_000, 5, 0, 0, 0), frequent.sample(50_000_000, 5, 0, 0, 0));
        assertEquals(5, sparse.sample(50_000_000, 5, 0, 0, 0).x());
    }

    @Test
    void staleUpdatesConvergeAndNeverExtrapolateIntoAnotherCar() {
        var poses = new RemoteCarPose();
        poses.sample(0, 0, 0, 0, 0);
        poses.sample(0, 5, 3, 2, 90);
        assertEquals(new RemoteCarPose.Pose(5, 3, 2, 90), poses.sample(5_000_000_000L, 5, 3, 2, 90));
    }

    @Test
    void teleportSnapsBothConsumersAndCannotLeaveOldCollider() {
        var poses = new RemoteCarPose();
        poses.sample(0, 0, 0, 0, 0);
        var teleported = new RemoteCarPose.Pose(100, 20, -10, 180);
        assertEquals(teleported, poses.sample(1, 100, 20, -10, 180));
        assertEquals(teleported, poses.sample(1, 100, 20, -10, 180));
    }

    @Test
    void burstCannotAccumulateOffsetBeyondBroadPhaseBound() {
        var poses = new RemoteCarPose();
        poses.sample(0, 0, 0, 0, 0);
        poses.sample(0, 10, 0, 0, 0);
        assertEquals(20, poses.sample(0, 20, 0, 0, 0).x());
    }

    @Test
    void yawWrapsAlongShortestArcAndResetRemovesPreviousDriverPose() {
        var poses = new RemoteCarPose();
        poses.sample(0, 0, 0, 0, 179);
        poses.sample(0, 0, 0, 0, -179);
        assertEquals(180, poses.sample(25_000_000, 0, 0, 0, -179).yaw());
        poses.clear();
        assertEquals(new RemoteCarPose.Pose(2, 3, 4, -90), poses.sample(25_000_000, 2, 3, 4, -90));
    }
}
