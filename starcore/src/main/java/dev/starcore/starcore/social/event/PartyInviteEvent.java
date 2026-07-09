package dev.starcore.starcore.social.event;

import dev.starcore.starcore.social.party.Party;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 派对邀请事件
 */
public class PartyInviteEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final UUID inviterId;
    private final UUID targetId;
    private final Party party;
    private boolean cancelled;

    public PartyInviteEvent(UUID inviterId, UUID targetId, Party party) {
        this.inviterId = inviterId;
        this.targetId = targetId;
        this.party = party;
    }

    public UUID getInviterId() {
        return inviterId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public Party getParty() {
        return party;
    }

    public Player getInviter() {
        return org.bukkit.Bukkit.getPlayer(inviterId);
    }

    public Player getTarget() {
        return org.bukkit.Bukkit.getPlayer(targetId);
    }

    public String getInviterName() {
        Player inviter = getInviter();
        return inviter != null ? inviter.getName() :
            (org.bukkit.Bukkit.getOfflinePlayer(inviterId).getName() != null ?
                org.bukkit.Bukkit.getOfflinePlayer(inviterId).getName() : inviterId.toString());
    }

    public String getTargetName() {
        Player target = getTarget();
        return target != null ? target.getName() :
            (org.bukkit.Bukkit.getOfflinePlayer(targetId).getName() != null ?
                org.bukkit.Bukkit.getOfflinePlayer(targetId).getName() : targetId.toString());
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
