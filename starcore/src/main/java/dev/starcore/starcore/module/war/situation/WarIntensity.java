package dev.starcore.starcore.module.war.situation;

/**
 * 战争强度等级
 */
public enum WarIntensity {
    CALM("平静", "⚪", 0xFFFFFF),
    SKIRMISH("小规模交火", "🟡", 0xFFAA00),
    ACTIVE("活跃交战中", "🟠", 0xFF6600),
    INTENSE("激烈战斗中", "🔴", 0xFF0000);

    private final String displayName;
    private final String emoji;
    private final int color;

    WarIntensity(String displayName, String emoji, int color) {
        this.displayName = displayName;
        this.emoji = emoji;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    public String emoji() {
        return emoji;
    }

    public int color() {
        return color;
    }

    public String coloredName() {
        return String.format("§%x[%s %s]§r", (color >> 16) & 0xFF, emoji, displayName);
    }
}