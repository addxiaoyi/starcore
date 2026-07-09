package dev.starcore.starcore.module.dungeon;

/**
 * 副本完成结果
 */
public enum DungeonCompletionResult {
    SUCCESS("成功"),
    FAILED("失败"),
    TIMEOUT("超时"),
    ABANDONED("放弃"),
    KICKED("踢出");

    private final String displayName;

    DungeonCompletionResult(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
