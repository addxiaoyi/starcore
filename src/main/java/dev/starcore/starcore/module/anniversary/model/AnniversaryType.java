package dev.starcore.starcore.module.anniversary.model;

/**
 * 纪念日类型枚举
 */
public enum AnniversaryType {
    /**
     * 国家成立纪念日
     */
    FOUNDING("国家成立", "🎉", true),

    /**
     * 战争胜利纪念日
     */
    VICTORY("战争胜利", "⚔️", true),

    /**
     * 联盟建立纪念日
     */
    ALLIANCE("联盟建立", "🤝", true),

    /**
     * 独立日
     */
    INDEPENDENCE("独立日", "🗽", true),

    /**
     * 宪法颁布日
     */
    CONSTITUTION("宪法颁布", "📜", true),

    /**
     * 节日/假日
     */
    HOLIDAY("节日", "🎊", true),

    /**
     * 特别纪念日
     */
    SPECIAL("特别纪念", "⭐", true),

    /**
     * 科技突破日
     */
    TECHNOLOGY("科技突破", "🔬", true),

    /**
     * 领土扩张日
     */
    EXPANSION("领土扩张", "🗺️", true),

    /**
     * 其他类型
     */
    OTHER("其他", "📅", true);

    private final String displayName;
    private final String emoji;
    private final boolean defaultRecurring;

    AnniversaryType(String displayName, String emoji, boolean defaultRecurring) {
        this.displayName = displayName;
        this.emoji = emoji;
        this.defaultRecurring = defaultRecurring;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmoji() {
        return emoji;
    }

    public boolean isDefaultRecurring() {
        return defaultRecurring;
    }

    /**
     * 从字符串获取类型
     */
    public static AnniversaryType fromString(String name) {
        if (name == null || name.isEmpty()) {
            return OTHER;
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 尝试模糊匹配
            String normalized = name.toLowerCase().replace("-", "").replace("_", "");
            for (AnniversaryType type : values()) {
                String typeNormalized = type.name().toLowerCase();
                if (typeNormalized.contains(normalized) ||
                    normalized.contains(typeNormalized) ||
                    type.displayName.contains(name)) {
                    return type;
                }
            }
            return OTHER;
        }
    }
}
