package dev.starcore.starcore.module.lease;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.lease.command.LeaseCommand;
import dev.starcore.starcore.module.lease.listener.LeaseEventListener;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.Bukkit;

import java.util.List;

/**
 * 租约契约模块
 * 提供租约创建、管理、签署、终止等完整功能
 */
public final class LeaseModule implements StarCoreModule {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "lease",
        "契约制度",
        ModuleLayer.MODULE,
        List.of("nation"),  // 依赖国家模块
        List.of(LeaseService.class),
        "Provides lease contract system for territory, resources and buildings."
    );

    private StarCoreContext context;
    private LeaseService leaseService;
    private LeaseCommand leaseCommand;
    private LeaseEventListener eventListener;
    private long expirationTaskId = -1;
    private long paymentTaskId = -1;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.context = context;
        context.persistenceService().ensureNamespace(metadata().id());

        // 获取依赖服务
        DatabaseService databaseService = context.serviceRegistry().require(DatabaseService.class);
        EconomyService economyService = context.economyService();
        MessageService messages = context.serviceRegistry().require(MessageService.class);
        NationService nationService = context.serviceRegistry().require(NationService.class);
        TreasuryService treasuryService = context.serviceRegistry().find(TreasuryService.class).orElse(null);

        // 初始化服务
        leaseService = new LeaseServiceImpl(
            context.plugin(),
            databaseService,
            economyService,
            treasuryService,
            messages
        );

        // 初始化数据库表
        leaseService.initializeTables();

        // 注册服务
        context.serviceRegistry().register(LeaseService.class, leaseService);

        // 注册命令
        leaseCommand = new LeaseCommand(leaseService, nationService, messages);
        var cmd = context.plugin().getCommand("lease");
        if (cmd != null) {
            cmd.setExecutor(leaseCommand);
            if (leaseCommand instanceof org.bukkit.command.TabCompleter tabCompleter) {
                cmd.setTabCompleter(tabCompleter);
            }
        }

        // 注册事件监听器
        eventListener = new LeaseEventListener(leaseService, nationService, messages);
        context.plugin().getServer().getPluginManager().registerEvents(eventListener, context.plugin());

        // 启动定时任务
        startScheduledTasks();

        context.plugin().getLogger().info("Lease module enabled: " + leaseService.summary());
    }

    @Override
    public void disable(StarCoreContext context) {
        // 停止定时任务
        stopScheduledTasks();

        // 清理监听器
        eventListener = null;

        // 清理命令
        leaseCommand = null;

        // 清理服务
        leaseService = null;
        this.context = null;

        context.plugin().getLogger().info("Lease module disabled");
    }

    /**
     * 启动定时任务
     */
    private void startScheduledTasks() {
        StarCoreScheduler scheduler = context.scheduler();

        // 每天检查一次租约到期
        expirationTaskId = scheduler.runSyncTimer(
            () -> {
                if (leaseService != null) {
                    leaseService.processExpiredLeases();
                }
            },
            20L * 60 * 60,  // 1小时后首次执行
            20L * 60 * 60 * 24  // 每天执行一次
        ).getTaskId();

        // 每小时检查一次逾期付款
        paymentTaskId = scheduler.runSyncTimer(
            () -> {
                if (leaseService != null) {
                    leaseService.processOverduePayments();
                }
            },
            20L * 60 * 5,  // 5分钟后首次执行
            20L * 60 * 60  // 每小时执行一次
        ).getTaskId();
    }

    /**
     * 停止定时任务
     */
    private void stopScheduledTasks() {
        if (expirationTaskId != -1) {
            Bukkit.getScheduler().cancelTask((int) expirationTaskId);
            expirationTaskId = -1L;
        }
        if (paymentTaskId != -1) {
            Bukkit.getScheduler().cancelTask((int) paymentTaskId);
            paymentTaskId = -1L;
        }
    }

    public LeaseService getLeaseService() {
        return leaseService;
    }
}
