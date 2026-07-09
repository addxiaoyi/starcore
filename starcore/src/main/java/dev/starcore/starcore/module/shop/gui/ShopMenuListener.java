package dev.starcore.starcore.module.shop.gui;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.shop.event.*;
import dev.starcore.starcore.module.shop.model.*;
import dev.starcore.starcore.module.shop.service.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 商店菜单监听器 - 完整实现版
 * 处理商店GUI的交互事件，包括购买、出售、交易历史等
 */
public final class ShopMenuListener implements org.bukkit.event.Listener {

    private final ShopService shopService;
    private final NpcShopService npcShopService;
    private final EconomyService economyService;
    private final NationService nationService;
    private final MessageService messages;
    private final Plugin plugin;

    // 待处理交易（防止重复操作）
    private final Map<UUID, ShopTransactionContext> pendingTransactions = new ConcurrentHashMap<>();

    // 购买/出售模式状态
    private final Map<UUID, Boolean> playerBuyMode = new ConcurrentHashMap<>();

    private static final int[] QUANTITY_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int CONFIRM_SLOT = 22;
    private static final int CANCEL_SLOT = 4;

    public ShopMenuListener(
        ShopService shopService,
        NpcShopService npcShopService,
        EconomyService economyService,
        NationService nationService,
        MessageService messages,
        Plugin plugin
    ) {
        this.shopService = shopService;
        this.npcShopService = npcShopService;
        this.economyService = economyService;
        this.nationService = nationService;
        this.messages = messages;
        this.plugin = plugin;
    }

    // ==================== 事件监听 ====================

    @org.bukkit.event.EventHandler
    public void onShopTransaction(ShopTransactionEvent event) {
        // 可以在此处理交易完成后的额外逻辑
        // 例如：发送通知、触发成就等
    }

    @org.bukkit.event.EventHandler
    public void onShopOpen(ShopOpenEvent event) {
        Player player = event.getPlayer();
        Shop shop = event.getShop();

        // 检查商店是否开放
        if (!shop.isOpen()) {
            sendMessage(player, "§c该商店当前不营业！");
            return;
        }

        // 检查玩家是否有访问权限
        if (!shop.canAccess(player.getUniqueId())) {
            sendMessage(player, "§c你没有权限访问该商店！");
            return;
        }

        // 设置默认模式为购买
        playerBuyMode.put(player.getUniqueId(), true);
    }

    // ==================== 点击处理 ====================

    /**
     * 处理商店菜单点击
     */
    public void handleShopClick(Shop shop, Player player, int slot, ItemStack clickedItem) {
        // 检查是否是模式切换按钮
        if (isModeSwitchSlot(slot)) {
            toggleBuyMode(player);
            return;
        }

        // 检查是否是数量按钮
        if (isQuantitySlot(slot)) {
            handleQuantityClick(player, slot);
            return;
        }

        // 检查是否是确认按钮
        if (slot == CONFIRM_SLOT) {
            executeTransaction(shop, player);
            return;
        }

        // 检查是否是取消按钮
        if (slot == CANCEL_SLOT) {
            cancelTransaction(player);
            return;
        }

        // 检查是否是物品槽
        if (isItemSlot(slot)) {
            handleItemClick(shop, player, slot, clickedItem);
        }
    }

    /**
     * 处理物品点击
     */
    private void handleItemClick(Shop shop, Player player, int slot, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;

        // 解析物品信息
        String itemData = getItemData(item);
        if (itemData == null) return;

        String[] parts = itemData.split(":");
        if (parts.length < 2) return;

        String type = parts[0];
        if (!"shopitem".equals(type)) return;

        UUID itemId;
        try {
            itemId = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            return;
        }

        Optional<ShopItem> optItem = shop.getItem(itemId);
        if (optItem.isEmpty()) return;

        ShopItem shopItem = optItem.get();
        boolean isBuyMode = playerBuyMode.getOrDefault(player.getUniqueId(), true);

        // 创建交易上下文
        ShopTransactionContext context = new ShopTransactionContext(
            shop,
            shopItem,
            isBuyMode ? 1 : getPlayerItemCount(player, shopItem.material()),
            isBuyMode,
            isBuyMode ? shopItem.calculateBuyPrice(1) : shopItem.calculateSellPrice(1)
        );
        pendingTransactions.put(player.getUniqueId(), context);

        // 播放点击音效
        playClickSound(player);
    }

    /**
     * 处理数量点击
     */
    private void handleQuantityClick(Player player, int slot) {
        ShopTransactionContext context = pendingTransactions.get(player.getUniqueId());
        if (context == null) return;

        int quantityChange = getQuantityFromSlot(slot);
        int newQuantity = Math.max(1, context.quantity + quantityChange);

        // 检查最大数量限制
        int maxQuantity = context.isBuyMode
            ? calculateMaxBuyQuantity(player, context.shopItem)
            : context.shopItem.stock();

        newQuantity = Math.min(newQuantity, maxQuantity);

        BigDecimal newPrice = context.isBuyMode
            ? context.shopItem.calculateBuyPrice(newQuantity)
            : context.shopItem.calculateSellPrice(newQuantity);

        // 更新上下文
        pendingTransactions.put(player.getUniqueId(), new ShopTransactionContext(
            context.shop,
            context.shopItem,
            newQuantity,
            context.isBuyMode,
            newPrice
        ));
    }

    /**
     * 执行交易
     */
    private void executeTransaction(Shop shop, Player player) {
        ShopTransactionContext context = pendingTransactions.get(player.getUniqueId());
        if (context == null) return;

        UUID playerId = player.getUniqueId();
        int quantity = context.quantity;
        BigDecimal totalPrice = context.price;

        if (context.isBuyMode) {
            executePurchase(shop, context.shopItem, player, quantity, totalPrice);
        } else {
            executeSale(shop, context.shopItem, player, quantity, totalPrice);
        }

        pendingTransactions.remove(playerId);
    }

    /**
     * 执行购买
     */
    private void executePurchase(Shop shop, ShopItem shopItem, Player player, int quantity, BigDecimal totalPrice) {
        // 1. 检查玩家金币
        BigDecimal playerBalance = economyService.getBalance(player.getUniqueId());
        if (playerBalance.compareTo(totalPrice) < 0) {
            sendMessage(player, "§c金币不足！需要 " + formatPrice(totalPrice) + " ✦");
            sendMessage(player, "§7你的余额: " + formatPrice(playerBalance) + " ✦");
            playErrorSound(player);
            return;
        }

        // 2. 检查库存
        if (!shopItem.infiniteStock() && shopItem.stock() < quantity) {
            sendMessage(player, "§c库存不足！当前库存: " + shopItem.stock());
            playErrorSound(player);
            return;
        }

        // 3. 检查背包空间
        int emptySlots = getEmptySlots(player);
        if (emptySlots < 1) {
            sendMessage(player, "§c背包空间不足！");
            playErrorSound(player);
            return;
        }

        // 4. 执行扣款
        economyService.withdraw(player.getUniqueId(), totalPrice);

        // 5. 给予物品
        ItemStack item = shopItem.toBukkitItemStack();
        item.setAmount(quantity);
        giveItems(player, item);

        // 6. 更新商店库存
        if (!shopItem.infiniteStock()) {
            shopItem.purchase(quantity);
        }

        // 7. 发送成功消息
        sendMessage(player, "§a成功购买 " + quantity + "x " + getItemName(shopItem.material()) + "！");
        sendMessage(player, "§7花费: -" + formatPrice(totalPrice) + " ✦");

        // 8. 关闭界面
        player.closeInventory();
        playSuccessSound(player);

        // 9. 触发交易完成事件
        Bukkit.getPluginManager().callEvent(new ShopTransactionEvent(
            player, shop, shopItem, TransactionType.BUY, quantity, totalPrice
        ));
    }

    /**
     * 执行出售
     */
    private void executeSale(Shop shop, ShopItem shopItem, Player player, int quantity, BigDecimal totalPrice) {
        // 1. 检查玩家手中物品
        int playerItemCount = getPlayerItemCount(player, shopItem.material());
        if (playerItemCount < quantity) {
            sendMessage(player, "§c你没有足够的物品！手中只有 " + playerItemCount + " 个");
            playErrorSound(player);
            return;
        }

        // 2. 检查商店金币（如果是玩家商店）
        if (shop.shopType() == ShopType.PLAYER) {
            // 玩家商店需要检查店主余额
            UUID ownerId = shop.ownerId();
            if (!economyService.has(ownerId, totalPrice)) {
                // 店主余额不足，尝试从商店余额中扣除
                BigDecimal shopBalance = getShopBalance(ownerId);
                if (shopBalance.compareTo(totalPrice) < 0) {
                    sendMessage(player, "§c商店余额不足！店主金币: " + formatPrice(shopBalance));
                    playErrorSound(player);
                    return;
                }
                // 从商店余额扣除
                deductShopBalance(ownerId, totalPrice);
            } else {
                // 从店主个人余额扣除
                economyService.withdraw(ownerId, totalPrice);
            }
        }

        // 3. 执行交易
        // 移除玩家物品
        removeItems(player, shopItem.material(), quantity);

        // 给玩家金币
        economyService.deposit(player.getUniqueId(), totalPrice);

        // 4. 更新商店库存
        if (!shopItem.infiniteStock()) {
            shopItem.sell(quantity);
        }

        // 5. 发送成功消息
        sendMessage(player, "§a成功出售 " + quantity + "x " + getItemName(shopItem.material()) + "！");
        sendMessage(player, "§7获得: +" + formatPrice(totalPrice) + " ✦");

        // 6. 关闭界面
        player.closeInventory();
        playSuccessSound(player);

        // 7. 触发交易完成事件
        Bukkit.getPluginManager().callEvent(new ShopTransactionEvent(
            player, shop, shopItem, TransactionType.SELL, quantity, totalPrice
        ));
    }

    // 玩家商店余额缓存 (ownerId -> balance)
    private static final Map<UUID, BigDecimal> shopBalances = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 获取商店余额
     */
    private BigDecimal getShopBalance(UUID ownerId) {
        return shopBalances.getOrDefault(ownerId, BigDecimal.ZERO);
    }

    /**
     * 扣除商店余额
     */
    private void deductShopBalance(UUID ownerId, BigDecimal amount) {
        BigDecimal current = shopBalances.getOrDefault(ownerId, BigDecimal.ZERO);
        shopBalances.put(ownerId, current.subtract(amount));
    }

    /**
     * 增加商店余额
     */
    private void addShopBalance(UUID ownerId, BigDecimal amount) {
        BigDecimal current = shopBalances.getOrDefault(ownerId, BigDecimal.ZERO);
        shopBalances.put(ownerId, current.add(amount));
    }

    /**
     * 取消交易
     */
    private void cancelTransaction(Player player) {
        pendingTransactions.remove(player.getUniqueId());
        sendMessage(player, "§7交易已取消");
        playCancelSound(player);
    }

    /**
     * 切换购买/出售模式
     */
    private void toggleBuyMode(Player player) {
        boolean currentMode = playerBuyMode.getOrDefault(player.getUniqueId(), true);
        boolean newMode = !currentMode;
        playerBuyMode.put(player.getUniqueId(), newMode);

        if (newMode) {
            sendMessage(player, "§a已切换到购买模式");
        } else {
            sendMessage(player, "§e已切换到出售模式");
        }
        playClickSound(player);
    }

    // ==================== 辅助方法 ====================

    private boolean isModeSwitchSlot(int slot) {
        return slot == 0;
    }

    private boolean isQuantitySlot(int slot) {
        for (int s : QUANTITY_SLOTS) {
            if (s == slot) return true;
        }
        return false;
    }

    private boolean isItemSlot(int slot) {
        return slot >= 10 && slot <= 16;
    }

    private int getQuantityFromSlot(int slot) {
        return switch (slot) {
            case 19 -> -10;
            case 20 -> -1;
            case 21 -> -64;
            case 23 -> 64;
            case 24 -> 1;
            case 25 -> 10;
            default -> 0;
        };
    }

    private int calculateMaxBuyQuantity(Player player, ShopItem item) {
        BigDecimal balance = economyService.getBalance(player.getUniqueId());
        BigDecimal price = item.buyPrice();

        if (price.signum() <= 0) {
            return item.infiniteStock() ? 64 : item.stock();
        }

        int maxByMoney = price.signum() > 0 ? balance.divide(price, java.math.RoundingMode.DOWN).intValue() : Integer.MAX_VALUE;
        int maxByStock = item.infiniteStock() ? 64 : item.stock();
        int maxBySlots = getEmptySlots(player);

        return Math.min(Math.min(maxByMoney, maxByStock), maxBySlots);
    }

    private int getEmptySlots(Player player) {
        return Arrays.stream(player.getInventory().getStorageContents())
            .filter(Objects::isNull)
            .toList().size();
    }

    private int getPlayerItemCount(Player player, Material material) {
        return Arrays.stream(player.getInventory().getContents())
            .filter(Objects::nonNull)
            .filter(item -> item.getType() == material)
            .mapToInt(ItemStack::getAmount)
            .sum();
    }

    private void giveItems(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        // 如果背包满了，掉落物品到地上
        if (!overflow.isEmpty()) {
            for (ItemStack drop : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            sendMessage(player, "§e背包已满，部分物品掉落在地上！");
        }
    }

    private void removeItems(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                int remove = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - remove);
                remaining -= remove;

                if (item.getAmount() <= 0) {
                    player.getInventory().setItem(i, null);
                }
            }
        }
    }

    private String getItemData(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        Component displayName = meta.displayName();
        if (displayName == null) return null;

        String text = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(displayName);

        // 解析lore中的数据
        if (meta.hasLore()) {
            List<Component> lore = meta.lore();
            if (lore != null && !lore.isEmpty()) {
                for (Component line : lore) {
                    String loreText = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(line);
                    if (loreText.contains("shopitem:")) {
                        int start = loreText.indexOf("shopitem:");
                        int end = loreText.indexOf("\"", start);
                        if (end > start) {
                            return loreText.substring(start, end);
                        }
                    }
                }
            }
        }

        return null;
    }

    private String getItemName(Material material) {
        return Arrays.stream(material.name().toLowerCase().split("_"))
            .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase())
            .collect(Collectors.joining(" "));
    }

    private String formatPrice(BigDecimal price) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(price);
    }

    private void sendMessage(Player player, String message) {
        player.sendMessage(net.kyori.adventure.text.Component.text(message));
    }

    private void playClickSound(Player player) {
        player.playSound(net.kyori.adventure.sound.Sound.sound(
            Key.key("minecraft:ui.button.click"),
            net.kyori.adventure.sound.Sound.Source.UI,
            0.5f,
            1.0f
        ));
    }

    private void playSuccessSound(Player player) {
        player.playSound(net.kyori.adventure.sound.Sound.sound(
            Key.key("minecraft:entity.experience_orb.pickup"),
            net.kyori.adventure.sound.Sound.Source.PLAYER,
            0.5f,
            1.2f
        ));
    }

    private void playErrorSound(Player player) {
        player.playSound(net.kyori.adventure.sound.Sound.sound(
            Key.key("minecraft:entity.villager.no"),
            net.kyori.adventure.sound.Sound.Source.NEUTRAL,
            0.5f,
            0.8f
        ));
    }

    private void playCancelSound(Player player) {
        player.playSound(net.kyori.adventure.sound.Sound.sound(
            Key.key("minecraft:ui.cartography_table.take_out"),
            net.kyori.adventure.sound.Sound.Source.UI,
            0.5f,
            0.8f
        ));
    }

    // ==================== 内部类 ====================

    /**
     * 交易上下文
     */
    private record ShopTransactionContext(
        Shop shop,
        ShopItem shopItem,
        int quantity,
        boolean isBuyMode,
        BigDecimal price
    ) {}

    /**
     * 注册监听器
     */
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // audit H-001: 修复 PlayerQuitEvent 未清理 playerBuyMode/pendingTransactions Map 导致的内存泄漏
    @org.bukkit.event.EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playerBuyMode.remove(playerId);
        pendingTransactions.remove(playerId);
    }

    // audit H-001: 修复 InventoryCloseEvent 未清理 playerBuyMode/pendingTransactions Map 导致的内存泄漏
    @org.bukkit.event.EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            UUID playerId = player.getUniqueId();
            playerBuyMode.remove(playerId);
            pendingTransactions.remove(playerId);
        }
    }
}
