package dev.starcore.starcore.module.territory.upgrade;

import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.territory.upgrade.model.*;

import java.util.List;
import java.util.Optional;

/**
 * Validates upgrade requirements and conditions.
 * 验证升级需求和条件
 */
public class UpgradeValidator {

    private final TerritoryUpgradeService service;
    private final NationService nationService;
    private final UpgradeDefinitionLoader definitionLoader;

    public UpgradeValidator(
            TerritoryUpgradeService service,
            ServiceRegistry serviceRegistry,
            UpgradeDefinitionLoader definitionLoader) {
        this.service = service;
        this.definitionLoader = definitionLoader;
        this.nationService = serviceRegistry.find(NationService.class).orElse(null);
    }

    /**
     * Validate if a nation can upgrade on a path.
     */
    public UpgradeCheckResult validate(NationId nationId, String pathId) {
        // 检查路径是否存在
        UpgradeTierDefinition pathDef = definitionLoader.getPath(pathId);
        if (pathDef == null) {
            return UpgradeCheckResult.failure(
                "升级路径不存在: " + pathId,
                UpgradeCheckError.INVALID_PATH
            );
        }

        // 检查国家是否存在
        if (nationService == null || nationService.nationById(nationId).isEmpty()) {
            return UpgradeCheckResult.failure(
                "国家不存在",
                UpgradeCheckError.NATION_NOT_FOUND
            );
        }

        // 检查是否已经最大等级
        int currentLevel = service.getCurrentLevel(nationId, pathId);
        if (currentLevel >= pathDef.maxLevel()) {
            return UpgradeCheckResult.failure(
                "已达到最高等级 (" + currentLevel + "/" + pathDef.maxLevel() + ")",
                UpgradeCheckError.ALREADY_MAXED
            );
        }

        // 检查是否有进行中的升级
        if (service.isUpgrading(nationId, pathId)) {
            return UpgradeCheckResult.failure(
                "升级正在进行中",
                UpgradeCheckError.UPGRADE_IN_PROGRESS
            );
        }

        // 检查经验值是否足够
        int currentExp = service.getTotalExp(nationId);
        Optional<TerritoryUpgradeLevel> nextLevelOpt = service.getNextLevel(nationId, pathId);
        if (nextLevelOpt.isEmpty()) {
            return UpgradeCheckResult.failure(
                "无法获取下一等级信息",
                UpgradeCheckError.INVALID_LEVEL
            );
        }

        TerritoryUpgradeLevel nextLevel = nextLevelOpt.get();
        int requiredExp = nextLevel.expRequired();
        int totalExpSpent = service.getExpSpent(nationId);

        // 可用经验 = 总经验 - 已消耗经验
        int availableExp = currentExp - totalExpSpent;
        if (availableExp < requiredExp) {
            return UpgradeCheckResult.failure(
                String.format("经验值不足: 需要 %d, 当前可用 %d", requiredExp, availableExp),
                UpgradeCheckError.NOT_ENOUGH_EXP
            );
        }

        // 检查前置条件
        List<String> missingPrereqs = service.getMissingPrerequisites(nationId, pathId);
        if (!missingPrereqs.isEmpty()) {
            return UpgradeCheckResult.failure(
                "缺少前置升级: " + String.join(", ", missingPrereqs),
                UpgradeCheckError.MISSING_PREREQUISITE
            );
        }

        return UpgradeCheckResult.success();
    }

    /**
     * Validate if a nation can upgrade to a specific level.
     */
    public UpgradeCheckResult validateLevel(NationId nationId, String pathId, int targetLevel) {
        // 检查路径是否存在
        UpgradeTierDefinition pathDef = definitionLoader.getPath(pathId);
        if (pathDef == null) {
            return UpgradeCheckResult.failure(
                "升级路径不存在: " + pathId,
                UpgradeCheckError.INVALID_PATH
            );
        }

        // 检查等级是否存在
        TerritoryUpgradeLevel levelDef = pathDef.getLevel(targetLevel);
        if (levelDef == null) {
            return UpgradeCheckResult.failure(
                "等级不存在: " + targetLevel,
                UpgradeCheckError.INVALID_LEVEL
            );
        }

        // 检查当前等级
        int currentLevel = service.getCurrentLevel(nationId, pathId);
        if (currentLevel >= targetLevel) {
            return UpgradeCheckResult.failure(
                "该等级已达成",
                UpgradeCheckError.LEVEL_ALREADY_ACHIEVED
            );
        }

        // 检查是否超过最大等级
        if (targetLevel > pathDef.maxLevel()) {
            return UpgradeCheckResult.failure(
                "等级超过最大限制: " + targetLevel + " > " + pathDef.maxLevel(),
                UpgradeCheckError.INVALID_LEVEL
            );
        }

        // 检查是否有进行中的升级
        if (service.isUpgrading(nationId, pathId)) {
            return UpgradeCheckResult.failure(
                "升级正在进行中",
                UpgradeCheckError.UPGRADE_IN_PROGRESS
            );
        }

        // 计算达到目标等级所需的总经验
        int requiredExp = calculateExpToReachLevel(nationId, pathId, targetLevel);
        int availableExp = service.getTotalExp(nationId) - service.getExpSpent(nationId);

        if (availableExp < requiredExp) {
            return UpgradeCheckResult.failure(
                String.format("经验值不足: 需要 %d, 当前可用 %d", requiredExp, availableExp),
                UpgradeCheckError.NOT_ENOUGH_EXP
            );
        }

        return UpgradeCheckResult.success();
    }

    /**
     * Calculate total exp needed to reach a specific level.
     */
    private int calculateExpToReachLevel(NationId nationId, String pathId, int targetLevel) {
        int totalExp = 0;
        UpgradeTierDefinition pathDef = definitionLoader.getPath(pathId);
        if (pathDef == null) {
            return Integer.MAX_VALUE;
        }

        int currentLevel = service.getCurrentLevel(nationId, pathId);
        for (int level = currentLevel + 1; level <= targetLevel; level++) {
            TerritoryUpgradeLevel levelDef = pathDef.getLevel(level);
            if (levelDef != null) {
                totalExp += levelDef.expRequired();
            }
        }

        return totalExp;
    }

    /**
     * Get missing prerequisites for a path.
     */
    public List<String> getMissingPrerequisites(NationId nationId, String pathId) {
        UpgradeTierDefinition pathDef = definitionLoader.getPath(pathId);
        if (pathDef == null) {
            return List.of();
        }

        int currentLevel = service.getCurrentLevel(nationId, pathId);
        TerritoryUpgradeLevel nextLevel = pathDef.getLevel(currentLevel + 1);
        if (nextLevel == null) {
            return List.of();
        }

        return nextLevel.prerequisites().stream()
            .filter(prereq -> {
                String[] parts = prereq.split(":");
                String prereqPath = parts[0];
                int prereqLevel = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                return service.getCurrentLevel(nationId, prereqPath) < prereqLevel;
            })
            .toList();
    }

    /**
     * Check if nation has sufficient permissions.
     */
    public UpgradeCheckResult checkPermissions(NationId nationId, String pathId) {
        UpgradeTierDefinition pathDef = definitionLoader.getPath(pathId);
        if (pathDef == null) {
            return UpgradeCheckResult.success();
        }

        int currentLevel = service.getCurrentLevel(nationId, pathId);
        TerritoryUpgradeLevel nextLevel = pathDef.getLevel(currentLevel + 1);
        if (nextLevel == null) {
            return UpgradeCheckResult.success();
        }

        // 如果没有权限需求，直接返回成功
        if (nextLevel.unlockPermissions().isEmpty()) {
            return UpgradeCheckResult.success();
        }

        // 检查权限（这里简化处理，实际可能需要检查玩家权限）
        // 权限检查在命令层进行
        return UpgradeCheckResult.success();
    }
}
