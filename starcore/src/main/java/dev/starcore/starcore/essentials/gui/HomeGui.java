package dev.starcore.starcore.essentials.gui;

import dev.starcore.starcore.essentials.home.HomeService;
import dev.starcore.starcore.essentials.teleport.TeleportService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 家园管理 GUI
 */
public final class HomeGui implements InventoryHolder {
    private static final int SIZE = 27;
    private static final int[] HOME_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int MAX_PER_PAGE = 7;
    // audit C-069: SIZE=27，关闭按钮槽位必须 < 27（原 49 越界）
    private static final int SLOT_CLOSE = 24;

    private final Player player;
    private final HomeService homeService;
    private final TeleportService teleportService;
    private final int page;

    private final Inventory inventory;

    public HomeGui(Player player, HomeService homeService, TeleportService teleportService, int page) {
        this.player = player;
        this.homeService = homeService;
        this.teleportService = teleportService;
        this.page = Math.max(1, page);

        this.inventory = Bukkit.createInventory(this, SIZE,
            Component.text("家园管理 (第" + page + "页)", NamedTextColor.GOLD));
        buildMenu();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * 构建菜单
     */
    private void buildMenu() {
        // 标题
        inventory.setItem(4, createTitleItem());

        // 家园列表
        List<String> homeNames = homeService.getHomeNames(player.getUniqueId());
        int startIndex = (page - 1) * MAX_PER_PAGE;
        int endIndex = Math.min(startIndex + MAX_PER_PAGE, homeNames.size());

        for (int i = startIndex; i < endIndex; i++) {
            int localIndex = i - startIndex;
            if (localIndex < 0 || localIndex >= HOME_SLOTS.length) {
                continue; // 跳过无效索引，防止越界
            }
            int slot = HOME_SLOTS[localIndex];
            String homeName = homeNames.get(i);
            inventory.setItem(slot, createHomeItem(homeName, localIndex + 1));
        }

        // 导航
        int totalPages = (int) Math.ceil((double) homeNames.size() / MAX_PER_PAGE);
        if (totalPages <= 0) totalPages = 1;

        if (page > 1) {
            inventory.setItem(18, createNavItem(Material.ARROW, "上一页", page - 1, NamedTextColor.GREEN));
        }
        if (page < totalPages) {
            inventory.setItem(26, createNavItem(Material.ARROW, "下一页", page + 1, NamedTextColor.GREEN));
        }

        // 设置新家园
        inventory.setItem(22, createSetHomeItem());

        // 关闭按钮 (audit C-069: 修复越界，原 slot 49)
        safeSetItem(inventory, SLOT_CLOSE, createCloseItem());
    }

    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("我的家园", NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("=== 家园统计 ===", NamedTextColor.GOLD));
        lore.add(Component.text("家园数量: " + homeService.getHomeNames(player.getUniqueId()).size(),
            NamedTextColor.GRAY));
        lore.add(Component.text("最大数量: " + getMaxHomes(), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击家园传送", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createHomeItem(String homeName, int number) {
        ItemStack item = new ItemStack(Material.WHITE_BED);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("家园 #" + number + ": " + homeName, NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("点击传送到此家园", NamedTextColor.YELLOW));
        lore.add(Component.text("右键删除此家园", NamedTextColor.RED));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavItem(Material material, String name, int targetPage, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(name + " (第" + targetPage + "页)", color));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("点击前往第" + targetPage + "页", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSetHomeItem() {
        ItemStack item = new ItemStack(Material.LIME_BED);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("设置当前点为新家园", NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("使用 /sethome [名称] 设置", NamedTextColor.GRAY));
        lore.add(Component.text("当前最多可设置 " + getRemainingHomes() + " 个家园", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击查看帮助", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("关闭", NamedTextColor.RED));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("点击关闭菜单", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 越界守卫 (audit C)
     */
    private static void safeSetItem(Inventory inv, int slot, ItemStack item) {
        if (slot < 0 || slot >= inv.getSize()) return;
        inv.setItem(slot, item);
    }

    private int getMaxHomes() {
        return 5; // 默认值，应从配置获取
    }

    private int getRemainingHomes() {
        return Math.max(0, getMaxHomes() - homeService.getHomeNames(player.getUniqueId()).size());
    }

    /**
     * 从槽位获取动作
     */
    public static HomeAction getActionFromSlot(int slot) {
        return switch (slot) {
            case SLOT_CLOSE -> HomeAction.CLOSE;  // audit C-069: 24（原 49）
            case 22 -> HomeAction.SET_HOME;
            case 18 -> HomeAction.PREV_PAGE;
            case 26 -> HomeAction.NEXT_PAGE;
            default -> {
                if (slot >= 10 && slot <= 16) {
                    yield HomeAction.TELEPORT_HOME;
                }
                yield HomeAction.NONE;
            }
        };
    }

    /**
     * 获取家园名称（从槽位）
     */
    public String getHomeNameFromSlot(int slot) {
        List<String> homeNames = homeService.getHomeNames(player.getUniqueId());
        int index = slot - 10;
        int globalIndex = (page - 1) * MAX_PER_PAGE + index;

        if (globalIndex >= 0 && globalIndex < homeNames.size()) {
            return homeNames.get(globalIndex);
        }
        return null;
    }

    /**
     * 家园动作枚举
     */
    public enum HomeAction {
        NONE,
        TELEPORT_HOME,
        SET_HOME,
        DELETE_HOME,
        PREV_PAGE,
        NEXT_PAGE,
        CLOSE
    }
}
