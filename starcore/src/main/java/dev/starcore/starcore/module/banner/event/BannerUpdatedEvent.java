package dev.starcore.starcore.module.banner.event;

import dev.starcore.starcore.module.banner.model.NationBanner;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Event fired when a nation's banner is updated
 */
public class BannerUpdatedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final UUID nationId;
    private final String nationName;
    private final NationBanner oldBanner;
    private final NationBanner newBanner;
    private final Player updater;
    private final String updateType; // "pattern", "baseColor", "patternColor", "design"

    public BannerUpdatedEvent(UUID nationId, String nationName, NationBanner oldBanner, NationBanner newBanner, Player updater, String updateType) {
        this.nationId = nationId;
        this.nationName = nationName;
        this.oldBanner = oldBanner;
        this.newBanner = newBanner;
        this.updater = updater;
        this.updateType = updateType;
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

    public NationBanner getOldBanner() {
        return oldBanner;
    }

    public NationBanner getNewBanner() {
        return newBanner;
    }

    public Player getUpdater() {
        return updater;
    }

    public String getUpdateType() {
        return updateType;
    }
}