package dev.starcore.starcore.module.army.exercise.event;

import dev.starcore.starcore.module.army.exercise.model.ExerciseParticipant;
import dev.starcore.starcore.module.army.exercise.model.WarExercise;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 演习参与者加入事件
 */
public class ExerciseParticipantJoinedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final WarExercise exercise;
    private final ExerciseParticipant participant;
    private final Player player;

    public ExerciseParticipantJoinedEvent(WarExercise exercise, ExerciseParticipant participant, Player player) {
        this.exercise = exercise;
        this.participant = participant;
        this.player = player;
    }

    public WarExercise getExercise() {
        return exercise;
    }

    public ExerciseParticipant getParticipant() {
        return participant;
    }

    public Player getPlayer() {
        return player;
    }

    public UUID getPlayerId() {
        return player.getUniqueId();
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
