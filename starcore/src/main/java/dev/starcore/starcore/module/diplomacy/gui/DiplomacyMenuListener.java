package dev.starcore.starcore.module.diplomacy.gui;

import dev.starcore.starcore.foundation.gui.ButtonFactory;
import dev.starcore.starcore.util.ColorCodes;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外交中心 GUI 菜单监听器
 * 处理玩家与外交菜单的交互
 */
public class DiplomacyMenuListener implements Listener {

    private final DiplomacyMenu diplomacyMenu;
    private final DiplomacyNetworkMenu networkMenu;
    private final Map<UUID, String> openMenus = new ConcurrentHashMap<>();

    public DiplomacyMenuListener(DiplomacyMenu diplomacyMenu) {
        this.diplomacyMenu = diplomacyMenu;
        this.networkMenu = diplomacyMenu.getNetworkMenu();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        if (inv == null) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        // 检查是否是外交相关菜单
        boolean isDiplomacyMenu = title.contains("外交中心") ||
                                  title.contains("外交关系") ||
                                  title.contains("联盟管理") ||
                                  title.contains("待处理邀请") ||
                                  title.contains("联盟列表") ||
                                  title.contains("选择邀请国家") ||
                                  title.contains("外交关系网络") ||
                                  title.contains("全球关系网络") ||
                                  title.contains("势力关系图") ||
                                  title.contains("网络统计") ||
                                  title.contains("国家详情");

        if (!isDiplomacyMenu) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // 播放点击音效
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

        // 处理边框点击
        if (clickedItem.getType() == Material.ORANGE_STAINED_GLASS_PANE ||
            clickedItem.getType() == Material.YELLOW_STAINED_GLASS_PANE ||
            clickedItem.getType() == Material.BLACK_STAINED_GLASS_PANE ||
            clickedItem.getType() == Material.BLUE_STAINED_GLASS_PANE ||
            clickedItem.getType() == Material.GREEN_STAINED_GLASS_PANE) {
            return;
        }

        int slot = event.getSlot();
        String itemName = getItemName(clickedItem);

        // 处理返回按钮
        if (itemName.contains("返回")) {
            if (title.contains("外交关系")) {
                // 从关系总览返回外交中心
                diplomacyMenu.open(player);
            }
            return;
        }

        // 处理外交中心主菜单
        if (title.contains("外交中心")) {
            handleMainMenuClick(player, slot, clickedItem, itemName);
            return;
        }

        // 处理外交关系总览菜单（特殊点击处理）
        if (title.contains("外交关系")) {
            handleRelationsMenuClick(player, clickedItem, itemName);
            return;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openMenus.remove(player.getUniqueId());
        }
    }

    // audit H-005: 修复 PlayerQuitEvent 未清理 openMenus Map 导致的内存泄漏
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    // ==================== 菜单点击处理 ====================

    private void handleMainMenuClick(Player player, int slot, ItemStack item, String itemName) {
        Material material = item.getType();

        // 槽位对应的功能
        switch (slot) {
            case 10 -> {
                // 联盟管理 - 打开联盟管理菜单
                if (diplomacyMenu.getAllianceMenu() != null) {
                    diplomacyMenu.getAllianceMenu().openMainMenu(player);
                } else {
                    player.sendMessage("§e联盟管理功能暂不可用，请使用命令 /alliance");
                }
            }
            case 12 -> {
                // 军事联盟 - 打开军事联盟菜单
                if (diplomacyMenu.getMilitaryMenu() != null) {
                    diplomacyMenu.getMilitaryMenu().openMainMenu(player);
                } else {
                    player.sendMessage("§e军事联盟功能暂不可用，请使用命令 /ma");
                }
            }
            case 14 -> {
                // 战争管理 - 打开战争菜单
                player.sendMessage("§e请使用 /war 命令打开战争菜单");
            }
            case 16 -> {
                // 外交关系总览
                diplomacyMenu.openRelationsView(player);
            }
            case 28 -> {
                // 发起联盟
                if (diplomacyMenu.getAllianceMenu() != null) {
                    diplomacyMenu.getAllianceMenu().openSendInviteMenu(player, 0);
                } else {
                    player.sendMessage("§e请使用 /alliance invite <国家名> 发起联盟");
                }
            }
            case 30 -> {
                // 发起战争
                player.sendMessage("§e请使用 /war declare <国家名> 发起战争");
            }
            case 31 -> {
                // 帮助信息按钮 - 重新打开菜单
                diplomacyMenu.open(player);
            }
            case 35 -> {
                // 国家信息 - 显示详情
                player.sendMessage("§6=== 国家信息 ===");
                player.sendMessage("§e这是你的国家信息面板");
            }
        }
    }

    private void handleRelationsMenuClick(Player player, ItemStack item, String itemName) {
        // 处理关系总览中的点击
        // 目前只是显示信息，后续可以扩展为查看关系详情
    }

    // ==================== 辅助方法 ====================

    private String getItemName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "";
        var displayName = item.getItemMeta().displayName();
        if (displayName == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(displayName);
    }

    /**
     * 获取外交菜单实例
     */
    public DiplomacyMenu getDiplomacyMenu() {
        return diplomacyMenu;
    }
}