package dev.starcore.starcore.module.army.raid.model;

/**
 * 突袭阶段枚举
 */
public enum RaidPhase {
    PREPARATION("准备阶段", 0),
    COMBAT("战斗阶段", 1),
    WITHDRAWAL("撤退阶段", 2),
    ENDED("已结束", 3);

    private final String displayName;
    private final int order;

    RaidPhase(String displayName, int order) {
        this.displayName = displayName;
        this.order = order;
    }

    public String displayName() { return displayName; }
    public int order() { return order; }

    public boolean canTransitionTo(RaidPhase next) {
        return next.order == this.order + 1 || (this == PREPARATION && next == ENDED);
    }
}