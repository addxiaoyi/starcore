package dev.starcore.starcore.module.army.siege;

import dev.starcore.starcore.module.army.siege.model.WallData;
import dev.starcore.starcore.module.army.siege.model.WallType;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.Instant;
import java.util.UUID;

/**
 * 城墙状态编解码器
 * 用于将 WallData 序列化为字符串以便持久化
 */
public final class WallStateCodec {

    /**
     * 编码为字符串格式
     * 格式: id|nationId|type|world|x|y|z|currentHealth|maxHealth|level|isUnderSiege|besiegingSiegeId|siegeStartTime|lastRepairTime
     */
    public String encode(WallData wall) {
        StringBuilder sb = new StringBuilder();
        sb.append(wall.id().toString()).append("|");
        sb.append(wall.nationId().toString()).append("|");
        sb.append(wall.type().name()).append("|");
        sb.append(wall.world()).append("|");
        sb.append(wall.blockX()).append("|");
        sb.append(wall.blockY()).append("|");
        sb.append(wall.blockZ()).append("|");
        sb.append(wall.currentHealth()).append("|");
        sb.append(wall.maxHealth()).append("|");
        sb.append(wall.level()).append("|");
        sb.append(wall.isUnderSiege()).append("|");
        sb.append(wall.besiegingSiegeId() != null ? wall.besiegingSiegeId().toString() : "null").append("|");
        sb.append(wall.siegeStartTime() != null ? wall.siegeStartTime().toEpochMilli() : "null").append("|");
        sb.append(wall.lastRepairTime() != null ? wall.lastRepairTime().toEpochMilli() : "null");

        return sb.toString();
    }

    /**
     * 从字符串解码
     */
    public WallData decode(String data) {
        String[] parts = data.split("\\|");
        if (parts.length < 14) {
            throw new IllegalArgumentException("Invalid wall data: " + data);
        }

        int index = 0;
        UUID id = UUID.fromString(parts[index++]);
        UUID nationId = UUID.fromString(parts[index++]);
        WallType type = WallType.valueOf(parts[index++]);

        String worldName = parts[index++];
        int blockX = Integer.parseInt(parts[index++]);
        int blockY = Integer.parseInt(parts[index++]);
        int blockZ = Integer.parseInt(parts[index++]);

        World world = org.bukkit.Bukkit.getWorld(worldName);
        Location location = new Location(world, blockX, blockY, blockZ);

        int currentHealth = Integer.parseInt(parts[index++]);
        int maxHealth = Integer.parseInt(parts[index++]);
        int level = Integer.parseInt(parts[index++]);
        boolean isUnderSiege = Boolean.parseBoolean(parts[index++]);

        UUID besiegingSiegeId = null;
        String siegeIdStr = parts[index++];
        if (!"null".equals(siegeIdStr)) {
            besiegingSiegeId = UUID.fromString(siegeIdStr);
        }

        Instant siegeStartTime = null;
        String siegeStartStr = parts[index++];
        if (!"null".equals(siegeStartStr)) {
            siegeStartTime = Instant.ofEpochMilli(Long.parseLong(siegeStartStr));
        }

        Instant lastRepairTime = null;
        String lastRepairStr = parts[index];
        if (!"null".equals(lastRepairStr)) {
            lastRepairTime = Instant.ofEpochMilli(Long.parseLong(lastRepairStr));
        }

        return new WallData(
            id, nationId, type, location,
            currentHealth, maxHealth, level,
            isUnderSiege, besiegingSiegeId, siegeStartTime, lastRepairTime
        );
    }
}