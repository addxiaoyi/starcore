package dev.starcore.starcore.module.army.exercise;

/**
 * 演习参与者角色
 */
public enum ExerciseRole {
    /**
     * 进攻方
     */
    ATTACKER("进攻方", "attacker"),

    /**
     * 防守方
     */
    DEFENDER("防守方", "defender"),

    /**
     * 中立/观察者
     */
    NEUTRAL("中立", "neutral"),

    /**
     * 自由参与者
     */
    FREE("自由", "free");

    private final String displayName;
    private final String key;

    ExerciseRole(String displayName, String key) {
        this.displayName = displayName;
        this.key = key;
    }

    public String displayName() {
        return displayName;
    }

    public String key() {
        return key;
    }

    /**
     * 是否为对立阵营
     */
    public boolean isOppositeTo(ExerciseRole other) {
        return (this == ATTACKER && other == DEFENDER)
            || (this == DEFENDER && other == ATTACKER);
    }

    /**
     * 是否可以与另一个角色战斗
     */
    public boolean canFightWith(ExerciseRole other) {
        if (this == FREE || other == FREE) {
            return this != other;
        }
        return isOppositeTo(other);
    }
}