package dev.starcore.starcore.module.war;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

final class WarStateCodec {
    private WarStateCodec() {
    }

    static Properties toProperties(Map<WarPairKey, Instant> activeWars) {
        Properties properties = new Properties();
        List<Map.Entry<WarPairKey, Instant>> snapshot = activeWars.entrySet().stream()
            .sorted((left, right) -> {
                String leftKey = left.getKey().left() + ":" + left.getKey().right();
                String rightKey = right.getKey().left() + ":" + right.getKey().right();
                return leftKey.compareTo(rightKey);
            })
            .toList();
        properties.setProperty("count", String.valueOf(snapshot.size()));
        for (int index = 0; index < snapshot.size(); index++) {
            Map.Entry<WarPairKey, Instant> entry = snapshot.get(index);
            String prefix = "war." + index + '.';
            properties.setProperty(prefix + "left", entry.getKey().left().toString());
            properties.setProperty(prefix + "right", entry.getKey().right().toString());
            properties.setProperty(prefix + "declaredAt", entry.getValue().toString());
        }
        return properties;
    }

    static Map<WarPairKey, Instant> fromProperties(Properties properties) {
        int count = parseInt(properties.getProperty("count"), 0);
        Map<WarPairKey, Instant> activeWars = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String prefix = "war." + index + '.';
            NationId left = parseNationId(properties.getProperty(prefix + "left"));
            NationId right = parseNationId(properties.getProperty(prefix + "right"));
            Instant declaredAt = parseInstant(properties.getProperty(prefix + "declaredAt"));
            if (left == null || right == null || left.equals(right) || declaredAt == null) {
                continue;
            }
            activeWars.put(WarPairKey.of(left, right), declaredAt);
        }
        return Map.copyOf(activeWars);
    }

    private static NationId parseNationId(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new NationId(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
