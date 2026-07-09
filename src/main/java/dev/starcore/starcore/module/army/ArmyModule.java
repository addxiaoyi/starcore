package dev.starcore.starcore.module.army;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.animation.GuiAnimationRegistry;
import dev.starcore.starcore.foundation.animation.ParticleEffectManager;
import dev.starcore.starcore.foundation.animation.SoundFeedbackManager;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.module.army.battle.BattleCalculator;
import dev.starcore.starcore.module.army.command.ArmyCommand;
import dev.starcore.starcore.module.army.gui.ArmyMenuListener;
import dev.starcore.starcore.module.army.gui.BattleResultHandler;
import dev.starcore.starcore.module.army.integration.WarArmyIntegration;
import dev.starcore.starcore.module.army.listener.ArmyInvasionListener;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.module.war.WarService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

/**
 * 军队模块
 * 提供军队创建、管理、战斗等功能
 */
public final class ArmyModule implements StarCoreModule {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "army",
        "军队系统",
        ModuleLayer.MODULE,
        List.of("nation", "treasury"),  // 依赖国家模块和金库模块
        List.of(ArmyService.class),
        "Provides army creation, management and battle system."
    );

    private Plugin plugin;
    private NationService nationService;
    private TreasuryService treasuryService;
    private EconomyService economyService;
    private MessageService messages;
    private PersistenceService persistenceService;

    private ArmyService armyService;
    private BattleCalculator battleCalculator;
    private ArmyMenuListener menuListener;
    private ArmyInvasionListener invasionListener;
    private BattleResultHandler battleResultHandler;
    private WarArmyIntegration warIntegration;
    private Optional<DiplomacyService> diplomacyService;
    private Optional<WarService> warService;

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
        this.warService = context.serviceRegistry().find(WarService.class);

        // 初始化战斗计算器
        battleCalculator = new BattleCalculator();

        // 从配置读取军队配置
        ConfigurationSection config = context.plugin().getConfig().getConfigurationSection("army");
        ArmyService.ArmyConfig armyConfig = ArmyService.ArmyConfig.fromConfig(config);

        // 初始化军队服务（传入持久化服务）
        armyService = new ArmyService(
            plugin,
            nationService,
            treasuryService,
            battleCalculator,
            messages,
            armyConfig,
            persistenceService
        );

        // 注册 ArmyService 到服务注册表
        context.serviceRegistry().register(ArmyService.class, armyService);

        // 初始化战斗结果处理器（获取动画管理器）
        GuiAnimationRegistry animRegistry = GuiAnimationRegistry.getInstance();
        battleResultHandler = new BattleResultHandler(
            armyService,
            nationService,
            messages,
            animRegistry.getParticleEffectManager(),
            animRegistry.getSoundFeedbackManager(),
            plugin
        );

        // 初始化战争-军队集成器（如果战争模块可用）
        if (warService.isPresent() && warService.get() != null) {
            warIntegration = new WarArmyIntegration(
                plugin,
                armyService,
                nationService,
                warService.get(),
                battleResultHandler,
                messages
            );
        }

        // 注册命令
        ArmyCommand command = new ArmyCommand(armyService, nationService, messages);
        var armyCmd = plugin.getServer().getPluginCommand("army");
        if (armyCmd != null) {
            armyCmd.setExecutor(command);
            armyCmd.setTabCompleter(command);
        }

        // 注册 GUI 监听器（传入所有必要服务）
        TerritoryService territorySvc = context.serviceRegistry()
            .find(TerritoryService.class).orElse(null);
        menuListener = new ArmyMenuListener(
            armyService,
            nationService,
            diplomacyService.orElse(null),
            warService.orElse(null),
            territorySvc,
            messages
        );
        plugin.getServer().getPluginManager().registerEvents(menuListener, plugin);

        // 注册入侵监听器（可选依赖外交模块）
        invasionListener = new ArmyInvasionListener(
            armyService,
            nationService,
            diplomacyService,
            messages
        );
        plugin.getServer().getPluginManager().registerEvents(invasionListener, plugin);
    }

    @Override
    public void disable(StarCoreContext context) {
        // 清理资源
        if (armyService != null) {
            // 保存所有军队状态到数据库
            armyService.shutdown();
            armyService = null;
        }

        if (menuListener != null) {
            menuListener = null;
        }

        if (warIntegration != null) {
            warIntegration = null;
        }

        if (battleResultHandler != null) {
            battleResultHandler.cleanup();
            battleResultHandler = null;
        }

        battleCalculator = null;
    }

    public ArmyService getArmyService() {
        return armyService;
    }

    public BattleCalculator getBattleCalculator() {
        return battleCalculator;
    }

    public ArmyMenuListener getMenuListener() {
        return menuListener;
    }

    public BattleResultHandler getBattleResultHandler() {
        return battleResultHandler;
    }

    public WarArmyIntegration getWarIntegration() {
        return warIntegration;
    }
}
