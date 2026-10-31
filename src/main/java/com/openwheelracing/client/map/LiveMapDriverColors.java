package com.openwheelracing.client.map;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Session-stable identities; team liveries never participate in color selection. */
public final class LiveMapDriverColors {
    private final Map<UUID, Identity> identities = new HashMap<>();
    private final Set<Integer> usedColors = new HashSet<>();

    public Identity identity(UUID driver) {
        return identities.computeIfAbsent(driver, ignored -> {
            int number = identities.size() + 1;
            int candidate = number - 1;
            int color;
            do {
                color = color(candidate++);
            } while (!usedColors.add(color));
            return new Identity(number, color);
        });
    }

    public void clear() {
        identities.clear();
        usedColors.clear();
    }

    private static int color(int index) {
        double hue = ((index * 0.618033988749895) % 1.0) * 6.0;
        double value = index % 2 == 0 ? 1.0 : 0.85;
        double saturation = index % 3 == 0 ? 0.55 : 0.85;
        double chroma = value * saturation;
        double secondary = chroma * (1.0 - Math.abs(hue % 2.0 - 1.0));
        double base = value - chroma;
        double red = 0, green = 0, blue = 0;
        switch ((int) hue) {
            case 0 -> { red = chroma; green = secondary; }
            case 1 -> { red = secondary; green = chroma; }
            case 2 -> { green = chroma; blue = secondary; }
            case 3 -> { green = secondary; blue = chroma; }
            case 4 -> { red = secondary; blue = chroma; }
            default -> { red = chroma; blue = secondary; }
        }
        return 0xFF000000 | ((int) Math.round((red + base) * 255) << 16)
            | ((int) Math.round((green + base) * 255) << 8) | (int) Math.round((blue + base) * 255);
    }

    public record Identity(int number, int color) {}
}
