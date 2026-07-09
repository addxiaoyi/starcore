package dev.starcore.starcore.module.banner.event;

import dev.starcore.starcore.module.banner.model.NationBanner;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Event fired when a nation's banner is created
 */
public class BannerCreatedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final UUID nationId;
    private final String nationName;
    private final NationBanner banner;
    private final Player creator;

    public BannerCreatedEvent(UUID nationId, String nationName, NationBanner banner, Player creator) {
        this.nationId = nationId;
        this.nationName = nationName;
        this.banner = banner;
        this.creator = creator;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public UUID getNationId() {
        return nationId;
    }

    public String getNationName() {
        return nationName;
    }

    public NationBanner getBanner() {
        return banner;
    }

    public Player getCreator() {
        return creator;
    }
}