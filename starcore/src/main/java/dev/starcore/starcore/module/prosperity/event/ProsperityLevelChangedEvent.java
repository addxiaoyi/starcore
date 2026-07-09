package dev.starcore.starcore.module.prosperity.event;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.time.Instant;
import java.util.Objects;

/**
 * 繁荣度等级变化事件
 * 当国家繁荣度等级发生变化时触发
 */
public class ProsperityLevelChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final NationId nationId;
    private final int oldLevel;
    private final int newLevel;
    private final Instant occurredAt;

    public ProsperityLevelChangedEvent(NationId nationId, int oldLevel, int newLevel, Instant occurredAt) {
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static ProsperityLevelChangedEvent create(NationId nationId, int oldLevel, int newLevel) {
        return new ProsperityLevelChangedEvent(nationId, oldLevel, newLevel, Instant.now());
    }

    public NationId nationId() {
        return nationId;
    }

    public int oldLevel() {
        return oldLevel;
    }

    public int newLevel() {
        return newLevel;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    /**
     * 是否为升级
     */
    public boolean isUpgrade() {
        return newLevel > oldLevel;
    }

    /**
     * 是否为降级
     */
    public boolean isDowngrade() {
        return newLevel < oldLevel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
