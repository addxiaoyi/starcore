package dev.starcore.starcore.module.resource.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 国家战略储备
 * 国家储存的战略资源
 */
public final class NationalReserve {
    private final NationId nationId;
    private final Map<String, Long> reserves;
    private final Map<String, Long> reserveGoals;

    public NationalReserve(NationId nationId) {
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.reserves = new LinkedHashMap<>();
        this.reserveGoals = new LinkedHashMap<>();
    }

    /**
     * 获取国家ID
     */
    public NationId nationId() {
        return nationId;
    }

    /**
     * 获取所有储备
     */
    public Map<String, Long> reserves() {
        return new LinkedHashMap<>(reserves);
    }

    /**
     * 获取指定资源的储备量
     */
    public long getReserve(String resourceId) {
        return reserves.getOrDefault(resourceId, 0L);
    }

    /**
     * 添加储备
     */
    public void addReserve(String resourceId, long amount) {
        if (amount <= 0) return;
        reserves.merge(resourceId, amount, Long::sum);
    }

    /**
     * 消耗储备
     * @return 是否成功消耗
     */
    public boolean consumeReserve(String resourceId, long amount) {
        if (amount <= 0) return false;
        long current = reserves.getOrDefault(resourceId, 0L);
        if (current < amount) {
            return false;
        }
        reserves.put(resourceId, current - amount);
        return true;
    }

    /**
     * 设置储备目标
     */
    public void setReserveGoal(String resourceId, long goal) {
        if (goal <= 0) {
            reserveGoals.remove(resourceId);
        } else {
            reserveGoals.put(resourceId, goal);
        }
    }

    /**
     * 获取储备目标
     */
    public long getReserveGoal(String resourceId) {
        return reserveGoals.getOrDefault(resourceId, 0L);
    }

    /**
     * 获取所有储备目标
     */
    public Map<String, Long> reserveGoals() {
        return new LinkedHashMap<>(reserveGoals);
    }

    /**
     * 检查是否达到储备目标
     */
    public boolean hasMetGoal(String resourceId) {
        long goal = reserveGoals.getOrDefault(resourceId, 0L);
        if (goal == 0) return true;
        return reserves.getOrDefault(resourceId, 0L) >= goal;
    }

    /**
     * 计算储备完成百分比
     */
    public double getGoalProgress(String resourceId) {
        long goal = reserveGoals.getOrDefault(resourceId, 0L);
        if (goal == 0) return 100.0;
        long current = reserves.getOrDefault(resourceId, 0L);
        return Math.min(100.0, (current * 100.0) / goal);
    }

    /**
     * 获取缺少的储备量
     */
    public long getShortfall(String resourceId) {
        long goal = reserveGoals.getOrDefault(resourceId, 0L);
        long current = reserves.getOrDefault(resourceId, 0L);
        return Math.max(0, goal - current);
    }

    /**
     * 计算总体储备完成度
     */
    public double getOverallProgress() {
        if (reserveGoals.isEmpty()) {
            return 100.0;
        }
        double totalProgress = 0.0;
        for (String resourceId : reserveGoals.keySet()) {
            totalProgress += getGoalProgress(resourceId);
        }
        return totalProgress / reserveGoals.size();
    }

    /**
     * 获取未完成的储备目标数量
     */
    public int getUnmetGoalsCount() {
        int count = 0;
        for (String resourceId : reserveGoals.keySet()) {
            if (!hasMetGoal(resourceId)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NationalReserve that = (NationalReserve) o;
        return nationId.equals(that.nationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nationId);
    }
}
