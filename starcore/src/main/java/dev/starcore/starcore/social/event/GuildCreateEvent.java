package dev.starcore.starcore.social.event;

import dev.starcore.starcore.social.guild.Guild;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 公会创建事件
 */
public class GuildCreateEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final UUID creatorId;
    private final String guildName;
    private final String guildTag;
    private final Guild guild;
    private boolean cancelled;

    public GuildCreateEvent(Player creator, String guildName, String guildTag, Guild guild) {
        this.creatorId = creator.getUniqueId();
        this.guildName = guildName;
        this.guildTag = guildTag;
        this.guild = guild;
    }

    public UUID getCreatorId() {
        return creatorId;
    }

    public String getGuildName() {
        return guildName;
    }

    public String getGuildTag() {
        return guildTag;
    }

    public Guild getGuild() {
        return guild;
    }

    public Player getCreator() {
        return guild.getMembers().contains(creatorId) ?
            org.bukkit.Bukkit.getPlayer(creatorId) : null;
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
