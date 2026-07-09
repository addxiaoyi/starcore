package dev.starcore.starcore.integration.vault;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.i18n.I18nManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Vault Economy 集成 - SSS级实现
 * 将 STARCORE 的经济系统暴露给 Vault，实现与其他插件的完美兼容
 * 支持多语言货币名称显示
 */
public final class VaultEconomyProvider implements Economy {
    private final EconomyService economyService;
    private final String pluginName;
    private final I18nManager i18nManager;
    // 默认货币名称（fallback）
    private final String defaultCurrencySingular;
    private final String defaultCurrencyPlural;
    // 显式管理的监听器引用，支持生命周期控制
    private final Listener playerCacheListener;

    // 玩家名称缓存
    private final Map<String, UUID> playerNameToUuid = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerUuidToName = new ConcurrentHashMap<>();
    // 账户存在性缓存 - 避免重复无效查询
    private final Map<UUID, Boolean> accountExistenceCache = new ConcurrentHashMap<>();

    public VaultEconomyProvider(EconomyService economyService, String pluginName, I18nManager i18nManager) {
        this(economyService, pluginName, i18nManager, "金币", "金币");
    }

    /**
     * 兼容旧版构造函数
     */
    public VaultEconomyProvider(EconomyService economyService, String pluginName, String currencySingular, String currencyPlural) {
        this(economyService, pluginName, null, currencySingular, currencyPlural);
    }

    private VaultEconomyProvider(EconomyService economyService, String pluginName, I18nManager i18nManager, String defaultSingular, String defaultPlural) {
        this.economyService = economyService;
        this.pluginName = pluginName;
        this.i18nManager = i18nManager;
        this.defaultCurrencySingular = defaultSingular;
        this.defaultCurrencyPlural = defaultPlural;
        // 显式创建监听器实例
        this.playerCacheListener = new PlayerCacheListener(this);
        // 初始化缓存
        initializePlayerCache();
    }

    /**
     * 初始化玩家名称缓存
     */
    private void initializePlayerCache() {
        // 缓存在线玩家
        for (Player player : Bukkit.getOnlinePlayers()) {
            cachePlayer(player.getUniqueId(), player.getName());
        }

        // 注册玩家事件监听器（使用显式保存的引用，便于后续注销）
        Bukkit.getPluginManager().registerEvents(playerCacheListener,
            Bukkit.getPluginManager().getPlugin(pluginName));
    }

    /**
     * 玩家缓存事件监听器（静态内部类，避免隐式持有外部类引用）
     */
    private static class PlayerCacheListener implements Listener {
        private final VaultEconomyProvider provider;

        PlayerCacheListener(VaultEconomyProvider provider) {
            this.provider = provider;
        }

        @org.bukkit.event.EventHandler
        public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
            provider.cachePlayer(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        }

        @org.bukkit.event.EventHandler
        public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
            String name = event.getPlayer().getName();
            UUID uuid = provider.playerNameToUuid.remove(name.toLowerCase());
            if (uuid != null) {
                provider.playerUuidToName.remove(uuid);
                // 清除账户存在性缓存
                provider.accountExistenceCache.remove(uuid);
            }
        }
    }

    /**
     * 显式注销监听器，释放资源（供外部调用，如插件禁用时）
     */
    public void shutdown() {
        HandlerList.unregisterAll(playerCacheListener);
        playerNameToUuid.clear();
        playerUuidToName.clear();
        accountExistenceCache.clear();
    }

    /**
     * 缓存玩家信息
     */
    private void cachePlayer(UUID uuid, String name) {
        playerNameToUuid.put(name.toLowerCase(), uuid);
        playerUuidToName.put(uuid, name);
    }

    /**
     * 根据名称查找玩家UUID
     */
    private UUID findPlayerUuid(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }

        String normalizedName = playerName.toLowerCase().trim();

        // 先从缓存查找
        UUID cached = playerNameToUuid.get(normalizedName);
        if (cached != null) {
            return cached;
        }

        // 尝试从在线玩家查找
        Player player = Bukkit.getPlayer(normalizedName);
        if (player != null) {
            cachePlayer(player.getUniqueId(), player.getName());
            return player.getUniqueId();
        }

        // 使用 Mojang API 或缓存查找离线玩家（高效替代遍历）
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(normalizedName);
        if (offline != null) {
            UUID uuid = offline.getUniqueId();
            String name = offline.getName();
            if (name != null) {
                cachePlayer(uuid, name);
            }
            return uuid;
        }

        return null;
    }

    /**
     * 安全获取玩家UUID，找不到时记录警告
     */
    private UUID safeFindPlayerUuid(String playerName, String operation) {
        UUID uuid = findPlayerUuid(playerName);
        if (uuid == null) {
            Bukkit.getLogger().warning(
                "[VaultEconomy] " + operation + " 失败: 找不到玩家 " + playerName
            );
        }
        return uuid;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return pluginName + " Economy";
    }

    @Override
    public boolean hasBankSupport() {
        return false; // STARCORE 使用国库系统，不是传统银行
    }

    @Override
    public int fractionalDigits() {
        return 2; // 支持小数点后2位
    }

    @Override
    public String format(double amount) {
        String currencyName = getCurrencyName();
        return String.format("%.2f %s", amount, currencyName);
    }

    @Override
    public String currencyNamePlural() {
        return getCurrencyNameSingularOrPlural(false);
    }

    @Override
    public String currencyNameSingular() {
        return getCurrencyNameSingularOrPlural(true);
    }

    /**
     * 获取货币名称（复数）
     */
    private String getCurrencyName() {
        return getCurrencyNameSingularOrPlural(false);
    }

    /**
     * 获取货币名称
     * @param singular true: 单数, false: 复数
     */
    private String getCurrencyNameSingularOrPlural(boolean singular) {
        if (i18nManager != null) {
            String key = singular ? "economy.currency.name-singular" : "economy.currency.name-plural";
            String localized = i18nManager.getMessage(key);
            // 如果返回的是 key 本身（未找到），使用默认
            if (!localized.equals(key)) {
                return localized;
            }
        }
        return singular ? defaultCurrencySingular : defaultCurrencyPlural;
    }

    // ==================== 账户管理 ====================

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        Boolean cached = accountExistenceCache.get(uuid);
        if (cached != null) {
            return cached;
        }
        boolean exists = economyService.hasAccount(uuid);
        accountExistenceCache.put(uuid, exists);
        return exists;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player); // 不区分世界
    }

    @Override
    public boolean hasAccount(String playerName) {
        // 先从缓存检查
        UUID cachedUuid = playerNameToUuid.get(playerName.toLowerCase());
        if (cachedUuid != null) {
            Boolean cached = accountExistenceCache.get(cachedUuid);
            if (cached != null) {
                return cached;
            }
            boolean exists = economyService.hasAccount(cachedUuid);
            accountExistenceCache.put(cachedUuid, exists);
            return exists;
        }

        UUID uuid = findPlayerUuid(playerName);
        if (uuid == null) {
            // 如果找不到玩家，尝试通过 EconomyService 检查
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(playerName);
            if (offline != null) {
                boolean exists = economyService.hasAccount(offline.getUniqueId());
                accountExistenceCache.put(offline.getUniqueId(), exists);
                return exists;
            }
            return false;
        }

        boolean exists = economyService.hasAccount(uuid);
        accountExistenceCache.put(uuid, exists);
        return exists;
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (hasAccount(player)) {
            return false;
        }
        economyService.createAccount(player.getUniqueId());
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        UUID uuid = findPlayerUuid(playerName);
        if (uuid == null) {
            return false;
        }
        if (economyService.hasAccount(uuid)) {
            return false;
        }
        economyService.createAccount(uuid);
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    // ==================== 余额查询 ====================

    @Override
    public double getBalance(OfflinePlayer player) {
        BigDecimal balance = economyService.getBalance(player.getUniqueId());
        return balance.doubleValue();
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public double getBalance(String playerName) {
        UUID uuid = findPlayerUuid(playerName);
        if (uuid == null) {
            return 0;
        }
        return economyService.getBalance(uuid).doubleValue();
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public boolean has(String playerName, double amount) {
        // 必须先检查账户是否存在，否则隐式返回 0 会导致 has(name, 0) = true
        if (!hasAccount(playerName)) {
            return false;
        }
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    // ==================== 存款 ====================

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(
                0,
                getBalance(player),
                EconomyResponse.ResponseType.FAILURE,
                "金额不能为负数"
            );
        }

        BigDecimal bigAmount = BigDecimal.valueOf(amount);
        boolean success = economyService.deposit(player.getUniqueId(), bigAmount);

        if (success) {
            double newBalance = getBalance(player);
            return new EconomyResponse(
                amount,
                newBalance,
                EconomyResponse.ResponseType.SUCCESS,
                null
            );
        } else {
            return new EconomyResponse(
                0,
                getBalance(player),
                EconomyResponse.ResponseType.FAILURE,
                "存款失败"
            );
        }
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        UUID uuid = findPlayerUuid(playerName);
        if (uuid == null) {
            return new EconomyResponse(
                0,
                0,
                EconomyResponse.ResponseType.FAILURE,
                "玩家不存在: " + playerName
            );
        }

        if (amount < 0) {
            return new EconomyResponse(
                0,
                getBalance(uuid),
                EconomyResponse.ResponseType.FAILURE,
                "金额不能为负数"
            );
        }

        BigDecimal bigAmount = BigDecimal.valueOf(amount);
        boolean success = economyService.deposit(uuid, bigAmount);

        if (success) {
            double newBalance = getBalance(uuid);
            return new EconomyResponse(
                amount,
                newBalance,
                EconomyResponse.ResponseType.SUCCESS,
                null
            );
        } else {
            return new EconomyResponse(
                0,
                getBalance(uuid),
                EconomyResponse.ResponseType.FAILURE,
                "存款失败"
            );
        }
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    // ==================== 取款 ====================

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(
                0,
                getBalance(player),
                EconomyResponse.ResponseType.FAILURE,
                "金额不能为负数"
            );
        }

        if (!has(player, amount)) {
            return new EconomyResponse(
                0,
                getBalance(player),
                EconomyResponse.ResponseType.FAILURE,
                "余额不足"
            );
        }

        BigDecimal bigAmount = BigDecimal.valueOf(amount);
        boolean success = economyService.withdraw(player.getUniqueId(), bigAmount);

        if (success) {
            double newBalance = getBalance(player);
            return new EconomyResponse(
                amount,
                newBalance,
                EconomyResponse.ResponseType.SUCCESS,
                null
            );
        } else {
            return new EconomyResponse(
                0,
                getBalance(player),
                EconomyResponse.ResponseType.FAILURE,
                "取款失败"
            );
        }
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        UUID uuid = findPlayerUuid(playerName);
        if (uuid == null) {
            return new EconomyResponse(
                0,
                0,
                EconomyResponse.ResponseType.FAILURE,
                "玩家不存在: " + playerName
            );
        }

        if (amount < 0) {
            return new EconomyResponse(
                0,
                getBalance(uuid),
                EconomyResponse.ResponseType.FAILURE,
                "金额不能为负数"
            );
        }

        if (!has(uuid, amount)) {
            return new EconomyResponse(
                0,
                getBalance(uuid),
                EconomyResponse.ResponseType.FAILURE,
                "余额不足"
            );
        }

        BigDecimal bigAmount = BigDecimal.valueOf(amount);
        boolean success = economyService.withdraw(uuid, bigAmount);

        if (success) {
            double newBalance = getBalance(uuid);
            return new EconomyResponse(
                amount,
                newBalance,
                EconomyResponse.ResponseType.SUCCESS,
                null
            );
        } else {
            return new EconomyResponse(
                0,
                getBalance(uuid),
                EconomyResponse.ResponseType.FAILURE,
                "取款失败"
            );
        }
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    // 添加辅助方法获取 UUID 的余额
    private double getBalance(UUID uuid) {
        return economyService.getBalance(uuid).doubleValue();
    }

    // 添加辅助方法检查余额
    private boolean has(UUID uuid, double amount) {
        return getBalance(uuid) >= amount;
    }

    // ==================== 银行系统（不支持）====================

    /**
     * 统一返回 NOT_IMPLEMENTED 响应
     * STARCORE 使用 Treasury 国库系统，不支持传统银行功能
     */
    private EconomyResponse bankNotImplemented() {
        return new EconomyResponse(
            0,
            0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED,
            "STARCORE 使用国库系统，不支持银行功能"
        );
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return bankNotImplemented();
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return bankNotImplemented();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return bankNotImplemented();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return bankNotImplemented();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return bankNotImplemented();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return bankNotImplemented();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return bankNotImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return bankNotImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return bankNotImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return bankNotImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return bankNotImplemented();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }
}
