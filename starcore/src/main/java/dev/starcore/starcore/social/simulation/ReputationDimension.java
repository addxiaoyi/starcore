package dev.starcore.starcore.social.simulation;

/**
 * 声望维度枚举
 */
public enum ReputationDimension {
    MORAL("道德", "§a"),
    ABILITY("能力", "§b"),
    WEALTH("财富", "§6"),
    CHARISMA("魅力", "§d"),
    TOTAL("综合", "§f");

    private final String displayName;
    private final String colorCode;

    ReputationDimension(String displayName, String colorCode) {
        this.displayName = displayName;
        this.colorCode = colorCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }
}
