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
            minecraft.gui.setTitle(Component.translatable("screen.openwheelracing.race_director.flag." + next.key()));
        }
    }

    public static RaceFlagMode getGlobalFlag() {
        return globalFlag;
    }
}
