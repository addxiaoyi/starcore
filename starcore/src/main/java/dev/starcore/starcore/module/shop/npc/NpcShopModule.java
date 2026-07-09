package dev.starcore.starcore.module.shop.npc;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.module.shop.npc.NpcShopServiceImpl.ShopEconomyService;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * NPC商店模块初始化器
 * 负责注册命令、监听器和初始化服务
 */
public class NpcShopModule {

    private final Plugin plugin;
    private NpcShopStorage storage;
    private NpcShopServiceImpl service;
    private NpcShopCommand command;
    private NpcShopListener listener;
    private NpcShopGuiListener guiListener;

    public NpcShopModule(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化模块
     * @param databaseService 数据库服务
     * @param economyService 经济服务
     */
    public void initialize(DatabaseService databaseService, dev.starcore.starcore.foundation.economy.EconomyService economyService) {
        // 创建存储层
        storage = new NpcShopStorage(databaseService, plugin.getLogger());

        // 创建服务层
        service = new NpcShopServiceImpl(plugin, storage, economyService);

        // 创建命令处理器
        command = new NpcShopCommand(plugin, service);

        // 创建监听器
        listener = new NpcShopListener(plugin, service);
        // 使用适配器包装经济服务
        guiListener = new NpcShopGuiListener(plugin, service, service.getEconomyServiceAdapter());

        // 注册命令
        registerCommands();

        // 注册监听器
        registerListeners();

        plugin.getLogger().info("NPC商店模块初始化完成");
    }

    /**
     * 注册命令
     */
    private void registerCommands() {
        // 注册主命令 - 使用 server.getPluginCommand 而不是 Bukkit.getPluginCommand
        org.bukkit.command.PluginCommand npcShopCommand = plugin.getServer().getPluginCommand("npcshop");
        if (npcShopCommand != null) {
            npcShopCommand.setExecutor(command);
            npcShopCommand.setTabCompleter(command);
        } else {
            // 动态注册命令
            plugin.getLogger().warning("npcshop 命令未在 plugin.yml 中定义，尝试动态注册");

            // 尝试动态注册
            try {
                var commandMap = plugin.getServer().getCommandMap();
                org.bukkit.command.Command npcShopCmd = new org.bukkit.command.Command("npcshop") {
                    @Override
                    public boolean execute(org.bukkit.command.CommandSender sender, String commandLabel, String[] args) {
                        return command.onCommand(sender, this, commandLabel, args);
                    }

                    @Override
                    public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
                        return command.onTabComplete(sender, this, alias, args);
                    }
                };
                commandMap.register(plugin.getDescription().getName(), npcShopCmd);
            } catch (Exception e) {
                plugin.getLogger().warning("无法注册 npcshop 命令: " + e.getMessage());
            }
        }
    }

    /**
     * 注册监听器
     */
    private void registerListeners() {
        PluginManager pm = plugin.getServer().getPluginManager();

        // 注册NPC交互监听器
        if (pm.getPlugin("Citizens") != null) {
            pm.registerEvents(listener, plugin);
        }

        // 注册GUI监听器
        pm.registerEvents(guiListener, plugin);
    }

    /**
     * 获取服务实例
     */
    public NpcShopService getService() {
        return service;
    }

    /**
     * 关闭模块
     */
    public void shutdown() {
        plugin.getLogger().info("NPC商店模块已关闭");
    }
}
