package dev.starcore.starcore.foundation.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 通用菜单工厂 - 快速创建标准菜单
 *
 * 使用示例:
 *   MenuFactory.list("标题", items, 45, player -> "显示名称")
 *     .onClick((player, item) -> { ... })
 *     .open(player);
 */
public class MenuFactory {

    private String title;
    private int size;
    private List<ItemStack> items;
    private Map<Integer, Consumer<Player>> clickHandlers;
    private Function<ItemStack, String> itemNameMapper;
    private Function<ItemStack, List<String>> itemLoreMapper;
    private Runnable onOpen;
    private Runnable onClose;

    private MenuFactory(String title, int size) {
        this.title = title;
        this.size = size;
        this.items = new ArrayList<>();
        this.clickHandlers = new HashMap<>();
    }

    /**
     * 创建列表菜单
     */
    public static MenuFactory list(String title, List<ItemStack> items, int size) {
        MenuFactory factory = new MenuFactory(title, size);
        factory.items = new ArrayList<>(items);
        return factory;
    }

    /**
     * 创建网格菜单
     */
    public static MenuFactory grid(String title, int rows) {
        return new MenuFactory(title, rows * 9);
    }

    /**
     * 创建标准大小菜单 (45格 = 5行)
     */
    public static MenuFactory standard(String title) {
        return new MenuFactory(title, 45);
    }

    /**
     * 设置标题
     */
    public MenuFactory title(String title) {
        this.title = title;
        return this;
    }

    /**
     * 设置物品列表
     */
    public MenuFactory items(List<ItemStack> items) {
        this.items = new ArrayList<>(items);
        return this;
    }

    /**
     * 添加物品
     */
    public MenuFactory add(ItemStack item) {
        this.items.add(item);
        return this;
    }

    /**
     * 设置物品名称映射器
     */
    public MenuFactory withName(Function<ItemStack, String> nameMapper) {
        this.itemNameMapper = nameMapper;
        return this;
    }

    /**
     * 设置物品描述映射器
     */
    public MenuFactory withLore(Function<ItemStack, List<String>> loreMapper) {
        this.itemLoreMapper = loreMapper;
        return this;
    }

    /**
     * 点击处理器
     */
    public MenuFactory onClick(Consumer<Player> handler) {
        return onClick(0, handler);
    }

    /**
     * 指定槽位点击处理器
     */
    public MenuFactory onClick(int slot, Consumer<Player> handler) {
        this.clickHandlers.put(slot, handler);
        return this;
    }

    /**
     * 所有非空槽位点击处理器
     */
    public MenuFactory onItemClick(Consumer<Player> handler) {
        this.clickHandlers.put(-1, handler);
        return this;
    }

    /**
     * 打开时执行
     */
    public MenuFactory onOpen(Runnable runnable) {
        this.onOpen = runnable;
        return this;
    }

    /**
     * 关闭时执行
     */
    public MenuFactory onClose(Runnable runnable) {
        this.onClose = runnable;
        return this;
    }

    /**
     * 构建并打开菜单
     */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, size, Component.text(title));
        fillInventory(inv, player);
        player.openInventory(inv);

        if (onOpen != null) {
            onOpen.run();
        }
    }

    /**
     * 填充物品到菜单
     */
    private void fillInventory(Inventory inv, Player player) {
        // 填充边框
        fillBorder(inv);

        // 填充物品
        int[] slots = UnifiedMenu.getCenteredSlots(items.size(), size);
        for (int i = 0; i < items.size() && i < slots.length; i++) {
            ItemStack item = items.get(i);

            // 应用映射器
            if (itemNameMapper != null || itemLoreMapper != null) {
                item = item.clone();
                if (item.hasItemMeta()) {
                    var meta = item.getItemMeta();
                    if (meta != null) {
                        if (itemNameMapper != null) {
                            String name = itemNameMapper.apply(item);
                            if (name != null) {
                                meta.displayName(Component.text(name, NamedTextColor.WHITE));
                            }
                        }
                        if (itemLoreMapper != null) {
                            List<String> lore = itemLoreMapper.apply(item);
                            if (lore != null && !lore.isEmpty()) {
                                List<Component> loreComponents = new ArrayList<>();
                                for (String line : lore) {
                                    loreComponents.add(Component.text(line, NamedTextColor.GRAY));
                                }
                                meta.lore(loreComponents);
                            }
                        }
                        item.setItemMeta(meta);
                    }
                }
            }

            inv.setItem(slots[i], item);
        }
    }

    /**
     * 填充边框
     */
    private void fillBorder(Inventory inv) {
        ItemStack border = ButtonFactory.createBorder();
        int rows = size / 9;

        for (int row = 0; row < rows; row++) {
            // 左边框
            inv.setItem(row * 9, border);
            // 右边框
            inv.setItem(row * 9 + 8, border);
        }

        // 顶部边框
        for (int col = 1; col < 8; col++) {
            inv.setItem(col, border);
        }

        // 底部边框
        for (int col = 1; col < 8; col++) {
            inv.setItem(size - 9 + col, border);
        }
    }

    // ==================== 静态便捷方法 ====================

    /**
     * 快速创建确认对话框
     */
    public static void confirm(Player player, String title, String message,
                              Runnable onConfirm, Runnable onCancel) {
        Inventory inv = Bukkit.createInventory(null, 9,
            Component.text(title, NamedTextColor.DARK_RED));

        // 填充边框
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, ButtonFactory.createBorder(Material.RED_STAINED_GLASS_PANE));
        }

        // 消息
        inv.setItem(4, ButtonFactory.createInfoButton(message, "点击下方按钮进行选择"));

        // 按钮
        inv.setItem(2, ButtonFactory.createConfirmButton(""));
        inv.setItem(6, ButtonFactory.createCancelButton());

        player.openInventory(inv);
    }

    /**
     * 快速创建信息面板
     */
    public static void info(Player player, String title, String... lines) {
        int size = Math.min(54, 9 + (lines.length / 7 + 1) * 9);
        Inventory inv = Bukkit.createInventory(null, size,
            Component.text(title, NamedTextColor.GOLD));

        // 填充边框
        for (int i = 0; i < size; i++) {
            if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, ButtonFactory.createBorder());
            }
        }

        // 填充信息
        int slot = 10;
        for (String line : lines) {
            if (slot % 9 == 8) {
                slot += 2; // 跳过右边框
            }
            if (slot < size - 9) {
                inv.setItem(slot, ButtonFactory.createInfoButton(line));
            }
            slot++;
        }

        player.openInventory(inv);
    }

    /**
     * 快速创建加载动画菜单
     */
    public static Inventory createLoadingMenu(String title) {
        return Bukkit.createInventory(null, 27,
            Component.text(title + " §e加载中...", NamedTextColor.YELLOW));
    }

    /**
     * 创建分割线菜单
     */
    public static Inventory createSeparator(String title) {
        Inventory inv = Bukkit.createInventory(null, 36,
            Component.text(title, NamedTextColor.GOLD));

        ItemStack sep = ButtonFactory.createSeparator();
        for (int i = 0; i < 36; i++) {
            if (i >= 9 && i < 27 && i % 9 >= 1 && i % 9 <= 7) {
                inv.setItem(i, sep);
            }
        }

        return inv;
    }

    /**
     * 创建选择菜单
     */
    public static Inventory createChoiceMenu(String title, String question,
                                           ItemStack option1, ItemStack option2) {
        Inventory inv = Bukkit.createInventory(null, 27,
            Component.text(title, NamedTextColor.DARK_PURPLE));

        // 填充边框
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, ButtonFactory.createBorder(Material.PURPLE_STAINED_GLASS_PANE));
        }

        // 问题
        inv.setItem(13, ButtonFactory.createInfoButton(question));

        // 选项
        inv.setItem(11, option1);
        inv.setItem(15, option2);

        return inv;
    }
}
