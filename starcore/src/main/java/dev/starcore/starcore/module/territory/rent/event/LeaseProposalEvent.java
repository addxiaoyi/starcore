package dev.starcore.starcore.module.territory.rent.event;

import dev.starcore.starcore.module.territory.rent.model.LeaseProposal;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Event fired when a lease proposal is created.
 */
public class LeaseProposalEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final LeaseProposal proposal;
    private final UUID proposerId;

    public LeaseProposalEvent(LeaseProposal proposal, UUID proposerId) {
        this.proposal = proposal;
        this.proposerId = proposerId;
    }

    public LeaseProposal getProposal() {
        return proposal;
    }

    public UUID getProposalId() {
        return proposal.proposalId();
    }

    public UUID getProposerId() {
        return proposerId;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
