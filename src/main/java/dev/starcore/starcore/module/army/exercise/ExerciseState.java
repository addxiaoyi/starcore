package dev.starcore.starcore.module.army.exercise;

/**
 * 演习状态枚举
 * 表示演习的生命周期状态
 */
public enum ExerciseState {
    /**
     * 筹备中 - 等待参与者和配置
     */
    PREPARING("筹备中", 0),

    /**
     * 等待开始 - 参与者已加入，等待开始
     */
    WAITING("等待开始", 1),

    /**
     * 进行中 - 演习正在进行
     */
    IN_PROGRESS("进行中", 2),

    /**
     * 暂停中 - 演习被暂停
     */
    PAUSED("暂停中", 3),

    /**
     * 已结束 - 演习正常结束
     */
    COMPLETED("已结束", 4),

    /**
     * 已取消 - 演习被取消
     */
    CANCELLED("已取消", 5),

    /**
     * 超时 - 演习超时自动结束
     */
    TIMEOUT("超时", 6);

    private final String displayName;
    private final int order;

    ExerciseState(String displayName, int order) {
        this.displayName = displayName;
        this.order = order;
    }

    public String displayName() {
        return displayName;
    }

    public int order() {
        return order;
    }

    /**
     * 是否为活跃状态（可以进行战斗）
     */
    public boolean isActive() {
        return this == IN_PROGRESS;
    }

    /**
     * 是否可以加入
     */
    public boolean canJoin() {
        return this == PREPARING || this == WAITING;
    }

    /**
     * 是否可以开始
     */
    public boolean canStart() {
        return this == WAITING;
    }

    /**
     * 是否为终止状态
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == TIMEOUT;
    }
}
