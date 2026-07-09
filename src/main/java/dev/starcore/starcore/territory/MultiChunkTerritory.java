package dev.starcore.starcore.territory;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

/**
 * 多区块领地
 * 支持多个相邻Chunk组成的大型领地
 */
public class MultiChunkTerritory {

    private final UUID id;
    private String name;
    private UUID nationId;
    private UUID ownerId;

    // 多个Chunk
    private final Set<ChunkCoord> chunks = new HashSet<>();

    // 领地类型
    private TerritoryType type = TerritoryType.RESIDENTIAL;

    // 领地等级
    private int level = 1;

    // 领地出生点
    private Location spawnPoint;

    // 创建时间
    private final long createdTime;

    public MultiChunkTerritory(UUID id, String name, UUID nationId, UUID ownerId) {
        this.id = id;
        this.name = name;
        this.nationId = nationId;
        this.ownerId = ownerId;
        this.createdTime = System.currentTimeMillis();
    }

    // ==================== Chunk管理 ====================

    /**
     * 添加Chunk
     */
    public boolean addChunk(World world, int chunkX, int chunkZ) {
        return chunks.add(new ChunkCoord(world.getName(), chunkX, chunkZ));
    }

    /**
     * 添加Chunk
     */
    public boolean addChunk(Chunk chunk) {
        return addChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    /**
     * 移除Chunk
     */
    public boolean removeChunk(World world, int chunkX, int chunkZ) {
        return chunks.remove(new ChunkCoord(world.getName(), chunkX, chunkZ));
    }

    /**
     * 移除Chunk
     */
    public boolean removeChunk(Chunk chunk) {
        return removeChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    /**
     * 包含Chunk
     */
    public boolean containsChunk(World world, int chunkX, int chunkZ) {
        return chunks.contains(new ChunkCoord(world.getName(), chunkX, chunkZ));
    }

    /**
     * 包含Chunk
     */
    public boolean containsChunk(Chunk chunk) {
        return containsChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    /**
     * 包含位置
     */
    public boolean containsLocation(Location loc) {
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        return containsChunk(loc.getWorld(), chunkX, chunkZ);
    }

    /**
     * 获取Chunk数量
     */
    public int getChunkCount() {
        return chunks.size();
    }

    /**
     * 获取所有Chunk坐标
     */
    public Set<ChunkCoord> getChunks() {
        return Collections.unmodifiableSet(chunks);
    }

    // ==================== 连通性检查 ====================

    /**
     * 检查领地是否连通
     * 使用BFS算法检查所有Chunk是否相邻连接
     */
    public boolean isConnected() {
        if (chunks.isEmpty()) {
            return true;
        }

        if (chunks.size() == 1) {
            return true;
        }

        // BFS检查连通性
        Set<ChunkCoord> visited = new HashSet<>();
        Queue<ChunkCoord> queue = new LinkedList<>();

        // 从第一个Chunk开始
        ChunkCoord start = chunks.iterator().next();
        queue.offer(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            ChunkCoord current = queue.poll();

            // 检查四个方向的相邻Chunk
            for (ChunkCoord neighbor : current.getNeighbors()) {
                if (chunks.contains(neighbor) && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }

        // 如果访问了所有Chunk，说明连通
        return visited.size() == chunks.size();
    }

    /**
     * 获取不连通的Chunk组
     */
    public List<Set<ChunkCoord>> getDisconnectedGroups() {
        List<Set<ChunkCoord>> groups = new ArrayList<>();
        Set<ChunkCoord> remaining = new HashSet<>(chunks);

        while (!remaining.isEmpty()) {
            Set<ChunkCoord> group = new HashSet<>();
            Queue<ChunkCoord> queue = new LinkedList<>();

            ChunkCoord start = remaining.iterator().next();
            queue.offer(start);
            group.add(start);
            remaining.remove(start);

            while (!queue.isEmpty()) {
                ChunkCoord current = queue.poll();

                for (ChunkCoord neighbor : current.getNeighbors()) {
                    if (remaining.contains(neighbor)) {
                        group.add(neighbor);
                        remaining.remove(neighbor);
                        queue.offer(neighbor);
                    }
                }
            }

            groups.add(group);
        }

        return groups;
    }

    // ==================== 扩展逻辑 ====================

    /**
     * 检查是否可以添加Chunk（必须相邻）
     */
    public boolean canAddChunk(World world, int chunkX, int chunkZ) {
        ChunkCoord newChunk = new ChunkCoord(world.getName(), chunkX, chunkZ);

        // 如果是第一个Chunk，可以添加
        if (chunks.isEmpty()) {
            return true;
        }

        // 检查是否与现有Chunk相邻
        for (ChunkCoord existing : chunks) {
            if (existing.isAdjacentTo(newChunk)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取可扩展的Chunk（相邻但未占用）
     */
    public Set<ChunkCoord> getExpandableChunks() {
        Set<ChunkCoord> expandable = new HashSet<>();

        for (ChunkCoord chunk : chunks) {
            for (ChunkCoord neighbor : chunk.getNeighbors()) {
                if (!chunks.contains(neighbor)) {
                    expandable.add(neighbor);
                }
            }
        }

        return expandable;
    }

    // ==================== 维护费计算 ====================

    /**
     * 计算每日维护费
     */
    public double calculateDailyMaintenance() {
        double base = type.getBaseMaintenance();
        double chunkCost = chunks.size() * 5.0;
        double levelMultiplier = 1.0 + (level * 0.1);

        return (base + chunkCost) * levelMultiplier;
    }

    // ==================== Getter/Setter ====================

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getNationId() {
        return nationId;
    }

    public void setNationId(UUID nationId) {
        this.nationId = nationId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public TerritoryType getType() {
        return type;
    }

    public void setType(TerritoryType type) {
        this.type = type;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(10, level));
    }

    public Location getSpawnPoint() {
        return spawnPoint;
    }

    public void setSpawnPoint(Location spawnPoint) {
        this.spawnPoint = spawnPoint;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取带颜色的类型名称
     */
    public String getColoredTypeName() {
        return type.getColoredName();
    }

    /**
     * 获取领地面积（平方米）
     */
    public int getArea() {
        return chunks.size() * 256; // 每个Chunk 16x16 = 256
    }

    @Override
    public String toString() {
        return String.format(
            "MultiChunkTerritory[name=%s, type=%s, chunks=%d, connected=%b]",
            name, type, chunks.size(), isConnected()
        );
    }

    // ==================== 内部类：Chunk坐标 ====================

    /**
     * Chunk坐标记录
     */
    public record ChunkCoord(String world, int x, int z) {

        /**
         * 获取相邻Chunk
         */
        public Set<ChunkCoord> getNeighbors() {
            return Set.of(
                new ChunkCoord(world, x + 1, z),     // 东
                new ChunkCoord(world, x - 1, z),     // 西
                new ChunkCoord(world, x, z + 1),     // 南
                new ChunkCoord(world, x, z - 1)      // 北
            );
        }

        /**
         * 是否与另一个Chunk相邻
         */
        public boolean isAdjacentTo(ChunkCoord other) {
            if (!world.equals(other.world)) {
                return false;
            }

            int dx = Math.abs(x - other.x);
            int dz = Math.abs(z - other.z);

            // 相邻：一个方向差1，另一个方向差0
            return (dx == 1 && dz == 0) || (dx == 0 && dz == 1);
        }

        /**
         * 计算距离
         */
        public int distance(ChunkCoord other) {
            if (!world.equals(other.world)) {
                return Integer.MAX_VALUE;
            }

            return Math.abs(x - other.x) + Math.abs(z - other.z);
        }
    }
}
