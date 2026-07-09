package dev.starcore.starcore.module.dungeon;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 副本定义
 * 定义副本的基本配置和房间结构
 */
public record DungeonDefinition(
    String id,
    String name,
    List<String> description,
    DungeonDifficulty difficulty,
    String icon,
    int minPlayers,
    int maxPlayers,
    int recommendedLevel,
    int entryFee,
    DungeonRewards rewards,
    List<DungeonRoom> rooms,
    DungeonEntrance entrance,
    String templateWorld,
    Map<String, Object> customSettings
) {
    /**
     * 获取副本总房间数
     */
    public int totalRooms() {
        return rooms.size();
    }

    /**
     * 获取BOSS房间
     */
    public DungeonRoom getBossRoom() {
        return rooms.stream()
            .filter(r -> r.type() == DungeonRoomType.BOSS)
            .findFirst()
            .orElse(null);
    }

    /**
     * 获取初始房间
     */
    public DungeonRoom getStartRoom() {
        return rooms.isEmpty() ? null : rooms.get(0);
    }

    /**
     * 检查玩家数量是否有效
     */
    public boolean isValidPlayerCount(int count) {
        return count >= minPlayers && count <= maxPlayers;
    }
}
