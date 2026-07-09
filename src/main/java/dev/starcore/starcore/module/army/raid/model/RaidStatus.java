package dev.starcore.starcore.module.army.raid.model;

/**
 * 突袭状态枚举
 */
public enum RaidStatus {
    PENDING("待开始", 0),
    ACTIVE("进行中", 1),
    ENDED("已结束", 2),
    CANCELLED("已取消", 3),
    EXPIRED("已过期", 4);

    private final String displayName;
    private final int order;

    RaidStatus(String displayName, int order) {
        this.displayName = displayName;
        this.order = order;
    }

    public String displayName() { return displayName; }
    public int order() { return order; }

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean isFinished() {
        return this == ENDED || this == CANCELLED || this == EXPIRED;
    }
}