package dev.starcore.starcore.module.nation;

import dev.starcore.starcore.foundation.territory.model.ChunkClaimSelection;
import dev.starcore.starcore.foundation.territory.model.TerritoryClaim;
import dev.starcore.starcore.module.nation.model.ClaimSelectionPreview;
import dev.starcore.starcore.module.nation.model.ClaimSelectionResult;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.NationKind;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface NationService {
    Nation createNation(UUID founderId, String founderName, String nationName);

    Nation createCityState(UUID founderId, String founderName, String cityStateName);

    Optional<Nation> nationById(NationId nationId);

    Optional<Nation> nationByName(String nationName);

    Optional<Nation> nationOf(UUID playerId);

    default Optional<Nation> getNationByMember(UUID playerId) {
        return nationOf(playerId);
    }

    default Optional<Nation> getNationByName(String nationName) {
        return nationByName(nationName);
    }

    /**
     * 根据 NationId 获取国家（兼容方法）
     */
    default Optional<Nation> getNation(NationId nationId) {
        return nationById(nationId);
    }

    /**
     * 根据 UUID 获取国家（兼容方法，UUID 会被包装为 NationId）
     */
    default Optional<Nation> getNation(UUID uuid) {
        return nationById(NationId.of(uuid));
    }

    boolean claimCurrentChunk(UUID playerId, String world, int x, int z);

    boolean unclaimCurrentChunk(UUID playerId, String world, int x, int z);

    Optional<TerritoryClaim> claimAt(String world, int x, int z);

    Collection<TerritoryClaim> claimsOf(NationId nationId);

    ClaimSelectionPreview previewClaimSelection(UUID playerId, ChunkClaimSelection selection);

    ClaimSelectionResult claimSelection(UUID playerId, ChunkClaimSelection selection);

    boolean isFounder(UUID playerId, NationId nationId);

    boolean addMember(NationId nationId, UUID playerId, String playerName);

    boolean removeMember(NationId nationId, UUID playerId);

    boolean setMemberRank(NationId nationId, UUID playerId, String rank);

    boolean renameNation(NationId nationId, String newName);

    int claimCount(NationId nationId);

    int levelOf(NationId nationId);

    long experienceOf(NationId nationId);

    int maxClaimsOf(NationId nationId);

    boolean addExperience(NationId nationId, long amount);

    int foundedCount(UUID playerId, NationKind kind);

    Collection<Nation> cityStatesOf(NationId parentNationId);

    Collection<Nation> nations();

    /**
     * 获取所有国家的ID列表
     * @return 所有国家ID的集合
     */
    default Collection<NationId> nationIds() {
        return nations().stream().map(Nation::id).toList();
    }

    /**
     * 检查两个国家是否处于战争状态
     * @param nationId 国家ID
     * @param otherNationId 另一个国家ID
     * @return 如果处于战争状态返回 true
     */
    default boolean atWar(NationId nationId, NationId otherNationId) {
        return false;
    }

    /**
     * 检查国家是否处于战争状态
     * @param nationId 国家ID
     * @return 如果处于战争状态返回 true
     */
    default boolean atWar(NationId nationId) {
        return false;
    }

    /**
     * 刷新国家缓存数据
     * @param nationId 国家ID
     */
    void refreshNationCache(NationId nationId);

    /**
     * 刷新所有国家的缓存数据
     */
    default void refreshAllNationCaches() {
    }

    default void saveState() {
    }

    /**
     * 解散国家
     * 只有国家创始人可以解散国家
     * @param nationId 要解散的国家ID
     * @param disbanderId 执行解散操作的玩家ID
     * @return 解散成功返回 true
     */
    boolean disbandNation(NationId nationId, UUID disbanderId);

    /**
     * 获取玩家的官职分配
     * @param playerId 玩家ID
     * @return 官职分配映射 (nationId -> 官职名称)
     */
    default java.util.Map<NationId, String> getOfficerAssignments(UUID playerId) {
        return java.util.Collections.emptyMap();
    }

    /**
     * 移除玩家的官职
     * @param nationId 国家ID
     * @param playerId 玩家ID
     * @return 移除成功返回 true
     */
    default boolean removeOfficer(NationId nationId, UUID playerId) {
        return false;
    }

    String summary();
}
