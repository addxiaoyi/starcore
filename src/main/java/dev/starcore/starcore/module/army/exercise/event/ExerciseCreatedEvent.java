package dev.starcore.starcore.module.army.exercise.event;

import dev.starcore.starcore.module.army.exercise.Exercise;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 演习创建事件
 * 在创建新演习时触发
 */
public class ExerciseCreatedEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final Exercise exercise;
    private boolean cancelled;

    public ExerciseCreatedEvent(Exercise exercise) {
        this.exercise = exercise;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public UUID getExerciseId() {
        return exercise.id();
    }

    public UUID getOrganizerId() {
        return exercise.organizerId();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
