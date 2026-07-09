package dev.starcore.starcore.module.army.mercenary;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.mercenary.command.MercenaryCommand;
import dev.starcore.starcore.module.army.mercenary.listener.MercenaryListener;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

/**
 * 雇佣兵模块
 * 提供雇佣兵系统：雇佣兵注册、合同管理、战斗记录等
 */
public final class MercenaryModule implements StarCoreModule {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "mercenary",
        "雇佣兵系统",
        ModuleLayer.MODULE,
        List.of("nation", "treasury"),  // 依赖国家模块和金库模块
        List.of(MercenaryService.class),
        "Provides mercenary recruitment, contract management, and combat tracking."
    );

    private Plugin plugin;
    private NationService nationService;
    private TreasuryService treasuryService;
    private EconomyService economyService;
    private MessageService messages;
    private PersistenceService persistenceService;
    private Optional<DatabaseService> databaseService;

    private MercenaryService mercenaryService;

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
        this.databaseService = context.serviceRegistry().find(DatabaseService.class);

        // 初始化数据库表
        initializeDatabase();

        // 初始化雇佣兵服务
        mercenaryService = new MercenaryService(
            plugin,
            nationService,
            treasuryService,
            economyService,
            messages,
            persistenceService,
            databaseService
        );

        // 注册服务
        context.serviceRegistry().register(MercenaryService.class, mercenaryService);

        // 注册命令
        registerCommands(context);

        // 注册监听器
        registerListeners(context);

        plugin.getLogger().info("Mercenary module enabled");
    }

    private void initializeDatabase() {
        databaseService.ifPresent(ds -> {
            ds.dataSource().ifPresent(dataSource -> {
                try {
                    var conn = dataSource.getConnection();
                    String sql = """
                        CREATE TABLE IF NOT EXISTS mercenary_contracts (
                            contract_id TEXT PRIMARY KEY,
                            mercenary_id TEXT NOT NULL,
                            employer_id TEXT NOT NULL,
                            nation_id TEXT NOT NULL,
                            type TEXT NOT NULL,
                            rank TEXT NOT NULL,
                            experience INTEGER DEFAULT 0,
                            kills INTEGER DEFAULT 0,
                            deaths INTEGER DEFAULT 0,
                            missions_completed INTEGER DEFAULT 0,
                            salary INTEGER DEFAULT 0,
                            hired_at INTEGER NOT NULL,
                            expires_at INTEGER,
                            status TEXT NOT NULL,
                            last_location TEXT,
                            last_active INTEGER NOT NULL,
                            INDEX idx_mercenary_id (mercenary_id),
                            INDEX idx_employer_id (employer_id),
                            INDEX idx_nation_id (nation_id),
                            INDEX idx_status (status)
                        )
                        """;
                    conn.createStatement().execute(sql);
                    conn.close();
                    plugin.getLogger().info("Mercenary contracts table initialized");
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to initialize mercenary table: " + e.getMessage());
                }
            });
        });
    }

    private void registerCommands(StarCoreContext context) {
        MercenaryCommand command = new MercenaryCommand(
            mercenaryService,
            nationService,
            economyService,
            messages
        );

        var mercenaryCmd = plugin.getServer().getPluginCommand("mercenary");
        if (mercenaryCmd != null) {
            mercenaryCmd.setExecutor(command);
            mercenaryCmd.setTabCompleter(command);
        }

        var mercCmd = plugin.getServer().getPluginCommand("merc");
        if (mercCmd != null) {
            mercCmd.setExecutor(command);
            mercCmd.setTabCompleter(command);
        }

        plugin.getLogger().info("Mercenary commands registered: /mercenary, /merc");
    }

    private void registerListeners(StarCoreContext context) {
        MercenaryListener listener = new MercenaryListener(
            mercenaryService,
            nationService,
            messages
        );
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        plugin.getLogger().info("Mercenary listeners registered");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存所有数据
        if (mercenaryService != null) {
            mercenaryService.shutdown();
        }

        // 清理引用
        mercenaryService = null;

        plugin.getLogger().info("Mercenary module disabled");
    }

    public MercenaryService getMercenaryService() {
        return mercenaryService;
    }
}