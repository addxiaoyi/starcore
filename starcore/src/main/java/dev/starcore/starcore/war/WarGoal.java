package dev.starcore.starcore.war;

/**
 * 战争目标
 * 定义战争的目的和胜利条件
 */
public enum WarGoal {
    /**
     * 征服战
     * 目标：占领对方首都或所有领土
     */
    CONQUEST("征服", 100, 1.5),

    /**
     * 领土战
     * 目标：夺取特定领土
     */
    TERRITORIAL("领土争夺", 50, 1.0),

    /**
     * 统一战
     * 目标：统一地区或合并国家
     */
    UNIFICATION("统一", 80, 1.3),

    /**
     * 独立战
     * 目标：脱离宗主国独立
     */
    INDEPENDENCE("独立", 60, 1.2),

    /**
     * 报复战
     * 目标：惩罚对方、削弱其实力
     */
    PUNITIVE("报复", 40, 0.8),

    /**
     * 防御战
     * 目标：保卫领土、抵御侵略
     */
    DEFENSIVE("防御", 30, 0.7),

    /**
     * 解放战
     * 目标：解放被占领的盟友或附庸
     */
    LIBERATION("解放", 50, 1.0);

    private final String displayName;
    private final int baseWarScore;         // 基础战争积分要求
    private final double difficultyModifier; // 难度系数

    WarGoal(String displayName, int baseWarScore, double difficultyModifier) {
        this.displayName = displayName;
        this.baseWarScore = baseWarScore;
        this.difficultyModifier = difficultyModifier;
    }

    public String displayName() {
        return displayName;
    }

    public int baseWarScore() {
        return baseWarScore;
    }

    public double difficultyModifier() {
        return difficultyModifier;
    }

    /**
     * 计算实际所需战争积分
     */
    public int calculateRequiredScore(int defenderStrength) {
        return (int) (baseWarScore * difficultyModifier * (1.0 + defenderStrength / 100.0));
    }

    /**
     * 是否为防御性战争
     */
    public boolean isDefensive() {
        return this == DEFENSIVE || this == INDEPENDENCE;
    }

    /**
     * 是否为侵略性战争
     */
    public boolean isAggressive() {
        return this == CONQUEST || this == TERRITORIAL || this == PUNITIVE;
    }
}
