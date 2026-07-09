package dev.starcore.starcore.module.diplomacy;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

final class DiplomacyStateCodec {
    private DiplomacyStateCodec() {
    }

    static Properties toProperties(Map<DiplomacyPairKey, DiplomacyRelation> relations) {
        Properties properties = new Properties();
        List<Map.Entry<DiplomacyPairKey, DiplomacyRelation>> snapshot = relations.entrySet().stream()
            .sorted((left, right) -> {
                String leftKey = left.getKey().left() + ":" + left.getKey().right();
                String rightKey = right.getKey().left() + ":" + right.getKey().right();
                return leftKey.compareTo(rightKey);
            })
            .toList();
        properties.setProperty("count", String.valueOf(snapshot.size()));
        for (int index = 0; index < snapshot.size(); index++) {
            Map.Entry<DiplomacyPairKey, DiplomacyRelation> entry = snapshot.get(index);
            String prefix = "relation." + index + '.';
            properties.setProperty(prefix + "left", entry.getKey().left().toString());
            properties.setProperty(prefix + "right", entry.getKey().right().toString());
            properties.setProperty(prefix + "relation", entry.getValue().name());
        }
        return properties;
    }

    static Map<DiplomacyPairKey, DiplomacyRelation> fromProperties(Properties properties) {
        int count = parseInt(properties.getProperty("count"), 0);
        Map<DiplomacyPairKey, DiplomacyRelation> relations = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String prefix = "relation." + index + '.';
            NationId left = parseNationId(properties.getProperty(prefix + "left"));
            NationId right = parseNationId(properties.getProperty(prefix + "right"));
            DiplomacyRelation relation = parseRelation(properties.getProperty(prefix + "relation"));
            // 跳过无效数据，但保留所有有效关系包括 NEUTRAL
            if (left == null || right == null || left.equals(right)) {
                continue;
            }
            relations.put(DiplomacyPairKey.of(left, right), relation);
        }
        return Map.copyOf(relations);
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

    private static DiplomacyRelation parseRelation(String value) {
        if (value == null) {
            return DiplomacyRelation.NEUTRAL;
        }
        try {
            return DiplomacyRelation.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return DiplomacyRelation.NEUTRAL;
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
