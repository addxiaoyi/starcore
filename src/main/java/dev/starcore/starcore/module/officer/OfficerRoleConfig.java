package dev.starcore.starcore.module.officer;

import org.bukkit.Material;

/**
 * 官员角色配置
 * 定义角色的显示名称、图标和排序
 */
public record OfficerRoleConfig(
    String id,        // 角色唯一标识
    String displayName,  // 本地化显示名称
    Material icon,    // GUI图标
    int slot          // 排序顺序
) {
    /**
     * 快速创建角色配置
     */
    public static OfficerRoleConfig of(String id, String displayName, Material icon) {
        return new OfficerRoleConfig(id, displayName, icon, 0);
    }

    /**
     * 获取简洁显示名称（不含颜色代码）
     */
    public String getSimpleDisplayName() {
        return displayName.replaceAll("§[0-9a-fk-or]", "");
    }
}
