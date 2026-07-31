package com.openwheelracing.client.screen;

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
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class RaceDirectorScreen extends AbstractContainerScreen<RaceDirectorMenu> {
    private static final int ROW_X = 12;
    private static final int ROW_Y = 116;
    private static final int ROW_WIDTH = 200;
    private static final int ROW_HEIGHT = 12;
    private static final int RIGHT_X = 232;
    private long selectedLapId = -1L;
    private int selectedTeamCarId = -1;

    public RaceDirectorScreen(RaceDirectorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 390;
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
            graphics.fill(x + 6, y + 66, x + imageWidth - 6, y + 218, 0xFF2A3038);
            graphics.fill(x + 12, y + 118, x + 190, y + 210, 0xFF242A31);
            graphics.fill(x + 200, y + 118, x + 378, y + 210, 0xFF242A31);
        } else {
            graphics.fill(x + 6, y + 66, x + 224, y + 218, 0xFF2A3038);
            graphics.fill(x + 228, y + 66, x + imageWidth - 6, y + 218, 0xFF2A3038);
            graphics.fill(x + 232, y + 90, x + imageWidth - 10, y + 132, 0xFF242A31);
            graphics.fill(x + 232, y + 152, x + imageWidth - 10, y + 214, 0xFF242A31);
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
        if (menu.showsTeamTerminal() && event.button() == 0 && isInsideTeamList(event.x(), event.y())) {
            int index = (int) ((event.y() - topPos - 92) / ROW_HEIGHT);
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
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.checkpoints"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorToggleRuleMessage(OWRNetwork.RaceDirectorToggleRuleMessage.CHECKPOINTS)))
            .bounds(leftPos + 232, topPos + 68, 72, 16)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.off_track"), button -> OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorToggleRuleMessage(OWRNetwork.RaceDirectorToggleRuleMessage.OFF_TRACK)))
            .bounds(leftPos + 310, topPos + 68, 62, 16)
            .build());
        addRenderableWidget(Button.builder(Component.literal("-"), button -> setMinimumLapTicks(menu.getSnapshot().minimumValidLapTicks() - 20))
            .bounds(leftPos + 232, topPos + 138, 18, 14)
            .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> setMinimumLapTicks(menu.getSnapshot().minimumValidLapTicks() + 20))
            .bounds(leftPos + 254, topPos + 138, 18, 14)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.invalidate"), button -> {
            if (selectedLapId != -1L) {
                OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorInvalidateLapMessage(selectedLapId));
            }
        }).bounds(leftPos + 286, topPos + 224, 86, 16).build());
        addFlagTiles(leftPos + RIGHT_X, topPos + 90);
    }

    private void addTeamWidgets() {
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.team_terminal.sense_cars"), button -> OWRNetwork.sendToServer(new OWRNetwork.TeamTerminalSenseCarsMessage()))
            .bounds(leftPos + 16, topPos + 68, 86, 16)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.team_terminal.bind_left"), button -> bindSelectedTeamCar(0))
            .bounds(leftPos + 108, topPos + 68, 58, 16)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.team_terminal.bind_right"), button -> bindSelectedTeamCar(1))
            .bounds(leftPos + 172, topPos + 68, 62, 16)
            .build());
        addRenderableWidget(Button.builder(Component.literal("-L"), button -> OWRNetwork.sendToServer(new OWRNetwork.TeamTerminalBindCarMessage(0, -1)))
            .bounds(leftPos + 304, topPos + 68, 28, 16)
            .build());
        addRenderableWidget(Button.builder(Component.literal("-R"), button -> OWRNetwork.sendToServer(new OWRNetwork.TeamTerminalBindCarMessage(1, -1)))
            .bounds(leftPos + 336, topPos + 68, 28, 16)
            .build());
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
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.session_info"), 8, 58, 0xFFE8EDF2, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.countdown_placeholder"), 12, 88, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.lap_counter_placeholder", snapshot.laps().size()), 12, 100, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable(snapshot.archiveMode() ? "screen.openwheelracing.race_director.archived_laps" : "screen.openwheelracing.race_director.recent_laps"), 12, 108, 0xFFE8EDF2, false);
        drawLapRows(graphics, snapshot);
        drawSelectedLap(graphics);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.rule_setup"), RIGHT_X, 74, 0xFFE8EDF2, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.rule_status", state(snapshot.checkpointCheckEnabled()), state(snapshot.offTrackCheckEnabled()), formatSeconds(snapshot.minimumValidLapTicks())), RIGHT_X, 136, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.flag_assignment"), RIGHT_X, 156, 0xFFE8EDF2, false);
        graphics.drawString(font, fit(Component.translatable("screen.openwheelracing.race_director.driver_flags_placeholder").getString(), 148), RIGHT_X, 174, 0xFFC9D1D9, false);
    }

    private void drawTeamTerminal(GuiGraphics graphics, RaceDirectorSnapshot snapshot) {
        graphics.drawString(font, Component.translatable("screen.openwheelracing.team_terminal.title"), 12, 72, 0xFFE8EDF2, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.team_terminal.bind_hint"), 108, 88, 0xFFC9D1D9, false);
        List<TeamCarRow> cars = snapshot.teamCars();
        if (cars.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.openwheelracing.team_terminal.no_cars"), 16, 94, 0xFFC9D1D9, false);
            return;
        }
        drawTeamCarPanel(graphics, findTeamCar(snapshot.leftTeamCarId()), 20, 120, Component.translatable("screen.openwheelracing.team_terminal.left_car"));
        drawTeamCarPanel(graphics, findTeamCar(snapshot.rightTeamCarId()), 206, 120, Component.translatable("screen.openwheelracing.team_terminal.right_car"));
        int listY = 94;
        for (int index = 0; index < Math.min(8, cars.size()); index++) {
            TeamCarRow car = cars.get(index);
            int y = listY + index * ROW_HEIGHT;
            if (car.entityId() == selectedTeamCarId) {
                graphics.fill(14, y - 1, 196, y + ROW_HEIGHT - 1, 0xFF3F5F7F);
            }
            if (car.entityId() == snapshot.leftTeamCarId() || car.entityId() == snapshot.rightTeamCarId()) {
                graphics.fill(16, y, 20, y + 10, 0xFF7EE787);
            } else {
                graphics.fill(16, y, 20, y + 10, car.liveryColor());
            }
            graphics.drawString(font, fit("#" + car.entityId() + " " + car.liveryName() + " " + car.riderName(), 168), 24, y, 0xFFE8EDF2, false);
        }
    }

    private void drawTeamCarPanel(GuiGraphics graphics, TeamCarRow car, int x, int y, Component title) {
        if (car == null) {
            graphics.drawString(font, title, x, y, 0xFFE8EDF2, false);
            graphics.drawString(font, Component.translatable("screen.openwheelracing.team_terminal.empty_slot"), x, y + 18, 0xFFC9D1D9, false);
            return;
        }
        drawTeamCar(graphics, car, x, y, title);
    }

    private void drawTeamCar(GuiGraphics graphics, TeamCarRow car, int x, int y, Component title) {
        graphics.drawString(font, title, x, y, 0xFFE8EDF2, false);
        graphics.fill(x, y + 13, x + 10, y + 21, car.liveryColor());
        graphics.drawString(font, fit("#" + car.entityId() + " " + car.liveryName(), 126), x + 14, y + 13, 0xFFE8EDF2, false);
        graphics.drawString(font, fit(Component.translatable("screen.openwheelracing.team_terminal.rider", car.riderName()).getString(), 142), x, y + 26, 0xFFC9D1D9, false);
        graphics.drawString(font, String.format("SPD %3.0f  G%s  RPM %d", car.speedKmh(), gearLabel(car.gear()), car.rpm()), x, y + 40, 0xFFE8EDF2, false);
        graphics.drawString(font, String.format("ERS %3.0f%% %+.0fkW", car.ersPercent(), car.ersPowerKw()), x, y + 52, 0xFF99DDFF, false);
        graphics.drawString(font, String.format("TYRE %3.0f%% %3.0fC", car.tyrePercent(), car.tyreTemperatureC()), x, y + 64, 0xFFFFD866, false);
        graphics.drawString(font, String.format("DMG %3.0f%%", car.damagePercent()), x, y + 76, car.damagePercent() > 70.0f ? 0xFFFF7777 : 0xFFC9D1D9, false);
    }

    private boolean isInsideRows(double mouseX, double mouseY) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        return localX >= ROW_X && localX <= ROW_X + ROW_WIDTH && localY >= ROW_Y && localY <= ROW_Y + menu.getSnapshot().laps().size() * ROW_HEIGHT;
    }

    private boolean isInsideTeamList(double mouseX, double mouseY) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        return localX >= 14 && localX <= 196 && localY >= 92 && localY <= 92 + Math.min(8, menu.getSnapshot().teamCars().size()) * ROW_HEIGHT;
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
        int y = 176;
        if (row == null) {
            graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.select_lap"), RIGHT_X, y, 0xFFC9D1D9, false);
            return;
        }
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.details"), RIGHT_X, y, 0xFFE8EDF2, false);
        graphics.drawString(font, fit(row.driverName(), 104), RIGHT_X, y + 14, 0xFFE8EDF2, false);
        graphics.drawString(font, formatLapTime(row.lapTicks()) + " / CP " + row.checkpointCount(), RIGHT_X, y + 26, row.invalidated() ? 0xFFFF7777 : 0xFF7EE787, false);
        if (menu.getSnapshot().archiveMode()) {
            graphics.drawString(font, fit(row.sessionName(), 104), RIGHT_X, y + 38, 0xFFC9D1D9, false);
            return;
        }
        BlockPos pos = BlockPos.of(row.startFinishPos());
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.position", pos.getX(), pos.getY(), pos.getZ()), RIGHT_X, y + 38, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.setup", row.power(), row.grip(), row.aero(), row.gearing()), RIGHT_X, y + 50, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.condition", row.damagePercent(), row.tyreWearPercent()), RIGHT_X, y + 62, 0xFFC9D1D9, false);
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
