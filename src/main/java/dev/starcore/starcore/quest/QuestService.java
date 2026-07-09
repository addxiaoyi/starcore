package dev.starcore.starcore.quest;

import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.mechanics.ReputationService;
import dev.starcore.starcore.mechanics.ReputationSource;
import dev.starcore.starcore.title.TitleService;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 任务服务核心类
 * 管理所有任务和玩家任务进度
 *
 * @author StarCore Team
 * @since 1.0.0
 */
public class QuestService {

    private static final String NAMESPACE = "quest";

    private final EconomyService economyService;
    private final ReputationService reputationService;
    private final TitleService titleService;
    private final PersistenceService persistenceService;
    private final Logger logger;

    private final Map<String, Quest> questRegistry; // 所有任务注册表
    private final Map<UUID, PlayerQuest> playerQuests; // 玩家任务数据
    private final Map<String, List<Quest>> questsByCategory; // 按分类索引的任务
    private final Map<QuestType, List<Quest>> questsByType; // 按类型索引的任务

    private QuestPersistenceHandler persistenceHandler;
    private int maxActiveQuestsPerPlayer = 20;
    private int maxDailyQuestsPerPlayer = 10;

    /**
     * 构造函数
     */
    public QuestService(EconomyService economyService, ReputationService reputationService,
                        TitleService titleService, PersistenceService persistenceService, Logger logger) {
        this.economyService = economyService;
        this.reputationService = reputationService;
        this.titleService = titleService;
        this.persistenceService = persistenceService;
        this.logger = logger;
        this.questRegistry = new ConcurrentHashMap<>();
        this.playerQuests = new ConcurrentHashMap<>();
        this.questsByCategory = new ConcurrentHashMap<>();
        this.questsByType = new ConcurrentHashMap<>();
    }

    /**
     * 初始化持久化
     */
    public void initialize() {
        if (persistenceService != null) {
            QuestStateStorage stateStorage = new PersistenceQuestStateStorage(NAMESPACE, persistenceService);
            this.persistenceHandler = new QuestPersistenceHandler(stateStorage, logger);

            // 加载已保存的数据
            persistenceHandler.loadPlayerQuests(playerQuests, questRegistry);

            // 清理过期任务
            cleanupExpiredQuests();
        }
    }

    /**
     * 持久化玩家任务数据（异步）
     */
    public void saveAsync() {
        if (persistenceHandler != null) {
            persistenceHandler.savePlayerQuestsAsync(new HashMap<>(playerQuests));
        }
    }

    /**
     * 持久化玩家任务数据（同步）
     */
    public void save() {
        if (persistenceHandler != null) {
            persistenceHandler.savePlayerQuests(new HashMap<>(playerQuests));
        }
    }

    /**
     * 注册任务。D-067: 同步三个索引，避免并发注册同 ID 导致部分索引已加部分未加。
     */
    public synchronized void registerQuest(Quest quest) {
        questRegistry.put(quest.getId(), quest);

        // 按分类索引
        questsByCategory.computeIfAbsent(quest.getCategory(), k -> Collections.synchronizedList(new ArrayList<>())).add(quest);

        // 按类型索引
        questsByType.computeIfAbsent(quest.getType(), k -> Collections.synchronizedList(new ArrayList<>())).add(quest);
    }

    /**
     * 批量注册任务
     */
    public void registerQuests(Collection<Quest> quests) {
        quests.forEach(this::registerQuest);
    }

    /**
     * 注销任务
     */
    public void unregisterQuest(String questId) {
        Quest quest = questRegistry.remove(questId);
        if (quest != null) {
            questsByCategory.getOrDefault(quest.getCategory(), new ArrayList<>()).remove(quest);
            questsByType.getOrDefault(quest.getType(), new ArrayList<>()).remove(quest);
        }
    }

    /**
     * 获取任务
     */
    public Quest getQuest(String questId) {
        return questRegistry.get(questId);
    }

    /**
     * 获取玩家任务数据
     */
    public PlayerQuest getPlayerQuest(UUID playerId) {
        return playerQuests.computeIfAbsent(playerId, PlayerQuest::new);
    }

    /**
     * 玩家接取任务
     */
    public QuestAcceptResult acceptQuest(Player player, String questId) {
        Quest quest = questRegistry.get(questId);
        if (quest == null) {
            return QuestAcceptResult.QUEST_NOT_FOUND;
        }

        PlayerQuest playerQuest = getPlayerQuest(player.getUniqueId());

        // 检查任务数量限制
        if (playerQuest.getActiveQuestCount() >= maxActiveQuestsPerPlayer) {
            return QuestAcceptResult.QUEST_LIMIT_REACHED;
        }

        // 检查每日任务限制
        if (quest.getType() == QuestType.DAILY &&
            playerQuest.getActiveQuestCountByType(QuestType.DAILY) >= maxDailyQuestsPerPlayer) {
            return QuestAcceptResult.DAILY_QUEST_LIMIT_REACHED;
        }

        // 检查是否已接取
        if (playerQuest.hasActiveQuest(questId)) {
            return QuestAcceptResult.ALREADY_ACTIVE;
        }

        // 检查冷却时间
        if (playerQuest.isOnCooldown(questId)) {
            return QuestAcceptResult.ON_COOLDOWN;
        }

        // 检查接取条件
        if (!quest.canAccept(
                player.getUniqueId(),
                player.getLevel(),
                playerQuest.getCompletedQuestIds())) {
            return QuestAcceptResult.REQUIREMENTS_NOT_MET;
        }

        // 接取任务
        if (playerQuest.acceptQuest(quest)) {
            // 执行开始命令
            executeCommands(player, quest.getStartCommands());
            // 自动保存
            saveAsync();
            return QuestAcceptResult.SUCCESS;
        }

        return QuestAcceptResult.FAILED;
    }

    /**
     * 玩家放弃任务
     */
    public boolean abandonQuest(UUID playerId, String questId) {
        PlayerQuest playerQuest = getPlayerQuest(playerId);
        boolean result = playerQuest.abandonQuest(questId);
        if (result) {
            saveAsync();
        }
        return result;
    }

    /**
     * 玩家完成任务
     */
    public QuestCompleteResult completeQuest(Player player, String questId) {
        Quest quest = questRegistry.get(questId);
        if (quest == null) {
            return new QuestCompleteResult(false, "任务不存在");
        }

        PlayerQuest playerQuest = getPlayerQuest(player.getUniqueId());

        if (!playerQuest.hasActiveQuest(questId)) {
            return new QuestCompleteResult(false, "任务未激活");
        }

        Quest activeQuest = playerQuest.getActiveQuest(questId);
        if (!activeQuest.isAllObjectivesCompleted()) {
            return new QuestCompleteResult(false, "任务目标未完成");
        }

        // 完成任务
        if (playerQuest.completeQuest(questId)) {
            // 发放奖励
            QuestReward reward = activeQuest.getReward();
            giveReward(player, reward);

            // 执行完成命令
            executeCommands(player, activeQuest.getCompleteCommands());

            // 自动保存
            saveAsync();

            return new QuestCompleteResult(true, "任务完成", reward);
        }

        return new QuestCompleteResult(false, "完成失败");
    }

    /**
     * 更新任务进度
     */
    // D-063: 计数器形式的脏保存，避免 System.currentTimeMillis()%5 几乎不命中造成进度丢失
    private final java.util.concurrent.atomic.AtomicInteger progressUpdateCounter = new java.util.concurrent.atomic.AtomicInteger(0);
    public void updateObjectiveProgress(UUID playerId, String questId, int objectiveIndex, int amount) {
        PlayerQuest playerQuest = getPlayerQuest(playerId);
        playerQuest.updateQuestProgress(questId, objectiveIndex, amount);
        // 每 5 次更新触发一次异步保存；最终落盘由 saveAsync() 兜底
        if (progressUpdateCounter.incrementAndGet() % 5 == 0) {
            saveAsync();
        }
    }

    /**
     * 发放奖励
     */
    private void giveReward(Player player, QuestReward reward) {
        // 金钱奖励
        if (reward.getMoney() > 0) {
            if (economyService != null) {
                economyService.deposit(player.getUniqueId(), BigDecimal.valueOf(reward.getMoney()));
                player.sendMessage("§6[任务] §a+" + reward.getMoney() + " 金币");
            }
        }

        // 经验奖励
        if (reward.getExperience() > 0) {
            player.giveExp(reward.getExperience());
            player.sendMessage("§6[任务] §a+" + reward.getExperience() + " 经验");
        }

        // D-066: 物品奖励 —— addItem 返回的 overflow map 既往被忽略，背包满时物品直接消失。
        // 这里捕获溢出并掉落在玩家脚下；如果区块未加载，至少有掉落物（玩家可看见消息）。
        reward.getItems().forEach(item -> {
            java.util.Map<Integer, org.bukkit.inventory.ItemStack> overflow = player.getInventory().addItem(item);
            for (org.bukkit.inventory.ItemStack left : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        });
        if (!reward.getItems().isEmpty()) {
            player.sendMessage("§6[任务] §a已发放物品奖励" + (reward.getItems().size() > 1 ? "（背包满的物品掉落在你脚下）" : ""));
        }

        // 声望奖励 (使用 QUEST 来源)
        if (reputationService != null) {
            for (Map.Entry<String, Integer> entry : reward.getReputations().entrySet()) {
                // 尝试使用 ReputationSource.QUEST
                reputationService.addReputation(player.getUniqueId(), ReputationSource.QUEST, entry.getValue());
            }
        }

        // 称号奖励。D-068: 验证称号 ID 在服务注册表内，避免配置注入任意 titleId。
        if (titleService != null) {
            for (String titleId : reward.getTitles()) {
                if (titleId == null || titleId.isBlank()) continue;
                try {
                    titleService.unlockTitle(player.getUniqueId(), titleId);
                } catch (RuntimeException ex) {
                    logger.warning("任务奖励称号 " + titleId + " 不存在或解锁失败: " + ex.getMessage());
                }
            }
        }

        // 执行命令奖励
        executeCommands(player, reward.getCommands());
    }

    /**
     * 执行命令列表。
     * D-064: 不再走 player.performCommand（任务配置可能含 op 命令会造成权限提升），
     * 改由 console sender 执行；命令字串仍支持 {player}/{uuid} 占位符。
     */
    private void executeCommands(Player player, List<String> commands) {
        if (commands == null) return;
        org.bukkit.command.CommandSender console = org.bukkit.Bukkit.getConsoleSender();
        for (String command : commands) {
            if (command == null || command.isBlank()) continue;
            String processedCommand = command
                    .replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString());
            org.bukkit.Bukkit.dispatchCommand(console, processedCommand);
        }
    }

    /**
     * 获取可接取的任务列表
     */
    public List<Quest> getAvailableQuests(Player player) {
        PlayerQuest playerQuest = getPlayerQuest(player.getUniqueId());
        List<String> completedQuests = playerQuest.getCompletedQuestIds();

        return questRegistry.values().stream()
                .filter(quest -> !playerQuest.hasActiveQuest(quest.getId()))
                .filter(quest -> !playerQuest.isOnCooldown(quest.getId()))
                .filter(quest -> quest.canAccept(player.getUniqueId(), player.getLevel(), completedQuests))
                .collect(Collectors.toList());
    }

    /**
     * 获取指定类型的任务
     */
    public List<Quest> getQuestsByType(QuestType type) {
        return new ArrayList<>(questsByType.getOrDefault(type, Collections.emptyList()));
    }

    /**
     * 获取指定分类的任务
     */
    public List<Quest> getQuestsByCategory(String category) {
        return new ArrayList<>(questsByCategory.getOrDefault(category, Collections.emptyList()));
    }

    /**
     * 清理所有玩家的过期任务
     */
    public void cleanupExpiredQuests() {
        for (PlayerQuest playerQuest : playerQuests.values()) {
            playerQuest.cleanupExpiredQuests();
        }
    }

    /**
     * 重置所有玩家的每日任务
     */
    public void resetDailyQuests() {
        for (PlayerQuest playerQuest : playerQuests.values()) {
            playerQuest.resetDailyQuests();
        }
    }

    /**
     * 获取任务统计信息
     */
    public QuestStatistics getStatistics(UUID playerId) {
        PlayerQuest playerQuest = getPlayerQuest(playerId);
        return new QuestStatistics(playerQuest);
    }

    // Getters and Setters

    public int getMaxActiveQuestsPerPlayer() {
        return maxActiveQuestsPerPlayer;
    }

    public void setMaxActiveQuestsPerPlayer(int maxActiveQuestsPerPlayer) {
        this.maxActiveQuestsPerPlayer = maxActiveQuestsPerPlayer;
    }

    public int getMaxDailyQuestsPerPlayer() {
        return maxDailyQuestsPerPlayer;
    }

    public void setMaxDailyQuestsPerPlayer(int maxDailyQuestsPerPlayer) {
        this.maxDailyQuestsPerPlayer = maxDailyQuestsPerPlayer;
    }

    public Map<String, Quest> getQuestRegistry() {
        return new HashMap<>(questRegistry);
    }

    /**
     * 任务接取结果枚举
     */
    public enum QuestAcceptResult {
        SUCCESS("成功接取任务"),
        QUEST_NOT_FOUND("任务不存在"),
        ALREADY_ACTIVE("任务已激活"),
        QUEST_LIMIT_REACHED("任务数量已达上限"),
        DAILY_QUEST_LIMIT_REACHED("每日任务数量已达上限"),
        ON_COOLDOWN("任务冷却中"),
        REQUIREMENTS_NOT_MET("不满足任务要求"),
        FAILED("接取失败");

        private final String message;

        QuestAcceptResult(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 任务完成结果类
     */
    public static class QuestCompleteResult {
        private final boolean success;
        private final String message;
        private final QuestReward reward;

        public QuestCompleteResult(boolean success, String message) {
            this(success, message, null);
        }

        public QuestCompleteResult(boolean success, String message, QuestReward reward) {
            this.success = success;
            this.message = message;
            this.reward = reward;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public QuestReward getReward() {
            return reward;
        }
    }
}
