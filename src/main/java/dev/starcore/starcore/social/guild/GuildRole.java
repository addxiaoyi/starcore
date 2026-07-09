package dev.starcore.starcore.social.guild;

/**
 * 公会职位
 */
public enum GuildRole {
    LEADER("会长", 3),      // 最高权限
    OFFICER("管理", 2),     // 管理权限
    MEMBER("成员", 1);      // 普通成员

    private final String displayName;
    private final int priority;

    GuildRole(String displayName, int priority) {
        this.displayName = displayName;
        this.priority = priority;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * 检查是否有更高权限
     */
    public boolean hasHigherPriorityThan(GuildRole other) {
        return this.priority > other.priority;
    }
}
