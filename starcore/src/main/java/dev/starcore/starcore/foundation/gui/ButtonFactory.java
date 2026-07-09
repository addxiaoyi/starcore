package dev.starcore.starcore.foundation.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * GUI 按钮工厂 - 统一创建各种类型的按钮
 * 提供一致的外观和交互体验
 */
public final class ButtonFactory {

    // 按钮样式常量
    public static final String BUTTON_STYLE_PRIMARY = "primary";
    public static final String BUTTON_STYLE_SECONDARY = "secondary";
    public static final String BUTTON_STYLE_DANGER = "danger";
    public static final String BUTTON_STYLE_SUCCESS = "success";
    public static final String BUTTON_STYLE_INFO = "info";
    public static final String BUTTON_STYLE_DISABLED = "disabled";

    // 边框材质
    private static final Material[] BORDER_MATERIALS = {
        Material.BLACK_STAINED_GLASS_PANE,
        Material.BLUE_STAINED_GLASS_PANE,
        Material.GREEN_STAINED_GLASS_PANE,
        Material.RED_STAINED_GLASS_PANE,
        Material.YELLOW_STAINED_GLASS_PANE,
        Material.PURPLE_STAINED_GLASS_PANE
    };

    /**
     * 创建主按钮
     */
    public static ItemStack createButton(String name, Material material, String... lore) {
        return createStyledButton(name, material, BUTTON_STYLE_PRIMARY, lore);
    }

    /**
     * 创建样式化按钮
     */
    public static ItemStack createStyledButton(String name, Material material, String style, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // 应用样式
            Component displayName = applyStyle(name, style);
            meta.displayName(displayName);

            // 设置描述
            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(Component.text(line, NamedTextColor.GRAY));
            }
            meta.lore(loreComponents);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 创建边框按钮（使用默认黑色染色玻璃板）
     */
    public static ItemStack createBorder() {
        return createBorder(Material.BLACK_STAINED_GLASS_PANE);
    }

    /**
     * 创建边框按钮
     */
    public static ItemStack createBorder(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" ", NamedTextColor.WHITE));
            meta.setEnchantmentGlintOverride(false);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建填充边框
     */
    public static ItemStack[] createBorders(int size) {
        Material borderMat = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack border = createBorder(borderMat);
        ItemStack[] items = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            items[i] = border;
        }
        return items;
    }

    /**
     * 创建分隔符
     */
    public static ItemStack createSeparator() {
        return createStyledButton("─".repeat(20), Material.IRON_BARS, BUTTON_STYLE_INFO);
    }

    /**
     * 创建状态按钮
     */
    public static ItemStack createStatusButton(String label, boolean enabled, String description) {
        String style = enabled ? BUTTON_STYLE_SUCCESS : BUTTON_STYLE_DISABLED;
        Material material = enabled ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        String status = enabled ? "§a[启用]" : "§c[禁用]";
        return createStyledButton(status + " " + label, material, style, description);
    }

    /**
     * 创建统计按钮（标签 + 数值）
     */
    public static ItemStack createStatButton(Material material, String label, String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(label, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§e" + value, NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建信息按钮
     */
    public static ItemStack createInfoButton(String title, String... info) {
        return createStyledButton(title, Material.BOOK, BUTTON_STYLE_INFO, info);
    }

    /**
     * 创建导航按钮（左箭头）
     */
    public static ItemStack createPrevButton(String label) {
        return createStyledButton("◀ " + label, Material.ARROW, BUTTON_STYLE_SECONDARY, "上一页");
    }

    /**
     * 创建导航按钮（右箭头）
     */
    public static ItemStack createNextButton(String label) {
        return createStyledButton(label + " ▶", Material.ARROW, BUTTON_STYLE_SECONDARY, "下一页");
    }

    /**
     * 创建返回按钮
     */
    public static ItemStack createBackButton() {
        return createStyledButton("§e↩ 返回上级", Material.BARRIER, BUTTON_STYLE_SECONDARY, "返回上一个菜单");
    }

    /**
     * 创建关闭按钮
     */
    public static ItemStack createCloseButton() {
        return createStyledButton("§c✖ 关闭", Material.BARRIER, BUTTON_STYLE_DANGER, "关闭此菜单");
    }

    /**
     * 创建确认按钮
     */
    public static ItemStack createConfirmButton(String action) {
        return createStyledButton("§a✓ 确认 " + action, Material.LIME_CONCRETE, BUTTON_STYLE_SUCCESS, "点击确认");
    }

    /**
     * 创建取消按钮
     */
    public static ItemStack createCancelButton() {
        return createStyledButton("§c✖ 取消", Material.RED_CONCRETE, BUTTON_STYLE_DANGER, "点击取消");
    }

    /**
     * 创建玩家头颅按钮（用于显示玩家信息）
     */
    public static ItemStack createPlayerHead(Player player) {
        return createPlayerHead(player.getName());
    }

    /**
     * 创建玩家头颅按钮
     */
    public static ItemStack createPlayerHead(String playerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(playerName, NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * 应用样式到文本
     */
    private static Component applyStyle(String text, String style) {
        Component component = Component.text(text);

        return switch (style) {
            case BUTTON_STYLE_PRIMARY -> component.color(NamedTextColor.GOLD);
            case BUTTON_STYLE_SECONDARY -> component.color(NamedTextColor.GRAY);
            case BUTTON_STYLE_DANGER -> component.color(NamedTextColor.RED);
            case BUTTON_STYLE_SUCCESS -> component.color(NamedTextColor.GREEN);
            case BUTTON_STYLE_INFO -> component.color(NamedTextColor.AQUA);
            case BUTTON_STYLE_DISABLED -> component.color(NamedTextColor.DARK_GRAY);
            default -> component.color(NamedTextColor.WHITE);
        };
    }

    /**
     * 创建页码按钮
     */
    public static ItemStack createPageButton(int page, int currentPage, int totalPages) {
        if (page == currentPage) {
            return createStyledButton("§6第 " + page + " 页",
                Material.NETHER_STAR, BUTTON_STYLE_PRIMARY,
                "当前页面: " + currentPage + "/" + totalPages);
        } else {
            return createStyledButton("§7第 " + page + " 页",
                Material.PAPER, BUTTON_STYLE_SECONDARY,
                "共 " + totalPages + " 页");
        }
    }

    /**
     * 创建进度条按钮
     */
    public static ItemStack createProgressButton(String label, int current, int max, Material fillMaterial, Material emptyMaterial) {
        int percentage = max > 0 ? (current * 100 / max) : 0;
        Material material = percentage >= 75 ? Material.LIME_STAINED_GLASS_PANE :
                           percentage >= 50 ? Material.YELLOW_STAINED_GLASS_PANE :
                           percentage >= 25 ? Material.ORANGE_STAINED_GLASS_PANE :
                           Material.RED_STAINED_GLASS_PANE;

        String progress = "§7[";
        int bars = 10;
        int filled = (int) (percentage / 10.0);
        for (int i = 0; i < bars; i++) {
            progress += i < filled ? "§a█" : "§7░";
        }
        progress += "§7] " + percentage + "%";

        return createStyledButton(label, material, BUTTON_STYLE_INFO,
            progress,
            "当前: " + current + " / " + max);
    }
}
