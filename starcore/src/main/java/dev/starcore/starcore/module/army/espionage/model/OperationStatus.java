package dev.starcore.starcore.module.army.espionage.model;

/**
 * 行动状态
 */
public enum OperationStatus {
    /**
     * 进行中
     */
    IN_PROGRESS,

    /**
     * 已完成（成功）
     */
    COMPLETED,

    /**
     * 失败
     */
    FAILED,

    /**
     * 被发现/暴露
     */
    EXPOSED,

    /**
     * 取消
     */
    CANCELLED
}
