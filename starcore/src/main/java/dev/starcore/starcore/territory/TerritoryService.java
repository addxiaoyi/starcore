package dev.starcore.starcore.territory;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 领土服务核心
 * 管理所有领土的创建、查询和操作
 *
 * 支持持久化：enable() 时自动加载，disable() 时自动保存
 */
public class TerritoryService {

    private final JavaPlugin plugin;

    // 领土存储
    private final TerritoryStorage storage;

    // 领土存储 - ID -> Territory
    private final Map<UUID, Territory> territories = new ConcurrentHashMap<>();

    // 位置索引 - World -> Territories（用于快速位置查询）
    private final Map<String, Set<Territory>> worldIndex = new ConcurrentHashMap<>();

    // 所有者索引 - OwnerID -> Territories
    private final Map<UUID, Set<UUID>> ownerIndex = new ConcurrentHashMap<>();

    // 是否已初始化
    private boolean initialized = false;

    public TerritoryService(JavaPlugin plugin, TerritoryStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    /**
     * 从存储加载所有领土
     * 在插件 enable() 时调用
     */
    public synchronized void load() {
        if (initialized) {
            plugin.getLogger().warning("TerritoryService 已加载，跳过重复初始化");
            return;
        }

        try {
            Collection<Territory> loaded = storage.loadTerritories();
            for (Territory territory : loaded) {
                territories.put(territory.getId(), territory);
                worldIndex.computeIfAbsent(territory.getWorldName(),
                    k -> ConcurrentHashMap.newKeySet()).add(territory);
                ownerIndex.computeIfAbsent(territory.getOwnerId(),
                    k -> ConcurrentHashMap.newKeySet()).add(territory.getId());
            }
            initialized = true;
            plugin.getLogger().info("已从存储加载 " + territories.size() + " 个领土");
        } catch (Exception e) {
            plugin.getLogger().severe("加载领土数据失败: " + e.getMessage());
            plugin.getLogger().warning("Stack trace: " + e);
        }
    }

    /**
     * 保存所有领土到存储
     * 在插件 disable() 时调用
     */
    public synchronized void save() {
        try {
            storage.saveTerritories(territories.values());
            plugin.getLogger().info("已保存 " + territories.size() + " 个领土到存储");
        } catch (Exception e) {
            plugin.getLogger().severe("保存领土数据失败: " + e.getMessage());
            plugin.getLogger().warning("Stack trace: " + e);
        }
    }

    /**
     * 异步保存
     */
    public void saveAsync() {
        storage.saveAllAsync(territories.values(), Collections.emptyList());
    }

    /**
     * 检查是否使用 SQL 模式
     */
    public boolean isUsingSql() {
        return storage.isUsingSql();
    }

    // ==================== 创建和删除 ====================

    /**
     * 创建领土
     */
    public Territory createTerritory(String name, UUID ownerId, String worldName,
                                     int minX, int minY, int minZ,
                                     int maxX, int maxY, int maxZ) {
        UUID id = UUID.randomUUID();
        Territory territory = new Territory(id, name, ownerId, worldName,
            minX, minY, minZ, maxX, maxY, maxZ);

        // 存储领土
        territories.put(id, territory);

        // 更新索引
        worldIndex.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet()).add(territory);
        ownerIndex.computeIfAbsent(ownerId, k -> ConcurrentHashMap.newKeySet()).add(id);

        // 立即持久化（审计 A-033：增量保存仅新增领土，避免重复全量写盘）
        storage.saveTerritoryIncremental(territory);

        plugin.getLogger().info("创建领土: " + name + " (ID: " + id + ")");
        return territory;
    }

    /**
     * 删除领土
     */
    public boolean deleteTerritory(UUID territoryId) {
        Territory territory = territories.remove(territoryId);
        if (territory == null) {
            return false;
        }

        // 移除索引
        Set<Territory> worldTerritories = worldIndex.get(territory.getWorldName());
        if (worldTerritories != null) {
            worldTerritories.remove(territory);
        }

        Set<UUID> ownerTerritories = ownerIndex.get(territory.getOwnerId());
        if (ownerTerritories != null) {
            ownerTerritories.remove(territoryId);
        }

        // 立即持久化（审计 A-034：删除无需额外持久化，territories.remove 已更新内存，下次全量保存时自动排除）
        // 注：若存储层需要显式通知删除，可调用 storage.deleteTerritory(territoryId) 扩展接口
        plugin.getLogger().info("删除领土: " + territory.getName() + " (ID: " + territoryId + ")");
        return true;
    }

    // ==================== 查询方法 ====================

    /**
     * 根据ID获取领土
     */
    public Territory getTerritory(UUID id) {
        return territories.get(id);
    }

    /**
     * 根据名称获取领土
     */
    public Territory getTerritoryByName(String name) {
        return territories.values().stream()
            .filter(t -> t.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    /**
     * 获取位置所在的领土
     */
    public Territory getTerritoryAt(Location location) {
        // 审计 A-036 配套：校验 getWorld() 非空
        if (location == null || location.getWorld() == null) {
            return null;
        }
        String worldName = location.getWorld().getName();
        Set<Territory> worldTerritories = worldIndex.get(worldName);

        if (worldTerritories == null) {
            return null;
        }

        // 按体积升序排序，确保子区域（小领地）优先于父区域（审计 A-032）
        return worldTerritories.stream()
            .filter(Territory::isEnabled)
            .filter(t -> t.contains(location))
            .min(Comparator.comparingLong(Territory::getVolume))
            .orElse(null);
    }

    /**
     * 获取位置所在的所有领土（包括重叠）
     */
    public List<Territory> getTerritoriesAt(Location location) {
        String worldName = location.getWorld().getName();
        Set<Territory> worldTerritories = worldIndex.get(worldName);

        if (worldTerritories == null) {
            return Collections.emptyList();
        }

        return worldTerritories.stream()
            .filter(Territory::isEnabled)
            .filter(t -> t.contains(location))
            .collect(Collectors.toList());
    }

    /**
     * 获取玩家拥有的所有领土
     */
    public List<Territory> getTerritoriesByOwner(UUID ownerId) {
        Set<UUID> territoryIds = ownerIndex.get(ownerId);
        if (territoryIds == null) {
            return Collections.emptyList();
        }

        return territoryIds.stream()
            .map(territories::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 获取国家拥有的所有领土（通过 nationId 查询）
     * @param nationId 国家ID
     * @return 国家拥有的领土列表
     */
    public List<Territory> getClaimsByNation(UUID nationId) {
        return getTerritoriesByOwner(nationId);
    }

    /**
     * 获取所有领土
     */
    public Collection<Territory> getAllTerritories() {
        return Collections.unmodifiableCollection(territories.values());
    }

    /**
     * 获取指定世界的所有领土
     */
    public List<Territory> getTerritoriesByWorld(String worldName) {
        Set<Territory> worldTerritories = worldIndex.get(worldName);
        if (worldTerritories == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(worldTerritories);
    }

    // ==================== 重叠检查 ====================

    /**
     * 检查区域是否与现有领土重叠
     */
    public boolean hasOverlap(String worldName, int minX, int minY, int minZ,
                             int maxX, int maxY, int maxZ, UUID excludeId) {
        Set<Territory> worldTerritories = worldIndex.get(worldName);
        if (worldTerritories == null) {
            return false;
        }

        // 审计 A-038：使用 overlapsBox 避免分配临时 Territory 对象
        return worldTerritories.stream()
            .filter(t -> !t.getId().equals(excludeId))
            .anyMatch(t -> t.overlapsBox(worldName, minX, minY, minZ, maxX, maxY, maxZ));
    }

    /**
     * 获取与指定区域重叠的所有领土
     */
    public List<Territory> getOverlappingTerritories(String worldName,
                                                     int minX, int minY, int minZ,
                                                     int maxX, int maxY, int maxZ) {
        Set<Territory> worldTerritories = worldIndex.get(worldName);
        if (worldTerritories == null) {
            return Collections.emptyList();
        }

        Territory temp = new Territory(UUID.randomUUID(), "temp", null, worldName,
            minX, minY, minZ, maxX, maxY, maxZ);

        return worldTerritories.stream()
            .filter(t -> t.overlaps(temp))
            .collect(Collectors.toList());
    }

    // ==================== 统计方法 ====================

    /**
     * 获取领土总数
     */
    public int getTerritoryCount() {
        return territories.size();
    }

    /**
     * 获取玩家拥有的领土数量
     */
    public int getOwnerTerritoryCount(UUID ownerId) {
        Set<UUID> territoryIds = ownerIndex.get(ownerId);
        return territoryIds != null ? territoryIds.size() : 0;
    }

    /**
     * 计算所有领土的总面积
     */
    public long getTotalArea() {
        return territories.values().stream()
            .mapToLong(Territory::getArea)
            .sum();
    }

    /**
     * 获取最大的领土
     */
    public Territory getLargestTerritory() {
        return territories.values().stream()
            .max(Comparator.comparingLong(Territory::getVolume))
            .orElse(null);
    }

    // ==================== 搜索方法 ====================

    /**
     * 搜索领土（模糊匹配名称）
     */
    public List<Territory> searchTerritories(String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        return territories.values().stream()
            .filter(t -> t.getName().toLowerCase().contains(lowerKeyword))
            .collect(Collectors.toList());
    }

    /**
     * 获取附近的领土
     */
    public List<Territory> getNearbyTerritories(Location location, double radius) {
        // 审计 A-036: 校验 location.getWorld() 非空否则返回空
        World world = location.getWorld();
        if (world == null) {
            return Collections.emptyList();
        }
        String worldName = world.getName();
        Set<Territory> worldTerritories = worldIndex.get(worldName);

        if (worldTerritories == null) {
            return Collections.emptyList();
        }

        return worldTerritories.stream()
            .filter(Territory::isEnabled)
            .filter(t -> {
                // 审计 A-037: 先校验 territory 的 worldName 与当前世界一致再计算中心点
                if (!worldName.equals(t.getWorldName())) {
                    return false;
                }
                Location center = t.getCenter(world);
                if (center == null) {
                    return false;
                }
                return center.distance(location) <= radius;
            })
            .sorted(Comparator.comparingDouble(t -> {
                Location center = t.getCenter(world);
                return center == null ? Double.MAX_VALUE : center.distance(location);
            }))
            .collect(Collectors.toList());
    }

    // ==================== 批量操作 ====================

    /**
     * 批量删除玩家的所有领土
     */
    public int deleteAllTerritoriesByOwner(UUID ownerId) {
        Set<UUID> territoryIds = ownerIndex.get(ownerId);
        if (territoryIds == null) {
            return 0;
        }

        int count = 0;
        for (UUID territoryId : new HashSet<>(territoryIds)) {
            if (deleteTerritory(territoryId)) {
                count++;
            }
        }

        return count;
    }

    /**
     * 转移所有权
     * 改为 synchronized 保证索引与领土 owner 字段的原子更新（审计 A-035）
     */
    public synchronized void transferOwnership(UUID territoryId, UUID newOwnerId) {
        Territory territory = territories.get(territoryId);
        if (territory == null) {
            return;
        }

        UUID oldOwnerId = territory.getOwnerId();

        // 更新领土
        territory.setOwnerId(newOwnerId);

        // 更新索引
        Set<UUID> oldOwnerTerritories = ownerIndex.get(oldOwnerId);
        if (oldOwnerTerritories != null) {
            oldOwnerTerritories.remove(territoryId);
        }

        ownerIndex.computeIfAbsent(newOwnerId, k -> ConcurrentHashMap.newKeySet()).add(territoryId);

        // 立即持久化
        storage.saveTerritories(territories.values());

        plugin.getLogger().info("领土 " + territory.getName() + " 转移所有权: " +
            oldOwnerId + " -> " + newOwnerId);
    }

    // ==================== 数据管理 ====================

    /**
     * 清空所有领土
     * 审计 A-039: 误触发会丢失全部数据；保留 initialized=false 并提示调用方手动 load() 恢复。
     */
    public void clearAll() {
        territories.clear();
        worldIndex.clear();
        ownerIndex.clear();
        // 重置为未初始化状态，避免"已初始化但空"的欺骗状态；如需恢复应显式调用 load()
        initialized = false;
        plugin.getLogger().warning("已清空所有领土数据 (initialized=false)。如需恢复请显式 load()");
    }

    /**
     * 重建索引
     */
    public void rebuildIndexes() {
        worldIndex.clear();
        ownerIndex.clear();

        for (Territory territory : territories.values()) {
            worldIndex.computeIfAbsent(territory.getWorldName(),
                k -> ConcurrentHashMap.newKeySet()).add(territory);
            ownerIndex.computeIfAbsent(territory.getOwnerId(),
                k -> ConcurrentHashMap.newKeySet()).add(territory.getId());
        }

        plugin.getLogger().info("重建领土索引完成");
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_territories", territories.size());
        stats.put("total_area", getTotalArea());
        stats.put("worlds", worldIndex.size());
        stats.put("owners", ownerIndex.size());
        return stats;
    }

    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }
}
