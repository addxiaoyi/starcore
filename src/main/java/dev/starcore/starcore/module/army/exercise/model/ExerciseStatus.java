package dev.starcore.starcore.module.army.exercise.model;

/**
 * 演习状态
 */
public enum ExerciseStatus {
    /**
     * 准备中 - 正在召集参与者
     */
    PREPARING("preparing", "准备中"),

    /**
     * 进行中 - 演习正在进行
     */
    ACTIVE("active", "进行中"),

    /**
     * 已暂停 - 演习暂时停止
     */
    PAUSED("paused", "已暂停"),

    /**
     * 已完成 - 演习正常结束
     */
    COMPLETED("completed", "已完成"),

    /**
     * 已取消 - 演习被取消
     */
    CANCELLED("cancelled", "已取消");

    private final String key;
    private final String displayName;

    ExerciseStatus(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * 从键获取状态
     */
    public static ExerciseStatus fromKey(String key) {
        if (key == null) {
            return PREPARING;
        }
        for (ExerciseStatus status : values()) {
            if (status.key.equalsIgnoreCase(key)) {
                return status;
            }
        }
        return PREPARING;
    }
}
