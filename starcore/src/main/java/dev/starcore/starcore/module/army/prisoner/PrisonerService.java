package dev.starcore.starcore.module.army.prisoner;

import dev.starcore.starcore.module.army.prisoner.model.PrisonerOfWar;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * 俘虏服务接口
 * 定义俘虏系统的核心操作
 */
public interface PrisonerService {

    // ==================== 俘虏管理 ====================

    /**
     * 俘虏一名玩家
     *
     * @param captorPlayerId  捕获者玩家ID
     * @param captorPlayerName 捕获者名称
     * @param targetPlayerId  目标玩家ID
     * @param targetPlayerName 目标玩家名称
     * @param targetNationId  目标玩家国家ID
     * @param targetNationName 目标玩家国家名称
     * @param captorNationId  捕获者国家ID
     * @param captorNationName 捕获者国家名称
     * @param ransom 赎金
     * @return 俘虏记录
     */
    PrisonerOfWar capturePrisoner(
        UUID captorPlayerId,
        String captorPlayerName,
        UUID targetPlayerId,
        String targetPlayerName,
        UUID targetNationId,
        String targetNationName,
        UUID captorNationId,
        String captorNationName,
        int ransom
    );

    /**
     * 俘虏一名玩家（带战斗关联）
     */
    PrisonerOfWar capturePrisonerWithBattle(
        UUID captorPlayerId,
        String captorPlayerName,
        UUID targetPlayerId,
        String targetPlayerName,
        UUID targetNationId,
        String targetNationName,
        UUID captorNationId,
        String captorNationName,
        int ransom,
        UUID battleId
    );

    // ==================== 释放管理 ====================

    /**
     * 释放俘虏
     *
     * @param prisonerId 俘虏ID
     * @param releaserId 释放者ID
     * @return 释放后的俘虏记录
     */
    PrisonerOfWar releasePrisoner(UUID prisonerId, UUID releaserId);

    /**
     * 处决俘虏
     *
     * @param prisonerId 俘虏ID
     * @param executorId 处决者ID
     * @return 处决后的俘虏记录
     */
    PrisonerOfWar executePrisoner(UUID prisonerId, UUID executorId);

    /**
     * 接受赎金释放俘虏
     *
     * @param prisonerId 俘虏ID
     * @param payerId 付款者ID
     * @return 释放后的俘虏记录
     */
    PrisonerOfWar releaseOnRansom(UUID prisonerId, UUID payerId);

    /**
     * 交换俘虏
     *
     * @param prisonerId1 第一名俘虏ID
     * @param prisonerId2 第二名俘虏ID
     * @param exchangerId 交换操作者ID
     * @return 交换后的俘虏记录数组
     */
    PrisonerOfWar[] exchangePrisoners(UUID prisonerId1, UUID prisonerId2, UUID exchangerId);

    // ==================== 逃跑系统 ====================

    /**
     * 开始逃跑
     *
     * @param prisonerId 俘虏ID
     * @return 更新后的俘虏记录
     */
    PrisonerOfWar startEscape(UUID prisonerId);

    /**
     * 完成逃跑
     *
     * @param prisonerId 俘虏ID
     * @return 更新后的俘虏记录
     */
    PrisonerOfWar completeEscape(UUID prisonerId);

    // ==================== 查询接口 ====================

    /**
     * 获取俘虏记录
     *
     * @param prisonerId 俘虏ID
     * @return 俘虏记录（如果存在）
     */
    Optional<PrisonerOfWar> getPrisoner(UUID prisonerId);

    /**
     * 获取玩家是否为俘虏
     *
     * @param playerId 玩家ID
     * @return 俘虏记录（如果被俘虏）
     */
    Optional<PrisonerOfWar> getPrisonerByPlayer(UUID playerId);

    /**
     * 获取国家的所有俘虏
     *
     * @param nationId 国家ID
     * @return 俘虏列表
     */
    Collection<PrisonerOfWar> getNationPrisoners(UUID nationId);

    /**
     * 获取国家的所有被俘成员
     *
     * @param nationId 国家ID
     * @return 被俘成员列表
     */
    Collection<PrisonerOfWar> getNationCapturedMembers(UUID nationId);

    // ==================== 俘虏转移 ====================

    /**
     * 更新俘虏的俘虏国信息
     *
     * @param prisonerId 俘虏ID
     * @param newNationId 新俘虏国ID
     * @param newNationName 新俘虏国名称
     * @param newCaptorPlayerId 新捕获者ID
     * @param newCaptorPlayerName 新捕获者名称
     * @return 更新后的俘虏记录
     */
    PrisonerOfWar transferPrisoner(
        UUID prisonerId,
        UUID newNationId,
        String newNationName,
        UUID newCaptorPlayerId,
        String newCaptorPlayerName
    );

    /**
     * 设置俘虏备注
     *
     * @param prisonerId 俘虏ID
     * @param notes 新备注
     * @return 更新后的俘虏记录
     */
    PrisonerOfWar setPrisonerNotes(UUID prisonerId, String notes);

    // ==================== 状态检查 ====================

    /**
     * 检查玩家是否为俘虏
     *
     * @param playerId 玩家ID
     * @return 是否为俘虏
     */
    boolean isPrisoner(UUID playerId);

    /**
     * 检查两个国家之间是否存在俘虏关系
     *
     * @param nationId1 国家ID1
     * @param nationId2 国家ID2
     * @return 是否存在俘虏关系
     */
    boolean hasPrisonerRelation(UUID nationId1, UUID nationId2);

    // ==================== 统计 ====================

    /**
     * 获取俘虏总数
     *
     * @return 当前俘虏数量
     */
    int getTotalPrisonerCount();

    /**
     * 获取某个国家的俘虏总数
     *
     * @param nationId 国家ID
     * @return 俘虏数量
     */
    int getNationPrisonerCount(UUID nationId);

    // ==================== 数据管理 ====================

    /**
     * 移除俘虏记录
     *
     * @param prisonerId 俘虏ID
     */
    void removePrisoner(UUID prisonerId);

    /**
     * 保存所有俘虏数据
     */
    void saveAll();

    /**
     * 获取配置
     */
    PrisonerConfig getConfig();

    /**
     * 获取模块摘要
     */
    String summary();

    /**
     * 关闭服务（保存数据）
     */
    void shutdown();

    /**
     * 俘虏系统配置
     */
    record PrisonerConfig(
        int maxPrisonersPerNation,        // 每个国家最多俘虏数
        int maxCapturesPerPlayer,         // 每个玩家最多同时俘虏数
        int baseRansom,                   // 基础赎金
        int ransomPerLevel,               // 每级增加赎金
        long captureDurationMinutes,      // 俘虏持续时间（分钟），-1表示永久
        long escapeChancePerHour,         // 每小时逃亡几率（百分比）
        boolean allowExecution,           // 是否允许处决
        boolean allowExchange,           // 是否允许交换俘虏
        boolean autoReleaseOnWarEnd       // 战争结束时自动释放俘虏
    ) {
        public static PrisonerConfig defaults() {
            return new PrisonerConfig(
                50,     // maxPrisonersPerNation
                10,     // maxCapturesPerPlayer
                500,    // baseRansom
                100,    // ransomPerLevel
                -1,     // captureDurationMinutes (permanent)
                5,      // escapeChancePerHour (5%)
                true,   // allowExecution
                true,   // allowExchange
                true    // autoReleaseOnWarEnd
            );
        }

        public static PrisonerConfig fromConfig(org.bukkit.configuration.ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            return new PrisonerConfig(
                section.getInt("max-prisoners-per-nation", 50),
                section.getInt("max-captures-per-player", 10),
                section.getInt("base-ransom", 500),
                section.getInt("ransom-per-level", 100),
                section.getLong("capture-duration-minutes", -1),
                section.getLong("escape-chance-per-hour", 5),
                section.getBoolean("allow-execution", true),
                section.getBoolean("allow-exchange", true),
                section.getBoolean("auto-release-on-war-end", true)
            );
        }
    }
}
