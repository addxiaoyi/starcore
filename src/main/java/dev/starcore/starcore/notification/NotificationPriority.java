package dev.starcore.starcore.notification;

/**
 * 通知优先级
 * 用于确定通知的紧急程度和显示方式
 */
public enum NotificationPriority {
    /**
     * 最低优先级 - 仅在通知面板显示
     */
    LOWEST(0, 0),

    /**
     * 低优先级 - 显示在通知列表
     */
    LOW(1, 1),

    /**
     * 普通优先级 - 标准通知
     */
    NORMAL(2, 2),

    /**
     * 高优先级 - 重要通知，带声音
     */
    HIGH(3, 5),

    /**
     * 紧急优先级 - 强制显示，带特殊效果
     */
    URGENT(4, 10),

    /**
     * 最高优先级 - 全屏通知，无法忽略
     */
    CRITICAL(5, 20);

    private final int level;
    private final int soundVolume;

    NotificationPriority(int level, int soundVolume) {
        this.level = level;
        this.soundVolume = soundVolume;
    }

    public int getLevel() {
        return level;
    }

    public int getSoundVolume() {
        return soundVolume;
    }

    /**
     * 是否应该显示通知声音
     */
    public boolean shouldPlaySound() {
        return this.level >= HIGH.level;
    }

    /**
     * 是否应该显示通知动画
     */
    public boolean shouldAnimate() {
        return this.level >= URGENT.level;
    }

    /**
     * 是否可以静默（批量处理）
     */
    public boolean canBatch() {
        return this.level <= NORMAL.level;
    }

    /**
     * 是否强制显示（不可关闭）
     */
    public boolean isForced() {
        return this.level >= CRITICAL.level;
    }

    /**
     * 根据等级获取优先级
     */
    public static NotificationPriority fromLevel(int level) {
        for (NotificationPriority priority : values()) {
            if (priority.level == level) {
                return priority;
            }
        }
        return NORMAL;
    }

    /**
     * 判断是否比另一个优先级更高
     */
    public boolean isHigherThan(NotificationPriority other) {
        return this.level > other.level;
    }
}