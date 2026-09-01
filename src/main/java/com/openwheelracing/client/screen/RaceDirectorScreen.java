package com.openwheelracing.client.screen;

import com.openwheelracing.client.map.CircuitMapRenderer;
import com.openwheelracing.client.telemetry.MonitorTelemetryClient;
import com.openwheelracing.client.telemetry.SpeedTraceGraphRenderer;
import com.openwheelracing.content.menu.RaceDirectorMenu;
import com.openwheelracing.content.race.RaceDirectorLapRow;
import com.openwheelracing.content.race.RaceDirectorSnapshot;
import com.openwheelracing.content.race.RaceFlagMode;
import com.openwheelracing.content.race.TeamCarRow;
import com.openwheelracing.network.OWRNetwork;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class RaceDirectorScreen extends AbstractContainerScreen<RaceDirectorMenu> {
    private static final int AUTO_DETECT_MIN_RADIUS = 128;
    private static final int AUTO_DETECT_MAX_RADIUS = 2_048;
    private static final int AUTO_DETECT_RADIUS_STEP = 16;
    private static final int AUTO_DETECT_DEFAULT_RADIUS = 1_280;
    private static final int ROW_X = 12;
    private static final int ROW_Y = 132;
    private static final int ROW_WIDTH = 176;
    private static final int ROW_HEIGHT = 12;
    private static final int LEFT_X = 12;
    private static final int MAP_X = 202;
    private static final int MAP_Y = 66;
    private static final int MAP_WIDTH = 180;
    private static final int MAP_HEIGHT = 176;
    private static final int RIGHT_X = 392;
    private static final int COLUMN_WIDTH = 180;
    private static final int TEAM_LIST_Y = 274;
    private static final int TEAM_LIST_ROWS = 3;
    private long selectedLapId = -1L;
    private int selectedTeamCarId = -1;
    private int autoDetectRadius = AUTO_DETECT_DEFAULT_RADIUS;
    private EditBox sessionNameBox;

    public RaceDirectorScreen(RaceDirectorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 584;
        imageHeight = menu.getMonitorType() == com.openwheelracing.content.block.entity.RaceMonitorType.BOARD ? 342 : 420;
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

    public static void applyMoistureSnapshot(com.openwheelracing.content.race.TrackMoistureSnapshot snapshot) {
        if (Minecraft.getInstance().screen instanceof RaceDirectorScreen screen) {
            screen.menu.applyMoistureSnapshot(snapshot);
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
        graphics.fill(x + 6, y + 66, x + 192, y + 330, 0xFF2A3038);
        graphics.fill(x + MAP_X - 4, y + MAP_Y - 4, x + MAP_X + MAP_WIDTH + 4, y + MAP_Y + MAP_HEIGHT + 4, 0xFF15191F);
        graphics.fill(x + RIGHT_X - 4, y + 66, x + imageWidth - 6, y + 330, 0xFF2A3038);
        if (menu.getMonitorType() != com.openwheelracing.content.block.entity.RaceMonitorType.BOARD) {
            graphics.fill(x + MAP_X - 4, y + 342, x + MAP_X + MAP_WIDTH + 4, y + 416, 0xFF15191F);
        }
        if (menu.showsTeamTerminal()) {
            graphics.fill(x + LEFT_X, y + 90, x + LEFT_X + COLUMN_WIDTH, y + 250, 0xFF242A31);
            graphics.fill(x + RIGHT_X, y + 90, x + RIGHT_X + COLUMN_WIDTH, y + 250, 0xFF242A31);
            graphics.fill(x + LEFT_X, y + 270, x + RIGHT_X + COLUMN_WIDTH, y + 326, 0xFF242A31);
        } else {
            graphics.fill(x + RIGHT_X, y + 88, x + RIGHT_X + COLUMN_WIDTH, y + 244, 0xFF242A31);
            graphics.fill(x + RIGHT_X, y + 250, x + RIGHT_X + COLUMN_WIDTH, y + 326, 0xFF242A31);
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
    public boolean keyPressed(KeyEvent event) {
        if (sessionNameBox != null && sessionNameBox.isFocused() && sessionNameBox.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (sessionNameBox != null && sessionNameBox.isFocused() && sessionNameBox.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
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
        if (menu.getMonitorType() == com.openwheelracing.content.block.entity.RaceMonitorType.DIRECTOR && event.button() == 0 && isInsideTelemetryCars(event.x(), event.y())) {
            int localX = (int) (event.x() - leftPos - MAP_X);
            int localY = (int) (event.y() - topPos - 250);
            int index = localY / ROW_HEIGHT * 2 + Math.min(1, Math.max(0, localX / 90));
            if (index >= 0 && index < menu.getSnapshot().teamCars().size()) {
                selectedTeamCarId = menu.getSnapshot().teamCars().get(index).entityId();
                MonitorTelemetryClient.clear();
                OWRNetwork.sendToServer(new OWRNetwork.MonitorTelemetrySubscribeMessage(menu.containerId, selectedTeamCarId));
                return true;
            }
        }
        if (menu.showsTeamTerminal() && event.button() == 0) {
            int index = teamListIndex(event.x(), event.y());
            if (index >= 0 && index < menu.getSnapshot().teamCars().size()) {
                selectedTeamCarId = menu.getSnapshot().teamCars().get(index).entityId();
                MonitorTelemetryClient.clear();
                OWRNetwork.sendToServer(new OWRNetwork.MonitorTelemetrySubscribeMessage(menu.containerId, selectedTeamCarId));
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
        addRenderableWidget(Button.builder(Component.literal("-"), button -> setRaceLapLimit(menu.getSnapshot().raceLapLimit() - 1))
            .bounds(leftPos + 142, topPos + 106, 18, 14)
            .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> setRaceLapLimit(menu.getSnapshot().raceLapLimit() + 1))
            .bounds(leftPos + 164, topPos + 106, 18, 14)
            .build());
        addRenderableWidget(Button.builder(Component.literal("<"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorSetPageMessage(menu.getSnapshot().page() - 1)))
            .bounds(leftPos + 190, topPos + 68, 16, 16)
            .build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorSetPageMessage(menu.getSnapshot().page() + 1)))
            .bounds(leftPos + 208, topPos + 68, 16, 16)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.checkpoints_short"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorToggleRuleMessage(OWRNetwork.RaceDirectorToggleRuleMessage.CHECKPOINTS)))
            .bounds(leftPos + RIGHT_X, topPos + 68, 48, 16)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.off_track_short"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorToggleRuleMessage(OWRNetwork.RaceDirectorToggleRuleMessage.OFF_TRACK)))
            .bounds(leftPos + RIGHT_X + 52, topPos + 68, 58, 16)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.auto_shift_short"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorToggleRuleMessage(OWRNetwork.RaceDirectorToggleRuleMessage.AUTO_SHIFTING)))
            .bounds(leftPos + RIGHT_X + 114, topPos + 68, 56, 16)
            .build());
        addRenderableWidget(Button.builder(Component.literal("-"), button -> setMinimumLapTicks(menu.getSnapshot().minimumValidLapTicks() - 20))
            .bounds(leftPos + RIGHT_X, topPos + 158, 18, 14)
            .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> setMinimumLapTicks(menu.getSnapshot().minimumValidLapTicks() + 20))
            .bounds(leftPos + RIGHT_X + 22, topPos + 158, 18, 14)
            .build());
        addLimitButtons(OWRNetwork.RaceDirectorSetErsLimitMessage.CAPACITY, leftPos + RIGHT_X, topPos + 192, 1);
        addLimitButtons(OWRNetwork.RaceDirectorSetErsLimitMessage.BALANCED_DEPLOY, leftPos + RIGHT_X, topPos + 210, 10);
        addLimitButtons(OWRNetwork.RaceDirectorSetErsLimitMessage.ATTACK_DEPLOY, leftPos + RIGHT_X, topPos + 228, 10);
        addLimitButtons(OWRNetwork.RaceDirectorSetErsLimitMessage.HARVEST_NEGATIVE, leftPos + RIGHT_X, topPos + 246, 10);
        addConditionButtons(OWRNetwork.RaceDirectorCycleConditionModifierMessage.CAR_DAMAGE, leftPos + RIGHT_X, topPos + 282);
        addConditionButtons(OWRNetwork.RaceDirectorCycleConditionModifierMessage.TYRE_WEAR, leftPos + RIGHT_X, topPos + 300);
        addMapWidgets();
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.invalidate"), button -> {
            if (selectedLapId != -1L) {
                OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorInvalidateLapMessage(selectedLapId));
            }
        }).bounds(leftPos + LEFT_X, topPos + 314, 170, 16).build());
        addFlagTiles(leftPos + RIGHT_X, topPos + 116);
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.start_lights"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorSignalControlMessage(OWRNetwork.RaceDirectorSignalControlMessage.START_PHASE, 1, 0)))
            .bounds(leftPos + 230, topPos + 318, 74, 14).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.lights_out"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorSignalControlMessage(OWRNetwork.RaceDirectorSignalControlMessage.START_PHASE, 6, 0)))
            .bounds(leftPos + 308, topPos + 318, 64, 14).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.auto_flags"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorSignalControlMessage(OWRNetwork.RaceDirectorSignalControlMessage.AUTO_FLAGGING, -1, 0)))
            .bounds(leftPos + 376, topPos + 318, 100, 14).build());
        if (selectedTeamCarId >= 0) {
            addRenderableWidget(Button.builder(Component.literal("BLUE"), button -> setSelectedDriverFlag(com.openwheelracing.content.race.RaceSignal.BLUE)).bounds(leftPos + 230, topPos + 334, 46, 14).build());
            addRenderableWidget(Button.builder(Component.literal("MECH"), button -> setSelectedDriverFlag(com.openwheelracing.content.race.RaceSignal.ORANGE)).bounds(leftPos + 280, topPos + 334, 46, 14).build());
            addRenderableWidget(Button.builder(Component.literal("CLEAR"), button -> setSelectedDriverFlag(com.openwheelracing.content.race.RaceSignal.OFF)).bounds(leftPos + 330, topPos + 334, 46, 14).build());
        }
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

    private void setRaceLapLimit(int laps) {
        OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorSetRaceLapLimitMessage(laps));
    }

    private void setSelectedDriverFlag(com.openwheelracing.content.race.RaceSignal signal) {
        if (selectedTeamCarId >= 0) OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorSignalControlMessage(OWRNetwork.RaceDirectorSignalControlMessage.DRIVER_FLAG, selectedTeamCarId, signal.ordinal()));
    }

    private void addTeamWidgets() {
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.team_terminal.sense_cars_short"), button -> OWRNetwork.sendToServer(new OWRNetwork.TeamTerminalSenseCarsMessage()))
            .bounds(leftPos + LEFT_X, topPos + 68, 80, 16)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.team_terminal.bind_left"), button -> bindSelectedTeamCar(0))
            .bounds(leftPos + LEFT_X + 86, topPos + 68, 58, 16)
            .build());
        addRenderableWidget(Button.builder(Component.literal("-L"), button -> OWRNetwork.sendToServer(new OWRNetwork.TeamTerminalBindCarMessage(0, -1)))
            .bounds(leftPos + LEFT_X + 148, topPos + 68, 28, 16)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.team_terminal.bind_right"), button -> bindSelectedTeamCar(1))
            .bounds(leftPos + RIGHT_X, topPos + 68, 86, 16)
            .build());
        addRenderableWidget(Button.builder(Component.literal("-R"), button -> OWRNetwork.sendToServer(new OWRNetwork.TeamTerminalBindCarMessage(1, -1)))
            .bounds(leftPos + RIGHT_X + 92, topPos + 68, 28, 16)
            .build());
        TeamCarRow selected = selectedTeamCar();
        Button push = Button.builder(Component.translatable("screen.openwheelracing.team_terminal.ai_push"), button -> {
            TeamCarRow car = selectedTeamCar();
            if (car != null) OWRNetwork.sendToServer(new OWRNetwork.TeamTerminalAiPushMessage(car.entityId()));
        }).bounds(leftPos + RIGHT_X + 124, topPos + 68, 52, 16).build();
        push.active = selected != null && selected.aiOwned();
        addRenderableWidget(push);
        addMapWidgets();
    }

    private void addMapWidgets() {
        addRenderableWidget(new AutoDetectRangeSlider(leftPos + MAP_X, topPos + 250, 72, 18));
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.auto_detect_map"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceMonitorAutoDetectMapMessage(autoDetectRadius())))
            .bounds(leftPos + MAP_X + 76, topPos + 252, 104, 16)
            .build());
    }

    private int autoDetectRadius() {
        return autoDetectRadius;
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
        String lapLimit = snapshot.raceLapLimit() == 0 ? Component.translatable("screen.openwheelracing.race_director.race_laps_open").getString() : Integer.toString(snapshot.raceLapLimit());
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.race_laps", lapLimit), 12, 108, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.lap_counter_placeholder", snapshot.laps().size()), 12, 120, 0xFFC9D1D9, false);
        drawLapRows(graphics, snapshot);
        CircuitMapRenderer.render(graphics, snapshot.trackMap(), menu.getMoistureSnapshot(), snapshot.teamCars(), MAP_X, MAP_Y, MAP_WIDTH, MAP_HEIGHT, selectedTeamCarId, snapshot.leftTeamCarId(), snapshot.rightTeamCarId());
        drawWeatherTelemetry(graphics, snapshot);
        drawMapState(graphics, snapshot);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.rule_setup"), RIGHT_X, 88, 0xFFE8EDF2, false);
        graphics.drawString(font, fit(Component.translatable("screen.openwheelracing.race_director.rule_status", state(snapshot.checkpointCheckEnabled()), state(snapshot.offTrackCheckEnabled()), state(snapshot.autoShiftingAllowed()), formatSeconds(snapshot.minimumValidLapTicks())).getString(), COLUMN_WIDTH - 8), RIGHT_X, 100, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.flag_assignment"), RIGHT_X, 112, 0xFFE8EDF2, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.ers_limits"), RIGHT_X, 180, 0xFFE8EDF2, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.ers_capacity", snapshot.maxErsCapacityMj()), RIGHT_X + 24, 194, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.ers_balanced", snapshot.maxBalancedDeployKw()), RIGHT_X + 24, 212, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.ers_attack", snapshot.maxAttackDeployKw()), RIGHT_X + 24, 230, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.ers_harvest", snapshot.maxHarvestNegativeKw()), RIGHT_X + 24, 248, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.condition_modifiers", modifier(snapshot.carDamageModifier()), modifier(snapshot.tyreWearModifier())), RIGHT_X + 24, 270, 0xFFC9D1D9, false);
        graphics.drawString(font, "Damage", RIGHT_X + 24, 284, 0xFFC9D1D9, false);
        graphics.drawString(font, "Tyres", RIGHT_X + 24, 302, 0xFFC9D1D9, false);
        drawSelectedLap(graphics);
        drawPitPenalties(graphics, snapshot);
        drawDirectorTelemetry(graphics, snapshot);
    }

    private void drawTeamTerminal(GuiGraphics graphics, RaceDirectorSnapshot snapshot) {
        List<TeamCarRow> cars = snapshot.teamCars();
        CircuitMapRenderer.render(graphics, snapshot.trackMap(), menu.getMoistureSnapshot(), cars, MAP_X, MAP_Y, MAP_WIDTH, MAP_HEIGHT, selectedTeamCarId, snapshot.leftTeamCarId(), snapshot.rightTeamCarId());
        drawWeatherTelemetry(graphics, snapshot);
        drawMapState(graphics, snapshot);
        drawTeamCarPanel(graphics, findTeamCar(snapshot.leftTeamCarId()), LEFT_X, 90, COLUMN_WIDTH, true);
        drawTeamCarPanel(graphics, findTeamCar(snapshot.rightTeamCarId()), RIGHT_X, 90, COLUMN_WIDTH, false);
        if (cars.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.openwheelracing.team_terminal.no_cars"), LEFT_X + 4, TEAM_LIST_Y, 0xFFC9D1D9, false);
            return;
        }
        SpeedTraceGraphRenderer.render(graphics, font, MAP_X, 350, MAP_WIDTH, 62);
        int visibleCars = Math.min(TEAM_LIST_ROWS * 2, cars.size());
        for (int index = 0; index < visibleCars; index++) {
            TeamCarRow car = cars.get(index);
            int column = index % 2;
            int row = index / 2;
            int x = LEFT_X + column * (COLUMN_WIDTH + 200);
            int y = TEAM_LIST_Y + row * ROW_HEIGHT;
            if (car.entityId() == selectedTeamCarId) {
                graphics.fill(x, y - 1, x + COLUMN_WIDTH, y + ROW_HEIGHT - 1, 0xFF3F5F7F);
            }
            int stripColor = car.entityId() == snapshot.leftTeamCarId() || car.entityId() == snapshot.rightTeamCarId()
                ? 0xFF7EE787
                : (car.inPitLane() ? 0xFF79C0FF : car.liveryColor());
            graphics.fill(x + 2, y, x + 6, y + 10, stripColor);
            graphics.drawString(font, fit("#" + car.entityId() + " " + car.liveryName() + " " + car.riderName(), COLUMN_WIDTH - 14), x + 10, y, car.onMap() ? 0xFFE8EDF2 : 0xFFFF7777, false);
        }
    }

    private void drawPitPenalties(GuiGraphics graphics, RaceDirectorSnapshot snapshot) {
        int x = RIGHT_X;
        int y = 326;
        graphics.drawString(font, "PIT INCIDENTS — PENDING VERIFICATION", x, y, 0xFFFFD76A, false);
        if (snapshot.pendingPitPenalties().isEmpty()) {
            graphics.drawString(font, "None", x, y + 13, 0xFF8792A2, false);
            return;
        }
        int shown = Math.min(4, snapshot.pendingPitPenalties().size());
        for (int offset = 0; offset < shown; offset++) {
            var row = snapshot.pendingPitPenalties().get(snapshot.pendingPitPenalties().size() - 1 - offset);
            String text = row.driverName() + "  I " + Math.round(row.instantKmh()) + " / A " + Math.round(row.averageKmh()) + " km/h";
            graphics.drawString(font, fit(text, COLUMN_WIDTH - 4), x, y + 13 + offset * 12, 0xFFFF7777, false);
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
        graphics.drawString(font, fit("#" + car.entityId() + " " + car.liveryName(), width - 34), x + 24, y + 8, 0xFFE8EDF2, false);
        graphics.drawString(font, fit(Component.translatable("screen.openwheelracing.team_terminal.rider", car.riderName()).getString(), width - 16), x + 8, y + 22, 0xFFC9D1D9, false);
        graphics.drawString(font, String.format("SPD %3.0f km/h  G%s", car.speedKmh(), gearLabel(car.gear())), x + 8, y + 40, 0xFFE8EDF2, false);
        graphics.drawString(font, String.format("RPM %d", car.rpm()), x + 8, y + 54, 0xFFE8EDF2, false);
        graphics.drawString(font, String.format("ERS %3.0f%%  %+.0fkW", car.ersPercent(), car.ersPowerKw()), x + 8, y + 68, 0xFF99DDFF, false);
        graphics.drawString(font, String.format("TYRE %3.0f%%  %3.0fC", car.tyrePercent(), car.tyreTemperatureC()), x + 8, y + 82, 0xFFFFD866, false);
        graphics.drawString(font, String.format("DMG %3.0f%%", car.damagePercent()), x + 8, y + 96, car.damagePercent() > 70.0f ? 0xFFFF7777 : 0xFFC9D1D9, false);
        ComponentDamageDisplay.drawCompact(graphics, font, car.componentDamage(), x + 8, y + 108, 0xFFC9D1D9);
        String lapText = "LST " + telemetryLapTime(car.lastLapMillis()) + "  BST " + telemetryLapTime(car.bestLapMillis());
        graphics.drawString(font, fit(lapText, width - 16), x + 8, y + 122, 0xFF7EE787, false);
        if (car.aiOwned()) graphics.drawString(font, String.format(java.util.Locale.ROOT, "AI PUSH x%.4f", car.aiPaceScale()), x + 8, y + 136, 0xFFFFD866, false);
        graphics.drawString(font, car.inPitLane() ? "MAP PIT" : (car.onMap() ? "MAP TRACK" : "MAP OFF"), x + 8, y + 150, car.onMap() ? 0xFF7EE787 : 0xFFFF7777, false);
    }

    private void drawDirectorTelemetry(GuiGraphics graphics, RaceDirectorSnapshot snapshot) {
        if (menu.getMonitorType() != com.openwheelracing.content.block.entity.RaceMonitorType.DIRECTOR) return;
        SpeedTraceGraphRenderer.render(graphics, font, MAP_X, 350, MAP_WIDTH, 62);
        int count = Math.min(6, snapshot.teamCars().size());
        for (int i = 0; i < count; i++) {
            TeamCarRow car = snapshot.teamCars().get(i);
            graphics.drawString(font, fit("#" + car.entityId() + " " + car.riderName(), 86), MAP_X + (i % 2) * 90, 250 + (i / 2) * ROW_HEIGHT, car.entityId() == selectedTeamCarId ? 0xFF009E73 : 0xFFC9D1D9, false);
        }
    }

    private boolean isInsideTelemetryCars(double mouseX, double mouseY) {
        double localX = mouseX - leftPos, localY = mouseY - topPos;
        return localX >= MAP_X && localX <= MAP_X + MAP_WIDTH && localY >= 250 && localY < 250 + ((Math.min(6, menu.getSnapshot().teamCars().size()) + 1) / 2) * ROW_HEIGHT;
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
        if (localX >= LEFT_X && localX <= LEFT_X + COLUMN_WIDTH) {
            return row * 2;
        }
        if (localX >= RIGHT_X && localX <= RIGHT_X + COLUMN_WIDTH) {
            return row * 2 + 1;
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
                ? row.sessionName() + " / " + row.driverName() + "  " + formatLapTime(row.lapMillis())
                : row.driverName() + "  " + formatLapTime(row.lapMillis());
            graphics.drawString(font, fit(rowText, ROW_WIDTH), ROW_X, y, color, false);
        }
    }

    private void drawSelectedLap(GuiGraphics graphics) {
        RaceDirectorLapRow row = selectedRow();
        int y = 230;
        if (row == null) {
            graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.select_lap"), LEFT_X, y, 0xFFC9D1D9, false);
            return;
        }
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.details"), LEFT_X, y, 0xFFE8EDF2, false);
        graphics.drawString(font, fit(row.driverName(), COLUMN_WIDTH - 8), LEFT_X, y + 14, 0xFFE8EDF2, false);
        graphics.drawString(font, formatLapTime(row.lapMillis()) + " / CP " + row.checkpointCount(), LEFT_X, y + 26, row.invalidated() ? 0xFFFF7777 : 0xFF7EE787, false);
        if (menu.getSnapshot().archiveMode()) {
            graphics.drawString(font, fit(row.sessionName(), COLUMN_WIDTH - 8), LEFT_X, y + 38, 0xFFC9D1D9, false);
            return;
        }
        BlockPos pos = BlockPos.of(row.startFinishPos());
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.position", pos.getX(), pos.getY(), pos.getZ()), LEFT_X, y + 38, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.setup", row.power(), row.grip(), row.aero(), row.gearing()), LEFT_X, y + 50, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.condition", row.damagePercent(), row.tyreWearPercent()), LEFT_X, y + 62, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.abs", row.absEnabled() ? "ON" : "OFF"), LEFT_X, y + 74, 0xFFC9D1D9, false);
    }

    private RaceDirectorLapRow selectedRow() {
        return menu.getSnapshot().laps().stream().filter(row -> row.id() == selectedLapId).findFirst().orElse(null);
    }

    private void setMinimumLapTicks(int ticks) {
        OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorSetMinLapTicksMessage(Math.max(1, ticks)));
    }

    private void addLimitButtons(int limit, int x, int y, int step) {
        addRenderableWidget(Button.builder(Component.literal("-"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorSetErsLimitMessage(limit, -step)))
            .bounds(x, y, 18, 14)
            .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorSetErsLimitMessage(limit, step)))
            .bounds(x + 152, y, 18, 14)
            .build());
    }

    private void addConditionButtons(int modifier, int x, int y) {
        addRenderableWidget(Button.builder(Component.literal("-"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorCycleConditionModifierMessage(modifier, -1)))
            .bounds(x, y, 18, 14)
            .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorCycleConditionModifierMessage(modifier, 1)))
            .bounds(x + 152, y, 18, 14)
            .build());
    }

    private void drawMapState(GuiGraphics graphics, RaceDirectorSnapshot snapshot) {
        if (snapshot.trackMapScanRunning()) {
            graphics.drawString(font, mapScanProgress(snapshot), MAP_X + 8, MAP_Y + MAP_HEIGHT / 2 - 4, 0xFFFFD866, false);
        } else if (!snapshot.trackMap().present()) {
            graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.no_track_map"), MAP_X + 34, MAP_Y + MAP_HEIGHT / 2 - 4, 0xFF888888, false);
        }
    }

    private void drawWeatherTelemetry(GuiGraphics graphics, RaceDirectorSnapshot snapshot) {
        var moisture = menu.getMoistureSnapshot();
        if (moisture.totalSamples() <= 0) return;
        int x = MAP_X;
        int y = 42;
        graphics.fill(x - 2, y - 2, x + MAP_WIDTH + 2, y + 11, 0xD0101418);
        String estimate = moisture.estimatedSamples() > 0 ? " E" + moisture.estimatedSamples() : "";
        String summary = String.format(java.util.Locale.ROOT, "%s %dC D%d M%d W%d S%d%s",
            moistureName(moisture.conditionLevel()), Math.round(moisture.ambientTemperatureC()),
            moisture.percent(0), moisture.percent(1),
            moisture.percent(2), moisture.percent(3), estimate);
        graphics.drawString(font, fit(summary, MAP_WIDTH), x, y, moistureTextColor(moisture.conditionLevel()), false);
    }

    private static String moistureName(int level) {
        return switch (level) {
            case 0 -> "DRY";
            case 1 -> "DAMP";
            case 2 -> "WET";
            default -> "SOAK";
        };
    }

    private static int moistureTextColor(int level) {
        return switch (level) {
            case 0 -> 0xFFE8EDF2;
            case 1 -> 0xFF6FD5DF;
            case 2 -> 0xFF79B8FF;
            default -> 0xFF7897E8;
        };
    }

    private static String modifier(double value) {
        return String.format("%.2fx", value);
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

    private static String formatLapTime(int millis) {
        int minutes = millis / 60000;
        int seconds = millis / 1000 % 60;
        int milliseconds = millis % 1000;
        return String.format("%d:%02d.%03d", minutes, seconds, milliseconds);
    }

    private static String telemetryLapTime(int millis) {
        return millis > 0 ? formatLapTime(millis) : "--:--.---";
    }

    private class AutoDetectRangeSlider extends AbstractSliderButton {
        private AutoDetectRangeSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), sliderValue(autoDetectRadius));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(autoDetectRadius + "m"));
        }

        @Override
        protected void applyValue() {
            int steps = (AUTO_DETECT_MAX_RADIUS - AUTO_DETECT_MIN_RADIUS) / AUTO_DETECT_RADIUS_STEP;
            int selectedStep = (int) Math.round(value * steps);
            autoDetectRadius = AUTO_DETECT_MIN_RADIUS + selectedStep * AUTO_DETECT_RADIUS_STEP;
            updateMessage();
        }
    }

    private static double sliderValue(int radius) {
        int clamped = Math.max(AUTO_DETECT_MIN_RADIUS, Math.min(AUTO_DETECT_MAX_RADIUS, radius));
        return (clamped - AUTO_DETECT_MIN_RADIUS) / (double) (AUTO_DETECT_MAX_RADIUS - AUTO_DETECT_MIN_RADIUS);
    }
}
