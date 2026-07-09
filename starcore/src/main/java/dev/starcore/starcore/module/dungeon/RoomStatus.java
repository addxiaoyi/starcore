package dev.starcore.starcore.module.dungeon;

/**
 * 房间状态
 */
public enum RoomStatus {
    LOCKED("锁定"),
    AVAILABLE("可用"),
    IN_PROGRESS("进行中"),
    CLEARED("已清除"),
    FAILED("已失败"),
    SKIPPED("已跳过");

    private final String displayName;

    RoomStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
