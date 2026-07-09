package dev.starcore.starcore.module.territory.rent.event;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.territory.rent.model.LeaseContract;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Event fired when a lease contract is created.
 */
public class LeaseCreatedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final LeaseContract contract;
    private final NationId lessorNationId;
    private final NationId lesseeNationId;
    private final UUID creatorId;

    public LeaseCreatedEvent(LeaseContract contract, UUID creatorId) {
        this.contract = contract;
        this.lessorNationId = new NationId(contract.lessorNationId());
        this.lesseeNationId = contract.lesseeNationId() != null ?
            new NationId(contract.lesseeNationId()) : null;
        this.creatorId = creatorId;
    }

    public LeaseContract getContract() {
        return contract;
    }

    public NationId getLessorNationId() {
        return lessorNationId;
    }

    public NationId getLesseeNationId() {
        return lesseeNationId;
    }

    public UUID getCreatorId() {
        return creatorId;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
