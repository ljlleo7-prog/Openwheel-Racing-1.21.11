package com.openwheelracing.content.race.weekend;

import com.openwheelracing.content.race.session.RaceSessionState;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

/** Bounded read model shared by the Race Director weekend sub-screen. */
public record GrandPrixWeekendInfo(boolean present, String eventName, String eventState, int entryCount,
                                   int activeSessionIndex, String activeSessionName, String activeSessionType,
                                   String activeSessionFormat, String activeSessionState, String suspensionReason,
                                   long elapsedTicks, long remainingTicks, long countdownRemainingTicks,
                                   List<SessionInfo> sessions) {
    private static final int MAX_SESSIONS = 32;

    public GrandPrixWeekendInfo {
        eventName = clean(eventName);
        eventState = clean(eventState);
        activeSessionName = clean(activeSessionName);
        activeSessionType = clean(activeSessionType);
        activeSessionFormat = clean(activeSessionFormat);
        activeSessionState = clean(activeSessionState);
        suspensionReason = clean(suspensionReason);
        entryCount = Math.max(0, entryCount);
        activeSessionIndex = Math.max(-1, activeSessionIndex);
        elapsedTicks = Math.max(0L, elapsedTicks);
        remainingTicks = Math.max(0L, remainingTicks);
        countdownRemainingTicks = Math.max(0L, countdownRemainingTicks);
        sessions = List.copyOf(sessions == null ? List.of() : sessions.stream().limit(MAX_SESSIONS).toList());
    }

    public static GrandPrixWeekendInfo empty() {
        return new GrandPrixWeekendInfo(false, "", "", 0, -1, "", "", "", "", "", 0L, 0L, 0L, List.of());
    }

    public static GrandPrixWeekendInfo from(GrandPrixWeekend weekend, long serverTick) {
        List<GrandPrixWeekend.SessionView> views = weekend.sessions(serverTick);
        List<SessionInfo> sessions = views.stream().map(view -> new SessionInfo(view.config().name(),
            view.config().type().name(), view.config().format().name(), view.state().name(), view.config().durationTicks(),
            view.config().lapLimit(), view.grid().size(), view.result() != null && view.result().official())).toList();
        GrandPrixWeekend.SessionView active = weekend.activeSession(serverTick).orElse(null);
        return new GrandPrixWeekendInfo(true, weekend.name(), weekend.state().name(), weekend.entries().size(),
            weekend.activeSessionIndex(), active == null ? "" : active.config().name(),
            active == null ? "" : active.config().type().name(), active == null ? "" : active.config().format().name(),
            active == null ? "" : active.state().name(), active == null ? "" : active.suspensionReason().name(),
            active == null ? 0L : active.elapsedTicks(), active == null ? 0L : active.remainingTicks(),
            active == null ? 0L : active.countdownRemainingTicks(), sessions);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(present);
        buffer.writeUtf(eventName, 80);
        buffer.writeUtf(eventState, 24);
        buffer.writeVarInt(entryCount);
        buffer.writeInt(activeSessionIndex);
        buffer.writeUtf(activeSessionName, 40);
        buffer.writeUtf(activeSessionType, 24);
        buffer.writeUtf(activeSessionFormat, 32);
        buffer.writeUtf(activeSessionState, 24);
        buffer.writeUtf(suspensionReason, 24);
        buffer.writeLong(elapsedTicks);
        buffer.writeLong(remainingTicks);
        buffer.writeLong(countdownRemainingTicks);
        buffer.writeVarInt(sessions.size());
        sessions.forEach(session -> session.encode(buffer));
    }

    public static GrandPrixWeekendInfo decode(FriendlyByteBuf buffer) {
        boolean present = buffer.readBoolean();
        String eventName = buffer.readUtf(80);
        String eventState = buffer.readUtf(24);
        int entryCount = buffer.readVarInt();
        int activeIndex = buffer.readInt();
        String activeName = buffer.readUtf(40);
        String activeType = buffer.readUtf(24);
        String activeFormat = buffer.readUtf(32);
        String activeState = buffer.readUtf(24);
        String reason = buffer.readUtf(24);
        long elapsed = buffer.readLong();
        long remaining = buffer.readLong();
        long countdown = buffer.readLong();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_SESSIONS) {
            throw new IllegalArgumentException("GP session summary count exceeds " + MAX_SESSIONS);
        }
        java.util.ArrayList<SessionInfo> sessions = new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            sessions.add(SessionInfo.decode(buffer));
        }
        return new GrandPrixWeekendInfo(present, eventName, eventState, entryCount, activeIndex, activeName,
            activeType, activeFormat, activeState, reason, elapsed, remaining, countdown, sessions);
    }

    public boolean can(String action) {
        if (!present) return false;
        RaceSessionState state;
        try {
            state = RaceSessionState.valueOf(activeSessionState);
        } catch (IllegalArgumentException exception) {
            state = null;
        }
        return switch (action) {
            case "advance" -> eventState.equals("OPEN") && activeSessionIndex < sessions.size() - 1
                && (activeSessionIndex < 0 || state != null && state.isTerminal());
            case "stage" -> state == RaceSessionState.OPEN && (activeSessionType.equals("SPRINT") || activeSessionType.equals("RACE"));
            case "countdown" -> state == RaceSessionState.STAGING;
            case "start" -> state == RaceSessionState.OPEN && (activeSessionType.equals("PRACTICE") || activeSessionType.equals("QUALIFYING"));
            case "suspend" -> state != null && state.isActive();
            case "resume" -> state == RaceSessionState.SUSPENDED;
            case "finish" -> state == RaceSessionState.RUNNING || state == RaceSessionState.SUSPENDED;
            case "provisional" -> state == RaceSessionState.FINISHING;
            case "official" -> state == RaceSessionState.PROVISIONAL;
            case "complete" -> eventState.equals("OPEN") && activeSessionIndex == sessions.size() - 1 && state == RaceSessionState.OFFICIAL;
            default -> false;
        };
    }

    private static String clean(String value) {
        return value == null ? "" : value;
    }

    public record SessionInfo(String name, String type, String format, String state, long durationTicks,
                              int lapLimit, int gridSize, boolean official) {
        public SessionInfo {
            name = clean(name);
            type = clean(type);
            format = clean(format);
            state = clean(state);
            durationTicks = Math.max(0L, durationTicks);
            lapLimit = Math.max(0, lapLimit);
            gridSize = Math.max(0, gridSize);
        }

        private void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(name, 40);
            buffer.writeUtf(type, 24);
            buffer.writeUtf(format, 32);
            buffer.writeUtf(state, 24);
            buffer.writeLong(durationTicks);
            buffer.writeVarInt(lapLimit);
            buffer.writeVarInt(gridSize);
            buffer.writeBoolean(official);
        }

        private static SessionInfo decode(FriendlyByteBuf buffer) {
            return new SessionInfo(buffer.readUtf(40), buffer.readUtf(24), buffer.readUtf(32), buffer.readUtf(24),
                buffer.readLong(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
        }
    }
}
