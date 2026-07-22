package com.openwheelracing.client.screen;

import com.openwheelracing.content.menu.RaceDirectorMenu;
import com.openwheelracing.content.race.RaceDirectorLapRow;
import com.openwheelracing.content.race.RaceDirectorSnapshot;
import com.openwheelracing.network.OWRNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.network.PacketDistributor;

public class RaceDirectorScreen extends AbstractContainerScreen<RaceDirectorMenu> {
    private static final int ROW_X = 12;
    private static final int ROW_Y = 74;
    private static final int ROW_WIDTH = 176;
    private static final int ROW_HEIGHT = 12;
    private long selectedLapId = -1L;

    public RaceDirectorScreen(RaceDirectorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 300;
        imageHeight = 204;
        inventoryLabelY = 1000;
    }

    public static void applySnapshot(RaceDirectorSnapshot snapshot) {
        if (Minecraft.getInstance().screen instanceof RaceDirectorScreen screen) {
            screen.menu.applySnapshot(snapshot);
            if (screen.selectedLapId != -1L && screen.selectedRow() == null) {
                screen.selectedLapId = -1L;
            }
            screen.rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.checkpoints"), button -> OWRNetwork.CHANNEL.send(new OWRNetwork.RaceDirectorToggleRuleMessage(OWRNetwork.RaceDirectorToggleRuleMessage.CHECKPOINTS), PacketDistributor.SERVER.noArg()))
            .bounds(leftPos + 12, topPos + 22, 112, 16)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.off_track"), button -> OWRNetwork.CHANNEL.send(new OWRNetwork.RaceDirectorToggleRuleMessage(OWRNetwork.RaceDirectorToggleRuleMessage.OFF_TRACK), PacketDistributor.SERVER.noArg()))
            .bounds(leftPos + 130, topPos + 22, 112, 16)
            .build());
        addRenderableWidget(Button.builder(Component.literal("-"), button -> setMinimumLapTicks(menu.getSnapshot().minimumValidLapTicks() - 20))
            .bounds(leftPos + 247, topPos + 22, 18, 16)
            .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> setMinimumLapTicks(menu.getSnapshot().minimumValidLapTicks() + 20))
            .bounds(leftPos + 268, topPos + 22, 18, 16)
            .build());
        addLimitButtons(OWRNetwork.RaceDirectorSetErsLimitMessage.CAPACITY, leftPos + 204, topPos + 92, 1);
        addLimitButtons(OWRNetwork.RaceDirectorSetErsLimitMessage.BALANCED_DEPLOY, leftPos + 204, topPos + 108, 10);
        addLimitButtons(OWRNetwork.RaceDirectorSetErsLimitMessage.ATTACK_DEPLOY, leftPos + 204, topPos + 124, 10);
        addLimitButtons(OWRNetwork.RaceDirectorSetErsLimitMessage.HARVEST_NEGATIVE, leftPos + 204, topPos + 140, 10);
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.previous"), button -> OWRNetwork.CHANNEL.send(new OWRNetwork.RaceDirectorSetPageMessage(Math.max(0, menu.getPage() - 1)), PacketDistributor.SERVER.noArg()))
            .bounds(leftPos + 12, topPos + 166, 60, 16)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.next"), button -> OWRNetwork.CHANNEL.send(new OWRNetwork.RaceDirectorSetPageMessage(menu.getPage() + 1), PacketDistributor.SERVER.noArg()))
            .bounds(leftPos + 78, topPos + 166, 60, 16)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.race_director.invalidate"), button -> {
            if (selectedLapId != -1L) {
                OWRNetwork.CHANNEL.send(new OWRNetwork.RaceDirectorInvalidateLapMessage(selectedLapId), PacketDistributor.SERVER.noArg());
            }
        }).bounds(leftPos + 196, topPos + 166, 92, 16).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF1F2328);
        graphics.fill(x + 6, y + 16, x + imageWidth - 6, y + 48, 0xFF2F3640);
        graphics.fill(x + 6, y + 66, x + 190, y + 160, 0xFF2A3038);
        graphics.fill(x + 194, y + 66, x + imageWidth - 6, y + 160, 0xFF2A3038);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        RaceDirectorSnapshot snapshot = menu.getSnapshot();
        graphics.drawString(font, title, 8, 6, 0xFFE8EDF2, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.rules"), 8, 54, 0xFFE8EDF2, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.recent_laps"), 8, 66, 0xFFE8EDF2, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.details"), 198, 66, 0xFFE8EDF2, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.rule_status", state(snapshot.checkpointCheckEnabled()), state(snapshot.offTrackCheckEnabled()), formatSeconds(snapshot.minimumValidLapTicks())), 12, 42, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.ers_limits"), 198, 82, 0xFFE8EDF2, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.ers_capacity", snapshot.maxErsCapacityMj()), 226, 94, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.ers_balanced", snapshot.maxBalancedDeployKw()), 226, 110, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.ers_attack", snapshot.maxAttackDeployKw()), 226, 126, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.ers_harvest", snapshot.maxHarvestNegativeKw()), 226, 142, 0xFFC9D1D9, false);
        drawLapRows(graphics, snapshot);
        drawSelectedLap(graphics);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.page", snapshot.page() + 1, snapshot.maxPage() + 1), 146, 170, 0xFFC9D1D9, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && isInsideRows(event.x(), event.y())) {
            int index = (int) ((event.y() - topPos - ROW_Y) / ROW_HEIGHT);
            if (index >= 0 && index < menu.getSnapshot().laps().size()) {
                selectedLapId = menu.getSnapshot().laps().get(index).id();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean isInsideRows(double mouseX, double mouseY) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        return localX >= ROW_X && localX <= ROW_X + ROW_WIDTH && localY >= ROW_Y && localY <= ROW_Y + menu.getSnapshot().laps().size() * ROW_HEIGHT;
    }

    private void drawLapRows(GuiGraphics graphics, RaceDirectorSnapshot snapshot) {
        if (snapshot.laps().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.no_laps"), ROW_X, ROW_Y, 0xFFC9D1D9, false);
            return;
        }
        for (int index = 0; index < snapshot.laps().size(); index++) {
            RaceDirectorLapRow row = snapshot.laps().get(index);
            int y = ROW_Y + index * ROW_HEIGHT;
            if (row.id() == selectedLapId) {
                graphics.fill(ROW_X - 2, y - 1, ROW_X + ROW_WIDTH, y + ROW_HEIGHT - 1, 0xFF3F5F7F);
            }
            int color = row.invalidated() ? 0xFFFF7777 : 0xFFE8EDF2;
            graphics.drawString(font, row.driverName() + "  " + formatLapTime(row.lapTicks()), ROW_X, y, color, false);
        }
    }

    private void drawSelectedLap(GuiGraphics graphics) {
        RaceDirectorLapRow row = selectedRow();
        if (row == null) {
            graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.select_lap"), 198, 154, 0xFFC9D1D9, false);
            return;
        }
        int y = 154;
        graphics.drawString(font, row.driverName(), 198, y, 0xFFE8EDF2, false);
        graphics.drawString(font, formatLapTime(row.lapTicks()) + " / CP " + row.checkpointCount(), 198, y + 12, row.invalidated() ? 0xFFFF7777 : 0xFF7EE787, false);
        BlockPos pos = BlockPos.of(row.startFinishPos());
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.position", pos.getX(), pos.getY(), pos.getZ()), 198, y + 24, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.setup", row.power(), row.grip(), row.aero(), row.gearing()), 198, y + 40, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.condition", row.damagePercent(), row.tyreWearPercent()), 198, y + 52, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.abs", row.absEnabled() ? "ON" : "OFF"), 198, y + 64, 0xFFC9D1D9, false);
    }

    private RaceDirectorLapRow selectedRow() {
        return menu.getSnapshot().laps().stream().filter(row -> row.id() == selectedLapId).findFirst().orElse(null);
    }

    private void setMinimumLapTicks(int ticks) {
        OWRNetwork.CHANNEL.send(new OWRNetwork.RaceDirectorSetMinLapTicksMessage(Math.max(1, ticks)), PacketDistributor.SERVER.noArg());
    }

    private void addLimitButtons(int limit, int x, int y, int step) {
        addRenderableWidget(Button.builder(Component.literal("-"), button -> OWRNetwork.CHANNEL.send(new OWRNetwork.RaceDirectorSetErsLimitMessage(limit, -step), PacketDistributor.SERVER.noArg()))
            .bounds(x, y, 18, 14)
            .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> OWRNetwork.CHANNEL.send(new OWRNetwork.RaceDirectorSetErsLimitMessage(limit, step), PacketDistributor.SERVER.noArg()))
            .bounds(x + 74, y, 18, 14)
            .build());
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
