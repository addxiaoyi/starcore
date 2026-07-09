package dev.starcore.starcore.foundation.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一 GUI 菜单基类
 * 提供标准化的菜单行为和性能优化
 *
 * audit C-051: 注意此类持有 static 共享映射 (openMenus / playerPageMap / playerDataMap)，
 *   调用方必须在 InventoryCloseEvent / PlayerQuitEvent 中调用 clearPlayerData(player)
 *   以避免玩家数据泄漏。后续重构建议改为实例字段 + per-player 会话对象。
 */
public abstract class UnifiedMenu {

    // 菜单标题
    protected final String title;
    protected final int size;

    // 玩家当前打开的菜单
    protected static final Map<UUID, UnifiedMenu> openMenus = new ConcurrentHashMap<>();

    // 玩家当前位置（用于分页）
    protected static final Map<UUID, Integer> playerPageMap = new ConcurrentHashMap<>();
    protected static final Map<UUID, Integer> playerDataMap = new ConcurrentHashMap<>();

    protected UnifiedMenu(String title, int size) {
        this.title = title;
        this.size = size;
    }

    /**
     * 获取菜单标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 获取菜单大小
     */
    public int getSize() {
        return size;
    }

    /**
     * 打开菜单给玩家
     */
    public void open(Player player) {
        // audit C-052: 防御空玩家
        if (player == null) return;
        Inventory inventory = Bukkit.createInventory(null, size, Component.text(title));
        fillMenu(player, inventory);
        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), this);
    }

    /**
     * 填充菜单内容 - 子类实现
     */
    protected abstract void fillMenu(Player player, Inventory inventory);

    /**
     * 处理点击事件 - 子类实现
     */
    public abstract void handleClick(Player player, int slot, ItemStack item);

    /**
     * 刷新菜单
     */
    public void refresh(Player player) {
        if (openMenus.get(player.getUniqueId()) == this) {
            open(player);
        }
    }

    /**
     * 关闭菜单
     */
    public static void closeMenu(Player player) {
        openMenus.remove(player.getUniqueId());
        player.closeInventory();
    }

    /**
     * 检查玩家当前是否在此菜单中
     */
    public static boolean isInMenu(Player player, Class<?> menuClass) {
        UnifiedMenu menu = openMenus.get(player.getUniqueId());
        return menu != null && menuClass.isInstance(menu);
    }

    // ==================== 静态工具方法 ====================

    /**
     * 设置玩家页码
     */
    public static void setPlayerPage(Player player, int page) {
        playerPageMap.put(player.getUniqueId(), page);
    }

    /**
     * 获取玩家页码
     */
    public static int getPlayerPage(Player player) {
        return playerPageMap.getOrDefault(player.getUniqueId(), 1);
    }

    /**
     * 设置玩家数据
     */
    public static void setPlayerData(Player player, int data) {
        playerDataMap.put(player.getUniqueId(), data);
    }

    /**
     * 获取玩家数据
     */
    public static int getPlayerData(Player player) {
        return playerDataMap.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * 清理玩家数据
     */
    public static void clearPlayerData(Player player) {
        playerPageMap.remove(player.getUniqueId());
        playerDataMap.remove(player.getUniqueId());
        openMenus.remove(player.getUniqueId());
    }

    /**
     * 创建分页导航按钮位置
     */
    protected static int[] getPageNavigationSlots(int size) {
        // 返回上一页、页码、下一页的位置
        return new int[] { size - 9, size - 5, size - 1 };
    }

    /**
     * 填充边框
     */
    protected void fillBorder(Inventory inventory) {
        ItemStack border = ButtonFactory.createBorder();
        int[] borderSlots = getBorderSlots(size);
        for (int slot : borderSlots) {
            inventory.setItem(slot, border);
        }
    }

    /**
     * 获取边框槽位
     */
    public static int[] getBorderSlots(int size) {
        int rows = size / 9;
        int[] slots = new int[rows * 2 * 7 + 14]; // 顶部和底部边框

        int index = 0;
        // 顶部边框 (第一行，除角落)
        for (int i = 1; i < 8; i++) {
            slots[index++] = i;
        }
        // 底部边框 (最后一行，除角落)
        for (int i = size - 8; i < size - 1; i++) {
            slots[index++] = i;
        }

        return slots;
    }

    /**
     * 居中填充内容
     */
    protected static int[] getCenteredSlots(int contentSize, int menuSize) {
        int rows = menuSize / 9;
        int contentPerRow = 7; // 边框占2列
        int rowsNeeded = (int) Math.ceil((double) contentSize / contentPerRow);

        int startRow = Math.max(1, (rows - rowsNeeded) / 2);
        int startSlot = startRow * 9 + 1;

        int[] slots = new int[Math.min(contentSize, rowsNeeded * contentPerRow)];
        int index = 0;
        for (int r = 0; r < rowsNeeded && index < slots.length; r++) {
            for (int c = 0; c < contentPerRow && index < slots.length; c++) {
                slots[index++] = startSlot + r * 9 + c;
            }
        }

        return slots;
    }

    /**
     * 设置居中的按钮数组
     */
    protected static void setCenteredItems(Inventory inventory, ItemStack[] items) {
        int[] slots = getCenteredSlots(items.length, inventory.getSize());
        for (int i = 0; i < items.length && i < slots.length; i++) {
            if (items[i] != null) {
                inventory.setItem(slots[i], items[i]);
            }
        }
    }

    /**
     * 检查点击的槽位是否是边框
     */
    protected static boolean isBorderSlot(int slot, int size) {
        int row = slot / 9;
        int col = slot % 9;
        return row == 0 || row == size / 9 - 1 || col == 0 || col == 8;
    }

    /**
     * 创建确认对话框
     * audit C-053/C-054: 通过自定义 InventoryHolder (ConfirmHolder) 将回调绑定到
     *   Inventory 实例本身，避免使用 static map 存储 Runnable 引发泄漏与竞态。
     *   监听器从 InventoryClickEvent / InventoryCloseEvent 取出 holder 后调用相应回调。
     */
    public static void openConfirmDialog(Player player, String title, String message,
                                        Runnable onConfirm, Runnable onCancel) {
        if (player == null) return;
        ConfirmHolder holder = new ConfirmHolder(
            onConfirm != null ? onConfirm : () -> {},
            onCancel != null ? onCancel : () -> {});
        Component titleComponent = Component.text(title, NamedTextColor.DARK_RED);
        Inventory inv = Bukkit.createInventory(holder, 9, titleComponent);
        holder.bindInventory(inv);

        // 填充边框
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, ButtonFactory.createBorder(Material.RED_STAINED_GLASS_PANE));
        }

        // 消息区域（中间）
        inv.setItem(4, ButtonFactory.createInfoButton(message, "点击下方按钮进行选择"));

        // 确认/取消按钮
        inv.setItem(2, ButtonFactory.createConfirmButton(""));
        inv.setItem(6, ButtonFactory.createCancelButton());

        player.openInventory(inv);
    }

    /**
     * 处理确认对话框的点击事件 —— 由监听器在识别到 ConfirmHolder 时转发到此。
     * audit C-053/C-054: 静态 map 替换为 holder-bound 回调。
     */
    public static void handleConfirmClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ConfirmHolder holder)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 2) {
            holder.onConfirm.run();
            event.getWhoClicked().closeInventory();
        } else if (slot == 6) {
            holder.onCancel.run();
            event.getWhoClicked().closeInventory();
        }
    }

    /**
     * 处理确认对话框的关闭事件 —— 视为取消。
     */
    public static void handleConfirmClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ConfirmHolder holder) {
            holder.onCancel.run();
        }
    }

    /**
     * 绑定确认/取消回调的 InventoryHolder。回调存放于 inventory 实例本身，
     * 关闭时由 GC 回收，避免 static map 引用存活导致的 Runnable 泄漏。
     */
    public static final class ConfirmHolder implements InventoryHolder {
        private final Runnable onConfirm;
        private final Runnable onCancel;
        private Inventory inventory;

        ConfirmHolder(Runnable onConfirm, Runnable onCancel) {
            this.onConfirm = onConfirm;
            this.onCancel = onCancel;
        }

        void bindInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
