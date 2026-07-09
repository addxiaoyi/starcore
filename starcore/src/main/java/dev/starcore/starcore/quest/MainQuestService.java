package dev.starcore.starcore.quest;

import java.util.*;

/**
 * 主线任务服务
 * 管理主线任务的剧情推进和章节系统
 *
 * @author StarCore Team
 * @since 1.0.0
 */
public class MainQuestService {

    private final QuestService questService;
    private final Map<String, QuestChapter> chapters; // 所有章节
    private final Map<String, QuestLine> questLines; // 所有任务线
    private final Map<UUID, String> playerCurrentChapter; // 玩家当前章节
    private final Map<UUID, Map<String, Integer>> playerChapterProgress; // 玩家章节进度

    /**
     * 构造函数
     */
    public MainQuestService(QuestService questService) {
        this.questService = questService;
        this.chapters = new LinkedHashMap<>();
        this.questLines = new HashMap<>();
        this.playerCurrentChapter = new HashMap<>();
        this.playerChapterProgress = new HashMap<>();
    }

    /**
     * 注册章节
     */
    public void registerChapter(QuestChapter chapter) {
        chapters.put(chapter.getId(), chapter);
    }

    /**
     * 注册任务线
     */
    public void registerQuestLine(QuestLine questLine) {
        questLines.put(questLine.getId(), questLine);
    }

    /**
     * 获取玩家当前章节
     */
    public QuestChapter getPlayerCurrentChapter(UUID playerId) {
        String chapterId = playerCurrentChapter.get(playerId);
        return chapterId != null ? chapters.get(chapterId) : getFirstChapter();
    }

    /**
     * 获取第一章节
     */
    public QuestChapter getFirstChapter() {
        return chapters.values().stream()
                .filter(chapter -> chapter.getPreviousChapterId() == null)
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取下一章节
     */
    public QuestChapter getNextChapter(String currentChapterId) {
        return chapters.values().stream()
                .filter(chapter -> currentChapterId.equals(chapter.getPreviousChapterId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 检查玩家是否可以进入下一章节
     */
    public boolean canAdvanceChapter(UUID playerId, String chapterId) {
        QuestChapter chapter = chapters.get(chapterId);
        if (chapter == null) {
            return false;
        }

        // 检查前置章节是否完成
        String previousChapterId = chapter.getPreviousChapterId();
        if (previousChapterId != null) {
            if (!isChapterCompleted(playerId, previousChapterId)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 推进玩家到下一章节
     */
    public boolean advancePlayerChapter(UUID playerId) {
        String currentChapterId = playerCurrentChapter.get(playerId);
        QuestChapter currentChapter = currentChapterId != null ? chapters.get(currentChapterId) : null;

        // 如果没有当前章节，设置为第一章
        if (currentChapter == null) {
            QuestChapter firstChapter = getFirstChapter();
            if (firstChapter != null) {
                playerCurrentChapter.put(playerId, firstChapter.getId());
                return true;
            }
            return false;
        }

        // 检查当前章节是否完成
        if (!isChapterCompleted(playerId, currentChapterId)) {
            return false;
        }

        // 获取下一章节
        QuestChapter nextChapter = getNextChapter(currentChapterId);
        if (nextChapter != null && canAdvanceChapter(playerId, nextChapter.getId())) {
            playerCurrentChapter.put(playerId, nextChapter.getId());
            return true;
        }

        return false;
    }

    /**
     * 检查章节是否完成
     */
    public boolean isChapterCompleted(UUID playerId, String chapterId) {
        QuestChapter chapter = chapters.get(chapterId);
        if (chapter == null) {
            return false;
        }

        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);

        // 检查所有必需任务是否完成
        for (String questId : chapter.getRequiredQuests()) {
            if (!playerQuest.hasCompletedQuest(questId)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 获取章节进度
     */
    public ChapterProgress getChapterProgress(UUID playerId, String chapterId) {
        QuestChapter chapter = chapters.get(chapterId);
        if (chapter == null) {
            return new ChapterProgress(0, 0);
        }

        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);
        List<String> requiredQuests = chapter.getRequiredQuests();

        int completed = 0;
        for (String questId : requiredQuests) {
            if (playerQuest.hasCompletedQuest(questId)) {
                completed++;
            }
        }

        return new ChapterProgress(completed, requiredQuests.size());
    }

    /**
     * 获取任务线进度
     */
    public QuestLineProgress getQuestLineProgress(UUID playerId, String questLineId) {
        QuestLine questLine = questLines.get(questLineId);
        if (questLine == null) {
            return new QuestLineProgress(0, 0, null);
        }

        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);
        List<String> questSequence = questLine.getQuestSequence();

        int completed = 0;
        String currentQuestId = null;

        for (String questId : questSequence) {
            if (playerQuest.hasCompletedQuest(questId)) {
                completed++;
            } else if (currentQuestId == null) {
                currentQuestId = questId;
            }
        }

        return new QuestLineProgress(completed, questSequence.size(), currentQuestId);
    }

    /**
     * 获取玩家可接取的主线任务
     */
    public List<Quest> getAvailableMainQuests(UUID playerId) {
        QuestChapter currentChapter = getPlayerCurrentChapter(playerId);
        if (currentChapter == null) {
            return Collections.emptyList();
        }

        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);
        List<Quest> available = new ArrayList<>();

        // 获取章节中的所有任务
        for (String questId : currentChapter.getRequiredQuests()) {
            if (!playerQuest.hasActiveQuest(questId) && !playerQuest.hasCompletedQuest(questId)) {
                Quest quest = questService.getQuest(questId);
                if (quest != null) {
                    // 检查前置任务
                    if (canAcceptQuest(playerId, quest)) {
                        available.add(quest);
                    }
                }
            }
        }

        return available;
    }

    /**
     * 检查是否可以接取任务（前置任务检查）
     */
    private boolean canAcceptQuest(UUID playerId, Quest quest) {
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);

        for (String prerequisiteId : quest.getPrerequisiteQuests()) {
            if (!playerQuest.hasCompletedQuest(prerequisiteId)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 获取所有章节
     */
    public List<QuestChapter> getAllChapters() {
        return new ArrayList<>(chapters.values());
    }

    /**
     * 获取章节
     */
    public QuestChapter getChapter(String chapterId) {
        return chapters.get(chapterId);
    }

    /**
     * 获取任务线
     */
    public QuestLine getQuestLine(String questLineId) {
        return questLines.get(questLineId);
    }

    /**
     * 获取玩家主线进度摘要
     */
    public MainQuestProgress getMainQuestProgress(UUID playerId) {
        int totalChapters = chapters.size();
        int completedChapters = 0;

        for (String chapterId : chapters.keySet()) {
            if (isChapterCompleted(playerId, chapterId)) {
                completedChapters++;
            }
        }

        QuestChapter currentChapter = getPlayerCurrentChapter(playerId);
        String currentChapterName = currentChapter != null ? currentChapter.getName() : "未开始";

        return new MainQuestProgress(completedChapters, totalChapters, currentChapterName);
    }

    /**
     * 章节进度类
     */
    public static class ChapterProgress {
        private final int completed;
        private final int total;

        public ChapterProgress(int completed, int total) {
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

        public boolean isCompleted() {
            return completed >= total && total > 0;
        }

        @Override
        public String toString() {
            return String.format("%d/%d (%.1f%%)", completed, total, getPercentage());
        }
    }

    /**
     * 任务线进度类
     */
    public static class QuestLineProgress {
        private final int completed;
        private final int total;
        private final String currentQuestId;

        public QuestLineProgress(int completed, int total, String currentQuestId) {
            this.completed = completed;
            this.total = total;
            this.currentQuestId = currentQuestId;
        }

        public int getCompleted() {
            return completed;
        }

        public int getTotal() {
            return total;
        }

        public String getCurrentQuestId() {
            return currentQuestId;
        }

        public boolean isCompleted() {
            return completed >= total && total > 0;
        }

        public double getPercentage() {
            return total == 0 ? 0.0 : (double) completed / total * 100.0;
        }

        @Override
        public String toString() {
            return String.format("%d/%d (%.1f%%)", completed, total, getPercentage());
        }
    }

    /**
     * 主线进度类
     */
    public static class MainQuestProgress {
        private final int completedChapters;
        private final int totalChapters;
        private final String currentChapter;

        public MainQuestProgress(int completedChapters, int totalChapters, String currentChapter) {
            this.completedChapters = completedChapters;
            this.totalChapters = totalChapters;
            this.currentChapter = currentChapter;
        }

        public int getCompletedChapters() {
            return completedChapters;
        }

        public int getTotalChapters() {
            return totalChapters;
        }

        public String getCurrentChapter() {
            return currentChapter;
        }

        public double getPercentage() {
            return totalChapters == 0 ? 0.0 : (double) completedChapters / totalChapters * 100.0;
        }

        @Override
        public String toString() {
            return String.format("章节: %d/%d (%.1f%%) - 当前: %s",
                    completedChapters, totalChapters, getPercentage(), currentChapter);
        }
    }
}
