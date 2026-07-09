package dev.starcore.starcore.module.army.espionage;

import dev.starcore.starcore.module.army.espionage.model.*;
import org.bukkit.Location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 间谍服务接口
 */
public interface EspionageService {

    // ==================== 间谍管理 ====================

    /**
     * 训练新间谍
     */
    Spy trainSpy(UUID nationId, String nationName, UUID trainerId, SpyType type);

    /**
     * 获取间谍
     */
    Optional<Spy> getSpy(UUID spyId);

    /**
     * 获取国家的所有间谍
     */
    List<Spy> getNationSpies(UUID nationId);

    /**
     * 解雇间谍
     */
    void dismissSpy(UUID spyId);

    /**
     * 获取间谍总数
     */
    int getSpyCount(UUID nationId);

    // ==================== 行动管理 ====================

    /**
     * 开始间谍行动
     */
    EspionageOperation startOperation(UUID spyId, UUID targetNationId, String targetNationName, OperationType type);

    /**
     * 获取进行中的行动
     */
    List<EspionageOperation> getActiveOperations();

    /**
     * 获取国家的行动历史
     */
    List<EspionageOperation> getNationOperationHistory(UUID nationId);

    /**
     * 获取进行中的行动数量
     */
    int getActiveOperationCount(UUID nationId);

    // ==================== 反间谍 ====================

    /**
     * 检查是否发现间谍
     */
    boolean detectSpy(UUID spyId, UUID targetNationId);

    /**
     * 获取国家的反间谍等级
     */
    int getCounterIntelligenceLevel(UUID nationId);

    // ==================== 行动结果 ====================

    /**
     * 获取行动报告
     */
    Optional<String> getOperationReport(UUID operationId);

    /**
     * 获取行动结果
     */
    Optional<EspionageOperation> getOperation(UUID operationId);

    // ==================== 配置 ====================

    /**
     * 获取配置
     */
    EspionageConfig getConfig();

    /**
     * 行动配置
     */
    record EspionageConfig(
        int maxSpiesPerNation,
        int maxActiveOperationsPerNation,
        int baseDetectionChance,
        int operationCooldownMinutes,
        double sabotageEfficiency,
        double resourceStealRate,
        boolean allowWarOnly
    ) {
        public static EspionageConfig defaults() {
            return new EspionageConfig(20, 5, 30, 60, 0.5, 0.1, false);
        }

        /**
         * 从配置节读取
         */
        public static EspionageConfig fromConfig(org.bukkit.configuration.ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            return new EspionageConfig(
                section.getInt("max-spies-per-nation", 20),
                section.getInt("max-active-operations-per-nation", 5),
                section.getInt("base-detection-chance", 30),
                section.getInt("operation-cooldown-minutes", 60),
                section.getDouble("sabotage-efficiency", 0.5),
                section.getDouble("resource-steal-rate", 0.1),
                section.getBoolean("allow-war-only", false)
            );
        }
    }
}
