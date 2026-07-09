package dev.starcore.starcore.module.territory.rent.event;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.territory.rent.model.LeaseContract;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Event fired when a lease contract is terminated.
 */
public class LeaseTerminatedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final LeaseContract contract;
    private final NationId lessorNationId;
    private final NationId lesseeNationId;
    private final UUID terminatorId;
    private final String reason;

    public LeaseTerminatedEvent(LeaseContract contract, UUID terminatorId, String reason) {
        this.contract = contract;
        this.lessorNationId = new NationId(contract.lessorNationId());
        this.lesseeNationId = contract.lesseeNationId() != null ?
            new NationId(contract.lesseeNationId()) : null;
        this.terminatorId = terminatorId;
        this.reason = reason;
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

    public UUID getTerminatorId() {
        return terminatorId;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
