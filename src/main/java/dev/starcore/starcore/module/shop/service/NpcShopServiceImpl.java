package dev.starcore.starcore.module.shop.service;
import java.util.Optional;

import dev.starcore.starcore.audit.AuditLogService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.module.shop.model.*;
import dev.starcore.starcore.module.shop.storage.ShopDatabaseStorage;
import dev.starcore.starcore.foundation.message.MessageService;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * NPC商店服务实现
 */
public class NpcShopServiceImpl implements NpcShopService {
    private final Plugin plugin;
    private final ShopService shopService;
    private final ShopDatabaseStorage storage;
    private final MessageService messages;
    private final EconomyService economyService;
    private final AuditLogService auditLogService;

    // NPC到商店的映射
    private final Map<Integer, UUID> npcToShop = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> shopToNpc = new ConcurrentHashMap<>();

    // 交易限制缓存 - 使用独立的 PlayerTradeCount 类避免与 resource.model.TradeRecord 冲突
    private final Map<UUID, Map<UUID, PlayerTradeCount>> tradeCounts = new ConcurrentHashMap<>();

    // 商店模板
    private final Map<String, Shop> templates = new ConcurrentHashMap<>();

    // 商店类别
    private final List<ShopCategory> categories;

    // 商店收入缓存
    private final Map<UUID, BigDecimal> shopRevenue = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> shopTransactionCount = new ConcurrentHashMap<>();

    public NpcShopServiceImpl(
        Plugin plugin,
        ShopService shopService,
        ShopDatabaseStorage storage,
        MessageService messages,
        EconomyService economyService,
        AuditLogService auditLogService
    ) {
        this.plugin = plugin;
        this.shopService = shopService;
        this.storage = storage;
        this.messages = messages;
        this.economyService = economyService;
        this.auditLogService = auditLogService;

        // 初始化默认类别
        this.categories = new ArrayList<>(Arrays.asList(ShopCategory.values()));

        // 加载NPC绑定数据
        loadNpcBindings();

        // 加载交易记录
        loadTradeCounts();

        // 初始化默认模板
        initializeDefaultTemplates();
    }

    @Override
    public boolean bindShopToNpc(UUID shopId, int npcId) {
        // 检查商店是否存在
        if (shopService.getShop(shopId).isEmpty()) {
            return false;
        }

        // 检查NPC是否存在（如果Citizens可用）
        if (!isCitizensAvailable() || CitizensAPI.getNPCRegistry().getById(npcId) == null) {
            return false;
        }

        // 解除之前的绑定
        if (shopToNpc.containsKey(shopId)) {
            int oldNpcId = shopToNpc.get(shopId);
            npcToShop.remove(oldNpcId);
        }

        // 创建新绑定
        npcToShop.put(npcId, shopId);
        shopToNpc.put(shopId, npcId);

        // 更新商店
        shopService.getShop(shopId).ifPresent(shop -> {
            shop.setNpcId(npcId);
            shop.setShopType(ShopType.NPC);
            shopService.updateShop(shop);
        });

        storage.saveNpcBinding(npcId, shopId);
        return true;
    }

    @Override
    public boolean unbindShopFromNpc(int npcId) {
        UUID shopId = npcToShop.remove(npcId);
        if (shopId == null) {
            return false;
        }

        shopToNpc.remove(shopId);

        shopService.getShop(shopId).ifPresent(shop -> {
            shop.setNpcId(null);
            shop.setShopType(ShopType.PLAYER);
            shopService.updateShop(shop);
        });

        storage.deleteNpcBinding(npcId);
        return true;
    }

    @Override
    public Optional<Shop> getShopByNpc(int npcId) {
        UUID shopId = npcToShop.get(npcId);
        if (shopId == null) {
            return Optional.empty();
        }
        return shopService.getShop(shopId);
    }

    @Override
    public Optional<UUID> getNpcShopId(int npcId) {
        return Optional.ofNullable(npcToShop.get(npcId));
    }

    @Override
    public boolean hasShop(int npcId) {
        return npcToShop.containsKey(npcId);
    }

    @Override
    public Shop createNpcShopTemplate(String name, String description, ShopCategory category) {
        Shop template = Shop.create(
            UUID.randomUUID(),
            ShopOwnerType.SERVER,
            name,
            ShopType.NPC
        );
        template.setDescription(description);
        template.setGlobalPublic(true);
        template.setInfiniteStock(true);

        templates.put(name.toLowerCase(), template);
        storage.saveTemplate(template);
        return template;
    }

    @Override
    public Shop createNpcShop(String name, String description, int npcId, ShopCategory category) {
        // 检查NPC是否存在
        if (!isCitizensAvailable() || CitizensAPI.getNPCRegistry().getById(npcId) == null) {
            return null;
        }

        // 检查NPC是否已有绑定商店
        if (hasShop(npcId)) {
            return null;
        }

        // 直接创建商店（NPC商店由服务器拥有）
        Shop shop = Shop.create(
            UUID.randomUUID(),
            ShopOwnerType.SERVER,
            name,
            ShopType.NPC
        );

        // 设置商店属性
        shop.setDescription(description);
        shop.setGlobalPublic(true);
        shop.setInfiniteStock(true);
        shop.setNpcId(npcId);

        // 保存到数据库并添加到缓存
        storage.saveShop(shop);
        shopService.updateShop(shop); // 添加到缓存

        // 添加到本地缓存
        npcToShop.put(npcId, shop.shopId());
        shopToNpc.put(shop.shopId(), npcId);

        return shop;
    }

    @Override
    public Optional<Shop> getShopTemplate(String templateId) {
        return Optional.ofNullable(templates.get(templateId.toLowerCase()));
    }

    @Override
    public List<Shop> getAllTemplates() {
        return new ArrayList<>(templates.values());
    }

    @Override
    public boolean deleteTemplate(String templateId) {
        Shop removed = templates.remove(templateId.toLowerCase());
        if (removed != null) {
            storage.deleteTemplate(templateId);
            return true;
        }
        return false;
    }

    @Override
    public List<ShopCategory> getCategories() {
        return Collections.unmodifiableList(categories);
    }

    @Override
    public List<Shop> getShopsByCategory(ShopCategory category) {
        return shopService.getPublicShops().stream()
            .filter(shop -> hasCategory(shop, category))
            .collect(Collectors.toList());
    }

    @Override
    public void openShopGui(Player player, UUID shopId) {
        Optional<Shop> shopOpt = shopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                messages.format("shop.not-found"),
                net.kyori.adventure.text.format.NamedTextColor.RED
            ));
            return;
        }

        // 触发打开GUI事件
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getServer().getPluginManager().callEvent(
                new dev.starcore.starcore.module.shop.event.ShopOpenEvent(player, shopOpt.get())
            );
        });
    }

    @Override
    public void openNpcShopGui(Player player, int npcId) {
        Optional<Shop> shopOpt = getShopByNpc(npcId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                messages.format("shop.npc.no-shop"),
                net.kyori.adventure.text.format.NamedTextColor.RED
            ));
            return;
        }

        // 打开商店GUI（由GUI监听器处理）
        Bukkit.getScheduler().runTask(plugin, () -> {
            // 触发打开GUI事件
            plugin.getServer().getPluginManager().callEvent(
                new dev.starcore.starcore.module.shop.event.ShopOpenEvent(player, shopOpt.get())
            );
        });
    }

    @Override
    public void onNpcClick(Player player, int npcId) {
        if (!hasShop(npcId)) {
            return;
        }
        openNpcShopGui(player, npcId);
    }

    @Override
    public boolean canTrade(Player player, UUID shopId) {
        TradeLimit limit = getPlayerTradeLimit(shopId, player.getUniqueId());
        if (limit.noLimit()) {
            return true;
        }

        PlayerTradeCount record = getOrCreateCount(shopId, player.getUniqueId());

        // 检查各种限制
        if (limit.exceedsDailyLimit(record.dailyCount())) {
            return false;
        }
        if (limit.exceedsWeeklyLimit(record.weeklyCount())) {
            return false;
        }
        if (limit.exceedsMonthlyLimit(record.monthlyCount())) {
            return false;
        }

        return true;
    }

    @Override
    public boolean setPlayerTradeLimit(UUID shopId, UUID playerId, TradeLimit limit) {
        PlayerTradeCount record = getOrCreateCount(shopId, playerId);
        record.limit = limit;
        saveTradeCount(shopId, playerId, record);
        return true;
    }

    @Override
    public TradeLimit getPlayerTradeLimit(UUID shopId, UUID playerId) {
        PlayerTradeCount record = getOrCreateCount(shopId, playerId);
        return record.limit;
    }

    @Override
    public int getPlayerTradeCount(UUID shopId, UUID playerId) {
        PlayerTradeCount record = getOrCreateCount(shopId, playerId);
        return record.dailyCount();
    }

    @Override
    public void recordTrade(UUID shopId, UUID playerId, int quantity) {
        PlayerTradeCount record = getOrCreateCount(shopId, playerId);
        record.addTrades(quantity);
        saveTradeCount(shopId, playerId, record);
    }

    @Override
    public ShopItem addItem(UUID shopId, Material material, BigDecimal price, int stock) {
        return addItem(shopId, material, price, BigDecimal.ZERO, stock);
    }

    @Override
    public ShopItem addItem(UUID shopId, Material material, BigDecimal buyPrice, BigDecimal sellPrice, int stock) {
        Optional<Shop> shopOpt = shopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            plugin.getLogger().warning("Cannot add item to shop " + shopId + ": shop not found");
            return null;
        }

        Shop shop = shopOpt.get();

        // 如果没有设置出售价，默认是购买价的50%
        BigDecimal effectiveSellPrice = sellPrice.compareTo(BigDecimal.ZERO) > 0
            ? sellPrice
            : buyPrice.multiply(new BigDecimal("0.5"));

        // 计算最大库存
        int maxStockValue = shop.infiniteStock() ? Integer.MAX_VALUE : stock * 10;

        // 创建物品
        ShopItem item = new ShopItem(
            UUID.randomUUID(),
            material,
            1,
            buyPrice,
            effectiveSellPrice,
            shop.infiniteStock() ? Integer.MAX_VALUE : stock,
            maxStockValue
        );

        if (shop.infiniteStock()) {
            item.setInfiniteStock(true);
        }

        // 添加物品到商店
        boolean added = shopService.addItemToShop(shopId, item, shop.ownerId());
        if (added) {
            plugin.getLogger().info("Added item " + material.name() + " to shop " + shopId);
            return item;
        } else {
            plugin.getLogger().warning("Failed to add item " + material.name() + " to shop " + shopId);
            return null;
        }
    }

    @Override
    public List<ShopItem> getShopItems(UUID shopId) {
        Optional<Shop> shopOpt = shopService.getShop(shopId);
        return shopOpt.map(Shop::items).orElse(Collections.emptyList());
    }

    @Override
    public boolean updateItemPrice(UUID shopId, UUID itemId, BigDecimal buyPrice, BigDecimal sellPrice) {
        Optional<Shop> shopOpt = shopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            return false;
        }

        Shop shop = shopOpt.get();
        Optional<ShopItem> itemOpt = shop.getItem(itemId);
        if (itemOpt.isEmpty()) {
            return false;
        }

        ShopItem item = itemOpt.get();
        item.setBuyPrice(buyPrice);
        item.setSellPrice(sellPrice);

        // 更新到商店
        boolean updated = shopService.updateShopItem(shopId, item, shop.ownerId());
        if (updated) {
            plugin.getLogger().info("Updated item " + itemId + " price in shop " + shopId);
        }
        return updated;
    }

    @Override
    public Shop setInfiniteStock(UUID shopId, boolean infinite) {
        Optional<Shop> shopOpt = shopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            return null;
        }

        Shop shop = shopOpt.get();
        shop.setInfiniteStock(infinite);

        // 更新到商店
        boolean updated = shopService.updateShop(shop);
        if (updated) {
            plugin.getLogger().info("Set shop " + shopId + " infiniteStock to " + infinite);
            return shop;
        }
        return null;
    }

    @Override
    public BigDecimal getShopRevenue(UUID shopId) {
        return shopRevenue.getOrDefault(shopId, BigDecimal.ZERO);
    }

    @Override
    public int getShopTransactionCount(UUID shopId) {
        return shopTransactionCount.getOrDefault(shopId, 0);
    }

    @Override
    public boolean removeItem(UUID shopId, UUID itemId) {
        Optional<Shop> shopOpt = shopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            return false;
        }

        Shop shop = shopOpt.get();
        Optional<ShopItem> itemOpt = shop.getItem(itemId);
        if (itemOpt.isEmpty()) {
            return false;
        }

        // 使用服务器所有者ID进行权限检查
        boolean removed = shopService.removeItemFromShop(shopId, itemId, shop.ownerId());
        if (removed) {
            plugin.getLogger().info("Removed item " + itemId + " from shop " + shopId);
        }
        return removed;
    }

    // ==================== 交易 ====================

    @Override
    public TransactionResult buyItem(Player player, UUID shopId, UUID itemId, int amount) {
        // E-110: 修复交易绕过漏洞 - 拒绝非正数数量
        if (amount <= 0) {
            return TransactionResult.failure("购买数量必须为正数");
        }

        Optional<Shop> shopOpt = shopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            return TransactionResult.failure("商店不存在");
        }

        Shop shop = shopOpt.get();

        // 检查商店是否允许购买
        if (!shop.buyEnabled()) {
            return TransactionResult.failure("此商店不允许购买物品");
        }

        Optional<ShopItem> itemOpt = shop.getItem(itemId);
        if (itemOpt.isEmpty()) {
            return TransactionResult.failure("物品不存在");
        }

        ShopItem item = itemOpt.get();

        // 检查库存
        if (!shop.infiniteStock() && !item.infiniteStock() && !item.hasStock(amount)) {
            int available = item.stock();
            if (available <= 0) {
                return TransactionResult.insufficientStock(0, amount);
            }
            amount = available;
        }

        // 计算总价（防溢出）
        BigDecimal totalPrice = item.calculateBuyPrice(amount);

        // 交易状态追踪
        boolean withdrawn = false;
        int purchasedQty = 0;

        try {
            // 扣除玩家金币（必须在给物品之前）
            if (economyService != null) {
                if (!economyService.withdraw(player.getUniqueId(), totalPrice)) {
                    return TransactionResult.failure("金币不足，需要 " + totalPrice);
                }
                withdrawn = true;
            }

            // 触发交易事件
            dev.starcore.starcore.module.shop.event.ShopTransactionEvent event =
                new dev.starcore.starcore.module.shop.event.ShopTransactionEvent(
                    player, shop, item,
                    dev.starcore.starcore.module.shop.model.TransactionType.BUY,
                    amount, totalPrice
                );
            plugin.getServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                // 回滚扣款
                if (economyService != null) {
                    economyService.deposit(player.getUniqueId(), totalPrice);
                }
                return TransactionResult.failure("交易被取消");
            }

            // 减少库存（非无限库存时）
            if (!item.infiniteStock() && !shop.infiniteStock()) {
                purchasedQty = item.purchase(amount);
            } else {
                purchasedQty = amount;
            }

            // 给予玩家物品（检查背包空间）
            ItemStack itemStack = new ItemStack(item.material(), purchasedQty * item.amount());
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(itemStack);
            if (!overflow.isEmpty()) {
                // 背包满时物品在地上掉落
                for (ItemStack overflowItem : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
                }
            }

            // 创建交易记录
            ShopTransaction transaction = ShopTransaction.create(
                shopId, player.getUniqueId(), itemId,
                dev.starcore.starcore.module.shop.model.TransactionType.BUY, purchasedQty, totalPrice
            );

            // 记录交易（更新限制计数）
            recordTrade(shopId, player.getUniqueId(), purchasedQty);

            // 保存交易记录到数据库
            storage.saveTransaction(transaction);

            // E-110: 记录经济审计日志
            auditLogService.logEconomyTransaction(
                player.getUniqueId(), null,
                totalPrice.toPlainString(), "SHOP_BUY"
            );

            return TransactionResult.success(transaction, purchasedQty);

        } catch (Exception e) {
            // 通用异常处理：回滚扣款
            if (withdrawn) {
                economyService.deposit(player.getUniqueId(), totalPrice);
            }
            if (purchasedQty > 0 && !item.infiniteStock() && !shop.infiniteStock()) {
                item.sell(-purchasedQty);
            }
            plugin.getLogger().warning("NPC Shop buy failed: " + e.getMessage());
            return TransactionResult.failure("交易处理失败: " + e.getMessage());
        }
    }

    @Override
    public TransactionResult sellItem(Player player, UUID shopId, UUID itemId, int amount) {
        // E-110: 修复交易绕过漏洞 - 拒绝非正数数量
        if (amount <= 0) {
            return TransactionResult.failure("出售数量必须为正数");
        }

        Optional<Shop> shopOpt = shopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            return TransactionResult.failure("商店不存在");
        }

        Shop shop = shopOpt.get();

        // 检查商店是否允许出售
        if (!shop.sellEnabled()) {
            return TransactionResult.failure("此商店不允许出售物品");
        }

        Optional<ShopItem> itemOpt = shop.getItem(itemId);
        if (itemOpt.isEmpty()) {
            return TransactionResult.failure("物品不存在");
        }

        ShopItem item = itemOpt.get();

        // 检查玩家物品
        int playerAmount = countPlayerItems(player, item.material());
        // 防溢出校验
        int requiredAmount;
        try {
            requiredAmount = Math.multiplyExact(amount, item.amount());
        } catch (ArithmeticException e) {
            return TransactionResult.failure("购买数量过大");
        }
        if (playerAmount < requiredAmount) {
            return TransactionResult.failure("你没有足够的物品出售，需要 " + requiredAmount + " 个，当前持有 " + playerAmount);
        }

        // 计算总价
        BigDecimal totalPrice = item.calculateSellPrice(amount);

        // 交易状态追踪
        boolean itemRemoved = false;
        int soldQty = 0;

        try {
            // 触发交易事件
            dev.starcore.starcore.module.shop.event.ShopTransactionEvent event =
                new dev.starcore.starcore.module.shop.event.ShopTransactionEvent(
                    player, shop, item,
                    dev.starcore.starcore.module.shop.model.TransactionType.SELL,
                    amount, totalPrice
                );
            plugin.getServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return TransactionResult.failure("交易被取消");
            }

            // 移除玩家物品
            removePlayerItems(player, item.material(), requiredAmount);
            itemRemoved = true;

            // 增加库存（非无限库存时）
            if (!item.infiniteStock()) {
                soldQty = item.sell(amount);
            } else {
                soldQty = amount;
            }

            // 给玩家金币
            if (economyService != null) {
                economyService.deposit(player.getUniqueId(), totalPrice);
            }

            // 记录交易（更新限制计数）
            recordTrade(shopId, player.getUniqueId(), soldQty);

            // 创建交易记录用于返回
            ShopTransaction transaction = ShopTransaction.create(
                shopId, player.getUniqueId(), itemId,
                dev.starcore.starcore.module.shop.model.TransactionType.SELL, soldQty, totalPrice
            );

            // 保存交易记录到数据库
            storage.saveTransaction(transaction);

            // E-110: 记录经济审计日志
            auditLogService.logEconomyTransaction(
                null, player.getUniqueId(),
                totalPrice.toPlainString(), "SHOP_SELL"
            );

            return TransactionResult.success(transaction, soldQty);

        } catch (Exception e) {
            // 通用异常处理：回滚
            if (itemRemoved) {
                player.getInventory().addItem(new ItemStack(item.material(), requiredAmount));
            }
            if (soldQty > 0 && !item.infiniteStock()) {
                item.sell(-soldQty);
            }
            plugin.getLogger().warning("NPC Shop sell failed: " + e.getMessage());
            return TransactionResult.failure("交易处理失败: " + e.getMessage());
        }
    }

    @Override
    public List<ShopTransaction> getTradeHistory(UUID shopId) {
        // 从数据库加载指定商店的交易历史
        return storage.loadTransactionsByShop(shopId);
    }

    @Override
    public List<ShopTransaction> getPlayerTradeHistory(UUID playerId, int limit) {
        // 从数据库加载指定玩家的交易历史
        return storage.loadTransactionsByPlayer(playerId, limit);
    }

    private int countPlayerItems(Player player, Material material) {
        return player.getInventory().all(material).values().stream()
            .mapToInt(ItemStack::getAmount)
            .sum();
    }

    private void removePlayerItems(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                int removeAmount = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - removeAmount);
                remaining -= removeAmount;
                if (item.getAmount() <= 0) {
                    player.getInventory().setItem(i, null);
                }
            }
        }
    }

    // ==================== 内部方法 ====================

    private boolean isCitizensAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("Citizens") != null;
    }

    private void loadNpcBindings() {
        Map<Integer, UUID> bindings = storage.loadNpcBindings();
        npcToShop.putAll(bindings);
        for (Map.Entry<Integer, UUID> entry : bindings.entrySet()) {
            shopToNpc.put(entry.getValue(), entry.getKey());
        }
    }

    private void initializeDefaultTemplates() {
        // 铁匠铺
        createNpcShopTemplate("铁匠铺", "武器和防具", ShopCategory.WEAPONS);

        // 杂货店
        createNpcShopTemplate("杂货店", "日常用品", ShopCategory.GENERAL);

        // 附魔屋
        createNpcShopTemplate("附魔屋", "附魔材料", ShopCategory.ENCHANTMENTS);

        // 食物店
        createNpcShopTemplate("食物店", "食品和农产品", ShopCategory.FOOD);
    }

    private void loadTradeCounts() {
        // 交易计数从 storage 加载
        // 目前简化实现，不需要从 storage 加载
    }

    private PlayerTradeCount getOrCreateCount(UUID shopId, UUID playerId) {
        return tradeCounts
            .computeIfAbsent(shopId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(playerId, k -> new PlayerTradeCount());
    }

    private void saveTradeCount(UUID shopId, UUID playerId, PlayerTradeCount record) {
        // 简化实现：交易计数不需要持久化
    }

    private boolean hasCategory(Shop shop, ShopCategory category) {
        // 检查物品是否属于该类别
        return shop.items().stream()
            .anyMatch(item -> getCategoryForMaterial(item.material()) == category);
    }

    private ShopCategory getCategoryForMaterial(org.bukkit.Material material) {
        String name = material.name();
        if (name.contains("SWORD") || name.contains("AXE") || name.contains("BOW") ||
            name.contains("HELMET") || name.contains("CHESTPLATE") || name.contains("LEGGINGS") ||
            name.contains("BOOTS") || name.contains("SHIELD")) {
            return ShopCategory.WEAPONS;
        }
        if (name.contains("PICKAXE") || name.contains("AXE") || name.contains("SHOVEL") ||
            name.contains("HOE") || name.contains("FISHING_ROD") || name.contains("SHEARS")) {
            return ShopCategory.TOOLS;
        }
        if (name.contains("FOOD") || name.contains("APPLE") || name.contains("BREAD") ||
            name.contains("CARROT") || name.contains("POTATO") || name.contains("COOKED") ||
            name.contains("RAW") || name.contains("MILK") || name.contains("BUCKET")) {
            return ShopCategory.FOOD;
        }
        if (name.contains("POTION") || name.contains("SPLASH") || name.contains("LINGERING") ||
            name.contains("CAULDRON") || name.contains("WATER_BUCKET")) {
            return ShopCategory.POTIONS;
        }
        if (name.contains("ENCHANTED") || name.contains("BOOK") || name.contains("LAPIS")) {
            return ShopCategory.ENCHANTMENTS;
        }
        if (name.contains("SPAWNER")) {
            return ShopCategory.SPAWNERS;
        }
        if (name.contains("DIAMOND") || name.contains("GOLD") || name.contains("IRON") ||
            name.contains("COAL") || name.contains("COPPER") || name.contains("EMERALD") ||
            name.contains("NETHERITE") || name.contains("RAW_")) {
            return ShopCategory.RESOURCE;
        }
        if (name.contains("NETHER_STAR") || name.contains("ELYTRA") || name.contains("TOTEM") ||
            name.contains("SHULKER") || name.contains("DRAGON_")) {
            return ShopCategory.RARE;
        }
        return ShopCategory.BLOCKS;
    }

    // ==================== 数据重载 ====================

    @Override
    public void reload() {
        // 清空缓存
        npcToShop.clear();
        shopToNpc.clear();
        tradeCounts.clear();
        templates.clear();

        // 重新加载NPC绑定数据
        loadNpcBindings();

        // 重新加载交易记录
        loadTradeCounts();

        // 重新加载模板
        initializeDefaultTemplates();

        // 重新加载商店服务数据
        shopService.reload();

        plugin.getLogger().info("NpcShopService reloaded successfully");
    }

    /**
     * 玩家交易计数
     */
    private static class PlayerTradeCount {
        int dailyCount = 0;
        int weeklyCount = 0;
        int monthlyCount = 0;
        Instant lastResetDaily = Instant.now();
        Instant lastResetWeekly = Instant.now();
        Instant lastResetMonthly = Instant.now();
        TradeLimit limit = TradeLimit.defaultLimit();

        int dailyCount() { return dailyCount; }
        int weeklyCount() { return weeklyCount; }
        int monthlyCount() { return monthlyCount; }

        void addTrades(int count) {
            dailyCount += count;
            weeklyCount += count;
            monthlyCount += count;
            checkAndReset();
        }

        void checkAndReset() {
            Instant now = Instant.now();

            // 重置每日计数
            if (lastResetDaily.plusSeconds(86400).isBefore(now)) {
                dailyCount = 0;
                lastResetDaily = now;
            }

            // 重置每周计数
            if (lastResetWeekly.plusSeconds(604800).isBefore(now)) {
                weeklyCount = 0;
                lastResetWeekly = now;
            }

            // 重置每月计数
            if (lastResetMonthly.plusSeconds(2592000).isBefore(now)) {
                monthlyCount = 0;
                lastResetMonthly = now;
            }
        }
    }
}
