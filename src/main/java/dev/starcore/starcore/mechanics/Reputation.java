package dev.starcore.starcore.mechanics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 声望记录
 * 存储玩家的声望数据
 */
public class Reputation {

    private final UUID playerId;
    private int totalReputation;
    private final Map<ReputationSource, Integer> reputationBySource;
    private ReputationLevel level;
    private long lastUpdate;

    public Reputation(UUID playerId) {
        this.playerId = playerId;
        this.totalReputation = 0;
        this.reputationBySource = new HashMap<>();
        this.level = ReputationLevel.NEWCOMER;
        this.lastUpdate = System.currentTimeMillis();

        // 初始化所有来源的声望为0
        for (ReputationSource source : ReputationSource.values()) {
            reputationBySource.put(source, 0);
        }
    }

    /**
     * 增加声望
     */
    public void addReputation(ReputationSource source, int amount) {
        if (amount <= 0) return;

        // 计算实际获得的声望（带倍率）
        int actualAmount = source.calculate(amount);

        // 更新总声望
        this.totalReputation += actualAmount;

        // 更新来源声望
        int currentSourceRep = reputationBySource.getOrDefault(source, 0);
        reputationBySource.put(source, currentSourceRep + actualAmount);

        // 更新等级
        updateLevel();

        // 更新时间
        this.lastUpdate = System.currentTimeMillis();
    }

    /**
     * 减少声望
     */
    public void removeReputation(int amount) {
        if (amount <= 0) return;

        this.totalReputation = Math.max(0, this.totalReputation - amount);
        updateLevel();
        this.lastUpdate = System.currentTimeMillis();
    }

    /**
     * 更新声望等级
     */
    private void updateLevel() {
        ReputationLevel newLevel = ReputationLevel.fromReputation(totalReputation);
        if (newLevel != this.level) {
            this.level = newLevel;
        }
    }

    /**
     * 获取指定来源的声望
     */
    public int getReputationFromSource(ReputationSource source) {
        return reputationBySource.getOrDefault(source, 0);
    }

    /**
     * 获取距离下一级的进度百分比
     */
    public double getProgressToNextLevel() {
        int currentMin = level.getMinReputation();
        int nextMin = level.getRequiredForNext();

        if (nextMin == Integer.MAX_VALUE) {
            return 100.0; // 已是最高级
        }

        int range = nextMin - currentMin;
        int progress = totalReputation - currentMin;

        return (progress * 100.0) / range;
    }

    /**
     * 获取距离下一级还需要的声望
     */
    public int getReputationToNextLevel() {
        int nextMin = level.getRequiredForNext();
        if (nextMin == Integer.MAX_VALUE) {
            return 0; // 已是最高级
        }
        return Math.max(0, nextMin - totalReputation);
    }

    /**
     * 获取声望排名描述
     */
    public String getRankDescription() {
        return level.getColoredName() + " §7(" + totalReputation + " 声望)";
    }

    /**
     * 获取详细统计信息
     */
    public String getDetailedStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("§6§l声望统计\n");
        sb.append("§e总声望: §f").append(totalReputation).append("\n");
        sb.append("§e等级: ").append(level.getColoredName()).append("\n");

        if (level.getRequiredForNext() != Integer.MAX_VALUE) {
            sb.append("§e升级进度: §f")
              .append(String.format("%.1f", getProgressToNextLevel()))
              .append("% §7(还需 ")
              .append(getReputationToNextLevel())
              .append(" 声望)\n");
        } else {
            sb.append("§e升级进度: §a已达最高等级\n");
        }

        sb.append("\n§6来源统计:\n");
        for (ReputationSource source : ReputationSource.values()) {
            int amount = reputationBySource.get(source);
            if (amount > 0) {
                sb.append("  §7- §f")
                  .append(source.getDisplayName())
                  .append(": §a")
                  .append(amount)
                  .append("\n");
            }
        }

        return sb.toString();
    }

    // Getters

    public UUID getPlayerId() {
        return playerId;
    }

    public int getTotalReputation() {
        return totalReputation;
    }

    public ReputationLevel getLevel() {
        return level;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public Map<ReputationSource, Integer> getReputationBySource() {
        return new HashMap<>(reputationBySource);
    }

    // Setters (用于数据库加载)

    public void setTotalReputation(int totalReputation) {
        this.totalReputation = totalReputation;
        updateLevel();
    }

    public void setReputationFromSource(ReputationSource source, int amount) {
        reputationBySource.put(source, amount);
    }
}
