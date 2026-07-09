package dev.starcore.starcore.module.army.mercenary.event;

import dev.starcore.starcore.module.army.mercenary.MercenaryRank;
import dev.starcore.starcore.module.army.mercenary.MercenaryType;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/**
 * 雇佣兵晋升事件
 */
public final class MercenaryPromotedEvent extends MercenaryContractEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MercenaryRank previousRank;
    private final MercenaryRank newRank;
    private final int experienceGained;

    public MercenaryPromotedEvent(
        UUID mercenaryId,
        UUID employerId,
        UUID nationId,
        String mercenaryName,
        String employerName,
        String nationName,
        MercenaryType type,
        MercenaryRank previousRank,
        MercenaryRank newRank,
        int experienceGained
    ) {
        super(mercenaryId, employerId, nationId, mercenaryName, employerName, nationName, type, newRank);
        this.previousRank = previousRank;
        this.newRank = newRank;
        this.experienceGained = experienceGained;
    }

    public MercenaryRank getPreviousRank() {
        return previousRank;
    }

    public MercenaryRank getNewRank() {
        return newRank;
    }

    public int getExperienceGained() {
        return experienceGained;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}