package dev.starcore.starcore.module.army.fatigue;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.fatigue.command.FatigueCommand;
import dev.starcore.starcore.module.army.fatigue.listener.FatigueListener;
import dev.starcore.starcore.module.army.fatigue.FatigueConfig;
import dev.starcore.starcore.module.army.fatigue.storage.FatigueStateCodec;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

/**
 * 疲劳度模块
 * 管理玩家的疲劳度系统，影响战斗力和恢复能力
 *
 * 疲劳度机制：
 * - 玩家通过战斗、采集、建造等活动积累疲劳度
 * - 高疲劳度降低战斗力、移动速度和恢复能力
 * - 疲劳度可以通过休息、进食特定食物、使用道具恢复
 * - 长时间不休息会导致严重的负面效果
 */
public final class FatigueModule implements StarCoreModule {
    private static final String PERSISTENCE_NAMESPACE = "fatigue";
    private static final String FATIGUE_STATE_FILE = "fatigue.dat";

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "fatigue",
        "疲劳度系统",
        ModuleLayer.MODULE,
        List.of(),  // 无强制依赖
        List.of(FatigueService.class),
        "Manages player fatigue affecting combat effectiveness and recovery."
    );

    private Plugin plugin;
    private FatigueService fatigueService;
    private FatigueListener fatigueListener;
    private FatigueConfig config;
    private FatigueStateCodec stateCodec;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        this.stateCodec = new FatigueStateCodec();

        // 从配置读取疲劳度配置
        org.bukkit.configuration.ConfigurationSection fatigueSection =
            context.plugin().getConfig().getConfigurationSection("fatigue");
        this.config = FatigueConfig.fromConfig(fatigueSection);

        // 初始化疲劳度服务
        MessageService messageService = context.serviceRegistry().require(MessageService.class);
        this.fatigueService = new FatigueService(
            plugin,
            messageService,
            context.persistenceService(),
            context.eventBus(),
            config
        );

        // 注册命令
        FatigueCommand command = new FatigueCommand(fatigueService, messageService);
        var fatigueCmd = plugin.getServer().getPluginCommand("fatigue");
        if (fatigueCmd != null) {
            fatigueCmd.setExecutor(command);
            fatigueCmd.setTabCompleter(command);
        }

        // 注册事件监听器
        this.fatigueListener = new FatigueListener(fatigueService, config);
        plugin.getServer().getPluginManager().registerEvents(fatigueListener, plugin);

        plugin.getLogger().info("Fatigue module enabled");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存所有疲劳度数据
        if (fatigueService != null) {
            fatigueService.saveAll();
        }

        this.fatigueService = null;
        this.fatigueListener = null;
        this.config = null;

        context.plugin().getLogger().info("Fatigue module disabled");
    }

    public FatigueService getFatigueService() {
        return fatigueService;
    }

    public FatigueConfig getConfig() {
        return config;
    }
}
