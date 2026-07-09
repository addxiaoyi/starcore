package dev.starcore.starcore.module.nation;

import dev.starcore.starcore.module.government.model.GovernmentType;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.NationKind;
import dev.starcore.starcore.module.nation.model.NationMember;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

final class NationStateCodec {
    private NationStateCodec() {
    }

    static Properties toProperties(Collection<Nation> nations) {
        Properties properties = new Properties();
        List<Nation> snapshot = nations.stream()
            .sorted((left, right) -> left.id().toString().compareTo(right.id().toString()))
            .toList();
        properties.setProperty("count", String.valueOf(snapshot.size()));
        for (int index = 0; index < snapshot.size(); index++) {
            Nation nation = snapshot.get(index);
            String prefix = "nation." + index + '.';
            properties.setProperty(prefix + "id", nation.id().toString());
            properties.setProperty(prefix + "name", nation.name());
            properties.setProperty(prefix + "founderId", nation.founderId().toString());
            properties.setProperty(prefix + "kind", nation.kind().name());
            if (nation.parentNationId() != null) {
                properties.setProperty(prefix + "parentNationId", nation.parentNationId().toString());
            }
            properties.setProperty(prefix + "government", nation.governmentType().name());
            properties.setProperty(prefix + "experience", String.valueOf(nation.experience()));
            properties.setProperty(prefix + "foundedAt", String.valueOf(nation.foundedAt().toEpochMilli()));

            // 序列化首都位置
            Location capitalLoc = nation.capitalLocation();
            if (capitalLoc != null) {
                String capitalLocStr = serializeLocation(capitalLoc);
                if (capitalLocStr != null) {
                    properties.setProperty(prefix + "capitalLocation", capitalLocStr);
                }
            }

            List<NationMember> members = nation.members().stream()
                .sorted((left, right) -> left.playerId().toString().compareTo(right.playerId().toString()))
                .toList();
            properties.setProperty(prefix + "memberCount", String.valueOf(members.size()));
            for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
                NationMember member = members.get(memberIndex);
                String memberPrefix = prefix + "member." + memberIndex + '.';
                properties.setProperty(memberPrefix + "id", member.playerId().toString());
                properties.setProperty(memberPrefix + "name", member.lastKnownName());
            }
        }
        return properties;
    }

    static List<Nation> fromProperties(Properties properties) {
        int count = parseInt(properties.getProperty("count"), 0);
        List<Nation> nations = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Nation nation = loadNation(properties, "nation." + index + '.');
            if (nation != null) {
                nations.add(nation);
            }
        }
        return List.copyOf(nations);
    }

    private static Nation loadNation(Properties properties, String prefix) {
        try {
            NationId id = new NationId(UUID.fromString(properties.getProperty(prefix + "id")));
            String name = properties.getProperty(prefix + "name");
            UUID founderId = UUID.fromString(properties.getProperty(prefix + "founderId"));
            NationKind kind = parseKind(properties.getProperty(prefix + "kind"));
            NationId parentNationId = parseNationId(properties.getProperty(prefix + "parentNationId"));
            int memberCount = parseInt(properties.getProperty(prefix + "memberCount"), 0);
            String founderName = properties.getProperty(prefix + "founderName", "Unknown");
            for (int memberIndex = 0; memberIndex < memberCount; memberIndex++) {
                String memberPrefix = prefix + "member." + memberIndex + '.';
                UUID playerId = UUID.fromString(properties.getProperty(memberPrefix + "id"));
                String playerName = properties.getProperty(memberPrefix + "name", "Unknown");
                if (playerId.equals(founderId)) {
                    founderName = playerName;
                    break;
                }
            }
            Nation nation = new Nation(id, name, founderId, founderName, kind, parentNationId,
                parseInstant(properties.getProperty(prefix + "foundedAt")));
            nation.setGovernmentType(parseGovernment(properties.getProperty(prefix + "government")));
            nation.setExperience(parseLong(properties.getProperty(prefix + "experience"), 0L));

            // 恢复首都位置
            Location capitalLocation = parseLocation(properties.getProperty(prefix + "capitalLocation"));
            if (capitalLocation != null) {
                nation.setCapitalLocation(capitalLocation);
            }

            for (int memberIndex = 0; memberIndex < memberCount; memberIndex++) {
                String memberPrefix = prefix + "member." + memberIndex + '.';
                UUID playerId = UUID.fromString(properties.getProperty(memberPrefix + "id"));
                String playerName = properties.getProperty(memberPrefix + "name", "Unknown");
                if (!playerId.equals(founderId)) {
                    nation.addMember(playerId, playerName);
                }
            }
            return nation;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static GovernmentType parseGovernment(String value) {
        if (value == null) {
            return GovernmentType.MONARCHY;
        }
        try {
            return GovernmentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return GovernmentType.MONARCHY;
        }
    }

    private static NationKind parseKind(String value) {
        if (value == null) {
            return NationKind.NATION;
        }
        try {
            return NationKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return NationKind.NATION;
        }
    }

    private static NationId parseNationId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new NationId(UUID.fromString(value));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null) {
            return Instant.now();
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return Instant.now();
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

    /**
     * 序列化 Location 为字符串格式: world,x,y,z,yaw,pitch
     */
    private static String serializeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        return String.format("%s,%.2f,%.2f,%.2f,%.2f,%.2f",
            loc.getWorld().getName(),
            loc.getX(), loc.getY(), loc.getZ(),
            loc.getYaw(), loc.getPitch());
    }

    /**
     * 从字符串格式解析 Location
     */
    private static Location parseLocation(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String[] parts = value.split(",");
            if (parts.length < 4) {
                return null;
            }
            String worldName = parts[0];
            World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world == null) {
                return null;
            }
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception exception) {
            return null;
        }
    }
}
