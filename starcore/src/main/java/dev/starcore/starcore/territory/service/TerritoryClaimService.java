package dev.starcore.starcore.territory.service;

import dev.starcore.starcore.territory.MultiChunkTerritory;
import dev.starcore.starcore.territory.MultiChunkTerritory.ChunkCoord;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Territory声明服务
 * 管理所有Territory的Chunk声明和冲突检测
 */
public class TerritoryClaimService {

    // Chunk -> Territory映射（快速查询）
    private final Map<ChunkCoord, UUID> chunkToTerritory = new ConcurrentHashMap<>();

    // Territory -> MultiChunkTerritory映射
    private final Map<UUID, MultiChunkTerritory> territories = new ConcurrentHashMap<>();

    // Nation -> Territory列表映射
    private final Map<UUID, Set<UUID>> nationTerritories = new ConcurrentHashMap<>();

    /**
     * 注册Territory
     */
    public void registerTerritory(MultiChunkTerritory territory) {
        UUID territoryId = territory.getId();
        territories.put(territoryId, territory);

        // 注册所有Chunk
        for (ChunkCoord coord : territory.getChunks()) {
            chunkToTerritory.put(coord, territoryId);
        }

        // 注册到Nation
        if (territory.getNationId() != null) {
            nationTerritories.computeIfAbsent(territory.getNationId(), k -> new HashSet<>())
                .add(territoryId);
        }
    }

    /**
     * 注销Territory
     */
    public void unregisterTerritory(UUID territoryId) {
        MultiChunkTerritory territory = territories.remove(territoryId);
        if (territory == null) {
            return;
        }

        // 移除所有Chunk映射
        for (ChunkCoord coord : territory.getChunks()) {
            chunkToTerritory.remove(coord);
        }

        // 从Nation移除
        if (territory.getNationId() != null) {
            Set<UUID> nationTerrs = nationTerritories.get(territory.getNationId());
            if (nationTerrs != null) {
                nationTerrs.remove(territoryId);
            }
        }
    }

    /**
     * 声明Chunk
     */
    public ClaimResult claimChunk(MultiChunkTerritory territory, Chunk chunk) {
        return claimChunk(territory, chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    /**
     * 声明Chunk（完整）
     */
    public ClaimResult claimChunk(MultiChunkTerritory territory, World world, int chunkX, int chunkZ) {
        ChunkCoord coord = new ChunkCoord(world.getName(), chunkX, chunkZ);

        // 1. 检查是否已被声明
        UUID existingTerritory = chunkToTerritory.get(coord);
        if (existingTerritory != null) {
            if (existingTerritory.equals(territory.getId())) {
                return new ClaimResult(false, ClaimFailReason.ALREADY_CLAIMED_BY_SELF, null);
            } else {
                return new ClaimResult(false, ClaimFailReason.ALREADY_CLAIMED_BY_OTHER, existingTerritory);
            }
        }

        // 2. 检查是否需要相邻（如果Territory已有Chunk）
        if (!territory.getChunks().isEmpty() && !territory.canAddChunk(world, chunkX, chunkZ)) {
            return new ClaimResult(false, ClaimFailReason.NOT_ADJACENT, null);
        }

        // 3. 添加Chunk到Territory
        if (!territory.addChunk(world, chunkX, chunkZ)) {
            return new ClaimResult(false, ClaimFailReason.UNKNOWN_ERROR, null);
        }

        // 4. 注册映射
        chunkToTerritory.put(coord, territory.getId());

        return new ClaimResult(true, null, null);
    }

    /**
     * 取消声明Chunk
     */
    public UnclaimResult unclaimChunk(MultiChunkTerritory territory, Chunk chunk) {
        return unclaimChunk(territory, chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    /**
     * 取消声明Chunk（完整）
     */
    public UnclaimResult unclaimChunk(MultiChunkTerritory territory, World world, int chunkX, int chunkZ) {
        ChunkCoord coord = new ChunkCoord(world.getName(), chunkX, chunkZ);

        // 1. 检查是否属于此Territory
        UUID owner = chunkToTerritory.get(coord);
        if (owner == null || !owner.equals(territory.getId())) {
            return new UnclaimResult(false, UnclaimFailReason.NOT_CLAIMED, false);
        }

        // 2. 移除Chunk
        territory.removeChunk(world, chunkX, chunkZ);
        chunkToTerritory.remove(coord);

        // 3. 检查连通性
        boolean connected = territory.isConnected();

        return new UnclaimResult(true, null, !connected);
    }

    /**
     * 获取Chunk的Territory
     */
    public MultiChunkTerritory getTerritoryAt(Chunk chunk) {
        return getTerritoryAt(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    /**
     * 获取Chunk的Territory（完整）
     */
    public MultiChunkTerritory getTerritoryAt(World world, int chunkX, int chunkZ) {
        ChunkCoord coord = new ChunkCoord(world.getName(), chunkX, chunkZ);
        UUID territoryId = chunkToTerritory.get(coord);
        return territoryId != null ? territories.get(territoryId) : null;
    }

    /**
     * 获取Territory
     */
    public MultiChunkTerritory getTerritory(UUID territoryId) {
        return territories.get(territoryId);
    }

    /**
     * 获取Nation的所有Territory
     */
    public Set<MultiChunkTerritory> getNationTerritories(UUID nationId) {
        Set<UUID> territoryIds = nationTerritories.get(nationId);
        if (territoryIds == null) {
            return Collections.emptySet();
        }

        Set<MultiChunkTerritory> result = new HashSet<>();
        for (UUID id : territoryIds) {
            MultiChunkTerritory territory = territories.get(id);
            if (territory != null) {
                result.add(territory);
            }
        }
        return result;
    }

    /**
     * 检查Chunk是否被声明
     */
    public boolean isClaimed(Chunk chunk) {
        return isClaimed(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    /**
     * 检查Chunk是否被声明（完整）
     */
    public boolean isClaimed(World world, int chunkX, int chunkZ) {
        ChunkCoord coord = new ChunkCoord(world.getName(), chunkX, chunkZ);
        return chunkToTerritory.containsKey(coord);
    }

    /**
     * 获取Nation的总Chunk数
     */
    public int getNationChunkCount(UUID nationId) {
        return getNationTerritories(nationId).stream()
            .mapToInt(MultiChunkTerritory::getChunkCount)
            .sum();
    }

    /**
     * 获取所有Territory
     */
    public Collection<MultiChunkTerritory> getAllTerritories() {
        return Collections.unmodifiableCollection(territories.values());
    }

    /**
     * 获取统计信息
     */
    public ClaimStats getStats() {
        int totalTerritories = territories.size();
        int totalChunks = chunkToTerritory.size();
        int totalNations = nationTerritories.size();

        return new ClaimStats(totalTerritories, totalChunks, totalNations);
    }

    // ==================== 结果类 ====================

    /**
     * 声明结果
     */
    public record ClaimResult(
        boolean success,
        ClaimFailReason failReason,
        UUID conflictingTerritory
    ) {
        public String getMessage() {
            if (success) {
                return "§a声明成功";
            }

            return switch (failReason) {
                case ALREADY_CLAIMED_BY_SELF -> "§c该Chunk已被你的Territory声明";
                case ALREADY_CLAIMED_BY_OTHER -> "§c该Chunk已被其他Territory声明";
                case NOT_ADJACENT -> "§c该Chunk不与现有Territory相邻";
                case UNKNOWN_ERROR -> "§c声明失败";
            };
        }
    }

    /**
     * 声明失败原因
     */
    public enum ClaimFailReason {
        ALREADY_CLAIMED_BY_SELF,
        ALREADY_CLAIMED_BY_OTHER,
        NOT_ADJACENT,
        UNKNOWN_ERROR
    }

    /**
     * 取消声明结果
     */
    public record UnclaimResult(
        boolean success,
        UnclaimFailReason failReason,
        boolean disconnected
    ) {
        public String getMessage() {
            if (success) {
                String msg = "§a取消声明成功";
                if (disconnected) {
                    msg += " §7(警告: Territory不再连通)";
                }
                return msg;
            }

            return switch (failReason) {
                case NOT_CLAIMED -> "§c该Chunk未被声明";
            };
        }
    }

    /**
     * 取消声明失败原因
     */
    public enum UnclaimFailReason {
        NOT_CLAIMED
    }

    /**
     * 统计信息
     */
    public record ClaimStats(
        int totalTerritories,
        int totalChunks,
        int totalNations
    ) {
        @Override
        public String toString() {
            return String.format(
                "ClaimStats[territories=%d, chunks=%d, nations=%d]",
                totalTerritories, totalChunks, totalNations
            );
        }
    }
}
