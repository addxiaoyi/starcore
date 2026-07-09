package dev.starcore.starcore.storage;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.service.StarCoreService;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * 仓库系统模块
 * 集成到StarCore插件系统
 */
public class StorageModule implements StarCoreModule {
    private StorageService storageService;
    private WarehouseListener listener;
    private StorageConfigLoader configLoader;
    private InternalEconomyService economyService;

    @Override
    public ModuleMetadata metadata() {
        return new ModuleMetadata(
            "storage",
            "Storage Module",
            ModuleLayer.FEATURE,
            List.of(),
            List.of(StorageService.class),
            "Warehouse and storage system"
        );
    }

    @Override
    public void enable(StarCoreContext context) {
        // 加载配置
        configLoader = new StorageConfigLoader(context.plugin());
        StorageConfig config = configLoader.load();

        // 获取经济服务
        this.economyService = context.economyService();

        // 初始化服务
        storageService = new StorageService(context.plugin().getLogger(), config, economyService, context.plugin());
        storageService.start();

        // 注册服务到服务注册表
        context.serviceRegistry().register(StorageService.class, storageService);

        // 注册事件监听器
        listener = new WarehouseListener(storageService);
        context.plugin().getServer().getPluginManager().registerEvents(listener, context.plugin());

        // 注册命令
        PluginCommand command = context.plugin().getCommand("warehouse");
        if (command != null) {
            WarehouseCommand commandExecutor = new WarehouseCommand(storageService);
            command.setExecutor(commandExecutor);
            command.setTabCompleter(commandExecutor);
        } else {
            context.plugin().getLogger().warning("Failed to register /warehouse command - command not defined in plugin.yml");
        }

        context.plugin().getLogger().info("Storage module started");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 清理GUI
        if (listener != null) {
            listener.cleanup();
        }

        // 停止服务
        if (storageService != null) {
            storageService.stop();
        }

        // 保存配置
        if (configLoader != null) {
            configLoader.save();
        }

        context.plugin().getLogger().info("Storage module stopped");
    }

    /**
     * 获取仓库服务
     * @return StorageService实例
     */
    public StorageService getStorageService() {
        return storageService;
    }

    /**
     * 重载配置
     */
    public void reloadConfig(Plugin plugin) {
        if (configLoader != null && storageService != null) {
            StorageConfig newConfig = configLoader.reload();
            // E-008: 通过 setConfig 把新配置引用下发到 storageService 及其子服务,
            // 避免服务仍持有旧引用导致配置不生效
            storageService.setConfig(newConfig);
            plugin.getLogger().info("Storage module config reloaded: " + newConfig);
        }
    }
}
