package dev.starcore.starcore.module.territory.upgrade.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the upgrade progress for a nation.
 * 国家的升级进度
 */
public class UpgradeProgressData {
    private final NationId nationId;
    private int totalExp;
    private int totalExpSpent;
    private Map<String, Integer> pathLevels; // pathId -> current level
    private Map<String, UpgradeProcess> activeUpgrades; // pathId -> process
    private long lastUpdated;

    public UpgradeProgressData(NationId nationId) {
        this.nationId = Objects.requireNonNull(nationId, "nationId cannot be null");
        this.pathLevels = new ConcurrentHashMap<>();
        this.activeUpgrades = new ConcurrentHashMap<>();
        this.totalExp = 0;
        this.totalExpSpent = 0;
        this.lastUpdated = System.currentTimeMillis();
    }

    // Getters
    public NationId nationId() {
        return nationId;
    }

    public int totalExp() {
        return totalExp;
    }

    public int totalExpSpent() {
        return totalExpSpent;
    }

    public Map<String, Integer> pathLevels() {
        return Map.copyOf(pathLevels);
    }

    public Map<String, UpgradeProcess> activeUpgrades() {
        return Map.copyOf(activeUpgrades);
    }

    public long lastUpdated() {
        return lastUpdated;
    }

    // Setters with state tracking
    public void setTotalExp(int totalExp) {
        this.totalExp = Math.max(0, totalExp);
        this.lastUpdated = System.currentTimeMillis();
    }

    public void addExp(int exp) {
        if (exp > 0) {
            this.totalExp += exp;
            this.lastUpdated = System.currentTimeMillis();
        }
    }

    public void spendExp(int exp) {
        if (exp > 0 && exp <= this.totalExp) {
            this.totalExp -= exp;
            this.totalExpSpent += exp;
            this.lastUpdated = System.currentTimeMillis();
        }
    }

    public void setPathLevel(String pathId, int level) {
        pathLevels.put(pathId.toLowerCase(), Math.max(0, level));
        this.lastUpdated = System.currentTimeMillis();
    }

    public int getPathLevel(String pathId) {
        return pathLevels.getOrDefault(pathId.toLowerCase(), 0);
    }

    public void addActiveUpgrade(String pathId, UpgradeProcess process) {
        activeUpgrades.put(pathId.toLowerCase(), process);
        this.lastUpdated = System.currentTimeMillis();
    }

    public void removeActiveUpgrade(String pathId) {
        activeUpgrades.remove(pathId.toLowerCase());
        this.lastUpdated = System.currentTimeMillis();
    }

    public void updateActiveUpgrade(String pathId, UpgradeProcess process) {
        activeUpgrades.put(pathId.toLowerCase(), process);
        this.lastUpdated = System.currentTimeMillis();
    }

    public boolean hasActiveUpgrade(String pathId) {
        return activeUpgrades.containsKey(pathId.toLowerCase());
    }

    public UpgradeProcess getActiveUpgrade(String pathId) {
        return activeUpgrades.get(pathId.toLowerCase());
    }

    public boolean isUpgrading(String pathId) {
        UpgradeProcess process = activeUpgrades.get(pathId.toLowerCase());
        return process != null && !process.isCompleted();
    }

    public void clearAllActiveUpgrades() {
        activeUpgrades.clear();
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Get total level across all paths.
     */
    public int getTotalLevel() {
        return pathLevels.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Get average level across all paths.
     */
    public double getAverageLevel() {
        if (pathLevels.isEmpty()) {
            return 0.0;
        }
        return pathLevels.values().stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }
}
