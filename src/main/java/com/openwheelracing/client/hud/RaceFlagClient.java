package com.openwheelracing.client.hud;

import com.openwheelracing.content.race.RaceFlagMode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RaceFlagClient {
    private static volatile RaceFlagMode globalFlag = RaceFlagMode.DEFAULT;

    private RaceFlagClient() {
    }

    public static void setGlobalFlag(RaceFlagMode flag, boolean announce) {
        RaceFlagMode next = flag == null ? RaceFlagMode.DEFAULT : flag;
        boolean changed = globalFlag != next;
        globalFlag = next;
        if (announce && changed) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gui.setTitle(Component.translatable("screen.openwheelracing.race_director.flag." + next.key()).withColor(flagColor(next)));
        }
    }

    public static RaceFlagMode getGlobalFlag() {
        return globalFlag;
    }

    private static int flagColor(RaceFlagMode flag) {
        return switch (flag) {
            case GREEN -> 0x7EE787;
            case YELLOW, VIRTUAL_SAFETY_CAR -> 0xFFD866;
            case RED -> 0xFF7777;
            case SAFETY_CAR -> 0x79C0FF;
        };
    }
}
