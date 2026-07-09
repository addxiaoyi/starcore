package dev.starcore.starcore.module.diplomacy.gui;

import dev.starcore.starcore.foundation.gui.ButtonFactory;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.diplomacy.network.DiplomacyGraph;
import dev.starcore.starcore.module.diplomacy.network.NetworkVisualizationService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外交关系网络可视化菜单
 *
 * 提供交互式关系网络展示:
 * - 全局关系网络视图
 * - 局部关系网络（以玩家国家为中心）
 * - 关系详情查看
 * - 网络统计信息
 */
public class DiplomacyNetworkMenu {

    // GUI 标题
    public static final String MAIN_NETWORK_TITLE = "§6§l🌐 外交关系网络";
    public static final String GLOBAL_NETWORK_TITLE = "§e§l🌐 全球关系网络";
    public static final String LOCAL_NETWORK_TITLE = "§b§l🌐 势力关系图";
    public static final String NETWORK_STATS_TITLE = "§6§l📊 网络统计";
    public static final String NATION_DETAIL_TITLE = "§b§l🔍 国家详情";

    private final NetworkVisualizationService networkService;
    private final NationService nationService;

    // 分页状态
    private final Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();
    private final Map<UUID, NationId> selectedNation = new ConcurrentHashMap<>();

    // 当前网络图缓存
    private DiplomacyGraph cachedGraph = null;
    private long lastCacheTime = 0;
    private static final long CACHE_DURATION_MS = 30000; // 30秒缓存

    public DiplomacyNetworkMenu(
            NetworkVisualizationService networkService,
            NationService nationService
    ) {
        this.networkService = networkService;
        this.nationService = nationService;
    }

    /**
     * 打开主菜单
     */
    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, Component.text(MAIN_NETWORK_TITLE));
        fillBorder(inv, Material.ORANGE_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, ButtonFactory.createStyledButton(
            "§6§l🌐 外交关系网络",
            Material.NETHER_STAR,
            ButtonFactory.BUTTON_STYLE_PRIMARY,
            "可视化展示所有国家的外交关系"
        ));

        NationId playerNationId = getPlayerNationId(player);

        // 功能按钮
        inv.setItem(10, ButtonFactory.createStyledButton(
            "§e🌐 全球关系网络",
            Material.MAP,
            ButtonFactory.BUTTON_STYLE_INFO,
            "查看所有国家间的外交关系",
            "包括联盟、敌对等状态"
        ));

        inv.setItem(12, ButtonFactory.createStyledButton(
            "§b🔍 势力关系图",
            Material.COMPASS,
            ButtonFactory.BUTTON_STYLE_PRIMARY,
            "以你的国家为中心",
            "查看周边国家关系"
        ));

        inv.setItem(14, ButtonFactory.createStyledButton(
            "§a📊 网络统计",
            Material.BEACON,
            ButtonFactory.BUTTON_STYLE_SUCCESS,
            "查看外交网络统计信息",
            "联盟排名、最活跃国家等"
        ));

        inv.setItem(16, ButtonFactory.createStyledButton(
            "§6🔎 查找国家",
            Material.ENDER_EYE,
            ButtonFactory.BUTTON_STYLE_SECONDARY,
            "搜索并查看特定国家的关系"
        ));

        // 统计预览
        if (cachedGraph == null || System.currentTimeMillis() - lastCacheTime > CACHE_DURATION_MS) {
            cachedGraph = networkService.buildGraph();
            lastCacheTime = System.currentTimeMillis();
        }

        int nationCount = cachedGraph.getNodeCount();
        int relationCount = cachedGraph.getEdgeCount();
        int allianceCount = (int) cachedGraph.getEdges().stream()
            .filter(e -> e.relation() == DiplomacyRelation.ALLIED)
            .count();
        int warCount = (int) cachedGraph.getEdges().stream()
            .filter(e -> e.relation() == DiplomacyRelation.WAR)
            .count();

        inv.setItem(19, ButtonFactory.createStatButton(
            Material.PAPER,
            "§7国家总数",
            String.valueOf(nationCount)
        ));

        inv.setItem(21, ButtonFactory.createStatButton(
            Material.EMERALD,
            "§7联盟总数",
            String.valueOf(allianceCount)
        ));

        inv.setItem(23, ButtonFactory.createStatButton(
            Material.REDSTONE,
            "§7战争总数",
            String.valueOf(warCount)
        ));

        // 帮助信息
        inv.setItem(31, ButtonFactory.createInfoButton(
            "提示: 点击上方按钮查看详细关系网络",
            "不同颜色代表不同的外交关系"
        ));

        // 图例说明
        inv.setItem(35, ButtonFactory.createInfoButton(
            "图例: §a绿线=联盟 §c红线=战争 §7灰线=中立"
        ));

        player.openInventory(inv);
    }

    /**
     * 打开全局关系网络
     */
    public void openGlobalNetwork(Player player, int page) {
        playerPages.put(player.getUniqueId(), page);

        if (cachedGraph == null || System.currentTimeMillis() - lastCacheTime > CACHE_DURATION_MS) {
            cachedGraph = networkService.buildGraph();
            lastCacheTime = System.currentTimeMillis();
        }

        DiplomacyGraph graph = cachedGraph;
        List<DiplomacyGraph.NationNode> nodes = new ArrayList<>(graph.getNodes());
        nodes.sort(Comparator.comparingInt(DiplomacyGraph.NationNode::influence).reversed());

        // 计算大小
        int itemsPerPage = 28;
        int totalPages = Math.max(1, (nodes.size() + itemsPerPage - 1) / itemsPerPage);
        page = Math.min(page, totalPages - 1);

        int size = 54;
        Inventory inv = Bukkit.createInventory(null, size, Component.text(GLOBAL_NETWORK_TITLE));
        fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, ButtonFactory.createStyledButton(
            "§e§l🌐 全球外交关系网络",
            Material.MAP,
            ButtonFactory.BUTTON_STYLE_INFO,
            "第 " + (page + 1) + " 页 / 共 " + totalPages + " 页"
        ));

        // 显示国家节点列表
        int startIdx = page * itemsPerPage;
        int endIdx = Math.min(startIdx + itemsPerPage, nodes.size());

        int slot = 10;
        for (int i = startIdx; i < endIdx; i++) {
            if (slot % 9 == 8) slot += 2;
            if (slot >= size - 10) break;

            DiplomacyGraph.NationNode node = nodes.get(i);
            ItemStack item = createNationNodeItem(graph, node);
            inv.setItem(slot, item);
            slot++;
        }

        // 底部功能
        inv.setItem(49, ButtonFactory.createStyledButton(
            "§e🌐 文本视图",
            Material.BOOK,
            ButtonFactory.BUTTON_STYLE_INFO,
            "以文字形式显示关系网络"
        ));

        // 分页
        if (totalPages > 1) {
            if (page > 0) {
                inv.setItem(45, ButtonFactory.createPrevButton("第 " + page + " 页"));
            }
            if (page < totalPages - 1) {
                inv.setItem(53, ButtonFactory.createNextButton("第 " + (page + 2) + " 页"));
            }
        }

        // 返回按钮
        inv.setItem(size - 9 + 4, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    /**
     * 打开局部关系网络（以玩家国家为中心）
     */
    public void openLocalNetwork(Player player) {
        NationId playerNationId = getPlayerNationId(player);
        if (playerNationId == null) {
            player.sendMessage("§c你需要先加入一个国家才能查看关系网络");
            return;
        }

        // 构建局部图（包含二级关系）
        DiplomacyGraph graph = networkService.buildLocalGraph(playerNationId, 2);

        if (graph.getNodeCount() <= 1) {
            player.sendMessage("§e你的国家暂无外交关系");
            openMainMenu(player);
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, Component.text(LOCAL_NETWORK_TITLE));
        fillBorder(inv, Material.BLUE_STAINED_GLASS_PANE);

        Optional<Nation> centerNation = nationService.nationById(playerNationId);

        // 标题
        inv.setItem(4, ButtonFactory.createStyledButton(
            "§b§l🌐 " + centerNation.map(Nation::name).orElse("你的国家") + " 势力关系图",
            Material.COMPASS,
            ButtonFactory.BUTTON_STYLE_PRIMARY,
            "展示你的国家及周边国家的外交关系"
        ));

        // 统计
        int allyCount = graph.getAllyCount(playerNationId);
        int enemyCount = graph.getEnemyCount(playerNationId);

        inv.setItem(19, ButtonFactory.createStatButton(
            Material.EMERALD,
            "§a盟国",
            String.valueOf(allyCount)
        ));

        inv.setItem(21, ButtonFactory.createStatButton(
            Material.REDSTONE,
            "§c敌国",
            String.valueOf(enemyCount)
        ));

        // 绘制简化关系图（使用物品表示）
        // 中心节点（玩家国家）
        NationId finalPlayerNationId = playerNationId;
        NationNodeDisplay centerNode = new NationNodeDisplay(
            centerNation.get().name(),
            4, 4, // 中心位置
            Material.GREEN_BANNER,
            true
        );
        inv.setItem(22, createPositionedNodeItem(centerNode));

        // 一级关系节点
        List<DiplomacyGraph.RelationEdge> directEdges = graph.getEdgesOf(playerNationId);
        int[] directSlots = {11, 13, 20, 24, 29, 33}; // 一级节点槽位
        int directIdx = 0;

        for (DiplomacyGraph.RelationEdge edge : directEdges) {
            if (directIdx >= directSlots.length) break;
            NationId otherId = edge.other(playerNationId);
            Optional<Nation> otherNation = nationService.nationById(otherId);
            if (otherNation.isEmpty()) continue;

            Material mat = switch (edge.relation()) {
                case ALLIED -> Material.EMERALD;
                case WAR -> Material.REDSTONE;
                case CEASE_FIRE -> Material.YELLOW_STAINED_GLASS_PANE;
                case HOSTILE -> Material.NETHER_WART;
                default -> Material.PAPER;
            };

            NationNodeDisplay nodeDisplay = new NationNodeDisplay(
                otherNation.get().name(),
                0, 0, mat, false
            );

            // 设置特殊图标
            String prefix = switch (edge.relation()) {
                case ALLIED -> "§a[盟] ";
                case WAR -> "§c[敌] ";
                case CEASE_FIRE -> "§e[停] ";
                case HOSTILE -> "§c[恶] ";
                default -> "§7";
            };

            ItemStack item = createRelationNodeItem(prefix, otherNation.get().name(), mat, edge.relation());
            inv.setItem(directSlots[directIdx], item);
            directIdx++;
        }

        // 功能按钮
        inv.setItem(38, ButtonFactory.createStyledButton(
            "§a➕ 发起联盟",
            Material.LAPIS_LAZULI,
            ButtonFactory.BUTTON_STYLE_SUCCESS,
            "向其他国家发送联盟邀请"
        ));

        inv.setItem(40, ButtonFactory.createStyledButton(
            "§c⚔️ 发动战争",
            Material.IRON_SWORD,
            ButtonFactory.BUTTON_STYLE_DANGER,
            "向敌对国家宣战"
        ));

        inv.setItem(42, ButtonFactory.createStyledButton(
            "§b🔍 关系详情",
            Material.ENDER_EYE,
            ButtonFactory.BUTTON_STYLE_INFO,
            "查看详细的二阶关系"
        ));

        // 图例
        inv.setItem(48, ButtonFactory.createInfoButton(
            "图例: §a绿色=盟国 §c红色=敌国 §e黄色=停战"
        ));

        // 返回按钮
        inv.setItem(53, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    /**
     * 打开网络统计页面
     */
    public void openNetworkStats(Player player) {
        if (cachedGraph == null || System.currentTimeMillis() - lastCacheTime > CACHE_DURATION_MS) {
            cachedGraph = networkService.buildGraph();
            lastCacheTime = System.currentTimeMillis();
        }

        DiplomacyGraph graph = cachedGraph;

        Inventory inv = Bukkit.createInventory(null, 45, Component.text(NETWORK_STATS_TITLE));
        fillBorder(inv, Material.ORANGE_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, ButtonFactory.createStyledButton(
            "§6§l📊 外交网络统计",
            Material.BEACON,
            ButtonFactory.BUTTON_STYLE_PRIMARY
        ));

        // 总体统计
        inv.setItem(10, ButtonFactory.createStatButton(
            Material.PAPER,
            "§7国家总数",
            String.valueOf(graph.getNodeCount())
        ));

        inv.setItem(11, ButtonFactory.createStatButton(
            Material.EMERALD,
            "§7联盟总数",
            String.valueOf((int) graph.getEdges().stream()
                .filter(e -> e.relation() == DiplomacyRelation.ALLIED).count())
        ));

        inv.setItem(12, ButtonFactory.createStatButton(
            Material.REDSTONE,
            "§7战争总数",
            String.valueOf((int) graph.getEdges().stream()
                .filter(e -> e.relation() == DiplomacyRelation.WAR).count())
        ));

        inv.setItem(14, ButtonFactory.createStatButton(
            Material.BOOK,
            "§7关系总数",
            String.valueOf(graph.getEdgeCount())
        ));

        // 最具影响力的国家
        Optional<DiplomacyGraph.NationNode> mostInfluential = graph.getMostConnectedNation();
        if (mostInfluential.isPresent()) {
            DiplomacyGraph.NationNode topNation = mostInfluential.get();
            inv.setItem(16, ButtonFactory.createStyledButton(
                "§6👑 最具影响力",
                Material.GOLD_INGOT,
                ButtonFactory.BUTTON_STYLE_PRIMARY,
                "国家: §f" + topNation.name(),
                "影响力: §e" + topNation.influence(),
                "关系数: §f" + graph.getEdgesOf(topNation.id()).size()
            ));
        }

        // 影响力排名
        inv.setItem(22, ButtonFactory.createStyledButton(
            "§e🏆 影响力排名",
            Material.NETHER_STAR,
            ButtonFactory.BUTTON_STYLE_INFO,
            "查看各国影响力排名"
        ));

        // 列表显示前5名
        List<DiplomacyGraph.NationNode> sortedNodes = graph.getNodes().stream()
            .sorted(Comparator.comparingInt(DiplomacyGraph.NationNode::influence).reversed())
            .limit(5)
            .toList();

        int rankSlot = 28;
        int rank = 1;
        for (DiplomacyGraph.NationNode node : sortedNodes) {
            Material medalMat = switch (rank) {
                case 1 -> Material.GOLD_INGOT;
                case 2 -> Material.IRON_INGOT;
                case 3 -> Material.COPPER_INGOT;
                default -> Material.PAPER;
            };

            ItemStack rankItem = ButtonFactory.createStyledButton(
                "§e#" + rank + " §f" + node.name(),
                medalMat,
                ButtonFactory.BUTTON_STYLE_INFO,
                "影响力: §a" + node.influence(),
                "盟国: §a" + graph.getAllyCount(node.id()),
                "敌国: §c" + graph.getEnemyCount(node.id())
            );
            inv.setItem(rankSlot, rankItem);
            rankSlot++;
            rank++;
        }

        // 返回按钮
        inv.setItem(40, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    /**
     * 打开国家详情页面
     */
    public void openNationDetail(Player player, NationId nationId) {
        Optional<Nation> nationOpt = nationService.nationById(nationId);
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c找不到该国家");
            return;
        }

        Nation nation = nationOpt.get();
        String title = "§b§l🔍 " + nation.name() + " 详情";

        Inventory inv = Bukkit.createInventory(null, 45, Component.text(title));
        fillBorder(inv, Material.BLUE_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, ButtonFactory.createStyledButton(
            "§b§l🔍 " + nation.name() + " 详情",
            Material.ENDER_EYE,
            ButtonFactory.BUTTON_STYLE_INFO
        ));

        // 国家基本信息
        inv.setItem(10, ButtonFactory.createStyledButton(
            "§e国家名称",
            Material.PAPER,
            ButtonFactory.BUTTON_STYLE_SECONDARY,
            "§f" + nation.name()
        ));

        inv.setItem(11, ButtonFactory.createStyledButton(
            "§e成员数量",
            Material.PLAYER_HEAD,
            ButtonFactory.BUTTON_STYLE_SECONDARY,
            "§f" + nation.members().size() + " 人"
        ));

        // 外交关系
        if (cachedGraph == null) {
            cachedGraph = networkService.buildGraph();
            lastCacheTime = System.currentTimeMillis();
        }

        int allyCount = cachedGraph.getAllyCount(nationId);
        int enemyCount = cachedGraph.getEnemyCount(nationId);
        int totalRelations = cachedGraph.getEdgesOf(nationId).size();

        inv.setItem(13, ButtonFactory.createStatButton(
            Material.EMERALD,
            "§a盟国",
            String.valueOf(allyCount)
        ));

        inv.setItem(14, ButtonFactory.createStatButton(
            Material.REDSTONE,
            "§c敌国",
            String.valueOf(enemyCount)
        ));

        inv.setItem(15, ButtonFactory.createStatButton(
            Material.BOOK,
            "§7总关系",
            String.valueOf(totalRelations)
        ));

        // 盟国列表
        inv.setItem(19, ButtonFactory.createStyledButton(
            "§a盟国列表",
            Material.EMERALD,
            ButtonFactory.BUTTON_STYLE_SUCCESS,
            "查看该国家的盟国"
        ));

        List<DiplomacyGraph.RelationEdge> allyEdges = cachedGraph.getEdges().stream()
            .filter(e -> e.involves(nationId) && e.relation() == DiplomacyRelation.ALLIED)
            .toList();

        int slot = 20;
        for (DiplomacyGraph.RelationEdge edge : allyEdges) {
            if (slot > 25) break;
            NationId otherId = edge.other(nationId);
            String otherName = cachedGraph.getNationName(otherId);

            ItemStack item = ButtonFactory.createStyledButton(
                "§a+ " + otherName,
                Material.EMERALD,
                ButtonFactory.BUTTON_STYLE_SUCCESS
            );
            inv.setItem(slot, item);
            slot++;
        }

        if (allyEdges.isEmpty()) {
            inv.setItem(22, ButtonFactory.createInfoButton("§7暂无盟国"));
        }

        // 敌国列表
        inv.setItem(28, ButtonFactory.createStyledButton(
            "§c敌国列表",
            Material.REDSTONE,
            ButtonFactory.BUTTON_STYLE_DANGER,
            "查看该国家的敌国"
        ));

        List<DiplomacyGraph.RelationEdge> enemyEdges = cachedGraph.getEdges().stream()
            .filter(e -> e.involves(nationId) && e.relation() == DiplomacyRelation.WAR)
            .toList();

        slot = 29;
        for (DiplomacyGraph.RelationEdge edge : enemyEdges) {
            if (slot > 34) break;
            NationId otherId = edge.other(nationId);
            String otherName = cachedGraph.getNationName(otherId);

            ItemStack item = ButtonFactory.createStyledButton(
                "§c x " + otherName,
                Material.REDSTONE,
                ButtonFactory.BUTTON_STYLE_DANGER
            );
            inv.setItem(slot, item);
            slot++;
        }

        if (enemyEdges.isEmpty()) {
            inv.setItem(31, ButtonFactory.createInfoButton("§7暂无敌国"));
        }

        // 操作按钮（如果是玩家国家）
        NationId playerNationId = getPlayerNationId(player);
        if (playerNationId != null && playerNationId.equals(nationId)) {
            inv.setItem(37, ButtonFactory.createStyledButton(
                "§e🗺️ 查看势力图",
                Material.MAP,
                ButtonFactory.BUTTON_STYLE_INFO,
                "以该国家为中心查看关系网络"
            ));
        }

        // 返回按钮
        inv.setItem(40, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    // ==================== 辅助方法 ====================

    private ItemStack createNationNodeItem(DiplomacyGraph graph, DiplomacyGraph.NationNode node) {
        Material material = Material.PLAYER_HEAD;
        int allyCount = graph.getAllyCount(node.id());
        int enemyCount = graph.getEnemyCount(node.id());

        StringBuilder title = new StringBuilder();
        if (allyCount > 0) {
            title.append("§a").append(allyCount).append("盟 ");
        }
        if (enemyCount > 0) {
            title.append("§c").append(enemyCount).append("敌 ");
        }
        title.append("§f").append(node.name());

        return ButtonFactory.createStyledButton(
            title.toString(),
            material,
            ButtonFactory.BUTTON_STYLE_INFO,
            "影响力: §e" + node.influence(),
            "",
            "§7盟国: §a" + allyCount + " §7敌国: §c" + enemyCount,
            "",
            "§e点击查看详情"
        );
    }

    private ItemStack createRelationNodeItem(String prefix, String nationName, Material material, DiplomacyRelation relation) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(prefix + nationName)
                .color(NamedTextColor.WHITE)
                .decorate(TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>();
            String relationName = switch (relation) {
                case ALLIED -> "§a联盟";
                case WAR -> "§c战争";
                case CEASE_FIRE -> "§e停战";
                case HOSTILE -> "§c敌对";
                case FRIENDLY -> "§a友好";
                default -> "§7中立";
            };
            lore.add(Component.text("关系: " + relationName, NamedTextColor.GRAY));
            lore.add(Component.text(""));
            lore.add(Component.text("§e点击查看详情", NamedTextColor.YELLOW));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPositionedNodeItem(NationNodeDisplay node) {
        Material material = node.isCenter ? Material.GREEN_BANNER : Material.PAPER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String prefix = node.isCenter ? "§a★ " : "§7";
            meta.displayName(Component.text(prefix + node.name)
                .color(NamedTextColor.WHITE)
                .decorate(TextDecoration.BOLD));
            if (node.isCenter) {
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("§e你的国家（中心）", NamedTextColor.GOLD));
                lore.add(Component.text(""));
                lore.add(Component.text("§7所有关系以此为中心", NamedTextColor.GRAY));
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillBorder(Inventory inv, Material material) {
        ItemStack border = ButtonFactory.createBorder(material);
        int size = inv.getSize();
        int rows = size / 9;

        for (int row = 0; row < rows; row++) {
            inv.setItem(row * 9, border);
            inv.setItem(row * 9 + 8, border);
        }
        for (int col = 1; col < 8; col++) {
            inv.setItem(col, border);
            inv.setItem(size - 9 + col, border);
        }
    }

    private NationId getPlayerNationId(Player player) {
        return nationService.nationOf(player.getUniqueId()).map(Nation::getId).orElse(null);
    }

    /**
     * 清除缓存
     */
    public void invalidateCache() {
        cachedGraph = null;
        lastCacheTime = 0;
    }

    // 内部类用于绘制节点
    private static class NationNodeDisplay {
        final String name;
        final double x, y;
        final Material material;
        final boolean isCenter;

        NationNodeDisplay(String name, double x, double y, Material material, boolean isCenter) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.material = material;
            this.isCenter = isCenter;
        }
    }
}