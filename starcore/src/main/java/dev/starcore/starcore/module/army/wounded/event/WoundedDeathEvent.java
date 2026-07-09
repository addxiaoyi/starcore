package dev.starcore.starcore.module.army.wounded.event;

import dev.starcore.starcore.module.army.wounded.WoundedService.WoundedRecord;
import dev.starcore.starcore.module.army.wounded.WoundedService.WoundedSeverity;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 伤兵死亡事件
 * 当伤兵因治疗失败、死亡风险或超时而死亡时触发
 */
public class WoundedDeathEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final WoundedRecord record;
    private final DeathReason reason;
    private final Location location;
    private final int deadSoldiers;

    public WoundedDeathEvent(WoundedRecord record, DeathReason reason, Location location, int deadSoldiers) {
        this.record = record;
        this.reason = reason;
        this.location = location;
        this.deadSoldiers = deadSoldiers;
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

    public DeathReason getReason() {
        return reason;
    }

    public Location getLocation() {
        return location;
    }

    public int getDeadSoldiers() {
        return deadSoldiers;
    }

    public WoundedSeverity getSeverity() {
        return record.severity();
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    /**
     * 死亡原因枚举
     */
    public enum DeathReason {
        /** 到达时死亡 */
        ARRIVAL,
        /** 治疗失败 */
        HEALING_FAILED,
        /** 死亡风险 */
        DEATH_RISK,
        /** 超时未治疗 */
        TIMEOUT,
        /** 伤兵容量已满 */
        CAPACITY_FULL,
        /** 未知原因 */
        UNKNOWN
    }
}
