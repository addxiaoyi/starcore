package dev.starcore.starcore.social.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 公会邀请事件
 */
public class GuildInviteEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final UUID inviterId;
    private final UUID targetId;
    private final UUID guildId;
    private final String guildName;
    private boolean cancelled;

    public GuildInviteEvent(UUID inviterId, UUID targetId, UUID guildId, String guildName) {
        this.inviterId = inviterId;
        this.targetId = targetId;
        this.guildId = guildId;
        this.guildName = guildName;
    }

    public UUID getInviterId() {
        return inviterId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public UUID getGuildId() {
        return guildId;
    }

    public String getGuildName() {
        return guildName;
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
