package dev.starcore.starcore.module.army.exercise.event;

import dev.starcore.starcore.module.army.exercise.Exercise;
import dev.starcore.starcore.module.army.exercise.ExerciseResult;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 演习结束事件
 * 在演习结束时触发
 */
public class ExerciseEndedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Exercise exercise;
    private final ExerciseResult result;
    private final String reason;

    public ExerciseEndedEvent(Exercise exercise, ExerciseResult result, String reason) {
        this.exercise = exercise;
        this.result = result;
        this.reason = reason;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public ExerciseResult getResult() {
        return result;
    }

    public String getReason() {
        return reason;
    }

    /**
     * 获取原因（record accessor）
     */
    public String reason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
