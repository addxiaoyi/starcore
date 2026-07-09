package dev.starcore.starcore.module.army.espionage.event;

import dev.starcore.starcore.module.army.espionage.model.EspionageOperation;
import dev.starcore.starcore.module.army.espionage.model.OperationStatus;
import dev.starcore.starcore.module.army.espionage.model.Spy;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 间谍行动完成事件
 */
public class EspionageOperationCompletedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final EspionageOperation operation;
    private final Spy spy;
    private final boolean detected;

    public EspionageOperationCompletedEvent(EspionageOperation operation, Spy spy, boolean detected) {
        this.operation = operation;
        this.spy = spy;
        this.detected = detected;
    }

    public EspionageOperation getOperation() {
        return operation;
    }

    public Spy getSpy() {
        return spy;
    }

    public boolean wasDetected() {
        return detected;
    }

    public boolean isSuccess() {
        return operation.success();
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