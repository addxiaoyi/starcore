package dev.starcore.starcore.foundation.saga;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Saga编排器
 *
 * 协调多步骤事务，自动执行补偿操作
 */
public final class SagaOrchestrator<S> {

    private final Logger logger;
    private final List<SagaStep<S, ?>> steps = new ArrayList<>();

    public SagaOrchestrator(Logger logger) {
        this.logger = logger;
    }

    public SagaOrchestrator<S> addStep(SagaStep<S, ?> step) {
        steps.add(step);
        return this;
    }

    /**
     * 执行Saga
     */
    public Result execute(S initialState) {
        S state = initialState;
        List<ExecutedStep> executedSteps = new ArrayList<>();

        for (SagaStep<S, ?> step : steps) {
            try {
                logger.debug("Executing saga step: {}", step.name());
                Object result = step.execute(state);
                executedSteps.add(new ExecutedStep(step, result));
            } catch (Exception e) {
                logger.error("Saga step failed: {} - {}", step.name(), e.getMessage());
                return compensate(state, executedSteps, e);
            }
        }

        return new Result(true, state, null, executedSteps);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Result compensate(Object state, List<ExecutedStep> executedSteps, Exception originalError) {
        logger.info("Starting saga compensation for {} steps", executedSteps.size());

        List<String> compensated = new ArrayList<>();

        // 逆序执行补偿
        for (int i = executedSteps.size() - 1; i >= 0; i--) {
            ExecutedStep executed = executedSteps.get(i);
            try {
                logger.debug("Compensating step: {}", executed.step().name());
                ((SagaStep) executed.step()).compensate(state, executed.result());
                compensated.add(executed.step().name());
            } catch (Exception e) {
                logger.error("Compensation failed for step {}: {}", executed.step().name(), e.getMessage(), e);
            }
        }

        return new Result(false, state, originalError, compensated);
    }

    public static class ExecutedStep {
        private final SagaStep<?, ?> step;
        private final Object result;

        public ExecutedStep(SagaStep<?, ?> step, Object result) {
            this.step = step;
            this.result = result;
        }

        public SagaStep<?, ?> step() { return step; }
        public Object result() { return result; }
    }

    public static class Result {
        private final boolean success;
        private final Object finalState;
        private final Exception error;
        private final List<?> executedSteps;

        public Result(boolean success, Object finalState, Exception error, List<?> executedSteps) {
            this.success = success;
            this.finalState = finalState;
            this.error = error;
            this.executedSteps = executedSteps;
        }

        public boolean success() { return success; }
        public Object finalState() { return finalState; }
        public Exception error() { return error; }
        public List<?> executedSteps() { return executedSteps; }
    }
}
