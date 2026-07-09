package dev.starcore.starcore.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * 玩家帮助玩家事件
 */
public class PlayerHelpPlayerEvent extends StarCorePlayerEvent {
    private final Player target;
    private final String helpType;

    public PlayerHelpPlayerEvent(Player helper, Player target, String helpType) {
        super(helper);
        this.target = target;
        this.helpType = helpType;
    }

    public Player getTarget() {
        return target;
    }

    public String getHelpType() {
        return helpType;
    }

    // Helper 是事件的发起者（从 StarCorePlayerEvent 继承的 getPlayer()）
    public Player getHelper() {
        return getPlayer();
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
