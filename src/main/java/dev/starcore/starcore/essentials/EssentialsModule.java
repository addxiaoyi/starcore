package dev.starcore.starcore.essentials;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.scheduler.FoliaCompatScheduler;
import dev.starcore.starcore.essentials.baltop.BalTopService;
import dev.starcore.starcore.essentials.command.*;
import dev.starcore.starcore.essentials.data.EssentialsDataManager;
import dev.starcore.starcore.essentials.home.HomeService;
import dev.starcore.starcore.essentials.listener.EssentialsGuiListener;
import dev.starcore.starcore.essentials.nickname.NicknameService;
import dev.starcore.starcore.essentials.social.SocialService;
import dev.starcore.starcore.essentials.teleport.TeleportConfig;
import dev.starcore.starcore.essentials.teleport.TeleportService;
import dev.starcore.starcore.essentials.warp.WarpService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Essentials 模块
 * 替代 EssentialsX 的核心功能
 *
 * 包含：
 * - 传送系统（spawn/home/warp/tpa/back）
 * - 社交系统（msg/reply/ignore）
 * - 昵称系统（nick/realname）
 * - Warp传送点
 * - 经济系统（bal/baltop/pay/eco）
 */
public final class EssentialsModule implements StarCoreModule, Listener {
    private final Plugin plugin;
    private final FoliaCompatScheduler scheduler;
    private final MessageService messages;
    private final EconomyService economyService;

    // 服务
    private TeleportService teleportService;
    private HomeService homeService;
    private WarpService warpService;
    private SocialService socialService;
    private NicknameService nicknameService;
    private BalTopService balTopService;
    private EssentialsDataManager dataManager;

    public EssentialsModule(
        Plugin plugin,
        FoliaCompatScheduler scheduler,
        MessageService messages,
        EconomyService economyService
    ) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.messages = messages;
        this.economyService = economyService;
    }

    @Override
    public ModuleMetadata metadata() {
        return new ModuleMetadata(
            "essentials",
            "Essentials Module",
            dev.starcore.starcore.core.module.ModuleLayer.FEATURE,
            List.of(),
            List.of(),
            "Essentials functionality module"
        );
    }

    @Override
    public void enable(StarCoreContext context) {
        plugin.getLogger().info("正在启用 Essentials 模块...");

        // 初始化配置
        TeleportConfig teleportConfig = TeleportConfig.defaults();

        // 初始化服务
        this.homeService = new HomeService(teleportConfig.maxHomes());
        this.warpService = new WarpService();
        this.socialService = new SocialService();
        this.nicknameService = new NicknameService();
        this.teleportService = new TeleportService(plugin, scheduler, teleportConfig);
        this.balTopService = new BalTopService();

        // 建立服务引用
        teleportService.setHomeService(homeService);
        teleportService.setWarpService(warpService);

        // 初始化数据管理器
        this.dataManager = new EssentialsDataManager(
            plugin,
            homeService,
            warpService,
            nicknameService,
            socialService,
            balTopService
        );

        // 加载数据
        dataManager.loadAll();

        // 注册命令
        registerCommands();

        // ========== 注册 GUI 监听器 ==========
        EssentialsGuiListener guiListener = new EssentialsGuiListener(
            plugin,
            homeService,
            warpService,
            teleportService,
            balTopService,
            economyService
        );
        plugin.getServer().getPluginManager().registerEvents(guiListener, plugin);

        // 注册主监听器
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // 定期保存数据（每5分钟）
        scheduler.runGlobalRepeating(() -> {
            dataManager.saveAll();
        }, 20L * 60 * 5, 20L * 60 * 5);

        // 定期更新财富排行榜（每分钟）
        scheduler.runGlobalRepeating(() -> {
            balTopService.updateRankings(economyService.getAllBalances());
        }, 20L * 60, 20L * 60);

        plugin.getLogger().info("✅ Essentials 模块已启用");
    }

    @Override
    public void disable(StarCoreContext context) {
        plugin.getLogger().info("正在禁用 Essentials 模块...");

        // 保存数据
        if (dataManager != null) {
            dataManager.saveAll();
        }

        plugin.getLogger().info("✅ Essentials 模块已禁用");
    }

    /**
     * 注册命令
     */
    private void registerCommands() {
        // ========== 社交命令 ==========
        SocialCommand socialCmd = new SocialCommand(socialService);
        registerCommand("msg", socialCmd);
        registerCommand("tell", socialCmd);
        registerCommand("whisper", socialCmd);
        registerCommand("reply", socialCmd);
        registerCommand("r", socialCmd);
        registerCommand("ignore", socialCmd);
        registerCommand("unignore", socialCmd);

        // ========== 昵称命令 ==========
        NicknameCommand nicknameCmd = new NicknameCommand(nicknameService);
        registerCommand("nick", nicknameCmd);
        registerCommand("realname", nicknameCmd);

        // ========== 家园命令 ==========
        HomeCommand homeCmd = new HomeCommand(homeService, teleportService);
        registerCommand("home", homeCmd);
        registerCommand("sethome", homeCmd);
        registerCommand("delhome", homeCmd);
        registerCommand("homes", homeCmd);

        // ========== Warp 命令 ==========
        WarpCommand warpCmd = new WarpCommand(warpService, teleportService);
        registerCommand("warp", warpCmd);
        registerCommand("setwarp", warpCmd);
        registerCommand("delwarp", warpCmd);
        registerCommand("warps", warpCmd);

        // ========== TPA 命令 ==========
        TpaCommand tpaCmd = new TpaCommand(teleportService);
        registerCommand("tpa", tpaCmd);
        registerCommand("tpaccept", tpaCmd);
        registerCommand("tpdeny", tpaCmd);
        registerCommand("back", tpaCmd);
        registerCommand("spawn", tpaCmd);
        registerCommand("setspawn", tpaCmd);

        // ========== 经济命令 ==========
        EconomyCommand economyCmd = new EconomyCommand(economyService, balTopService);
        registerCommand("balance", economyCmd);
        registerCommand("bal", economyCmd);
        registerCommand("money", economyCmd);
        registerCommand("baltop", economyCmd);
        registerCommand("pay", economyCmd);
        registerCommand("eco", economyCmd);
        registerCommand("economy", economyCmd);

        // ========== GUI 命令 ==========
        EssentialsGuiCommand guiCmd = new EssentialsGuiCommand(
            homeService,
            warpService,
            teleportService,
            balTopService,
            economyService
        );
        registerCommand("homegui", guiCmd);
        registerCommand("warpgui", guiCmd);
        registerCommand("baltopgui", guiCmd);
        registerCommand("teleporter", guiCmd);

        plugin.getLogger().info("已注册 Essentials 命令: home/sethome/delhome/homes, warp/setwarp/delwarp/warps, tpa/tpaccept/tpdeny/back/spawn, balance/baltop/pay/eco, homegui/warpgui/baltopgui/teleporter");
    }

    /**
     * 注册单个命令
     */
    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = plugin.getServer().getPluginCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
                command.setTabCompleter(tabCompleter);
            }
        }
    }

    /**
     * 玩家加入
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 异步加载玩家数据
        scheduler.runAsync(() -> {
            // 数据已经在启动时加载，这里可以做额外处理
        });
    }

    /**
     * 玩家退出
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 异步保存玩家数据
        scheduler.runAsync(() -> {
            dataManager.savePlayerData(event.getPlayer().getUniqueId());
        });

        // 清理内存
        homeService.cleanup(event.getPlayer().getUniqueId());
        socialService.cleanup(event.getPlayer().getUniqueId());
        nicknameService.cleanup(event.getPlayer().getUniqueId());
        teleportService.cleanup(event.getPlayer().getUniqueId());
    }

    // Getter 方法
    public TeleportService getTeleportService() {
        return teleportService;
    }

    public HomeService getHomeService() {
        return homeService;
    }

    public WarpService getWarpService() {
        return warpService;
    }

    public SocialService getSocialService() {
        return socialService;
    }

    public NicknameService getNicknameService() {
        return nicknameService;
    }

    public BalTopService getBalTopService() {
        return balTopService;
    }

    public EssentialsDataManager getDataManager() {
        return dataManager;
    }
}
