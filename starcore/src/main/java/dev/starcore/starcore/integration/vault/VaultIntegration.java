package dev.starcore.starcore.integration.vault;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.i18n.I18nManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

import java.util.Optional;
import java.util.logging.Level;

/**
 * Vault 集成管理器
 * 负责注册和管理 Vault Economy 服务
 */
public final class VaultIntegration {
    private final Plugin plugin;
    private final EconomyService economyService;
    private final I18nManager i18nManager;
    private final FileConfiguration config;
    private VaultEconomyProvider economyProvider;
    private boolean registered = false;

    public VaultIntegration(Plugin plugin, EconomyService economyService, I18nManager i18nManager) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.i18nManager = i18nManager;
        this.config = plugin.getConfig();
    }

    /**
     * 注册 Vault Economy 服务
     */
    public boolean register() {
        if (registered) {
            plugin.getLogger().warning("Vault Economy 已经注册过了");
            return false;
        }

        // 检查 Vault 是否存在
        Plugin vaultPlugin = plugin.getServer().getPluginManager().getPlugin("Vault");
        if (vaultPlugin == null || !vaultPlugin.isEnabled()) {
            plugin.getLogger().info("Vault 未找到，经济功能仅限内部使用");
            return false;
        }

        try {
            // 读取默认货币名称配置（fallback）
            String currencySingular = config.getString("economy.currency.singular", "金币");
            String currencyPlural = config.getString("economy.currency.plural", "金币");

            // 创建 Economy Provider（传入 i18nManager 以支持多语言）
            economyProvider = new VaultEconomyProvider(economyService, plugin.getName(), currencySingular, currencyPlural);

            // 注册到 Vault（使用 High 优先级，优于 Essentials）
            ServicesManager sm = plugin.getServer().getServicesManager();
            sm.register(
                Economy.class,
                economyProvider,
                plugin,
                ServicePriority.High  // 高优先级，替代 Essentials
            );

            registered = true;

            plugin.getLogger().info("✅ " + plugin.getName() + " Economy 已成功注册到 Vault（优先级：High）");
            plugin.getLogger().info("✅ 其他插件现在可以通过 Vault 使用 " + plugin.getName() + " 的经济系统");

            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("注册 Vault Economy 失败: " + e.getMessage());
            plugin.getLogger().log(Level.WARNING, "Vault registration failed", e);
            return false;
        }
    }

    /**
     * 注销 Vault Economy 服务
     */
    public void unregister() {
        if (!registered || economyProvider == null) {
            return;
        }

        try {
            ServicesManager sm = plugin.getServer().getServicesManager();

            // 遍历查找并注销匹配的提供者
            for (RegisteredServiceProvider<Economy> provider : sm.getRegistrations(Economy.class)) {
                if (provider.getProvider() == economyProvider) {
                    sm.unregister(provider);
                    break;
                }
            }

            registered = false;
            economyProvider = null;

            plugin.getLogger().info("✅ Vault Economy 服务已注销");

        } catch (Exception e) {
            plugin.getLogger().warning("注销 Vault Economy 时出错: " + e.getMessage());
        }
    }

    /**
     * 检查是否已注册
     */
    public boolean isRegistered() {
        return registered;
    }

    /**
     * 检查是否已启用
     */
    public boolean isEnabled() {
        return registered;
    }

    /**
     * 获取 Economy Provider
     * @return 如果已注册则返回 Provider，否则返回空 Optional
     */
    public Optional<VaultEconomyProvider> getEconomyProvider() {
        return Optional.ofNullable(economyProvider);
    }

    /**
     * 检查 Vault 是否可用
     */
    public static boolean isVaultAvailable(Plugin plugin) {
        Plugin vaultPlugin = plugin.getServer().getPluginManager().getPlugin("Vault");
        return vaultPlugin != null && vaultPlugin.isEnabled();
    }

    /**
     * 获取当前活跃的 Economy Provider 信息
     */
    public static String getActiveEconomyInfo(Plugin plugin) {
        ServicesManager sm = plugin.getServer().getServicesManager();

        if (sm.isProvidedFor(Economy.class)) {
            Economy economy = sm.getRegistration(Economy.class).getProvider();
            return String.format(
                "当前 Economy Provider: %s (启用: %s)",
                economy.getName(),
                economy.isEnabled()
            );
        }

        return "没有注册的 Economy Provider";
    }
}
