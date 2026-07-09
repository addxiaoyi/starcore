package dev.starcore.starcore.module.army.wounded.event;

import dev.starcore.starcore.module.army.wounded.WoundedService.WoundedRecord;
import dev.starcore.starcore.module.army.wounded.WoundedService.WoundedSeverity;
import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 伤兵创建事件
 * 当士兵被标记为伤兵时触发
 */
public class WoundedCreatedEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final WoundedRecord record;
    private final UUID creatorId;  // 导致伤兵的原因（如战斗中的攻击者）
    private final Location location;
    private boolean cancelled;

    public WoundedCreatedEvent(WoundedRecord record, UUID creatorId, Location location) {
        this.record = record;
        this.creatorId = creatorId;
        this.location = location;
    }

    public WoundedRecord getRecord() {
        return record;
    }

    public UUID getCreatorId() {
        return creatorId;
    }

    public Location getLocation() {
        return location;
    }

    public UUID getNationId() {
        return record.nationId();
    }

    public UUID getArmyId() {
        return record.armyId();
    }

    public int getWoundedCount() {
        return record.currentWounded();
    }

    public WoundedSeverity getSeverity() {
        return record.severity();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
