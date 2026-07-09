package dev.starcore.starcore.essentials.command;

import dev.starcore.starcore.essentials.gui.BalTopGui;
import dev.starcore.starcore.essentials.gui.HomeGui;
import dev.starcore.starcore.essentials.gui.WarpGui;
import dev.starcore.starcore.essentials.home.HomeService;
import dev.starcore.starcore.essentials.teleport.TeleportService;
import dev.starcore.starcore.essentials.warp.WarpService;
import dev.starcore.starcore.foundation.economy.EconomyService;
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
 * 综合传送菜单 GUI - 家园 + 传送点
 */
public final class TeleportGui implements InventoryHolder {
    private static final int SIZE = 36;

    private final Player player;
    private final HomeService homeService;
    private final WarpService warpService;
    private final TeleportService teleportService;

    private final Inventory inventory;

    public TeleportGui(Player player, HomeService homeService, WarpService warpService, TeleportService teleportService) {
        this.player = player;
        this.homeService = homeService;
        this.warpService = warpService;
        this.teleportService = teleportService;

        this.inventory = Bukkit.createInventory(this, SIZE,
            Component.text("传送中心", NamedTextColor.AQUA));
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

        // 主城按钮
        inventory.setItem(10, createSpawnItem());

        // 家园按钮
        inventory.setItem(13, createHomeMenuItem());

        // 传送点按钮
        inventory.setItem(16, createWarpMenuItem());

        // 返回按钮
        inventory.setItem(22, createBackItem());

        // 关闭按钮
        inventory.setItem(31, createCloseItem());
    }

    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.END_PORTAL_FRAME);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("传送中心", NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("=== 传送选项 ===", NamedTextColor.GOLD));
        lore.add(Component.text("家园: " + homeService.getHomeNames(player.getUniqueId()).size() + " 个", NamedTextColor.GRAY));
        lore.add(Component.text("传送点: " + warpService.getWarpCount() + " 个", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击对应图标打开传送选项", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSpawnItem() {
        ItemStack item = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("主城 (星核)", NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("传送到服务器主城", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击传送到主城", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createHomeMenuItem() {
        int homeCount = homeService.getHomeNames(player.getUniqueId()).size();
        Material material = homeCount > 0 ? Material.WHITE_BED : Material.BEDROCK;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("家园 (" + homeCount + ")", NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("管理你的家园", NamedTextColor.GRAY));
        lore.add(Component.text("最多可设置 5 个家园", NamedTextColor.DARK_GRAY));
        lore.add(Component.text(""));
        if (homeCount > 0) {
            lore.add(Component.text("点击打开家园列表", NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("使用 /sethome 设置第一个家园", NamedTextColor.RED));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createWarpMenuItem() {
        int warpCount = warpService.getWarpCount();
        Material material = warpCount > 0 ? Material.END_PORTAL_FRAME : Material.BARRIER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("星港 (" + warpCount + ")", NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("公共传送点列表", NamedTextColor.GRAY));
        lore.add(Component.text("管理员可设置传送点", NamedTextColor.DARK_GRAY));
        lore.add(Component.text(""));
        if (warpCount > 0) {
            lore.add(Component.text("点击打开传送点列表", NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("暂无公共传送点", NamedTextColor.RED));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("返回上一个位置", NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("返回到你上一次传送前的位置", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击返回", NamedTextColor.YELLOW));

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
     * 从槽位获取动作
     */
    public static TeleportAction getActionFromSlot(int slot) {
        return switch (slot) {
            case 10 -> TeleportAction.SPAWN;
            case 13 -> TeleportAction.HOME_MENU;
            case 16 -> TeleportAction.WARP_MENU;
            case 22 -> TeleportAction.BACK;
            case 31 -> TeleportAction.CLOSE;
            default -> TeleportAction.NONE;
        };
    }

    /**
     * 传送动作枚举
     */
    public enum TeleportAction {
        NONE,
        SPAWN,
        HOME_MENU,
        WARP_MENU,
        BACK,
        CLOSE
    }
}
