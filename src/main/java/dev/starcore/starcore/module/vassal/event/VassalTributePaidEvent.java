package dev.starcore.starcore.module.vassal.event;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;

/**
 * 贡金缴纳事件
 * Fired when tribute is paid
 */
public class VassalTributePaidEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final NationId vassalId;
    private final NationId suzerainId;
    private final BigDecimal amount;

    public VassalTributePaidEvent(NationId vassalId, NationId suzerainId, BigDecimal amount) {
        this.vassalId = vassalId;
        this.suzerainId = suzerainId;
        this.amount = amount;
    }

    public NationId vassalId() {
        return vassalId;
    }

    public NationId suzerainId() {
        return suzerainId;
    }

    public BigDecimal amount() {
        return amount;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}