package dev.starcore.starcore.module.army.espionage.event;

import dev.starcore.starcore.module.army.espionage.model.Spy;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 间谍死亡/解雇事件
 */
public class SpyLostEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Spy spy;
    private final Cause cause;

    public SpyLostEvent(Spy spy, Cause cause) {
        this.spy = spy;
        this.cause = cause;
    }

    public Spy getSpy() {
        return spy;
    }

    public UUID getSpyId() {
        return spy.id();
    }

    public UUID getNationId() {
        return spy.ownerId();
    }

    public Cause getCause() {
        return cause;
    }

    public enum Cause {
        /**
         * 被发现并处决
         */
        DETECTED_AND_EXECUTED,

        /**
         * 主动解雇
         */
        DISMISSED,

        /**
         * 士气耗尽死亡
         */
        MORALE_DEPLETED,

        /**
         * 其他原因
         */
        OTHER
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}