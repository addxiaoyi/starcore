package dev.starcore.starcore.module.army.model;

/**
 * 兵种类型
 */
public enum ArmyType {
    /**
     * 步兵 - 平衡型单位
     * 攻击: 10, 防御: 15, 生命: 100, 机动: 1.0
     */
    INFANTRY("infantry", 10, 15, 100, 1.0, 100),

    /**
     * 骑兵 - 高机动单位
     * 攻击: 15, 防御: 10, 生命: 80, 机动: 2.0
     */
    CAVALRY("cavalry", 15, 10, 80, 2.0, 200),

    /**
     * 弓箭手 - 远程单位
     * 攻击: 12, 防御: 8, 生命: 70, 机动: 1.5
     */
    ARCHER("archer", 12, 8, 70, 1.5, 150),

    /**
     * 攻城器械 - 攻城专用
     * 攻击: 5, 防御: 20, 生命: 150, 机动: 0.5
     */
    SIEGE("siege", 5, 20, 150, 0.5, 300),

    /**
     * 守军 - 防御专用
     * 攻击: 8, 防御: 25, 生命: 120, 机动: 0
     */
    DEFENSIVE("defensive", 8, 25, 120, 0.0, 50);

    private final String key;
    private final int baseAttack;
    private final int baseDefense;
    private final int baseHealth;
    private final double mobility;
    private final int costPerUnit;

    ArmyType(String key, int baseAttack, int baseDefense, int baseHealth, double mobility, int costPerUnit) {
        this.key = key;
        this.baseAttack = baseAttack;
        this.baseDefense = baseDefense;
        this.baseHealth = baseHealth;
        this.mobility = mobility;
        this.costPerUnit = costPerUnit;
    }

    public String key() {
        return key;
    }

    public int baseAttack() {
        return baseAttack;
    }

    public int baseDefense() {
        return baseDefense;
    }

    public int baseHealth() {
        return baseHealth;
    }

    public double mobility() {
        return mobility;
    }

    public int costPerUnit() {
        return costPerUnit;
    }

    /**
     * 计算总成本
     */
    public int totalCost(int soldiers) {
        return costPerUnit * soldiers;
    }

    /**
     * 是否克制目标兵种
     */
    public boolean counters(ArmyType target) {
        return switch (this) {
            case INFANTRY -> target == ARCHER;
            case ARCHER -> target == CAVALRY;
            case CAVALRY -> target == INFANTRY;
            case SIEGE -> false; // 攻城器械不克制其他单位
            case DEFENSIVE -> false; // 守军不克制其他单位
        };
    }

    /**
     * 攻城效率（相对于普通单位的倍数）
     */
    public double siegeEfficiency() {
        return this == SIEGE ? 3.0 : 1.0;
    }

    /**
     * 从字符串解析
     */
    public static ArmyType fromString(String str) {
        for (ArmyType type : values()) {
            if (type.key.equalsIgnoreCase(str) || type.name().equalsIgnoreCase(str)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown army type: " + str);
    }
}
