package dev.starcore.starcore.social.simulation;

/**
 * 声望立场/阵营枚举
 */
public enum ReputationStanding {
    HERO("英雄", 100),
    RESPECTED("受尊敬", 50),
    NEUTRAL("中立", 0),
    SUSPECTED("可疑", -50),
    OUTLAW("通缉犯", -100);

    private final String displayName;
    private final int threshold;

    ReputationStanding(String displayName, int threshold) {
        this.displayName = displayName;
        this.threshold = threshold;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getThreshold() {
        return threshold;
    }

    public static ReputationStanding fromReputation(int totalReputation) {
        if (totalReputation >= HERO.threshold) return HERO;
        if (totalReputation >= RESPECTED.threshold) return RESPECTED;
        if (totalReputation >= NEUTRAL.threshold) return NEUTRAL;
        if (totalReputation >= SUSPECTED.threshold) return SUSPECTED;
        return OUTLAW;
    }
}
