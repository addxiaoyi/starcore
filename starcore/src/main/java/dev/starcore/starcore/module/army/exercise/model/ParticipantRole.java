package dev.starcore.starcore.module.army.exercise.model;

/**
 * 参与者角色
 */
public enum ParticipantRole {
    /**
     * 指挥官
     */
    COMMANDER("commander", "指挥官", 1.5),

    /**
     * 参谋
     */
    STAFF("staff", "参谋", 1.2),

    /**
     * 普通士兵
     */
    SOLDIER("soldier", "士兵", 1.0),

    /**
     * 观察员
     */
    OBSERVER("observer", "观察员", 0.5),

    /**
     * 后勤
     */
    LOGISTICS("logistics", "后勤", 0.8);

    private final String key;
    private final String displayName;
    private final double scoreMultiplier;

    ParticipantRole(String key, String displayName, double scoreMultiplier) {
        this.key = key;
        this.displayName = displayName;
        this.scoreMultiplier = scoreMultiplier;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public double scoreMultiplier() {
        return scoreMultiplier;
    }

    /**
     * 从键获取角色
     */
    public static ParticipantRole fromKey(String key) {
        if (key == null) {
            return SOLDIER;
        }
        for (ParticipantRole role : values()) {
            if (role.key.equalsIgnoreCase(key)) {
                return role;
            }
        }
        return SOLDIER;
    }
}
