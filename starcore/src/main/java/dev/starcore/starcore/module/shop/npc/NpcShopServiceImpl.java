package dev.starcore.starcore.module.shop.npc;
import java.util.Optional;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.module.shop.npc.NpcShopStorage.NpcTradeRecord;
import dev.starcore.starcore.module.shop.npc.TransactionResult;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * NPC商店服务实现
 * 提供完整的NPC商店功能实现
 */
public class NpcShopServiceImpl implements NpcShopService {

    private final Plugin plugin;
    private final NpcShopStorage storage;
    private final EconomyService economyService;

    // 商店缓存
    private final Map<UUID, NpcShopData> shops = new ConcurrentHashMap<>();

    // NPC到商店的映射
    private final Map<Integer, UUID> npcToShop = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> shopToNpc = new ConcurrentHashMap<>();

    // 商店物品缓存
    private final Map<UUID, List<NpcShopItemData>> shopItems = new ConcurrentHashMap<>();

    // 交易记录
    private final List<NpcTradeRecord> tradeHistory = new ArrayList<>();

    // 每商店的交易统计
    private final Map<UUID, BigDecimal> shopRevenue = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> shopTransactionCount = new ConcurrentHashMap<>();

    /**
     * 内部经济服务接口（用于GUI）
     * 与 foundation.economy.EconomyService 分离以避免命名冲突
     */
    public interface ShopEconomyService {
        boolean has(Player player, BigDecimal amount);
        boolean withdraw(Player player, BigDecimal amount);
        boolean deposit(Player player, BigDecimal amount);
        BigDecimal getBalance(Player player);
    }

    /**
     * 内部经济服务适配器
     * 将 foundation.economy.EconomyService 适配为 GUI 需要的接口
     */
    public static class EconomyServiceAdapter implements ShopEconomyService {
        private final EconomyService delegate;

        public EconomyServiceAdapter(EconomyService delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean has(Player player, BigDecimal amount) {
            return delegate.has(player.getUniqueId(), amount);
        }

        @Override
        public boolean withdraw(Player player, BigDecimal amount) {
            return delegate.withdraw(player.getUniqueId(), amount);
        }

        @Override
        public boolean deposit(Player player, BigDecimal amount) {
            return delegate.deposit(player.getUniqueId(), amount);
        }

        @Override
        public BigDecimal getBalance(Player player) {
            return delegate.getBalance(player.getUniqueId());
        }
    }

    public NpcShopServiceImpl(Plugin plugin, NpcShopStorage storage, EconomyService economyService) {
        this.plugin = plugin;
        this.storage = storage;
        this.economyService = economyService;

        // 加载所有商店数据
        loadAllData();
    }

    /**
     * 加载所有数据
     */
    private void loadAllData() {
        // 加载商店
        List<NpcShopData> loadedShops = storage.loadAllShops();
        for (NpcShopData shop : loadedShops) {
            shops.put(shop.id(), shop);
            if (shop.hasNpc()) {
                npcToShop.put(shop.npcId(), shop.id());
                shopToNpc.put(shop.id(), shop.npcId());
            }
        }

        // 加载物品
        for (UUID shopId : shops.keySet()) {
            List<NpcShopItemData> items = storage.loadShopItems(shopId);
            shopItems.put(shopId, new ArrayList<>(items));
        }

        // 加载交易记录
        List<NpcTradeRecord> loadedHistory = storage.loadTradeHistory();
        tradeHistory.addAll(loadedHistory);

        // 计算统计数据
        for (NpcTradeRecord record : loadedHistory) {
            shopRevenue.merge(record.shopId(), record.totalPrice(), BigDecimal::add);
            shopTransactionCount.merge(record.shopId(), 1, Integer::sum);
        }
    }

    // ==================== 商店管理 ====================

    @Override
    public NpcShopData createNpcShop(String name, org.bukkit.Location location, Player owner) {
        return createNpcShop(name, location, owner, null);
    }

    @Override
    public NpcShopData createNpcShop(String name, org.bukkit.Location location, Player owner, String description) {
        NpcShopData shop = description != null
            ? NpcShopData.create(name, owner.getUniqueId(), owner.getName(), location, description)
            : NpcShopData.create(name, owner.getUniqueId(), owner.getName(), location);

        shops.put(shop.id(), shop);
        storage.saveShop(shop);

        // 初始化空物品列表
        shopItems.put(shop.id(), new ArrayList<>());

        return shop;
    }

    @Override
    public boolean deleteNpcShop(UUID shopId) {
        NpcShopData shop = shops.remove(shopId);
        if (shop == null) {
            return false;
        }

        // 解除NPC绑定
        if (shop.hasNpc()) {
            npcToShop.remove(shop.npcId());
            shopToNpc.remove(shopId);
        }

        // 清除物品
        shopItems.remove(shopId);

        // 保存更改
        storage.deleteShop(shopId);

        return true;
    }

    @Override
    public Optional<NpcShopData> getShop(UUID shopId) {
        return Optional.ofNullable(shops.get(shopId));
    }

    @Override
    public List<NpcShopData> getPlayerShops(UUID playerId) {
        return shops.values().stream()
            .filter(shop -> shop.ownerId().equals(playerId))
            .collect(Collectors.toList());
    }

    @Override
    public List<NpcShopData> getAllShops() {
        return new ArrayList<>(shops.values());
    }

    // ==================== NPC绑定 ====================

    /**
     * 将商店绑定到NPC（兼容性别名）
     */
    public boolean bindShopToNpc(UUID shopId, int npcId) {
        NpcShopData shop = shops.get(shopId);
        if (shop == null) {
            return false;
        }

        // 检查NPC是否已被占用
        if (npcToShop.containsKey(npcId)) {
            return false;
        }

        // 解除之前的绑定
        if (shop.hasNpc()) {
            npcToShop.remove(shop.npcId());
            shopToNpc.remove(shopId);
        }

        // 创建新绑定
        NpcShopData updatedShop = shop.withNpcId(npcId);
        shops.put(shopId, updatedShop);
        npcToShop.put(npcId, shopId);
        shopToNpc.put(shopId, npcId);

        // 保存到数据库
        storage.saveShop(updatedShop);

        return true;
    }

    /**
     * 兼容性别名
     */
    public boolean bindToNpc(UUID shopId, int npcId) {
        return bindShopToNpc(shopId, npcId);
    }

    /**
     * 解除商店与NPC的绑定（兼容性别名）
     */
    public boolean unbindShopFromNpc(int npcId) {
        UUID shopId = npcToShop.remove(npcId);
        if (shopId == null) {
            return false;
        }

        shopToNpc.remove(shopId);

        NpcShopData shop = shops.get(shopId);
        if (shop != null && shop.hasNpc()) {
            NpcShopData updatedShop = new NpcShopData(
                shop.id(), shop.name(), shop.ownerId(), shop.ownerName(),
                shop.location(), null, shop.infiniteStock(),
                shop.buyEnabled(), shop.sellEnabled(), shop.createdAt(), shop.description()
            );
            shops.put(shopId, updatedShop);
            storage.saveShop(updatedShop);
        }

        return true;
    }

    /**
     * 兼容性别名
     */
    public boolean unbindFromNpc(UUID shopId) {
        Integer npcId = shopToNpc.get(shopId);
        if (npcId == null) {
            return false;
        }
        return unbindShopFromNpc(npcId);
    }

    @Override
    public Optional<NpcShopData> getShopByNpc(int npcId) {
        UUID shopId = npcToShop.get(npcId);
        if (shopId == null) {
            return Optional.empty();
        }
        return getShop(shopId);
    }

    @Override
    public boolean hasShop(int npcId) {
        return npcToShop.containsKey(npcId);
    }

    // ==================== 物品管理 ====================

    @Override
    public NpcShopItemData addItem(UUID shopId, Material material, BigDecimal price, int stock) {
        return addItem(shopId, material, price, BigDecimal.ZERO, stock);
    }

    @Override
    public NpcShopItemData addItem(UUID shopId, Material material, BigDecimal buyPrice, BigDecimal sellPrice, int stock) {
        NpcShopData shop = shops.get(shopId);
        if (shop == null) {
            throw new IllegalArgumentException("商店不存在: " + shopId);
        }

        NpcShopItemData item = NpcShopItemData.create(shopId, material.name(), buyPrice, sellPrice, stock);

        List<NpcShopItemData> items = shopItems.computeIfAbsent(shopId, k -> new ArrayList<>());
        items.add(item);

        storage.saveShopItem(item);

        return item;
    }

    @Override
    public boolean removeItem(UUID shopId, UUID itemId) {
        List<NpcShopItemData> items = shopItems.get(shopId);
        if (items == null) {
            return false;
        }

        boolean removed = items.removeIf(item -> item.id().equals(itemId));
        if (removed) {
            storage.deleteShopItem(shopId, itemId);
        }

        return removed;
    }

    @Override
    public List<NpcShopItemData> getShopItems(UUID shopId) {
        List<NpcShopItemData> items = shopItems.get(shopId);
        return items != null ? new ArrayList<>(items) : Collections.emptyList();
    }

    @Override
    public Optional<NpcShopItemData> getItem(UUID shopId, UUID itemId) {
        List<NpcShopItemData> items = shopItems.get(shopId);
        if (items == null) {
            return Optional.empty();
        }
        return items.stream()
            .filter(item -> item.id().equals(itemId))
            .findFirst();
    }

    @Override
    public Optional<NpcShopItemData> updateItemPrice(UUID shopId, UUID itemId, BigDecimal buyPrice, BigDecimal sellPrice) {
        List<NpcShopItemData> items = shopItems.get(shopId);
        if (items == null) {
            return Optional.empty();
        }

        for (int i = 0; i < items.size(); i++) {
            NpcShopItemData item = items.get(i);
            if (item.id().equals(itemId)) {
                NpcShopItemData updated = new NpcShopItemData(
                    item.id(), item.shopId(), item.material(),
                    item.displayName(), item.amount(),
                    buyPrice, sellPrice,
                    item.stock(), item.maxStock(),
                    item.infiniteStock(), item.createdAt()
                );
                items.set(i, updated);
                storage.saveShopItem(updated);
                return Optional.of(updated);
            }
        }

        return Optional.empty();
    }

    @Override
    public Optional<NpcShopItemData> updateItemStock(UUID shopId, UUID itemId, int stock) {
        List<NpcShopItemData> items = shopItems.get(shopId);
        if (items == null) {
            return Optional.empty();
        }

        for (int i = 0; i < items.size(); i++) {
            NpcShopItemData item = items.get(i);
            if (item.id().equals(itemId)) {
                NpcShopItemData updated = new NpcShopItemData(
                    item.id(), item.shopId(), item.material(),
                    item.displayName(), item.amount(),
                    item.buyPrice(), item.sellPrice(),
                    stock, item.maxStock(),
                    item.infiniteStock(), item.createdAt()
                );
                items.set(i, updated);
                storage.saveShopItem(updated);
                return Optional.of(updated);
            }
        }

        return Optional.empty();
    }

    // ==================== 库存设置 ====================

    @Override
    public Optional<NpcShopData> setInfiniteStock(UUID shopId, boolean infinite) {
        NpcShopData shop = shops.get(shopId);
        if (shop == null) {
            return Optional.empty();
        }

        NpcShopData updated = shop.withInfiniteStock(infinite);
        shops.put(shopId, updated);
        storage.saveShop(updated);

        return Optional.of(updated);
    }

    @Override
    public Optional<NpcShopData> updateShop(UUID shopId, String property, String value) {
        NpcShopData shop = shops.get(shopId);
        if (shop == null) {
            return Optional.empty();
        }

        NpcShopData updated = switch (property.toLowerCase()) {
            case "name" -> new NpcShopData(
                shop.id(), value, shop.ownerId(), shop.ownerName(),
                shop.location(), shop.npcId(), shop.infiniteStock(),
                shop.buyEnabled(), shop.sellEnabled(), shop.createdAt(), shop.description()
            );
            case "description", "desc" -> new NpcShopData(
                shop.id(), shop.name(), shop.ownerId(), shop.ownerName(),
                shop.location(), shop.npcId(), shop.infiniteStock(),
                shop.buyEnabled(), shop.sellEnabled(), shop.createdAt(), value
            );
            case "infinite" -> {
                boolean infinite = Boolean.parseBoolean(value);
                yield shop.withInfiniteStock(infinite);
            }
            case "buy", "buyenabled" -> {
                boolean enabled = Boolean.parseBoolean(value);
                yield shop.withBuyEnabled(enabled);
            }
            case "sell", "sellenabled" -> {
                boolean enabled = Boolean.parseBoolean(value);
                yield shop.withSellEnabled(enabled);
            }
            default -> null;
        };

        if (updated == null) {
            return Optional.empty();
        }

        shops.put(shopId, updated);
        storage.saveShop(updated);

        return Optional.of(updated);
    }

    @Override
    public Optional<NpcShopItemData> setItemInfiniteStock(UUID shopId, UUID itemId, boolean infinite) {
        List<NpcShopItemData> items = shopItems.get(shopId);
        if (items == null) {
            return Optional.empty();
        }

        for (int i = 0; i < items.size(); i++) {
            NpcShopItemData item = items.get(i);
            if (item.id().equals(itemId)) {
                NpcShopItemData updated = new NpcShopItemData(
                    item.id(), item.shopId(), item.material(),
                    item.displayName(), item.amount(),
                    item.buyPrice(), item.sellPrice(),
                    infinite ? Integer.MAX_VALUE : item.stock(),
                    infinite ? Integer.MAX_VALUE : item.maxStock(),
                    infinite, item.createdAt()
                );
                items.set(i, updated);
                storage.saveShopItem(updated);
                return Optional.of(updated);
            }
        }

        return Optional.empty();
    }

    // ==================== 交易 ====================

    @Override
    public TransactionResult buyItem(Player player, UUID shopId, UUID itemId, int amount) {
        NpcShopData shop = shops.get(shopId);
        if (shop == null) {
            return TransactionResult.failure("商店不存在");
        }

        if (!shop.buyEnabled()) {
            return TransactionResult.failure("此商店禁止购买");
        }

        Optional<NpcShopItemData> itemOpt = getItem(shopId, itemId);
        if (itemOpt.isEmpty()) {
            return TransactionResult.failure("物品不存在");
        }

        NpcShopItemData item = itemOpt.get();

        // 检查库存
        if (!shop.infiniteStock() && !item.infiniteStock() && !item.hasStock(amount)) {
            int available = item.stock();
            if (available <= 0) {
                return TransactionResult.outOfStock(formatMaterialName(item.material()), 0);
            }
            // 购买所有可用库存
            amount = available;
        }

        // 计算价格
        BigDecimal totalPrice = item.calculateBuyTotal(amount);

        // 检查玩家金币
        if (!economyService.has(player.getUniqueId(), totalPrice)) {
            BigDecimal playerBalance = economyService.getBalance(player.getUniqueId());
            return TransactionResult.insufficientFunds(totalPrice, playerBalance);
        }

        // 扣除金币
        if (!economyService.withdraw(player.getUniqueId(), totalPrice)) {
            return TransactionResult.failure("交易失败");
        }

        // 给予物品
        ItemStack itemStack = new ItemStack(Material.valueOf(item.material()), amount * item.amount());
        player.getInventory().addItem(itemStack);

        // 更新库存
        if (!shop.infiniteStock() && !item.infiniteStock()) {
            updateItemStock(shopId, itemId, item.stock() - amount);
        }

        // 记录交易
        NpcTradeRecord record = new NpcTradeRecord(
            UUID.randomUUID(),
            shopId,
            player.getUniqueId(),
            player.getName(),
            itemId,
            item.material(),
            NpcTradeRecord.TradeType.BUY,
            amount,
            totalPrice,
            java.time.Instant.now()
        );
        recordTrade(record);

        // 构建成功消息
        String itemName = formatMaterialName(item.material());
        String message = String.format("成功购买 %dx %s，消耗 %s 金币", amount, itemName, totalPrice.toPlainString());
        return TransactionResult.success(message, amount, totalPrice, itemName, true);
    }

    @Override
    public TransactionResult sellItem(Player player, UUID shopId, UUID itemId, int amount) {
        NpcShopData shop = shops.get(shopId);
        if (shop == null) {
            return TransactionResult.failure("商店不存在");
        }

        if (!shop.sellEnabled()) {
            return TransactionResult.failure("此商店禁止出售");
        }

        Optional<NpcShopItemData> itemOpt = getItem(shopId, itemId);
        if (itemOpt.isEmpty()) {
            return TransactionResult.failure("物品不存在");
        }

        NpcShopItemData item = itemOpt.get();
        String itemName = formatMaterialName(item.material());
        Material mat;

        try {
            mat = Material.valueOf(item.material());
        } catch (IllegalArgumentException e) {
            return TransactionResult.failure("无效的物品类型");
        }

        // 检查玩家是否有足够的物品
        int playerAmount = countPlayerItems(player, mat);
        int requiredAmount = amount * item.amount();
        if (playerAmount < requiredAmount) {
            return TransactionResult.insufficientItems(itemName, playerAmount / item.amount(), amount);
        }

        // 计算价格
        BigDecimal totalPrice = item.calculateSellTotal(amount);

        // 移除玩家物品
        removePlayerItems(player, mat, requiredAmount);

        // 给玩家金币
        economyService.deposit(player.getUniqueId(), totalPrice);

        // 更新库存
        if (!shop.infiniteStock() && !item.infiniteStock()) {
            updateItemStock(shopId, itemId, Math.min(item.stock() + amount, item.maxStock()));
        }

        // 记录交易
        NpcTradeRecord record = new NpcTradeRecord(
            UUID.randomUUID(),
            shopId,
            player.getUniqueId(),
            player.getName(),
            itemId,
            item.material(),
            NpcTradeRecord.TradeType.SELL,
            amount,
            totalPrice,
            java.time.Instant.now()
        );
        recordTrade(record);

        // 构建成功消息
        String message = String.format("成功出售 %dx %s，获得 %s 金币", amount, itemName, totalPrice.toPlainString());
        return TransactionResult.success(message, amount, totalPrice, itemName, false);
    }

    /**
     * 格式化材料名称
     */
    private static String formatMaterialName(String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return "未知物品";
        }
        StringBuilder result = new StringBuilder();
        String[] words = materialName.toLowerCase().split("_");

        for (String word : words) {
            if (result.length() > 0) {
                result.append(" ");
            }
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
            }
        }

        return result.toString();
    }

    @Override
    public List<NpcTradeRecord> getTradeHistory(UUID shopId) {
        return tradeHistory.stream()
            .filter(record -> record.shopId().equals(shopId))
            .sorted(Comparator.comparing(NpcTradeRecord::timestamp).reversed())
            .collect(Collectors.toList());
    }

    @Override
    public List<NpcTradeRecord> getPlayerTradeHistory(UUID playerId, int limit) {
        return tradeHistory.stream()
            .filter(record -> record.playerId().equals(playerId))
            .sorted(Comparator.comparing(NpcTradeRecord::timestamp).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    // ==================== GUI ====================

    @Override
    public void openShopGui(Player player, UUID shopId) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            NpcShopGui.openShop(player, shopId, this, new EconomyServiceAdapter(economyService));
        });
    }

    @Override
    public void openNpcShopGui(Player player, int npcId) {
        Optional<NpcShopData> shopOpt = getShopByNpc(npcId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(net.kyori.adventure.text.Component.text("此NPC没有绑定商店", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        openShopGui(player, shopOpt.get().id());
    }

    @Override
    public void onNpcClick(Player player, int npcId) {
        if (hasShop(npcId)) {
            openNpcShopGui(player, npcId);
        }
    }

    // ==================== 统计 ====================

    @Override
    public BigDecimal getShopRevenue(UUID shopId) {
        return shopRevenue.getOrDefault(shopId, BigDecimal.ZERO);
    }

    @Override
    public int getShopTransactionCount(UUID shopId) {
        return shopTransactionCount.getOrDefault(shopId, 0);
    }

    // ==================== 内部方法 ====================

    private void recordTrade(NpcTradeRecord record) {
        tradeHistory.add(record);
        shopRevenue.merge(record.shopId(), record.totalPrice(), BigDecimal::add);
        shopTransactionCount.merge(record.shopId(), 1, Integer::sum);
        storage.saveTradeRecord(record);
    }

    private int countPlayerItems(Player player, Material material) {
        return player.getInventory().all(material).values().stream()
            .mapToInt(ItemStack::getAmount)
            .sum();
    }

    private void removePlayerItems(Player player, Material material, int amount) {
        int remaining = amount;
        var contents = player.getInventory().getContents();
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

    @Override
    public void reload() {
        // 清空缓存
        shops.clear();
        npcToShop.clear();
        shopToNpc.clear();
        shopItems.clear();
        shopRevenue.clear();
        shopTransactionCount.clear();

        // 重新加载数据
        loadAllData();
    }

    @Override
    public void saveAll() {
        // 保存所有商店
        for (NpcShopData shop : shops.values()) {
            storage.saveShop(shop);
        }

        // 保存所有物品
        for (Map.Entry<UUID, List<NpcShopItemData>> entry : shopItems.entrySet()) {
            for (NpcShopItemData item : entry.getValue()) {
                storage.saveShopItem(item);
            }
        }
    }

    /**
     * 检查Citizens插件是否可用
     */
    public boolean isCitizensAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("Citizens") != null;
    }

    /**
     * 获取经济服务适配器
     */
    public ShopEconomyService getEconomyServiceAdapter() {
        return new EconomyServiceAdapter(economyService);
    }

}
