package dev.starcore.starcore.module.visualizer;

import java.util.Locale;
import java.util.Optional;

public enum VisualizerDisplayMode {
    ITEM_STAND("itemstand", "ItemStands"),
    ITEM_DROP("itemdrop", "ItemDrops"),
    HOLOGRAM("hologram", "Holograms");

    private final String key;
    private final String displayName;

    VisualizerDisplayMode(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<VisualizerDisplayMode> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        for (VisualizerDisplayMode mode : values()) {
            String key = mode.key.replace("_", "").replace("-", "");
            if (key.equals(normalized) || mode.name().toLowerCase(Locale.ROOT).replace("_", "").equals(normalized)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}

