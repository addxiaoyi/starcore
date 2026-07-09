package dev.starcore.starcore.module.shop.service;
import java.util.Optional;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.shop.model.*;
import dev.starcore.starcore.module.shop.storage.ShopDatabaseStorage;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 商店服务实现
 */
public class ShopServiceImpl implements ShopService {
    private final Plugin plugin;
    private final ShopDatabaseStorage storage;
    private final MessageService messages;
    private final NationService nationService;
    private final EconomyService economyService;
    private final TreasuryService treasuryService;

    // 内存缓存
    private final Map<UUID, Shop> shops = new ConcurrentHashMap<>();
    private final Map<UUID, List<Shop>> playerShops = new ConcurrentHashMap<>();
    private final Map<UUID, List<Shop>> nationShops = new ConcurrentHashMap<>();
    private final Map<UUID, List<ShopTransaction>> transactions = new ConcurrentHashMap<>();
    private final Map<UUID, List<ShopTransaction>> playerTransactions = new ConcurrentHashMap<>();

    public ShopServiceImpl(
        Plugin plugin,
        ShopDatabaseStorage storage,
        MessageService messages,
        NationService nationService,
        EconomyService economyService,
        TreasuryService treasuryService
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.messages = messages;
        this.nationService = nationService;
        // economyService 不能为空，必须提供有效的经济服务
        if (economyService == null) {
            throw new IllegalStateException("EconomyService cannot be null for ShopServiceImpl");
        }
        this.economyService = economyService;
        // treasuryService 可能为空
        this.treasuryService = treasuryService;
    }

    @Override
    public Shop createPlayerShop(Player owner, String name) {
        Shop shop = Shop.create(
            owner.getUniqueId(),
            ShopOwnerType.PLAYER,
            name,
            ShopType.PLAYER
        );
        shop.setLocation(owner.getLocation());

        shops.put(shop.shopId(), shop);
        playerShops.computeIfAbsent(owner.getUniqueId(), k -> new ArrayList<>()).add(shop);

        storage.saveShop(shop);
        return shop;
    }

    @Override
    public Shop createNationShop(UUID nationId, String name) {
        Shop shop = Shop.create(
            nationId,
            ShopOwnerType.NATION,
            name,
            ShopType.NATION
        );
        shop.setNationPublic(true);

        shops.put(shop.shopId(), shop);
        nationShops.computeIfAbsent(nationId, k -> new ArrayList<>()).add(shop);

        storage.saveShop(shop);
        return shop;
    }

    @Override
    public Optional<Shop> getShop(UUID shopId) {
        return Optional.ofNullable(shops.get(shopId));
    }

    @Override
    public List<Shop> getPlayerShops(UUID playerId) {
        return playerShops.getOrDefault(playerId, Collections.emptyList()).stream()
            .filter(Shop::isOpen)
            .collect(Collectors.toList());
    }

    @Override
    public List<Shop> getNationShops(UUID nationId) {
        return nationShops.getOrDefault(nationId, Collections.emptyList()).stream()
            .filter(Shop::isOpen)
            .collect(Collectors.toList());
    }

    @Override
    public List<Shop> getPublicShops() {
        return shops.values().stream()
            .filter(Shop::isOpen)
            .filter(shop -> shop.globalPublic() || shop.nationPublic() || shop.shopType().isPublic())
            .collect(Collectors.toList());
    }

    @Override
    public List<Shop> getAllShops() {
        return new ArrayList<>(shops.values());
    }

    @Override
    public boolean deleteShop(UUID shopId, UUID playerId) {
        Shop shop = shops.get(shopId);
        if (shop == null) {
            return false;
        }

        // 检查权限
        if (!shop.ownerId().equals(playerId)) {
            return false;
        }

        // 移除缓存
        shops.remove(shopId);
        playerShops.getOrDefault(shop.ownerId(), Collections.emptyList()).removeIf(s -> s.shopId().equals(shopId));
        if (shop.ownerType() == ShopOwnerType.NATION) {
            nationShops.getOrDefault(shop.ownerId(), Collections.emptyList()).removeIf(s -> s.shopId().equals(shopId));
        }

        storage.deleteShop(shopId);
        return true;
    }

    @Override
    public boolean updateShop(Shop shop) {
        // 如果商店不存在于缓存，先添加到缓存
        if (!shops.containsKey(shop.shopId())) {
            shops.put(shop.shopId(), shop);
        }

        shops.put(shop.shopId(), shop);
        storage.saveShop(shop);
        return true;
    }

    @Override
    public void saveAll() {
        for (Shop shop : shops.values()) {
            storage.saveShop(shop);
        }
    }

    @Override
    public boolean addItemToShop(UUID shopId, ShopItem item, UUID playerId) {
        Shop shop = shops.get(shopId);
        if (shop == null) {
            plugin.getLogger().warning("Cannot add item: shop not found: " + shopId);
            return false;
        }
        if (!shop.canAccess(playerId)) {
            plugin.getLogger().warning("Access denied: player " + playerId + " cannot access shop " + shopId);
            return false;
        }

        // 限制商店物品数量，防止内存膨胀
        if (shop.items().size() >= 100) {
            plugin.getLogger().warning("Shop " + shopId + " has reached maximum item limit (100)");
            return false;
        }

        shop.addItem(item);
        storage.saveShop(shop);
        return true;
    }

    @Override
    public boolean removeItemFromShop(UUID shopId, UUID itemId, UUID playerId) {
        Shop shop = shops.get(shopId);
        if (shop == null || !shop.canAccess(playerId)) {
            return false;
        }

        boolean removed = shop.removeItem(itemId);
        if (removed) {
            storage.saveShop(shop);
        }
        return removed;
    }

    @Override
    public boolean updateShopItem(UUID shopId, ShopItem item, UUID playerId) {
        Shop shop = shops.get(shopId);
        if (shop == null || !shop.canAccess(playerId)) {
            return false;
        }

        boolean updated = shop.updateItem(item);
        if (updated) {
            storage.saveShop(shop);
        }
        return updated;
    }

    @Override
    public Optional<ShopItem> getShopItem(UUID shopId, UUID itemId) {
        Shop shop = shops.get(shopId);
        if (shop == null) {
            return Optional.empty();
        }
        return shop.getItem(itemId);
    }

    @Override
    public TransactionResult buyItem(UUID shopId, UUID itemId, int quantity, Player player) {
        Shop shop = shops.get(shopId);
        if (shop == null) {
            return TransactionResult.failure("商店不存在");
        }

        if (!shop.isOpen()) {
            return TransactionResult.shopClosed();
        }

        if (!shop.buyEnabled()) {
            return TransactionResult.failure("此商店不允许购买");
        }

        if (!shop.canAccess(player.getUniqueId())) {
            return TransactionResult.failure("你没有权限访问此商店");
        }

        Optional<ShopItem> itemOpt = shop.getItem(itemId);
        if (itemOpt.isEmpty()) {
            return TransactionResult.itemNotFound();
        }

        ShopItem item = itemOpt.get();
        if (!item.hasStock(quantity)) {
            return TransactionResult.insufficientStock(item.stock(), quantity);
        }

        BigDecimal totalPrice = item.calculateBuyPrice(quantity);
        BigDecimal playerBalance = economyService.getBalance(player.getUniqueId());

        if (playerBalance.compareTo(totalPrice) < 0) {
            return TransactionResult.insufficientFunds(totalPrice, playerBalance);
        }

        // 交易状态追踪
        boolean withdrawn = false;
        int purchasedQty = 0;
        boolean itemGiven = false;
        ItemStack givenItems = null;
        ItemStack droppedItems = null;
        int stackAmount = quantity * item.amount();

        try {
            // 扣款（检查返回值）
            if (!economyService.withdraw(player.getUniqueId(), totalPrice)) {
                return TransactionResult.failure("扣款失败");
            }
            withdrawn = true;

            // 减少库存
            purchasedQty = item.purchase(quantity);
            if (purchasedQty <= 0) {
                // 库存不足，回滚
                economyService.deposit(player.getUniqueId(), totalPrice);
                return TransactionResult.insufficientStock(item.stock(), quantity);
            }

            // 创建交易记录
            ShopTransaction transaction = ShopTransaction.create(
                shopId, player.getUniqueId(), itemId,
                TransactionType.BUY, purchasedQty, item.buyPrice()
            );

            // 记录交易
            recordTransaction(shopId, transaction);
            recordPlayerTransaction(player.getUniqueId(), transaction);

            // 给玩家物品（检查数量溢出，ItemStack 数量上限 64）
            if (stackAmount > 64) {
                int fullStacks = stackAmount / 64;
                int remainder = stackAmount % 64;
                for (int i = 0; i < fullStacks; i++) {
                    player.getInventory().addItem(new ItemStack(item.material(), 64));
                }
                if (remainder > 0) {
                    player.getInventory().addItem(new ItemStack(item.material(), remainder));
                }
            } else {
                givenItems = new ItemStack(item.material(), stackAmount);
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(givenItems);
                if (!overflow.isEmpty()) {
                    // 背包满时在地上掉落
                    droppedItems = new ItemStack(item.material(), stackAmount);
                    player.getWorld().dropItemNaturally(player.getLocation(), droppedItems);
                }
            }
            itemGiven = true;

            // 更新商店（所有者获得金币）
            boolean depositSuccess = true;
            try {
                if (shop.ownerType() == ShopOwnerType.PLAYER) {
                    depositSuccess = economyService.deposit(shop.ownerId(), totalPrice);
                } else if (shop.ownerType() == ShopOwnerType.NATION && treasuryService != null) {
                    NationId nationId = new NationId(shop.ownerId());
                    treasuryService.deposit(nationId, totalPrice, "商店 " + shop.name() + " 销售收入");
                    plugin.getLogger().info("Deposited " + totalPrice + " to nation treasury: " + nationId);
                }
            } catch (Exception e) {
                depositSuccess = false;
                plugin.getLogger().warning("Failed to deposit to shop owner: " + e.getMessage());
            }

            // 若所有者收款失败，回滚玩家扣款
            if (!depositSuccess && shop.ownerType() == ShopOwnerType.PLAYER) {
                economyService.deposit(player.getUniqueId(), totalPrice);
                withdrawn = false; // 标记已回滚
                return TransactionResult.failure("商店所有者收款失败，已退款");
            }

            storage.saveShop(shop);
            storage.saveTransaction(transaction);

            return TransactionResult.success(transaction, purchasedQty);

        } catch (Exception e) {
            // 通用异常处理：回滚所有操作
            plugin.getLogger().warning("Purchase transaction failed: " + e.getMessage());
            if (withdrawn) {
                economyService.deposit(player.getUniqueId(), totalPrice);
            }
            if (purchasedQty > 0) {
                item.sell(-purchasedQty); // 回滚库存
            }
            return TransactionResult.failure("交易处理失败，已退款: " + e.getMessage());
        }
    }

    @Override
    public TransactionResult sellItem(UUID shopId, UUID itemId, int quantity, ItemStack itemStack, Player player) {
        Shop shop = shops.get(shopId);
        if (shop == null) {
            return TransactionResult.failure("商店不存在");
        }

        if (!shop.isOpen()) {
            return TransactionResult.shopClosed();
        }

        if (!shop.sellEnabled()) {
            return TransactionResult.failure("此商店不允许出售");
        }

        Optional<ShopItem> itemOpt = shop.getItem(itemId);
        if (itemOpt.isEmpty()) {
            return TransactionResult.itemNotFound();
        }

        ShopItem item = itemOpt.get();

        // 检查玩家物品是否匹配
        if (itemStack.getType() != item.material()) {
            return TransactionResult.failure("物品类型不匹配");
        }

        int totalQuantity = itemStack.getAmount();
        if (totalQuantity < quantity) {
            return TransactionResult.failure("你没有足够的物品");
        }

        // 计算总价
        BigDecimal totalPrice = item.calculateSellPrice(quantity);

        // 交易状态追踪
        boolean ownerWithdrawn = false;
        boolean itemRemoved = false;
        boolean depositDone = false;
        int addedQty = 0;

        try {
            // 所有者支付前先检查余额（玩家商店需要扣所有者钱）
            if (shop.ownerType() == ShopOwnerType.PLAYER) {
                BigDecimal ownerBalance = economyService.getBalance(shop.ownerId());
                if (ownerBalance.compareTo(totalPrice) < 0) {
                    return TransactionResult.failure("商店所有者余额不足");
                }
                if (!economyService.withdraw(shop.ownerId(), totalPrice)) {
                    return TransactionResult.failure("商店所有者扣款失败");
                }
                ownerWithdrawn = true;
            }

            // 增加库存
            addedQty = item.sell(quantity);
            if (addedQty <= 0) {
                // 库存操作失败，回滚
                if (ownerWithdrawn) {
                    economyService.deposit(shop.ownerId(), totalPrice);
                }
                return TransactionResult.failure("库存增加失败");
            }

            // 创建交易记录
            ShopTransaction transaction = ShopTransaction.create(
                shopId, player.getUniqueId(), itemId,
                TransactionType.SELL, addedQty, item.sellPrice()
            );

            // 记录交易
            recordTransaction(shopId, transaction);
            recordPlayerTransaction(player.getUniqueId(), transaction);

            // 从玩家背包安全移除物品
            int removeAmount = Math.min(quantity, itemStack.getAmount());
            ItemStack toRemove = new ItemStack(item.material(), removeAmount);
            Map<Integer, ItemStack> notRemoved = player.getInventory().removeItem(toRemove);
            if (!notRemoved.isEmpty()) {
                // 物品移除失败，回滚
                if (ownerWithdrawn) {
                    economyService.deposit(shop.ownerId(), totalPrice);
                }
                item.sell(-addedQty); // 回滚库存
                return TransactionResult.failure("物品移除失败");
            }
            itemRemoved = true;

            // 给玩家金币
            boolean depositSuccess = economyService.deposit(player.getUniqueId(), totalPrice);
            if (!depositSuccess) {
                // 退款物品给玩家
                player.getInventory().addItem(toRemove);
                item.sell(-addedQty);
                if (ownerWithdrawn) {
                    economyService.deposit(shop.ownerId(), totalPrice);
                }
                return TransactionResult.failure("玩家收款失败");
            }
            depositDone = true;

            storage.saveShop(shop);
            storage.saveTransaction(transaction);

            return TransactionResult.success(transaction, addedQty);

        } catch (Exception e) {
            // 通用异常处理：回滚所有操作
            plugin.getLogger().warning("Sell transaction failed: " + e.getMessage());
            if (itemRemoved) {
                player.getInventory().addItem(new ItemStack(item.material(), Math.min(quantity, itemStack.getAmount())));
            }
            if (addedQty > 0) {
                item.sell(-addedQty);
            }
            if (ownerWithdrawn) {
                economyService.deposit(shop.ownerId(), totalPrice);
            }
            return TransactionResult.failure("交易处理失败: " + e.getMessage());
        }
    }

    @Override
    public List<ShopTransaction> getShopTransactions(UUID shopId, int limit) {
        return transactions.getOrDefault(shopId, Collections.emptyList()).stream()
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public List<ShopTransaction> getPlayerTransactions(UUID playerId, int limit) {
        return playerTransactions.getOrDefault(playerId, Collections.emptyList()).stream()
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public List<Shop> searchShops(String searchTerm) {
        String term = searchTerm.toLowerCase();
        return shops.values().stream()
            .filter(Shop::isOpen)
            .filter(shop -> {
                String name = shop.name().toLowerCase();
                String desc = shop.description() != null ? shop.description().toLowerCase() : "";
                return name.contains(term) || desc.contains(term);
            })
            .collect(Collectors.toList());
    }

    @Override
    public List<Shop> getShopsByType(ShopType type) {
        return shops.values().stream()
            .filter(Shop::isOpen)
            .filter(shop -> shop.shopType() == type)
            .collect(Collectors.toList());
    }

    @Override
    public List<Shop> getShopsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return shops.values().stream()
            .filter(Shop::isOpen)
            .filter(shop -> shop.items().stream()
                .anyMatch(item -> {
                    BigDecimal price = item.buyPrice();
                    return price.compareTo(minPrice) >= 0 && price.compareTo(maxPrice) <= 0;
                }))
            .collect(Collectors.toList());
    }

    @Override
    public BigDecimal getShopRevenue(UUID shopId) {
        // Revenue = sum of BUY transactions (player's purchase = shop's income)
        // This represents the shop owner's incoming revenue from sales.
        return transactions.getOrDefault(shopId, Collections.emptyList()).stream()
            .filter(t -> t.type() == TransactionType.BUY)
            .map(ShopTransaction::totalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public BigDecimal getPlayerTotalSpent(UUID playerId) {
        return playerTransactions.getOrDefault(playerId, Collections.emptyList()).stream()
            .filter(t -> t.type() == TransactionType.BUY)
            .map(ShopTransaction::totalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public BigDecimal getPlayerTotalEarned(UUID playerId) {
        return playerTransactions.getOrDefault(playerId, Collections.emptyList()).stream()
            .filter(t -> t.type() == TransactionType.SELL)
            .map(ShopTransaction::totalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public boolean isShopAdmin(UUID playerId, UUID shopId) {
        Shop shop = shops.get(shopId);
        if (shop == null) {
            return false;
        }

        // 拥有者总是管理员
        if (shop.ownerId().equals(playerId)) {
            return true;
        }

        // 检查是否在允许列表中
        return shop.allowedPlayers().contains(playerId);
    }

    @Override
    public boolean addShopAdmin(UUID shopId, UUID playerId) {
        Shop shop = shops.get(shopId);
        if (shop == null || !shop.ownerId().equals(playerId)) {
            return false;
        }

        shop.addAllowedPlayer(playerId);
        storage.saveShop(shop);
        return true;
    }

    @Override
    public boolean removeShopAdmin(UUID shopId, UUID playerId) {
        Shop shop = shops.get(shopId);
        if (shop == null || !shop.ownerId().equals(playerId)) {
            return false;
        }

        shop.removeAllowedPlayer(playerId);
        storage.saveShop(shop);
        return true;
    }

    @Override
    public void reload() {
        // 清空缓存
        shops.clear();
        playerShops.clear();
        nationShops.clear();
        transactions.clear();
        playerTransactions.clear();

        // 重新加载所有数据
        loadAll();

        plugin.getLogger().info("ShopService reloaded successfully");
    }

    // ==================== 内部方法 ====================

    /**
     * 加载所有商店数据
     */
    public void loadAll() {
        List<Shop> allShops = storage.loadAllShops();
        for (Shop shop : allShops) {
            shops.put(shop.shopId(), shop);

            // 按拥有者分类
            if (shop.ownerType() == ShopOwnerType.PLAYER) {
                playerShops.computeIfAbsent(shop.ownerId(), k -> new ArrayList<>()).add(shop);
            } else if (shop.ownerType() == ShopOwnerType.NATION) {
                nationShops.computeIfAbsent(shop.ownerId(), k -> new ArrayList<>()).add(shop);
            }
        }

        // 加载交易记录
        List<ShopTransaction> allTransactions = storage.loadAllTransactions();
        for (ShopTransaction transaction : allTransactions) {
            recordTransaction(transaction.shopId(), transaction);
            recordPlayerTransaction(transaction.playerId(), transaction);
        }
    }

    private void recordTransaction(UUID shopId, ShopTransaction transaction) {
        List<ShopTransaction> list = transactions.computeIfAbsent(shopId, k -> new ArrayList<>());
        synchronized (list) {
            list.add(0, transaction);
            // 限制每个商店的交易记录数量（同步保护 subList().clear()）
            if (list.size() > 1000) {
                list.subList(1000, list.size()).clear();
            }
        }
    }

    private void recordPlayerTransaction(UUID playerId, ShopTransaction transaction) {
        List<ShopTransaction> list = playerTransactions.computeIfAbsent(playerId, k -> new ArrayList<>());
        synchronized (list) {
            list.add(0, transaction);
            // 限制每个玩家的交易记录数量（同步保护 subList().clear()）
            if (list.size() > 500) {
                list.subList(500, list.size()).clear();
            }
        }
    }
}
