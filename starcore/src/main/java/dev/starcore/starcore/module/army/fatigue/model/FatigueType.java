package dev.starcore.starcore.module.army.fatigue.model;

/**
 * 疲劳类型枚举
 */
public enum FatigueType {
    PHYSICAL("体力疲劳"),
    MENTAL("精神疲劳"),
    COMBAT("战斗疲劳"),
    TRAVEL("旅行疲劳");

    private final String displayName;

    FatigueType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static FatigueType fromString(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            for (FatigueType type : values()) {
                if (type.name().startsWith(name.toUpperCase().substring(0, 1))) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown fatigue type: " + name);
        }
    }
}