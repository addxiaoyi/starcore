package dev.starcore.starcore.module.shop.event;

import dev.starcore.starcore.module.shop.model.Shop;
import dev.starcore.starcore.module.shop.model.ShopItem;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 商店物品点击事件
 * 当玩家点击商店中的物品时触发
 */
public class ShopItemClickEvent extends PlayerEvent {

    private static final HandlerList handlers = new HandlerList();

    private final Shop shop;
    private final ShopItem shopItem;
    private final ClickType clickType;
    private final int slot;

    public enum ClickType {
        LEFT_CLICK,
        RIGHT_CLICK,
        SHIFT_LEFT,
        SHIFT_RIGHT,
        NUMBER_KEY
    }

    public ShopItemClickEvent(
        @NotNull Player who,
        Shop shop,
        ShopItem shopItem,
        ClickType clickType,
        int slot
    ) {
        super(who);
        this.shop = shop;
        this.shopItem = shopItem;
        this.clickType = clickType;
        this.slot = slot;
    }

    public Shop getShop() {
        return shop;
    }

    public ShopItem getShopItem() {
        return shopItem;
    }

    public ClickType getClickType() {
        return clickType;
    }

    public int getSlot() {
        return slot;
    }

    public boolean isLeftClick() {
        return clickType == ClickType.LEFT_CLICK || clickType == ClickType.SHIFT_LEFT;
    }

    public boolean isRightClick() {
        return clickType == ClickType.RIGHT_CLICK || clickType == ClickType.SHIFT_RIGHT;
    }

    public boolean isShiftClick() {
        return clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}
