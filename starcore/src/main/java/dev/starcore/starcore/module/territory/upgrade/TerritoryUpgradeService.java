package dev.starcore.starcore.module.territory.upgrade;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.territory.upgrade.model.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Territory Upgrade Service interface.
 * 领地升级系统核心服务接口
 */
public interface TerritoryUpgradeService {

    // ========== 基础查询方法 ==========

    /**
     * Get all available upgrade paths.
     */
    Collection<String> getAvailablePaths();

    /**
     * Get upgrade path definition.
     */
    Optional<UpgradeTierDefinition> getPathDefinition(String pathId);

    /**
     * Get level definition for a path and level.
     */
    Optional<TerritoryUpgradeLevel> getLevelDefinition(String pathId, int level);

    /**
     * Get current level for a nation on a path.
     */
    int getCurrentLevel(NationId nationId, String pathId);

    /**
     * Get total experience for a nation.
     */
    int getTotalExp(NationId nationId);

    /**
     * Get experience spent for a nation.
     */
    int getExpSpent(NationId nationId);

    // ========== 升级检查和验证 ==========

    /**
     * Check if a nation can upgrade on a path.
     */
    UpgradeCheckResult canUpgrade(NationId nationId, String pathId);

    /**
     * Check if a nation can upgrade to a specific level.
     */
    UpgradeCheckResult canUpgradeTo(NationId nationId, String pathId, int targetLevel);

    /**
     * Validate upgrade requirements.
     */
    UpgradeCheckResult validateRequirements(NationId nationId, String pathId);

    /**
     * Get missing prerequisites for upgrading.
     */
    List<String> getMissingPrerequisites(NationId nationId, String pathId);

    // ========== 升级操作 ==========

    /**
     * Start an upgrade process for a nation.
     */
    UpgradeCheckResult startUpgrade(NationId nationId, String pathId);

    /**
     * Cancel an ongoing upgrade.
     */
    boolean cancelUpgrade(NationId nationId, String pathId);

    /**
     * Force complete an upgrade (admin command).
     */
    boolean forceCompleteUpgrade(NationId nationId, String pathId);

    /**
     * Add experience to a nation.
     */
    void addExperience(NationId nationId, int exp, String source);

    /**
     * Add experience with callback.
     */
    void addExperience(NationId nationId, int exp, String source, Consumer<Integer> callback);

    // ========== 进度查询 ==========

    /**
     * Get active upgrade process for a nation.
     */
    Optional<UpgradeProcess> getActiveUpgrade(NationId nationId, String pathId);

    /**
     * Get all active upgrades for a nation.
     */
    Map<String, UpgradeProcess> getActiveUpgrades(NationId nationId);

    /**
     * Check if nation is currently upgrading on a path.
     */
    boolean isUpgrading(NationId nationId, String pathId);

    /**
     * Get progress percentage for active upgrade.
     */
    int getUpgradeProgress(NationId nationId, String pathId);

    // ========== 收益计算 ==========

    /**
     * Get cumulative benefits for a nation on a path.
     */
    UpgradeBenefit getCumulativeBenefits(NationId nationId, String pathId);

    /**
     * Get benefit modifier for a specific type.
     */
    double getBenefitModifier(NationId nationId, String benefitType);

    /**
     * Get claim limit bonus.
     */
    int getClaimLimitBonus(NationId nationId);

    /**
     * Get tax rate modifier.
     */
    double getTaxRateModifier(NationId nationId);

    /**
     * Get resource bonus multiplier.
     */
    double getResourceBonus(NationId nationId);

    // ========== 路径信息 ==========

    /**
     * Get next level definition.
     */
    Optional<TerritoryUpgradeLevel> getNextLevel(NationId nationId, String pathId);

    /**
     * Get exp required for next level.
     */
    int getExpRequiredForNextLevel(NationId nationId, String pathId);

    /**
     * Get progress to next level (0-100).
     */
    int getProgressToNextLevel(NationId nationId, String pathId);

    /**
     * Check if path is maxed.
     */
    boolean isPathMaxed(NationId nationId, String pathId);

    // ========== 管理和统计 ==========

    /**
     * Get summary of upgrade system.
     */
    String getSummary();

    /**
     * Reset nation's upgrade progress.
     */
    void resetProgress(NationId nationId);

    /**
     * Get all nations with upgrades.
     */
    Collection<NationId> getAllUpgradedNations();

    // ========== 配置方法 ==========

    /**
     * Reload upgrade definitions from config.
     */
    void reloadDefinitions();

    /**
     * Get experience from an action source.
     */
    int getExpFromSource(String source, int baseAmount);
}
