package dev.starcore.starcore.quest;

/**
 * 任务类型枚举
 *
 * @author StarCore Team
 * @since 1.0.0
 */
public enum QuestType {
    /**
     * 每日任务 - 每日凌晨4点刷新
     */
    DAILY("每日任务", "daily"),

    /**
     * 每周任务 - 每周一凌晨4点刷新
     */
    WEEKLY("每周任务", "weekly"),

    /**
     * 主线任务 - 剧情推进任务
     */
    MAIN("主线任务", "main"),

    /**
     * 支线任务 - 可选任务
     */
    SIDE("支线任务", "side"),

    /**
     * 委托任务 - 玩家发布的任务
     */
    COMMISSION("委托任务", "commission"),

    /**
     * 重复任务 - 可多次完成
     */
    REPEATABLE("重复任务", "repeatable"),

    /**
     * 成就任务 - 长期目标
     */
    ACHIEVEMENT("成就任务", "achievement"),

    /**
     * 事件任务 - 限时活动任务
     */
    EVENT("事件任务", "event");

    private final String displayName;
    private final String key;

    QuestType(String displayName, String key) {
        this.displayName = displayName;
        this.key = key;
    }

    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取键值
     */
    public String getKey() {
        return key;
    }

    /**
     * 是否为每日刷新任务
     */
    public boolean isDailyRefresh() {
        return this == DAILY;
    }

    /**
     * 是否为周期性任务
     */
    public boolean isPeriodic() {
        return this == DAILY || this == WEEKLY;
    }

    /**
     * 是否为剧情任务
     */
    public boolean isStoryQuest() {
        return this == MAIN || this == SIDE;
    }

    /**
     * 通过键值获取任务类型
     */
    public static QuestType fromKey(String key) {
        for (QuestType type : values()) {
            if (type.key.equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }
}
