package dev.starcore.starcore.module.army.espionage.event;

import dev.starcore.starcore.module.army.espionage.model.Spy;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 间谍被训练事件
 */
public class SpyRecruitedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Spy spy;
    private final UUID recruiterId;

    public SpyRecruitedEvent(Spy spy, UUID recruiterId) {
        this.spy = spy;
        this.recruiterId = recruiterId;
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

    public UUID getRecruiterId() {
        return recruiterId;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}