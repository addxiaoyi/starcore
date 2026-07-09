package dev.starcore.starcore.quest;

import java.util.*;

/**
 * 玩家任务记录类
 * 记录玩家的任务进度和状态
 *
 * @author StarCore Team
 * @since 1.0.0
 */
public class PlayerQuest {

    /**
     * 任务状态枚举
     */
    public enum QuestStatus {
        NOT_STARTED("未开始"),
        IN_PROGRESS("进行中"),
        COMPLETED("已完成"),
        FAILED("已失败"),
        ABANDONED("已放弃"),
        READY_TO_COMPLETE("可完成");

        private final String displayName;

        QuestStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final UUID playerId;
    private final Map<String, Quest> activeQuests; // 进行中的任务
    private final Map<String, Long> completedQuests; // 已完成的任务（questId -> 完成时间）
    private final Map<String, Long> failedQuests; // 失败的任务（questId -> 失败时间）
    private final Map<String, Long> questCooldowns; // 任务冷却时间（questId -> 可接取时间）
    private final Map<String, Long> questStartTimes; // 任务开始时间（用于计时任务）
    private final Map<String, Integer> questCompletionCounts; // 任务完成次数（用于重复任务）

    /**
     * 构造函数
     */
    public PlayerQuest(UUID playerId) {
        this.playerId = playerId;
        this.activeQuests = new HashMap<>();
        this.completedQuests = new HashMap<>();
        this.failedQuests = new HashMap<>();
        this.questCooldowns = new HashMap<>();
        this.questStartTimes = new HashMap<>();
        this.questCompletionCounts = new HashMap<>();
    }

    /**
     * 接取任务
     */
    public boolean acceptQuest(Quest quest) {
        if (activeQuests.containsKey(quest.getId())) {
            return false; // 已经接取
        }

        if (isOnCooldown(quest.getId())) {
            return false; // 冷却中
        }

        Quest questInstance = quest.createInstance();
        activeQuests.put(quest.getId(), questInstance);
        questStartTimes.put(quest.getId(), System.currentTimeMillis());

        return true;
    }

    /**
     * 放弃任务
     */
    public boolean abandonQuest(String questId) {
        Quest quest = activeQuests.remove(questId);
        if (quest != null) {
            questStartTimes.remove(questId);
            return true;
        }
        return false;
    }

    /**
     * 完成任务
     */
    public boolean completeQuest(String questId) {
        Quest quest = activeQuests.get(questId);
        if (quest == null || !quest.isAllObjectivesCompleted()) {
            return false;
        }

        activeQuests.remove(questId);
        questStartTimes.remove(questId);

        long completionTime = System.currentTimeMillis();
        completedQuests.put(questId, completionTime);

        // 更新完成次数
        questCompletionCounts.merge(questId, 1, Integer::sum);

        // 设置冷却时间
        if (quest.isRepeatable() && quest.getCooldown() > 0) {
            questCooldowns.put(questId, completionTime + quest.getCooldown());
        }

        return true;
    }

    /**
     * 任务失败
     */
    public void failQuest(String questId) {
        Quest quest = activeQuests.remove(questId);
        if (quest != null) {
            questStartTimes.remove(questId);
            failedQuests.put(questId, System.currentTimeMillis());
        }
    }

    /**
     * 更新任务进度
     */
    public boolean updateQuestProgress(String questId, int objectiveIndex, int amount) {
        Quest quest = activeQuests.get(questId);
        if (quest == null) {
            return false;
        }

        List<QuestObjective> objectives = quest.getObjectives();
        if (objectiveIndex >= 0 && objectiveIndex < objectives.size()) {
            objectives.get(objectiveIndex).addProgress(amount);
            return true;
        }

        return false;
    }

    /**
     * 检查任务是否已完成（历史）
     */
    public boolean hasCompletedQuest(String questId) {
        return completedQuests.containsKey(questId);
    }

    /**
     * 检查任务是否正在进行
     */
    public boolean hasActiveQuest(String questId) {
        return activeQuests.containsKey(questId);
    }

    /**
     * 检查是否在冷却中
     */
    public boolean isOnCooldown(String questId) {
        Long cooldownEnd = questCooldowns.get(questId);
        if (cooldownEnd == null) {
            return false;
        }

        if (System.currentTimeMillis() >= cooldownEnd) {
            questCooldowns.remove(questId);
            return false;
        }

        return true;
    }

    /**
     * 获取剩余冷却时间（毫秒）
     */
    public long getRemainingCooldown(String questId) {
        Long cooldownEnd = questCooldowns.get(questId);
        if (cooldownEnd == null) {
            return 0;
        }

        long remaining = cooldownEnd - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * 检查任务是否超时
     */
    public boolean isQuestExpired(String questId) {
        Quest quest = activeQuests.get(questId);
        if (quest == null || quest.getTimeLimit() <= 0) {
            return false;
        }

        Long startTime = questStartTimes.get(questId);
        if (startTime == null) {
            return false;
        }

        return System.currentTimeMillis() - startTime >= quest.getTimeLimit();
    }

    /**
     * 获取任务剩余时间（毫秒）
     */
    public long getRemainingTime(String questId) {
        Quest quest = activeQuests.get(questId);
        if (quest == null || quest.getTimeLimit() <= 0) {
            return -1; // 无时间限制
        }

        Long startTime = questStartTimes.get(questId);
        if (startTime == null) {
            return 0;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        return Math.max(0, quest.getTimeLimit() - elapsed);
    }

    /**
     * 获取任务状态
     */
    public QuestStatus getQuestStatus(String questId) {
        if (activeQuests.containsKey(questId)) {
            Quest quest = activeQuests.get(questId);
            if (isQuestExpired(questId)) {
                return QuestStatus.FAILED;
            }
            if (quest.isAllObjectivesCompleted()) {
                return QuestStatus.READY_TO_COMPLETE;
            }
            return QuestStatus.IN_PROGRESS;
        }

        if (completedQuests.containsKey(questId)) {
            return QuestStatus.COMPLETED;
        }

        if (failedQuests.containsKey(questId)) {
            return QuestStatus.FAILED;
        }

        return QuestStatus.NOT_STARTED;
    }

    /**
     * 获取任务完成次数
     */
    public int getQuestCompletionCount(String questId) {
        return questCompletionCounts.getOrDefault(questId, 0);
    }

    /**
     * 获取所有已完成的任务ID列表
     */
    public List<String> getCompletedQuestIds() {
        return new ArrayList<>(completedQuests.keySet());
    }

    /**
     * 获取进行中的任务数量
     */
    public int getActiveQuestCount() {
        return activeQuests.size();
    }

    /**
     * 获取某类型的进行中任务数量
     */
    public int getActiveQuestCountByType(QuestType type) {
        return (int) activeQuests.values().stream()
                .filter(quest -> quest.getType() == type)
                .count();
    }

    /**
     * 清理过期任务
     */
    public List<String> cleanupExpiredQuests() {
        List<String> expiredQuestIds = new ArrayList<>();

        for (String questId : new ArrayList<>(activeQuests.keySet())) {
            if (isQuestExpired(questId)) {
                failQuest(questId);
                expiredQuestIds.add(questId);
            }
        }

        return expiredQuestIds;
    }

    /**
     * 重置每日任务
     */
    public void resetDailyQuests() {
        List<String> dailyQuestIds = new ArrayList<>();

        for (Quest quest : activeQuests.values()) {
            if (quest.getType() == QuestType.DAILY) {
                dailyQuestIds.add(quest.getId());
            }
        }

        for (String questId : dailyQuestIds) {
            abandonQuest(questId);
        }
    }

    // Getters

    public UUID getPlayerId() {
        return playerId;
    }

    public Map<String, Quest> getActiveQuests() {
        return new HashMap<>(activeQuests);
    }

    public Quest getActiveQuest(String questId) {
        return activeQuests.get(questId);
    }

    public Map<String, Long> getCompletedQuests() {
        return new HashMap<>(completedQuests);
    }

    public Map<String, Long> getFailedQuests() {
        return new HashMap<>(failedQuests);
    }

    public Map<String, Long> getQuestCooldowns() {
        return new HashMap<>(questCooldowns);
    }

    public Map<String, Integer> getQuestCompletionCounts() {
        return new HashMap<>(questCompletionCounts);
    }

    /**
     * 获取任务开始时间映射
     */
    public Map<String, Long> getQuestStartTimes() {
        return new HashMap<>(questStartTimes);
    }

    // ==================== 持久化支持方法 ====================

    /**
     * 标记任务为已完成（用于持久化加载）
     * @param questId 任务ID
     * @param completionTime 完成时间戳
     */
    public void markQuestCompleted(String questId, long completionTime) {
        completedQuests.put(questId, completionTime);
    }

    /**
     * 设置任务冷却时间（用于持久化加载）
     * @param questId 任务ID
     * @param cooldownEnd 冷却结束时间戳
     */
    public void setQuestCooldown(String questId, long cooldownEnd) {
        questCooldowns.put(questId, cooldownEnd);
    }

    /**
     * 设置任务完成次数（用于持久化加载）
     * @param questId 任务ID
     * @param count 完成次数
     */
    public void setQuestCompletionCount(String questId, int count) {
        questCompletionCounts.put(questId, count);
    }
}
