package com.openwheelracing.client.hud;

import com.openwheelracing.client.render.SurveyRouteOverlay;
import com.openwheelracing.content.track.survey.SurveyRouteLocalizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;

import java.util.Locale;

public final class SurveyRouteHud {
    private SurveyRouteHud() {}

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!SurveyRouteOverlay.visible() || mc.options.hideGui || mc.player == null) return;
        int y = 8;
        if (SurveyRouteOverlay.recording() && (mc.level.getGameTime() / 8L & 1L) == 0L) {
            String warning = "SURVEY RECORDING";
            int warningX = (graphics.guiWidth() - mc.font.width(warning)) / 2;
            graphics.drawString(mc.font, warning, warningX, 28, 0xFFFF3030, true);
        }
        String title = SurveyRouteOverlay.recording() ? "SURVEY REC " : SurveyRouteOverlay.nodeCount() == 0 ? "SURVEY ARMED " : "SURVEY ";
        graphics.drawString(mc.font, title + SurveyRouteOverlay.trackName(), 8, y, 0xFFFFFFFF, true);
        y += 11;
        graphics.drawString(mc.font, "raw=" + SurveyRouteOverlay.rawCount() + " nodes=" + SurveyRouteOverlay.nodeCount() + " length=" + Math.round(SurveyRouteOverlay.length()) + "m", 8, y, 0xFFB8C2CC, true);
        SurveyRouteLocalizer.Result result = SurveyRouteOverlay.localization();
        if (result == null) return;
        y += 11;
        int color = switch (result.status()) {
            case TRACKED -> 0xFF34D058;
            case LOW_CONFIDENCE -> 0xFFFFD044;
            case AMBIGUOUS -> 0xFFD65CFF;
            case UNTRACKED -> 0xFFDA1A20;
        };
        String detail = result.best().map(candidate -> String.format(Locale.ROOT, "%s s=%.1fm lat=%+.1fm h=%.1fm conf=%.0f%%",
            result.status(), candidate.distanceAlongRoute(), candidate.signedLateralDistance(), Math.abs(candidate.verticalDelta()), result.confidence() * 100.0)).orElse(result.status().name());
        graphics.drawString(mc.font, detail, 8, y, color, true);
        if (result.second().isPresent()) {
            var candidate = result.second().get();
            graphics.drawString(mc.font, String.format(Locale.ROOT, "candidate 2: s=%.1fm segment=%d", candidate.distanceAlongRoute(), candidate.segmentIndex()), 8, y + 11, 0xFFB67CFF, true);
        }
    }
}
