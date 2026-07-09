package dev.starcore.starcore.module.territory.rent.event;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.territory.rent.model.LeaseContract;
import dev.starcore.starcore.module.territory.rent.model.LeasePayment;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Event fired when rent is collected for a contract.
 */
public class RentCollectedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final LeaseContract contract;
    private final LeasePayment payment;
    private final NationId lessorNationId;
    private final NationId lesseeNationId;

    public RentCollectedEvent(LeaseContract contract, LeasePayment payment) {
        this.contract = contract;
        this.payment = payment;
        this.lessorNationId = new NationId(contract.lessorNationId());
        this.lesseeNationId = contract.lesseeNationId() != null ?
            new NationId(contract.lesseeNationId()) : null;
    }

    public LeaseContract getContract() {
        return contract;
    }

    public LeasePayment getPayment() {
        return payment;
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
