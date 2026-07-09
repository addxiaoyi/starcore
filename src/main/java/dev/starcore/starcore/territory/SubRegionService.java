package dev.starcore.starcore.territory;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 子区域服务
 * 管理所有子区域的创建、查询和权限继承
 *
 * 支持持久化：enable() 时自动加载，disable() 时自动保存
 */
public class SubRegionService {

    private final JavaPlugin plugin;
    private final TerritoryService territoryService;
    private final TerritoryStorage storage;

    // 子区域存储 - ID -> SubRegion
    private final Map<UUID, SubRegion> subRegions = new ConcurrentHashMap<>();

    // 父领土索引 - ParentID -> Set<SubRegionID>
    private final Map<UUID, Set<UUID>> parentIndex = new ConcurrentHashMap<>();

    // 最大嵌套深度
    private int maxDepth = 3;

    // 最小尺寸
    private int minSize = 5;

    // 是否已初始化
    private boolean initialized = false;

    public SubRegionService(JavaPlugin plugin, TerritoryService territoryService, TerritoryStorage storage) {
        this.plugin = plugin;
        this.territoryService = territoryService;
        this.storage = storage;
    }

    /**
     * 从存储加载所有子区域
     * 在插件 enable() 时调用
     */
    public synchronized void load() {
        if (initialized) {
            plugin.getLogger().warning("SubRegionService 已加载，跳过重复初始化");
            return;
        }

        try {
            Collection<SubRegion> loaded = storage.loadSubRegions();
            for (SubRegion subRegion : loaded) {
                // 验证父领土存在
                if (territoryService.getTerritory(subRegion.getParentTerritoryId()) != null) {
                    subRegions.put(subRegion.getId(), subRegion);
                    parentIndex.computeIfAbsent(subRegion.getParentTerritoryId(),
                        k -> ConcurrentHashMap.newKeySet()).add(subRegion.getId());
                } else {
                    plugin.getLogger().warning("跳过无效子区域 " + subRegion.getName() +
                        "：父领土 " + subRegion.getParentTerritoryId() + " 不存在");
                }
            }
            initialized = true;
            plugin.getLogger().info("已从存储加载 " + subRegions.size() + " 个子区域");
        } catch (Exception e) {
            plugin.getLogger().severe("加载子区域数据失败: " + e.getMessage());
            plugin.getLogger().warning("Stack trace: " + e);
        }
    }

    /**
     * 保存所有子区域到存储
     * 在插件 disable() 时调用
     */
    public synchronized void save() {
        try {
            storage.saveSubRegions(subRegions.values());
            plugin.getLogger().info("已保存 " + subRegions.size() + " 个子区域到存储");
        } catch (Exception e) {
            plugin.getLogger().severe("保存子区域数据失败: " + e.getMessage());
            plugin.getLogger().warning("Stack trace: " + e);
        }
    }

    /**
     * 异步保存
     */
    public void saveAsync() {
        storage.saveAllAsync(Collections.emptyList(), subRegions.values());
    }

    // ==================== 创建和删除 ====================

    /**
     * 创建子区域
     */
    public SubRegion createSubRegion(String name, UUID parentTerritoryId,
                                     String worldName, int minX, int minY, int minZ,
                                     int maxX, int maxY, int maxZ) {
        // 验证父领土存在
        Territory parent = territoryService.getTerritory(parentTerritoryId);
        if (parent == null) {
            plugin.getLogger().warning("父领土不存在: " + parentTerritoryId);
            return null;
        }

        // 创建子区域
        UUID id = UUID.randomUUID();
        SubRegion subRegion = new SubRegion(id, name, parentTerritoryId,
            worldName, minX, minY, minZ, maxX, maxY, maxZ);

        // 验证边界在父领土内
        if (!subRegion.isWithinBounds(parent)) {
            plugin.getLogger().warning("子区域超出父领土边界");
            return null;
        }

        // 验证最小尺寸
        if (!validateMinSize(subRegion)) {
            plugin.getLogger().warning("子区域尺寸小于最小要求: " + minSize);
            return null;
        }

        // 存储子区域
        subRegions.put(id, subRegion);
        parentIndex.computeIfAbsent(parentTerritoryId, k -> ConcurrentHashMap.newKeySet()).add(id);

        // 更新父领土
        parent.addSubRegion(id);

        // 立即持久化
        storage.saveSubRegions(subRegions.values());

        plugin.getLogger().info("创建子区域: " + name + " (Parent: " + parent.getName() + ")");
        return subRegion;
    }

    /**
     * 删除子区域
     */
    public boolean deleteSubRegion(UUID subRegionId) {
        SubRegion subRegion = subRegions.remove(subRegionId);
        if (subRegion == null) {
            return false;
        }

        // 从父领土移除
        Territory parent = territoryService.getTerritory(subRegion.getParentTerritoryId());
        if (parent != null) {
            parent.removeSubRegion(subRegionId);
        }

        // 移除索引
        Set<UUID> siblings = parentIndex.get(subRegion.getParentTerritoryId());
        if (siblings != null) {
            siblings.remove(subRegionId);
        }

        // 立即持久化
        storage.saveSubRegions(subRegions.values());

        plugin.getLogger().info("删除子区域: " + subRegion.getName());
        return true;
    }

    // ==================== 查询方法 ====================

    /**
     * 根据ID获取子区域
     */
    public SubRegion getSubRegion(UUID id) {
        return subRegions.get(id);
    }

    /**
     * 获取父领土的所有子区域
     */
    public List<SubRegion> getSubRegionsByParent(UUID parentTerritoryId) {
        Set<UUID> subRegionIds = parentIndex.get(parentTerritoryId);
        if (subRegionIds == null) {
            return Collections.emptyList();
        }

        return subRegionIds.stream()
            .map(subRegions::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 获取位置所在的子区域（按优先级排序）
     */
    public SubRegion getSubRegionAt(Location location, UUID parentTerritoryId) {
        List<SubRegion> regions = getSubRegionsByParent(parentTerritoryId);

        return regions.stream()
            .filter(SubRegion::isEnabled)
            .filter(sr -> sr.contains(location))
            .max(Comparator.comparingInt(SubRegion::getPriority))
            .orElse(null);
    }

    /**
     * 获取位置所在的所有子区域
     */
    public List<SubRegion> getAllSubRegionsAt(Location location, UUID parentTerritoryId) {
        List<SubRegion> regions = getSubRegionsByParent(parentTerritoryId);

        return regions.stream()
            .filter(SubRegion::isEnabled)
            .filter(sr -> sr.contains(location))
            .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
            .collect(Collectors.toList());
    }

    /**
     * 获取所有子区域
     */
    public Collection<SubRegion> getAllSubRegions() {
        return Collections.unmodifiableCollection(subRegions.values());
    }

    // ==================== 权限继承 ====================

    /**
     * 获取有效权限（考虑继承和覆盖）
     */
    public PermissionLevel getEffectivePermission(SubRegion subRegion,
                                                  TerritoryPermission permission) {
        Territory parent = territoryService.getTerritory(subRegion.getParentTerritoryId());
        return subRegion.getPermission(permission, parent);
    }

    /**
     * 批量应用父领土权限到子区域
     */
    public void inheritAllPermissions(UUID subRegionId) {
        SubRegion subRegion = subRegions.get(subRegionId);
        if (subRegion == null) {
            return;
        }

        subRegion.clearOverridePermissions();
        subRegion.setInheritPermissions(true);

        // 立即持久化
        storage.saveSubRegions(subRegions.values());

        plugin.getLogger().info("子区域 " + subRegion.getName() + " 继承父领土所有权限");
    }

    /**
     * 复制父领土权限到子区域（作为覆盖）
     */
    public void copyParentPermissions(UUID subRegionId) {
        SubRegion subRegion = subRegions.get(subRegionId);
        if (subRegion == null) {
            return;
        }

        Territory parent = territoryService.getTerritory(subRegion.getParentTerritoryId());
        if (parent == null) {
            return;
        }

        // 复制所有权限
        for (Map.Entry<TerritoryPermission, PermissionLevel> entry : parent.getAllPermissions().entrySet()) {
            subRegion.setOverridePermission(entry.getKey(), entry.getValue());
        }

        subRegion.setInheritPermissions(false);

        // 立即持久化
        storage.saveSubRegions(subRegions.values());

        plugin.getLogger().info("子区域 " + subRegion.getName() + " 复制父领土权限");
    }

    // ==================== 优先级管理 ====================

    /**
     * 设置子区域优先级
     */
    public void setPriority(UUID subRegionId, int priority) {
        SubRegion subRegion = subRegions.get(subRegionId);
        if (subRegion != null) {
            subRegion.setPriority(priority);
            // 立即持久化
            storage.saveSubRegions(subRegions.values());
        }
    }

    /**
     * 自动调整优先级（根据体积，小的优先级高）
     */
    public void autoAdjustPriorities(UUID parentTerritoryId) {
        List<SubRegion> regions = getSubRegionsByParent(parentTerritoryId);

        // 按体积排序，体积小的优先级高
        List<SubRegion> sorted = regions.stream()
            .sorted(Comparator.comparingLong(SubRegion::getVolume))
            .toList();

        // 分配优先级
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setPriority(sorted.size() - i);
        }

        // 立即持久化
        storage.saveSubRegions(subRegions.values());

        plugin.getLogger().info("自动调整优先级: " + regions.size() + " 个子区域");
    }

    /**
     * 解决优先级冲突（确保重叠的子区域有不同的优先级）
     */
    public void resolvePriorityConflicts(UUID parentTerritoryId) {
        List<SubRegion> regions = getSubRegionsByParent(parentTerritoryId);

        // 查找重叠的子区域
        for (int i = 0; i < regions.size(); i++) {
            SubRegion sr1 = regions.get(i);
            for (int j = i + 1; j < regions.size(); j++) {
                SubRegion sr2 = regions.get(j);

                // 如果重叠且优先级相同，调整优先级
                if (sr1.overlaps(sr2) && sr1.getPriority() == sr2.getPriority()) {
                    // 较小的区域获得更高优先级
                    if (sr1.getVolume() < sr2.getVolume()) {
                        sr1.setPriority(sr2.getPriority() + 1);
                    } else {
                        sr2.setPriority(sr1.getPriority() + 1);
                    }
                    plugin.getLogger().info("解决优先级冲突: " + sr1.getName() + " vs " + sr2.getName());
                }
            }
        }

        // 立即持久化
        storage.saveSubRegions(subRegions.values());
    }

    // ==================== 重叠检查 ====================

    /**
     * 检查子区域是否与同级子区域重叠
     */
    public boolean hasOverlap(UUID parentTerritoryId, String worldName,
                             int minX, int minY, int minZ,
                             int maxX, int maxY, int maxZ,
                             UUID excludeId) {
        List<SubRegion> regions = getSubRegionsByParent(parentTerritoryId);

        SubRegion temp = new SubRegion(UUID.randomUUID(), "temp", parentTerritoryId,
            worldName, minX, minY, minZ, maxX, maxY, maxZ);

        return regions.stream()
            .filter(sr -> !sr.getId().equals(excludeId))
            .anyMatch(sr -> sr.overlaps(temp));
    }

    /**
     * 获取重叠的子区域
     */
    public List<SubRegion> getOverlappingSubRegions(UUID parentTerritoryId,
                                                   SubRegion target) {
        List<SubRegion> regions = getSubRegionsByParent(parentTerritoryId);

        return regions.stream()
            .filter(sr -> !sr.getId().equals(target.getId()))
            .filter(sr -> sr.overlaps(target))
            .collect(Collectors.toList());
    }

    // ==================== 验证方法 ====================

    /**
     * 验证最小尺寸
     */
    private boolean validateMinSize(SubRegion subRegion) {
        int width = subRegion.getMaxX() - subRegion.getMinX() + 1;
        int height = subRegion.getMaxY() - subRegion.getMinY() + 1;
        int depth = subRegion.getMaxZ() - subRegion.getMinZ() + 1;

        return width >= minSize && height >= minSize && depth >= minSize;
    }

    /**
     * 验证嵌套深度（预留用于嵌套子区域）
     */
    private boolean validateDepth(UUID parentTerritoryId, int currentDepth) {
        return currentDepth < maxDepth;
    }

    // ==================== 配置 ====================

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMinSize(int minSize) {
        this.minSize = minSize;
    }

    public int getMinSize() {
        return minSize;
    }

    // ==================== 统计方法 ====================

    /**
     * 获取子区域总数
     */
    public int getSubRegionCount() {
        return subRegions.size();
    }

    /**
     * 获取父领土的子区域数量
     */
    public int getSubRegionCount(UUID parentTerritoryId) {
        Set<UUID> subRegionIds = parentIndex.get(parentTerritoryId);
        return subRegionIds != null ? subRegionIds.size() : 0;
    }

    /**
     * 清空所有子区域
     */
    public void clearAll() {
        subRegions.clear();
        parentIndex.clear();
        initialized = true; // 保持已初始化状态
        plugin.getLogger().info("清空所有子区域数据");
    }

    /**
     * 重建索引
     */
    public void rebuildIndexes() {
        parentIndex.clear();

        for (SubRegion subRegion : subRegions.values()) {
            parentIndex.computeIfAbsent(subRegion.getParentTerritoryId(),
                k -> ConcurrentHashMap.newKeySet()).add(subRegion.getId());
        }

        plugin.getLogger().info("重建子区域索引完成");
    }

    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }
}
