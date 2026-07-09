package dev.starcore.starcore.module.event;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

final class EventStateCodec {
    private EventStateCodec() {
    }

    static Properties toProperties(Map<NationId, List<NationEventRecord>> events) {
        Properties properties = new Properties();
        List<NationEventRecord> snapshot = events.values().stream()
            .flatMap(List::stream)
            .sorted(java.util.Comparator.comparing(NationEventRecord::occurredAt))
            .toList();
        properties.setProperty("count", String.valueOf(snapshot.size()));
        for (int index = 0; index < snapshot.size(); index++) {
            NationEventRecord record = snapshot.get(index);
            String prefix = "event." + index + '.';
            properties.setProperty(prefix + "id", record.id().toString());
            properties.setProperty(prefix + "nationId", record.nationId().toString());
            properties.setProperty(prefix + "occurredAt", record.occurredAt().toString());
            properties.setProperty(prefix + "type", record.type());
            properties.setProperty(prefix + "message", record.message());
            properties.setProperty(prefix + "context", record.context() == null ? "" : record.context());
        }
        return properties;
    }

    static Map<NationId, List<NationEventRecord>> fromProperties(Properties properties) {
        int count = parseInt(properties.getProperty("count"), 0);
        Map<NationId, java.util.ArrayList<NationEventRecord>> events = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String prefix = "event." + index + '.';
            UUID id = parseUuid(properties.getProperty(prefix + "id"));
            NationId nationId = parseNationId(properties.getProperty(prefix + "nationId"));
            Instant occurredAt = parseInstant(properties.getProperty(prefix + "occurredAt"));
            String type = normalize(properties.getProperty(prefix + "type"));
            String message = normalize(properties.getProperty(prefix + "message"));
            String context = normalize(properties.getProperty(prefix + "context"));
            if (id == null || nationId == null || occurredAt == null || type.isBlank() || message.isBlank()) {
                continue;
            }
            NationEventRecord record = new NationEventRecord(id, nationId, occurredAt, type, message, context);
            events.computeIfAbsent(nationId, ignored -> new java.util.ArrayList<>()).add(record);
        }
        Map<NationId, List<NationEventRecord>> immutable = new LinkedHashMap<>();
        events.forEach((nationId, records) -> immutable.put(nationId, List.copyOf(records)));
        return Map.copyOf(immutable);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static NationId parseNationId(String value) {
        UUID uuid = parseUuid(value);
        return uuid == null ? null : new NationId(uuid);
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
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
