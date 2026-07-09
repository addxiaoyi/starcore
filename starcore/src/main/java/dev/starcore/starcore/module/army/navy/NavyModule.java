package dev.starcore.starcore.module.army.navy;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.navy.command.NavyCommand;
import dev.starcore.starcore.module.army.navy.listener.NavyBattleListener;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

/**
 * 海军模块
 * 提供舰队创建、管理、海战等功能
 */
public final class NavyModule implements StarCoreModule {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "navy",
        "海军系统",
        ModuleLayer.MODULE,
        List.of("nation", "treasury"),  // 依赖国家模块和金库模块
        List.of(NavyService.class),
        "Provides fleet creation, management and naval battle system."
    );

    private Plugin plugin;
    private NationService nationService;
    private TreasuryService treasuryService;
    private EconomyService economyService;
    private MessageService messages;
    private PersistenceService persistenceService;

    private NavyService navyService;
    private NavyBattleCalculator battleCalculator;
    private NavyBattleListener battleListener;
    private Optional<DiplomacyService> diplomacyService;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.treasuryService = context.serviceRegistry().require(TreasuryService.class);
        this.economyService = context.economyService();
        this.messages = context.serviceRegistry().require(MessageService.class);
        this.persistenceService = context.serviceRegistry().find(PersistenceService.class).orElse(null);

        // 获取可选服务
        this.diplomacyService = context.serviceRegistry().find(DiplomacyService.class);

        // 初始化战斗计算器
        battleCalculator = new NavyBattleCalculator();

        // 从配置读取海军配置
        ConfigurationSection config = context.plugin().getConfig().getConfigurationSection("navy");
        NavyService.NavyServiceConfig navyConfig = NavyService.NavyServiceConfig.fromConfig(config);

        // 初始化海军服务（传入持久化服务）
        navyService = new NavyService(
            plugin,
            nationService,
            treasuryService,
            battleCalculator,
            navyConfig,
            persistenceService
        );

        // 注册命令
        NavyCommand command = new NavyCommand(navyService, nationService, messages);
        var navyCmd = plugin.getServer().getPluginCommand("navy");
        if (navyCmd != null) {
            navyCmd.setExecutor(command);
            navyCmd.setTabCompleter(command);
        }

        // 注册战斗监听器
        battleListener = new NavyBattleListener(
            navyService,
            nationService,
            diplomacyService,
            messages
        );
        plugin.getServer().getPluginManager().registerEvents(battleListener, plugin);
    }

    @Override
    public void disable(StarCoreContext context) {
        // 清理资源
        if (navyService != null) {
            // 保存所有舰队状态到数据库
            navyService.shutdown();
            navyService = null;
        }

        if (battleListener != null) {
            battleListener = null;
        }

        battleCalculator = null;
    }

    public NavyService getNavyService() {
        return navyService;
    }

    public NavyBattleCalculator getBattleCalculator() {
        return battleCalculator;
    }

    public NavyBattleListener getBattleListener() {
        return battleListener;
    }
}
