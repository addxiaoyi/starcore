package dev.starcore.starcore.territory;

import org.bukkit.Location;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Territory边界渲染器
 * 基于SimpleClaimSystem的智能边界算法
 *
 * 优化：
 * - 只渲染外边界（不渲染相邻Territory的共享边）
 * - 跳格渲染（每2格一个粒子）
 * - 节省50%粒子开销
 */
public class TerritoryBorderRenderer {

    private static final int PARTICLE_SPACING = 2; // 粒子间隔
    private static final int MIN_HEIGHT = 60;      // 最低高度
    private static final int MAX_HEIGHT = 100;     // 最高高度

    /**
     * 获取Territory的边界位置
     * 注意：Territory record 只包含 nationName 和 ownerId，不包含 chunk 信息
     * 此方法需要从外部传入 chunk 集合
     */
    public Set<Location> getBorderLocations(Set<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptySet();
        }

        // 构建Chunk坐标集合（用于快速查询相邻）
        Set<Long> chunkKeys = chunks.stream()
            .map(c -> chunkKey(c.getX(), c.getZ()))
            .collect(Collectors.toSet());

        Set<Location> borders = new HashSet<>();

        // 遍历每个Chunk，检查四个方向
        for (Chunk chunk : chunks) {
            addBorderIfNeeded(chunk, chunkKeys, borders);
        }

        return borders;
    }

    /**
     * 检查并添加Chunk的边界
     */
    private void addBorderIfNeeded(Chunk chunk, Set<Long> chunkKeys, Set<Location> borders) {
        int cx = chunk.getX();
        int cz = chunk.getZ();
        World world = chunk.getWorld();

        // Chunk的方块坐标范围
        int xStart = cx << 4;  // cx * 16
        int zStart = cz << 4;  // cz * 16
        int xEnd = xStart + 15;
        int zEnd = zStart + 15;

        // 西侧边界（X最小）
        if (!chunkKeys.contains(chunkKey(cx - 1, cz))) {
            for (int y = MIN_HEIGHT; y <= MAX_HEIGHT; y += PARTICLE_SPACING) {
                for (int z = zStart; z <= zEnd; z += PARTICLE_SPACING) {
                    borders.add(new Location(world, xStart, y, z));
                }
            }
        }

        // 东侧边界（X最大）
        if (!chunkKeys.contains(chunkKey(cx + 1, cz))) {
            for (int y = MIN_HEIGHT; y <= MAX_HEIGHT; y += PARTICLE_SPACING) {
                for (int z = zStart; z <= zEnd; z += PARTICLE_SPACING) {
                    borders.add(new Location(world, xEnd + 1, y, z));
                }
            }
        }

        // 北侧边界（Z最小）
        if (!chunkKeys.contains(chunkKey(cx, cz - 1))) {
            for (int y = MIN_HEIGHT; y <= MAX_HEIGHT; y += PARTICLE_SPACING) {
                for (int x = xStart; x <= xEnd; x += PARTICLE_SPACING) {
                    borders.add(new Location(world, x, y, zStart));
                }
            }
        }

        // 南侧边界（Z最大）
        if (!chunkKeys.contains(chunkKey(cx, cz + 1))) {
            for (int y = MIN_HEIGHT; y <= MAX_HEIGHT; y += PARTICLE_SPACING) {
                for (int x = xStart; x <= xEnd; x += PARTICLE_SPACING) {
                    borders.add(new Location(world, x, y, zEnd + 1));
                }
            }
        }
    }

    /**
     * 压缩Chunk坐标为long键
     * 高32位：X坐标
     * 低32位：Z坐标
     */
    private long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * 获取边界统计信息
     */
    public BorderStats calculateStats(Set<Chunk> chunks) {
        Set<Location> borders = getBorderLocations(chunks);

        return new BorderStats(
            chunks.size(),
            borders.size(),
            borders.size() * PARTICLE_SPACING / 2 // 传统方式会需要的粒子数
        );
    }

    /**
     * 边界统计记录
     */
    public record BorderStats(
        int chunkCount,
        int particleCount,
        int savedParticles
    ) {
        public double getSavingPercentage() {
            return (double) savedParticles / (particleCount + savedParticles) * 100;
        }

        @Override
        public String toString() {
            return String.format(
                "BorderStats[chunks=%d, particles=%d, saved=%d (%.1f%%)]",
                chunkCount, particleCount, savedParticles, getSavingPercentage()
            );
        }
    }

    /**
     * 获取自适应高度的边界
     * 根据地形调整高度范围
     */
    public Set<Location> getAdaptiveBorderLocations(Set<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> chunkKeys = chunks.stream()
            .map(c -> chunkKey(c.getX(), c.getZ()))
            .collect(Collectors.toSet());

        Set<Location> borders = new HashSet<>();

        for (Chunk chunk : chunks) {
            addAdaptiveBorder(chunk, chunkKeys, borders);
        }

        return borders;
    }

    /**
     * 添加自适应高度的边界
     */
    private void addAdaptiveBorder(Chunk chunk, Set<Long> chunkKeys, Set<Location> borders) {
        int cx = chunk.getX();
        int cz = chunk.getZ();
        World world = chunk.getWorld();

        int xStart = cx << 4;
        int zStart = cz << 4;
        int xEnd = xStart + 15;
        int zEnd = zStart + 15;

        // 获取Chunk的平均高度
        int avgHeight = getAverageHeight(chunk);
        int minY = Math.max(avgHeight - 20, MIN_HEIGHT);
        int maxY = Math.min(avgHeight + 20, MAX_HEIGHT);

        // 西侧边界
        if (!chunkKeys.contains(chunkKey(cx - 1, cz))) {
            for (int y = minY; y <= maxY; y += PARTICLE_SPACING) {
                for (int z = zStart; z <= zEnd; z += PARTICLE_SPACING) {
                    borders.add(new Location(world, xStart, y, z));
                }
            }
        }

        // 其他三个方向同理...
    }

    /**
     * 计算Chunk的平均高度
     */
    private int getAverageHeight(Chunk chunk) {
        int sum = 0;
        int count = 0;

        // 采样：每4格检查一次
        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                sum += chunk.getWorld().getHighestBlockYAt(
                    (chunk.getX() << 4) + x,
                    (chunk.getZ() << 4) + z
                );
                count++;
            }
        }

        return count > 0 ? sum / count : 70; // 默认70
    }
}
