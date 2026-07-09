package dev.starcore.starcore.mechanics;

/**
 * 声望来源
 * 定义声望的获取途径
 */
public enum ReputationSource {

    WAR("战争", 1.5),
    QUEST("任务", 1.0),
    TRADE("贸易", 0.8),
    CONSTRUCTION("建设", 1.2),
    DIPLOMACY("外交", 1.3),
    ACHIEVEMENT("成就", 1.1),
    EVENT("事件", 1.0),
    PVP("PVP", 1.4),
    BOSS_KILL("击杀首领", 2.0),
    EXPLORATION("探索", 0.9);

    private final String displayName;
    private final double multiplier;

    ReputationSource(String displayName, double multiplier) {
        this.displayName = displayName;
        this.multiplier = multiplier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getMultiplier() {
        return multiplier;
    }

    /**
     * 计算实际获得的声望值
     */
    public int calculate(int baseReputation) {
        return (int) (baseReputation * multiplier);
    }
}
