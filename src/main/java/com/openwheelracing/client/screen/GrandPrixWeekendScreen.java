package com.openwheelracing.client.screen;

import com.openwheelracing.content.race.weekend.GrandPrixWeekendInfo;
import com.openwheelracing.network.OWRNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Separate Race Director page so weekend operations never displace the existing monitor controls. */
public final class GrandPrixWeekendScreen extends Screen {
    private static final int WIDTH = 520;
    private static final int HEIGHT = 340;
    private static final int SESSION_ROWS = 12;
    private final RaceDirectorScreen parent;
    private GrandPrixWeekendInfo info;

    GrandPrixWeekendScreen(RaceDirectorScreen parent, GrandPrixWeekendInfo info) {
        super(Component.translatable("screen.openwheelracing.gp_weekend.title"));
        this.parent = parent;
        this.info = info == null ? GrandPrixWeekendInfo.empty() : info;
    }

    void applySnapshot(com.openwheelracing.content.race.RaceDirectorSnapshot snapshot) {
        parent.applySnapshotFromWeekend(snapshot);
        info = snapshot == null ? GrandPrixWeekendInfo.empty() : snapshot.grandPrixWeekend();
        rebuildWidgets();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
            .bounds(left + 12, top + HEIGHT - 28, 72, 18).build());
        if (!info.present()) {
            return;
        }
        addControl(left + 336, top + 72, "advance", "screen.openwheelracing.gp_weekend.advance");
        addControl(left + 424, top + 72, "stage", "screen.openwheelracing.gp_weekend.stage");
        addControl(left + 336, top + 94, "countdown", "screen.openwheelracing.gp_weekend.countdown");
        addControl(left + 424, top + 94, "start", "screen.openwheelracing.gp_weekend.start");
        addControl(left + 336, top + 126, "suspend", "screen.openwheelracing.gp_weekend.suspend");
        addControl(left + 424, top + 126, "resume", "screen.openwheelracing.gp_weekend.resume");
        addControl(left + 336, top + 158, "finish", "screen.openwheelracing.gp_weekend.finish");
        addControl(left + 424, top + 158, "provisional", "screen.openwheelracing.gp_weekend.provisional");
        addControl(left + 336, top + 180, "official", "screen.openwheelracing.gp_weekend.official");
        addControl(left + 424, top + 180, "complete", "screen.openwheelracing.gp_weekend.complete");
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.refresh"), button ->
            OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorRefreshSessionMessage()))
            .bounds(left + WIDTH - 84, top + HEIGHT - 28, 72, 18).build());
    }

    private void addControl(int x, int y, String action, String translationKey) {
        Button button = Button.builder(Component.translatable(translationKey), ignored -> {
            OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorGrandPrixControlMessage(info.eventName(), action));
        }).bounds(x, y, 80, 18).build();
        button.active = info.can(action);
        addRenderableWidget(button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xF21F2328);
        graphics.fill(left + 6, top + 6, left + WIDTH - 6, top + 54, 0xFF2F3640);
        graphics.fill(left + 10, top + 64, left + 324, top + HEIGHT - 38, 0xFF252B32);
        graphics.fill(left + 330, top + 64, left + WIDTH - 10, top + 216, 0xFF252B32);
        graphics.drawCenteredString(font, title, left + WIDTH / 2, top + 12, 0xFFFFFFFF);
        if (!info.present()) {
            graphics.drawCenteredString(font, Component.translatable("screen.openwheelracing.gp_weekend.none"),
                left + WIDTH / 2, top + 90, 0xFF9AA6B2);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        graphics.drawCenteredString(font, info.eventName(), left + WIDTH / 2, top + 27, 0xFF79C0FF);
        graphics.drawCenteredString(font, info.eventState() + "  •  " + info.entryCount() + " entries",
            left + WIDTH / 2, top + 40, 0xFFAEBCCC);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.gp_weekend.schedule"), left + 18, top + 70, 0xFFE8EDF2, false);
        drawSchedule(graphics, left, top);
        drawActiveSession(graphics, left, top);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawSchedule(GuiGraphics graphics, int left, int top) {
        List<GrandPrixWeekendInfo.SessionInfo> sessions = info.sessions().stream().limit(SESSION_ROWS).toList();
        for (int index = 0; index < sessions.size(); index++) {
            GrandPrixWeekendInfo.SessionInfo session = sessions.get(index);
            int y = top + 86 + index * 18;
            boolean active = index == info.activeSessionIndex();
            if (active) graphics.fill(left + 14, y - 3, left + 320, y + 12, 0x663B82B8);
            int color = active ? 0xFFFFFFFF : session.official() ? 0xFF7EE787 : 0xFFC9D1D9;
            graphics.drawString(font, (index + 1) + ". " + fit(session.name(), 128), left + 20, y, color, false);
            graphics.drawString(font, session.type(), left + 160, y, 0xFF9FB1C4, false);
            graphics.drawString(font, session.state(), left + 228, y, color, false);
            String limit = session.lapLimit() > 0 ? session.lapLimit() + "L" : formatClock(session.durationTicks());
            graphics.drawString(font, limit, left + 314 - font.width(limit), y, 0xFF9FB1C4, false);
        }
    }

    private void drawActiveSession(GuiGraphics graphics, int left, int top) {
        graphics.drawString(font, Component.translatable("screen.openwheelracing.gp_weekend.controls"), left + 338, top + 70, 0xFFE8EDF2, false);
        int y = top + 224;
        graphics.drawString(font, Component.translatable("screen.openwheelracing.gp_weekend.active"), left + 338, y, 0xFF9FB1C4, false);
        graphics.drawString(font, fit(info.activeSessionName(), 164), left + 338, y + 13, 0xFFFFFFFF, false);
        graphics.drawString(font, info.activeSessionType() + " / " + info.activeSessionState(), left + 338, y + 26, 0xFF79C0FF, false);
        graphics.drawString(font, "Elapsed  " + formatClock(info.elapsedTicks()), left + 338, y + 43, 0xFFC9D1D9, false);
        graphics.drawString(font, "Remaining  " + formatClock(info.remainingTicks()), left + 338, y + 56, 0xFFC9D1D9, false);
        if (info.countdownRemainingTicks() > 0L) {
            graphics.drawString(font, "Countdown  " + formatClock(info.countdownRemainingTicks()), left + 338, y + 69, 0xFFFFD76A, false);
        }
        if (!info.suspensionReason().isBlank() && !info.suspensionReason().equals("NONE")) {
            graphics.drawString(font, fit("Reason  " + info.suspensionReason(), 164), left + 338, y + 82, 0xFFFF8B8B, false);
        }
    }

    private String fit(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        return font.plainSubstrByWidth(value, maxWidth - font.width("...")) + "...";
    }

    private static String formatClock(long ticks) {
        long seconds = Math.max(0L, ticks + 19L) / 20L;
        return String.format("%d:%02d", seconds / 60L, seconds % 60L);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
