package com.openwheelracing.client.hud;

import com.openwheelracing.network.OWRNetwork;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class LapDeltaClient {
    public static final int STATUS_UNREACHED = OWRNetwork.TIMING_STATUS_UNREACHED;
    public static final int STATUS_SLOWER = OWRNetwork.TIMING_STATUS_SLOWER;
    public static final int STATUS_PERSONAL_BEST = OWRNetwork.TIMING_STATUS_PERSONAL_BEST;
    public static final int STATUS_SESSION_BEST = OWRNetwork.TIMING_STATUS_SESSION_BEST;

    private static final int FLASH_TICKS = 60;

    private static volatile int segmentCount;
    private static volatile List<Integer> statuses = List.of();
    private static volatile String flashLabel = "";
    private static volatile int flashTicks;
    private static volatile int lastSegmentIndex = -1;
    private static volatile int cumulativeDeltaMillis;
    private static volatile int miniDeltaMillis;

    private LapDeltaClient() {
    }

    public static void reset(int count) {
        segmentCount = Math.max(0, count);
        statuses = java.util.Collections.nCopies(segmentCount, STATUS_UNREACHED);
        flashLabel = "";
        flashTicks = 0;
        lastSegmentIndex = -1;
        cumulativeDeltaMillis = 0;
        miniDeltaMillis = 0;
    }

    public static void update(int count, List<Integer> nextStatuses, String label, int segmentIndex, int cumulativeDelta, int miniDelta) {
        segmentCount = Math.max(0, count);
        java.util.ArrayList<Integer> merged = new java.util.ArrayList<>(segmentCount);
        for (int index = 0; index < segmentCount; index++) {
            int next = index < nextStatuses.size() ? nextStatuses.get(index) : STATUS_UNREACHED;
            int previous = index < statuses.size() ? statuses.get(index) : STATUS_UNREACHED;
            merged.add(next == STATUS_UNREACHED && previous != STATUS_UNREACHED ? previous : next);
        }
        statuses = List.copyOf(merged);
        flashLabel = label == null ? "" : label;
        flashTicks = flashLabel.isBlank() ? 0 : FLASH_TICKS;
        lastSegmentIndex = segmentIndex;
        cumulativeDeltaMillis = cumulativeDelta;
        miniDeltaMillis = miniDelta;
    }

    public static void tick(boolean active) {
        if (!active) {
            clear();
            return;
        }
        if (flashTicks > 0) {
            flashTicks--;
        }
    }

    public static void clear() {
        segmentCount = 0;
        statuses = List.of();
        flashLabel = "";
        flashTicks = 0;
        lastSegmentIndex = -1;
        cumulativeDeltaMillis = 0;
        miniDeltaMillis = 0;
    }

    public static int segmentCount() {
        return segmentCount;
    }

    public static List<Integer> statuses() {
        return statuses;
    }

    public static int hitCount() {
        int count = 0;
        for (int status : statuses) {
            if (status != STATUS_UNREACHED) {
                count++;
            }
        }
        return count;
    }

    public static String flashLabel() {
        return flashTicks > 0 ? flashLabel : "";
    }

    public static int lastSegmentIndex() {
        return lastSegmentIndex;
    }

    public static int cumulativeDeltaMillis() {
        return cumulativeDeltaMillis;
    }

    public static int miniDeltaMillis() {
        return miniDeltaMillis;
    }
}
