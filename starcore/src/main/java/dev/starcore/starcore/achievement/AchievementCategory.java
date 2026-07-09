package dev.starcore.starcore.achievement;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

/**
 * 成就分类
 * 定义成就的不同类别
 */
public enum AchievementCategory {
    // 基础冒险
    ADVENTURE("冒险", "探索世界的每一片角落", Material.MAP, NamedTextColor.GREEN, 0),

    // 战斗与 PvP
    COMBAT("战斗", "征服所有敌人", Material.DIAMOND_SWORD, NamedTextColor.RED, 1),

    // 采集与资源
    GATHERING("采集", "收集各种资源", Material.DIAMOND_PICKAXE, NamedTextColor.AQUA, 2),

    // 农业与畜牧
    FARMING("农业", "发展农业与畜牧业", Material.WHEAT, NamedTextColor.YELLOW, 3),

    // 社交互动
    SOCIAL("社交", "与其他玩家互动", Material.PLAYER_HEAD, NamedTextColor.LIGHT_PURPLE, 4),

    // 王国与国家
    NATION("王国", "管理与扩展你的国家", Material.BROWN_BANNER, NamedTextColor.GOLD, 5),

    // 科技与工艺
    TECH("科技", "研究与升级科技", Material.BLAST_FURNACE, NamedTextColor.GRAY, 6),

    // 探索与维度
    EXPLORATION("探索", "发现所有维度", Material.END_PORTAL_FRAME, NamedTextColor.DARK_PURPLE, 7),

    // 经济与交易
    ECONOMY("经济", "积累财富", Material.GOLD_INGOT, NamedTextColor.YELLOW, 8),

    // 特殊成就
    SPECIAL("特殊", "稀有而独特的成就", Material.NETHER_STAR, NamedTextColor.DARK_RED, 9);

    private final String displayName;
    private final Component description;
    private final Material icon;
    private final NamedTextColor color;
    private final int sortOrder;

    AchievementCategory(String displayName, String description, Material icon, NamedTextColor color, int sortOrder) {
        this.displayName = displayName;
        this.description = Component.text(description);
        this.icon = icon;
        this.color = color;
        this.sortOrder = sortOrder;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Component getDescription() {
        return description;
    }

    public Material getIcon() {
        return icon;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Component getColoredName() {
        return Component.text(displayName, color);
    }
}
