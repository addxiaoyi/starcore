package dev.starcore.starcore.module.army.mercenary.event;

import dev.starcore.starcore.module.army.mercenary.MercenaryRank;
import dev.starcore.starcore.module.army.mercenary.MercenaryType;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/**
 * 雇佣兵被雇佣事件
 */
public final class MercenaryHiredEvent extends MercenaryContractEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final int contractDurationDays;
    private final int totalCost;

    public MercenaryHiredEvent(
        UUID mercenaryId,
        UUID employerId,
        UUID nationId,
        String mercenaryName,
        String employerName,
        String nationName,
        MercenaryType type,
        MercenaryRank rank,
        int contractDurationDays,
        int totalCost
    ) {
        super(mercenaryId, employerId, nationId, mercenaryName, employerName, nationName, type, rank);
        this.contractDurationDays = contractDurationDays;
        this.totalCost = totalCost;
    }

    public int getContractDurationDays() {
        return contractDurationDays;
    }

    public int getTotalCost() {
        return totalCost;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}