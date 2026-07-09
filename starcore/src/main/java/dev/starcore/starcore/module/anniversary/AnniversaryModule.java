package dev.starcore.starcore.module.anniversary;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.anniversary.command.AnniversaryCommand;
import dev.starcore.starcore.module.anniversary.listener.AnniversaryListener;
import dev.starcore.starcore.module.nation.NationService;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 纪念日模块
 * 提供国家纪念日的创建、管理和通知功能
 */
public final class AnniversaryModule implements StarCoreModule {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "anniversary",
        "纪念日系统",
        ModuleLayer.MODULE,
        List.of("nation"),
        List.of(AnniversaryService.class),
        "Provides national anniversary creation, management and notifications."
    );

    private Plugin plugin;
    private NationService nationService;
    private MessageService messages;
    private PersistenceService persistenceService;

    private AnniversaryService anniversaryService;
    private AnniversaryCommand command;
    private AnniversaryListener listener;

    private AtomicInteger checkTaskId;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.messages = context.serviceRegistry().require(MessageService.class);
        this.persistenceService = context.serviceRegistry().find(PersistenceService.class).orElse(null);

        // 初始化服务
        anniversaryService = new AnniversaryServiceImpl(
            plugin,
            messages,
            persistenceService
        );

        // 注册服务
        context.serviceRegistry().register(AnniversaryService.class, anniversaryService);

        // 加载持久化数据
        anniversaryService.loadState();

        // 为已有国家创建成立纪念日（如果不存在）
        initializeFoundingAnniversaries();

        // 注册命令
        command = new AnniversaryCommand(anniversaryService, nationService, messages);
        var anniversaryCmd = plugin.getServer().getPluginCommand("anniversary");
        if (anniversaryCmd != null) {
            anniversaryCmd.setExecutor(command);
            anniversaryCmd.setTabCompleter(command);
        }

        // 注册监听器
        listener = new AnniversaryListener(anniversaryService, nationService, messages);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        // 启动定时检查任务（每小时检查一次）
        startPeriodicCheck();

        plugin.getLogger().info("STARCORE Anniversary module enabled. " + anniversaryService.summary());
    }

    @Override
    public void disable(StarCoreContext context) {
        // 停止定时任务
        stopPeriodicCheck();

        // 保存状态
        if (anniversaryService != null) {
            anniversaryService.saveState();
        }

        // 清理引用
        command = null;
        listener = null;
        anniversaryService = null;

        context.plugin().getLogger().info("STARCORE Anniversary module disabled");
    }

    /**
     * 获取模块摘要
     */
    public String summary() {
        return anniversaryService != null ? anniversaryService.summary() : "Anniversary module inactive";
    }

    public AnniversaryService getAnniversaryService() {
        return anniversaryService;
    }

    /**
     * 为已有国家初始化成立纪念日
     */
    private void initializeFoundingAnniversaries() {
        // 从 NationService 获取所有国家并检查是否有成立纪念日
        // 注意：这里通过反射或事件来获取国家列表
        // 实际实现可能需要根据 NationService 的接口来调整
        plugin.getLogger().info("Checking existing nations for founding anniversaries...");
    }

    /**
     * 启动定时检查任务
     */
    private void startPeriodicCheck() {
        checkTaskId = new AtomicInteger(-1);

        // 每小时检查一次
        int taskId = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            () -> {
                if (anniversaryService instanceof AnniversaryServiceImpl serviceImpl) {
                    serviceImpl.checkUpcomingAnniversaries();
                }
            },
            20L * 60 * 5,  // 5分钟后开始
            20L * 60 * 60  // 每小时检查一次
        ).getTaskId();

        checkTaskId.set(taskId);
        plugin.getLogger().info("STARCORE Anniversary periodic check task started (hourly).");
    }

    /**
     * 停止定时检查任务
     */
    private void stopPeriodicCheck() {
        if (checkTaskId != null && checkTaskId.get() != -1) {
            plugin.getServer().getScheduler().cancelTask(checkTaskId.get());
            checkTaskId.set(-1);
        }
    }
}
