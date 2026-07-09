package dev.starcore.starcore.module.army.mercenary;

import java.util.UUID;

/**
 * 雇佣兵类型
 */
public enum MercenaryType {
    INFANTRY("infantry", "步兵", 50, 1.0, 1.0, 0.8),
    CAVALRY("cavalry", "骑兵", 80, 1.5, 0.7, 1.2),
    ARCHER("archer", "弓兵", 60, 1.2, 1.0, 1.0),
    MAGE("mage", "法师", 100, 1.8, 0.5, 0.9),
    HEAVY_INFANTRY("heavy_infantry", "重步兵", 120, 0.8, 1.5, 0.6),
    SCOUT("scout", "斥候", 40, 0.9, 0.6, 1.5);

    private final String key;
    private final String displayName;
    private final int baseCost;
    private final double attackMultiplier;
    private final double defenseMultiplier;
    private final double mobilityMultiplier;

    MercenaryType(String key, String displayName, int baseCost,
                  double attackMultiplier, double defenseMultiplier, double mobilityMultiplier) {
        this.key = key;
        this.displayName = displayName;
        this.baseCost = baseCost;
        this.attackMultiplier = attackMultiplier;
        this.defenseMultiplier = defenseMultiplier;
        this.mobilityMultiplier = mobilityMultiplier;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public int baseCost() {
        return baseCost;
    }

    public double attackMultiplier() {
        return attackMultiplier;
    }

    public double defenseMultiplier() {
        return defenseMultiplier;
    }

    public double mobilityMultiplier() {
        return mobilityMultiplier;
    }

    /**
     * 计算雇佣成本
     * @param count 雇佣数量
     * @param durationDays 雇佣天数
     * @return 总成本
     */
    public int calculateCost(int count, int durationDays) {
        return baseCost * count * durationDays;
    }

    /**
     * 从key获取类型
     */
    public static MercenaryType fromKey(String key) {
        for (MercenaryType type : values()) {
            if (type.key.equalsIgnoreCase(key)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown mercenary type: " + key);
    }
}
