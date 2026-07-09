package dev.starcore.starcore.module.army.doctrine;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.doctrine.model.DoctrineType;
import dev.starcore.starcore.module.army.doctrine.model.NationDoctrine;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 军事学说模块
 * 提供国家军事学说的选择和管理功能
 */
public final class DoctrineModule implements StarCoreModule {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "doctrine",
        "军事学说",
        ModuleLayer.MODULE,
        List.of("nation", "army"),  // 依赖国家模块和军队模块
        List.of(DoctrineService.class),
        "Provides military doctrine selection and combat bonuses."
    );

    private Plugin plugin;
    private NationService nationService;
    private TreasuryService treasuryService;
    private MessageService messages;

    private DoctrineService doctrineService;
    private DoctrineCommand doctrineCommand;
    private DoctrineListener doctrineListener;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.treasuryService = context.serviceRegistry().require(TreasuryService.class);
        this.messages = context.serviceRegistry().require(MessageService.class);

        // 从配置读取学说配置
        ConfigurationSection config = context.plugin().getConfig().getConfigurationSection("doctrine");

        // 初始化学说服务
        doctrineService = new DoctrineServiceImpl(config);

        // 注册服务
        context.serviceRegistry().register(DoctrineService.class, doctrineService);

        // 注册命令
        doctrineCommand = new DoctrineCommand(doctrineService, nationService, treasuryService, messages);
        var cmd = plugin.getServer().getPluginCommand("doctrine");
        if (cmd != null) {
            cmd.setExecutor(doctrineCommand);
            cmd.setTabCompleter(doctrineCommand);
        }

        // 注册别名命令
        registerAliasCommand("doctrine", "doc");

        // 注册事件监听器
        doctrineListener = new DoctrineListener(doctrineService, nationService, messages);
        plugin.getServer().getPluginManager().registerEvents(doctrineListener, plugin);

        // 注册学说变更事件监听（影响军队战斗力）
        doctrineService.onDoctrineChanged(event -> {
            Bukkit.getPluginManager().callEvent(event);
        });

        plugin.getLogger().info("Doctrine module enabled: " + METADATA.displayName());
    }

    /**
     * 注册别名命令
     */
    private void registerAliasCommand(String primary, String alias) {
        var aliasCmd = plugin.getServer().getPluginCommand(alias);
        if (aliasCmd != null) {
            aliasCmd.setExecutor(doctrineCommand);
            aliasCmd.setTabCompleter(doctrineCommand);
        }
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存所有学说数据
        if (doctrineService != null) {
            doctrineService.saveAllDoctrines();
        }

        // 清理引用
        doctrineService = null;
        doctrineCommand = null;
        doctrineListener = null;

        context.plugin().getLogger().info("Doctrine module disabled");
    }

    public DoctrineService getDoctrineService() {
        return doctrineService;
    }
}