package com.openwheelracing.content.entity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Build-definition check, not remote proof against a client that falsifies its responses. */
public final class PerformanceRules {
    private static String fingerprint;

    private PerformanceRules() {}

    public static synchronized String fingerprint() {
        if (fingerprint != null) return fingerprint;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var index = PerformanceRules.class.getResourceAsStream("/openwheelracing-performance-index.txt")) {
                if (index == null) throw new IOException("Missing performance definition index");
                String[] paths = new String(index.readAllBytes(), StandardCharsets.UTF_8).strip().split("\\R");
                if (paths.length == 0 || paths[0].isEmpty()) throw new IOException("Empty performance definition index");
                for (String path : paths) {
                    try (var input = PerformanceRules.class.getResourceAsStream("/" + path)) {
                        if (input == null) throw new IOException("Missing performance definition " + path);
                        digest.update(path.getBytes(StandardCharsets.UTF_8));
                        digest.update(input.readAllBytes());
                    }
                }
            }
            return fingerprint = HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to inspect performance definitions", exception);
        }
    }

    public static String probe(int preset) {
        var result = VehiclePhysics.resolvePlanarCollision(3, 1, 800, -2, 0.5, 1200, 1, 0, 0.15);
        return preset + ":" + Long.toHexString(Double.doubleToLongBits(result.firstVelocityX()))
            + ":" + Long.toHexString(Double.doubleToLongBits(result.secondVelocityX()));
    }

    /** Legal control preferences. Engine/grip/aero definitions are checked by fingerprint. */
    public static boolean legalControls(double[] state) {
        if (state.length < 55 || state.length > 128) return false;
        for (double value : state) if (!Double.isFinite(value)) return false;
        double[] minimum = {.5,.5,.5,.5,.7,.5,.6,0,.90,.90,0,0,0,0};
        double[] maximum = {2,2,2,2,1.3,1.3,1.4,1,1.10,1.10,1,1.5,1.5,1.5};
        for (int i = 0; i < minimum.length; i++) {
            if (state[i] < minimum[i] - 1e-6 || state[i] > maximum[i] + 1e-6) return false;
        }
        if (state.length >= 70) {
            for (int i = 66; i < 70; i++) if (state[i] < 0 || state[i] > 100) return false;
        }
        return true;
    }
}
