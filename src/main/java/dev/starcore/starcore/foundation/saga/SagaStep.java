package dev.starcore.starcore.foundation.saga;

/**
 * Saga步骤接口
 */
public interface SagaStep<S, R> {

    /**
     * 执行步骤
     */
    R execute(S state) throws Exception;

    /**
     * 补偿操作（回滚）
     */
    void compensate(S state, R result) throws Exception;

    /**
     * 步骤名称
     */
    String name();
}
