package com.openwheelracing.client.screen;

import com.openwheelracing.content.race.weekend.GrandPrixWeekendInfo;
import com.openwheelracing.network.OWRNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** Dedicated setup and live-control workspace which leaves the main Race Director layout untouched. */
public final class GrandPrixWeekendScreen extends Screen {
    private static final int WIDTH = 620;
    private static final int HEIGHT = 390;
    private static final int SESSION_ROWS = 10;
    private final RaceDirectorScreen parent;
    private GrandPrixWeekendInfo info;
    private Page page = Page.SETUP;
    private int selectedSession = -1;
    private int selectedEntry = -1;
    private String type = "practice";
    private String format = "timed";
    private String gridSource = "entry";
    private EditBox eventNameBox;
    private EditBox sessionNameBox;
    private EditBox durationBox;
    private EditBox lapLimitBox;
    private EditBox countdownBox;
    private EditBox graceBox;
    private EditBox worldTimeBox;
    private EditBox driverNameBox;
    private EditBox driverCodeBox;

    GrandPrixWeekendScreen(RaceDirectorScreen parent, GrandPrixWeekendInfo info) {
        super(Component.translatable("screen.openwheelracing.gp_weekend.title"));
        this.parent = parent;
        this.info = info == null ? GrandPrixWeekendInfo.empty() : info;
    }

    void applySnapshot(com.openwheelracing.content.race.RaceDirectorSnapshot snapshot) {
        parent.applySnapshotFromWeekend(snapshot);
        info = snapshot == null ? GrandPrixWeekendInfo.empty() : snapshot.grandPrixWeekend();
        selectedSession = Math.min(selectedSession, info.sessions().size() - 1);
        selectedEntry = Math.min(selectedEntry, info.entries().size() - 1);
        if (!(getFocused() instanceof EditBox)) rebuildWidgets();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        String eventName = text(eventNameBox, "");
        String sessionName = text(sessionNameBox, "FP1");
        String duration = text(durationBox, "900");
        String laps = text(lapLimitBox, "10");
        String countdown = text(countdownBox, "10");
        String grace = text(graceBox, "120");
        String worldTime = text(worldTimeBox, "6000");
        String driverName = text(driverNameBox, "");
        String driverCode = text(driverCodeBox, "");
        clearWidgets();
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.gp_weekend.setup"), button -> switchPage(Page.SETUP))
            .bounds(left + 12, top + 58, 90, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.gp_weekend.control_tab"), button -> switchPage(Page.CONTROL))
            .bounds(left + 106, top + 58, 90, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
            .bounds(left + 12, top + HEIGHT - 26, 72, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.refresh"), button -> refresh())
            .bounds(left + WIDTH - 84, top + HEIGHT - 26, 72, 18).build());
        if (page == Page.CONTROL) {
            buildControls(left, top);
            return;
        }
        if (!info.present()) {
            eventNameBox = edit(left + 176, top + 132, 268, 18, 80, eventName, "screen.openwheelracing.gp_weekend.event_name");
            Button create = Button.builder(Component.translatable("screen.openwheelracing.gp_weekend.create"), button ->
                sendSetup("create", eventNameBox.getValue())).bounds(left + 250, top + 158, 120, 18).build();
            create.active = !eventNameBox.getValue().isBlank();
            eventNameBox.setResponder(value -> create.active = !value.isBlank());
            addRenderableWidget(create);
            return;
        }
        buildSetup(left, top, sessionName, duration, laps, countdown, grace, worldTime, driverName, driverCode);
    }

    private void buildSetup(int left, int top, String sessionName, String duration, String laps, String countdown,
                            String grace, String worldTime, String driverName, String driverCode) {
        boolean editable = info.eventState().equals("DRAFT");
        int x = left + 382;
        sessionNameBox = edit(x, top + 106, 224, 16, 40, sessionName, "screen.openwheelracing.gp_weekend.session_name");
        addRenderableWidget(Button.builder(Component.literal("Type: " + type.toUpperCase(Locale.ROOT)), button -> cycleType())
            .bounds(x, top + 127, 108, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Format: " + format.toUpperCase(Locale.ROOT)), button -> cycleFormat())
            .bounds(x + 116, top + 127, 108, 18).build());
        durationBox = edit(x, top + 158, 108, 16, 6, duration, "screen.openwheelracing.gp_weekend.duration");
        lapLimitBox = edit(x + 116, top + 158, 108, 16, 5, laps, "screen.openwheelracing.gp_weekend.laps");
        countdownBox = edit(x, top + 188, 108, 16, 4, countdown, "screen.openwheelracing.gp_weekend.countdown_seconds");
        graceBox = edit(x + 116, top + 188, 108, 16, 5, grace, "screen.openwheelracing.gp_weekend.grace");
        worldTimeBox = edit(x, top + 218, 108, 16, 5, worldTime, "screen.openwheelracing.gp_weekend.world_time");
        addRenderableWidget(Button.builder(Component.literal("Grid: " + gridSource.toUpperCase(Locale.ROOT)), button -> cycleGrid())
            .bounds(x + 116, top + 217, 108, 18).build());
        Button add = Button.builder(Component.translatable("screen.openwheelracing.gp_weekend.add_session"), button -> addSession())
            .bounds(x, top + 239, 224, 18).build();
        add.active = editable;
        addRenderableWidget(add);

        addSmallSetupButton(left + 18, top + 275, "screen.openwheelracing.gp_weekend.move_up", () -> moveSession(-1), editable && selectedSession > 0);
        addSmallSetupButton(left + 98, top + 275, "screen.openwheelracing.gp_weekend.move_down", () -> moveSession(1), editable && selectedSession >= 0 && selectedSession < info.sessions().size() - 1);
        addSmallSetupButton(left + 178, top + 275, "screen.openwheelracing.gp_weekend.remove_session", this::removeSession, editable && selectedSession >= 0);

        driverNameBox = edit(x, top + 281, 142, 16, 40, driverName, "screen.openwheelracing.gp_weekend.player_name");
        driverCodeBox = edit(x + 150, top + 281, 74, 16, 8, driverCode, "screen.openwheelracing.gp_weekend.driver_code");
        Button register = Button.builder(Component.translatable("screen.openwheelracing.gp_weekend.register"), button ->
            sendSetup("register", info.eventName(), driverNameBox.getValue(), driverCodeBox.getValue()))
            .bounds(x, top + 300, 108, 18).build();
        register.active = editable;
        addRenderableWidget(register);
        Button unregister = Button.builder(Component.translatable("screen.openwheelracing.gp_weekend.unregister"), button -> unregisterEntry())
            .bounds(x + 116, top + 300, 108, 18).build();
        unregister.active = editable && (selectedEntry >= 0 || !driverNameBox.getValue().isBlank());
        addRenderableWidget(unregister);
        Button open = Button.builder(Component.translatable("screen.openwheelracing.gp_weekend.open_event"), button ->
            openWeekend()).bounds(x, top + 326, 108, 18).build();
        open.active = editable && !info.sessions().isEmpty() && !info.entries().isEmpty();
        addRenderableWidget(open);
        Button delete = Button.builder(Component.translatable("screen.openwheelracing.gp_weekend.delete"), button ->
            sendSetup("delete", info.eventName())).bounds(x + 116, top + 326, 108, 18).build();
        delete.active = !info.eventState().equals("OPEN");
        addRenderableWidget(delete);
        setSetupFieldsEditable(editable);
    }

    private void buildControls(int left, int top) {
        if (!info.present()) return;
        addControl(left + 390, top + 158, "advance", "screen.openwheelracing.gp_weekend.advance");
        addControl(left + 500, top + 158, "stage", "screen.openwheelracing.gp_weekend.stage");
        addControl(left + 390, top + 182, "countdown", "screen.openwheelracing.gp_weekend.countdown");
        addControl(left + 500, top + 182, "start", "screen.openwheelracing.gp_weekend.start");
        addControl(left + 390, top + 216, "suspend", "screen.openwheelracing.gp_weekend.suspend");
        addControl(left + 500, top + 216, "resume", "screen.openwheelracing.gp_weekend.resume");
        addControl(left + 390, top + 250, "finish", "screen.openwheelracing.gp_weekend.finish");
        addControl(left + 500, top + 250, "provisional", "screen.openwheelracing.gp_weekend.provisional");
        addControl(left + 390, top + 274, "official", "screen.openwheelracing.gp_weekend.official");
        addControl(left + 500, top + 274, "complete", "screen.openwheelracing.gp_weekend.complete");
    }

    private EditBox edit(int x, int y, int width, int height, int maxLength, String value, String hintKey) {
        EditBox box = new EditBox(font, x, y, width, height, Component.translatable(hintKey));
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setHint(Component.translatable(hintKey));
        addRenderableWidget(box);
        return box;
    }

    private void setSetupFieldsEditable(boolean editable) {
        for (EditBox box : List.of(sessionNameBox, durationBox, lapLimitBox, countdownBox, graceBox, worldTimeBox,
            driverNameBox, driverCodeBox)) box.setEditable(editable);
        boolean lapRace = (type.equals("sprint") || type.equals("race")) && format.equals("laps");
        lapLimitBox.setEditable(editable && lapRace);
        durationBox.setEditable(editable && !lapRace);
    }

    private void addSmallSetupButton(int x, int y, String key, Runnable action, boolean active) {
        Button button = Button.builder(Component.translatable(key), ignored -> action.run()).bounds(x, y, 74, 18).build();
        button.active = active;
        addRenderableWidget(button);
    }

    private void addControl(int x, int y, String action, String translationKey) {
        Button button = Button.builder(Component.translatable(translationKey), ignored ->
            OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorGrandPrixControlMessage(info.eventName(), action)))
            .bounds(x, y, 102, 18).build();
        button.active = info.can(action);
        addRenderableWidget(button);
    }

    private void addSession() {
        String effectiveLapLimit = (type.equals("sprint") || type.equals("race")) && format.equals("laps")
            ? lapLimitBox.getValue() : "0";
        sendSetup("add", info.eventName(), type, format, sessionNameBox.getValue(), durationBox.getValue(),
            effectiveLapLimit, countdownBox.getValue(), graceBox.getValue(), worldTimeBox.getValue(), gridSource);
    }

    private void removeSession() {
        if (selectedSession >= 0) sendSetup("remove", info.eventName(), Integer.toString(selectedSession + 1));
    }

    private void moveSession(int delta) {
        int target = selectedSession + delta;
        if (selectedSession < 0 || target < 0 || target >= info.sessions().size()) return;
        sendSetup("move", info.eventName(), Integer.toString(selectedSession + 1), Integer.toString(target + 1));
        selectedSession = target;
    }

    private void unregisterEntry() {
        String name = selectedEntry >= 0 ? info.entries().get(selectedEntry).driverName() : driverNameBox.getValue();
        if (!name.isBlank()) sendSetup("unregister", info.eventName(), name);
    }

    private void openWeekend() {
        page = Page.CONTROL;
        sendSetup("open", info.eventName());
        rebuildWidgets();
    }

    private void sendSetup(String operation, String... arguments) {
        OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorGrandPrixSetupMessage(operation, List.of(arguments)));
    }

    private void refresh() {
        OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorRefreshSessionMessage());
    }

    private void switchPage(Page value) {
        page = value;
        rebuildWidgets();
    }

    private void cycleType() {
        type = switch (type) { case "practice" -> "qualifying"; case "qualifying" -> "sprint"; case "sprint" -> "race"; default -> "practice"; };
        format = switch (type) { case "practice", "qualifying" -> "timed"; default -> "laps"; };
        rebuildWidgets();
    }

    private void cycleFormat() {
        format = switch (type) {
            case "qualifying" -> switch (format) { case "timed" -> "one_shot"; case "one_shot" -> "two_shot"; default -> "timed"; };
            case "sprint", "race" -> format.equals("laps") ? "timed" : "laps";
            default -> "timed";
        };
        rebuildWidgets();
    }

    private void cycleGrid() {
        gridSource = switch (gridSource) { case "entry" -> "previous"; case "previous" -> "manual"; default -> "entry"; };
        rebuildWidgets();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (page != Page.SETUP || !info.present()) return false;
        double mouseX = event.x();
        double mouseY = event.y();
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        if (mouseX >= left + 14 && mouseX < left + 370 && mouseY >= top + 96 && mouseY < top + 96 + SESSION_ROWS * 17) {
            int index = (int) ((mouseY - (top + 96)) / 17);
            if (index < info.sessions().size()) {
                selectedSession = index;
                rebuildWidgets();
                return true;
            }
        }
        if (mouseX >= left + 18 && mouseX < left + 370 && mouseY >= top + 314) {
            int index = (int) ((mouseY - (top + 314)) / 14);
            if (index >= 0 && index < Math.min(3, info.entries().size())) {
                selectedEntry = index;
                driverNameBox.setValue(info.entries().get(index).driverName());
                driverCodeBox.setValue(info.entries().get(index).displayCode());
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xB0000000);
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xF21F2328);
        graphics.fill(left + 6, top + 6, left + WIDTH - 6, top + 52, 0xFF2F3640);
        graphics.drawCenteredString(font, title, left + WIDTH / 2, top + 11, 0xFFFFFFFF);
        if (info.present()) {
            graphics.drawCenteredString(font, info.eventName(), left + WIDTH / 2, top + 26, 0xFF79C0FF);
            graphics.drawCenteredString(font, info.eventState() + "  •  " + info.entryCount() + " entries",
                left + WIDTH / 2, top + 39, 0xFFAEBCCC);
        }
        if (page == Page.SETUP) drawSetup(graphics, left, top);
        else drawControl(graphics, left, top);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawSetup(GuiGraphics graphics, int left, int top) {
        if (!info.present()) {
            graphics.drawCenteredString(font, Component.translatable("screen.openwheelracing.gp_weekend.none"), left + WIDTH / 2, top + 102, 0xFF9AA6B2);
            graphics.drawCenteredString(font, Component.translatable("screen.openwheelracing.gp_weekend.create_help"), left + WIDTH / 2, top + 190, 0xFF7F8C99);
            return;
        }
        graphics.fill(left + 10, top + 84, left + 374, top + 358, 0xFF252B32);
        graphics.fill(left + 378, top + 84, left + WIDTH - 10, top + 358, 0xFF252B32);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.gp_weekend.schedule"), left + 18, top + 87, 0xFFE8EDF2, false);
        for (int index = 0; index < Math.min(SESSION_ROWS, info.sessions().size()); index++) {
            GrandPrixWeekendInfo.SessionInfo session = info.sessions().get(index);
            int y = top + 98 + index * 17;
            if (index == selectedSession) graphics.fill(left + 14, y - 2, left + 370, y + 11, 0x663B82B8);
            graphics.drawString(font, (index + 1) + ". " + fit(session.name(), 125), left + 20, y, 0xFFFFFFFF, false);
            graphics.drawString(font, session.type(), left + 158, y, 0xFF9FB1C4, false);
            graphics.drawString(font, session.format(), left + 235, y, 0xFF9FB1C4, false);
        }
        graphics.drawString(font, Component.translatable("screen.openwheelracing.gp_weekend.roster"), left + 18, top + 300, 0xFFE8EDF2, false);
        for (int index = 0; index < Math.min(3, info.entries().size()); index++) {
            GrandPrixWeekendInfo.EntryInfo entry = info.entries().get(index);
            int y = top + 314 + index * 14;
            if (index == selectedEntry) graphics.fill(left + 14, y - 2, left + 370, y + 11, 0x663B82B8);
            graphics.drawString(font, entry.displayCode() + "  " + fit(entry.driverName(), 240), left + 20, y, 0xFFC9D1D9, false);
        }
        graphics.drawString(font, Component.translatable("screen.openwheelracing.gp_weekend.session_editor"), left + 386, top + 87, 0xFFE8EDF2, false);
        drawFieldLabel(graphics, "screen.openwheelracing.gp_weekend.session_name", left + 382, top + 97, 224);
        drawFieldLabel(graphics, "screen.openwheelracing.gp_weekend.duration", left + 382, top + 149, 108);
        drawFieldLabel(graphics, "screen.openwheelracing.gp_weekend.laps", left + 498, top + 149, 108);
        drawFieldLabel(graphics, "screen.openwheelracing.gp_weekend.countdown_seconds", left + 382, top + 179, 108);
        drawFieldLabel(graphics, "screen.openwheelracing.gp_weekend.grace", left + 498, top + 179, 108);
        drawFieldLabel(graphics, "screen.openwheelracing.gp_weekend.world_time", left + 382, top + 209, 108);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.gp_weekend.roster_editor"), left + 386, top + 263, 0xFFE8EDF2, false);
        drawFieldLabel(graphics, "screen.openwheelracing.gp_weekend.player_name", left + 382, top + 272, 142);
        drawFieldLabel(graphics, "screen.openwheelracing.gp_weekend.driver_code", left + 532, top + 272, 74);
        if (!info.eventState().equals("DRAFT")) {
            graphics.drawCenteredString(font, Component.translatable("screen.openwheelracing.gp_weekend.locked"), left + 492, top + 349, 0xFFFFD76A);
        }
    }

    private void drawControl(GuiGraphics graphics, int left, int top) {
        if (!info.present()) {
            graphics.drawCenteredString(font, Component.translatable("screen.openwheelracing.gp_weekend.none"), left + WIDTH / 2, top + 110, 0xFF9AA6B2);
            return;
        }
        graphics.fill(left + 10, top + 84, left + 374, top + 354, 0xFF252B32);
        graphics.fill(left + 382, top + 84, left + WIDTH - 10, top + 354, 0xFF252B32);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.gp_weekend.schedule"), left + 18, top + 90, 0xFFE8EDF2, false);
        for (int index = 0; index < Math.min(12, info.sessions().size()); index++) {
            GrandPrixWeekendInfo.SessionInfo session = info.sessions().get(index);
            int y = top + 106 + index * 18;
            boolean active = index == info.activeSessionIndex();
            if (active) graphics.fill(left + 14, y - 3, left + 370, y + 12, 0x663B82B8);
            graphics.drawString(font, (index + 1) + ". " + fit(session.name(), 145), left + 20, y, active ? 0xFFFFFFFF : 0xFFC9D1D9, false);
            graphics.drawString(font, session.type(), left + 180, y, 0xFF9FB1C4, false);
            graphics.drawString(font, session.state(), left + 268, y, session.official() ? 0xFF7EE787 : 0xFFC9D1D9, false);
        }
        graphics.drawString(font, Component.translatable("screen.openwheelracing.gp_weekend.controls"), left + 390, top + 87, 0xFFE8EDF2, false);
        int y = top + 101;
        graphics.drawString(font, Component.translatable("screen.openwheelracing.gp_weekend.active"), left + 390, y, 0xFF9FB1C4, false);
        graphics.drawString(font, fit(info.activeSessionName(), 210), left + 390, y + 14, 0xFFFFFFFF, false);
        graphics.drawString(font, info.activeSessionType() + " / " + info.activeSessionState(), left + 390, y + 28, 0xFF79C0FF, false);
        Component next = nextAction();
        graphics.drawString(font, fit(Component.translatable("screen.openwheelracing.gp_weekend.next", next).getString(), 210),
            left + 390, y + 42, 0xFFFFD76A, false);
        int clockY = top + 307;
        if (info.activeSessionState().equals("COUNTDOWN")) {
            String countdown = Component.translatable("screen.openwheelracing.gp_weekend.countdown_display",
                formatClock(info.countdownRemainingTicks())).getString();
            graphics.drawCenteredString(font, countdown, left + 496, clockY, 0xFFFFD76A);
        } else {
            graphics.drawString(font, Component.translatable("screen.openwheelracing.gp_weekend.elapsed_display",
                formatClock(info.elapsedTicks())), left + 390, clockY, 0xFFC9D1D9, false);
            graphics.drawString(font, Component.translatable("screen.openwheelracing.gp_weekend.remaining_display",
                formatClock(info.remainingTicks())), left + 390, clockY + 14, 0xFFC9D1D9, false);
        }
    }

    private void drawFieldLabel(GuiGraphics graphics, String key, int x, int y, int maxWidth) {
        graphics.drawString(font, fit(Component.translatable(key).getString(), maxWidth), x, y, 0xFF9FB1C4, false);
    }

    private Component nextAction() {
        if (info.eventState().equals("DRAFT")) return Component.translatable("screen.openwheelracing.gp_weekend.next_open");
        if (info.eventState().equals("COMPLETE")) return Component.translatable("screen.openwheelracing.gp_weekend.next_complete");
        if (info.activeSessionIndex() < 0) return Component.translatable("screen.openwheelracing.gp_weekend.next_advance");
        return switch (info.activeSessionState()) {
            case "OPEN" -> Component.translatable(info.activeSessionType().equals("SPRINT") || info.activeSessionType().equals("RACE")
                ? "screen.openwheelracing.gp_weekend.next_stage" : "screen.openwheelracing.gp_weekend.next_start");
            case "STAGING" -> Component.translatable("screen.openwheelracing.gp_weekend.next_countdown");
            case "COUNTDOWN" -> Component.translatable("screen.openwheelracing.gp_weekend.next_counting");
            case "RUNNING" -> Component.translatable("screen.openwheelracing.gp_weekend.next_finish");
            case "SUSPENDED" -> Component.translatable("screen.openwheelracing.gp_weekend.next_resume");
            case "FINISHING" -> Component.translatable("screen.openwheelracing.gp_weekend.next_provisional");
            case "PROVISIONAL" -> Component.translatable("screen.openwheelracing.gp_weekend.next_official");
            case "OFFICIAL" -> Component.translatable(info.activeSessionIndex() == info.sessions().size() - 1
                ? "screen.openwheelracing.gp_weekend.next_complete_gp" : "screen.openwheelracing.gp_weekend.next_advance");
            default -> Component.translatable("screen.openwheelracing.gp_weekend.next_wait");
        };
    }

    private String fit(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        return font.plainSubstrByWidth(value, maxWidth - font.width("...")) + "...";
    }

    private static String text(EditBox box, String fallback) {
        return box == null ? fallback : box.getValue();
    }

    private static String formatClock(long ticks) {
        long seconds = Math.max(0L, ticks + 19L) / 20L;
        return String.format("%d:%02d", seconds / 60L, seconds % 60L);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private enum Page { SETUP, CONTROL }
}
