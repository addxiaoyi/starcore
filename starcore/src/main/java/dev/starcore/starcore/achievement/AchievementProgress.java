package dev.starcore.starcore.achievement;
import java.util.Optional;

import org.bukkit.NamespacedKey;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 成就进度追踪器
 * 追踪玩家对每个成就的完成进度
 */
public final class AchievementProgress {
    private final UUID playerId;

    // 已完成的成就
    private final Set<NamespacedKey> completedAchievements = ConcurrentHashMap.newKeySet();

    // 进行中的成就及其进度
    private final Map<NamespacedKey, ProgressData> inProgressAchievements = new ConcurrentHashMap<>();

    // 每个触发类型的当前计数
    private final Map<String, Integer> triggerCounts = new ConcurrentHashMap<>();

    public AchievementProgress(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * 检查成就是否已完成
     */
    public boolean isCompleted(NamespacedKey key) {
        return completedAchievements.contains(key);
    }

    /**
     * 标记成就为完成
     */
    public void markCompleted(NamespacedKey key) {
        completedAchievements.add(key);
        inProgressAchievements.remove(key);
    }

    /**
     * 获取已完成的成就集合
     */
    public Set<NamespacedKey> getCompletedAchievements() {
        return Collections.unmodifiableSet(completedAchievements);
    }

    /**
     * 增加触发器计数
     */
    public int incrementTriggerCount(String triggerKey) {
        return incrementTriggerCount(triggerKey, 1);
    }

    /**
     * 增加触发器计数
     */
    public int incrementTriggerCount(String triggerKey, int amount) {
        return triggerCounts.merge(triggerKey, amount, Integer::sum);
    }

    /**
     * 获取触发器计数
     */
    public int getTriggerCount(String triggerKey) {
        return triggerCounts.getOrDefault(triggerKey, 0);
    }

    /**
     * 设置触发器计数
     */
    public void setTriggerCount(String triggerKey, int count) {
        triggerCounts.put(triggerKey, count);
    }

    /**
     * 获取所有触发器计数
     */
    public Map<String, Integer> getAllTriggerCounts() {
        return Collections.unmodifiableMap(new HashMap<>(triggerCounts));
    }

    /**
     * 更新成就进度
     */
    public void updateProgress(NamespacedKey achievementKey, int current, int target) {
        inProgressAchievements.put(achievementKey, new ProgressData(current, target, System.currentTimeMillis()));
    }

    /**
     * 获取成就进度
     */
    public Optional<ProgressData> getProgress(NamespacedKey achievementKey) {
        return Optional.ofNullable(inProgressAchievements.get(achievementKey));
    }

    /**
     * 获取进行中的成就
     */
    public Collection<ProgressData> getInProgressAchievements() {
        return inProgressAchievements.values();
    }

    /**
     * 检查父成就是否完成
     */
    public boolean isParentCompleted(NamespacedKey parentKey) {
        if (parentKey == null) {
            return true;
        }
        return completedAchievements.contains(parentKey);
    }

    /**
     * 获取完成进度百分比
     */
    public double getCompletionPercentage() {
        // 这个需要在外部根据总成就数计算
        return 0;
    }

    /**
     * 序列化数据
     */
    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("playerId", playerId.toString());
        data.put("completed", new ArrayList<>(completedAchievements.stream()
            .map(Object::toString)
            .toList()));
        data.put("triggerCounts", new HashMap<>(triggerCounts));
        return data;
    }

    /**
     * 反序列化数据
     */
    @SuppressWarnings("unchecked")
    public static AchievementProgress deserialize(UUID playerId, Map<String, Object> data) {
        AchievementProgress progress = new AchievementProgress(playerId);

        List<String> completed = (List<String>) data.get("completed");
        if (completed != null) {
            for (String key : completed) {
                String[] parts = key.split(":", 2);
                if (parts.length == 2) {
                    progress.completedAchievements.add(new NamespacedKey(parts[0], parts[1]));
                }
            }
        }

        Map<String, Integer> counts = (Map<String, Integer>) data.get("triggerCounts");
        if (counts != null) {
            progress.triggerCounts.putAll(counts);
        }

        return progress;
    }

    /**
     * 进度数据
     */
    public record ProgressData(int current, int target, long lastUpdated) {
        public double getPercentage() {
            if (target <= 0) return 0;
            return Math.min(100.0, (current * 100.0) / target);
        }

        public boolean isComplete() {
            return current >= target;
        }

        public ProgressData withIncrementedCurrent() {
            return new ProgressData(current + 1, target, System.currentTimeMillis());
        }
    }
}
