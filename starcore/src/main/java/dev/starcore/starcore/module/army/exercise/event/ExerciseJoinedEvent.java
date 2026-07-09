package dev.starcore.starcore.module.army.exercise.event;

import dev.starcore.starcore.module.army.exercise.Exercise;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 演习加入事件
 * 在国家加入演习时触发
 */
public class ExerciseJoinedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Exercise exercise;
    private final UUID nationId;
    private final String nationName;
    private final int soldierCount;

    public ExerciseJoinedEvent(Exercise exercise, UUID nationId, String nationName, int soldierCount) {
        this.exercise = exercise;
        this.nationId = nationId;
        this.nationName = nationName;
        this.soldierCount = soldierCount;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public UUID getExerciseId() {
        return exercise.id();
    }

    public UUID getNationId() {
        return nationId;
    }

    public String getNationName() {
        return nationName;
    }

    public int getSoldierCount() {
        return soldierCount;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
