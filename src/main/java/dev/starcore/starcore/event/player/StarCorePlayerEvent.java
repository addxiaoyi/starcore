package dev.starcore.starcore.event.player;

import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.entity.Player;

/**
 * 玩家相关事件基类
 */
public class StarCorePlayerEvent extends PlayerEvent {
    public StarCorePlayerEvent(Player who) {
        super(who);
    }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}