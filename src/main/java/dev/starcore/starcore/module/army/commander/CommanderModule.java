package dev.starcore.starcore.module.army.commander;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.commander.command.CommanderCommand;
import dev.starcore.starcore.module.army.commander.listener.CommanderListener;
import dev.starcore.starcore.module.nation.NationService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

/**
 * 指挥官技能模块
 * 提供指挥官等级、技能树和战场指挥能力
 */
public final class CommanderModule implements StarCoreModule {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "commander",
        "指挥官系统",
        ModuleLayer.MODULE,
        List.of("nation", "army"),  // 依赖国家模块和军队模块
        List.of(CommanderService.class),
        "Provides commander skills and leveling system."
    );

    private Plugin plugin;
    private NationService nationService;
    private ArmyService armyService;
    private MessageService messages;

    private CommanderService commanderService;
    private CommanderCommand command;
    private CommanderListener listener;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.armyService = context.serviceRegistry().require(ArmyService.class);
        this.messages = context.serviceRegistry().require(MessageService.class);

        // 从配置读取指挥官配置
        ConfigurationSection configSection = context.plugin().getConfig().getConfigurationSection("commander");
        CommanderConfig commanderConfig = CommanderConfig.fromConfig(configSection);

        // 检查是否启用
        if (!commanderConfig.enabled()) {
            plugin.getLogger().info("Commander module is disabled in config");
            return;
        }

        // 初始化指挥官服务
        commanderService = new CommanderServiceImpl(
            plugin,
            nationService,
            armyService,
            messages,
            commanderConfig
        );

        // 注册命令
        command = new CommanderCommand(
            commanderService,
            nationService,
            armyService,
            messages,
            commanderConfig
        );
        var commanderCmd = plugin.getServer().getPluginCommand("commander");
        if (commanderCmd != null) {
            commanderCmd.setExecutor(command);
            commanderCmd.setTabCompleter(command);
        }

        // 注册事件监听器
        listener = new CommanderListener(
            commanderService,
            armyService,
            nationService,
            messages,
            commanderConfig
        );
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        plugin.getLogger().info("Commander module enabled");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存所有玩家数据
        if (commanderService != null) {
            plugin.getLogger().info("Saving commander data...");
            // 保存逻辑在 shutdown 中处理
        }

        commanderService = null;
        command = null;
        listener = null;
    }

    /**
     * 获取指挥官服务
     */
    public Optional<CommanderService> getCommanderService() {
        return Optional.ofNullable(commanderService);
    }
}
