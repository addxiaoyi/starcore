package dev.starcore.starcore.module.army.wounded.event;

import dev.starcore.starcore.module.army.wounded.WoundedService.WoundedRecord;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 伤兵康复事件
 * 当伤兵完成治疗并转为可用士兵时触发
 */
public class WoundedRecoveredEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final WoundedRecord record;
    private final Location hospitalLocation;
    private final int recoveredSoldiers;
    private final long healingDurationSeconds;

    public WoundedRecoveredEvent(WoundedRecord record, Location hospitalLocation, int recoveredSoldiers) {
        this.record = record;
        this.hospitalLocation = hospitalLocation;
        this.recoveredSoldiers = recoveredSoldiers;
        this.healingDurationSeconds = (System.currentTimeMillis() - record.healingStartedAt()) / 1000;
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

    public UUID getArmyId() {
        return record.armyId();
    }

    public int getRecoveredSoldiers() {
        return recoveredSoldiers;
    }

    public long getHealingDurationSeconds() {
        return healingDurationSeconds;
    }

    public Location getHospitalLocation() {
        return hospitalLocation;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
