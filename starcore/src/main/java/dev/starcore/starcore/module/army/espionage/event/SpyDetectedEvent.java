package dev.starcore.starcore.module.army.espionage.event;

import dev.starcore.starcore.module.army.espionage.model.Spy;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 间谍被发现事件
 */
public class SpyDetectedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Spy spy;
    private final UUID sourceNationId;
    private final String sourceNationName;
    private final UUID targetNationId;
    private final String targetNationName;

    public SpyDetectedEvent(Spy spy, UUID sourceNationId, String sourceNationName,
                            UUID targetNationId, String targetNationName) {
        this.spy = spy;
        this.sourceNationId = sourceNationId;
        this.sourceNationName = sourceNationName;
        this.targetNationId = targetNationId;
        this.targetNationName = targetNationName;
    }

    public Spy getSpy() {
        return spy;
    }

    public UUID getSpyId() {
        return spy.id();
    }

    public UUID getSourceNationId() {
        return sourceNationId;
    }

    public String getSourceNationName() {
        return sourceNationName;
    }

    public UUID getTargetNationId() {
        return targetNationId;
    }

    public String getTargetNationName() {
        return targetNationName;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}