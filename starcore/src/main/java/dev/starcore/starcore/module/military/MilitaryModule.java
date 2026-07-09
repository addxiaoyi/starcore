package dev.starcore.starcore.module.military;

import dev.starcore.starcore.StarCorePlugin;
import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.module.army.ArmyModule;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService;
import dev.starcore.starcore.module.military.gui.BattleStatusMenu;
import dev.starcore.starcore.module.military.gui.BattleStatusMenuListener;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.module.war.situation.WarSituationService;
import dev.starcore.starcore.war.BattlefieldService;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * 军事综合模块
 * 整合战争、军队、战场、军事联盟等功能
 * 提供统一的战况实时预览界面
 */
public class MilitaryModule implements StarCoreModule {

    // 模块元数据
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "military",
        "Military Command Center",
        ModuleLayer.MODULE,
        List.of(),
        List.of(),
        "Unified military command center with real-time battle status preview"
    );

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    private StarCorePlugin plugin;
    private BattleStatusMenu battleStatusMenu;
    private BattleStatusMenuListener battleStatusMenuListener;

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = (StarCorePlugin) context.plugin();

        // 获取所需服务
        WarService warService = context.serviceRegistry()
            .find(WarService.class).orElse(null);

        ArmyService armyService = context.serviceRegistry()
            .find(ArmyService.class).orElse(null);

        // 获取 BattlefieldService（需要从 WarServiceImpl 获取）
        BattlefieldService battlefieldService = findBattlefieldService(context);

        NationService nationService = context.serviceRegistry()
            .find(NationService.class).orElse(null);

        MilitaryAllianceService allianceService = context.serviceRegistry()
            .find(MilitaryAllianceService.class).orElse(null);

        // 获取 WarSituationService
        WarSituationService situationService = context.serviceRegistry()
            .find(WarSituationService.class).orElse(null);

        // 创建战况预览菜单（注入依赖）
        this.battleStatusMenu = new BattleStatusMenu(
            warService,
            armyService,
            nationService,
            situationService
        );

        // 创建监听器
        this.battleStatusMenuListener = new BattleStatusMenuListener(battleStatusMenu);

        // 注册事件监听器
        plugin.getServer().getPluginManager().registerEvents(
            battleStatusMenuListener,
            plugin
        );

        // 注册命令
        registerCommands();

        plugin.getLogger().info("[MilitaryModule] 军事指挥中心模块已启用");
        plugin.getLogger().info("[MilitaryModule] 使用 /military 或 /battle 打开战况预览");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 停止所有自动刷新任务
        if (battleStatusMenuListener != null) {
            battleStatusMenuListener.stopAllRefresh();
        }

        plugin.getLogger().info("[MilitaryModule] 军事指挥中心模块已禁用");
    }

    private BattlefieldService findBattlefieldService(StarCoreContext context) {
        // 尝试从 serviceRegistry 获取
        var bfService = context.serviceRegistry().find(BattlefieldService.class).orElse(null);
        if (bfService != null) {
            return bfService;
        }

        // 如果没有直接注册，尝试从 WarService 获取
        WarService warService = context.serviceRegistry().find(WarService.class).orElse(null);
        if (warService != null) {
            try {
                java.lang.reflect.Method method = warService.getClass().getMethod("getBattlefieldService");
                Object result = method.invoke(warService);
                if (result instanceof BattlefieldService) {
                    return (BattlefieldService) result;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[MilitaryModule] 通过反射获取 BattlefieldService 失败: " + e.getMessage());
            }
                        // 静默跳过，保持数据兼容
        }

        return null;
    }

    private void registerCommands() {
        // 获取 NationService
        NationService nationService = plugin != null ?
            plugin.context().serviceRegistry().find(NationService.class).orElse(null) : null;

        // 注册主命令
        MilitaryCommand mainCommand = new MilitaryCommand(this, nationService);
        registerCommand("military", mainCommand);

        // 注册别名命令
        registerCommand("battle", mainCommand);
        registerCommand("battlestatus", mainCommand);
        registerCommand("warstatus", mainCommand);
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        org.bukkit.command.PluginCommand cmd = plugin.getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            if (executor instanceof org.bukkit.command.TabCompleter completer) {
                cmd.setTabCompleter(completer);
            }
        }
    }

    // Getter for commands
    public BattleStatusMenu getBattleStatusMenu() {
        return battleStatusMenu;
    }

    public BattleStatusMenuListener getBattleStatusMenuListener() {
        return battleStatusMenuListener;
    }
}