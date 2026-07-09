package dev.starcore.starcore.module.army.exercise.event;

import dev.starcore.starcore.module.army.exercise.Exercise;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 演习开始事件
 * 在演习正式开始时触发
 */
public class ExerciseStartedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Exercise exercise;

    public ExerciseStartedEvent(Exercise exercise) {
        this.exercise = exercise;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public UUID getExerciseId() {
        return exercise.id();
    }

    public String getExerciseName() {
        return exercise.name();
    }

    public int getParticipantCount() {
        return exercise.participantCount();
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
