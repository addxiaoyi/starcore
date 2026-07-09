package dev.starcore.starcore.module.army.raid.event;

import dev.starcore.starcore.module.army.raid.model.Raid;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 突袭结束事件
 */
public class RaidEndEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Raid raid;
    private final NationId attackerNationId;
    private final NationId targetNationId;
    private final boolean success;

    public RaidEndEvent(Raid raid, NationId attackerNationId, NationId targetNationId, boolean success) {
        this.raid = raid;
        this.attackerNationId = attackerNationId;
        this.targetNationId = targetNationId;
        this.success = success;
    }

    public Raid getRaid() {
        return raid;
    }

    public NationId getAttackerNationId() {
        return attackerNationId;
    }

    public NationId getTargetNationId() {
        return targetNationId;
    }

    public boolean isSuccess() {
        return success;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}