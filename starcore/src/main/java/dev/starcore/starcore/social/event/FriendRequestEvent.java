package dev.starcore.starcore.social.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 好友请求事件
 */
public class FriendRequestEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final UUID senderId;
    private final UUID targetId;
    private boolean cancelled;

    public FriendRequestEvent(UUID senderId, UUID targetId) {
        this.senderId = senderId;
        this.targetId = targetId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public Player getSender() {
        return org.bukkit.Bukkit.getPlayer(senderId);
    }

    public Player getTarget() {
        return org.bukkit.Bukkit.getPlayer(targetId);
    }

    public String getSenderName() {
        Player sender = getSender();
        return sender != null ? sender.getName() :
            (org.bukkit.Bukkit.getOfflinePlayer(senderId).getName() != null ?
                org.bukkit.Bukkit.getOfflinePlayer(senderId).getName() : senderId.toString());
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
