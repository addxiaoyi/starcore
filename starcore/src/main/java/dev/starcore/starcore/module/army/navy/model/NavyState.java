package dev.starcore.starcore.module.army.navy.model;

/**
 * 海军舰队状态
 */
public enum NavyState {
    /**
     * 停泊 - 在港口停靠，低消耗
     */
    ANCHORED("anchored", 1, 1.2),

    /**
     * 航行 - 移动中，标准消耗
     */
    SAILING("sailing", 3, 1.0),

    /**
     * 巡逻 - 海域巡逻，中等消耗
     */
    PATROLLING("patrolling", 4, 1.1),

    /**
     * 海战 - 战斗中，高消耗
     */
    IN_COMBAT("in_combat", 6, 1.3),

    /**
     * 登陆 - 准备或执行登陆
     */
    LANDING("landing", 5, 0.9),

    /**
     * 封锁 - 封锁敌方港口
     */
    BLOCKADING("blockading", 5, 1.15),

    /**
     * 撤退 - 撤离战斗，低攻击
     */
    RETREATING("retreating", 4, 0.5);

    private final String key;
    private final int supplyConsumptionPerHour;
    private final double combatModifier;

    NavyState(String key, int supplyConsumptionPerHour, double combatModifier) {
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
        return this == SAILING || this == ANCHORED || this == PATROLLING;
    }

    /**
     * 是否处于战斗状态
     */
    public boolean isInCombat() {
        return this == IN_COMBAT || this == BLOCKADING;
    }

    /**
     * 是否处于撤退状态
     */
    public boolean isRetreating() {
        return this == RETREATING;
    }

    /**
     * 从字符串解析
     */
    public static NavyState fromString(String str) {
        for (NavyState state : values()) {
            if (state.key.equalsIgnoreCase(str) || state.name().equalsIgnoreCase(str)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown navy state: " + str);
    }
}
