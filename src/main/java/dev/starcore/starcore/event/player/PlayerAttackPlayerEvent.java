package dev.starcore.starcore.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * 玩家攻击玩家事件
 */
public class PlayerAttackPlayerEvent extends StarCorePlayerEvent {
    private final Player target;
    private final double damage;

    public PlayerAttackPlayerEvent(Player attacker, Player target, double damage) {
        super(attacker);
        this.target = target;
        this.damage = damage;
    }

    public Player getTarget() {
        return target;
    }

    public double getDamage() {
        return damage;
    }

    // Attacker 是事件的发起者（从 StarCorePlayerEvent 继承的 getPlayer()）
    public Player getAttacker() {
        return getPlayer();
    }

    public Player getVictim() {
        return target;
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
