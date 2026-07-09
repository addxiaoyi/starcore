package dev.starcore.starcore.social.simulation;

/**
 * 文化冲突等级枚举
 *
 * 用于描述两个国家间的文化冲突严重程度
 */
public enum ConflictLevel {
    NONE("无冲突", 0, 0.0),
    MINOR("轻微冲突", 10, -0.10),      // 贸易-10%
    MODERATE("中等冲突", 30, -0.20),   // 关系-20%
    SEVERE("严重冲突", 60, -0.35);     // 可能引发战争

    private final String displayName;
    private final int tensionThreshold;  // 触发此等级需要的紧张度
    private final double tradePenalty;   // 贸易惩罚百分比

    ConflictLevel(String displayName, int tensionThreshold, double tradePenalty) {
        this.displayName = displayName;
        this.tensionThreshold = tensionThreshold;
        this.tradePenalty = tradePenalty;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getTensionThreshold() {
        return tensionThreshold;
    }

    public double getTradePenalty() {
        return tradePenalty;
    }

    /**
     * 从紧张度获取冲突等级
     */
    public static ConflictLevel fromTension(int tension) {
        if (tension >= SEVERE.tensionThreshold) return SEVERE;
        if (tension >= MODERATE.tensionThreshold) return MODERATE;
        if (tension >= MINOR.tensionThreshold) return MINOR;
        return NONE;
    }

    /**
     * 获取等级顺序值
     */
    public int ordinalValue() {
        return this.ordinal();
    }

    /**
     * 是否会产生战争风险
     */
    public boolean canTriggerWar() {
        return this == SEVERE;
    }
}
