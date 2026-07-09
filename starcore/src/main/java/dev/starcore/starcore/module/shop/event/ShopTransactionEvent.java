package dev.starcore.starcore.module.shop.event;

import dev.starcore.starcore.module.shop.model.Shop;
import dev.starcore.starcore.module.shop.model.ShopItem;
import dev.starcore.starcore.module.shop.model.TransactionType;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import java.math.BigDecimal;

/**
 * 商店交易事件
 */
public class ShopTransactionEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();

    private final Shop shop;
    private final ShopItem item;
    private final TransactionType type;
    private final int quantity;
    private final BigDecimal totalPrice;
    private final boolean cancelled;

    public ShopTransactionEvent(
        Player player,
        Shop shop,
        ShopItem item,
        TransactionType type,
        int quantity,
        BigDecimal totalPrice
    ) {
        super(player);
        this.shop = shop;
        this.item = item;
        this.type = type;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.cancelled = false;
    }

    public Shop getShop() {
        return shop;
    }

    public ShopItem getItem() {
        return item;
    }

    public TransactionType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
