package dev.starcore.starcore.module.dynasty.event;

import dev.starcore.starcore.module.dynasty.model.Dynasty;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 王朝创建事件
 * 在国家创建时自动创建王朝时触发
 */
public class DynastyCreatedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final NationId nationId;
    private final Dynasty dynasty;
    private final UUID founderId;
    private final String founderName;

    public DynastyCreatedEvent(NationId nationId, Dynasty dynasty, UUID founderId, String founderName) {
        this.nationId = nationId;
        this.dynasty = dynasty;
        this.founderId = founderId;
        this.founderName = founderName;
    }

    public NationId getNationId() {
        return nationId;
    }

    public Dynasty getDynasty() {
        return dynasty;
    }

    public UUID getFounderId() {
        return founderId;
    }

    public String getFounderName() {
        return founderName;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}