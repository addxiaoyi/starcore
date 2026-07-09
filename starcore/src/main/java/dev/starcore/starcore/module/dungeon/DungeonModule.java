package dev.starcore.starcore.module.dungeon;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.dungeon.command.DungeonCommand;
import dev.starcore.starcore.module.dungeon.gui.DungeonGui;
import dev.starcore.starcore.module.dungeon.gui.DungeonGuiListener;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 副本模块
 * 提供多人副本系统，包括副本创建、BOSS战斗、奖励分发等功能
 */
public final class DungeonModule implements StarCoreModule {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "dungeon",
        "副本系统",
        ModuleLayer.FEATURE,
        List.of(), // 无强制依赖
        List.of(DungeonService.class),
        "多人副本系统：副本创建、BOSS战斗、奖励分发"
    );

    private JavaPlugin plugin;
    private StarCoreContext context;
    private DungeonServiceImpl dungeonService;
    private DungeonCommand command;
    private DungeonGuiListener guiListener;
    private DungeonGui.Factory guiFactory;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        this.context = context;

        plugin.getLogger().info("正在初始化副本模块...");

        // 获取依赖服务
        EconomyService economyService = context.economyService();
        MessageService messages = context.serviceRegistry().require(MessageService.class);

        // 初始化副本服务
        this.dungeonService = new DungeonServiceImpl(plugin, context);

        // 初始化GUI工厂
        this.guiFactory = new DungeonGui.Factory(dungeonService, messages);

        // 初始化GUI监听器
        this.guiListener = new DungeonGuiListener(plugin, dungeonService);

        // 注册事件监听器
        registerEventListeners();

        // 注册命令
        registerCommands();

        // 启动服务
        dungeonService.initialize();

        // 注册服务到 ServiceRegistry
        context.serviceRegistry().register(DungeonService.class, dungeonService);
        context.serviceRegistry().register(DungeonServiceImpl.class, dungeonService);

        plugin.getLogger().info("副本模块已启用!");
        plugin.getLogger().info("  - " + dungeonService.getAllDungeons().size() + " 个副本配置");
        plugin.getLogger().info("  - 最大并发实例: " + dungeonService.getConfig().getMaxConcurrentInstances());
    }

    @Override
    public void disable(StarCoreContext context) {
        plugin.getLogger().info("正在关闭副本模块...");

        // 关闭服务
        if (dungeonService != null) {
            dungeonService.shutdown();
        }

        plugin.getLogger().info("副本模块已关闭");
    }

    /**
     * 注册事件监听器
     */
    private void registerEventListeners() {
        // 注册副本事件监听器
        DungeonEventListener eventListener = new DungeonEventListener(dungeonService, dungeonService.getConfig());
        plugin.getServer().getPluginManager().registerEvents(eventListener, plugin);
        plugin.getLogger().info("副本事件监听器已注册.");
    }

    /**
     * 注册命令
     */
    private void registerCommands() {
        // 主命令 dungeon
        PluginCommand dungeonCmd = plugin.getServer().getPluginCommand("dungeon");
        if (dungeonCmd != null) {
            this.command = new DungeonCommand(dungeonService, context.serviceRegistry().require(MessageService.class), plugin);
            dungeonCmd.setExecutor(command);
            if (command instanceof TabCompleter completer) {
                dungeonCmd.setTabCompleter(completer);
            }
            plugin.getLogger().info("副本命令已注册: /dungeon (别名: /df, /副本)");
        } else {
            plugin.getLogger().warning("副本命令注册失败: dungeon 命令未在 plugin.yml 中定义");
        }
    }

    // ==================== 服务获取器 ====================

    /**
     * 获取副本服务
     */
    public DungeonService getDungeonService() {
        return dungeonService;
    }

    /**
     * 获取副本服务实现
     */
    public DungeonServiceImpl getDungeonServiceImpl() {
        return dungeonService;
    }

    /**
     * 获取GUI工厂
     */
    public DungeonGui.Factory getGuiFactory() {
        return guiFactory;
    }

    /**
     * 打开副本GUI
     */
    public void openGui(org.bukkit.entity.Player player) {
        if (guiFactory != null) {
            guiFactory.open(player);
        }
    }

    @Override
    public String toString() {
        return "DungeonModule{" +
            "dungeons=" + (dungeonService != null ? dungeonService.getAllDungeons().size() : 0) +
            ", activeInstances=" + (dungeonService != null ? dungeonService.getActiveInstances().size() : 0) +
            '}';
    }
}
