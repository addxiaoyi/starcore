package dev.starcore.starcore.module.shop.gui;

import dev.starcore.starcore.module.shop.model.*;
import dev.starcore.starcore.module.shop.service.ShopService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 商店创建GUI
 * 处理商店的图形界面交互
 */
public class ShopCreationGui {

    private final ShopService shopService;
    private final Player player;
    private final Shop shop;
    private Inventory inventory;

    // GUI常量
    private static final String SHOP_GUI_TITLE = "§6§l🏪 商店";
    private static final String TYPE_TITLE = "§e§l选择类型";
    private static final String PRICE_TITLE = "§a§l设置价格";

    public ShopCreationGui(ShopService shopService, Shop shop, Player player) {
        this.shopService = shopService;
        this.shop = shop;
        this.player = player;
    }

    /**
     * 打开商店主界面
     */
    public void open() {
        if (shop == null) {
            player.sendMessage(Component.text("商店不存在!", NamedTextColor.RED));
            return;
        }

        inventory = Bukkit.createInventory(null, 54, Component.text(SHOP_GUI_TITLE));
        fillBorder(inventory);

        // 商店信息
        inventory.setItem(4, createShopInfoItem());

        // 物品预览
        ItemStack preview = shop.items().isEmpty() ? new ItemStack(Material.CHEST) : shop.items().get(0).toBukkitItemStack();
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§e" + shop.shopType().name(), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7库存: §f" + getTotalStock(shop), NamedTextColor.GRAY));
            lore.add(Component.text("§7最大库存: §f" + getMaxStock(shop), NamedTextColor.GRAY));
            meta.lore(lore);
            preview.setItemMeta(meta);
        }
        inventory.setItem(22, preview);

        // 购买按钮
        if (shop.buyEnabled()) {
            inventory.setItem(20, createBuyButton(1));
            inventory.setItem(21, createBuyButton(16));
            inventory.setItem(23, createBuyButton(64));
        }

        // 出售按钮
        if (shop.sellEnabled()) {
            inventory.setItem(30, createSellButton(1));
            inventory.setItem(31, createSellButton(16));
            inventory.setItem(32, createSellButton(64));
        }

        // 商店拥有者信息
        inventory.setItem(49, createOwnerInfo());

        // 关闭按钮
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(Component.text("§c关闭", NamedTextColor.RED));
        close.setItemMeta(closeMeta);
        inventory.setItem(49, close);

        player.openInventory(inventory);
    }

    /**
     * 打开类型选择界面
     */
    public void openTypeSelection() {
        inventory = Bukkit.createInventory(null, 27, Component.text(TYPE_TITLE));
        fillBorder(inventory);

        // 标题
        inventory.setItem(4, createTitleItem("§e请选择商店类型"));

        // 玩家商店
        inventory.setItem(10, createTypeItem(ShopType.PLAYER,
            "§a购买商店",
            "§7玩家可以从商店购买物品",
            "§e玩家支付金币获取物品"
        ));

        // NPC商店
        inventory.setItem(13, createTypeItem(ShopType.NPC,
            "§cNPC商店",
            "§7绑定到NPC的商店",
            "§e需要配置NPC"
        ));

        // 服务器商店
        inventory.setItem(16, createTypeItem(ShopType.SERVER,
            "§b服务器商店",
            "§7管理员管理的商店",
            "§e官方商店"
        ));

        // 返回
        inventory.setItem(22, createBackButton());

        player.openInventory(inventory);
    }

    /**
     * 打开价格设置界面
     */
    public void openPriceSetting() {
        inventory = Bukkit.createInventory(null, 36, Component.text(PRICE_TITLE));
        fillBorder(inventory);

        // 标题
        inventory.setItem(4, createTitleItem("§a设置商品价格"));

        // 确认按钮
        ItemStack confirm = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = confirm.getItemMeta();
        meta.displayName(Component.text("§a✓ 确认创建", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        confirm.setItemMeta(meta);
        inventory.setItem(31, confirm);

        // 取消按钮
        inventory.setItem(22, createBackButton());

        player.openInventory(inventory);
    }

    /**
     * 刷新GUI
     */
    public void refresh() {
        if (inventory != null) {
            open();
        }
    }

    // ==================== Helper Methods ====================

    private int getTotalStock(Shop shop) {
        return shop.items().stream()
            .mapToInt(ShopItem::stock)
            .sum();
    }

    private int getMaxStock(Shop shop) {
        return shop.items().stream()
            .mapToInt(ShopItem::maxStock)
            .sum();
    }

    private BigDecimal getBuyPrice(Shop shop) {
        return shop.items().isEmpty() ? BigDecimal.ZERO : shop.items().get(0).buyPrice();
    }

    private BigDecimal getSellPrice(Shop shop) {
        return shop.items().isEmpty() ? BigDecimal.ZERO : shop.items().get(0).sellPrice();
    }

    // ==================== Item Creation ====================

    private ItemStack createShopInfoItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§6§l商店信息", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7所有者: §f" + getOwnerName(shop), NamedTextColor.GRAY));
            lore.add(Component.text("§7类型: §f" + shop.shopType().name(), NamedTextColor.GRAY));
            if (shop.location() != null) {
                lore.add(Component.text("§7位置: §f" + shop.location().getWorld().getName() + " (" +
                    shop.location().getBlockX() + ", " +
                    shop.location().getBlockY() + ", " +
                    shop.location().getBlockZ() + ")", NamedTextColor.GRAY));
            }
            lore.add(Component.text("", NamedTextColor.GRAY));
            lore.add(Component.text("§7购买价: §a" + getBuyPrice(shop) + " 金币/个", NamedTextColor.GREEN));
            lore.add(Component.text("§7出售价: §c" + getSellPrice(shop) + " 金币/个", NamedTextColor.RED));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String getOwnerName(Shop shop) {
        return switch (shop.ownerType()) {
            case PLAYER -> "玩家商店";
            case NATION -> "国家商店";
            case GUILD -> "公会商店";
            case SERVER -> "服务器商店";
        };
    }

    private ItemStack createBuyButton(int amount) {
        BigDecimal unitPrice = getBuyPrice(shop);
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(amount));

        Material material = switch (amount) {
            case 1 -> Material.GREEN_STAINED_GLASS_PANE;
            case 16 -> Material.LIME_STAINED_GLASS_PANE;
            default -> Material.GREEN_CONCRETE;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§a购买 x" + amount, NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7单价: §a" + unitPrice + " 金币", NamedTextColor.GRAY));
            lore.add(Component.text("§7总价: §e" + totalPrice + " 金币", NamedTextColor.YELLOW));
            lore.add(Component.text("", NamedTextColor.GRAY));
            lore.add(Component.text("§e点击购买", NamedTextColor.YELLOW));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSellButton(int amount) {
        BigDecimal unitPrice = getSellPrice(shop);
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(amount));

        Material material = switch (amount) {
            case 1 -> Material.RED_STAINED_GLASS_PANE;
            case 16 -> Material.PINK_STAINED_GLASS_PANE;
            default -> Material.RED_CONCRETE;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§c出售 x" + amount, NamedTextColor.RED).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7单价: §c" + unitPrice + " 金币", NamedTextColor.GRAY));
            lore.add(Component.text("§7总收入: §e" + totalPrice + " 金币", NamedTextColor.YELLOW));
            lore.add(Component.text("", NamedTextColor.GRAY));
            lore.add(Component.text("§e点击出售", NamedTextColor.YELLOW));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createOwnerInfo() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§7所有者: " + getOwnerName(shop), NamedTextColor.GRAY));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7点击查看所有者信息", NamedTextColor.DARK_GRAY));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createTypeItem(ShopType type, String name, String desc1, String desc2) {
        Material material = switch (type) {
            case PLAYER -> Material.GREEN_SHULKER_BOX;
            case NPC -> Material.BLUE_SHULKER_BOX;
            case AUCTION -> Material.ORANGE_SHULKER_BOX;
            case SERVER -> Material.PURPLE_SHULKER_BOX;
            case NATION -> Material.CYAN_SHULKER_BOX;
            case GUILD -> Material.YELLOW_SHULKER_BOX;
            case BLACK_MARKET -> Material.BLACK_SHULKER_BOX;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7" + desc1, NamedTextColor.GRAY));
            lore.add(Component.text("§a" + desc2, NamedTextColor.GREEN));
            lore.add(Component.text("", NamedTextColor.GRAY));
            lore.add(Component.text("§e点击选择", NamedTextColor.YELLOW));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPriceItem(String name, BigDecimal price, String type) {
        Material material = "buy".equals(type) ? Material.GOLD_INGOT : Material.IRON_INGOT;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§e当前价格: §f" + price + " 金币", NamedTextColor.GREEN));
            lore.add(Component.text("", NamedTextColor.GRAY));
            lore.add(Component.text("§7使用命令调整价格:", NamedTextColor.GRAY));
            lore.add(Component.text("§e/shop price <buy|sell> <价格>", NamedTextColor.YELLOW));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createTitleItem(String title) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(title, NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            meta.lore(List.of(Component.text("§7请按照指引设置", NamedTextColor.GRAY)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§c返回", NamedTextColor.RED));
        item.setItemMeta(meta);
        return item;
    }

    private void fillBorder(Inventory inv) {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        meta.displayName(Component.text(" "));
        border.setItemMeta(meta);

        int size = inv.getSize();
        int rows = size / 9;

        for (int row = 0; row < rows; row++) {
            inv.setItem(row * 9, border);
            inv.setItem(row * 9 + 8, border);
        }
        for (int col = 1; col < 8; col++) {
            inv.setItem(col, border);
            inv.setItem(size - 9 + col, border);
        }
    }
}
