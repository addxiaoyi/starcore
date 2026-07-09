package dev.starcore.starcore.module.army.mercenary;

/**
 * 雇佣兵军衔
 */
public enum MercenaryRank {
    RECRUIT("recruit", "新兵", 1.0),
    SOLDIER("soldier", "士兵", 1.2),
    VETERAN("veteran", "老兵", 1.5),
    SERGEANT("sergeant", "士官", 1.8),
    LIEUTENANT("lieutenant", "尉官", 2.2),
    CAPTAIN("captain", "校官", 2.8),
    MAJOR("major", "校官", 3.5),
    COMMANDER("commander", "指挥官", 4.5),
    GENERAL("general", "将领", 6.0);

    private final String key;
    private final String displayName;
    private final double payMultiplier;

    MercenaryRank(String key, String displayName, double payMultiplier) {
        this.key = key;
        this.displayName = displayName;
        this.payMultiplier = payMultiplier;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public double payMultiplier() {
        return payMultiplier;
    }

    /**
     * 从key获取军衔
     */
    public static MercenaryRank fromKey(String key) {
        for (MercenaryRank rank : values()) {
            if (rank.key.equalsIgnoreCase(key)) {
                return rank;
            }
        }
        return RECRUIT;
    }

    /**
     * 根据经验等级获取军衔
     */
    public static MercenaryRank fromExperienceLevel(int level) {
        if (level >= 100) return GENERAL;
        if (level >= 80) return COMMANDER;
        if (level >= 60) return MAJOR;
        if (level >= 45) return CAPTAIN;
        if (level >= 30) return LIEUTENANT;
        if (level >= 18) return SERGEANT;
        if (level >= 8) return VETERAN;
        if (level >= 3) return SOLDIER;
        return RECRUIT;
    }
}
