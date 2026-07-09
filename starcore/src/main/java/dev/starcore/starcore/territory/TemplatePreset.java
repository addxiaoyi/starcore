package dev.starcore.starcore.territory;

/**
 * 预设权限模板枚举
 * 定义常用的权限配置模板
 */
public enum TemplatePreset {

    /**
     * 访客模板 - 只能行走和观看
     */
    VISITOR("访客", "只允许基本的移动和观看，无法进行任何交互") {
        @Override
        public void configure(PermissionTemplate template) {
            // 所有权限都设为NONE，除了传送
            for (TerritoryPermission perm : TerritoryPermission.values()) {
                template.setPermission(perm, PermissionLevel.NONE);
            }
            template.setPermission(TerritoryPermission.TELEPORT, PermissionLevel.VISITOR);
        }
    },

    /**
     * 信任模板 - 可以交互但不能建造
     */
    TRUSTED("信任", "可以使用门、按钮等交互，但不能建造或破坏") {
        @Override
        public void configure(PermissionTemplate template) {
            // 基本权限
            template.setPermission(TerritoryPermission.INTERACT, PermissionLevel.TRUSTED);
            template.setPermission(TerritoryPermission.REDSTONE, PermissionLevel.TRUSTED);
            template.setPermission(TerritoryPermission.TELEPORT, PermissionLevel.TRUSTED);
            template.setPermission(TerritoryPermission.VEHICLE, PermissionLevel.TRUSTED);

            // 禁止建造和破坏
            template.setPermission(TerritoryPermission.BUILD, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.BREAK, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.CONTAINER, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.ITEM_USE, PermissionLevel.MEMBER);

            // 禁止危险操作
            template.setPermission(TerritoryPermission.PVP, PermissionLevel.NONE);
            template.setPermission(TerritoryPermission.EXPLOSION, PermissionLevel.NONE);
            template.setPermission(TerritoryPermission.SPAWN_MOBS, PermissionLevel.OWNER);
        }
    },

    /**
     * 建筑者模板 - 可以建造和破坏
     */
    BUILDER("建筑者", "拥有建造和破坏权限，适合建筑工人") {
        @Override
        public void configure(PermissionTemplate template) {
            // 建造权限
            template.setPermission(TerritoryPermission.BUILD, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.BREAK, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.INTERACT, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.ITEM_USE, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.REDSTONE, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.FARMLAND, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.ITEM_FRAME, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.TELEPORT, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.VEHICLE, PermissionLevel.MEMBER);

            // 限制容器访问
            template.setPermission(TerritoryPermission.CONTAINER, PermissionLevel.ADMIN);

            // 禁止危险操作
            template.setPermission(TerritoryPermission.PVP, PermissionLevel.NONE);
            template.setPermission(TerritoryPermission.EXPLOSION, PermissionLevel.NONE);
            template.setPermission(TerritoryPermission.SPAWN_MOBS, PermissionLevel.OWNER);
            template.setPermission(TerritoryPermission.HURT_ANIMALS, PermissionLevel.ADMIN);
        }
    },

    /**
     * 成员模板 - 完整的居住权限
     */
    MEMBER("成员", "完整的居住和使用权限，适合领土成员") {
        @Override
        public void configure(PermissionTemplate template) {
            // 所有基本权限
            template.setPermission(TerritoryPermission.BUILD, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.BREAK, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.INTERACT, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.CONTAINER, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.ITEM_USE, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.REDSTONE, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.FARMLAND, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.ITEM_FRAME, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.TELEPORT, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.VEHICLE, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.HURT_ANIMALS, PermissionLevel.MEMBER);

            // 禁止危险操作
            template.setPermission(TerritoryPermission.PVP, PermissionLevel.NONE);
            template.setPermission(TerritoryPermission.EXPLOSION, PermissionLevel.NONE);
            template.setPermission(TerritoryPermission.SPAWN_MOBS, PermissionLevel.ADMIN);
        }
    },

    /**
     * 管理员模板 - 管理权限
     */
    ADMIN("管理员", "拥有除所有权以外的所有权限") {
        @Override
        public void configure(PermissionTemplate template) {
            // 所有权限设为ADMIN
            for (TerritoryPermission perm : TerritoryPermission.values()) {
                template.setPermission(perm, PermissionLevel.ADMIN);
            }

            // 某些特殊权限仍然只有所有者可以
            template.setPermission(TerritoryPermission.SPAWN_MOBS, PermissionLevel.OWNER);
        }
    },

    /**
     * 商店模板 - 商业区专用
     */
    SHOP("商店", "适合商业区，允许访客交互但不能建造") {
        @Override
        public void configure(PermissionTemplate template) {
            // 访客可以交互和使用物品
            template.setPermission(TerritoryPermission.INTERACT, PermissionLevel.VISITOR);
            template.setPermission(TerritoryPermission.ITEM_USE, PermissionLevel.VISITOR);
            template.setPermission(TerritoryPermission.CONTAINER, PermissionLevel.VISITOR);
            template.setPermission(TerritoryPermission.TELEPORT, PermissionLevel.VISITOR);
            template.setPermission(TerritoryPermission.VEHICLE, PermissionLevel.VISITOR);

            // 只有成员可以建造
            template.setPermission(TerritoryPermission.BUILD, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.BREAK, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.REDSTONE, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.ITEM_FRAME, PermissionLevel.MEMBER);

            // 禁止危险操作
            template.setPermission(TerritoryPermission.PVP, PermissionLevel.NONE);
            template.setPermission(TerritoryPermission.EXPLOSION, PermissionLevel.NONE);
            template.setPermission(TerritoryPermission.SPAWN_MOBS, PermissionLevel.NONE);
            template.setPermission(TerritoryPermission.HURT_ANIMALS, PermissionLevel.ADMIN);
            template.setPermission(TerritoryPermission.FARMLAND, PermissionLevel.MEMBER);
        }
    },

    /**
     * 农场模板 - 农业区专用
     */
    FARM("农场", "允许耕种和养殖，限制建造") {
        @Override
        public void configure(PermissionTemplate template) {
            // 农业相关权限对信任者开放
            template.setPermission(TerritoryPermission.FARMLAND, PermissionLevel.TRUSTED);
            template.setPermission(TerritoryPermission.HURT_ANIMALS, PermissionLevel.TRUSTED);
            template.setPermission(TerritoryPermission.ITEM_USE, PermissionLevel.TRUSTED);
            template.setPermission(TerritoryPermission.INTERACT, PermissionLevel.TRUSTED);
            template.setPermission(TerritoryPermission.TELEPORT, PermissionLevel.TRUSTED);

            // 建造权限限制为成员
            template.setPermission(TerritoryPermission.BUILD, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.BREAK, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.CONTAINER, PermissionLevel.MEMBER);
            template.setPermission(TerritoryPermission.REDSTONE, PermissionLevel.MEMBER);

            // 允许怪物生成（农场需要）
            template.setPermission(TerritoryPermission.SPAWN_MOBS, PermissionLevel.TRUSTED);

            // 禁止危险操作
            template.setPermission(TerritoryPermission.PVP, PermissionLevel.NONE);
            template.setPermission(TerritoryPermission.EXPLOSION, PermissionLevel.NONE);
            template.setPermission(TerritoryPermission.VEHICLE, PermissionLevel.TRUSTED);
            template.setPermission(TerritoryPermission.ITEM_FRAME, PermissionLevel.MEMBER);
        }
    },

    /**
     * PvP竞技场模板
     */
    ARENA("竞技场", "PvP战斗区域，允许玩家对战") {
        @Override
        public void configure(PermissionTemplate template) {
            // 所有人都能进入和交互
            template.setPermission(TerritoryPermission.INTERACT, PermissionLevel.VISITOR);
            template.setPermission(TerritoryPermission.TELEPORT, PermissionLevel.VISITOR);
            template.setPermission(TerritoryPermission.VEHICLE, PermissionLevel.VISITOR);

            // 允许PvP
            template.setPermission(TerritoryPermission.PVP, PermissionLevel.VISITOR);

            // 禁止建造和破坏
            template.setPermission(TerritoryPermission.BUILD, PermissionLevel.ADMIN);
            template.setPermission(TerritoryPermission.BREAK, PermissionLevel.ADMIN);
            template.setPermission(TerritoryPermission.CONTAINER, PermissionLevel.NONE);
            template.setPermission(TerritoryPermission.ITEM_USE, PermissionLevel.VISITOR);

            // 其他权限
            template.setPermission(TerritoryPermission.EXPLOSION, PermissionLevel.NONE);
            template.setPermission(TerritoryPermission.SPAWN_MOBS, PermissionLevel.ADMIN);
            template.setPermission(TerritoryPermission.HURT_ANIMALS, PermissionLevel.VISITOR);
            template.setPermission(TerritoryPermission.FARMLAND, PermissionLevel.NONE);
            template.setPermission(TerritoryPermission.REDSTONE, PermissionLevel.ADMIN);
            template.setPermission(TerritoryPermission.ITEM_FRAME, PermissionLevel.ADMIN);
        }
    };

    private final String displayName;
    private final String description;

    TemplatePreset(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * 配置模板（由子类实现）
     */
    public abstract void configure(PermissionTemplate template);

    /**
     * 创建预设模板实例
     */
    public PermissionTemplate createTemplate() {
        PermissionTemplate template = new PermissionTemplate(
            displayName,
            description,
            null, // 系统模板无创建者
            true  // 标记为预设
        );
        configure(template);
        return template;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 从字符串获取预设
     */
    public static TemplatePreset fromString(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 获取所有预设的显示名称
     */
    public static String[] getDisplayNames() {
        TemplatePreset[] presets = values();
        String[] names = new String[presets.length];
        for (int i = 0; i < presets.length; i++) {
            names[i] = presets[i].displayName;
        }
        return names;
    }
}
