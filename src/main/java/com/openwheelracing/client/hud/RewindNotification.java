package com.openwheelracing.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Brief private banner with a single fading tint; no camera movement or repeated flashing. */
public final class RewindNotification {
    private static long shownAt;
    private static double seconds;
    private static boolean preview;
    private static int status = com.openwheelracing.network.VehicleRollbackNetwork.REWOUND;
    private static String displacement = "";
    private static int vehicleId = -1;

    private RewindNotification() {}

    public static void show(double rewindSeconds, boolean isPreview) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getVehicle() == null) return;
        status = com.openwheelracing.network.VehicleRollbackNetwork.REWOUND;
        displacement = "";
        seconds = rewindSeconds;
        preview = isPreview;
        vehicleId = mc.player.getVehicle().getId();
        shownAt = System.nanoTime();
    }

    public static void status(int value) {
        show(0, false);
        status = value;
    }

    public static void displacement(net.minecraft.world.phys.Vec3 moved, float yaw) {
        var forward = net.minecraft.world.phys.Vec3.directionFromRotation(0, yaw);
        var right = new net.minecraft.world.phys.Vec3(forward.z, 0, -forward.x);
        double side = -moved.dot(right), ahead = -moved.dot(forward);
        String direction = Math.abs(side) > Math.abs(ahead) ? (side > 0 ? ">" : "<")
            : (ahead > 0 ? "^" : "v");
        displacement = direction + " " + String.format(java.util.Locale.ROOT, "%.1f m", moved.horizontalDistance());
    }

    public static void clear() { shownAt = 0; }

    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (shownAt == 0 || mc.player == null || mc.player.getVehicle() == null
                || mc.player.getVehicle().getId() != vehicleId) {
            clear();
            return;
        }
        double age = (System.nanoTime() - shownAt) / 1_000_000_000.0;
        double duration = status == com.openwheelracing.network.VehicleRollbackNetwork.CHECKING ? 60.0
            : status == com.openwheelracing.network.VehicleRollbackNetwork.BLOCKED ? 6.0 : 2.0;
        if (age > duration) { clear(); return; }
        if (mc.options.hideGui) return;
        int w = graphics.guiWidth();
        int y = Math.max(28, graphics.guiHeight() / 4);
        int tint = status == com.openwheelracing.network.VehicleRollbackNetwork.REWOUND
            ? (int) (55 * Math.max(0.0, 1.0 - age / 0.35)) : 0;
        if (tint > 0) graphics.fill(0, 0, w, graphics.guiHeight(), (tint << 24) | 0x55CCFF);
        int alpha = (int) (255 * Math.min(1.0, (duration - age) / 0.4));
        if (status == com.openwheelracing.network.VehicleRollbackNetwork.REWOUND) {
            graphics.fill(0, y - 8, w, y + 38, (Math.min(alpha, 220) << 24) | 0x990D20);
        } else {
            y = Math.max(8, graphics.guiHeight() - 110);
            graphics.fill(w / 2 - 115, y - 5, w / 2 + 115,
                y + (status == com.openwheelracing.network.VehicleRollbackNetwork.BLOCKED ? 32 : 14),
                (Math.min(alpha, 220) << 24) | 0x463100);
        }
        String key = switch (status) {
            case com.openwheelracing.network.VehicleRollbackNetwork.CHECKING -> "checking";
            case com.openwheelracing.network.VehicleRollbackNetwork.CONFIRMED -> "confirmed";
            case com.openwheelracing.network.VehicleRollbackNetwork.UNVERIFIED -> "unverified";
            case com.openwheelracing.network.VehicleRollbackNetwork.SYNC -> "resynced";
            case com.openwheelracing.network.VehicleRollbackNetwork.BLOCKED -> "blocked";
            default -> preview ? "preview" : "title";
        };
        Component title = Component.translatable("hud.openwheelracing.rewind." + key);
        Component detail = status == com.openwheelracing.network.VehicleRollbackNetwork.REWOUND
            ? Component.translatable("hud.openwheelracing.rewind.detail",
                String.format(java.util.Locale.ROOT, "%.2f", seconds))
            : status == com.openwheelracing.network.VehicleRollbackNetwork.BLOCKED
                ? Component.translatable("hud.openwheelracing.rewind.blocked_detail") : Component.empty();
        graphics.drawCenteredString(mc.font, title, w / 2, y, (alpha << 24) | 0xFFFFFF);
        graphics.drawCenteredString(mc.font, detail, w / 2, y + 17, (alpha << 24) | 0xFFFFFF);
        if (!displacement.isEmpty()) graphics.drawString(mc.font, displacement, w / 2 + 95, y + 17,
            (alpha << 24) | 0xAAEEFF);
    }
}
