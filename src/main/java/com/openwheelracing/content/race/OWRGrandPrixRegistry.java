package com.openwheelracing.content.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.openwheelracing.OpenwheelRacing;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Persistent server-wide driver entry registry for Grand Prix weekends. */
public final class OWRGrandPrixRegistry extends SavedData {
    public static final int MAX_GP_NAME_LENGTH = 80;
    public static final int MAX_DISPLAY_CODE_LENGTH = 16;
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("gp_key").forGetter(Entry::gpKey),
        Codec.STRING.fieldOf("gp_name").forGetter(Entry::gpName),
        UUID_CODEC.fieldOf("player_id").forGetter(Entry::playerId),
        Codec.STRING.fieldOf("player_name").forGetter(Entry::playerName),
        Codec.STRING.fieldOf("display_code").forGetter(Entry::displayCode),
        Codec.LONG.optionalFieldOf("registered_game_time", 0L).forGetter(Entry::registeredGameTime)
    ).apply(instance, Entry::new));
    private static final Codec<OWRGrandPrixRegistry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ENTRY_CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(OWRGrandPrixRegistry::entriesForCodec)
    ).apply(instance, OWRGrandPrixRegistry::new));
    private static final SavedDataType<OWRGrandPrixRegistry> TYPE = new SavedDataType<>(
        OpenwheelRacing.MODID + "_grand_prix_registry", OWRGrandPrixRegistry::new, CODEC, null);

    private final List<Entry> entries = new ArrayList<>();

    public OWRGrandPrixRegistry() {
    }

    private OWRGrandPrixRegistry(List<Entry> entries) {
        for (Entry entry : entries) {
            if (valid(entry)) {
                register(entry.gpName(), entry.playerId(), entry.playerName(), entry.displayCode(), entry.registeredGameTime());
            }
        }
    }

    public static OWRGrandPrixRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public RegistrationResult register(String gpName, UUID playerId, String playerName, String displayCode, long gameTime) {
        String cleanGpName = sanitizeGpName(gpName);
        String gpKey = key(cleanGpName);
        cleanGpName = entries.stream().filter(entry -> entry.gpKey().equals(gpKey)).map(Entry::gpName).findFirst().orElse(cleanGpName);
        String cleanCode = sanitizeDisplayCode(displayCode);
        String cleanPlayerName = playerName == null || playerName.isBlank() ? playerId.toString() : playerName.trim();
        Optional<Entry> collision = entries.stream()
            .filter(entry -> entry.gpKey().equals(gpKey) && entry.displayCode().equalsIgnoreCase(cleanCode))
            .filter(entry -> !entry.playerId().equals(playerId))
            .findFirst();
        if (collision.isPresent()) {
            return new RegistrationResult(false, false, collision.get());
        }

        Optional<Entry> previous = entry(cleanGpName, playerId);
        Entry updated = new Entry(gpKey, cleanGpName, playerId, cleanPlayerName, cleanCode, Math.max(0L, gameTime));
        entries.removeIf(entry -> entry.gpKey().equals(gpKey) && entry.playerId().equals(playerId));
        entries.add(updated);
        setDirty();
        return new RegistrationResult(true, previous.isPresent(), updated);
    }

    public boolean unregister(String gpName, UUID playerId) {
        String gpKey = key(sanitizeGpName(gpName));
        boolean removed = entries.removeIf(entry -> entry.gpKey().equals(gpKey) && entry.playerId().equals(playerId));
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public Optional<Entry> entry(String gpName, UUID playerId) {
        String gpKey = key(sanitizeGpName(gpName));
        return entries.stream().filter(entry -> entry.gpKey().equals(gpKey) && entry.playerId().equals(playerId)).findFirst();
    }

    public List<Entry> entries(String gpName) {
        String gpKey = key(sanitizeGpName(gpName));
        return entries.stream().filter(entry -> entry.gpKey().equals(gpKey))
            .sorted(Comparator.comparing(Entry::displayCode, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::playerName))
            .toList();
    }

    public List<String> grandPrixNames() {
        return entries.stream().map(Entry::gpName).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public static String sanitizeGpName(String value) {
        String clean = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (clean.isEmpty() || clean.length() > MAX_GP_NAME_LENGTH) {
            throw new IllegalArgumentException("GP ID/name must be 1-" + MAX_GP_NAME_LENGTH + " characters");
        }
        return clean;
    }

    public static String sanitizeDisplayCode(String value) {
        String clean = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (clean.isEmpty() || clean.length() > MAX_DISPLAY_CODE_LENGTH || clean.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Display code must be 1-" + MAX_DISPLAY_CODE_LENGTH + " characters without spaces");
        }
        return clean;
    }

    private static String key(String gpName) {
        return gpName.toLowerCase(Locale.ROOT);
    }

    private static boolean valid(Entry entry) {
        try {
            return entry != null && key(sanitizeGpName(entry.gpName())).equals(entry.gpKey())
                && sanitizeDisplayCode(entry.displayCode()).equals(entry.displayCode());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private List<Entry> entriesForCodec() {
        return List.copyOf(entries);
    }

    public record Entry(String gpKey, String gpName, UUID playerId, String playerName, String displayCode,
                        long registeredGameTime) {
    }

    public record RegistrationResult(boolean registered, boolean updated, Entry entry) {
    }
}
