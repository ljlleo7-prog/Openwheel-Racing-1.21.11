package com.openwheelracing.content.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Follows the assigned predecessor, including across the route seam; missing/inverted order holds. */
public final class AiFormationOrder {
    private AiFormationOrder() { }

    public static double gap(UUID driver, List<UUID> order, Map<UUID, Double> distances, double length) {
        int index = order.indexOf(driver);
        if (index == 0) return Double.POSITIVE_INFINITY;
        if (index < 0 || !distances.containsKey(driver) || !distances.containsKey(order.get(index - 1)) || !(length > 0.0)) return 0.0;
        if (!Double.isFinite(length) || !Double.isFinite(distances.get(driver)) || !Double.isFinite(distances.get(order.get(index - 1)))) return 0.0;
        double gap = SurveyRouteSampler.forwardDelta(distances.get(driver), distances.get(order.get(index - 1)), length);
        return gap <= length * 0.5 ? gap : 0.0;
    }
}
