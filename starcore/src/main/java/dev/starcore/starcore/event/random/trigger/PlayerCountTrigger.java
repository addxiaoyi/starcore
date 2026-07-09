package dev.starcore.starcore.event.random.trigger;

import dev.starcore.starcore.event.random.EventTrigger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Location;

/**
 * 玩家数量触发器
 * 根据在线玩家数量触发事件
 */
public class PlayerCountTrigger implements EventTrigger {

    private final int minPlayers;
    private final int maxPlayers;
    private final CountType countType;
    private final String worldName;
    private final double radius;

    /**
     * 创建玩家数量触发器
     *
     * @param minPlayers 最少玩家数
     * @param maxPlayers 最多玩家数
     * @param countType 计数类型
     * @param worldName 世界名称（仅用于WORLD类型）
     * @param radius 半径（仅用于NEARBY类型）
     */
    public PlayerCountTrigger(int minPlayers, int maxPlayers, CountType countType,
                             String worldName, double radius) {
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.countType = countType;
        this.worldName = worldName;
        this.radius = radius;
    }

    @Override
    public boolean check(Player player, Location location) {
        int playerCount;

        switch (countType) {
            case GLOBAL:
                // 全局在线玩家数
                playerCount = Bukkit.getOnlinePlayers().size();
                break;

            case WORLD:
                // 指定世界的玩家数
                if (worldName == null || Bukkit.getWorld(worldName) == null) {
                    return false;
                }
                playerCount = (int) Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.getWorld().getName().equals(worldName))
                    .count();
                break;

            case NEARBY:
                // 附近玩家数
                Location checkLocation = location != null ? location :
                                       (player != null ? player.getLocation() : null);
                if (checkLocation == null) {
                    return false;
                }

                playerCount = (int) Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.getWorld().equals(checkLocation.getWorld()))
                    .filter(p -> p.getLocation().distance(checkLocation) <= radius)
                    .count();
                break;

            default:
                return false;
        }

        return playerCount >= minPlayers && playerCount <= maxPlayers;
    }

    @Override
    public String getType() {
        return "PLAYER_COUNT";
    }

    @Override
    public String getDescription() {
        return String.format("玩家数量触发器 [类型=%s, 范围=%d-%d]",
                           countType, minPlayers, maxPlayers);
    }

    public enum CountType {
        GLOBAL,     // 全局
        WORLD,      // 指定世界
        NEARBY      // 附近区域
    }
}
