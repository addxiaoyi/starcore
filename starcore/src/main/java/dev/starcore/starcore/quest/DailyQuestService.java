package dev.starcore.starcore.quest;

import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 每日任务服务
 * 管理每日任务的生成、刷新和分配
 *
 * @author StarCore Team
 * @since 1.0.0
 */
public class DailyQuestService {

    private static final String NAMESPACE = "quest";

    private final QuestService questService;
    private final EconomyService economyService;
    private final PersistenceService persistenceService;
    private final Logger logger;
    private final DailyQuestPool questPool;
    // D-070: 改为 ConcurrentHashMap，避免异步保存复制 + 主线程操作触发 CME
    private final Map<UUID, List<Quest>> playerDailyQuests;
    private final Map<UUID, Long> lastRefreshTime;

    private QuestPersistenceHandler persistenceHandler;

    private int dailyQuestCount = 5; // 每天提供的每日任务数量
    private boolean usePersonalizedQuests = true; // 是否为每个玩家生成个性化任务
    private long refreshHour = 4; // 刷新时间（小时，0-23）
    private double refreshCost = 100.0; // 手动刷新费用
    // D-071: 显式时区，避免 JVM 默认时区变化或跨服运维变更导致刷新边界不一致
    private java.time.ZoneId refreshZone = java.time.ZoneId.systemDefault();
    public java.time.ZoneId getRefreshZone() { return refreshZone; }
    public void setRefreshZone(java.time.ZoneId zone) { if (zone != null) this.refreshZone = zone; }

    /**
     * 构造函数
     */
    public DailyQuestService(QuestService questService, EconomyService economyService,
                             PersistenceService persistenceService, Logger logger) {
        this.questService = questService;
        this.economyService = economyService;
        this.persistenceService = persistenceService;
        this.logger = logger;
        this.questPool = new DailyQuestPool();
        this.playerDailyQuests = new java.util.concurrent.ConcurrentHashMap<>();
        this.lastRefreshTime = new java.util.concurrent.ConcurrentHashMap<>();
    }

    /**
     * 初始化每日任务池和持久化
     */
    public void initialize() {
        // 从QuestService获取所有每日任务模板
        List<Quest> dailyTemplates = questService.getQuestsByType(QuestType.DAILY);
        questPool.addTemplates(dailyTemplates);

        Bukkit.getLogger().info("[DailyQuest] 加载了 " + dailyTemplates.size() + " 个每日任务模板");

        // 初始化持久化
        if (persistenceService != null) {
            QuestStateStorage stateStorage = new PersistenceQuestStateStorage(NAMESPACE, persistenceService);
            this.persistenceHandler = new QuestPersistenceHandler(stateStorage, logger);

            // 加载已保存的每日任务数据
            persistenceHandler.loadDailyQuests(playerDailyQuests, lastRefreshTime,
                new HashMap<>(questService.getQuestRegistry()));
        }
    }

    /**
     * 持久化每日任务数据（异步）
     */
    public void saveAsync() {
        if (persistenceHandler != null) {
            persistenceHandler.saveDailyQuestsAsync(new HashMap<>(playerDailyQuests),
                new HashMap<>(lastRefreshTime));
        }
    }

    /**
     * 持久化每日任务数据（同步）
     */
    public void save() {
        if (persistenceHandler != null) {
            persistenceHandler.saveDailyQuests(new HashMap<>(playerDailyQuests),
                new HashMap<>(lastRefreshTime));
        }
    }

    /**
     * 为玩家生成今日任务
     */
    public List<Quest> generateDailyQuests(Player player) {
        UUID playerId = player.getUniqueId();

        // 检查是否需要刷新
        if (shouldRefresh(playerId)) {
            List<Quest> quests;

            if (usePersonalizedQuests) {
                // 个性化任务（根据玩家等级、偏好等）
                quests = questPool.generatePersonalizedQuests(player, dailyQuestCount);
            } else {
                // 全服统一任务
                quests = questPool.generateRandomQuests(dailyQuestCount);
            }

            playerDailyQuests.put(playerId, quests);
            lastRefreshTime.put(playerId, System.currentTimeMillis());

            // 自动保存
            saveAsync();

            return quests;
        }

        // 返回已生成的任务
        return playerDailyQuests.getOrDefault(playerId, Collections.emptyList());
    }

    /**
     * 检查是否应该刷新
     */
    private boolean shouldRefresh(UUID playerId) {
        Long lastRefresh = lastRefreshTime.get(playerId);

        if (lastRefresh == null) {
            return true;
        }

        // D-071: 使用显式时区，避免 JVM 默认时区在跨服或运维变更后改变刷新边界
        java.time.ZonedDateTime lastZdt = java.time.Instant.ofEpochMilli(lastRefresh).atZone(refreshZone);
        java.time.ZonedDateTime nowZdt = java.time.ZonedDateTime.now(refreshZone);

        // 不同日期，且当前时间已过刷新点
        if (!lastZdt.toLocalDate().equals(nowZdt.toLocalDate())) {
            return nowZdt.getHour() >= refreshHour;
        }

        // 同一天：上次刷新在刷新点之前，现在超过刷新点
        if (lastZdt.getHour() < refreshHour && nowZdt.getHour() >= refreshHour) {
            return true;
        }

        return false;
    }

    /**
     * 全服刷新每日任务
     */
    public void refreshAllDailyQuests() {
        Bukkit.getLogger().info("[DailyQuest] 开始刷新每日任务...");

        // 清空所有玩家的每日任务进度
        questService.resetDailyQuests();

        // 清空每日任务列表
        playerDailyQuests.clear();
        lastRefreshTime.clear();

        // 为在线玩家重新生成
        for (Player player : Bukkit.getOnlinePlayers()) {
            generateDailyQuests(player);
            player.sendMessage("§a每日任务已刷新！输入 /quest daily 查看今日任务。");
        }

        // 保存刷新后的数据
        save();

        Bukkit.getLogger().info("[DailyQuest] 每日任务刷新完成");
    }

    /**
     * 获取玩家今日任务列表
     */
    public List<Quest> getPlayerDailyQuests(UUID playerId) {
        return playerDailyQuests.getOrDefault(playerId, Collections.emptyList());
    }

    /**
     * 获取玩家今日任务完成进度
     */
    public DailyProgress getDailyProgress(UUID playerId) {
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);
        List<Quest> todayQuests = playerDailyQuests.getOrDefault(playerId, Collections.emptyList());

        int completed = 0;
        int total = todayQuests.size();

        for (Quest quest : todayQuests) {
            if (playerQuest.hasCompletedQuest(quest.getId())) {
                completed++;
            }
        }

        return new DailyProgress(completed, total);
    }

    /**
     * 手动刷新玩家每日任务（需要消耗道具或货币）
     * @param force true 表示强制刷新（admin），跳过 shouldRefresh 与冷却校验
     */
    public boolean manualRefresh(Player player, boolean force) {
        UUID playerId = player.getUniqueId();

        if (!force && !shouldRefresh(playerId)) {
            player.sendMessage("§c今日任务尚未到刷新时间！");
            return false;
        }

        // 检查并扣除刷新费用
        if (economyService != null && refreshCost > 0) {
            if (!economyService.has(playerId, BigDecimal.valueOf(refreshCost))) {
                player.sendMessage("§c刷新每日任务需要 " + refreshCost + " 金币，您的余额不足！");
                return false;
            }
            economyService.withdraw(playerId, BigDecimal.valueOf(refreshCost));
            player.sendMessage("§e刷新每日任务消耗 " + refreshCost + " 金币");
        }

        // D-069: 必须清空 lastRefreshTime，否则 generateDailyQuests 内的 shouldRefresh 仍判定 false，
        // 玩家被扣钱却拿不到新任务。
        playerDailyQuests.remove(playerId);
        lastRefreshTime.remove(playerId);

        generateDailyQuests(player);
        player.sendMessage("§a每日任务已刷新！");

        return true;
    }

    /**
     * 添加每日任务模板
     */
    public void addDailyQuestTemplate(Quest quest) {
        questPool.addTemplate(quest);
    }

    // Getters and Setters

    public int getDailyQuestCount() {
        return dailyQuestCount;
    }

    public void setDailyQuestCount(int dailyQuestCount) {
        this.dailyQuestCount = dailyQuestCount;
    }

    public boolean isUsePersonalizedQuests() {
        return usePersonalizedQuests;
    }

    public void setUsePersonalizedQuests(boolean usePersonalizedQuests) {
        this.usePersonalizedQuests = usePersonalizedQuests;
    }

    public long getRefreshHour() {
        return refreshHour;
    }

    public void setRefreshHour(long refreshHour) {
        this.refreshHour = Math.max(0, Math.min(23, refreshHour));
    }

    public DailyQuestPool getQuestPool() {
        return questPool;
    }

    public double getRefreshCost() {
        return refreshCost;
    }

    public void setRefreshCost(double refreshCost) {
        this.refreshCost = refreshCost;
    }

    /**
     * 每日进度类
     */
    public static class DailyProgress {
        private final int completed;
        private final int total;

        public DailyProgress(int completed, int total) {
            this.completed = completed;
            this.total = total;
        }

        public int getCompleted() {
            return completed;
        }

        public int getTotal() {
            return total;
        }

        public double getPercentage() {
            return total == 0 ? 0.0 : (double) completed / total * 100.0;
        }

        public boolean isAllCompleted() {
            return completed >= total && total > 0;
        }

        public String getProgressBar() {
            int barLength = 20;
            int filled = total == 0 ? 0 : (int) ((double) completed / total * barLength);

            StringBuilder bar = new StringBuilder("§7[");
            for (int i = 0; i < barLength; i++) {
                bar.append(i < filled ? "§a■" : "§8□");
            }
            bar.append("§7]");

            return bar.toString();
        }

        public String getProgressText() {
            return String.format("§e%d§7/§a%d §7(§6%.1f%%§7)", completed, total, getPercentage());
        }

        @Override
        public String toString() {
            return String.format("%d/%d (%.1f%%)", completed, total, getPercentage());
        }
    }
}
