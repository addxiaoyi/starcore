package dev.starcore.starcore.module.satellite.event;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;

/**
 * 卫星国贡金缴纳事件
 */
public class SatelliteTributePaidEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final NationId suzerainId;
    private final NationId satelliteId;
    private final BigDecimal amount;
    private final BigDecimal previousSuzerainBalance;
    private final BigDecimal newSuzerainBalance;

    public SatelliteTributePaidEvent(
        NationId suzerainId,
        NationId satelliteId,
        BigDecimal amount,
        BigDecimal previousSuzerainBalance,
        BigDecimal newSuzerainBalance
    ) {
        this.suzerainId = suzerainId;
        this.satelliteId = satelliteId;
        this.amount = amount;
        this.previousSuzerainBalance = previousSuzerainBalance;
        this.newSuzerainBalance = newSuzerainBalance;
    }

    public NationId getSuzerainId() {
        return suzerainId;
    }

    public NationId getSatelliteId() {
        return satelliteId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getPreviousSuzerainBalance() {
        return previousSuzerainBalance;
    }

    public BigDecimal getNewSuzerainBalance() {
        return newSuzerainBalance;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
