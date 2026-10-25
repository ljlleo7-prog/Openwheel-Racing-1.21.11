package com.openwheelracing.client.screen;

import com.openwheelracing.content.race.OWRRaceControlState;
import com.openwheelracing.network.OWRNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicit lap selection for standalone sessions, independent of saved race settings. */
public final class NewRaceSessionScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 166;
    private final Screen parent;
    private final String initialName;
    private EditBox nameBox;
    private EditBox lapsBox;

    public NewRaceSessionScreen(Screen parent, String initialName) {
        super(Component.translatable("screen.openwheelracing.new_session.title"));
        this.parent = parent;
        this.initialName = initialName;
    }

    @Override
    protected void init() {
        String name = nameBox == null ? initialName : nameBox.getValue();
        String laps = lapsBox == null ? "0" : lapsBox.getValue();
        int x = (width - PANEL_WIDTH) / 2;
        int y = (height - PANEL_HEIGHT) / 2;
        nameBox = new EditBox(font, x + 16, y + 44, PANEL_WIDTH - 32, 18,
            Component.translatable("screen.openwheelracing.race_director.session_name_hint"));
        nameBox.setMaxLength(80);
        nameBox.setValue(name);
        addRenderableWidget(nameBox);
        lapsBox = new EditBox(font, x + 16, y + 82, 64, 18,
            Component.translatable("screen.openwheelracing.new_session.laps"));
        lapsBox.setMaxLength(3);
        lapsBox.setFilter(value -> value.matches("[0-9]*"));
        lapsBox.setValue(laps);
        addRenderableWidget(lapsBox);
        Button create = addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.new_session.create"), button -> {
            int lapLimit = Integer.parseInt(lapsBox.getValue());
            OWRNetwork.sendToServer(new OWRNetwork.RaceDirectorStartSessionMessage(nameBox.getValue().trim(), lapLimit));
            onClose();
        }).bounds(x + 16, y + 130, 128, 20).build());
        lapsBox.setResponder(value -> create.active = validLapLimit(value));
        create.active = validLapLimit(laps);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(x + 156, y + 130, 128, 20).build());
        setInitialFocus(nameBox);
    }

    private static boolean validLapLimit(String value) {
        return !value.isEmpty() && value.length() <= 3
            && Integer.parseInt(value) <= OWRRaceControlState.MAX_RACE_LAP_LIMIT;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF10151C);
        int x = (width - PANEL_WIDTH) / 2;
        int y = (height - PANEL_HEIGHT) / 2;
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xFF202832);
        graphics.drawString(font, title, x + 16, y + 12, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.race_director.session_name_hint"), x + 16, y + 32, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.new_session.laps"), x + 16, y + 70, 0xFFC9D1D9, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.new_session.unlimited"), x + 16, y + 110, 0xFFC9D1D9, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
