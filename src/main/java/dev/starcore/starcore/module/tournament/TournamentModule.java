package dev.starcore.starcore.module.tournament;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.module.tournament.command.TournamentCommand;
import dev.starcore.starcore.module.tournament.gui.TournamentGui;
import dev.starcore.starcore.module.tournament.gui.TournamentGuiListener;
import dev.starcore.starcore.module.tournament.listener.TournamentListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 锦标赛模块
 * 提供 PvP 锦标赛和 PvE 竞速比赛功能
 *
 * 支持的比赛类型：
 * - PvP 1v1 单挑赛
 * - PvP FFA 乱斗赛
 * - PvP 团队赛
 * - PvE 速通挑战
 * - PvE 跑酷挑战
 * - PvP 淘汰赛
 */
public final class TournamentModule implements StarCoreModule {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "tournament",
        "锦标赛系统",
        ModuleLayer.MODULE,
        List.of(),
        List.of(TournamentService.class),
        "提供 PvP 锦标赛和 PvE 竞速比赛功能"
    );

    private JavaPlugin plugin;
    private TournamentService tournamentService;
    private TournamentGui tournamentGui;
    private TournamentListener tournamentListener;
    private TournamentGuiListener guiListener;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    /**
     * 获取锦标赛服务实例
     */
    public TournamentService getTournamentService() {
        return tournamentService;
    }

    /**
     * 获取锦标赛 GUI 实例
     */
    public TournamentGui tournamentGui() {
        return tournamentGui;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();

        // 初始化服务
        this.tournamentService = new TournamentServiceImpl(plugin, context.scheduler());

        // 初始化 GUI
        this.tournamentGui = new TournamentGui(plugin, tournamentService);

        // 初始化监听器
        this.tournamentListener = new TournamentListener(plugin, tournamentService);
        this.guiListener = new TournamentGuiListener(plugin, tournamentService, tournamentGui);

        // 注册命令
        registerCommands();

        // 注册事件监听器
        context.plugin().getServer().getPluginManager()
            .registerEvents(tournamentListener, context.plugin());
        context.plugin().getServer().getPluginManager()
            .registerEvents(guiListener, context.plugin());

        // 注册到服务注册表
        context.serviceRegistry().register(TournamentService.class, tournamentService);

        plugin.getLogger().info("锦标赛模块已启用 - 支持 6 种比赛类型");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存所有比赛数据
        if (tournamentService != null) {
            tournamentService.saveAllTournaments();
            tournamentService.cancelAllMatches();
        }
        plugin.getLogger().info("锦标赛模块已禁用");
    }

    private void registerCommands() {
        TournamentCommand command = new TournamentCommand(plugin, tournamentService, tournamentGui);

        // 注册主命令
        org.bukkit.command.PluginCommand tournamentCmd = plugin.getCommand("tournament");
        if (tournamentCmd != null) {
            tournamentCmd.setExecutor(command);
            tournamentCmd.setTabCompleter(command);
        }

        // 注册别名命令
        org.bukkit.command.PluginCommand tourCmd = plugin.getCommand("tour");
        if (tourCmd != null) {
            tourCmd.setExecutor(command);
            tourCmd.setTabCompleter(command);
        }
    }
}
