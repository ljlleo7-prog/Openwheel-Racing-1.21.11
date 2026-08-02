package com.openwheelracing.content.track;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TrackEditorUndoStore {
    private static final int MAX_UNDO_DEPTH = 20;
    private static final Map<Key, Deque<List<Entry>>> HISTORY = new HashMap<>();

    private TrackEditorUndoStore() {
    }

    public static void push(ServerPlayer player, List<Entry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        Deque<List<Entry>> stack = HISTORY.computeIfAbsent(Key.of(player), key -> new ArrayDeque<>());
        stack.push(List.copyOf(entries));
        while (stack.size() > MAX_UNDO_DEPTH) {
            stack.removeLast();
        }
    }

    public static void undo(ServerPlayer player) {
        Deque<List<Entry>> stack = HISTORY.get(Key.of(player));
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        List<Entry> entries = new ArrayList<>(stack.pop());
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            if (level.isInWorldBounds(entry.pos())) {
                level.setBlock(entry.pos(), entry.previousState(), 3);
            }
        }
    }

    private record Key(UUID playerId, ResourceKey<Level> dimension) {
        private static Key of(ServerPlayer player) {
            return new Key(player.getUUID(), player.level().dimension());
        }
    }

    public record Entry(BlockPos pos, BlockState previousState) {
    }
}
