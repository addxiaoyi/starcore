package dev.starcore.starcore.module.combat;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.protection.ExternalProtectionService;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.combat.command.CombatCommand;
import dev.starcore.starcore.module.combat.config.CombatConfig;
import dev.starcore.starcore.module.combat.gui.BattleGui;
import dev.starcore.starcore.module.combat.gui.CombatGui;
import dev.starcore.starcore.module.combat.listener.CombatListener;
import dev.starcore.starcore.module.combat.listener.CombatGuiListener;
import dev.starcore.starcore.module.combat.storage.CombatStorage;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.war.WarService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;

/**
 * 实时战斗系统模块
 * 提供玩家战斗状态追踪、战斗标记、伤害记录、战场管理、国家对抗战等功能
 */
public final class CombatModule implements StarCoreModule {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "combat",
        "实时战斗系统",
        ModuleLayer.MODULE,
        List.of("nation", "army"),
        List.of(CombatService.class),
        "Real-time combat system with combat tags, damage tracking, battlefield management, and nation-vs-nation battles."
    );

    private JavaPlugin plugin;
    private CombatService combatService;
    private CombatConfig combatConfig;
    private CombatStorage combatStorage;
    private CombatListener combatListener;
    private CombatGui combatGui;
    private BattleGui battleGui;
    private CombatGuiListener combatGuiListener;
    private CombatCommand combatCommand;

    private Optional<NationService> nationService;
    private Optional<DiplomacyService> diplomacyService;
    private Optional<WarService> warService;
    private Optional<ArmyService> armyService;
    private Optional<ExternalProtectionService> externalProtectionService;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();

        // 获取依赖服务
        this.nationService = context.serviceRegistry().find(NationService.class);
        this.diplomacyService = context.serviceRegistry().find(DiplomacyService.class);
        this.warService = context.serviceRegistry().find(WarService.class);
        this.armyService = context.serviceRegistry().find(ArmyService.class);
        this.externalProtectionService = context.serviceRegistry().find(ExternalProtectionService.class);

        // 初始化配置
        this.combatConfig = new CombatConfig(context.plugin());

        // 初始化存储
        this.combatStorage = new CombatStorage(context.plugin(), context.databaseService());

        // 初始化服务实现
        this.combatService = new CombatServiceImpl(
            plugin,
            combatConfig,
            combatStorage,
            context.scheduler(),
            nationService,
            diplomacyService,
            warService,
            armyService
        );

        // 初始化事件监听器
        this.combatListener = new CombatListener(
            combatService,
            combatConfig,
            nationService,
            diplomacyService,
            warService,
            externalProtectionService
        );

        // 注册事件监听
        plugin.getServer().getPluginManager().registerEvents(combatListener, plugin);

        // 初始化GUI
        this.combatGui = new CombatGui(combatService);
        this.battleGui = new BattleGui(combatService);
        this.combatGuiListener = new CombatGuiListener(combatGui, battleGui);
        plugin.getServer().getPluginManager().registerEvents(combatGuiListener, plugin);

        // 注册命令
        registerCommands(context);

        // 注册到服务注册表
        context.serviceRegistry().register(CombatService.class, combatService);

        plugin.getLogger().info("Real-time combat system enabled with nation-vs-nation support.");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存所有战斗状态
        if (combatService != null) {
            combatService.saveAll();
            combatService.shutdown();
        }

        // 关闭存储
        if (combatStorage != null) {
            combatStorage.close();
        }

        plugin.getLogger().info("Real-time combat system disabled.");
    }

    private void registerCommands(StarCoreContext context) {
        // 从 context.plugin() 获取插件实例
        JavaPlugin pluginInstance = (JavaPlugin) context.plugin();
        PluginCommand combatCmd = pluginInstance.getCommand("combat");
        if (combatCmd == null) {
            pluginInstance.getLogger().warning("Command 'combat' not found in plugin.yml");
            return;
        }

        this.combatCommand = new CombatCommand(combatService, combatGui, battleGui, combatConfig);
        combatCmd.setExecutor(combatCommand);
        combatCmd.setTabCompleter(combatCommand);
        pluginInstance.getLogger().info("Combat command registered: /combat");

        // 注册 /battle 命令（战场管理）
        PluginCommand battleCmd = pluginInstance.getCommand("battle");
        if (battleCmd != null) {
            battleCmd.setExecutor(combatCommand);
            battleCmd.setTabCompleter(combatCommand);
            pluginInstance.getLogger().info("Battle command registered: /battle");
        }
    }

    public CombatService getCombatService() {
        return combatService;
    }

    public CombatConfig getCombatConfig() {
        return combatConfig;
    }

    public CombatListener getCombatListener() {
        return combatListener;
    }

    public BattleGui getBattleGui() {
        return battleGui;
    }
}
