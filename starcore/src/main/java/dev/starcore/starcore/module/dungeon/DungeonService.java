package dev.starcore.starcore.module.dungeon;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 副本服务接口
 * 提供副本系统的核心功能
 */
public interface DungeonService {

    // ==================== 副本定义管理 ====================

    /**
     * 获取所有副本定义
     */
    Collection<DungeonDefinition> getAllDungeons();

    /**
     * 根据ID获取副本定义
     */
    Optional<DungeonDefinition> getDungeonById(String dungeonId);

    /**
     * 根据难度获取副本列表
     */
    List<DungeonDefinition> getDungeonsByDifficulty(DungeonDifficulty difficulty);

    // ==================== 副本实例管理 ====================

    /**
     * 创建副本实例
     * @param dungeonId 副本ID
     * @param partyMembers 队伍成员列表
     * @return 副本实例，如果创建失败返回空
     */
    Optional<DungeonInstance> createInstance(String dungeonId, List<Player> partyMembers);

    /**
     * 获取玩家当前所在的副本实例
     */
    Optional<DungeonInstance> getInstanceByPlayer(UUID playerId);

    /**
     * 获取副本实例
     */
    Optional<DungeonInstance> getInstance(UUID instanceId);

    /**
     * 获取所有活跃的副本实例
     */
    Collection<DungeonInstance> getActiveInstances();

    /**
     * 关闭副本实例
     */
    void closeInstance(UUID instanceId);

    /**
     * 强制关闭所有副本实例
     */
    void closeAllInstances();

    // ==================== 玩家状态 ====================

    /**
     * 获取玩家的副本进度
     */
    Optional<DungeonProgress> getPlayerProgress(UUID playerId, UUID instanceId);

    /**
     * 获取玩家的副本历史
     */
    List<DungeonCompletionRecord> getPlayerHistory(UUID playerId);

    /**
     * 获取玩家的副本冷却时间
     */
    long getPlayerCooldown(UUID playerId, String dungeonId);

    // ==================== 队伍系统 ====================

    /**
     * 创建副本专属队伍
     */
    DungeonParty createParty(Player leader, DungeonDefinition dungeon);

    /**
     * 加入队伍
     */
    boolean joinParty(UUID partyId, Player player);

    /**
     * 离开队伍
     */
    void leaveParty(UUID partyId, Player player);

    /**
     * 解散队伍
     */
    void disbandParty(UUID partyId);

    /**
     * 获取玩家所在队伍
     */
    Optional<DungeonParty> getPlayerParty(UUID playerId);

    /**
     * 获取队伍
     */
    Optional<DungeonParty> getParty(UUID partyId);

    // ==================== 进入/退出 ====================

    /**
     * 尝试进入副本
     * @return 是否成功进入
     */
    boolean tryEnterDungeon(Player player, String dungeonId);

    /**
     * 离开副本
     */
    void leaveDungeon(Player player);

    /**
     * 强制传送玩家到副本入口
     */
    void teleportToEntrance(Player player, String dungeonId);

    // ==================== 副本进度 ====================

    /**
     * 玩家死亡处理
     */
    void handlePlayerDeath(Player player);

    /**
     * 玩家复活处理
     */
    void respawnPlayer(Player player);

    /**
     * 检查副本是否完成
     */
    void checkDungeonCompletion(UUID instanceId);

    /**
     * 处理房间清理完成
     */
    void onRoomCleared(UUID instanceId, String roomId);

    /**
     * 处理BOSS被击败
     */
    void onBossDefeated(UUID instanceId, String bossId);

    // ==================== 管理功能 ====================

    /**
     * 重新加载配置
     */
    void reload();

    /**
     * 保存所有数据
     */
    void save();

    /**
     * 获取服务摘要
     */
    String getSummary();

    /**
     * 获取统计数据
     */
    DungeonStatistics getStatistics();

    // ==================== 国家相关 ====================

    /**
     * 国家是否可以进入副本
     */
    boolean canNationEnterDungeon(NationId nationId, String dungeonId);

    /**
     * 国家进入副本
     */
    boolean nationEnterDungeon(NationId nationId, String dungeonId, List<Player> members);

    /**
     * 获取国家副本记录
     */
    List<DungeonCompletionRecord> getNationRecords(NationId nationId);
}
