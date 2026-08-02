package com.openwheelracing.client.screen;

import com.openwheelracing.client.map.CircuitMapRenderer;
import com.openwheelracing.content.menu.RaceDirectorMenu;
import com.openwheelracing.content.race.RaceDirectorLapRow;
import com.openwheelracing.content.race.RaceDirectorSnapshot;
import com.openwheelracing.content.race.RaceFlagMode;
import com.openwheelracing.content.race.TeamCarRow;
import com.openwheelracing.network.OWRNetwork;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class RaceDirectorScreen extends AbstractContainerScreen<RaceDirectorMenu> {
    private static final int[] AUTO_DETECT_RADII = {128, 256, 512, 1_024, 2_048};
    private static final int ROW_X = 12;
    private static final int ROW_Y = 132;
    private static final int ROW_WIDTH = 200;
    private static final int ROW_HEIGHT = 12;
    private static final int RIGHT_X = 420;
    private static final int MAP_X = 232;
    private static final int MAP_Y = 66;
    private static final int MAP_WIDTH = 180;
    private static final int MAP_HEIGHT = 152;
    private static final int TEAM_LIST_X = 14;
    private static final int TEAM_LIST_Y = 90;
    private static final int TEAM_LIST_COLUMN_WIDTH = 176;
    private static final int TEAM_LIST_ROWS = 3;
    private static final int TEAM_PANEL_Y = 132;
    private static final int TEAM_PANEL_WIDTH = 176;
    private long selectedLapId = -1L;
    private int selectedTeamCarId = -1;
    private int autoDetectRadiusIndex = 2;
    private EditBox sessionNameBox;

    public RaceDirectorScreen(RaceDirectorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 584;
        imageHeight = 246;
        inventoryLabelY = 1000;
    }

    public static void applySnapshot(RaceDirectorSnapshot snapshot) {
        if (Minecraft.getInstance().screen instanceof RaceDirectorScreen screen) {
            screen.menu.applySnapshot(snapshot);
            if (screen.selectedLapId != -1L && screen.selectedRow() == null) {
                screen.selectedLapId = -1L;
            }
            if (screen.selectedTeamCarId != -1 && screen.selectedTeamCar() == null) {
                screen.selectedTeamCarId = -1;
            }
            screen.rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        super.init();
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        if (menu.showsTeamTerminal()) {
            addTeamWidgets();
            return;
        }
        if (menu.showsBoard()) {
            addBoardWidgets();
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF1F2328);
        graphics.fill(x + 6, y + 16, x + imageWidth - 6, y + 58, 0xFF2F3640);
        if (menu.showsTeamTerminal()) {
            graphics.fill(x + 6, y + 66, x + 224, y + 218, 0xFF2A3038);
            graphics.fill(x + MAP_X - 4, y + MAP_Y - 4, x + MAP_X + MAP_WIDTH + 4, y + MAP_Y + MAP_HEIGHT + 4, 0xFF15191F);
            graphics.fill(x + RIGHT_X - 4, y + 66, x + imageWidth - 6, y + 218, 0xFF2A3038);
            graphics.fill(x + 12, y + 88, x + 192, y + 126, 0xFF242A31);
            graphics.fill(x + RIGHT_X, y + 88, x + imageWidth - 12, y + 126, 0xFF242A31);
            graphics.fill(x + 12, y + 130, x + 192, y + 218, 0xFF242A31);
            graphics.fill(x + RIGHT_X, y + 130, x + imageWidth - 12, y + 218, 0xFF242A31);
        } else {
            graphics.fill(x + 6, y + 66, x + 224, y + 218, 0xFF2A3038);
            graphics.fill(x + MAP_X - 4, y + MAP_Y - 4, x + MAP_X + MAP_WIDTH + 4, y + MAP_Y + MAP_HEIGHT + 4, 0xFF15191F);
            graphics.fill(x + RIGHT_X - 4, y + 66, x + imageWidth - 6, y + 218, 0xFF2A3038);
            graphics.fill(x + RIGHT_X, y + 90, x + imageWidth - 12, y + 138, 0xFF242A31);
            graphics.fill(x + RIGHT_X, y + 158, x + imageWidth - 12, y + 218, 0xFF242A31);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        RaceDirectorSnapshot snapshot = menu.getSnapshot();
        graphics.drawString(font, title, 8, 6, 0xFFE8EDF2, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.active_session", snapshot.activeSessionName()), 12, 22, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.global_flag", flagName(snapshot.globalFlag())), 244, 22, flagColor(snapshot.globalFlag()), false);
        if (menu.showsTeamTerminal()) {
            drawTeamTerminal(graphics, snapshot);
        } else if (menu.showsBoard()) {
            drawBoardPage(graphics, snapshot);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menu.showsBoard() && event.button() == 0 && isInsideRows(event.x(), event.y())) {
            int index = (int) ((event.y() - topPos - ROW_Y) / ROW_HEIGHT);
            if (index >= 0 && index < menu.getSnapshot().laps().size()) {
                selectedLapId = menu.getSnapshot().laps().get(index).id();
                return true;
            }
        }
        if (menu.showsTeamTerminal() && event.button() == 0) {
            int index = teamListIndex(event.x(), event.y());
            if (index >= 0 && index < menu.getSnapshot().teamCars().size()) {
                selectedTeamCarId = menu.getSnapshot().teamCars().get(index).entityId();
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void addPageTabs() {
    }

    private void addBoardWidgets() {
        addSessionNameBox();
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.new_session_short"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorStartSessionMessage(sessionName())))
            .bounds(leftPos + 12, topPos + 68, 64, 16)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.refresh"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorRefreshSessionMessage()))
            .bounds(leftPos + 82, topPos + 68, 48, 16)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.archive"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorSetArchiveModeMessage(!menu.getSnapshot().archiveMode())))
            .bounds(leftPos + 136, topPos + 68, 48, 16)
            .build());
        addRenderableWidget(Button.builder(Component.literal("<"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorSetPageMessage(menu.getSnapshot().page() - 1)))
            .bounds(leftPos + 190, topPos + 68, 16, 16)
            .build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorSetPageMessage(menu.getSnapshot().page() + 1)))
            .bounds(leftPos + 208, topPos + 68, 16, 16)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.checkpoints_short"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorToggleRuleMessage(OWRNetwork.RaceDirectorToggleRuleMessage.CHECKPOINTS)))
            .bounds(leftPos + RIGHT_X, topPos + 68, 44, 16)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.off_track_short"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorToggleRuleMessage(OWRNetwork.RaceDirectorToggleRuleMessage.OFF_TRACK)))
            .bounds(leftPos + RIGHT_X + 50, topPos + 68, 70, 16)
            .build());
        addRenderableWidget(Button.builder(Component.literal("-"), button -> setMinimumLapTicks(menu.getSnapshot().minimumValidLapTicks() - 20))
            .bounds(leftPos + RIGHT_X, topPos + 148, 18, 14)
            .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> setMinimumLapTicks(menu.getSnapshot().minimumValidLapTicks() + 20))
            .bounds(leftPos + RIGHT_X + 22, topPos + 148, 18, 14)
            .build());
        addMapWidgets();
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.invalidate"), button -> {
            if (selectedLapId != -1L) {
                OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorInvalidateLapMessage(selectedLapId));
            }
        }).bounds(leftPos + RIGHT_X + 46, topPos + 146, 70, 16).build());
        addFlagTiles(leftPos + RIGHT_X, topPos + 92);
    }

    private void addSessionNameBox() {
        String previous = sessionNameBox == null ? menu.getSnapshot().activeSessionName() : sessionNameBox.getValue();
        sessionNameBox = new EditBox(font, leftPos + 12, topPos + 88, 128, 16, Component.translatable("screen.openwheelracing.race_director.session_name_hint"));
        sessionNameBox.setMaxLength(80);
        sessionNameBox.setValue(previous == null || previous.isBlank() ? menu.getSnapshot().activeSessionName() : previous);
        sessionNameBox.setHint(Component.translatable("screen.openwheelracing.race_director.session_name_hint"));
        addRenderableWidget(sessionNameBox);
    }

    private String sessionName() {
        String value = sessionNameBox == null ? "" : sessionNameBox.getValue().trim();
        return value.isEmpty() ? "Session" : value;
    }

    private void addTeamWidgets() {
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.team_terminal.sense_cars_short"), button -> OWRNetwork.sendToServer(new OWRNetwork.TeamTerminalSenseCarsMessage()))
            .bounds(leftPos + 16, topPos + 68, 70, 16)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.team_terminal.bind_left"), button -> bindSelectedTeamCar(0))
            .bounds(leftPos + RIGHT_X, topPos + 68, 58, 16)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.team_terminal.bind_right"), button -> bindSelectedTeamCar(1))
            .bounds(leftPos + RIGHT_X + 64, topPos + 68, 62, 16)
            .build());
        addRenderableWidget(Button.builder(Component.literal("-L"), button -> OWRNetwork.sendToServer(new OWRNetwork.TeamTerminalBindCarMessage(0, -1)))
            .bounds(leftPos + RIGHT_X, topPos + 88, 28, 16)
            .build());
        addRenderableWidget(Button.builder(Component.literal("-R"), button -> OWRNetwork.sendToServer(new OWRNetwork.TeamTerminalBindCarMessage(1, -1)))
            .bounds(leftPos + RIGHT_X + 32, topPos + 88, 28, 16)
            .build());
        addMapWidgets();
    }

    private void addMapWidgets() {
        addRenderableWidget(Button.builder(Component.literal("<"), button -> cycleAutoDetectRadius(-1))
            .bounds(leftPos + MAP_X, topPos + 224, 18, 16)
            .build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> cycleAutoDetectRadius(1))
            .bounds(leftPos + MAP_X + 22, topPos + 224, 18, 16)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.auto_detect_map"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceMonitorAutoDetectMapMessage(autoDetectRadius())))
            .bounds(leftPos + MAP_X + 76, topPos + 224, 104, 16)
            .build());
    }

    private int autoDetectRadius() {
        return AUTO_DETECT_RADII[autoDetectRadiusIndex];
    }

    private void cycleAutoDetectRadius(int delta) {
        autoDetectRadiusIndex = Math.floorMod(autoDetectRadiusIndex + delta, AUTO_DETECT_RADII.length);
        rebuildWidgets();
    }

    private String autoDetectRangeLabel() {
        int radius = autoDetectRadius();
        return radius >= 1_000 ? "Range " + (radius / 1_000) + " km" : "Range " + radius + " m";
    }

    private String mapScanProgress(RaceDirectorSnapshot snapshot) {
        int total = Math.max(1, snapshot.trackMapScanTotalChunks());
        int percent = Math.min(100, snapshot.trackMapScanScannedChunks() * 100 / total);
        return "Scanning fixed region " + percent + "%  " + snapshot.trackMapScanDetectedCells() + " cells";
    }

    private void addLiveWidgets() {
    }

    private void addRuleWidgets() {
    }

    private void addFlagTiles(int x, int y) {
        RaceFlagMode[] flags = RaceFlagMode.values();
        for (int index = 0; index < flags.length; index++) {
            RaceFlagMode flag = flags[index];
            addRenderableWidget(new FlagTile(x + index % 3 * 48, y + index / 3 * 20, 44, 16, flag));
        }
    }

    private void drawBoardPage(GuiGraphics graphics, RaceDirectorSnapshot snapshot) {
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.countdown_placeholder"), 12, 108, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.lap_counter_placeholder", snapshot.laps().size()), 12, 120, 0xFFC9D1D9, false);
        drawLapRows(graphics, snapshot);
        drawSelectedLap(graphics);
        CircuitMapRenderer.render(graphics, snapshot.trackMap(), snapshot.teamCars(), MAP_X, MAP_Y, MAP_WIDTH, MAP_HEIGHT, selectedTeamCarId, snapshot.leftTeamCarId(), snapshot.rightTeamCarId());
        if (snapshot.trackMapScanRunning()) {
            graphics.drawString(font, mapScanProgress(snapshot), MAP_X + 8, MAP_Y + MAP_HEIGHT / 2 - 4, 0xFFFFD866, false);
        } else if (!snapshot.trackMap().present()) {
            graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.no_track_map"), MAP_X + 34, MAP_Y + MAP_HEIGHT / 2 - 4, 0xFF888888, false);
        }
        graphics.drawString(font, autoDetectRangeLabel(), MAP_X + 44, MAP_Y + MAP_HEIGHT + 8, 0xFFC9D1D9, false);
        graphics.drawString(font, fit("CP " + state(snapshot.checkpointCheckEnabled()) + "  OT " + state(snapshot.offTrackCheckEnabled()) + "  Min " + formatSeconds(snapshot.minimumValidLapTicks()), 142), RIGHT_X, 136, 0xFFC9D1D9, false);
    }

    private void drawTeamTerminal(GuiGraphics graphics, RaceDirectorSnapshot snapshot) {
        List<TeamCarRow> cars = snapshot.teamCars();
        CircuitMapRenderer.render(graphics, snapshot.trackMap(), cars, MAP_X, MAP_Y, MAP_WIDTH, MAP_HEIGHT, selectedTeamCarId, snapshot.leftTeamCarId(), snapshot.rightTeamCarId());
        if (snapshot.trackMapScanRunning()) {
            graphics.drawString(font, mapScanProgress(snapshot), MAP_X + 8, MAP_Y + MAP_HEIGHT / 2 - 4, 0xFFFFD866, false);
        } else if (!snapshot.trackMap().present()) {
            graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.no_track_map"), MAP_X + 34, MAP_Y + MAP_HEIGHT / 2 - 4, 0xFF888888, false);
        }
        drawTeamCarPanel(graphics, findTeamCar(snapshot.leftTeamCarId()), RIGHT_X, TEAM_PANEL_Y, 72, true);
        drawTeamCarPanel(graphics, findTeamCar(snapshot.rightTeamCarId()), RIGHT_X + 78, TEAM_PANEL_Y, 72, false);
        if (cars.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.openwheelracing.team_terminal.no_cars"), TEAM_LIST_X, TEAM_LIST_Y + 44, 0xFFC9D1D9, false);
            return;
        }
        int visibleCars = Math.min(TEAM_LIST_ROWS, cars.size());
        for (int index = 0; index < visibleCars; index++) {
            TeamCarRow car = cars.get(index);
            int x = TEAM_LIST_X;
            int y = TEAM_LIST_Y + index * ROW_HEIGHT;
            if (car.entityId() == selectedTeamCarId) {
                graphics.fill(x, y - 1, x + TEAM_LIST_COLUMN_WIDTH, y + ROW_HEIGHT - 1, 0xFF3F5F7F);
            }
            if (car.entityId() == snapshot.leftTeamCarId() || car.entityId() == snapshot.rightTeamCarId()) {
                graphics.fill(x + 2, y, x + 6, y + 10, 0xFF7EE787);
            } else {
                graphics.fill(x + 2, y, x + 6, y + 10, car.inPitLane() ? 0xFF79C0FF : car.liveryColor());
            }
            graphics.drawString(font, fit("#" + car.entityId() + " " + car.liveryName() + " " + car.riderName(), TEAM_LIST_COLUMN_WIDTH - 14), x + 10, y, car.onMap() ? 0xFFE8EDF2 : 0xFFFF7777, false);
        }
    }

    private void drawTeamCarPanel(GuiGraphics graphics, TeamCarRow car, int x, int y, int width, boolean leftSide) {
        graphics.drawString(font, leftSide ? "L" : "R", x + width - 10, y + 6, 0xFF56616C, false);
        if (car == null) {
            graphics.drawString(font, Component.translatable("screen.openwheelracing.team_terminal.empty_slot"), x + 8, y + 34, 0xFFC9D1D9, false);
            return;
        }
        drawTeamCar(graphics, car, x, y, width);
    }

    private void drawTeamCar(GuiGraphics graphics, TeamCarRow car, int x, int y, int width) {
        graphics.fill(x + 8, y + 8, x + 18, y + 18, car.liveryColor());
        graphics.drawString(font, fit("#" + car.entityId() + " " + car.liveryName(), width - 20), x + 8, y + 20, 0xFFE8EDF2, false);
        graphics.drawString(font, fit(car.riderName(), width - 16), x + 8, y + 32, 0xFFC9D1D9, false);
        graphics.drawString(font, String.format("%3.0f km/h", car.speedKmh()), x + 8, y + 46, 0xFFE8EDF2, false);
        graphics.drawString(font, "G" + gearLabel(car.gear()) + "  " + String.format("%3.0f%%", car.ersPercent()), x + 8, y + 58, 0xFF99DDFF, false);
        graphics.drawString(font, car.inPitLane() ? "PIT" : (car.onMap() ? "TRACK" : "OFF"), x + 8, y + 70, car.onMap() ? 0xFF7EE787 : 0xFFFF7777, false);
    }

    private boolean isInsideRows(double mouseX, double mouseY) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        return localX >= ROW_X && localX <= ROW_X + ROW_WIDTH && localY >= ROW_Y && localY <= ROW_Y + menu.getSnapshot().laps().size() * ROW_HEIGHT;
    }

    private int teamListIndex(double mouseX, double mouseY) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        if (localY < TEAM_LIST_Y || localY >= TEAM_LIST_Y + TEAM_LIST_ROWS * ROW_HEIGHT) {
            return -1;
        }
        int row = (int) ((localY - TEAM_LIST_Y) / ROW_HEIGHT);
        if (localX >= TEAM_LIST_X && localX <= TEAM_LIST_X + TEAM_LIST_COLUMN_WIDTH) {
            return row;
        }
        return -1;
    }

    private TeamCarRow selectedTeamCar() {
        return findTeamCar(selectedTeamCarId);
    }

    private TeamCarRow findTeamCar(int entityId) {
        if (entityId < 0) {
            return null;
        }
        return menu.getSnapshot().teamCars().stream().filter(car -> car.entityId() == entityId).findFirst().orElse(null);
    }

    private void bindSelectedTeamCar(int side) {
        if (selectedTeamCarId != -1) {
            OWRNetwork.sendToServer(new OWRNetwork.TeamTerminalBindCarMessage(side, selectedTeamCarId));
        }
    }

    private void drawLapRows(GuiGraphics graphics, RaceDirectorSnapshot snapshot) {
        if (snapshot.laps().isEmpty()) {
            graphics.drawString(font, Component.translatable(snapshot.archiveMode() ? "screen.openwheelracing.race_director.no_archived_laps" : "screen.openwheelracing.race_director.no_session_laps"), ROW_X, ROW_Y, 0xFFC9D1D9, false);
            return;
        }
        for (int index = 0; index < snapshot.laps().size(); index++) {
            RaceDirectorLapRow row = snapshot.laps().get(index);
            int y = ROW_Y + index * ROW_HEIGHT;
            if (row.id() == selectedLapId) {
                graphics.fill(ROW_X - 2, y - 1, ROW_X + ROW_WIDTH, y + ROW_HEIGHT - 1, 0xFF3F5F7F);
            }
            int color = row.invalidated() ? 0xFFFF7777 : 0xFFE8EDF2;
            String rowText = snapshot.archiveMode()
                ? row.sessionName() + " / " + row.driverName() + "  " + formatLapTime(row.lapTicks())
                : row.driverName() + "  " + formatLapTime(row.lapTicks());
            graphics.drawString(font, fit(rowText, ROW_WIDTH), ROW_X, y, color, false);
        }
    }

    private void drawSelectedLap(GuiGraphics graphics) {
        RaceDirectorLapRow row = selectedRow();
        int y = 164;
        if (row == null) {
            graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.select_lap"), RIGHT_X, y, 0xFFC9D1D9, false);
            return;
        }
        graphics.drawString(font, fit(row.driverName(), 142), RIGHT_X, y, 0xFFE8EDF2, false);
        graphics.drawString(font, formatLapTime(row.lapTicks()) + " / CP " + row.checkpointCount(), RIGHT_X, y + 12, row.invalidated() ? 0xFFFF7777 : 0xFF7EE787, false);
        if (menu.getSnapshot().archiveMode()) {
            graphics.drawString(font, fit(row.sessionName(), 142), RIGHT_X, y + 24, 0xFFC9D1D9, false);
            return;
        }
        BlockPos pos = BlockPos.of(row.startFinishPos());
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.position", pos.getX(), pos.getY(), pos.getZ()), RIGHT_X, y + 24, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.setup", row.power(), row.grip(), row.aero(), row.gearing()), RIGHT_X, y + 36, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.condition", row.damagePercent(), row.tyreWearPercent()), RIGHT_X, y + 48, 0xFFC9D1D9, false);
    }

    private RaceDirectorLapRow selectedRow() {
        return menu.getSnapshot().laps().stream().filter(row -> row.id() == selectedLapId).findFirst().orElse(null);
    }

    private void setMinimumLapTicks(int ticks) {
        OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorSetMinLapTicksMessage(Math.max(1, ticks)));
    }

    private String fit(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }

    private static String gearLabel(int gear) {
        if (gear < 0) {
            return "R";
        }
        if (gear == 0) {
            return "N";
        }
        return Integer.toString(gear);
    }

    private static Component flagName(RaceFlagMode flag) {
        return Component.translatable("screen.openwheelracing.race_director.flag." + flag.key());
    }

    private static int flagColor(RaceFlagMode flag) {
        return switch (flag) {
            case GREEN -> 0xFF7EE787;
            case YELLOW, VIRTUAL_SAFETY_CAR -> 0xFFFFD866;
            case RED -> 0xFFFF7777;
            case SAFETY_CAR -> 0xFF79C0FF;
        };
    }

    private class FlagTile extends AbstractWidget {
        private final RaceFlagMode flag;

        private FlagTile(int x, int y, int width, int height, RaceFlagMode flag) {
            super(x, y, width, height, flagName(flag));
            this.flag = flag;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean active = menu.getSnapshot().globalFlag() == flag;
            int color = flagColor(flag);
            graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF101418);
            graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, active ? color : dim(color));
            if (active) {
                graphics.fill(getX(), getY(), getX() + width, getY() + 1, 0xFFFFFFFF);
                graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, 0xFFFFFFFF);
            }
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + 4, flagTextColor(flag));
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (!isMouseOver(event.x(), event.y())) {
                return false;
            }
            OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorSetGlobalFlagMessage(flag.ordinal()));
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static int dim(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return 0xFF000000 | (r / 3 << 16) | (g / 3 << 8) | b / 3;
    }

    private static int flagTextColor(RaceFlagMode flag) {
        return flag == RaceFlagMode.YELLOW || flag == RaceFlagMode.VIRTUAL_SAFETY_CAR ? 0xFF1F2328 : 0xFFFFFFFF;
    }

    private static String state(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }

    private static String formatSeconds(int ticks) {
        return String.format("%.1fs", ticks / 20.0f);
    }

    private static String formatLapTime(int ticks) {
        int totalCentiseconds = ticks * 5;
        int minutes = totalCentiseconds / 6000;
        int seconds = totalCentiseconds / 100 % 60;
        int centiseconds = totalCentiseconds % 100;
        return String.format("%d:%02d.%02d", minutes, seconds, centiseconds);
    }
}
