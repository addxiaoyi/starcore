package dev.starcore.starcore.module.army.navy.event;

import dev.starcore.starcore.module.army.navy.model.NavyBattleResult;
import dev.starcore.starcore.module.army.navy.model.NavyUnit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Event;

import java.util.UUID;

/**
 * 海战事件
 */
public final class NavyBattleEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final NavyBattleResult result;
    private final NavyUnit attacker;
    private final NavyUnit defender;

    public NavyBattleEvent(NavyUnit attacker, NavyUnit defender, NavyBattleResult result) {
        this.attacker = attacker;
        this.defender = defender;
        this.result = result;
    }

    public NavyBattleResult getResult() {
        return result;
    }

    public NavyUnit getAttacker() {
        return attacker;
    }

    public NavyUnit getDefender() {
        return defender;
    }

    public UUID getAttackerId() {
        return attacker != null ? attacker.id() : null;
    }

    public UUID getDefenderId() {
        return defender != null ? defender.id() : null;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
