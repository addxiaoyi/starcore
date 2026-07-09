package dev.starcore.starcore.module.army.espionage.event;

import dev.starcore.starcore.module.army.espionage.model.EspionageOperation;
import dev.starcore.starcore.module.army.espionage.model.Spy;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 间谍行动开始事件
 */
public class EspionageOperationStartedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final EspionageOperation operation;
    private final Spy spy;

    public EspionageOperationStartedEvent(EspionageOperation operation, Spy spy) {
        this.operation = operation;
        this.spy = spy;
    }

    public EspionageOperation getOperation() {
        return operation;
    }

    public Spy getSpy() {
        return spy;
    }

    public UUID getSourceNationId() {
        return operation.sourceNationId();
    }

    public UUID getTargetNationId() {
        return operation.targetNationId();
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}