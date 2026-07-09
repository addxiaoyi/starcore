package dev.starcore.starcore.territory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * 领土状态编解码器
 * 处理 Territory 和 SubRegion 的 Properties 序列化/反序列化
 */
public final class TerritoryStateCodec {

    private static final Logger LOGGER = Logger.getLogger("StarCore.TerritoryStateCodec");

    private TerritoryStateCodec() {}

    // ==================== 领土编解码 ====================

    /**
     * 将领土集合序列化为 Properties
     */
    public static Properties toPropertiesTerritories(Collection<Territory> territories) {
        Properties props = new Properties();
        List<Territory> sorted = territories.stream()
            .sorted(Comparator.comparing(Territory::getId, UUIDComparator))
            .toList();

        props.setProperty("territory.count", String.valueOf(sorted.size()));

        AtomicInteger index = new AtomicInteger(0);
        for (Territory territory : sorted) {
            String prefix = "territory." + index.getAndIncrement() + ".";

            props.setProperty(prefix + "id", territory.getId().toString());
            props.setProperty(prefix + "name", territory.getName());
            props.setProperty(prefix + "owner", territory.getOwnerId().toString());
            props.setProperty(prefix + "nation", territory.getNationId() != null ? territory.getNationId().toString() : "");
            props.setProperty(prefix + "world", territory.getWorldName());
            props.setProperty(prefix + "min_x", String.valueOf(territory.getMinX()));
            props.setProperty(prefix + "min_y", String.valueOf(territory.getMinY()));
            props.setProperty(prefix + "min_z", String.valueOf(territory.getMinZ()));
            props.setProperty(prefix + "max_x", String.valueOf(territory.getMaxX()));
            props.setProperty(prefix + "max_y", String.valueOf(territory.getMaxY()));
            props.setProperty(prefix + "max_z", String.valueOf(territory.getMaxZ()));
            props.setProperty(prefix + "type", territory.getType().name());
            props.setProperty(prefix + "enabled", String.valueOf(territory.isEnabled()));
            props.setProperty(prefix + "created", String.valueOf(territory.getCreatedTime()));

            // 出生点
            if (territory.getSpawnPoint() != null) {
                props.setProperty(prefix + "spawn.world", territory.getSpawnPoint().getWorld().getName());
                props.setProperty(prefix + "spawn.x", String.valueOf(territory.getSpawnPoint().getX()));
                props.setProperty(prefix + "spawn.y", String.valueOf(territory.getSpawnPoint().getY()));
                props.setProperty(prefix + "spawn.z", String.valueOf(territory.getSpawnPoint().getZ()));
            }

            // 权限
            StringBuilder permStr = new StringBuilder();
            for (Map.Entry<TerritoryPermission, PermissionLevel> entry : territory.getAllPermissions().entrySet()) {
                if (permStr.length() > 0) permStr.append(";");
                permStr.append(entry.getKey().name()).append(":").append(entry.getValue().name());
            }
            props.setProperty(prefix + "permissions", permStr.toString());

            // 成员
            StringBuilder memberStr = new StringBuilder();
            for (Map.Entry<UUID, PermissionLevel> entry : territory.getAllMembers().entrySet()) {
                if (memberStr.length() > 0) memberStr.append(";");
                memberStr.append(entry.getKey().toString()).append(":").append(entry.getValue().name());
            }
            props.setProperty(prefix + "members", memberStr.toString());

            // 子区域ID列表
            StringBuilder subregionStr = new StringBuilder();
            for (UUID subId : territory.getSubRegionIds()) {
                if (subregionStr.length() > 0) subregionStr.append(";");
                subregionStr.append(subId.toString());
            }
            props.setProperty(prefix + "subregions", subregionStr.toString());
        }

        return props;
    }

    /**
     * 从 Properties 反序列化领土集合
     */
    public static List<Territory> fromPropertiesTerritories(Properties props) {
        List<Territory> territories = new ArrayList<>();
        int count = parseInt(props.getProperty("territory.count"), 0);

        for (int i = 0; i < count; i++) {
            String prefix = "territory." + i + ".";
            try {
                UUID id = UUID.fromString(props.getProperty(prefix + "id"));
                String name = props.getProperty(prefix + "name");
                UUID ownerId = UUID.fromString(props.getProperty(prefix + "owner"));
                String nationStr = props.getProperty(prefix + "nation", "");
                UUID nationId = nationStr.isEmpty() ? null : UUID.fromString(nationStr);
                String world = props.getProperty(prefix + "world");
                int minX = parseInt(props.getProperty(prefix + "min_x"), 0);
                int minY = parseInt(props.getProperty(prefix + "min_y"), 0);
                int minZ = parseInt(props.getProperty(prefix + "min_z"), 0);
                int maxX = parseInt(props.getProperty(prefix + "max_x"), 0);
                int maxY = parseInt(props.getProperty(prefix + "max_y"), 0);
                int maxZ = parseInt(props.getProperty(prefix + "max_z"), 0);
                TerritoryType type = TerritoryType.valueOf(props.getProperty(prefix + "type", "RESIDENTIAL"));
                boolean enabled = Boolean.parseBoolean(props.getProperty(prefix + "enabled", "true"));
                long createdTime = parseLong(props.getProperty(prefix + "created"), System.currentTimeMillis());

                Territory territory = new Territory(id, name, ownerId, world, minX, minY, minZ, maxX, maxY, maxZ);
                territory.setNationId(nationId);
                territory.setType(type);
                territory.setEnabled(enabled);

                // 反射设置 createdTime
                try {
                    java.lang.reflect.Field field = Territory.class.getDeclaredField("createdTime");
                    field.setAccessible(true);
                    field.setLong(territory, createdTime);
                } catch (NoSuchFieldException e) {
                    LOGGER.fine("Field 'createdTime' not found in Territory class: " + e.getMessage());
                } catch (IllegalAccessException e) {
                    LOGGER.warning("Cannot access field 'createdTime': " + e.getMessage());
                }

                // 出生点
                String spawnWorld = props.getProperty(prefix + "spawn.world");
                if (spawnWorld != null) {
                    double x = parseDouble(props.getProperty(prefix + "spawn.x"), 0);
                    double y = parseDouble(props.getProperty(prefix + "spawn.y"), 0);
                    double z = parseDouble(props.getProperty(prefix + "spawn.z"), 0);
                    // 注意：这里需要世界对象，实际使用时需要通过 WorldProvider 获取
                    // territory.setSpawnPoint(new Location(Bukkit.getWorld(spawnWorld), x, y, z));
                }

                // 权限
                String permStr = props.getProperty(prefix + "permissions", "");
                if (!permStr.isEmpty()) {
                    for (String permEntry : permStr.split(";")) {
                        String[] parts = permEntry.split(":");
                        if (parts.length == 2) {
                            try {
                                TerritoryPermission perm = TerritoryPermission.valueOf(parts[0]);
                                PermissionLevel level = PermissionLevel.valueOf(parts[1]);
                                territory.setPermission(perm, level);
                            } catch (IllegalArgumentException e) {
                                LOGGER.fine("Invalid permission entry '" + permEntry + "': " + e.getMessage());
                            }
                        }
                    }
                }

                // 成员
                String memberStr = props.getProperty(prefix + "members", "");
                if (!memberStr.isEmpty()) {
                    for (String memberEntry : memberStr.split(";")) {
                        String[] parts = memberEntry.split(":");
                        if (parts.length == 2) {
                            try {
                                UUID playerId = UUID.fromString(parts[0]);
                                PermissionLevel level = PermissionLevel.valueOf(parts[1]);
                                territory.addMember(playerId, level);
                            } catch (IllegalArgumentException e) {
                                LOGGER.fine("Invalid member entry '" + memberEntry + "': " + e.getMessage());
                            }
                        }
                    }
                }

                // 子区域ID
                String subregionStr = props.getProperty(prefix + "subregions", "");
                if (!subregionStr.isEmpty()) {
                    for (String subIdStr : subregionStr.split(";")) {
                        try {
                            UUID subId = UUID.fromString(subIdStr.trim());
                            territory.addSubRegion(subId);
                        } catch (IllegalArgumentException e) {
                            LOGGER.fine("Invalid subregion UUID '" + subIdStr + "': " + e.getMessage());
                        }
                    }
                }

                territories.add(territory);
            } catch (Exception e) {
                // 跳过损坏的领土数据
                LOGGER.warning("Failed to load territory at index " + i + ": " + e.getMessage());
            }
        }

        return territories;
    }

    // ==================== 子区域编解码 ====================

    /**
     * 将子区域集合序列化为 Properties
     */
    public static Properties toPropertiesSubRegions(Collection<SubRegion> subRegions) {
        Properties props = new Properties();
        List<SubRegion> sorted = subRegions.stream()
            .sorted(Comparator.comparing(SubRegion::getId, UUIDComparator))
            .toList();

        props.setProperty("subregion.count", String.valueOf(sorted.size()));

        AtomicInteger index = new AtomicInteger(0);
        for (SubRegion subRegion : sorted) {
            String prefix = "subregion." + index.getAndIncrement() + ".";

            props.setProperty(prefix + "id", subRegion.getId().toString());
            props.setProperty(prefix + "parent", subRegion.getParentTerritoryId().toString());
            props.setProperty(prefix + "name", subRegion.getName());
            props.setProperty(prefix + "world", subRegion.getWorldName());
            props.setProperty(prefix + "min_x", String.valueOf(subRegion.getMinX()));
            props.setProperty(prefix + "min_y", String.valueOf(subRegion.getMinY()));
            props.setProperty(prefix + "min_z", String.valueOf(subRegion.getMinZ()));
            props.setProperty(prefix + "max_x", String.valueOf(subRegion.getMaxX()));
            props.setProperty(prefix + "max_y", String.valueOf(subRegion.getMaxY()));
            props.setProperty(prefix + "max_z", String.valueOf(subRegion.getMaxZ()));
            props.setProperty(prefix + "priority", String.valueOf(subRegion.getPriority()));
            props.setProperty(prefix + "inherit", String.valueOf(subRegion.isInheritPermissions()));
            props.setProperty(prefix + "description", subRegion.getDescription() != null ? subRegion.getDescription() : "");
            props.setProperty(prefix + "enabled", String.valueOf(subRegion.isEnabled()));
            props.setProperty(prefix + "created", String.valueOf(subRegion.getCreatedTime()));

            // 权限覆盖
            StringBuilder permStr = new StringBuilder();
            for (Map.Entry<TerritoryPermission, PermissionLevel> entry : subRegion.getOverridePermissions().entrySet()) {
                if (permStr.length() > 0) permStr.append(";");
                permStr.append(entry.getKey().name()).append(":").append(entry.getValue().name());
            }
            props.setProperty(prefix + "permissions", permStr.toString());

            // 成员
            StringBuilder memberStr = new StringBuilder();
            for (Map.Entry<UUID, PermissionLevel> entry : subRegion.getAllMembers().entrySet()) {
                if (memberStr.length() > 0) memberStr.append(";");
                memberStr.append(entry.getKey().toString()).append(":").append(entry.getValue().name());
            }
            props.setProperty(prefix + "members", memberStr.toString());
        }

        return props;
    }

    /**
     * 从 Properties 反序列化子区域集合
     */
    public static List<SubRegion> fromPropertiesSubRegions(Properties props) {
        List<SubRegion> subRegions = new ArrayList<>();
        int count = parseInt(props.getProperty("subregion.count"), 0);

        for (int i = 0; i < count; i++) {
            String prefix = "subregion." + i + ".";
            try {
                UUID id = UUID.fromString(props.getProperty(prefix + "id"));
                UUID parentId = UUID.fromString(props.getProperty(prefix + "parent"));
                String name = props.getProperty(prefix + "name");
                String world = props.getProperty(prefix + "world");
                int minX = parseInt(props.getProperty(prefix + "min_x"), 0);
                int minY = parseInt(props.getProperty(prefix + "min_y"), 0);
                int minZ = parseInt(props.getProperty(prefix + "min_z"), 0);
                int maxX = parseInt(props.getProperty(prefix + "max_x"), 0);
                int maxY = parseInt(props.getProperty(prefix + "max_y"), 0);
                int maxZ = parseInt(props.getProperty(prefix + "max_z"), 0);
                int priority = parseInt(props.getProperty(prefix + "priority"), 0);
                boolean inherit = Boolean.parseBoolean(props.getProperty(prefix + "inherit", "true"));
                String description = props.getProperty(prefix + "description", "");
                boolean enabled = Boolean.parseBoolean(props.getProperty(prefix + "enabled", "true"));
                long createdTime = parseLong(props.getProperty(prefix + "created"), System.currentTimeMillis());

                SubRegion subRegion = new SubRegion(id, name, parentId, world, minX, minY, minZ, maxX, maxY, maxZ);
                subRegion.setPriority(priority);
                subRegion.setInheritPermissions(inherit);
                subRegion.setDescription(description.isEmpty() ? null : description);
                subRegion.setEnabled(enabled);

                // 反射设置 createdTime
                try {
                    java.lang.reflect.Field field = SubRegion.class.getDeclaredField("createdTime");
                    field.setAccessible(true);
                    field.setLong(subRegion, createdTime);
                } catch (NoSuchFieldException e) {
                    LOGGER.fine("Field 'createdTime' not found in SubRegion class: " + e.getMessage());
                } catch (IllegalAccessException e) {
                    LOGGER.warning("Cannot access field 'createdTime': " + e.getMessage());
                }

                // 权限覆盖
                String permStr = props.getProperty(prefix + "permissions", "");
                if (!permStr.isEmpty()) {
                    for (String permEntry : permStr.split(";")) {
                        String[] parts = permEntry.split(":");
                        if (parts.length == 2) {
                            try {
                                TerritoryPermission perm = TerritoryPermission.valueOf(parts[0]);
                                PermissionLevel level = PermissionLevel.valueOf(parts[1]);
                                subRegion.setOverridePermission(perm, level);
                            } catch (IllegalArgumentException e) {
                                LOGGER.fine("Invalid permission entry '" + permEntry + "': " + e.getMessage());
                            }
                        }
                    }
                }

                // 成员
                String memberStr = props.getProperty(prefix + "members", "");
                if (!memberStr.isEmpty()) {
                    for (String memberEntry : memberStr.split(";")) {
                        String[] parts = memberEntry.split(":");
                        if (parts.length == 2) {
                            try {
                                UUID playerId = UUID.fromString(parts[0]);
                                PermissionLevel level = PermissionLevel.valueOf(parts[1]);
                                subRegion.addMember(playerId, level);
                            } catch (IllegalArgumentException e) {
                                LOGGER.fine("Invalid member entry '" + memberEntry + "': " + e.getMessage());
                            }
                        }
                    }
                }

                subRegions.add(subRegion);
            } catch (Exception e) {
                LOGGER.warning("Failed to load subregion at index " + i + ": " + e.getMessage());
            }
        }

        return subRegions;
    }

    // ==================== 工具方法 ====================

    private static final Comparator<UUID> UUIDComparator = Comparator.comparing(UUID::toString);

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isEmpty()) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isEmpty()) return fallback;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null || value.isEmpty()) return fallback;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
