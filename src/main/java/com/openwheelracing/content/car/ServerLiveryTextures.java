package com.openwheelracing.content.car;

import com.openwheelracing.content.entity.OpenwheelCarEntity;
import com.openwheelracing.network.OWRNetwork;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.level.ServerPlayer;

public final class ServerLiveryTextures {
    private static final int MAX_SYNC_BYTES = 1_048_576;

    private ServerLiveryTextures() {
    }

    public static Path directory(net.minecraft.server.MinecraftServer server) {
        return server.getServerDirectory().resolve("openwheelracing").resolve("liveries");
    }

    public static Path file(net.minecraft.server.MinecraftServer server, String id) {
        return directory(server).resolve(CarLiveryTexture.sanitize(id) + ".png");
    }

    public static void save(net.minecraft.server.MinecraftServer server, String id, byte[] pngBytes) throws IOException {
        String safe = CarLiveryTexture.sanitize(id);
        if (safe.isEmpty() || pngBytes.length == 0 || pngBytes.length > MAX_SYNC_BYTES) {
            return;
        }
        Files.createDirectories(directory(server));
        Files.write(file(server, safe), pngBytes);
    }

    public static byte[] read(net.minecraft.server.MinecraftServer server, String id) {
        String safe = CarLiveryTexture.sanitize(id);
        if (safe.isEmpty()) {
            return new byte[0];
        }
        Path file = file(server, safe);
        if (!Files.isRegularFile(file)) {
            return new byte[0];
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            return bytes.length <= MAX_SYNC_BYTES ? bytes : new byte[0];
        } catch (IOException ignored) {
            return new byte[0];
        }
    }

    public static void syncToPlayer(OpenwheelCarEntity car, ServerPlayer player) {
        if (!car.getLiveryTexture().isPresent()) {
            return;
        }
        net.minecraft.server.MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        String id = car.getLiveryTexture().id();
        byte[] bytes = read(server, id);
        if (bytes.length > 0) {
            OWRNetwork.sendLiveryTexture(player, id, bytes);
        }
    }
}
