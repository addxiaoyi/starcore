package dev.starcore.starcore.module.diplomacy.gui;

import dev.starcore.starcore.foundation.gui.ButtonFactory;
import dev.starcore.starcore.module.diplomacy.network.NetworkVisualizationService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外交关系网络菜单监听器
 *
 * 处理玩家与网络可视化菜单的交互
 */
public class DiplomacyNetworkListener implements Listener {

    private final DiplomacyNetworkMenu networkMenu;
    private final NetworkVisualizationService networkService;
    private final NationService nationService;

    // 菜单状态跟踪
    private final Map<UUID, String> playerStates = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();

    public DiplomacyNetworkListener(
            DiplomacyNetworkMenu networkMenu,
            NetworkVisualizationService networkService,
            NationService nationService
    ) {
        this.networkMenu = networkMenu;
        this.networkService = networkService;
        this.nationService = nationService;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        if (inv == null) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        // 检查是否是网络菜单
        if (!title.contains("外交关系网络") &&
            !title.contains("全球关系网络") &&
            !title.contains("势力关系图") &&
            !title.contains("网络统计") &&
            !title.contains("国家详情")) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // 播放音效
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

        // 处理边框点击
        if (isBorderItem(clickedItem)) {
            return;
        }

        int slot = event.getSlot();
        String itemName = getItemName(clickedItem);

        // 处理返回按钮
        if (itemName.contains("返回") || itemName.contains("返回上级")) {
            handleBackButton(player, title);
            return;
        }

        // 根据菜单类型处理
        if (title.contains("外交关系网络") && !title.contains("全球") && !title.contains("势力")) {
            handleMainMenuClick(player, slot, itemName, clickedItem);
        } else if (title.contains("全球关系网络")) {
            handleGlobalNetworkClick(player, slot, itemName, clickedItem);
        } else if (title.contains("势力关系图")) {
            handleLocalNetworkClick(player, slot, itemName, clickedItem);
        } else if (title.contains("网络统计")) {
            handleStatsClick(player, slot, itemName, clickedItem);
        } else if (title.contains("国家详情")) {
            handleNationDetailClick(player, slot, itemName, clickedItem);
        }
    }

    // ==================== 主菜单处理 ====================

    private void handleMainMenuClick(Player player, int slot, String itemName, ItemStack item) {
        Material material = item.getType();

        switch (slot) {
            case 10 -> {
                // 全球关系网络
                playerPages.put(player.getUniqueId(), 0);
                networkMenu.openGlobalNetwork(player, 0);
            }
            case 12 -> {
                // 势力关系图（局部网络）
                networkMenu.openLocalNetwork(player);
            }
            case 14 -> {
                // 网络统计
                networkMenu.openNetworkStats(player);
            }
            case 16 -> {
                // 查找国家
                openNationSearch(player);
            }
        }
    }

    // ==================== 全局网络处理 ====================

    private void handleGlobalNetworkClick(Player player, int slot, String itemName, ItemStack item) {
        Material material = item.getType();

        // 返回按钮
        if (itemName.contains("返回")) {
            networkMenu.openMainMenu(player);
            return;
        }

        // 文本视图
        if (itemName.contains("文本视图")) {
            showTextGraph(player);
            return;
        }

        // 分页
        if (itemName.contains("上一页")) {
            int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);
            if (currentPage > 0) {
                currentPage--;
                playerPages.put(player.getUniqueId(), currentPage);
                networkMenu.openGlobalNetwork(player, currentPage);
            }
            return;
        }

        if (itemName.contains("下一页")) {
            int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);
            currentPage++;
            playerPages.put(player.getUniqueId(), currentPage);
            networkMenu.openGlobalNetwork(player, currentPage);
            return;
        }

        // 国家节点点击 - 检查是否是玩家头颅物品
        if (material == Material.PLAYER_HEAD || material == Material.PAPER ||
            material == Material.EMERALD || material == Material.REDSTONE) {
            // 尝试解析国家名称
            String nationName = extractNationName(itemName);
            if (nationName != null) {
                findAndOpenNation(player, nationName);
            }
        }
    }

    // ==================== 局部网络处理 ====================

    private void handleLocalNetworkClick(Player player, int slot, String itemName, ItemStack item) {
        Material material = item.getType();

        // 返回按钮
        if (itemName.contains("返回")) {
            networkMenu.openMainMenu(player);
            return;
        }

        // 功能按钮
        if (itemName.contains("发起联盟") || itemName.contains("查看盟国")) {
            player.sendMessage("§e请使用 /alliance invite <国家名> 发起联盟");
        } else if (itemName.contains("发动战争") || itemName.contains("查看敌国")) {
            player.sendMessage("§e请使用 /war declare <国家名> 发起战争");
        } else if (itemName.contains("关系详情") || itemName.contains("二阶")) {
            player.sendMessage("§e请通过点击盟国/敌国图标查看关系详情");
            player.sendMessage("§7提示: 绿宝石图标=盟国，红石图标=敌国");
        }

        // 关系节点点击
        if (material == Material.EMERALD || material == Material.REDSTONE ||
            material == Material.YELLOW_STAINED_GLASS_PANE || material == Material.NETHER_WART) {
            String nationName = extractNationName(itemName);
            if (nationName != null) {
                findAndOpenNation(player, nationName);
            }
        }
    }

    // ==================== 统计页面处理 ====================

    private void handleStatsClick(Player player, int slot, String itemName, ItemStack item) {
        // 返回按钮
        if (itemName.contains("返回")) {
            networkMenu.openMainMenu(player);
            return;
        }

        // 影响力排名点击
        if (itemName.contains("#")) {
            String nationName = extractNationName(itemName);
            if (nationName != null) {
                findAndOpenNation(player, nationName);
            }
        }
    }

    // ==================== 国家详情处理 ====================

    private void handleNationDetailClick(Player player, int slot, String itemName, ItemStack item) {
        Material material = item.getType();

        // 返回按钮
        if (itemName.contains("返回")) {
            // 返回到之前的菜单
            String state = playerStates.get(player.getUniqueId());
            if ("global".equals(state)) {
                int page = playerPages.getOrDefault(player.getUniqueId(), 0);
                networkMenu.openGlobalNetwork(player, page);
            } else if ("local".equals(state)) {
                networkMenu.openLocalNetwork(player);
            } else {
                networkMenu.openMainMenu(player);
            }
            return;
        }

        // 盟国/敌国列表中的国家点击
        if (material == Material.EMERALD || material == Material.REDSTONE) {
            String nationName = extractNationName(itemName);
            if (nationName != null) {
                findAndOpenNation(player, nationName);
            }
        }

        // 势力图按钮
        if (itemName.contains("势力图") || itemName.contains("查看关系网络")) {
            NationId playerNationId = nationService.nationOf(player.getUniqueId()).map(Nation::getId).orElse(null);
            if (playerNationId != null) {
                playerStates.put(player.getUniqueId(), "local");
                networkMenu.openLocalNetwork(player);
            }
        }
    }

    // ==================== 辅助方法 ====================

    private void handleBackButton(Player player, String title) {
        if (title.contains("全球关系网络")) {
            networkMenu.openMainMenu(player);
        } else if (title.contains("势力关系图")) {
            networkMenu.openMainMenu(player);
        } else if (title.contains("网络统计")) {
            networkMenu.openMainMenu(player);
        } else if (title.contains("国家详情")) {
            // 返回到来源菜单
            String state = playerStates.get(player.getUniqueId());
            if ("global".equals(state)) {
                int page = playerPages.getOrDefault(player.getUniqueId(), 0);
                networkMenu.openGlobalNetwork(player, page);
            } else {
                networkMenu.openMainMenu(player);
            }
        } else {
            networkMenu.openMainMenu(player);
        }
    }

    private void openNationSearch(Player player) {
        player.closeInventory();
        player.sendMessage("§6=== 查找国家 ===");
        player.sendMessage("§7请在聊天框输入国家名称进行搜索");
        player.sendMessage("§7输入 'cancel' 取消");
        playerStates.put(player.getUniqueId(), "searching");

        // 提示玩家输入
        player.sendMessage("§e提示: 国家名称支持模糊匹配");
    }

    private void showTextGraph(Player player) {
        NationId playerNationId = nationService.nationOf(player.getUniqueId()).map(Nation::getId).orElse(null);
        if (playerNationId == null) {
            player.sendMessage("§c你需要先加入一个国家");
            return;
        }

        String graph = networkService.generateSimpleTextGraph(playerNationId);
        for (String line : graph.split("\n")) {
            player.sendMessage(line);
        }
    }

    private void findAndOpenNation(Player player, String nationName) {
        // 搜索国家
        Collection<Nation> nations = nationService.nations();
        Nation foundNation = null;

        for (Nation nation : nations) {
            if (nation.name().equalsIgnoreCase(nationName) ||
                nation.name().toLowerCase().contains(nationName.toLowerCase())) {
                foundNation = nation;
                break;
            }
        }

        if (foundNation != null) {
            playerStates.put(player.getUniqueId(), "detail");
            networkMenu.openNationDetail(player, foundNation.id());
        } else {
            player.sendMessage("§c找不到国家: " + nationName);
        }
    }

    private String extractNationName(String itemName) {
        // 移除颜色代码和特殊符号
        String name = itemName
            .replaceAll("§.", "")  // 移除颜色代码
            .replaceAll("[\\[\\]☀★✓✗+\\-x×]", "") // 移除特殊符号
            .replaceAll("\\d+盟", "") // 移除盟国数量
            .replaceAll("\\d+敌", "") // 移除敌国数量
            .trim();

        // 检查是否是排名
        if (name.contains("#")) {
            // 格式: #1 国家名
            String[] parts = name.split("#");
            if (parts.length > 1) {
                String[] nameParts = parts[1].split(" ");
                StringBuilder sb = new StringBuilder();
                boolean first = false;
                for (String part : nameParts) {
                    if (!part.isEmpty() && !part.matches("\\d+")) {
                        if (first) sb.append(" ");
                        sb.append(part);
                        first = true;
                    }
                }
                name = sb.toString().trim();
            }
        }

        return name.isEmpty() ? null : name;
    }

    private boolean isBorderItem(ItemStack item) {
        if (item == null) return false;
        Material mat = item.getType();
        return mat == Material.BLACK_STAINED_GLASS_PANE ||
               mat == Material.BLUE_STAINED_GLASS_PANE ||
               mat == Material.ORANGE_STAINED_GLASS_PANE ||
               mat == Material.YELLOW_STAINED_GLASS_PANE ||
               mat == Material.GREEN_STAINED_GLASS_PANE ||
               mat == Material.RED_STAINED_GLASS_PANE ||
               mat == Material.PURPLE_STAINED_GLASS_PANE;
    }

    private String getItemName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "";
        var displayName = item.getItemMeta().displayName();
        if (displayName == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(displayName);
    }

    /**
     * 处理聊天输入（用于搜索国家）
     */
    public void handleChatInput(Player player, String message) {
        if (!"searching".equals(playerStates.get(player.getUniqueId()))) {
            return;
        }

        playerStates.remove(player.getUniqueId());

        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage("§7已取消搜索");
            networkMenu.openMainMenu(player);
            return;
        }

        findAndOpenNation(player, message);
    }

    /**
     * 获取网络菜单实例
     */
    public DiplomacyNetworkMenu getNetworkMenu() {
        return networkMenu;
    }

    // E-035 修复: 玩家退出时清理 Map 状态
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playerStates.remove(playerId);
        playerPages.remove(playerId);
    }
}