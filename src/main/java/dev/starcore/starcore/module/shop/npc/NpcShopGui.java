package dev.starcore.starcore.module.shop.npc;

import dev.starcore.starcore.module.shop.npc.TransactionResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

/**
 * NPC商店GUI界面
 * 提供图形化的商店交互界面
 */
public class NpcShopGui {

    private static final String SHOP_GUI_TITLE = "NPC商店";
    private static final String SHOP_EDIT_TITLE = "编辑商店";
    private static final int SHOP_GUI_SIZE = 54; // 6行

    // 特殊槽位
    private static final int PAGE_PREV_SLOT = 45;
    private static final int PAGE_INFO_SLOT = 49;
    private static final int PAGE_NEXT_SLOT = 53;

    // 功能按钮槽位
    private static final int SELL_MODE_SLOT = 47;
    private static final int BUY_MODE_SLOT = 48;
    private static final int CLOSE_SLOT = 50;

    // 数量选择相关槽位
    private static final int QUANTITY_MINUS_SLOT = 42;
    private static final int QUANTITY_DISPLAY_SLOT = 43;
    private static final int QUANTITY_PLUS_SLOT = 44;

    // 每页物品数量（排除底部功能栏）
    private static final int ITEMS_PER_PAGE = 36; // 6x6 网格，底部保留更多空间

    // 数量选择状态
    private static final Map<UUID, Integer> playerQuantities = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_QUANTITY = 64;
    private static final int QUANTITY_STEP = 1;

    /**
     * 打开商店GUI
     */
    public static void openShop(Player player, UUID shopId, NpcShopService service, NpcShopServiceImpl.ShopEconomyService economyService) {
        openShop(player, shopId, 0, service, economyService);
    }

    /**
     * 打开商店GUI（指定页码）
     */
    public static void openShop(Player player, UUID shopId, int page, NpcShopService service, NpcShopServiceImpl.ShopEconomyService economyService) {
        Optional<NpcShopData> shopOpt = service.getShop(shopId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("商店不存在", NamedTextColor.RED));
            return;
        }

        NpcShopData shop = shopOpt.get();
        List<NpcShopItemData> items = service.getShopItems(shopId);

        // 计算总页数并校验页码
        int totalPages = (items.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE;
        if (totalPages == 0) totalPages = 1;
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        // 初始化玩家选择的数量
        playerQuantities.putIfAbsent(player.getUniqueId(), 1);

        // 创建GUI（保存当前页码）
        String title = shop.name() + " - " + SHOP_GUI_TITLE;
        ShopHolder holder = new ShopHolder(shopId, page);
        Inventory gui = Bukkit.createInventory(holder, SHOP_GUI_SIZE, Component.text(title, NamedTextColor.GOLD));

        // 填充物品
        fillItems(gui, items, page);

        // 填充功能按钮
        fillFunctionButtons(gui, shop, page, items.size(), service);

        // 填充数量选择区域
        fillQuantitySelector(gui, player.getUniqueId());

        // 播放打开声音
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);

        // 打开GUI
        player.openInventory(gui);
    }

    /**
     * 填充数量选择器
     */
    private static void fillQuantitySelector(Inventory gui, UUID playerId) {
        int quantity = playerQuantities.getOrDefault(playerId, 1);

        // 减号按钮
        ItemStack minusBtn = createButton(
            Material.ARROW,
            NamedTextColor.RED + "- 1",
            "点击减少数量",
            "当前: " + quantity
        );
        gui.setItem(QUANTITY_MINUS_SLOT, minusBtn);

        // 数量显示
        ItemStack quantityDisplay = new ItemStack(Material.PAPER);
        ItemMeta meta = quantityDisplay.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("数量: " + quantity, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("--- 操作提示 ---", NamedTextColor.GRAY));
            lore.add(Component.text("点击 +/- 调整数量", NamedTextColor.GRAY));
            lore.add(Component.text("Shift+点击 +/- 调整10", NamedTextColor.GRAY));
            lore.add(Component.text("最大数量: " + MAX_QUANTITY, NamedTextColor.GRAY));
            meta.lore(lore);
            quantityDisplay.setItemMeta(meta);
        }
        gui.setItem(QUANTITY_DISPLAY_SLOT, quantityDisplay);

        // 加号按钮
        ItemStack plusBtn = createButton(
            Material.ARROW,
            NamedTextColor.GREEN + "+ 1",
            "点击增加数量",
            "当前: " + quantity
        );
        gui.setItem(QUANTITY_PLUS_SLOT, plusBtn);
    }

    /**
     * 更新玩家的选择数量
     */
    public static void updateQuantity(UUID playerId, int delta) {
        int current = playerQuantities.getOrDefault(playerId, 1);
        int newQuantity = Math.max(1, Math.min(MAX_QUANTITY, current + delta));
        playerQuantities.put(playerId, newQuantity);
    }

    /**
     * 获取玩家当前选择数量
     */
    public static int getPlayerQuantity(UUID playerId) {
        return playerQuantities.getOrDefault(playerId, 1);
    }

    /**
     * 重置玩家数量选择
     */
    public static void resetQuantity(UUID playerId) {
        playerQuantities.put(playerId, 1);
    }

    /**
     * 填充物品到GUI
     */
    private static void fillItems(Inventory gui, List<NpcShopItemData> items, int page) {
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, items.size());

        // 清空物品区域
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            gui.setItem(i, createFillerItem(Material.BLACK_STAINED_GLASS_PANE));
        }

        // 填充物品
        for (int i = startIndex; i < endIndex; i++) {
            NpcShopItemData item = items.get(i);
            ItemStack itemStack = createShopItem(item);
            gui.setItem(i - startIndex, itemStack);
        }
    }

    /**
     * 填充功能按钮
     */
    private static void fillFunctionButtons(Inventory gui, NpcShopData shop, int page, int totalItems, NpcShopService service) {
        // 上一页按钮
        ItemStack prevButton = createButton(
            Material.ARROW,
            "上一页",
            "当前页: " + (page + 1)
        );
        gui.setItem(PAGE_PREV_SLOT, prevButton);

        // 页码信息
        int totalPages = (totalItems + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE;
        if (totalPages == 0) totalPages = 1;
        ItemStack infoButton = createButton(
            Material.BOOK,
            "第 " + (page + 1) + " / " + totalPages + " 页",
            "共 " + totalItems + " 件物品"
        );
        gui.setItem(PAGE_INFO_SLOT, infoButton);

        // 下一页按钮
        ItemStack nextButton = createButton(
            Material.ARROW,
            "下一页",
            "当前页: " + (page + 1)
        );
        gui.setItem(PAGE_NEXT_SLOT, nextButton);

        // 购买模式按钮
        ItemStack buyButton = createButton(
            shop.buyEnabled() ? Material.GOLD_INGOT : Material.GRAY_DYE,
            shop.buyEnabled() ? NamedTextColor.GREEN + "购买模式" : NamedTextColor.GRAY + "购买已禁用",
            shop.buyEnabled() ? "点击切换到购买模式" : "此商店禁止购买"
        );
        gui.setItem(BUY_MODE_SLOT, buyButton);

        // 出售模式按钮
        ItemStack sellButton = createButton(
            shop.sellEnabled() ? Material.IRON_INGOT : Material.GRAY_DYE,
            shop.sellEnabled() ? NamedTextColor.AQUA + "出售模式" : NamedTextColor.GRAY + "出售已禁用",
            shop.sellEnabled() ? "点击切换到出售模式" : "此商店禁止出售"
        );
        gui.setItem(SELL_MODE_SLOT, sellButton);

        // 关闭按钮
        ItemStack closeButton = createButton(
            Material.BARRIER,
            NamedTextColor.RED + "关闭",
            "点击关闭商店"
        );
        gui.setItem(CLOSE_SLOT, closeButton);

        // 填充边框
        for (int slot : new int[]{46, 51, 52}) {
            gui.setItem(slot, createFillerItem(Material.BLACK_STAINED_GLASS_PANE));
        }
    }

    /**
     * 创建商店物品显示（增强版）
     */
    private static ItemStack createShopItem(NpcShopItemData shopItem) {
        Material material;
        try {
            material = Material.valueOf(shopItem.material());
        } catch (IllegalArgumentException e) {
            material = Material.BARRIER;
        }

        int playerQty = 1; // 默认数量
        ItemStack item = new ItemStack(material, Math.min(shopItem.amount(), 64));
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // 设置显示名称
            String displayName = shopItem.displayName() != null ? shopItem.displayName() : formatMaterialName(shopItem.material());
            meta.displayName(Component.text(displayName, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));

            // 设置描述
            List<Component> lore = new ArrayList<>();

            // 价格预览（显示1个和玩家选择数量的价格）
            lore.add(Component.text("=== 价格预览 ===", NamedTextColor.DARK_PURPLE));
            lore.add(Component.text("单件购买: " + shopItem.buyPrice() + " 金币", NamedTextColor.GOLD));
            if (shopItem.sellPrice().signum() > 0) {
                lore.add(Component.text("单件出售: " + shopItem.sellPrice() + " 金币", NamedTextColor.GREEN));
            }

            // 批量价格预览
            lore.add(Component.text(" ", NamedTextColor.GRAY));
            lore.add(Component.text("批量价格 (x1/x16/x32/x64):", NamedTextColor.GRAY));
            lore.add(Component.text("  购买: " +
                formatBulkPrice(shopItem.buyPrice(), 1) + " / " +
                formatBulkPrice(shopItem.buyPrice(), 16) + " / " +
                formatBulkPrice(shopItem.buyPrice(), 32) + " / " +
                formatBulkPrice(shopItem.buyPrice(), 64), NamedTextColor.GOLD));
            if (shopItem.sellPrice().signum() > 0) {
                lore.add(Component.text("  出售: " +
                    formatBulkPrice(shopItem.sellPrice(), 1) + " / " +
                    formatBulkPrice(shopItem.sellPrice(), 16) + " / " +
                    formatBulkPrice(shopItem.sellPrice(), 32) + " / " +
                    formatBulkPrice(shopItem.sellPrice(), 64), NamedTextColor.GREEN));
            }

            // 库存信息（带颜色状态指示）
            lore.add(Component.text(" ", NamedTextColor.GRAY));
            if (shopItem.infiniteStock()) {
                lore.add(Component.text("库存: 无限", NamedTextColor.AQUA));
            } else {
                int stock = shopItem.stock();
                int maxStock = shopItem.maxStock();
                double percentage = (double) stock / maxStock * 100;

                NamedTextColor stockColor;
                String stockStatus;
                if (percentage > 50) {
                    stockColor = NamedTextColor.GREEN;
                    stockStatus = "充足";
                } else if (percentage > 20) {
                    stockColor = NamedTextColor.YELLOW;
                    stockStatus = "较低";
                } else if (percentage > 0) {
                    stockColor = NamedTextColor.RED;
                    stockStatus = "紧缺";
                } else {
                    stockColor = NamedTextColor.DARK_RED;
                    stockStatus = "缺货";
                }

                lore.add(Component.text()
                    .color(stockColor)
                    .append(Component.text("库存: "))
                    .append(Component.text(stock + " / " + maxStock, stockColor))
                    .append(Component.text(" [" + stockStatus + "]"))
                    .build());
            }

            // 操作提示
            lore.add(Component.text(" ", NamedTextColor.GRAY));
            lore.add(Component.text("=== 操作提示 ===", NamedTextColor.DARK_PURPLE));
            lore.add(Component.text("左键 - 使用当前数量购买", NamedTextColor.YELLOW));
            lore.add(Component.text("右键 - 购买 1 个", NamedTextColor.YELLOW));
            if (shopItem.sellPrice().signum() > 0) {
                lore.add(Component.text("Shift+左键 - 使用当前数量出售", NamedTextColor.AQUA));
                lore.add(Component.text("Shift+右键 - 出售 1 个", NamedTextColor.AQUA));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 格式化批量价格
     */
    private static String formatBulkPrice(BigDecimal unitPrice, int quantity) {
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity));
        return total.toPlainString();
    }

    /**
     * 创建功能按钮
     */
    private static ItemStack createButton(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));

            List<Component> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(Component.text(line, NamedTextColor.GRAY));
            }
            meta.lore(loreList);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 创建填充物品（边框）
     */
    private static ItemStack createFillerItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(" ", NamedTextColor.BLACK));
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 格式化物品名称
     */
    private static String formatMaterialName(String materialName) {
        StringBuilder result = new StringBuilder();
        String[] words = materialName.toLowerCase().split("_");

        for (String word : words) {
            if (result.length() > 0) {
                result.append(" ");
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            result.append(word.substring(1));
        }

        return result.toString();
    }

    /**
     * 处理GUI点击
     */
    public static boolean handleClick(Player player, int slot, ItemStack clickedItem, NpcShopService service, NpcShopServiceImpl.ShopEconomyService economyService) {
        if (clickedItem == null || clickedItem.getType() == Material.BLACK_STAINED_GLASS_PANE) {
            return false;
        }

        // 获取商店holder
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        if (!(holder instanceof ShopHolder shopHolder)) {
            return false;
        }

        UUID shopId = shopHolder.getShopId();
        Optional<NpcShopData> shopOpt = service.getShop(shopId);
        if (shopOpt.isEmpty()) {
            return false;
        }

        NpcShopData shop = shopOpt.get();
        UUID playerId = player.getUniqueId();

        // 处理功能按钮
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            // 清理玩家状态
            playerQuantities.remove(playerId);
            return true;
        }

        // 处理数量选择器
        if (slot == QUANTITY_MINUS_SLOT) {
            int delta = player.isSneaking() ? -10 : -1;
            updateQuantity(playerId, delta);
            refreshQuantityDisplay(player, shopHolder, service, economyService);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.0f);
            return true;
        }

        if (slot == QUANTITY_PLUS_SLOT) {
            int delta = player.isSneaking() ? 10 : 1;
            updateQuantity(playerId, delta);
            refreshQuantityDisplay(player, shopHolder, service, economyService);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.2f);
            return true;
        }

        if (slot == PAGE_PREV_SLOT || slot == PAGE_NEXT_SLOT) {
            // 分页处理
            int currentPage = shopHolder.getCurrentPage();
            List<NpcShopItemData> items = service.getShopItems(shopId);
            int totalPages = (items.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE;
            if (totalPages == 0) totalPages = 1;

            int newPage;
            if (slot == PAGE_PREV_SLOT) {
                newPage = currentPage - 1;
                if (newPage < 0) newPage = totalPages - 1; // 循环到最后一页
            } else {
                newPage = currentPage + 1;
                if (newPage >= totalPages) newPage = 0; // 循环到第一页
            }

            // 更新页码并刷新GUI
            shopHolder.setCurrentPage(newPage);
            openShop(player, shopId, newPage, service, economyService);
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 1.0f);
            return true;
        }

        if (slot == PAGE_INFO_SLOT) {
            // 显示商店信息
            player.sendMessage(Component.text()
                .append(Component.text("=== " + shop.name() + " ===", NamedTextColor.GOLD)));
            player.sendMessage(Component.text("拥有者: " + shop.ownerName(), NamedTextColor.GRAY));
            player.sendMessage(Component.text("物品数: " + service.getShopItems(shopId).size(), NamedTextColor.GRAY));
            player.sendMessage(Component.text("总收入: " + service.getShopRevenue(shopId) + " 金币", NamedTextColor.GOLD));
            player.sendMessage(Component.text("交易次数: " + service.getShopTransactionCount(shopId), NamedTextColor.AQUA));
            return true;
        }

        // 处理物品购买/出售
        if (slot < ITEMS_PER_PAGE) {
            int currentPage = shopHolder.getCurrentPage();
            int startIndex = currentPage * ITEMS_PER_PAGE;
            int itemIndex = startIndex + slot;
            List<NpcShopItemData> items = service.getShopItems(shopId);

            if (itemIndex >= items.size()) {
                return true;
            }

            NpcShopItemData item = items.get(itemIndex);

            // 获取玩家选择的数量
            int selectedQuantity = getPlayerQuantity(playerId);

            // 检查是否是出售模式（需要手持物品）
            boolean isSellMode = player.isSneaking() &&
                shop.sellEnabled() &&
                item.sellPrice().signum() > 0;

            if (isSellMode) {
                // 出售模式
                int quantity = selectedQuantity;
                TransactionResult result = service.sellItem(player, shopId, item.id(), quantity);
                sendTransactionResult(player, result);

                // 刷新GUI
                if (result.success()) {
                    openShop(player, shopId, service, economyService);
                }
            } else if (shop.buyEnabled()) {
                // 购买模式
                int quantity = selectedQuantity;

                TransactionResult result = service.buyItem(player, shopId, item.id(), quantity);
                sendTransactionResult(player, result);

                // 刷新GUI
                if (result.success()) {
                    openShop(player, shopId, service, economyService);
                }
            }

            return true;
        }

        return false;
    }

    /**
     * 刷新数量显示区域
     */
    private static void refreshQuantityDisplay(Player player, ShopHolder holder, NpcShopService service, NpcShopServiceImpl.ShopEconomyService economyService) {
        Inventory gui = player.getOpenInventory().getTopInventory();
        if (gui == null) return;

        fillQuantitySelector(gui, player.getUniqueId());
    }

    /**
     * 发送交易结果消息（增强版）
     */
    private static void sendTransactionResult(Player player, TransactionResult result) {
        NamedTextColor color = result.success() ? NamedTextColor.GREEN : NamedTextColor.RED;
        player.sendMessage(Component.text(result.message(), color));

        if (result.success()) {
            // 成功音效
            if (result.isBuy()) {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 2.0f);
            }

            // 显示成功粒子效果（如果有足够权限）
            spawnTransactionParticles(player, result.isBuy());

            // 显示成功标题
            showTransactionTitle(player, true, result.message());
        } else {
            // 失败音效
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);

            // 失败时给予短暂眩晕效果
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 0, true, false));

            // 显示失败标题
            showTransactionTitle(player, false, result.message());
        }
    }

    /**
     * 显示交易标题
     */
    private static void showTransactionTitle(Player player, boolean success, String message) {
        Component title = Component.text(success ? "交易成功!" : "交易失败", success ? NamedTextColor.GREEN : NamedTextColor.RED);
        Component subtitle = Component.text(message, NamedTextColor.WHITE);

        player.showTitle(Title.title(
            title,
            subtitle,
            Title.Times.times(
                Duration.ofMillis(100),
                Duration.ofMillis(1500),
                Duration.ofMillis(300)
            )
        ));
    }

    /**
     * 生成交易粒子效果
     */
    private static void spawnTransactionParticles(Player player, boolean isPurchase) {
        if (isPurchase) {
            // 购买 - 蓝色/绿色粒子
            player.getWorld().spawnParticle(
                org.bukkit.Particle.HAPPY_VILLAGER,
                player.getLocation().add(0, 1, 0),
                12,
                0.5, 0.5, 0.5,
                0.02
            );
            player.getWorld().spawnParticle(
                org.bukkit.Particle.INSTANT_EFFECT,
                player.getLocation().add(0, 1, 0),
                5,
                0.3, 0.3, 0.3,
                0.01
            );
        } else {
            // 出售 - 金色粒子
            player.getWorld().spawnParticle(
                org.bukkit.Particle.END_ROD,
                player.getLocation().add(0, 1.2, 0),
                15,
                0.5, 0.5, 0.5,
                0.03
            );
            player.getWorld().spawnParticle(
                org.bukkit.Particle.INSTANT_EFFECT,
                player.getLocation().add(0, 1, 0),
                8,
                0.3, 0.3, 0.3,
                0.02
            );
        }
    }

    /**
     * 统计玩家背包中指定物品数量
     */
    private static int countPlayerItems(Player player, Material material) {
        return player.getInventory().all(material).values().stream()
            .mapToInt(ItemStack::getAmount)
            .sum();
    }

    /**
     * 创建价格预览提示
     */
    public static Component createPricePreview(int quantity, BigDecimal buyPrice, BigDecimal sellPrice, boolean isBuying) {
        BigDecimal total = isBuying ? buyPrice.multiply(BigDecimal.valueOf(quantity)) : sellPrice.multiply(BigDecimal.valueOf(quantity));
        return Component.text()
            .color(isBuying ? NamedTextColor.GOLD : NamedTextColor.GREEN)
            .append(Component.text("总价: " + total + " 金币", isBuying ? NamedTextColor.GOLD : NamedTextColor.GREEN))
            .build();
    }

    /**
     * GUI容器持有者
     * 实现 InventoryHolder 接口，提供商店上下文存储
     */
    private static class ShopHolder implements InventoryHolder {
        private final UUID shopId;
        private int currentPage;

        public ShopHolder(UUID shopId) {
            this.shopId = shopId;
            this.currentPage = 0;
        }

        public ShopHolder(UUID shopId, int currentPage) {
            this.shopId = shopId;
            this.currentPage = currentPage;
        }

        public UUID getShopId() {
            return shopId;
        }

        public int getCurrentPage() {
            return currentPage;
        }

        public void setCurrentPage(int page) {
            this.currentPage = page;
        }

        /**
         * InventoryHolder 接口要求的实现
         * 返回 null，因为实际的 Inventory 由 Bukkit.createInventory() 自动管理
         */
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
