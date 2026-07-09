package dev.starcore.starcore.mechanics;

/**
 * 声望等级枚举
 */
public enum ReputationLevel {

    NEWCOMER("萌新", 0, 99, "§7"),
    ORDINARY("普通", 100, 499, "§f"),
    KNOWN("知名", 500, 1499, "§a"),
    FAMOUS("著名", 1500, 3999, "§9"),
    LEGENDARY("传奇", 4000, Integer.MAX_VALUE, "§6");

    private final String displayName;
    private final int minReputation;
    private final int maxReputation;
    private final String colorCode;

    ReputationLevel(String displayName, int minReputation, int maxReputation, String colorCode) {
        this.displayName = displayName;
        this.minReputation = minReputation;
        this.maxReputation = maxReputation;
        this.colorCode = colorCode;
    }

    /**
     * 根据声望值获取等级
     */
    public static ReputationLevel fromReputation(int reputation) {
        for (ReputationLevel level : values()) {
            if (reputation >= level.minReputation && reputation <= level.maxReputation) {
                return level;
            }
        }
        return NEWCOMER;
    }

    /**
     * 获取下一级所需声望
     */
    public int getRequiredForNext() {
        ReputationLevel[] levels = values();
        int currentIndex = this.ordinal();

        if (currentIndex < levels.length - 1) {
            return levels[currentIndex + 1].minReputation;
        }

        return Integer.MAX_VALUE; // 已是最高级
    }

    /**
     * 获取带颜色的显示名称
     */
    public String getColoredName() {
        return colorCode + displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMinReputation() {
        return minReputation;
    }

    public int getMaxReputation() {
        return maxReputation;
    }

    public String getColorCode() {
        return colorCode;
    }
}
