package dev.starcore.starcore.module.army.mercenary.event;

import dev.starcore.starcore.module.army.mercenary.MercenaryRank;
import dev.starcore.starcore.module.army.mercenary.MercenaryType;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/**
 * 雇佣兵合同相关事件基类
 */
public abstract class MercenaryContractEvent extends org.bukkit.event.Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID mercenaryId;
    private final UUID employerId;
    private final UUID nationId;
    private final String mercenaryName;
    private final String employerName;
    private final String nationName;
    private final MercenaryType type;
    private final MercenaryRank rank;

    protected MercenaryContractEvent(
        UUID mercenaryId,
        UUID employerId,
        UUID nationId,
        String mercenaryName,
        String employerName,
        String nationName,
        MercenaryType type,
        MercenaryRank rank
    ) {
        this.mercenaryId = mercenaryId;
        this.employerId = employerId;
        this.nationId = nationId;
        this.mercenaryName = mercenaryName;
        this.employerName = employerName;
        this.nationName = nationName;
        this.type = type;
        this.rank = rank;
    }

    public UUID getMercenaryId() {
        return mercenaryId;
    }

    public UUID getEmployerId() {
        return employerId;
    }

    public UUID getNationId() {
        return nationId;
    }

    public String getMercenaryName() {
        return mercenaryName;
    }

    public String getEmployerName() {
        return employerName;
    }

    public String getNationName() {
        return nationName;
    }

    public MercenaryType getType() {
        return type;
    }

    public MercenaryRank getRank() {
        return rank;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}