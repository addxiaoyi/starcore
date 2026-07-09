package dev.starcore.starcore.module.army.model;

/**
 * 军队状态
 */
public enum ArmyState {
    /**
     * 驻扎 - 防御加成, 低补给消耗
     */
    STATIONARY("stationary", 1, 1.2),

    /**
     * 行军 - 可移动, 高补给消耗
     */
    MARCHING("marching", 3, 1.0),

    /**
     * 进攻 - 攻击领地
     */
    ATTACKING("attacking", 5, 1.1),

    /**
     * 攻城 - 攻击城镇
     */
    SIEGING("sieging", 5, 0.9),

    /**
     * 防御 - 驻守防御
     */
    DEFENDING("defending", 2, 1.3);

    private final String key;
    private final int supplyConsumptionPerHour;
    private final double combatModifier;

    ArmyState(String key, int supplyConsumptionPerHour, double combatModifier) {
        this.key = key;
        this.supplyConsumptionPerHour = supplyConsumptionPerHour;
        this.combatModifier = combatModifier;
    }

    public String key() {
        return key;
    }

    /**
     * 每小时补给消耗
     */
    public int supplyConsumptionPerHour() {
        return supplyConsumptionPerHour;
    }

    /**
     * 战斗力修正
     */
    public double combatModifier() {
        return combatModifier;
    }

    /**
     * 是否可以移动
     */
    public boolean canMove() {
        return this == MARCHING || this == STATIONARY;
    }

    /**
     * 是否处于战斗状态
     */
    public boolean isInCombat() {
        return this == ATTACKING || this == SIEGING || this == DEFENDING;
    }

    /**
     * 从字符串解析
     */
    public static ArmyState fromString(String str) {
        for (ArmyState state : values()) {
            if (state.key.equalsIgnoreCase(str) || state.name().equalsIgnoreCase(str)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown army state: " + str);
    }
}
