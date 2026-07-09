package dev.starcore.starcore.module.resource;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class ResourceStateCodec {
    private ResourceStateCodec() {
    }

    static Properties toProperties(Map<NationId, Map<String, Long>> stockpiles) {
        Properties properties = new Properties();
        List<Map.Entry<NationId, Map<String, Long>>> snapshot = stockpiles.entrySet().stream()
            .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
            .sorted((left, right) -> left.getKey().toString().compareTo(right.getKey().toString()))
            .toList();
        int index = 0;
        for (Map.Entry<NationId, Map<String, Long>> nationEntry : snapshot) {
            List<Map.Entry<String, Long>> values = nationEntry.getValue().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0L)
                .map(entry -> Map.entry(normalizeResource(entry.getKey()), entry.getValue()))
                .filter(entry -> !entry.getKey().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .toList();
            for (Map.Entry<String, Long> value : values) {
                String prefix = "stockpile." + index++ + '.';
                properties.setProperty(prefix + "nationId", nationEntry.getKey().toString());
                properties.setProperty(prefix + "type", value.getKey());
                properties.setProperty(prefix + "amount", String.valueOf(value.getValue()));
            }
        }
        properties.setProperty("count", String.valueOf(index));
        return properties;
    }

    static Map<NationId, Map<String, Long>> fromProperties(Properties properties, Set<String> allowedResourceTypes) {
        Set<String> allowed = allowedResourceTypes == null ? Set.of() : allowedResourceTypes.stream()
            .map(ResourceStateCodec::normalizeResource)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toUnmodifiableSet());
        int count = parseInt(properties.getProperty("count"), 0);
        Map<NationId, Map<String, Long>> stockpiles = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String prefix = "stockpile." + index + '.';
            NationId nationId = parseNationId(properties.getProperty(prefix + "nationId"));
            String type = normalizeResource(properties.getProperty(prefix + "type"));
            long amount = parseLong(properties.getProperty(prefix + "amount"), 0L);
            if (nationId != null && allowed.contains(type) && amount > 0L) {
                stockpiles.computeIfAbsent(nationId, ignored -> new LinkedHashMap<>()).put(type, amount);
            }
        }
        Map<NationId, Map<String, Long>> immutable = new LinkedHashMap<>();
        stockpiles.forEach((nationId, values) -> immutable.put(nationId, Map.copyOf(values)));
        return Map.copyOf(immutable);
    }

    private static String normalizeResource(String resourceType) {
        return resourceType == null ? "" : resourceType.trim().toLowerCase(Locale.ROOT);
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

    private static long parseLong(String value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
