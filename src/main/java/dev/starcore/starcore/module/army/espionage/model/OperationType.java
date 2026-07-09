package dev.starcore.starcore.module.army.espionage.model;

import java.util.UUID;

/**
 * 间谍行动类型
 */
public enum OperationType {
    /**
     * 侦察 - 收集目标国家基本信息
     */
    RECON("recon", 1, 0.1, 30, 500.0),

    /**
     * 窃取资源 - 偷取目标国库资源
     */
    STEAL_RESOURCES("steal_resources", 3, 0.25, 50, 2000.0),

    /**
     * 破坏设施 - 降低目标国家设施效率
     */
    SABOTAGE("sabotage", 4, 0.3, 60, 3000.0),

    /**
     * 暗杀官员 - 消灭目标国家的高级成员
     */
    ASSASSINATE("assassinate", 5, 0.4, 80, 5000.0),

    /**
     * 煽动叛变 - 说服目标国家成员叛逃
     */
    INCITE_DEFECTION("incite_defection", 4, 0.35, 70, 4000.0),

    /**
     * 窃取科技 - 获取目标国家科技信息
     */
    STEAL_TECHNOLOGY("steal_technology", 3, 0.2, 45, 2500.0),

    /**
     * 破坏外交 - 损害目标国家外交关系
     */
    DIPLOMATIC_SABOTAGE("diplomatic_sabotage", 2, 0.15, 40, 1500.0),

    /**
     * 间谍渗透 - 安插双面间谍
     */
    INFILTRATE("infiltrate", 5, 0.5, 100, 10000.0);

    private final String key;
    private final int difficulty;        // 难度等级 (1-5)
    private final double riskFactor;      // 风险系数
    private final int requiredExp;        // 所需最低经验
    private final double cost;           // 行动成本

    OperationType(String key, int difficulty, double riskFactor, int requiredExp, double cost) {
        this.key = key;
        this.difficulty = difficulty;
        this.riskFactor = riskFactor;
        this.requiredExp = requiredExp;
        this.cost = cost;
    }

    public String key() {
        return key;
    }

    public int difficulty() {
        return difficulty;
    }

    public double riskFactor() {
        return riskFactor;
    }

    public int requiredExp() {
        return requiredExp;
    }

    public double cost() {
        return cost;
    }

    public static OperationType fromString(String str) {
        for (OperationType type : values()) {
            if (type.key.equalsIgnoreCase(str) || type.name().equalsIgnoreCase(str)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown operation type: " + str);
    }
}
