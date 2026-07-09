package dev.starcore.starcore.module.army.exercise.model;

/**
 * 演习类型
 */
public enum ExerciseType {
    /**
     * 战术演练 - 测试攻防战术配合
     */
    TACTICAL("tactical", "战术演练", 1.0, 1.2),

    /**
     * 联合军演 - 多军种协同作战
     */
    JOINT("joint", "联合军演", 1.5, 1.5),

    /**
     * 防御演习 - 防守战术训练
     */
    DEFENSIVE("defensive", "防御演习", 0.8, 1.5),

    /**
     * 进攻演习 - 进攻战术训练
     */
    OFFENSIVE("offensive", "进攻演习", 1.5, 0.8),

    /**
     * 包围演习 - 包围战术训练
     */
    ENCIRCLEMENT("encirclement", "包围演习", 1.3, 1.3),

    /**
     * 突围演习 - 被包围状态下的突围
     */
    BREAKOUT("breakout", "突围演习", 1.2, 1.0);

    private final String key;
    private final String displayName;
    private final double attackBonus;
    private final double defenseBonus;

    ExerciseType(String key, String displayName, double attackBonus, double defenseBonus) {
        this.key = key;
        this.displayName = displayName;
        this.attackBonus = attackBonus;
        this.defenseBonus = defenseBonus;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public double attackBonus() {
        return attackBonus;
    }

    public double defenseBonus() {
        return defenseBonus;
    }

    /**
     * 从键获取演习类型
     */
    public static ExerciseType fromKey(String key) {
        if (key == null) {
            return TACTICAL;
        }
        for (ExerciseType type : values()) {
            if (type.key.equalsIgnoreCase(key)) {
                return type;
            }
        }
        return TACTICAL;
    }

    /**
     * 获取所有类型
     */
    public static ExerciseType[] allTypes() {
        return values();
    }
}
