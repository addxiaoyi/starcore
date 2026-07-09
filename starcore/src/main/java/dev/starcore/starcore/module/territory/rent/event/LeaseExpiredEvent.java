package dev.starcore.starcore.module.territory.rent.event;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.territory.rent.model.LeaseContract;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a lease contract expires.
 */
public class LeaseExpiredEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final LeaseContract contract;
    private final NationId lessorNationId;
    private final NationId lesseeNationId;

    public LeaseExpiredEvent(LeaseContract contract) {
        this.contract = contract;
        this.lessorNationId = new NationId(contract.lessorNationId());
        this.lesseeNationId = contract.lesseeNationId() != null ?
            new NationId(contract.lesseeNationId()) : null;
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

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
