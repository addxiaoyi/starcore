package dev.starcore.starcore.module.technology;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

final class TechnologyStateCodec {
    private TechnologyStateCodec() {
    }

    static Properties toProperties(Map<NationId, Set<String>> unlocked) {
        Properties properties = new Properties();
        List<Map.Entry<NationId, Set<String>>> snapshot = unlocked.entrySet().stream()
            .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
            .sorted((left, right) -> left.getKey().toString().compareTo(right.getKey().toString()))
            .toList();
        properties.setProperty("count", String.valueOf(snapshot.size()));
        for (int index = 0; index < snapshot.size(); index++) {
            Map.Entry<NationId, Set<String>> entry = snapshot.get(index);
            String prefix = "technology." + index + '.';
            List<String> values = entry.getValue().stream()
                .map(TechnologyStateCodec::normalizeTechnology)
                .filter(value -> !value.isBlank())
                .sorted()
                .toList();
            properties.setProperty(prefix + "nationId", entry.getKey().toString());
            properties.setProperty(prefix + "count", String.valueOf(values.size()));
            for (int technologyIndex = 0; technologyIndex < values.size(); technologyIndex++) {
                properties.setProperty(prefix + "key." + technologyIndex, values.get(technologyIndex));
            }
        }
        return properties;
    }

    static Map<NationId, Set<String>> fromProperties(Properties properties, Set<String> allowedTechnologies) {
        Set<String> allowed = allowedTechnologies == null ? Set.of() : allowedTechnologies.stream()
            .map(TechnologyStateCodec::normalizeTechnology)
            .filter(value -> !value.isBlank())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        int count = parseInt(properties.getProperty("count"), 0);
        Map<NationId, Set<String>> unlocked = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String prefix = "technology." + index + '.';
            NationId nationId = parseNationId(properties.getProperty(prefix + "nationId"));
            int technologyCount = parseInt(properties.getProperty(prefix + "count"), 0);
            if (nationId == null) {
                continue;
            }
            Set<String> values = new LinkedHashSet<>();
            for (int technologyIndex = 0; technologyIndex < technologyCount; technologyIndex++) {
                String key = normalizeTechnology(properties.getProperty(prefix + "key." + technologyIndex));
                if (allowed.contains(key)) {
                    values.add(key);
                }
            }
            if (!values.isEmpty()) {
                unlocked.put(nationId, Set.copyOf(values));
            }
        }
        return Map.copyOf(unlocked);
    }

    private static String normalizeTechnology(String technologyKey) {
        return technologyKey == null ? "" : technologyKey.trim().toLowerCase(Locale.ROOT);
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
