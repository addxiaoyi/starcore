package dev.starcore.starcore.module.shop.event;

import dev.starcore.starcore.module.shop.model.Shop;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * 商店打开事件
 */
public class ShopOpenEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();

    private final Shop shop;

    public ShopOpenEvent(Player player, Shop shop) {
        super(player);
        this.shop = shop;
    }

    public Shop getShop() {
        return shop;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
