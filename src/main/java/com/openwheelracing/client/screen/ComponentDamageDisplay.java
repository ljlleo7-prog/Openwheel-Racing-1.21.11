package com.openwheelracing.client.screen;

import com.openwheelracing.content.car.CarComponentDamage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class ComponentDamageDisplay {
    private ComponentDamageDisplay() {
    }

    static void draw(GuiGraphics graphics, Font font, CarComponentDamage damage, int x, int y) {
        graphics.drawString(font, Component.translatable("screen.openwheelracing.damage.components"), x, y, 0xFF404040, false);
        drawValue(graphics, font, "screen.openwheelracing.damage.front_wing", damage.frontEnd(), x, y + 13);
        drawValue(graphics, font, "screen.openwheelracing.damage.rear_wing", damage.rearEnd(), x, y + 25);
        drawValue(graphics, font, "screen.openwheelracing.damage.chassis", damage.chassis(), x, y + 37);
        drawValue(graphics, font, "screen.openwheelracing.damage.engine", damage.engine(), x, y + 49);
        drawValue(graphics, font, "screen.openwheelracing.damage.fl_wheel", damage.frontLeftWheel(), x, y + 61);
        drawValue(graphics, font, "screen.openwheelracing.damage.fr_wheel", damage.frontRightWheel(), x, y + 73);
        drawValue(graphics, font, "screen.openwheelracing.damage.rl_wheel", damage.rearLeftWheel(), x, y + 85);
        drawValue(graphics, font, "screen.openwheelracing.damage.rr_wheel", damage.rearRightWheel(), x, y + 97);
    }

    static void drawCompact(GuiGraphics graphics, Font font, CarComponentDamage damage, int x, int y, int color) {
        graphics.drawString(font, String.format("FW %d  RW %d  CH %d  EN %d", damage.frontEnd(), damage.rearEnd(), damage.chassis(), damage.engine()), x, y, color, false);
        graphics.drawString(font, String.format("FL %d  FR %d  RL %d  RR %d", damage.frontLeftWheel(), damage.frontRightWheel(), damage.rearLeftWheel(), damage.rearRightWheel()), x, y + 12, color, false);
    }

    private static void drawValue(GuiGraphics graphics, Font font, String key, int damage, int x, int y) {
        int color = damage >= 75 ? 0xFFCC3333 : damage >= 40 ? 0xFFB26A00 : 0xFF404040;
        graphics.drawString(font, Component.translatable("screen.openwheelracing.damage.component_value", Component.translatable(key), damage), x, y, color, false);
    }
}
