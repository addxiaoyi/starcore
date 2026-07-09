package dev.starcore.starcore.module.prosperity.event;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.time.Instant;
import java.util.Objects;

/**
 * 繁荣度变化事件
 * 当国家繁荣度发生变化时触发
 */
public class ProsperityChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final NationId nationId;
    private final double oldValue;
    private final double newValue;
    private final String reason;
    private final Instant occurredAt;

    public ProsperityChangedEvent(NationId nationId, double oldValue, double newValue, String reason, Instant occurredAt) {
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.reason = Objects.requireNonNull(reason, "reason");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static ProsperityChangedEvent create(NationId nationId, double oldValue, double newValue, String reason) {
        return new ProsperityChangedEvent(nationId, oldValue, newValue, reason, Instant.now());
    }

    public NationId nationId() {
        return nationId;
    }

    public double oldValue() {
        return oldValue;
    }

    public double newValue() {
        return newValue;
    }

    public String reason() {
        return reason;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    /**
     * 获取变化量
     */
    public double delta() {
        return newValue - oldValue;
    }

    /**
     * 是否为增长
     */
    public boolean isIncrease() {
        return delta() > 0;
    }

    /**
     * 是否为下降
     */
    public boolean isDecrease() {
        return delta() < 0;
    }

    /**
     * 获取变化百分比
     */
    public double deltaPercent() {
        if (oldValue == 0) {
            return newValue > 0 ? 100.0 : 0.0;
        }
        return (delta() / oldValue) * 100.0;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
