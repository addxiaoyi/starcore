package dev.starcore.starcore.essentials.gui;

import dev.starcore.starcore.essentials.teleport.TeleportService;
import dev.starcore.starcore.essentials.warp.WarpService;
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

/**
 * 传送点选择 GUI
 */
public final class WarpGui implements InventoryHolder {
    private static final int SIZE = 27;
    private static final int[] WARP_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24};
    private static final int MAX_PER_PAGE = 13;
    // audit C-070: SIZE=27，关闭按钮槽位必须 < 27（原 49 越界）。0 为左上角，无 Warp 槽位冲突
    private static final int SLOT_CLOSE = 0;

    private final Player player;
    private final WarpService warpService;
    private final TeleportService teleportService;
    private final int page;

    private final Inventory inventory;

    public WarpGui(Player player, WarpService warpService, TeleportService teleportService, int page) {
        this.player = player;
        this.warpService = warpService;
        this.teleportService = teleportService;
        this.page = Math.max(1, page);

        this.inventory = Bukkit.createInventory(this, SIZE,
            Component.text("星港列表 (第" + page + "页)", NamedTextColor.AQUA));
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

        // 传送点列表
        List<String> warpNames = warpService.getWarpNames();
        int startIndex = (page - 1) * MAX_PER_PAGE;
        int endIndex = Math.min(startIndex + MAX_PER_PAGE, warpNames.size());

        for (int i = startIndex; i < endIndex; i++) {
            int localIndex = i - startIndex;
            if (localIndex < 0 || localIndex >= WARP_SLOTS.length) {
                continue; // 跳过无效索引，防止越界
            }
            int slot = WARP_SLOTS[localIndex];
            String warpName = warpNames.get(i);
            inventory.setItem(slot, createWarpItem(warpName, localIndex + 1));
        }

        // 导航
        int totalPages = (int) Math.ceil((double) warpNames.size() / MAX_PER_PAGE);
        if (totalPages <= 0) totalPages = 1;

        if (page > 1) {
            inventory.setItem(18, createNavItem(Material.ARROW, "上一页", page - 1, NamedTextColor.GREEN));
        }
        if (page < totalPages) {
            inventory.setItem(26, createNavItem(Material.ARROW, "下一页", page + 1, NamedTextColor.GREEN));
        }

        // 关闭按钮 (audit C-070: 修复越界，原 slot 49)
        if (SLOT_CLOSE < 0 || SLOT_CLOSE >= SIZE) return;
        inventory.setItem(SLOT_CLOSE, createCloseItem());
    }

    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.END_PORTAL_FRAME);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("星港系统", NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("=== 星港统计 ===", NamedTextColor.GOLD));
        lore.add(Component.text("可用传送点: " + warpService.getWarpCount(), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击传送点直接传送", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createWarpItem(String warpName, int number) {
        Material material = getWarpMaterial(warpName);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(warpName, NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("传送点 #" + number, NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击传送到此地点", NamedTextColor.GREEN));
        lore.add(Component.text("需要等待传送延迟", NamedTextColor.DARK_GRAY));

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
     * 根据名称获取材质
     */
    private Material getWarpMaterial(String warpName) {
        String lower = warpName.toLowerCase();
        if (lower.contains("spawn") || lower.contains("主城")) {
            return Material.EMERALD_BLOCK;
        } else if (lower.contains("nether") || lower.contains("地狱")) {
            return Material.NETHERRACK;
        } else if (lower.contains("end") || lower.contains("末地")) {
            return Material.END_STONE;
        } else if (lower.contains("farm") || lower.contains("农场")) {
            return Material.HAY_BLOCK;
        } else if (lower.contains("shop") || lower.contains("商店")) {
            return Material.GOLD_INGOT;
        } else if (lower.contains("pvp") || lower.contains("战场")) {
            return Material.DIAMOND_SWORD;
        } else if (lower.contains("mine") || lower.contains("矿")) {
            return Material.DIAMOND_PICKAXE;
        }
        return Material.END_PORTAL_FRAME;
    }

    /**
     * 从槽位获取动作
     */
    public static WarpAction getActionFromSlot(int slot) {
        return switch (slot) {
            case SLOT_CLOSE -> WarpAction.CLOSE;  // audit C-070: 0（原 49）
            case 18 -> WarpAction.PREV_PAGE;
            case 26 -> WarpAction.NEXT_PAGE;
            default -> {
                if (isWarpSlot(slot)) {
                    yield WarpAction.TELEPORT_WARP;
                }
                yield WarpAction.NONE;
            }
        };
    }

    private static boolean isWarpSlot(int slot) {
        for (int warpSlot : WARP_SLOTS) {
            if (warpSlot == slot) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取传送点名称（从槽位）
     */
    public String getWarpNameFromSlot(int slot) {
        List<String> warpNames = warpService.getWarpNames();
        int index = getWarpSlotIndex(slot);
        if (index < 0) return null;

        int globalIndex = (page - 1) * MAX_PER_PAGE + index;
        if (globalIndex >= 0 && globalIndex < warpNames.size()) {
            return warpNames.get(globalIndex);
        }
        return null;
    }

    private int getWarpSlotIndex(int slot) {
        for (int i = 0; i < WARP_SLOTS.length; i++) {
            if (WARP_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 传送点动作枚举
     */
    public enum WarpAction {
        NONE,
        TELEPORT_WARP,
        PREV_PAGE,
        NEXT_PAGE,
        CLOSE
    }
}
