package dev.starcore.starcore.pet;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.foundation.economy.EconomyService;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Field;
import java.util.List;
import java.util.logging.Logger;

/**
 * 宠物/坐骑系统模块
 */
public class PetModule implements StarCoreModule {

    private PetService petService;
    private PetShopGUI shopGUI;
    private PetListGUI listGUI;
    private PetDetailGUI detailGUI;
    private PetCommand petCommand;
    private PetListener petListener;

    @Override
    public ModuleMetadata metadata() {
        return new ModuleMetadata(
            "pet",
            "Pet Module",
            ModuleLayer.FEATURE,
            List.of(),
            List.of(PetService.class),
            "Pet and mount system - summon, upgrade, ride"
        );
    }

    @Override
    public void enable(StarCoreContext context) {
        Logger logger = context.plugin().getLogger();

        // 加载配置
        PetConfig config = new PetConfig(context.plugin().getDataFolder());

        // 获取经济服务
        EconomyService economyService = context.serviceRegistry()
            .find(EconomyService.class)
            .orElse(null);

        // 获取玩家档案服务
        var playerProfileService = context.serviceRegistry()
            .find(dev.starcore.starcore.foundation.player.PlayerProfileService.class)
            .orElse(null);

        // 初始化服务
        petService = new PetService(
            context.plugin(),
            config,
            economyService,
            playerProfileService
        );

        // 初始化GUI
        shopGUI = new PetShopGUI(petService, economyService);
        listGUI = new PetListGUI(petService);
        detailGUI = new PetDetailGUI(petService);

        // 初始化命令
        petCommand = new PetCommand(petService, shopGUI, listGUI, detailGUI);

        // 初始化监听器
        petListener = new PetListener(petService, shopGUI, listGUI, detailGUI, petCommand);

        // 注册服务
        context.serviceRegistry().register(PetService.class, petService);

        // 注册事件监听器
        context.plugin().getServer().getPluginManager().registerEvents(petListener, context.plugin());

        // 注册命令
        registerCommands(context.plugin());

        logger.info("Pet module started - supports summon, ride, upgrade, and shop");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存数据
        if (petService != null) {
            petService.saveAll();
        }

        context.plugin().getLogger().info("Pet module stopped");
    }

    /**
     * 注册宠物相关命令
     */
    private void registerCommands(Plugin plugin) {
        // 获取命令映射器，使用 Server.getPluginManager() 避免 SimplePluginManager 弃用
        try {
            Server server = plugin.getServer();
            PluginManager pluginManager = server.getPluginManager();

            // 获取 PluginManager 中的 commandMap 字段
            Field commandMapField = pluginManager.getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(pluginManager);

            // 注册 /pet 命令
            registerCommand("pet", petCommand, commandMap, "starcore.pet", plugin);

            // 注册别名命令
            registerAliasCommand("petlist", petCommand, commandMap, "starcore.pet.list", plugin);
            registerAliasCommand("petshop", petCommand, commandMap, "starcore.pet.shop", plugin);
            registerAliasCommand("myshop", petCommand, commandMap, "starcore.pet.shop", plugin);

        } catch (Exception e) {
            plugin.getLogger().warning("无法注册宠物命令: " + e.getMessage());
        }
    }

    /**
     * 注册单个命令
     */
    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor,
                                 CommandMap commandMap, String permission, Plugin plugin) {
        // 检查命令是否已存在
        Command existing = commandMap.getCommand(name);
        if (existing != null) {
            // 如果已存在，尝试设置执行器
            if (existing instanceof org.bukkit.command.PluginCommand pluginCmd) {
                pluginCmd.setExecutor(executor);
            }
            return;
        }

        // 使用匿名命令类
        Command cmd = new Command(name) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                return executor.onCommand(sender, this, label, args);
            }

            @Override
            public java.util.List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
                    return tabCompleter.onTabComplete(sender, this, alias, args);
                }
                return super.tabComplete(sender, alias, args);
            }
        };
        cmd.setPermission(permission);
        commandMap.register(plugin.getDescription().getName(), cmd);
    }

    /**
     * 注册别名命令
     */
    private void registerAliasCommand(String alias, org.bukkit.command.CommandExecutor executor,
                                       CommandMap commandMap, String permission, Plugin plugin) {
        registerCommand(alias, executor, commandMap, permission, plugin);
    }

    /**
     * 获取宠物服务
     */
    public PetService getPetService() {
        return petService;
    }

    /**
     * 获取商店GUI
     */
    public PetShopGUI getShopGUI() {
        return shopGUI;
    }

    /**
     * 获取列表GUI
     */
    public PetListGUI getListGUI() {
        return listGUI;
    }

    /**
     * 获取详情GUI
     */
    public PetDetailGUI getDetailGUI() {
        return detailGUI;
    }
}
