package dev.starcore.starcore.module.combat;

import dev.starcore.starcore.module.combat.config.CombatConfig;
import dev.starcore.starcore.module.combat.model.Battlefield;
import dev.starcore.starcore.module.combat.model.Battlefield.BattlefieldType;
import dev.starcore.starcore.module.combat.model.Buff;
import dev.starcore.starcore.module.combat.model.CombatSession;
import dev.starcore.starcore.module.combat.model.CombatSession.CombatSessionType;
import dev.starcore.starcore.module.combat.model.CombatTag;
import dev.starcore.starcore.module.combat.model.CombatTag.CombatTagType;
import dev.starcore.starcore.module.combat.model.PlayerCombatState;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * 战斗服务接口 - 定义战斗系统的核心功能
 *
 * 提供完整的实时战斗系统功能，包括：
 * - 战场管理（创建、加入、开始、结束）
 * - 战斗标记和状态追踪
 * - 伤害计算和Buff系统
 * - 战斗统计和历史记录
 */
public interface CombatService {

    // ==================== 战场管理 ====================

    /**
     * 创建战场 - 基于国家对立
     *
     * @param name 战场名称
     * @param nation1 第一个国家ID
     * @param nation2 第二个国家ID
     * @param center 战场中心位置
     * @param radius 战场半径
     * @param type 战场类型
     * @return 创建的战场对象
     */
    Battlefield createBattlefield(String name, NationId nation1, NationId nation2,
                                   Location center, double radius, BattlefieldType type);

    /**
     * 创建自由PVP战场
     *
     * @param name 战场名称
     * @param center 战场中心位置
     * @param radius 战场半径
     * @param type 战场类型
     * @return 创建的战场对象
     */
    Battlefield createBattlefield(String name, Location center, double radius, BattlefieldType type);

    /**
     * 加入战场 - 国家加入
     *
     * @param battlefieldId 战场ID
     * @param nationId 要加入的国家ID
     * @return 是否成功加入
     */
    boolean joinBattlefield(UUID battlefieldId, NationId nationId);

    /**
     * 玩家加入战场
     *
     * @param playerId 玩家ID
     * @param battlefield 战场对象
     */
    void addPlayerToBattlefield(UUID playerId, Battlefield battlefield);

    /**
     * 玩家离开战场
     *
     * @param playerId 玩家ID
     * @param battlefield 战场对象
     */
    void removePlayerFromBattlefield(UUID playerId, Battlefield battlefield);

    /**
     * 开始战斗
     *
     * @param battlefieldId 战场ID
     * @return 是否成功开始
     */
    boolean startBattle(UUID battlefieldId);

    /**
     * 结束战斗
     *
     * @param battlefieldId 战场ID
     * @param winner 获胜方国家ID（可选，表示平局）
     * @return 战场摘要信息
     */
    Battlefield.BattlefieldSummary endBattle(UUID battlefieldId, NationId winner);

    /**
     * 获取战场状态
     *
     * @param battlefieldId 战场ID
     * @return 战场摘要信息
     */
    Optional<Battlefield.BattlefieldSummary> getBattlefieldStatus(UUID battlefieldId);

    /**
     * 获取玩家所在的战场
     *
     * @param location 位置
     * @return 战场（如果存在）
     */
    Optional<Battlefield> getBattlefieldAt(Location location);

    /**
     * 获取所有活跃战场
     */
    Collection<Battlefield> getActiveBattlefields();

    /**
     * 获取所有战场
     */
    Collection<Battlefield> getAllBattlefields();

    // ==================== 战斗标记 ====================

    /**
     * 给玩家添加战斗标记
     */
    CombatTag tagPlayer(UUID playerId, UUID taggerId, CombatTagType type);

    /**
     * 给玩家添加战斗标记（指定超时时间）
     */
    CombatTag tagPlayer(UUID playerId, UUID taggerId, CombatTagType type, long timeoutMs);

    /**
     * 清除玩家的战斗标记
     */
    void untagPlayer(UUID playerId);

    /**
     * 检查玩家是否被标记
     */
    boolean isTagged(UUID playerId);

    /**
     * 检查玩家是否处于战斗中
     */
    boolean isInCombat(UUID playerId);

    /**
     * 获取玩家的战斗状态
     */
    Optional<PlayerCombatState> getPlayerState(UUID playerId);

    // ==================== 伤害计算 ====================

    /**
     * 计算伤害 - 基于攻击者、防御者和武器
     *
     * @param attacker 攻击者玩家
     * @param defender 防御者玩家
     * @param weapon 武器名称（用于查找武器加成）
     * @return 计算后的伤害值
     */
    double calculateDamage(Player attacker, Player defender, String weapon);

    /**
     * 记录伤害
     */
    void recordDamage(Player attacker, Player victim, double damage);

    /**
     * 记录击杀
     */
    void recordKill(UUID killerId, UUID victimId, CombatSession.CombatEndReason reason);

    // ==================== Buff系统 ====================

    /**
     * 应用Buff到目标
     *
     * @param target 目标玩家ID
     * @param buff Buff类型
     * @param duration 持续时间（毫秒）
     * @return 创建的Buff对象
     */
    Buff applyBuff(UUID target, Buff.BuffType buff, long duration);

    /**
     * 移除Buff
     *
     * @param target 目标玩家ID
     * @param buff 要移除的Buff类型
     */
    void removeBuff(UUID target, Buff.BuffType buff);

    /**
     * 获取玩家当前的所有Buff
     */
    Collection<Buff> getActiveBuffs(UUID playerId);

    /**
     * 检查玩家是否有特定Buff
     */
    boolean hasBuff(UUID playerId, Buff.BuffType buffType);

    // ==================== 战斗会话 ====================

    /**
     * 获取玩家的战斗会话
     */
    Optional<CombatSession> getPlayerSession(UUID playerId);

    /**
     * 获取所有活跃的战斗会话
     */
    Collection<CombatSession> getActiveSessions();

    // ==================== 统计和配置 ====================

    /**
     * 获取战斗统计
     */
    CombatStats getStats();

    /**
     * 获取战斗配置
     */
    CombatConfig getConfig();

    /**
     * 获取战斗配置（别名）
     */
    CombatConfig getCombatConfig();

    /**
     * 获取所有玩家战斗状态
     */
    Collection<PlayerCombatState> getAllPlayerStates();

    /**
     * 切换玩家PVP状态
     * @param playerId 玩家ID
     * @return 新的PVP状态（true=开启，false=关闭）
     */
    boolean togglePlayerPvp(UUID playerId);

    /**
     * 检查玩家是否开启了PVP
     * @param playerId 玩家ID
     * @return 是否开启了PVP
     */
    boolean isPlayerPvpEnabled(UUID playerId);

    /**
     * 设置玩家PVP状态
     * @param playerId 玩家ID
     * @param enabled 是否开启PVP
     */
    void setPlayerPvpEnabled(UUID playerId, boolean enabled);

    // ==================== 持久化 ====================

    /**
     * 获取战斗存储服务
     */
    dev.starcore.starcore.module.combat.storage.CombatStorage getStorage();

    /**
     * 获取玩家的战斗历史记录
     * @param playerId 玩家ID
     * @param limit 最大记录数
     * @return 战斗历史列表
     */
    java.util.List<dev.starcore.starcore.module.combat.storage.CombatStorage.CombatHistoryRecord>
        getCombatHistory(UUID playerId, int limit);

    /**
     * 保存所有战斗状态
     */
    void saveAll();

    /**
     * 保存指定玩家的战斗状态
     */
    void savePlayerState(UUID playerId);

    /**
     * 关闭服务
     */
    void shutdown();

    /**
     * 战斗统计记录
     */
    record CombatStats(
        int totalPlayers,
        int totalSessions,
        int activeSessions,
        int activeParticipants,
        int battlefields
    ) {}
}
