package dev.starcore.starcore.module.diplomacy;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

final class DiplomacyWarCodec {
    private DiplomacyWarCodec() {
    }

    static Properties toProperties(Map<DiplomacyPairKey, WarRecord> warRecords) {
        Properties properties = new Properties();
        var snapshot = warRecords.entrySet().stream()
            .sorted((left, right) -> {
                String leftKey = left.getKey().left() + ":" + left.getKey().right();
                String rightKey = right.getKey().left() + ":" + right.getKey().right();
                return leftKey.compareTo(rightKey);
            })
            .toList();
        properties.setProperty("warCount", String.valueOf(snapshot.size()));
        for (int index = 0; index < snapshot.size(); index++) {
            Map.Entry<DiplomacyPairKey, WarRecord> entry = snapshot.get(index);
            String prefix = "war." + index + '.';
            properties.setProperty(prefix + "declarer", entry.getValue().declarer().value().toString());
            properties.setProperty(prefix + "target", entry.getValue().target().value().toString());
            properties.setProperty(prefix + "declaredAt", String.valueOf(entry.getValue().declaredAt().toEpochMilli()));
            properties.setProperty(prefix + "attacker", entry.getValue().attacker().value().toString());
        }
        return properties;
    }

    static Map<DiplomacyPairKey, WarRecord> fromProperties(Properties properties) {
        int count = parseInt(properties.getProperty("warCount"), 0);
        Map<DiplomacyPairKey, WarRecord> warRecords = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String prefix = "war." + index + '.';
            NationId declarer = parseNationId(properties.getProperty(prefix + "declarer"));
            NationId target = parseNationId(properties.getProperty(prefix + "target"));
            Instant declaredAt = parseInstant(properties.getProperty(prefix + "declaredAt"));
            NationId attacker = parseNationId(properties.getProperty(prefix + "attacker"));
            if (declarer == null || target == null || declaredAt == null) {
                continue;
            }
            if (attacker == null) {
                attacker = declarer;
            }
            WarRecord record = new WarRecord(declarer, target, declaredAt, attacker);
            warRecords.put(DiplomacyPairKey.of(declarer, target), record);
        }
        return Map.copyOf(warRecords);
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
            return Instant.ofEpochMilli(Long.parseLong(value));
        } catch (NumberFormatException exception) {
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
