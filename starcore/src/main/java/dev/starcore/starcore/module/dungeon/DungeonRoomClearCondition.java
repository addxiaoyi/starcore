package dev.starcore.starcore.module.dungeon;

import java.util.List;

/**
 * 房间清除条件
 */
public record DungeonRoomClearCondition(
    DungeonClearType type,
    int surviveDurationSeconds,
    List<String> requiredItems
) {
    public static DungeonRoomClearCondition killAll() {
        return new DungeonRoomClearCondition(DungeonClearType.KILL_ALL, 0, List.of());
    }

    public static DungeonRoomClearCondition survive(int seconds) {
        return new DungeonRoomClearCondition(DungeonClearType.SURVIVE, seconds, List.of());
    }

    public static DungeonRoomClearCondition solvePuzzle() {
        return new DungeonRoomClearCondition(DungeonClearType.SOLVE_PUZZLE, 0, List.of());
    }

    public static DungeonRoomClearCondition defeatBoss() {
        return new DungeonRoomClearCondition(DungeonClearType.DEFEAT_BOSS, 0, List.of());
    }

    public static DungeonRoomClearCondition surviveWaves(int waveCount) {
        return new DungeonRoomClearCondition(DungeonClearType.SURVIVE, 0, List.of());
    }
}
