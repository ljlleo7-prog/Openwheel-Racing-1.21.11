package com.openwheelracing.client.screen;

import com.openwheelracing.content.race.RaceDirectorLapRow;
import com.openwheelracing.network.OWRNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Paged server query, so filters and sorting apply to the complete record history. */
public final class LapRecordsScreen extends Screen {
    private static int nextRequestId;
    private final Screen parent;
    private final EditBox[] filters = new EditBox[6];
    private final String[] values = {"", "", "", "", "", ""};
    private List<RaceDirectorLapRow> rows = List.of();
    private int sort = 1;
    private boolean descending = true;
    private boolean expanded;
    private boolean canManage;
    private int page;
    private int total;
    private int requestId;
    private int ticks;
    private String error = "";
    private static final String[] SORTS = {"lap_time", "created", "player", "session"};
    private static final String[] LABELS = {"player_filter", "session_filter", "min", "max", "from", "through"};
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    public LapRecordsScreen(Screen parent) {
        super(Component.literal("Lap records"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        buildWidgets();
        request(0, 0);
    }

    private int columns() { return expanded ? 1 : 2; }
    private int rowHeight() { return expanded ? 88 : 58; }
    private int visibleRows() { return Math.max(1, (height - 170) / rowHeight()); }
    private int pageSize() { return Math.min(24, visibleRows() * columns()); }

    private void rememberFilters() {
        for (int i = 0; i < filters.length; i++) if (filters[i] != null) values[i] = filters[i].getValue();
    }

    private void buildWidgets() {
        rememberFilters();
        clearWidgets();
        int w = (width - 40) / 3;
        for (int i = 0; i < filters.length; i++) {
            filters[i] = new EditBox(font, 16 + i % 3 * (w + 4), 38 + i / 3 * 34, w, 18, Component.literal(t(LABELS[i])));
            filters[i].setMaxLength(i == 0 ? 128 : 32);
            filters[i].setValue(values[i]);
            filters[i].setHint(Component.literal(i >= 4 ? "YYYY-MM-DD" : t("any")));
            addRenderableWidget(filters[i]);
        }
        int x = 16;
        addRenderableWidget(Button.builder(Component.literal(t("apply")), b -> { page = 0; request(0, 0); }).bounds(x, 100, 86, 20).build());
        addRenderableWidget(Button.builder(Component.literal(t("sort", t(SORTS[sort]))), b -> {
            sort = (sort + 1) % SORTS.length; page = 0; request(0, 0);
        }).bounds(x + 90, 100, 112, 20).build());
        addRenderableWidget(Button.builder(Component.literal(descending ? t("desc") : t("asc")), b -> {
            descending = !descending; page = 0; request(0, 0);
        }).bounds(x + 206, 100, 84, 20).build());
        addRenderableWidget(Button.builder(Component.literal(expanded ? t("compact") : t("expand")), b -> {
            expanded = !expanded; page = 0; rows = List.of(); buildWidgets(); request(0, 0);
        }).bounds(x + 294, 100, 128, 20).build());
        addRenderableWidget(Button.builder(Component.literal(t("back")), b -> onClose()).bounds(width - 66, height - 26, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { page = Math.max(0, page - 1); request(0, 0); })
            .bounds(16, height - 26, 24, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; request(0, 0); })
            .bounds(44, height - 26, 24, 20).build()).active = (long) (page + 1) * pageSize() < total;
        if (expanded && canManage) {
            for (int i = 0; i < rows.size(); i++) {
                RaceDirectorLapRow row = rows.get(i);
                int y = 132 + i * rowHeight();
                addRenderableWidget(Button.builder(Component.literal(row.invalidated() ? t("revalidate") : t("invalidate")),
                    b -> request(row.id(), row.invalidated() ? 2 : 1)).bounds(width - 182, y + 63, 94, 18).build());
                addRenderableWidget(Button.builder(Component.literal(t("discard")), b -> request(row.id(), 3))
                    .bounds(width - 84, y + 63, 60, 18).build());
            }
        }
    }

    private void request(long lapId, int action) {
        rememberFilters();
        requestId = ++nextRequestId;
        OWRNetwork.sendToServer(new OWRNetwork.LapRecordsRequest(requestId, values[0], values[1], values[2], values[3],
            values[4], values[5], sort, descending, page, pageSize(), lapId, action));
    }

    public static void receive(OWRNetwork.LapRecordsResponse response) {
        if (!(Minecraft.getInstance().screen instanceof LapRecordsScreen screen) || response.requestId() != screen.requestId) return;
        screen.rows = response.rows();
        screen.total = response.total();
        screen.page = response.page();
        screen.error = response.error();
        screen.canManage = response.canManage();
        screen.buildWidgets();
    }

    public void applyParentSnapshot(com.openwheelracing.content.race.RaceDirectorSnapshot snapshot) {
        if (parent instanceof RaceDirectorScreen director) director.applySnapshotFromWeekend(snapshot);
    }

    @Override public void tick() {
        super.tick();
        // Refresh after changes made by another director; avoid interrupting filter entry.
        if (++ticks % 100 == 0 && getFocused() == null) request(0, 0);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF101820);
        g.drawCenteredString(font, t("all"), width / 2, 10, 0xFFE8EDF2);
        int w = (width - 40) / 3;
        for (int i = 0; i < LABELS.length; i++) g.drawString(font, t(LABELS[i]), 16 + i % 3 * (w + 4), 28 + i / 3 * 34, 0xFF9AA7B2, false);
        int panelWidth = (width - 32 - (columns() - 1) * 8) / columns();
        for (int i = 0; i < rows.size(); i++) {
            RaceDirectorLapRow row = rows.get(i);
            int x = 16 + i % columns() * (panelWidth + 8), y = 132 + i / columns() * rowHeight();
            g.fill(x, y, x + panelWidth, y + rowHeight() - 4, 0xFF1B2A35);
            int color = row.invalidated() ? 0xFFFF7777 : 0xFF7EE787;
            line(g, time(row.lapMillis()) + "  " + row.driverName() + "  [" + (row.invalidated() ? t("invalid") : t("valid")) + "]", x, y + 5, panelWidth, color);
            line(g, "#" + row.id() + "  " + timestamp(row.completedWorldTime()) + " UTC", x, y + 17, panelWidth, 0xFFC9D1D9);
            line(g, t("session_row", row.sessionId(), row.sessionName()), x, y + 29, panelWidth, 0xFFC9D1D9);
            if (expanded) {
                String tyre = row.tyre().isBlank() ? t("unknown") : row.tyre();
                String bop = row.bopWeightPercent() == 0 ? t("unknown") : t("bop", row.bopWeightPercent(), row.bopPowerPercent());
                String distance = row.traveledMeters() < 0 ? t("unknown") : String.format(java.util.Locale.ROOT, "%.1f m", row.traveledMeters());
                line(g, t("tyre_row", tyre, row.tyreWearPercent(), distance), x, y + 41, panelWidth, 0xFFC9D1D9);
                line(g, t("bop_row", bop, row.damagePercent()), x, y + 53, panelWidth, 0xFFC9D1D9);
                line(g, row.invalidated() ? row.invalidationReason() : "CP " + row.checkpointCount() + " / Damage " + row.damagePercent() + "%", x, y + 68, panelWidth - 174, color);
            }
        }
        if (rows.isEmpty()) g.drawCenteredString(font, error.isEmpty() ? t("no_matches") : error, width / 2, 138, 0xFFFFCC88);
        g.drawString(font, t("page", page + 1, Math.max(1, (total + pageSize() - 1) / pageSize()), total), 78, height - 20, 0xFFC9D1D9, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void line(GuiGraphics g, String text, int x, int y, int width, int color) {
        g.drawString(font, font.plainSubstrByWidth(text, Math.max(0, width - 12)), x + 6, y, color, false);
    }
    private static String t(String key, Object... args) { return Component.translatable("screen.openwheelracing.lap_records." + key, args).getString(); }
    private static String time(int millis) { return String.format(java.util.Locale.ROOT, "%d:%02d.%03d", millis / 60000, millis / 1000 % 60, millis % 1000); }
    private static String timestamp(long millis) { return millis >= 1_000_000_000_000L ? TIMESTAMP.format(Instant.ofEpochMilli(millis)) : t("unknown_time"); }
    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
