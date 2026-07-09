package dev.starcore.starcore.module.visualizer;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.Optional;

/**
 * 可视化条目 - 与 InteractionVisualizer 兼容的条目定义
 *
 * 这些条目用于 StarCore 自定义显示，如国家领地信息、特殊资源点等
 */
public enum VisualizerEntry {

    // ===== StarCore 自定义条目 =====

    // 国家/领地
    NATION_CLAIM("nation_claim", "国家领地"),
    NATION_CAPITAL("nation_capital", "首都"),
    NATION_BORDER("nation_border", "领地边界"),

    // 资源采集
    RESOURCE_MINERAL("resource_mineral", "矿物资源"),
    RESOURCE_TREE("resource_tree", "木材资源"),
    RESOURCE_FARM("resource_farm", "农业资源"),
    RESOURCE_FISHING("resource_fishing", "渔业资源"),

    // 战争状态
    WAR_TARGET("war_target", "战争目标"),
    WAR_ZONE("war_zone", "战争区域"),
    TRUCE_ZONE("truce_zone", "休战区域"),

    // 贸易
    TRADE_SHOP("trade_shop", "商店"),
    TRADE_NPC("trade_npc", "NPC 交易"),
    TRADE_ROUTE("trade_route", "贸易路线"),

    // 特殊功能
    QUEST_MARKER("quest_marker", "任务点"),
    TELEPORT_PAD("teleport_pad", "传送点"),
    SPAWN_POINT("spawn_point", "出生点");

    private final String key;
    private final String displayName;

    VisualizerEntry(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * 从方块识别条目
     */
    public static Optional<VisualizerEntry> fromBlock(Block block) {
        if (block == null) {
            return Optional.empty();
        }

        Material type = block.getType();

        // 矿物资源
        if (isOre(type)) {
            return Optional.of(RESOURCE_MINERAL);
        }

        // 树木
        if (isLog(type) || isLeaves(type)) {
            return Optional.of(RESOURCE_TREE);
        }

        // 农作物
        if (isCrop(type)) {
            return Optional.of(RESOURCE_FARM);
        }

        return Optional.empty();
    }

    private static boolean isOre(Material type) {
        return switch (type) {
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE,
                 GOLD_ORE, DEEPSLATE_GOLD_ORE, RAW_GOLD_BLOCK,
                 IRON_ORE, DEEPSLATE_IRON_ORE, RAW_IRON_BLOCK,
                 COPPER_ORE, DEEPSLATE_COPPER_ORE, RAW_COPPER_BLOCK,
                 COAL_ORE,
                 EMERALD_ORE, DEEPSLATE_EMERALD_ORE,
                 LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                 REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE,
                 NETHER_GOLD_ORE, NETHER_QUARTZ_ORE -> true;
            default -> false;
        };
    }

    private static boolean isLog(Material type) {
        return switch (type) {
            case OAK_LOG, BIRCH_LOG, SPRUCE_LOG, JUNGLE_LOG, DARK_OAK_LOG, ACACIA_LOG,
                 MANGROVE_LOG, CHERRY_LOG, CRIMSON_STEM, WARPED_STEM,
                 STRIPPED_OAK_LOG, STRIPPED_BIRCH_LOG, STRIPPED_SPRUCE_LOG,
                 STRIPPED_JUNGLE_LOG, STRIPPED_DARK_OAK_LOG, STRIPPED_ACACIA_LOG,
                 STRIPPED_MANGROVE_LOG, STRIPPED_CHERRY_LOG,
                 STRIPPED_CRIMSON_STEM, STRIPPED_WARPED_STEM -> true;
            default -> false;
        };
    }

    private static boolean isLeaves(Material type) {
        return switch (type) {
            case OAK_LEAVES, BIRCH_LEAVES, SPRUCE_LEAVES, JUNGLE_LEAVES,
                 DARK_OAK_LEAVES, ACACIA_LEAVES, MANGROVE_LEAVES, CHERRY_LEAVES,
                 AZALEA, FLOWERING_AZALEA,
                 CRIMSON_NYLIUM, WARPED_NYLIUM -> true;
            default -> false;
        };
    }

    private static boolean isCrop(Material type) {
        return switch (type) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS,
                 PUMPKIN, MELON, COCOA, SWEET_BERRY_BUSH,
                 NETHER_WART -> true;
            default -> false;
        };
    }
}
