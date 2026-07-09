package dev.starcore.starcore.module.army.navy.event;

import dev.starcore.starcore.module.army.navy.model.NavyUnit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Event;

import java.util.UUID;

/**
 * 海军事件基类
 */
public abstract class NavyEvent extends Event {
    private final NavyUnit navy;
    private final UUID nationId;

    protected NavyEvent(NavyUnit navy, UUID nationId) {
        this.navy = navy;
        this.nationId = nationId;
    }

    public NavyUnit getNavy() {
        return navy;
    }

    public UUID getNavyId() {
        return navy != null ? navy.id() : null;
    }

    public UUID getNationId() {
        return nationId;
    }

    public static HandlerList getHandlerList() {
        return new HandlerList();
    }
}
