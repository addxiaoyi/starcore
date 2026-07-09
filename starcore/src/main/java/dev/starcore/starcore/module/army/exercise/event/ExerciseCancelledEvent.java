package dev.starcore.starcore.module.army.exercise.event;

import dev.starcore.starcore.module.army.exercise.model.WarExercise;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 演习被取消事件
 */
public class ExerciseCancelledEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final WarExercise exercise;
    private final UUID cancelledBy;

    public ExerciseCancelledEvent(WarExercise exercise, UUID cancelledBy) {
        this.exercise = exercise;
        this.cancelledBy = cancelledBy;
    }

    public WarExercise getExercise() {
        return exercise;
    }

    public UUID getCancelledBy() {
        return cancelledBy;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
