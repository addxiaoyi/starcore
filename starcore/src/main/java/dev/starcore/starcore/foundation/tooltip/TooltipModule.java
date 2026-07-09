package dev.starcore.starcore.foundation.tooltip;

import dev.starcore.starcore.StarCorePlugin;
import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.module.nation.NationService;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * 提示系统模块
 * 统一管理智能物品提示和快捷栏提示
 */
public class TooltipModule {

    private final StarCorePlugin plugin;
    private SmartTooltipService tooltipService;
    private HotbarTooltipListener hotbarListener;
    private TooltipConfigManager configManager;
    private boolean enabled = false;

    public TooltipModule(@NotNull StarCorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 启用提示系统模块
     */
    public void enable() {
        if (enabled) {
            plugin.getLogger().warning("提示系统模块已经启用");
            return;
        }

        plugin.getLogger().info("正在启用智能物品提示...");

        // 1. 初始化服务
        tooltipService = SmartTooltipService.getInstance();

        // 2. 加载配置
        configManager = new TooltipConfigManager(plugin, tooltipService);

        // 3. 获取 NationService
        NationService nationService = findNationService();

        // 4. 注册默认提供者
        tooltipService.registerProvider(new DefaultTooltipProvider());

        // 5. 注册国家提示提供者 (传入 nationService)
        tooltipService.registerProvider(new NationTooltipProvider(nationService));

        // 6. 启用快捷栏监听
        if (tooltipService.getConfig().isHotbarHintsEnabled()) {
            hotbarListener = new HotbarTooltipListener(tooltipService);
            Bukkit.getPluginManager().registerEvents(hotbarListener, plugin);
            plugin.getLogger().info("快捷栏提示已启用");
        }

        // 7. 注册命令
        registerCommands();

        enabled = true;
        plugin.getLogger().info("智能物品提示系统已启用!");
    }

    /**
     * 禁用提示系统模块
     */
    public void disable() {
        if (!enabled) {
            return;
        }

        plugin.getLogger().info("正在禁用智能物品提示...");

        // 1. 注销监听器
        if (hotbarListener != null) {
            HandlerList.unregisterAll(hotbarListener);
            hotbarListener.shutdown();
            hotbarListener = null;
        }

        // 2. 关闭服务
        if (tooltipService != null) {
            tooltipService.shutdown();
        }

        // 3. 保存配置
        if (configManager != null) {
            configManager.saveConfig();
        }

        enabled = false;
        plugin.getLogger().info("智能物品提示系统已禁用");
    }

    /**
     * 获取提示服务
     */
    @NotNull
    public SmartTooltipService getTooltipService() {
        if (tooltipService == null) {
            throw new IllegalStateException("提示服务尚未初始化");
        }
        return tooltipService;
    }

    /**
     * 获取配置管理器
     */
    @NotNull
    public TooltipConfigManager getConfigManager() {
        if (configManager == null) {
            throw new IllegalStateException("配置管理器尚未初始化");
        }
        return configManager;
    }

    /**
     * 检查模块是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 注册命令
     */
    private void registerCommands() {
        // 可注册 /tooltip reload, /tooltip toggle 等
    }

    /**
     * 查找 NationService
     */
    private NationService findNationService() {
        try {
            // 通过 StarCorePlugin 获取 context，再通过 serviceRegistry 查找
            var ctx = plugin.context();
            if (ctx != null) {
                var registry = ctx.serviceRegistry();
                if (registry != null) {
                    return registry.find(NationService.class).orElse(null);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("无法获取 NationService: " + e.getMessage());
        }
        return null;
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        if (configManager != null) {
            configManager.loadConfig();
        }
    }
}
