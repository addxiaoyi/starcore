package dev.starcore.starcore.module.blueprint;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.*;

/**
 * 区域选择接口
 * 支持立方体和多边形选区
 */
public interface RegionSelection {

    /**
     * 获取选区类型
     */
    String getType();

    /**
     * 获取选区内的所有方块位置
     */
    List<BlockVector3> getBlocks();

    /**
     * 获取选区方块数量
     */
    int getBlockCount();

    /**
     * 获取选区宽度
     */
    int getWidth();

    /**
     * 获取选区高度
     */
    int getHeight();

    /**
     * 获取选区深度
     */
    int getDepth();

    /**
     * 检查位置是否在选区内
     */
    boolean contains(BlockVector3 position);

    /**
     * 获取选区的最小点
     */
    BlockVector3 getMinPoint();

    /**
     * 获取选区的最大点
     */
    BlockVector3 getMaxPoint();

    /**
     * 获取选区中心点
     */
    BlockVector3 getCenter();

    /**
     * 获取所属世界名称
     */
    String getWorldName();

    /**
     * 获取包含的方块类型列表
     */
    Set<String> getMaterialTypes();

    /**
     * 创建相对坐标的副本（原点在0,0,0）
     */
    RegionSelection toRelative();

    /**
     * 转换为立方体选区（如果可能）
     */
    Optional<CuboidRegion> toCuboid();

    /**
     * 转换为多边形选区（如果可能）
     */
    Optional<PolygonalRegion> toPolygonal();

    /**
     * 移动选区
     */
    RegionSelection shift(int dx, int dy, int dz);

    /**
     * 扩展选区
     */
    RegionSelection expand(int amount);

    /**
     * 收缩选区
     */
    RegionSelection contract(int amount);

    /**
     * 创建选区的镜像
     */
    RegionSelection mirror(String axis);

    /**
     * 合并两个选区
     */
    RegionSelection union(RegionSelection other);

    /**
     * 获取与另一个选区的交集
     * @return 无交集时返回 Optional.empty()
     */
    Optional<RegionSelection> intersection(RegionSelection other);
}
