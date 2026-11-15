package com.openwheelracing.client.debug;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Admission is bound to the current connection and challenge, never a persisted preference. */
public final class TechnicalClientState {
    private static ClientPacketListener connection;
    private static UUID nonce;
    private static boolean verified;
    private TechnicalClientState() {}
    public static void begin(UUID challenge) {
        connection = Minecraft.getInstance().getConnection();
        nonce = challenge; verified = false;
    }
    public static void accept(UUID challenge) {
        if (connection == Minecraft.getInstance().getConnection() && challenge.equals(nonce)) verified = true;
    }
    public static boolean ready() {
        return verified && connection != null && connection == Minecraft.getInstance().getConnection();
    }
}
