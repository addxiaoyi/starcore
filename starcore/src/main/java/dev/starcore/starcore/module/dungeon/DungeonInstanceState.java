package dev.starcore.starcore.module.dungeon;

/**
 * 副本实例状态
 */
public enum DungeonInstanceState {
    WAITING("等待中"),
    IN_PROGRESS("进行中"),
    PAUSED("已暂停"),
    COMPLETED("已完成"),
    FAILED("已失败"),
    TIMEOUT("已超时"),
    CANCELLED("已取消");

    private final String displayName;

    DungeonInstanceState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isActive() {
        return this == IN_PROGRESS || this == WAITING;
    }

    public boolean isFinished() {
        return this == COMPLETED || this == FAILED || this == TIMEOUT || this == CANCELLED;
    }
}
