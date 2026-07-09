package dev.starcore.starcore.module.army.wounded.event;

import dev.starcore.starcore.module.army.wounded.WoundedService.WoundedRecord;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 伤兵开始治疗事件
 * 当伤兵开始治疗时触发
 */
public class WoundedHealingStartedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final WoundedRecord record;
    private final Location hospitalLocation;
    private final long durationSeconds;

    public WoundedHealingStartedEvent(WoundedRecord record, Location hospitalLocation) {
        this.record = record;
        this.hospitalLocation = hospitalLocation;
        this.durationSeconds = record.remainingHealingTime();
    }

    public WoundedRecord getRecord() {
        return record;
    }

    public UUID getWoundedId() {
        return record.id();
    }

    public UUID getNationId() {
        return record.nationId();
    }

    public Location getHospitalLocation() {
        return hospitalLocation;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public int getWoundedCount() {
        return record.currentWounded();
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}