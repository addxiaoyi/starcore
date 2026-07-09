package dev.starcore.starcore.event.random;

import org.bukkit.entity.Player;
import org.bukkit.Location;

/**
 * 事件触发器接口
 * 用于检查随机事件是否应该触发
 */
public interface EventTrigger {

    /**
     * 检查触发条件是否满足
     *
     * @param player 触发玩家（可能为null，用于全局事件）
     * @param location 触发位置（可能为null）
     * @return 如果条件满足返回true
     */
    boolean check(Player player, Location location);

    /**
     * 获取触发器类型名称
     *
     * @return 触发器类型
     */
    String getType();

    /**
     * 获取触发器描述
     *
     * @return 触发器的可读描述
     */
    String getDescription();
}
