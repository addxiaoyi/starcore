package dev.starcore.starcore.module.dungeon;

/**
 * 清除条件类型
 */
public enum DungeonClearType {
    KILL_ALL("清除所有敌人"),
    SURVIVE("存活指定时间"),
    SOLVE_PUZZLE("解开谜题"),
    DEFEAT_BOSS("击败BOSS"),
    COLLECT_ITEMS("收集物品"),
    SURVIVE_WAVES("存活所有波次");

    private final String displayName;

    DungeonClearType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
