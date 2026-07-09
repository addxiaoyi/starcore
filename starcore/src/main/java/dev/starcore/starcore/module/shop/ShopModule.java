package dev.starcore.starcore.module.shop;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.shop.command.ShopCommand;
import dev.starcore.starcore.module.shop.gui.ShopMenuListener;
import dev.starcore.starcore.module.shop.listener.NpcShopListener;
import dev.starcore.starcore.module.shop.model.*;
import dev.starcore.starcore.module.shop.service.NpcShopService;
import dev.starcore.starcore.module.shop.service.NpcShopServiceImpl;
import dev.starcore.starcore.module.shop.service.ShopService;
import dev.starcore.starcore.module.shop.service.ShopServiceImpl;
import dev.starcore.starcore.module.shop.storage.ShopDatabaseStorage;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * NPC商店模块
 * 提供玩家商店、NPC商店、拍卖行等交易系统
 */
public final class ShopModule implements StarCoreModule, ShopService, NpcShopService {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "shop",
        "商店系统",
        ModuleLayer.MODULE,
        List.of(),
        List.of(ShopService.class, NpcShopService.class),
        "Provides NPC shops, player trading, auction house and commerce system."
    );

    private Plugin plugin;
    private ShopServiceImpl shopService;
    private NpcShopServiceImpl npcShopService;
    private ShopMenuListener menuListener;
    private NpcShopListener npcShopListener;
    private NationService nationService;
    private MessageService messages;
    private PersistenceService persistenceService;
    private EconomyService economyService;
    private ShopDatabaseStorage databaseStorage;
    // E-110: 注入审计日志服务用于交易记录
    private dev.starcore.starcore.audit.AuditLogService auditLogService;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        this.messages = context.serviceRegistry().require(MessageService.class);
        this.nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        this.persistenceService = context.serviceRegistry().find(PersistenceService.class).orElse(null);
        this.economyService = context.economyService();

        // E-110: 获取审计日志服务
        this.auditLogService = context.serviceRegistry().require(dev.starcore.starcore.audit.AuditLogService.class);

        // 初始化数据库存储
        this.databaseStorage = new ShopDatabaseStorage(context.databaseService(), plugin.getLogger());

        // 获取 TreasuryService（可选）
        var treasuryService = context.serviceRegistry().find(dev.starcore.starcore.module.treasury.TreasuryService.class).orElse(null);

        // 初始化商店服务
        this.shopService = new ShopServiceImpl(
            plugin,
            databaseStorage,
            messages,
            nationService,
            economyService,
            treasuryService
        );

        // 初始化NPC商店服务
        this.npcShopService = new NpcShopServiceImpl(
            plugin,
            shopService,
            databaseStorage,
            messages,
            economyService,
            auditLogService
        );

        // 注册服务到 ServiceRegistry
        context.serviceRegistry().register(ShopService.class, this);
        context.serviceRegistry().register(NpcShopService.class, this);

        // 加载数据
        this.shopService.loadAll();

        // 注册命令
        ShopCommand command = new ShopCommand(this, this, nationService, messages);
        var shopCmd = plugin.getServer().getPluginCommand("shop");
        if (shopCmd != null) {
            shopCmd.setExecutor(command);
            shopCmd.setTabCompleter(command);
        }

        var npcShopCmd = plugin.getServer().getPluginCommand("npcshop");
        if (npcShopCmd != null) {
            npcShopCmd.setExecutor(command);
            npcShopCmd.setTabCompleter(command);
        }

        // 注册GUI监听器
        this.menuListener = new ShopMenuListener(
            shopService,
            npcShopService,
            economyService,
            nationService,
            messages,
            plugin
        );
        plugin.getServer().getPluginManager().registerEvents(menuListener, plugin);

        // 注册NPC交互监听器
        this.npcShopListener = new NpcShopListener(npcShopService, plugin);
        plugin.getServer().getPluginManager().registerEvents(npcShopListener, plugin);

        plugin.getLogger().info("STARCORE Shop module enabled.");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存所有商店数据
        if (shopService != null) {
            shopService.saveAll();
        }

        plugin.getLogger().info("STARCORE Shop module disabled.");
    }

    // ==================== ShopService 实现 ====================

    @Override
    public Shop createPlayerShop(org.bukkit.entity.Player owner, String name) {
        return shopService.createPlayerShop(owner, name);
    }

    @Override
    public Shop createNationShop(java.util.UUID nationId, String name) {
        return shopService.createNationShop(nationId, name);
    }

    @Override
    public java.util.Optional<Shop> getShop(java.util.UUID shopId) {
        return shopService.getShop(shopId);
    }

    @Override
    public java.util.List<Shop> getPlayerShops(java.util.UUID playerId) {
        return shopService.getPlayerShops(playerId);
    }

    @Override
    public java.util.List<Shop> getNationShops(java.util.UUID nationId) {
        return shopService.getNationShops(nationId);
    }

    @Override
    public java.util.List<Shop> getPublicShops() {
        return shopService.getPublicShops();
    }

    @Override
    public java.util.List<Shop> getAllShops() {
        return shopService.getAllShops();
    }

    @Override
    public boolean deleteShop(java.util.UUID shopId, java.util.UUID playerId) {
        return shopService.deleteShop(shopId, playerId);
    }

    @Override
    public boolean updateShop(Shop shop) {
        return shopService.updateShop(shop);
    }

    @Override
    public void saveAll() {
        shopService.saveAll();
    }

    @Override
    public void reload() {
        shopService.loadAll();
        npcShopService.reload();
    }

    @Override
    public boolean addItemToShop(UUID shopId, ShopItem item, UUID playerId) {
        return shopService.addItemToShop(shopId, item, playerId);
    }

    @Override
    public boolean removeItemFromShop(UUID shopId, UUID itemId, UUID playerId) {
        return shopService.removeItemFromShop(shopId, itemId, playerId);
    }

    @Override
    public boolean updateShopItem(UUID shopId, ShopItem item, UUID playerId) {
        return shopService.updateShopItem(shopId, item, playerId);
    }

    @Override
    public Optional<ShopItem> getShopItem(UUID shopId, UUID itemId) {
        return shopService.getShopItem(shopId, itemId);
    }

    @Override
    public TransactionResult buyItem(UUID shopId, UUID itemId, int quantity, org.bukkit.entity.Player player) {
        return shopService.buyItem(shopId, itemId, quantity, player);
    }

    @Override
    public TransactionResult sellItem(UUID shopId, UUID itemId, int quantity, org.bukkit.inventory.ItemStack itemStack, org.bukkit.entity.Player player) {
        return shopService.sellItem(shopId, itemId, quantity, itemStack, player);
    }

    @Override
    public java.util.List<ShopTransaction> getShopTransactions(UUID shopId, int limit) {
        return shopService.getShopTransactions(shopId, limit);
    }

    @Override
    public java.util.List<ShopTransaction> getPlayerTransactions(UUID playerId, int limit) {
        return shopService.getPlayerTransactions(playerId, limit);
    }

    @Override
    public java.util.List<Shop> searchShops(String searchTerm) {
        return shopService.searchShops(searchTerm);
    }

    @Override
    public java.util.List<Shop> getShopsByType(ShopType type) {
        return shopService.getShopsByType(type);
    }

    @Override
    public java.util.List<Shop> getShopsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return shopService.getShopsByPriceRange(minPrice, maxPrice);
    }

    @Override
    public BigDecimal getShopRevenue(UUID shopId) {
        return shopService.getShopRevenue(shopId);
    }

    @Override
    public BigDecimal getPlayerTotalSpent(UUID playerId) {
        return shopService.getPlayerTotalSpent(playerId);
    }

    @Override
    public BigDecimal getPlayerTotalEarned(UUID playerId) {
        return shopService.getPlayerTotalEarned(playerId);
    }

    @Override
    public boolean isShopAdmin(UUID playerId, UUID shopId) {
        return shopService.isShopAdmin(playerId, shopId);
    }

    @Override
    public boolean addShopAdmin(UUID shopId, UUID playerId) {
        return shopService.addShopAdmin(shopId, playerId);
    }

    @Override
    public boolean removeShopAdmin(UUID shopId, UUID playerId) {
        return shopService.removeShopAdmin(shopId, playerId);
    }

    // ==================== NpcShopService 实现 ====================

    @Override
    public boolean bindShopToNpc(UUID shopId, int npcId) {
        return npcShopService.bindShopToNpc(shopId, npcId);
    }

    @Override
    public boolean unbindShopFromNpc(int npcId) {
        return npcShopService.unbindShopFromNpc(npcId);
    }

    @Override
    public Optional<Shop> getShopByNpc(int npcId) {
        return npcShopService.getShopByNpc(npcId);
    }

    @Override
    public Optional<UUID> getNpcShopId(int npcId) {
        return npcShopService.getNpcShopId(npcId);
    }

    @Override
    public boolean hasShop(int npcId) {
        return npcShopService.hasShop(npcId);
    }

    @Override
    public Shop createNpcShopTemplate(String name, String description, ShopCategory category) {
        return npcShopService.createNpcShopTemplate(name, description, category);
    }

    @Override
    public Optional<Shop> getShopTemplate(String templateId) {
        return npcShopService.getShopTemplate(templateId);
    }

    @Override
    public java.util.List<Shop> getAllTemplates() {
        return npcShopService.getAllTemplates();
    }

    @Override
    public Shop createNpcShop(String name, String description, int npcId, ShopCategory category) {
        return npcShopService.createNpcShop(name, description, npcId, category);
    }

    @Override
    public boolean deleteTemplate(String templateId) {
        return npcShopService.deleteTemplate(templateId);
    }

    @Override
    public java.util.List<ShopCategory> getCategories() {
        return npcShopService.getCategories();
    }

    @Override
    public java.util.List<Shop> getShopsByCategory(ShopCategory category) {
        return npcShopService.getShopsByCategory(category);
    }

    @Override
    public void openShopGui(org.bukkit.entity.Player player, java.util.UUID shopId) {
        npcShopService.openShopGui(player, shopId);
    }

    @Override
    public void openNpcShopGui(org.bukkit.entity.Player player, int npcId) {
        npcShopService.openNpcShopGui(player, npcId);
    }

    @Override
    public void onNpcClick(org.bukkit.entity.Player player, int npcId) {
        npcShopService.onNpcClick(player, npcId);
    }

    @Override
    public boolean canTrade(org.bukkit.entity.Player player, java.util.UUID shopId) {
        return npcShopService.canTrade(player, shopId);
    }

    @Override
    public boolean setPlayerTradeLimit(java.util.UUID shopId, java.util.UUID playerId, TradeLimit limit) {
        return npcShopService.setPlayerTradeLimit(shopId, playerId, limit);
    }

    @Override
    public TradeLimit getPlayerTradeLimit(java.util.UUID shopId, java.util.UUID playerId) {
        return npcShopService.getPlayerTradeLimit(shopId, playerId);
    }

    @Override
    public int getPlayerTradeCount(java.util.UUID shopId, java.util.UUID playerId) {
        return npcShopService.getPlayerTradeCount(shopId, playerId);
    }

    @Override
    public void recordTrade(java.util.UUID shopId, java.util.UUID playerId, int quantity) {
        npcShopService.recordTrade(shopId, playerId, quantity);
    }

    @Override
    public ShopItem addItem(java.util.UUID shopId, org.bukkit.Material material, BigDecimal price, int stock) {
        return npcShopService.addItem(shopId, material, price, stock);
    }

    @Override
    public ShopItem addItem(java.util.UUID shopId, org.bukkit.Material material, BigDecimal buyPrice, BigDecimal sellPrice, int stock) {
        return npcShopService.addItem(shopId, material, buyPrice, sellPrice, stock);
    }

    @Override
    public boolean removeItem(java.util.UUID shopId, java.util.UUID itemId) {
        return npcShopService.removeItem(shopId, itemId);
    }

    @Override
    public java.util.List<ShopItem> getShopItems(java.util.UUID shopId) {
        return npcShopService.getShopItems(shopId);
    }

    @Override
    public boolean updateItemPrice(java.util.UUID shopId, java.util.UUID itemId, BigDecimal buyPrice, BigDecimal sellPrice) {
        return npcShopService.updateItemPrice(shopId, itemId, buyPrice, sellPrice);
    }

    @Override
    public Shop setInfiniteStock(java.util.UUID shopId, boolean infinite) {
        return npcShopService.setInfiniteStock(shopId, infinite);
    }

    @Override
    public int getShopTransactionCount(java.util.UUID shopId) {
        return npcShopService.getShopTransactionCount(shopId);
    }

    // Note: getShopRevenue() 和 reload() 在 ShopService 部分已实现

    @Override
    public TransactionResult buyItem(org.bukkit.entity.Player player, java.util.UUID shopId, java.util.UUID itemId, int amount) {
        return npcShopService.buyItem(player, shopId, itemId, amount);
    }

    @Override
    public TransactionResult sellItem(org.bukkit.entity.Player player, java.util.UUID shopId, java.util.UUID itemId, int amount) {
        return npcShopService.sellItem(player, shopId, itemId, amount);
    }

    @Override
    public java.util.List<ShopTransaction> getTradeHistory(java.util.UUID shopId) {
        return npcShopService.getTradeHistory(shopId);
    }

    @Override
    public java.util.List<ShopTransaction> getPlayerTradeHistory(java.util.UUID playerId, int limit) {
        return npcShopService.getPlayerTradeHistory(playerId, limit);
    }
}
