package dev.starcore.starcore.territory;

import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 子区域类
 * 代表一个领土内的子区域，支持权限继承和覆盖
 */
public class SubRegion {

    private final UUID id;
    private String name;
    private final UUID parentTerritoryId;

    // 边界坐标（相对于父领土）
    private String worldName;
    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;

    // 优先级（数值越大优先级越高）
    private int priority;

    // 是否启用权限继承
    private boolean inheritPermissions;

    // 覆盖的权限（null表示使用继承）
    private final Map<TerritoryPermission, PermissionLevel> overridePermissions = new EnumMap<>(TerritoryPermission.class);

    // 子区域专属成员
    private final Map<UUID, PermissionLevel> members = new ConcurrentHashMap<>();

    // 创建时间
    private final long createdTime;

    // 是否启用
    private boolean enabled = true;

    // 描述
    private String description;

    public SubRegion(UUID id, String name, UUID parentTerritoryId,
                     String worldName, int minX, int minY, int minZ,
                     int maxX, int maxY, int maxZ) {
        this.id = id;
        this.name = name;
        this.parentTerritoryId = parentTerritoryId;
        this.worldName = worldName;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
        this.createdTime = System.currentTimeMillis();
        this.priority = 0;
        this.inheritPermissions = true;
    }

    // ==================== 位置检查 ====================

    /**
     * 检查位置是否在子区域内
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
     * 检查是否与另一个子区域重叠
     */
    public boolean overlaps(SubRegion other) {
        if (!this.worldName.equals(other.worldName)) {
            return false;
        }

        return !(this.maxX < other.minX || this.minX > other.maxX ||
                 this.maxY < other.minY || this.minY > other.maxY ||
                 this.maxZ < other.minZ || this.minZ > other.maxZ);
    }

    /**
     * 检查是否完全在父领土边界内
     */
    public boolean isWithinBounds(Territory parent) {
        if (!this.worldName.equals(parent.getWorldName())) {
            return false;
        }

        return this.minX >= parent.getMinX() && this.maxX <= parent.getMaxX() &&
               this.minY >= parent.getMinY() && this.maxY <= parent.getMaxY() &&
               this.minZ >= parent.getMinZ() && this.maxZ <= parent.getMaxZ();
    }

    // ==================== 权限管理 ====================

    /**
     * 设置覆盖权限
     */
    public void setOverridePermission(TerritoryPermission permission, PermissionLevel level) {
        overridePermissions.put(permission, level);
    }

    /**
     * 移除覆盖权限（恢复继承）
     */
    public void removeOverridePermission(TerritoryPermission permission) {
        overridePermissions.remove(permission);
    }

    /**
     * 获取权限（考虑继承）
     */
    public PermissionLevel getPermission(TerritoryPermission permission, Territory parent) {
        // 如果有覆盖权限，使用覆盖
        if (overridePermissions.containsKey(permission)) {
            return overridePermissions.get(permission);
        }

        // 如果启用继承，使用父领土权限
        if (inheritPermissions && parent != null) {
            return parent.getPermission(permission);
        }

        // 默认无权限
        return PermissionLevel.NONE;
    }

    /**
     * 获取所有覆盖的权限
     */
    public Map<TerritoryPermission, PermissionLevel> getOverridePermissions() {
        return Collections.unmodifiableMap(overridePermissions);
    }

    /**
     * 清空所有覆盖权限
     */
    public void clearOverridePermissions() {
        overridePermissions.clear();
    }

    // ==================== 成员管理 ====================

    /**
     * 添加成员
     */
    public void addMember(UUID playerId, PermissionLevel level) {
        members.put(playerId, level);
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
        return members.getOrDefault(playerId, PermissionLevel.NONE);
    }

    /**
     * 检查是否为成员
     */
    public boolean isMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    /**
     * 获取所有成员
     */
    public Map<UUID, PermissionLevel> getAllMembers() {
        return Collections.unmodifiableMap(members);
    }

    // ==================== 优先级 ====================

    /**
     * 比较优先级
     */
    public int comparePriority(SubRegion other) {
        return Integer.compare(this.priority, other.priority);
    }

    /**
     * 是否优先级更高
     */
    public boolean hasHigherPriority(SubRegion other) {
        return this.priority > other.priority;
    }

    // ==================== 统计方法 ====================

    /**
     * 计算子区域体积
     */
    public long getVolume() {
        long width = maxX - minX + 1;
        long height = maxY - minY + 1;
        long depth = maxZ - minZ + 1;
        return width * height * depth;
    }

    /**
     * 计算子区域面积
     */
    public int getArea() {
        return (maxX - minX + 1) * (maxZ - minZ + 1);
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

    public UUID getParentTerritoryId() {
        return parentTerritoryId;
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

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isInheritPermissions() {
        return inheritPermissions;
    }

    public void setInheritPermissions(boolean inheritPermissions) {
        this.inheritPermissions = inheritPermissions;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return String.format("SubRegion[id=%s, name=%s, parent=%s, priority=%d, inherit=%b, overrides=%d]",
            id, name, parentTerritoryId, priority, inheritPermissions, overridePermissions.size());
    }
}
