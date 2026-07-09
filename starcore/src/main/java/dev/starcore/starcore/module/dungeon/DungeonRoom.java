package dev.starcore.starcore.module.dungeon;

import java.util.List;
import java.util.Map;

/**
 * 副本房间定义
 */
public record DungeonRoom(
    String id,
    DungeonRoomType type,
    String displayName,
    String mobType,
    int mobCount,
    List<String> traps,
    String puzzleType,
    int puzzleTimeLimitSeconds,
    DungeonBoss boss,
    int waveCount,
    List<String> allowedMobTypes,
    DungeonRoomClearCondition clearCondition,
    Map<String, Object> customSettings
) {
    /**
     * 检查是否为战斗房间
     */
    public boolean isCombatRoom() {
        return type == DungeonRoomType.SPAWNER || type == DungeonRoomType.BOSS;
    }

    /**
     * 检查是否有时间限制
     */
    public boolean hasTimeLimit() {
        return puzzleTimeLimitSeconds > 0 || clearCondition.type() == DungeonClearType.SURVIVE;
    }

    /**
     * 获取时间限制秒数
     */
    public int getTimeLimitSeconds() {
        if (clearCondition.type() == DungeonClearType.SURVIVE && clearCondition.surviveDurationSeconds() > 0) {
            return clearCondition.surviveDurationSeconds();
        }
        return puzzleTimeLimitSeconds;
    }
}
