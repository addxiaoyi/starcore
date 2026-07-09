package dev.starcore.starcore.module.lease.event;

import dev.starcore.starcore.module.lease.model.LeaseContract;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 租约租金支付事件
 */
public class LeasePaymentEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final LeaseContract contract;
    private final UUID payerId;
    private final BigDecimal amount;
    private final int monthsPaid;

    public LeasePaymentEvent(LeaseContract contract, UUID payerId, BigDecimal amount, int monthsPaid) {
        this.contract = contract;
        this.payerId = payerId;
        this.amount = amount;
        this.monthsPaid = monthsPaid;
    }

    public LeaseContract getContract() {
        return contract;
    }

    public UUID getPayerId() {
        return payerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getMonthsPaid() {
        return monthsPaid;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}