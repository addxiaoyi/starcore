package dev.starcore.starcore.quest;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.mechanics.ReputationService;
import dev.starcore.starcore.quest.command.CommissionCommand;
import dev.starcore.starcore.quest.command.DailyQuestCommand;
import dev.starcore.starcore.quest.command.QuestCommand;
import dev.starcore.starcore.quest.gui.QuestGuiCommand;
import dev.starcore.starcore.quest.gui.QuestMenu;
import dev.starcore.starcore.quest.gui.QuestMenuListener;
import dev.starcore.starcore.title.TitleService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Calendar;
import java.util.List;

/**
 * 任务模块：统一持有任务/每日任务/委托服务的共享实例，
 * 注册命令、事件监听器，并提供每日刷新调度。
 */
public final class QuestModule implements StarCoreModule {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "quest",
        "任务系统",
        ModuleLayer.FEATURE,
        List.of(),
        List.of(QuestService.class, DailyQuestService.class, CommissionService.class),
        "任务/每日任务/委托系统（数据持久化）"
    );

    private JavaPlugin plugin;
    private StarCoreContext context; // 保存 context 引用供命令注册使用
    private PersistenceService persistenceService;
    private StarCoreScheduler scheduler;

    // 核心服务实例
    private QuestService questService;
    private DailyQuestService dailyQuestService;
    private CommissionService commissionService;

    // 事件监听器
    private QuestProgressService questProgressService;
    private CommissionCompleteListener commissionCompleteListener;

    // 命令实例
    private QuestCommand questCommand;
    private DailyQuestCommand dailyQuestCommand;
    private CommissionCommand commissionCommand;
    private QuestGuiCommand questGuiCommand;

    // GUI实例
    private QuestMenu questMenu;
    private QuestMenuListener questMenuListener;

    // 调度任务
    private BukkitTask dailyRefreshTask;
    private BukkitTask autoSaveTask;

    // 每日刷新配置
    private int refreshHour = 4; // 默认凌晨4点刷新

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    // ==================== 服务获取器 ====================

    public QuestService getQuestService() {
        return questService;
    }

    public DailyQuestService getDailyQuestService() {
        return dailyQuestService;
    }

    public CommissionService getCommissionService() {
        return commissionService;
    }

    // ==================== 模块生命周期 ====================

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = (JavaPlugin) context.plugin();
        this.context = context; // 保存 context 供命令注册使用
        this.persistenceService = context.persistenceService();
        this.scheduler = context.scheduler();

        plugin.getLogger().info("正在初始化任务模块...");

        // 1. 初始化核心服务
        initializeServices(context);

        // 2. 初始化持久化
        initializePersistence();

        // 3. 注册事件监听器
        registerListeners();

        // 4. 注册命令
        registerCommands();

        // 5. 注册定时任务
        registerScheduledTasks();

        // 6. 注册服务到 ServiceRegistry
        registerServices(context);

        plugin.getLogger().info("任务模块已启用（任务/每日任务/委托）");
    }

    @Override
    public void disable(StarCoreContext context) {
        plugin.getLogger().info("正在关闭任务模块...");

        // 1. 取消定时任务
        cancelScheduledTasks();

        // 2. 注销事件监听器
        unregisterListeners();

        // 3. 保存所有数据
        saveAllData();

        // 4. 注销服务
        unregisterServices(context);

        plugin.getLogger().info("任务模块已禁用，所有数据已保存");
    }

    // ==================== 服务初始化 ====================

    private void initializeServices(StarCoreContext context) {
        // 获取依赖服务
        EconomyService economyService = context.economyService();
        ReputationService reputationService = context.serviceRegistry()
            .find(ReputationService.class).orElse(null);
        TitleService titleService = context.serviceRegistry()
            .find(TitleService.class).orElse(null);

        // 创建核心服务实例
        this.questService = new QuestService(
            economyService,
            reputationService,
            titleService,
            persistenceService,
            plugin.getLogger()
        );

        this.dailyQuestService = new DailyQuestService(
            questService,
            economyService,
            persistenceService,
            plugin.getLogger()
        );

        this.commissionService = new CommissionService(
            questService,
            economyService,
            persistenceService,
            reputationService,
            plugin.getLogger()
        );

        // 创建事件监听器
        this.questProgressService = new QuestProgressService(questService);
        this.commissionCompleteListener = new CommissionCompleteListener(commissionService);

        // 创建GUI相关实例（在注册监听器之前）
        this.questMenu = new QuestMenu(questService, dailyQuestService, commissionService);
        this.questMenuListener = new QuestMenuListener(questMenu);

        plugin.getLogger().info("任务服务实例已创建");
    }

    private void initializePersistence() {
        // 初始化持久化处理
        questService.initialize();
        dailyQuestService.initialize();
        commissionService.initialize();

        // 启动委托服务定时任务
        commissionService.setScheduler(plugin);

        plugin.getLogger().info("任务持久化已初始化");
    }

    // ==================== 事件监听器 ====================

    private void registerListeners() {
        // 注册任务进度监听器
        plugin.getServer().getPluginManager().registerEvents(questProgressService, plugin);
        plugin.getLogger().info("任务进度监听器已注册");

        // 注册委托完成监听器
        plugin.getServer().getPluginManager().registerEvents(commissionCompleteListener, plugin);
        plugin.getLogger().info("委托完成监听器已注册");

        // 注册任务GUI监听器
        plugin.getServer().getPluginManager().registerEvents(questMenuListener, plugin);
        plugin.getLogger().info("任务GUI监听器已注册");
    }

    private void unregisterListeners() {
        // 注销所有事件监听器
        if (questProgressService != null) {
            HandlerList.unregisterAll(questProgressService);
            questProgressService = null;
        }
        if (commissionCompleteListener != null) {
            HandlerList.unregisterAll(commissionCompleteListener);
            commissionCompleteListener = null;
        }
    }

    // ==================== 命令注册 ====================

    private void registerCommands() {
        // 获取依赖服务
        EconomyService economyService = context.economyService();
        ReputationService reputationService = context.serviceRegistry()
            .find(ReputationService.class).orElse(null);

        // 创建命令实例
        this.questCommand = new QuestCommand(
            questService,
            dailyQuestService,
            commissionService,
            null // OnlinePlayerDirectory 如果需要可从 context 获取
        );

        this.dailyQuestCommand = new DailyQuestCommand(
            dailyQuestService,
            questService
        );

        this.commissionCommand = new CommissionCommand(
            commissionService,
            questService,
            economyService,
            reputationService
        );

        this.questGuiCommand = new QuestGuiCommand(questService, dailyQuestService, commissionService);
        this.questGuiCommand.setQuestMenu(questMenu);

        // 绑定命令
        bind("quest", questCommand);
        bind("dailyquest", dailyQuestCommand);
        bind("daily", dailyQuestCommand);
        bind("commission", commissionCommand);
        bind("委托", commissionCommand);
        bind("questgui", questGuiCommand);
        bind("任务", questGuiCommand);

        plugin.getLogger().info("任务相关命令已注册");
    }

    private void bind(String name, CommandExecutor executor) {
        PluginCommand cmd = plugin.getServer().getPluginCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            if (executor instanceof TabCompleter tc) {
                cmd.setTabCompleter(tc);
            }
        } else {
            plugin.getLogger().warning("命令未找到: " + name);
        }
    }

    // ==================== 定时任务 ====================

    private void registerScheduledTasks() {
        // 每日任务刷新调度 - 每天在指定时间检查并刷新
        scheduleDailyRefresh();

        // 自动保存调度 - 每5分钟保存一次数据
        scheduleAutoSave();

        plugin.getLogger().info("定时任务已注册（每日刷新 + 自动保存）");
    }

    /**
     * 调度每日任务刷新
     * 使用较短的检查间隔（每5分钟），在接近刷新时间时更频繁检查
     */
    private void scheduleDailyRefresh() {
        // 每5分钟检查一次是否需要刷新
        long checkInterval = 5 * 60 * 20L; // 5分钟（tick）

        dailyRefreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                Calendar now = Calendar.getInstance();
                int currentHour = now.get(Calendar.HOUR_OF_DAY);

                // 检查是否到达刷新时间
                if (currentHour == refreshHour) {
                    // 获取上次的刷新日期
                    Long lastRefresh = getLastDailyRefreshDate();
                    Long today = getTodayDate();

                    if (lastRefresh == null || !lastRefresh.equals(today)) {
                        plugin.getLogger().info("执行每日任务自动刷新...");
                        dailyQuestService.refreshAllDailyQuests();
                        setLastDailyRefreshDate(today);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("每日任务刷新检查失败: " + e.getMessage());
            }
        }, 20L, checkInterval); // 延迟1秒后开始，之后每5分钟执行
    }

    /**
     * 调度自动保存
     * 每5分钟保存一次任务进度数据
     */
    private void scheduleAutoSave() {
        long saveInterval = 5 * 60 * 20L; // 5分钟（tick）

        autoSaveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                questService.saveAsync();
                dailyQuestService.saveAsync();
                commissionService.saveAsync();
            } catch (Exception e) {
                plugin.getLogger().warning("自动保存失败: " + e.getMessage());
            }
        }, saveInterval, saveInterval); // 延迟5分钟后开始，之后每5分钟执行
    }

    private void cancelScheduledTasks() {
        if (dailyRefreshTask != null) {
            dailyRefreshTask.cancel();
            dailyRefreshTask = null;
        }
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        // 关闭委托服务定时任务
        if (commissionService != null) {
            commissionService.shutdown();
        }
    }

    // ==================== 数据持久化 ====================

    private void saveAllData() {
        try {
            questService.save();
            dailyQuestService.save();
            commissionService.save();
            plugin.getLogger().info("任务数据已保存");
        } catch (Exception e) {
            plugin.getLogger().warning("保存任务数据失败: " + e.getMessage());
        }
    }

    // ==================== 服务注册 ====================

    private void registerServices(StarCoreContext context) {
        context.serviceRegistry().register(QuestService.class, questService);
        context.serviceRegistry().register(DailyQuestService.class, dailyQuestService);
        context.serviceRegistry().register(CommissionService.class, commissionService);

        plugin.getLogger().info("任务服务已注册到 ServiceRegistry");
    }

    private void unregisterServices(StarCoreContext context) {
        // 注意: ServiceRegistry 不支持 unregister，保留服务实例用于可能的数据访问
        // 服务会在模块禁用时保存数据并停止调度任务
        plugin.getLogger().info("任务服务保留在 ServiceRegistry 中（模块禁用）");
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取今日日期（天级别）
     */
    private Long getTodayDate() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * 获取上次每日刷新日期
     */
    private Long getLastDailyRefreshDate() {
        try {
            File file = new File(plugin.getDataFolder(), "quest/last_daily_refresh.txt");
            if (file.exists()) {
                String content = Files.readString(file.toPath());
                return Long.parseLong(content.trim());
            }
        } catch (NumberFormatException e) {
            // 文件内容格式错误，忽略并返回null
        } catch (IOException e) {
            // 文件读取失败，忽略并返回null
        }
        return null;
    }

    /**
     * 设置上次每日刷新日期
     */
    private void setLastDailyRefreshDate(Long date) {
        try {
            File file = new File(plugin.getDataFolder(), "quest/last_daily_refresh.txt");
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), date.toString());
        } catch (IOException e) {
            // 文件写入失败，静默忽略
        }
    }

    // ==================== 管理命令 ====================

    /**
     * 手动刷新每日任务（供管理员使用）
     */
    public void manualRefreshDailyQuests() {
        dailyQuestService.refreshAllDailyQuests();
    }

    /**
     * 获取模块摘要
     */
    @Override
    public String toString() {
        return "QuestModule{" +
            "activeQuests=" + (questService != null ? questService.getQuestRegistry().size() : 0) +
            "}";
    }
}
