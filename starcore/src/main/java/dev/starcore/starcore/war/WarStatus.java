package dev.starcore.starcore.war;

/**
 * 战争状态枚举
 */
public enum WarStatus {
    /**
     * 准备期
     * 宣战后的准备阶段，双方可以动员、筹集资金、征兵
     */
    PREPARATION("准备期", true, false),

    /**
     * 进行中
     * 战争正在进行，可以发起战斗
     */
    ACTIVE("进行中", true, true),

    /**
     * 停火
     * 临时停火状态，不能发起战斗但战争未结束
     */
    CEASEFIRE("停火", true, false),

    /**
     * 已结束
     * 战争已结束，和平条约已签署
     */
    ENDED("已结束", false, false);

    private final String displayName;
    private final boolean ongoing;          // 是否正在进行
    private final boolean combatAllowed;    // 是否允许战斗

    WarStatus(String displayName, boolean ongoing, boolean combatAllowed) {
        this.displayName = displayName;
        this.ongoing = ongoing;
        this.combatAllowed = combatAllowed;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isOngoing() {
        return ongoing;
    }

    public boolean isCombatAllowed() {
        return combatAllowed;
    }

    /**
     * 是否可以转换到目标状态
     */
    public boolean canTransitionTo(WarStatus target) {
        return switch (this) {
            case PREPARATION -> target == ACTIVE || target == ENDED;
            case ACTIVE -> target == CEASEFIRE || target == ENDED;
            case CEASEFIRE -> target == ACTIVE || target == ENDED;
            case ENDED -> false; // 已结束的战争不能转换状态
        };
    }
}
