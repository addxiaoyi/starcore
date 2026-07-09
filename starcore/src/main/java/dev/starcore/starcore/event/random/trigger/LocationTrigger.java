package dev.starcore.starcore.event.random.trigger;

import dev.starcore.starcore.event.random.EventTrigger;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.block.Biome;

import java.util.HashSet;
import java.util.Set;

/**
 * 位置触发器
 * 根据玩家或事件位置触发
 */
public class LocationTrigger implements EventTrigger {

    private final LocationType locationType;
    private final Set<String> allowedValues;
    private final double radius;

    public LocationTrigger(LocationType locationType, Set<String> allowedValues, double radius) {
        this.locationType = locationType;
        this.allowedValues = new HashSet<>(allowedValues);
        this.radius = radius;
    }

    @Override
    public boolean check(Player player, Location location) {
        Location checkLocation = location != null ? location :
                                (player != null ? player.getLocation() : null);

        if (checkLocation == null) {
            return false;
        }

        switch (locationType) {
            case WORLD:
                String worldName = checkLocation.getWorld() != null ?
                                 checkLocation.getWorld().getName() : null;
                return worldName != null && allowedValues.contains(worldName);

            case BIOME:
                Biome biome = checkLocation.getBlock().getBiome();
                return allowedValues.contains(biome.getKey().toString());

            case COORDINATES:
                // 检查是否在指定坐标范围内
                for (String coordStr : allowedValues) {
                    if (isInRange(checkLocation, coordStr, radius)) {
                        return true;
                    }
                }
                return false;

            case Y_LEVEL:
                int y = checkLocation.getBlockY();
                for (String rangeStr : allowedValues) {
                    if (isInYRange(y, rangeStr)) {
                        return true;
                    }
                }
                return false;

            default:
                return false;
        }
    }

    /**
     * 检查位置是否在指定坐标范围内
     *
     * @param location 要检查的位置
     * @param coordStr 坐标字符串（格式：x,y,z）
     * @param radius 半径
     * @return 如果在范围内返回true
     */
    private boolean isInRange(Location location, String coordStr, double radius) {
        try {
            String[] parts = coordStr.split(",");
            if (parts.length < 2) {
                return false;
            }

            double targetX = Double.parseDouble(parts[0].trim());
            double targetZ = Double.parseDouble(parts[1].trim());

            double distance = Math.sqrt(
                Math.pow(location.getX() - targetX, 2) +
                Math.pow(location.getZ() - targetZ, 2)
            );

            return distance <= radius;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 检查Y坐标是否在范围内
     *
     * @param y Y坐标
     * @param rangeStr 范围字符串（格式：min-max 或 单个值）
     * @return 如果在范围内返回true
     */
    private boolean isInYRange(int y, String rangeStr) {
        try {
            if (rangeStr.contains("-")) {
                String[] parts = rangeStr.split("-");
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                return y >= min && y <= max;
            } else {
                int target = Integer.parseInt(rangeStr.trim());
                return y == target;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public String getType() {
        return "LOCATION";
    }

    @Override
    public String getDescription() {
        return String.format("位置触发器 [类型=%s, 值=%s, 半径=%.1f]",
                           locationType, allowedValues, radius);
    }

    public enum LocationType {
        WORLD,          // 世界名称
        BIOME,          // 生物群系
        COORDINATES,    // 坐标范围
        Y_LEVEL         // Y轴高度
    }
}
