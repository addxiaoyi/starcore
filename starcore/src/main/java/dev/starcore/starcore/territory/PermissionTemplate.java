package dev.starcore.starcore.territory;

import java.util.*;

/**
 * 权限模板类
 * 定义一组预设的权限配置
 */
public class PermissionTemplate {

    private final UUID id;
    private String name;
    private String description;

    // 权限配置
    private final Map<TerritoryPermission, PermissionLevel> permissions = new EnumMap<>(TerritoryPermission.class);

    // 是否为系统预设模板
    private final boolean isPreset;

    // 创建者
    private final UUID creatorId;

    // 创建时间
    private final long createdTime;

    public PermissionTemplate(String name, String description, UUID creatorId, boolean isPreset) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.creatorId = creatorId;
        this.isPreset = isPreset;
        this.createdTime = System.currentTimeMillis();
    }

    // ==================== 权限管理 ====================

    /**
     * 设置权限
     */
    public void setPermission(TerritoryPermission permission, PermissionLevel level) {
        permissions.put(permission, level);
    }

    /**
     * 获取权限
     */
    public PermissionLevel getPermission(TerritoryPermission permission) {
        return permissions.getOrDefault(permission, PermissionLevel.NONE);
    }

    /**
     * 移除权限
     */
    public void removePermission(TerritoryPermission permission) {
        permissions.remove(permission);
    }

    /**
     * 获取所有权限
     */
    public Map<TerritoryPermission, PermissionLevel> getAllPermissions() {
        return Collections.unmodifiableMap(permissions);
    }

    /**
     * 清空所有权限
     */
    public void clearPermissions() {
        if (isPreset) {
            throw new UnsupportedOperationException("不能修改预设模板");
        }
        permissions.clear();
    }

    /**
     * 批量设置权限
     */
    public void setPermissions(Map<TerritoryPermission, PermissionLevel> permissions) {
        if (isPreset) {
            throw new UnsupportedOperationException("不能修改预设模板");
        }
        this.permissions.clear();
        this.permissions.putAll(permissions);
    }

    // ==================== 应用模板 ====================

    /**
     * 应用到领土
     */
    public void applyToTerritory(Territory territory) {
        for (Map.Entry<TerritoryPermission, PermissionLevel> entry : permissions.entrySet()) {
            territory.setPermission(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 应用到子区域
     */
    public void applyToSubRegion(SubRegion subRegion) {
        subRegion.clearOverridePermissions();
        for (Map.Entry<TerritoryPermission, PermissionLevel> entry : permissions.entrySet()) {
            subRegion.setOverridePermission(entry.getKey(), entry.getValue());
        }
        subRegion.setInheritPermissions(false);
    }

    /**
     * 从领土创建模板
     */
    public static PermissionTemplate fromTerritory(Territory territory, String name,
                                                   String description, UUID creatorId) {
        PermissionTemplate template = new PermissionTemplate(name, description, creatorId, false);
        template.permissions.putAll(territory.getAllPermissions());
        return template;
    }

    /**
     * 从子区域创建模板
     */
    public static PermissionTemplate fromSubRegion(SubRegion subRegion, String name,
                                                   String description, UUID creatorId) {
        PermissionTemplate template = new PermissionTemplate(name, description, creatorId, false);
        template.permissions.putAll(subRegion.getOverridePermissions());
        return template;
    }

    // ==================== 克隆 ====================

    /**
     * 克隆模板
     */
    public PermissionTemplate clone(String newName, UUID creatorId) {
        PermissionTemplate clone = new PermissionTemplate(newName, this.description, creatorId, false);
        clone.permissions.putAll(this.permissions);
        return clone;
    }

    // ==================== Getter/Setter ====================

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (isPreset) {
            throw new UnsupportedOperationException("不能修改预设模板名称");
        }
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        if (isPreset) {
            throw new UnsupportedOperationException("不能修改预设模板描述");
        }
        this.description = description;
    }

    public boolean isPreset() {
        return isPreset;
    }

    public UUID getCreatorId() {
        return creatorId;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    @Override
    public String toString() {
        return String.format("PermissionTemplate[id=%s, name=%s, preset=%b, permissions=%d]",
            id, name, isPreset, permissions.size());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PermissionTemplate that = (PermissionTemplate) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
