package com.openwheelracing.client.debug;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.network.OWRNetwork;
import io.netty.channel.ChannelFutureListener;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;

/** Single-player-only, intermittent vehicle packet stalls. Never blocks a thread. */
public final class LocalVehicleNetworkTest {
    private record Deferred(Connection connection, Packet<?> packet, ChannelFutureListener listener,
                            boolean flush, long due) {}
    private static final ArrayDeque<Deferred> QUEUE = new ArrayDeque<>();
    private static int delayTicks;
    private static final IntermittentVehicleLag LAG = new IntermittentVehicleLag(new java.util.Random());
    private static boolean autoRewind = true;
    private static boolean sending;
    private static long tick;
    private static Connection session;
    private static int vehicleId = -1;
    private static Object sessionLevel;
    private static OpenwheelCarEntity visualCar;

    private LocalVehicleNetworkTest() {}

    public static int delayTicks() { return delayTicks; }
    public static boolean autoRewind() { return autoRewind; }
    public static boolean localSession() {
        Minecraft mc = Minecraft.getInstance();
        return mc.hasSingleplayerServer() && mc.getConnection() != null;
    }

    public static void setDelay(int ticks) {
        drain(true);
        delayTicks = Math.max(0, Math.min(40, ticks));
        LAG.configure(delayTicks, tick);
    }

    public static void discardBufferedVehiclePackets() { QUEUE.clear(); }

    public static void setAutoRewind(boolean value) { autoRewind = value; }

    public static boolean defer(Connection connection, Packet<?> packet, ChannelFutureListener listener,
                                boolean flush) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || sending || delayTicks == 0 || !localSession()
                || mc.getConnection().getConnection() != connection
                || !(mc.player != null && mc.player.getVehicle() instanceof OpenwheelCarEntity)) return false;
        boolean vehiclePacket = packet instanceof ServerboundMoveVehiclePacket;
        boolean inputPacket = packet instanceof ServerboundCustomPayloadPacket custom
            && (custom.payload() instanceof OWRNetwork.DriveInputMessage
                || custom.payload() instanceof com.openwheelracing.network.VehicleRollbackNetwork.Frame);
        if (!vehiclePacket && !inputPacket) return false;
        long due = LAG.releaseAt(tick);
        if (due <= tick && QUEUE.isEmpty()) return false;
        if (QUEUE.size() >= 512) drain(true);
        if (!QUEUE.isEmpty()) due = Math.max(due, QUEUE.peekLast().due);
        QUEUE.addLast(new Deferred(connection, packet, listener, flush, due));
        return true;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Connection current = mc.getConnection() == null ? null : mc.getConnection().getConnection();
        int currentVehicle = mc.player != null && mc.player.getVehicle() instanceof OpenwheelCarEntity car
            ? car.getId() : -1;
        boolean contextChanged = current != session || mc.level != sessionLevel;
        if (contextChanged || currentVehicle != vehicleId) {
            QUEUE.clear();
            if (visualCar != null) visualCar.clearCorrectionRenderOffset();
            visualCar = mc.player != null && mc.player.getVehicle() instanceof OpenwheelCarEntity car ? car : null;
            com.openwheelracing.client.hud.RewindNotification.clear();
            if (contextChanged) {
                delayTicks = 0;
                autoRewind = true;
            }
            session = current;
            sessionLevel = mc.level;
            vehicleId = currentVehicle;
            LAG.configure(delayTicks, tick);
        }
        if (!localSession()) delayTicks = 0;
        tick++;
        drain(false);
    }

    private static void drain(boolean all) {
        Minecraft mc = Minecraft.getInstance();
        Connection current = mc.getConnection() == null ? null : mc.getConnection().getConnection();
        sending = true;
        try {
            while (!QUEUE.isEmpty() && (all || QUEUE.peekFirst().due <= tick)) {
                Deferred item = QUEUE.removeFirst();
                if (item.connection == current && item.connection.isConnected()) {
                    item.connection.send(item.packet, item.listener, item.flush);
                }
            }
        } finally {
            sending = false;
        }
    }
}
