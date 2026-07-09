package dev.starcore.starcore.module.army.exercise.event;

import dev.starcore.starcore.module.army.exercise.ExerciseBattleResult;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 演习战斗事件
 * 在演习中发生战斗时触发
 */
public class ExerciseBattleEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final ExerciseBattleResult result;

    public ExerciseBattleEvent(ExerciseBattleResult result) {
        this.result = result;
    }

    public ExerciseBattleResult getResult() {
        return result;
    }

    public boolean hasWinner() {
        return result.winnerId() != null;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
