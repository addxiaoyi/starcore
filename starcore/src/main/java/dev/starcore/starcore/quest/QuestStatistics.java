package dev.starcore.starcore.quest;

import java.util.*;

/**
 * 任务统计类
 * 记录和展示玩家任务完成情况
 *
 * @author StarCore Team
 * @since 1.0.0
 */
public class QuestStatistics {

    private final UUID playerId;
    private final int totalQuestsCompleted;
    private final int dailyQuestsCompleted;
    private final int weeklyQuestsCompleted;
    private final int mainQuestsCompleted;
    private final int sideQuestsCompleted;
    private final int commissionsCompleted;
    private final Map<QuestDifficulty, Integer> completionsByDifficulty;
    private final Map<String, Integer> completionsByCategory;
    private final int currentActiveQuests;
    private final int currentDailyQuests;

    /**
     * 构造函数 - 从PlayerQuest生成统计
     */
    public QuestStatistics(PlayerQuest playerQuest) {
        this.playerId = playerQuest.getPlayerId();
        this.currentActiveQuests = playerQuest.getActiveQuestCount();
        this.currentDailyQuests = playerQuest.getActiveQuestCountByType(QuestType.DAILY);

        // 初始化计数器
        int totalCompleted = 0;
        int dailyCompleted = 0;
        int weeklyCompleted = 0;
        int mainCompleted = 0;
        int sideCompleted = 0;
        int commissionCompleted = 0;

        Map<QuestDifficulty, Integer> difficultyMap = new EnumMap<>(QuestDifficulty.class);
        Map<String, Integer> categoryMap = new HashMap<>();

        // 统计已完成的任务
        for (Map.Entry<String, Integer> entry : playerQuest.getQuestCompletionCounts().entrySet()) {
            String questId = entry.getKey();
            int count = entry.getValue();

            totalCompleted += count;

            // 这里需要从任务注册表获取任务信息来分类统计
            // 简化实现，实际使用时需要注入QuestService
        }

        // 统计进行中的任务类型
        for (Quest quest : playerQuest.getActiveQuests().values()) {
            QuestType type = quest.getType();
            QuestDifficulty difficulty = quest.getDifficulty();
            String category = quest.getCategory();

            // 按难度统计（进行中的）
            difficultyMap.merge(difficulty, 1, Integer::sum);

            // 按分类统计（进行中的）
            categoryMap.merge(category, 1, Integer::sum);
        }

        this.totalQuestsCompleted = totalCompleted;
        this.dailyQuestsCompleted = dailyCompleted;
        this.weeklyQuestsCompleted = weeklyCompleted;
        this.mainQuestsCompleted = mainCompleted;
        this.sideQuestsCompleted = sideCompleted;
        this.commissionsCompleted = commissionCompleted;
        this.completionsByDifficulty = difficultyMap;
        this.completionsByCategory = categoryMap;
    }

    /**
     * 完整构造函数
     */
    public QuestStatistics(UUID playerId, int totalCompleted, int dailyCompleted,
                          int weeklyCompleted, int mainCompleted, int sideCompleted,
                          int commissionCompleted, int activeQuests, int dailyActive) {
        this.playerId = playerId;
        this.totalQuestsCompleted = totalCompleted;
        this.dailyQuestsCompleted = dailyCompleted;
        this.weeklyQuestsCompleted = weeklyCompleted;
        this.mainQuestsCompleted = mainCompleted;
        this.sideQuestsCompleted = sideCompleted;
        this.commissionsCompleted = commissionCompleted;
        this.currentActiveQuests = activeQuests;
        this.currentDailyQuests = dailyActive;
        this.completionsByDifficulty = new EnumMap<>(QuestDifficulty.class);
        this.completionsByCategory = new HashMap<>();
    }

    /**
     * 获取完成率（基于总任务数）
     */
    public double getCompletionRate(int totalAvailableQuests) {
        if (totalAvailableQuests == 0) {
            return 0.0;
        }
        return (double) totalQuestsCompleted / totalAvailableQuests * 100.0;
    }

    /**
     * 获取平均每日完成数
     */
    public double getAverageDailyCompletions(int daysPlayed) {
        if (daysPlayed == 0) {
            return 0.0;
        }
        return (double) dailyQuestsCompleted / daysPlayed;
    }

    /**
     * 获取主线任务完成百分比
     */
    public double getMainQuestProgress(int totalMainQuests) {
        if (totalMainQuests == 0) {
            return 0.0;
        }
        return (double) mainQuestsCompleted / totalMainQuests * 100.0;
    }

    /**
     * 获取统计摘要
     */
    public List<String> getSummary() {
        List<String> summary = new ArrayList<>();

        summary.add("§6========== 任务统计 ==========");
        summary.add(String.format("§e总完成任务: §a%d", totalQuestsCompleted));
        summary.add(String.format("§e当前进行中: §b%d", currentActiveQuests));
        summary.add("");
        summary.add("§6按类型统计:");
        summary.add(String.format("  §7每日任务: §f%d", dailyQuestsCompleted));
        summary.add(String.format("  §7每周任务: §f%d", weeklyQuestsCompleted));
        summary.add(String.format("  §7主线任务: §f%d", mainQuestsCompleted));
        summary.add(String.format("  §7支线任务: §f%d", sideQuestsCompleted));
        summary.add(String.format("  §7委托任务: §f%d", commissionsCompleted));

        if (!completionsByDifficulty.isEmpty()) {
            summary.add("");
            summary.add("§6按难度统计:");
            for (QuestDifficulty difficulty : QuestDifficulty.values()) {
                int count = completionsByDifficulty.getOrDefault(difficulty, 0);
                if (count > 0) {
                    summary.add(String.format("  %s: §f%d", difficulty.getColoredName(), count));
                }
            }
        }

        summary.add("§6============================");

        return summary;
    }

    /**
     * 获取排行榜条目
     */
    public String getLeaderboardEntry(int rank, String playerName) {
        return String.format("§e#%d §f%s §7- §a%d §7个任务",
                rank, playerName, totalQuestsCompleted);
    }

    // Getters

    public UUID getPlayerId() {
        return playerId;
    }

    public int getTotalQuestsCompleted() {
        return totalQuestsCompleted;
    }

    public int getDailyQuestsCompleted() {
        return dailyQuestsCompleted;
    }

    public int getWeeklyQuestsCompleted() {
        return weeklyQuestsCompleted;
    }

    public int getMainQuestsCompleted() {
        return mainQuestsCompleted;
    }

    public int getSideQuestsCompleted() {
        return sideQuestsCompleted;
    }

    public int getCommissionsCompleted() {
        return commissionsCompleted;
    }

    public Map<QuestDifficulty, Integer> getCompletionsByDifficulty() {
        return new EnumMap<>(completionsByDifficulty);
    }

    public Map<String, Integer> getCompletionsByCategory() {
        return new HashMap<>(completionsByCategory);
    }

    public int getCurrentActiveQuests() {
        return currentActiveQuests;
    }

    public int getCurrentDailyQuests() {
        return currentDailyQuests;
    }

    @Override
    public String toString() {
        return String.format("QuestStatistics{player=%s, total=%d, active=%d}",
                playerId, totalQuestsCompleted, currentActiveQuests);
    }
}
