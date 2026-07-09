package dev.starcore.starcore.module.army.siege.model;

/**
 * 攻城器械类型
 */
public enum SiegeType {
    /**
     * 撞锤 - 近距离攻城，对城门伤害极高
     * 范围: 10, 基础伤害: 50, 攻城倍数: 3.0
     */
    BATTERING_RAM("battering-ram", "撞锤", 10, 50, 3.0, 5000, 100),

    /**
     * 投石车 - 中距离攻城，范围伤害
     * 范围: 50, 基础伤害: 30, 攻城倍数: 2.0
     */
    CATAPULT("catapult", "投石车", 50, 30, 2.0, 8000, 150),

    /**
     * 投石机 - 远程攻城，高伤害
     * 范围: 100, 基础伤害: 45, 攻城倍数: 2.5
     */
    TREBUCHET("trebuchet", "投石机", 100, 45, 2.5, 12000, 200),

    /**
     * 弩炮 - 远程精确打击
     * 范围: 80, 基础伤害: 25, 攻城倍数: 1.5
     */
    BALLISTA("ballista", "弩炮", 80, 25, 1.5, 6000, 120),

    /**
     * 攻城塔 - 移动攻城塔，可运送士兵
     * 范围: 15, 基础伤害: 20, 攻城倍数: 2.0
     */
    TOWER("tower", "攻城塔", 15, 20, 2.0, 15000, 250);

    private final String key;
    private final String displayName;
    private final int effectiveRange;
    private final double baseDamage;
    private final double siegeDamageMultiplier;
    private final int constructionCost;
    private final int maintenanceCostPerHour;

    SiegeType(String key, String displayName, int effectiveRange, double baseDamage,
              double siegeDamageMultiplier, int constructionCost, int maintenanceCostPerHour) {
        this.key = key;
        this.displayName = displayName;
        this.effectiveRange = effectiveRange;
        this.baseDamage = baseDamage;
        this.siegeDamageMultiplier = siegeDamageMultiplier;
        this.constructionCost = constructionCost;
        this.maintenanceCostPerHour = maintenanceCostPerHour;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public int effectiveRange() {
        return effectiveRange;
    }

    public double baseDamage() {
        return baseDamage;
    }

    public double siegeDamageMultiplier() {
        return siegeDamageMultiplier;
    }

    public int constructionCost() {
        return constructionCost;
    }

    public int maintenanceCostPerHour() {
        return maintenanceCostPerHour;
    }

    /**
     * 计算总建造成本
     */
    public int totalCost(int crewSize) {
        return constructionCost + (crewSize * 50);
    }

    /**
     * 计算有效伤害（考虑器械状态）
     */
    public double effectiveDamage(double healthPercent, double crewMorale, boolean isSiegeMode) {
        double healthMod = healthPercent / 100.0;
        double moraleMod = crewMorale / 100.0;
        double siegeMod = isSiegeMode ? siegeDamageMultiplier : 1.0;
        return baseDamage * healthMod * moraleMod * siegeMod;
    }

    /**
     * 从字符串解析
     */
    public static SiegeType fromString(String str) {
        for (SiegeType type : values()) {
            if (type.key.equalsIgnoreCase(str) || type.name().equalsIgnoreCase(str)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown siege type: " + str);
    }

    /**
     * 获取所有类型
     */
    public static SiegeType[] valuesExcluding(SiegeType exclude) {
        SiegeType[] result = new SiegeType[values().length - 1];
        int index = 0;
        for (SiegeType type : values()) {
            if (type != exclude) {
                result[index++] = type;
            }
        }
        return result;
    }
}