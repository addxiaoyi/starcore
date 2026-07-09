package dev.starcore.starcore.foundation.sound;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public final class BukkitSoundResolver {
    private BukkitSoundResolver() {
    }

    public static Optional<Sound> resolve(String soundName) {
        if (soundName == null || soundName.isBlank()) {
            return Optional.empty();
        }
        NamespacedKey key = toKey(soundName);
        return key == null ? Optional.empty() : Optional.ofNullable(Registry.SOUND_EVENT.get(key));
    }

    public static Stream<String> names() {
        return Registry.SOUND_EVENT.keyStream()
            .map(NamespacedKey::getKey)
            .map(key -> key.toUpperCase(Locale.ROOT).replace('.', '_'));
    }

    private static NamespacedKey toKey(String soundName) {
        String normalized = soundName.trim().toLowerCase(Locale.ROOT);
        if (normalized.indexOf(':') >= 0) {
            return NamespacedKey.fromString(normalized);
        }
        return NamespacedKey.minecraft(normalized.replace('_', '.'));
    }
}
