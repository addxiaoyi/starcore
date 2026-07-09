package dev.starcore.starcore.module.army.siege;

import dev.starcore.starcore.module.army.siege.model.SiegeState;
import dev.starcore.starcore.module.army.siege.model.SiegeType;
import dev.starcore.starcore.module.army.siege.model.SiegeUnit;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.Instant;
import java.util.UUID;

/**
 * 攻城器械状态编解码器
 * 用于将 SiegeUnit 序列化为字符串以便持久化
 */
public final class SiegeStateCodec {

    /**
     * 编码为字符串格式
     * 格式: id|nationId|type|crewSize|health|morale|world|x|y|z|deployedWorld|deployedX|deployedY|deployedZ|state|ammunition|experience|createdAt|lastUpdated|siegeTarget
     */
    public String encode(SiegeUnit siege) {
        StringBuilder sb = new StringBuilder();
        sb.append(siege.id().toString()).append("|");
        sb.append(siege.nationId().toString()).append("|");
        sb.append(siege.type().name()).append("|");
        sb.append(siege.crewSize()).append("|");
        sb.append(siege.health()).append("|");
        sb.append(siege.crewMorale()).append("|");
        sb.append(siege.location().getWorld().getName()).append("|");
        sb.append(siege.location().getX()).append("|");
        sb.append(siege.location().getY()).append("|");
        sb.append(siege.location().getZ()).append("|");

        // 部署位置
        if (siege.deployedLocation() != null) {
            sb.append(siege.deployedLocation().getWorld().getName()).append("|");
            sb.append(siege.deployedLocation().getX()).append("|");
            sb.append(siege.deployedLocation().getY()).append("|");
            sb.append(siege.deployedLocation().getZ()).append("|");
        } else {
            sb.append("null|null|null|null|");
        }

        sb.append(siege.state().name()).append("|");
        sb.append(siege.ammunition()).append("|");
        sb.append(siege.siegeExperience()).append("|");
        sb.append(siege.createdAt().toEpochMilli()).append("|");
        sb.append(siege.lastUpdated().toEpochMilli()).append("|");
        sb.append(siege.siegeTarget() != null ? siege.siegeTarget().toString() : "null");

        return sb.toString();
    }

    /**
     * 从字符串解码
     */
    public SiegeUnit decode(String data) {
        String[] parts = data.split("\\|");
        if (parts.length < 19) {
            throw new IllegalArgumentException("Invalid siege data: " + data);
        }

        int index = 0;
        UUID id = UUID.fromString(parts[index++]);
        UUID nationId = UUID.fromString(parts[index++]);
        SiegeType type = SiegeType.valueOf(parts[index++]);
        int crewSize = Integer.parseInt(parts[index++]);
        double health = Double.parseDouble(parts[index++]);
        double crewMorale = Double.parseDouble(parts[index++]);

        // 位置
        World world = org.bukkit.Bukkit.getWorld(parts[index++]);
        double x = Double.parseDouble(parts[index++]);
        double y = Double.parseDouble(parts[index++]);
        double z = Double.parseDouble(parts[index++]);
        Location location = new Location(world, x, y, z);

        // 部署位置
        Location deployedLocation = null;
        String deployedWorldName = parts[index++];
        if (!"null".equals(deployedWorldName)) {
            World deployedWorld = org.bukkit.Bukkit.getWorld(deployedWorldName);
            if (deployedWorld != null) {
                double dx = Double.parseDouble(parts[index++]);
                double dy = Double.parseDouble(parts[index++]);
                double dz = Double.parseDouble(parts[index++]);
                deployedLocation = new Location(deployedWorld, dx, dy, dz);
            } else {
                index += 3; // skip deployed coords
            }
        } else {
            index++; // skip x
            index++; // skip y
            index++; // skip z
        }

        SiegeState state = SiegeState.valueOf(parts[index++]);
        int ammunition = Integer.parseInt(parts[index++]);
        int siegeExperience = Integer.parseInt(parts[index++]);
        Instant createdAt = Instant.ofEpochMilli(Long.parseLong(parts[index++]));
        Instant lastUpdated = Instant.ofEpochMilli(Long.parseLong(parts[index++]));

        UUID siegeTarget = null;
        String targetStr = parts[index];
        if (!"null".equals(targetStr)) {
            siegeTarget = UUID.fromString(targetStr);
        }

        // 创建 SiegeUnit 并设置所有字段
        SiegeUnit siege = new SiegeUnit(
            id, nationId, type, crewSize, health, crewMorale,
            location, deployedLocation, state, ammunition, siegeExperience, createdAt
        );
        // 手动设置 lastUpdated（因为构造函数会自动设置）
        try {
            var field = SiegeUnit.class.getDeclaredField("lastUpdated");
            field.setAccessible(true);
            field.set(siege, lastUpdated);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // 字段不存在或无法访问，忽略
        }

        if (siegeTarget != null) {
            siege.setSiegeTarget(siegeTarget);
        }

        return siege;
    }
}