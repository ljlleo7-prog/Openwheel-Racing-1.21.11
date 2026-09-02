package com.openwheelracing.content.race;

/** Pure route arithmetic used by the server-authoritative auto-yellow service. */
public final class RaceAutoFlagLogic {
    private RaceAutoFlagLogic() { }

    public static double physicalScore(double horizontalDistance, double verticalDelta) {
        return horizontalDistance * horizontalDistance + verticalDelta * verticalDelta;
    }

    public static boolean isWithinRouteEnvelope(double horizontalDistance, double verticalDelta,
                                                 double horizontalLimit, double verticalLimit) {
        return horizontalDistance <= horizontalLimit && Math.abs(verticalDelta) <= verticalLimit;
    }

    public static boolean isUpstreamWithin(double lightDistance, double hazardDistance, double routeLength, double warningDistance) {
        if (!(routeLength > 0.0) || warningDistance < 0.0) return false;
        long lengthMillimetres = Math.max(1L, Math.round(routeLength * 1000.0));
        long difference = Math.round((hazardDistance - lightDistance) * 1000.0);
        double downstream = Math.floorMod(difference, lengthMillimetres) / 1000.0;
        return downstream <= warningDistance;
    }
}
