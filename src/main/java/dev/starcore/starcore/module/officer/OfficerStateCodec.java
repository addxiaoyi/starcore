package dev.starcore.starcore.module.officer;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

final class OfficerStateCodec {
    private OfficerStateCodec() {
    }

    static Properties toProperties(Map<NationId, Map<String, OfficerAppointment>> officers) {
        Properties properties = new Properties();
        Map<NationId, Map<String, OfficerAppointment>> snapshot = new LinkedHashMap<>();
        officers.entrySet().stream()
            .sorted((left, right) -> left.getKey().toString().compareTo(right.getKey().toString()))
            .forEach(entry -> snapshot.put(entry.getKey(), entry.getValue()));
        int index = 0;
        for (Map.Entry<NationId, Map<String, OfficerAppointment>> nationEntry : snapshot.entrySet()) {
            List<OfficerAppointment> appointments = nationEntry.getValue().values().stream()
                .sorted(java.util.Comparator.comparing(OfficerAppointment::role))
                .toList();
            for (OfficerAppointment appointment : appointments) {
                String prefix = "officer." + index++ + '.';
                properties.setProperty(prefix + "nationId", nationEntry.getKey().toString());
                properties.setProperty(prefix + "role", appointment.role());
                properties.setProperty(prefix + "playerId", appointment.playerId().toString());
                properties.setProperty(prefix + "playerName", appointment.playerName());
            }
        }
        properties.setProperty("count", String.valueOf(index));
        return properties;
    }

    static Map<NationId, Map<String, OfficerAppointment>> fromProperties(Properties properties) {
        int count = parseInt(properties.getProperty("count"), 0);
        Map<NationId, Map<String, OfficerAppointment>> officers = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String prefix = "officer." + index + '.';
            NationId nationId = parseNationId(properties.getProperty(prefix + "nationId"));
            String role = normalize(properties.getProperty(prefix + "role"));
            UUID playerId = parseUuid(properties.getProperty(prefix + "playerId"));
            String playerName = normalize(properties.getProperty(prefix + "playerName"));
            if (nationId != null && !role.isBlank() && playerId != null && !playerName.isBlank()) {
                officers.computeIfAbsent(nationId, ignored -> new LinkedHashMap<>())
                    .put(role, new OfficerAppointment(role, playerId, playerName));
            }
        }
        Map<NationId, Map<String, OfficerAppointment>> immutable = new LinkedHashMap<>();
        officers.forEach((nationId, byRole) -> immutable.put(nationId, Map.copyOf(byRole)));
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
