package dev.starcore.starcore.module.war.reparations.event;

import dev.starcore.starcore.war.WarReparation;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;

/**
 * 赔款支付事件
 * 在每次赔款支付成功后触发
 */
public class ReparationPaymentEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final WarReparation reparation;
    private final BigDecimal amountPaid;
    private final BigDecimal totalPaid;
    private final BigDecimal remaining;

    public ReparationPaymentEvent(WarReparation reparation, BigDecimal amountPaid,
                                  BigDecimal totalPaid, BigDecimal remaining) {
        this.reparation = reparation;
        this.amountPaid = amountPaid;
        this.totalPaid = totalPaid;
        this.remaining = remaining;
    }

    public WarReparation getReparation() {
        return reparation;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public BigDecimal getTotalPaid() {
        return totalPaid;
    }

    public BigDecimal getRemaining() {
        return remaining;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}