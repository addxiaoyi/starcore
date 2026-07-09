package dev.starcore.starcore.module.shop.service;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.shop.model.*;
import dev.starcore.starcore.module.shop.storage.ShopDatabaseStorage;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 商店服务（门面类，兼容接口）
 */
public final class ShopServiceFacade implements ShopService {
    private final ShopServiceImpl delegate;

    public ShopServiceFacade(
        Plugin plugin,
        ShopDatabaseStorage storage,
        MessageService messages,
        NationService nationService,
        TreasuryService treasuryService
    ) {
        this.delegate = new ShopServiceImpl(
            plugin,
            storage,
            messages,
            nationService,
            null, // economyService 延迟
            treasuryService
        );
    }

    @Override
    public Shop createPlayerShop(Player owner, String name) {
        return delegate.createPlayerShop(owner, name);
    }

    @Override
    public Shop createNationShop(UUID nationId, String name) {
        return delegate.createNationShop(nationId, name);
    }

    @Override
    public Optional<Shop> getShop(UUID shopId) {
        return delegate.getShop(shopId);
    }

    @Override
    public List<Shop> getPlayerShops(UUID playerId) {
        return delegate.getPlayerShops(playerId);
    }

    @Override
    public List<Shop> getNationShops(UUID nationId) {
        return delegate.getNationShops(nationId);
    }

    @Override
    public List<Shop> getPublicShops() {
        return delegate.getPublicShops();
    }

    @Override
    public List<Shop> getAllShops() {
        return delegate.getAllShops();
    }

    @Override
    public boolean deleteShop(UUID shopId, UUID playerId) {
        return delegate.deleteShop(shopId, playerId);
    }

    @Override
    public boolean updateShop(Shop shop) {
        return delegate.updateShop(shop);
    }

    @Override
    public void saveAll() {
        delegate.saveAll();
    }

    @Override
    public boolean addItemToShop(UUID shopId, ShopItem item, UUID playerId) {
        return delegate.addItemToShop(shopId, item, playerId);
    }

    @Override
    public boolean removeItemFromShop(UUID shopId, UUID itemId, UUID playerId) {
        return delegate.removeItemFromShop(shopId, itemId, playerId);
    }

    @Override
    public boolean updateShopItem(UUID shopId, ShopItem item, UUID playerId) {
        return delegate.updateShopItem(shopId, item, playerId);
    }

    @Override
    public Optional<ShopItem> getShopItem(UUID shopId, UUID itemId) {
        return delegate.getShopItem(shopId, itemId);
    }

    @Override
    public TransactionResult buyItem(UUID shopId, UUID itemId, int quantity, Player player) {
        return delegate.buyItem(shopId, itemId, quantity, player);
    }

    @Override
    public TransactionResult sellItem(UUID shopId, UUID itemId, int quantity, org.bukkit.inventory.ItemStack itemStack, Player player) {
        return delegate.sellItem(shopId, itemId, quantity, itemStack, player);
    }

    @Override
    public List<ShopTransaction> getShopTransactions(UUID shopId, int limit) {
        return delegate.getShopTransactions(shopId, limit);
    }

    @Override
    public List<ShopTransaction> getPlayerTransactions(UUID playerId, int limit) {
        return delegate.getPlayerTransactions(playerId, limit);
    }

    @Override
    public List<Shop> searchShops(String searchTerm) {
        return delegate.searchShops(searchTerm);
    }

    @Override
    public List<Shop> getShopsByType(ShopType type) {
        return delegate.getShopsByType(type);
    }

    @Override
    public List<Shop> getShopsByPriceRange(java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice) {
        return delegate.getShopsByPriceRange(minPrice, maxPrice);
    }

    @Override
    public java.math.BigDecimal getShopRevenue(UUID shopId) {
        return delegate.getShopRevenue(shopId);
    }

    @Override
    public java.math.BigDecimal getPlayerTotalSpent(UUID playerId) {
        return delegate.getPlayerTotalSpent(playerId);
    }

    @Override
    public java.math.BigDecimal getPlayerTotalEarned(UUID playerId) {
        return delegate.getPlayerTotalEarned(playerId);
    }

    @Override
    public boolean isShopAdmin(UUID playerId, UUID shopId) {
        return delegate.isShopAdmin(playerId, shopId);
    }

    @Override
    public boolean addShopAdmin(UUID shopId, UUID playerId) {
        return delegate.addShopAdmin(shopId, playerId);
    }

    @Override
    public boolean removeShopAdmin(UUID shopId, UUID playerId) {
        return delegate.removeShopAdmin(shopId, playerId);
    }

    @Override
    public void reload() {
        delegate.loadAll();
    }

    public void loadAll() {
        delegate.loadAll();
    }
}
