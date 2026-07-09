package dev.starcore.starcore.module.territory.upgrade;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a nation completes a territory upgrade.
 * 国家完成领地升级时触发的事件
 */
public class TerritoryUpgradedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final NationId nationId;
    private final String pathId;
    private final int newLevel;

    public TerritoryUpgradedEvent(NationId nationId, String pathId, int newLevel) {
        this.nationId = nationId;
        this.pathId = pathId;
        this.newLevel = newLevel;
    }

    public NationId getNationId() {
        return nationId;
    }

    public String getPathId() {
        return pathId;
    }

    public int getNewLevel() {
        return newLevel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
