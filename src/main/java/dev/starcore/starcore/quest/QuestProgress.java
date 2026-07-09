package dev.starcore.starcore.quest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 任务进度记录类
 * 记录单个任务的详细进度信息
 *
 * @author StarCore Team
 * @since 1.0.0
 */
public class QuestProgress {

    private final UUID playerId;
    private final String questId;
    private final Map<Integer, Integer> objectiveProgress; // 目标索引 -> 进度
    private final long startTime;
    private long lastUpdateTime;
    private boolean completed;
    private long completeTime;

    /**
     * 构造函数
     */
    public QuestProgress(UUID playerId, String questId) {
        this.playerId = playerId;
        this.questId = questId;
        this.objectiveProgress = new HashMap<>();
        this.startTime = System.currentTimeMillis();
        this.lastUpdateTime = startTime;
        this.completed = false;
        this.completeTime = 0;
    }

    /**
     * 更新目标进度
     */
    public void updateObjectiveProgress(int objectiveIndex, int progress) {
        objectiveProgress.put(objectiveIndex, progress);
        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * 增加目标进度
     */
    public void addObjectiveProgress(int objectiveIndex, int amount) {
        int current = objectiveProgress.getOrDefault(objectiveIndex, 0);
        objectiveProgress.put(objectiveIndex, current + amount);
        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * 获取目标进度
     */
    public int getObjectiveProgress(int objectiveIndex) {
        return objectiveProgress.getOrDefault(objectiveIndex, 0);
    }

    /**
     * 标记为完成
     */
    public void markCompleted() {
        this.completed = true;
        this.completeTime = System.currentTimeMillis();
    }

    /**
     * 获取任务用时（毫秒）
     */
    public long getElapsedTime() {
        if (completed) {
            return completeTime - startTime;
        }
        return System.currentTimeMillis() - startTime;
    }

    /**
     * 获取任务用时文本
     */
    public String getElapsedTimeText() {
        long elapsed = getElapsedTime();
        long seconds = elapsed / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%d天%d小时", days, hours % 24);
        } else if (hours > 0) {
            return String.format("%d小时%d分钟", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d分钟", minutes);
        } else {
            return String.format("%d秒", seconds);
        }
    }

    /**
     * 获取上次更新后的时间
     */
    public long getTimeSinceLastUpdate() {
        return System.currentTimeMillis() - lastUpdateTime;
    }

    /**
     * 重置进度
     */
    public void reset() {
        objectiveProgress.clear();
        completed = false;
        completeTime = 0;
        lastUpdateTime = System.currentTimeMillis();
    }

    // Getters

    public UUID getPlayerId() {
        return playerId;
    }

    public String getQuestId() {
        return questId;
    }

    public Map<Integer, Integer> getObjectiveProgress() {
        return new HashMap<>(objectiveProgress);
    }

    public long getStartTime() {
        return startTime;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public boolean isCompleted() {
        return completed;
    }

    public long getCompleteTime() {
        return completeTime;
    }

    @Override
    public String toString() {
        return String.format("QuestProgress{player=%s, quest=%s, completed=%s, elapsed=%s}",
                playerId, questId, completed, getElapsedTimeText());
    }
}
