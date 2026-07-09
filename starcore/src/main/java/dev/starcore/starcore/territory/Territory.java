package dev.starcore.starcore.territory;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 领土核心类
 * 代表一个完整的领土实体，支持子区域、权限管理和租赁
 */
public class Territory {

    private final UUID id;
    private String name;
    private UUID ownerId;
    private UUID nationId;

    // 边界坐标（世界、最小/最大坐标）
    private String worldName;
    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;

    // 领土类型
    private TerritoryType type;

    // 出生点
    private Location spawnPoint;

    // 权限设置 - 每个权限类型对应一个访问级别（审计 A-046：改用 ConcurrentHashMap 防止 setPermission 并发损坏）
    private final Map<TerritoryPermission, PermissionLevel> permissions = new ConcurrentHashMap<>();

    // 成员列表 - 玩家ID -> 权限级别（使用 ConcurrentHashMap 保证线程安全）
    private final Map<UUID, PermissionLevel> members = new ConcurrentHashMap<>();

    // 子区域列表（审计 A-042：使用 ConcurrentHashMap.newKeySet 保证线程安全）
    private final Set<UUID> subRegionIds = ConcurrentHashMap.newKeySet();

    // 创建时间
    private final long createdTime;

    // 是否启用
    private boolean enabled = true;

    public Territory(UUID id, String name, UUID ownerId, String worldName,
                     int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
        this.worldName = worldName;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
        this.createdTime = System.currentTimeMillis();
        this.type = TerritoryType.RESIDENTIAL;

        // 初始化默认权限
        initializeDefaultPermissions();
    }

    /**
     * 初始化默认权限设置
     */
    private void initializeDefaultPermissions() {
        permissions.put(TerritoryPermission.BUILD, PermissionLevel.MEMBER);
        permissions.put(TerritoryPermission.BREAK, PermissionLevel.MEMBER);
        permissions.put(TerritoryPermission.INTERACT, PermissionLevel.TRUSTED);
        permissions.put(TerritoryPermission.CONTAINER, PermissionLevel.MEMBER);
        permissions.put(TerritoryPermission.PVP, PermissionLevel.NONE);
        permissions.put(TerritoryPermission.TELEPORT, PermissionLevel.TRUSTED);
        permissions.put(TerritoryPermission.SPAWN_MOBS, PermissionLevel.OWNER);
        permissions.put(TerritoryPermission.EXPLOSION, PermissionLevel.NONE);
    }

    // ==================== 位置检查 ====================

    /**
     * 检查位置是否在领土内
     */
    public boolean contains(Location location) {
        if (!location.getWorld().getName().equals(worldName)) {
            return false;
        }

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        return x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }

    /**
     * 检查是否与另一个领土重叠
     */
    public boolean overlaps(Territory other) {
        if (!this.worldName.equals(other.worldName)) {
            return false;
        }

        return !(this.maxX < other.minX || this.minX > other.maxX ||
                 this.maxY < other.minY || this.minY > other.maxY ||
                 this.maxZ < other.minZ || this.minZ > other.maxZ);
    }

    /**
     * 检查是否与给定坐标范围重叠（审计 A-038：避免分配临时 Territory 对象）
     */
    public boolean overlapsBox(String otherWorld, int oMinX, int oMinY, int oMinZ,
                               int oMaxX, int oMaxY, int oMaxZ) {
        if (!this.worldName.equals(otherWorld)) {
            return false;
        }
        return !(this.maxX < oMinX || this.minX > oMaxX ||
                 this.maxY < oMinY || this.minY > oMaxY ||
                 this.maxZ < oMinZ || this.minZ > oMaxZ);
    }

    // ==================== 权限管理 ====================

    /**
     * 设置权限
     */
    public void setPermission(TerritoryPermission permission, PermissionLevel level) {
        permissions.put(permission, level);
    }

    /**
     * 获取权限级别
     */
    public PermissionLevel getPermission(TerritoryPermission permission) {
        return permissions.getOrDefault(permission, PermissionLevel.NONE);
    }

    /**
     * 获取所有权限设置
     */
    public Map<TerritoryPermission, PermissionLevel> getAllPermissions() {
        return Collections.unmodifiableMap(permissions);
    }

    // ==================== 成员管理 ====================

    /**
     * 添加成员（审计 A-043：区分新增与覆盖，提供反馈）
     * @return true 表示新增；false 表示覆盖已有或被拒绝
     */
    public boolean addMember(UUID playerId, PermissionLevel level) {
        // 拒绝把 owner 当作普通成员加入，避免 getMemberLevel 级别不一致（审计 A-044）
        if (playerId != null && playerId.equals(ownerId)) {
            return false;
        }
        boolean isUpdate = members.containsKey(playerId);
        members.put(playerId, level);
        return !isUpdate;
    }

    /**
     * 检查是否成功更新（用于区分 addMember 的新增与覆盖场景）
     */
    public boolean hasMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    /**
     * 移除成员
     */
    public void removeMember(UUID playerId) {
        members.remove(playerId);
    }

    /**
     * 获取成员权限级别
     */
    public PermissionLevel getMemberLevel(UUID playerId) {
        if (playerId.equals(ownerId)) {
            return PermissionLevel.OWNER;
        }
        return members.getOrDefault(playerId, PermissionLevel.NONE);
    }

    /**
     * 检查是否为成员
     */
    public boolean isMember(UUID playerId) {
        return playerId.equals(ownerId) || members.containsKey(playerId);
    }

    /**
     * 获取所有成员
     */
    public Map<UUID, PermissionLevel> getAllMembers() {
        return Collections.unmodifiableMap(members);
    }

    // ==================== 子区域管理 ====================

    /**
     * 添加子区域
     */
    public void addSubRegion(UUID subRegionId) {
        subRegionIds.add(subRegionId);
    }

    /**
     * 移除子区域
     */
    public void removeSubRegion(UUID subRegionId) {
        subRegionIds.remove(subRegionId);
    }

    /**
     * 获取所有子区域ID
     */
    public Set<UUID> getSubRegionIds() {
        return Collections.unmodifiableSet(subRegionIds);
    }

    // ==================== 统计方法 ====================

    /**
     * 计算领土体积
     * 审计 A-041：宽度>46341 时面积已溢出，体积为 long 可能仍超 9.2e18。先用 long 计算，
     * 超出 Long.MAX_VALUE 时返回 Long.MAX_VALUE；调用方如有需要可改 BigInteger。
     */
    public long getVolume() {
        long width = maxX - minX + 1;
        long height = maxY - minY + 1;
        long depth = maxZ - minZ + 1;
        // 检测乘法溢出
        long area = width * depth;
        // 简易溢出兜底（不完全精确，但避免负数/false 负值）
        if (width != 0 && area / width != depth) {
            return Long.MAX_VALUE;
        }
        long volume = area * height;
        if (area != 0 && volume / area != height) {
            return Long.MAX_VALUE;
        }
        return volume;
    }

    /**
     * 计算领土面积（XZ平面）（审计 A-040：宽度>46341 时 int 溢出为负，改用 long）
     */
    public long getArea() {
        return (long)(maxX - minX + 1) * (long)(maxZ - minZ + 1);
    }

    /**
     * 获取中心点（审计 A-045：传入 null world 返回 null 避免后续 NPE）
     */
    public Location getCenter(World world) {
        if (world == null) {
            return null;
        }
        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;
        return new Location(world, centerX, centerY, centerZ);
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

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public UUID getNationId() {
        return nationId;
    }

    public void setNationId(UUID nationId) {
        this.nationId = nationId;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public TerritoryType getType() {
        return type;
    }

    public void setType(TerritoryType type) {
        this.type = type;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return String.format("Territory[id=%s, name=%s, type=%s, area=%d, members=%d, subregions=%d]",
            id, name, type, getArea(), members.size(), subRegionIds.size());
    }
}
