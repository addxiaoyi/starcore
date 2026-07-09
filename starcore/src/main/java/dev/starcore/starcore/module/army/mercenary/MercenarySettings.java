package dev.starcore.starcore.module.army.mercenary;

import java.util.UUID;

/**
 * 雇佣兵设置
 * 存储雇佣兵的个人偏好设置
 */
public final class MercenarySettings {
    private final UUID playerId;
    private boolean available;                      // 是否接受雇佣
    private int minContractDays;                    // 最小合同天数
    private int maxContractDays;                    // 最大合同天数
    private int minSalaryPerDay;                    // 每日最低薪资
    private java.util.Set<MercenaryType> preferredTypes;  // 偏好的雇佣兵类型

    public MercenarySettings(UUID playerId) {
        this.playerId = playerId;
        this.available = false;
        this.minContractDays = 1;
        this.maxContractDays = 30;
        this.minSalaryPerDay = 100;
        this.preferredTypes = new java.util.HashSet<>();
    }

    public MercenarySettings(
        UUID playerId,
        boolean available,
        int minContractDays,
        int maxContractDays,
        int minSalaryPerDay,
        java.util.Set<MercenaryType> preferredTypes
    ) {
        this.playerId = playerId;
        this.available = available;
        this.minContractDays = Math.max(1, minContractDays);
        this.maxContractDays = Math.max(minContractDays, maxContractDays);
        this.minSalaryPerDay = Math.max(0, minSalaryPerDay);
        this.preferredTypes = preferredTypes != null ? new java.util.HashSet<>(preferredTypes) : new java.util.HashSet<>();
    }

    // ==================== Getters ====================

    public UUID playerId() {
        return playerId;
    }

    public boolean isAvailable() {
        return available;
    }

    public int minContractDays() {
        return minContractDays;
    }

    public int maxContractDays() {
        return maxContractDays;
    }

    public int minSalaryPerDay() {
        return minSalaryPerDay;
    }

    public java.util.Set<MercenaryType> getPreferredTypes() {
        return new java.util.HashSet<>(preferredTypes);
    }

    // ==================== Setters ====================

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setMinContractDays(int minContractDays) {
        this.minContractDays = Math.max(1, minContractDays);
    }

    public void setMaxContractDays(int maxContractDays) {
        this.maxContractDays = Math.max(minContractDays, maxContractDays);
    }

    public void setMinSalaryPerDay(int minSalaryPerDay) {
        this.minSalaryPerDay = Math.max(0, minSalaryPerDay);
    }

    public void setPreferredTypes(java.util.Set<MercenaryType> types) {
        this.preferredTypes = new java.util.HashSet<>(types);
    }

    public void addPreferredType(MercenaryType type) {
        this.preferredTypes.add(type);
    }

    public void removePreferredType(MercenaryType type) {
        this.preferredTypes.remove(type);
    }

    @Override
    public String toString() {
        return String.format("MercenarySettings{playerId=%s, available=%s, minDays=%d, maxDays=%d, minSalary=%d}",
            playerId, available, minContractDays, maxContractDays, minSalaryPerDay);
    }
}