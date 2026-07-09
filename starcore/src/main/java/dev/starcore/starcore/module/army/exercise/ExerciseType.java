package dev.starcore.starcore.module.army.exercise;

/**
 * 演习类型枚举
 * 定义不同类型的军事演习
 */
public enum ExerciseType {
    /**
     * 攻防演习 - 进攻方vs防守方
     */
    ATTACK_DEFENSE("攻防演习", "att_def", "进攻方需攻破防守方阵地", true),

    /**
     * 自由对抗 - 无限制自由战斗
     */
    FREE_FOR_ALL("自由对抗", "ffa", "所有参与者自由战斗", false),

    /**
     * 模拟战 - 两军对垒
     */
    SIMULATION("模拟战", "sim", "模拟真实战场环境", true),

    /**
     * 战术演练 - 测试战术配合
     */
    TACTICAL("战术演练", "tactical", "测试部队战术配合能力", false),

    /**
     * 围剿演习 - 一方围剿另一方
     */
    SIEGE("围剿演习", "siege", "进攻方围剿防守方据点", true),

    /**
     * 撤退演练 - 测试撤退战术
     */
    RETREAT("撤退演练", "retreat", "防守方测试撤退能力", true),

    /**
     * 侦察演习 - 重点在侦察和信息收集
     */
    RECONNAISSANCE("侦察演习", "recon", "测试侦察和情报收集能力", false);

    private final String displayName;
    private final String key;
    private final String description;
    private final boolean requiresTwoSides;

    ExerciseType(String displayName, String key, String description, boolean requiresTwoSides) {
        this.displayName = displayName;
        this.key = key;
        this.description = description;
        this.requiresTwoSides = requiresTwoSides;
    }

    public String displayName() {
        return displayName;
    }

    public String key() {
        return key;
    }

    public String description() {
        return description;
    }

    /**
     * 是否需要两方参与
     */
    public boolean requiresTwoSides() {
        return requiresTwoSides;
    }

    /**
     * 根据key获取类型
     */
    public static ExerciseType fromKey(String key) {
        for (ExerciseType type : values()) {
            if (type.key.equalsIgnoreCase(key)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown exercise type key: " + key);
    }
}
