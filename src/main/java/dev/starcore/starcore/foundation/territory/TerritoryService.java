package dev.starcore.starcore.foundation.territory;

import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.foundation.territory.model.TerritoryClaim;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface TerritoryService {
    Optional<TerritoryClaim> claimAt(ChunkCoordinate coordinate);

    boolean isClaimed(ChunkCoordinate coordinate);

    boolean claim(String ownerId, ChunkCoordinate coordinate);

    boolean unclaim(String ownerId, ChunkCoordinate coordinate);

    int claimedChunkCount();

    Collection<TerritoryClaim> claimsByOwner(String ownerId);

    /**
     * 检查玩家是否可以访问指定坐标的领土
     * @param playerId 玩家UUID
     * @param coordinate 区块坐标
     * @return true 如果可以访问（友好领土或无领土）
     */
    default boolean canAccess(UUID playerId, ChunkCoordinate coordinate) {
        Optional<TerritoryClaim> claim = claimAt(coordinate);
        if (claim.isEmpty()) {
            return true; // 无主之地可自由进入
        }
        return claim.get().ownerId().equals(playerId.toString());
    }

    /**
     * 检查两个坐标是否属于同一个领土所有者
     * @param coord1 坐标1
     * @param coord2 坐标2
     * @return true 如果属于同一所有者
     */
    default boolean isSameOwner(ChunkCoordinate coord1, ChunkCoordinate coord2) {
        if (coord1.equals(coord2)) {
            return true;
        }
        Optional<TerritoryClaim> claim1 = claimAt(coord1);
        Optional<TerritoryClaim> claim2 = claimAt(coord2);

        if (claim1.isEmpty() && claim2.isEmpty()) {
            return true; // 两块都是无主之地，视为同一
        }
        if (claim1.isEmpty() || claim2.isEmpty()) {
            return false; // 一块有主一块无主，不相同
        }
        return claim1.get().ownerId().equals(claim2.get().ownerId());
    }

    /**
     * 检查指定坐标是否属于指定所有者
     * @param ownerId 所有者ID
     * @param coordinate 区块坐标
     * @return true 如果属于该所有者
     */
    default boolean isOwnerOf(String ownerId, ChunkCoordinate coordinate) {
        return claimAt(coordinate)
            .map(claim -> claim.ownerId().equals(ownerId))
            .orElse(false);
    }

    /**
     * 获取指定所有者的领土数量
     * @param ownerId 所有者ID
     * @return 领土区块数量
     */
    default int claimCountOf(String ownerId) {
        return claimsByOwner(ownerId).size();
    }

    /**
     * 检查是否与指定所有者处于战争状态
     * 此方法需要与战争服务配合使用，默认返回 false
     * @param ownerId 所有者ID
     * @param enemyOwnerId 敌对所有者ID
     * @return true 如果处于战争状态
     */
    default boolean isAtWarWith(String ownerId, String enemyOwnerId) {
        // 默认实现，由外部服务覆盖
        return false;
    }
}
