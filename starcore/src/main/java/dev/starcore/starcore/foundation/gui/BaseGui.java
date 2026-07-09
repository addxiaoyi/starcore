package dev.starcore.starcore.foundation.gui;

import dev.starcore.starcore.foundation.animation.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * StarCore GUI 基类
 * 提供统一的菜单创建、导航、动画和交互处理
 *
 * 所有自定义 GUI 应继承此类以获得：
 * - 统一的动画效果（打开/关闭/点击）
 * - 标准化的导航系统（分页、返回）
 * - 统一的视觉风格
 */
public abstract class BaseGui implements InventoryHolder {

    // ==================== 静态配置 ====================

    protected static final int SLOT_BACK = 45;
    protected static final int SLOT_INFO = 49;
    protected static final int SLOT_PREV = 48;
    protected static final int SLOT_NEXT = 50;
    protected static final int SLOT_CLOSE = 53;

    // 默认填充物品
    protected static final Material FILL_GLASS = Material.BLACK_STAINED_GLASS_PANE;
    protected static final Material NAV_GLASS = Material.GRAY_STAINED_GLASS_PANE;
    protected static final Material SELECTED_GLASS = Material.CYAN_STAINED_GLASS_PANE;

    // 颜色配置
    protected static final TextColor COLOR_TITLE = NamedTextColor.GOLD;
    protected static final TextColor COLOR_ITEM = NamedTextColor.WHITE;
    protected static final TextColor COLOR_HIGHLIGHT = NamedTextColor.YELLOW;
    protected static final TextColor COLOR_DISABLED = NamedTextColor.GRAY;

    // ==================== 实例字段 ====================

    protected final Player player;
    protected final Plugin plugin;
    protected GuiAnimationRegistry animations;

    protected Inventory inventory;
    protected String menuId;
    protected Component title;
    protected int page = 0;
    protected int totalPages = 1;

    // 导航历史
    protected final Deque<BaseGui> history = new ArrayDeque<>();

    // 事件处理
    protected final Map<Integer, Consumer<Player>> clickHandlers = new ConcurrentHashMap<>();
    protected final Map<Integer, Predicate<Player>> conditionHandlers = new ConcurrentHashMap<>();

    // 当前菜单的物品缓存（用于动画效果）
    protected Map<Integer, ItemStack> previousItems = new ConcurrentHashMap<>();
    protected long lastUpdateTime = 0;

    public BaseGui(Player player, Plugin plugin, String menuId, Component title) {
        this.player = player;
        this.plugin = plugin;
        this.menuId = menuId;
        this.title = title;

        // 获取动画系统
        try {
            this.animations = GuiAnimationRegistry.getInstance();
        } catch (IllegalStateException e) {
            // 如果动画系统未初始化，使用 null
            this.animations = null;
        }
    }

    // ==================== 抽象方法 ====================

    /**
     * 创建菜单内容
     * 子类实现此方法填充菜单内容
     */
    protected abstract void buildContent();

    /**
     * 获取菜单大小（必须是9的倍数，最大54）
     */
    protected abstract int getSize();

    // ==================== 生命周期 ====================

    /**
     * 打开菜单
     */
    public void open() {
        open(0);
    }

    /**
     * 打开菜单（指定页码）
     */
    public void open(int page) {
        this.page = page;
        build();

        // 播放打开动画
        if (animations != null) {
            animations.playMenuOpen(player, menuId);
        }

        // 注册到监听器
        BaseGuiListener.registerGui(player, this);

        player.openInventory(inventory);
    }

    /**
     * 构建菜单
     */
    protected void build() {
        // 计算大小
        int size = getSize();
        if (size % 9 != 0) {
            size = ((size / 9) + 1) * 9;
        }
        size = Math.max(9, Math.min(54, size));

        // 创建库存
        String titleStr = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(title);
        inventory = Bukkit.createInventory(this, size, titleStr);

        // 清空处理器
        clickHandlers.clear();
        conditionHandlers.clear();

        // 填充背景
        fillBackground();

        // 保存当前物品状态（用于动画检测变化）
        previousItems.clear();
        for (int i = 0; i < size; i++) {
            if (inventory.getItem(i) != null) {
                previousItems.put(i, inventory.getItem(i).clone());
            }
        }

        // 构建内容
        buildContent();

        // 添加导航栏
        buildNavigation();

        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * 刷新菜单（不关闭）
     */
    public void refresh() {
        build();
        player.openInventory(inventory);
    }

    /**
     * 关闭菜单
     */
    public void close() {
        if (animations != null) {
            animations.playMenuClose(player);
        }
        player.closeInventory();
        // audit C-058: 关闭时清理点击处理缓存，避免泄漏
        clickHandlers.clear();
        conditionHandlers.clear();
        previousItems.clear();
    }

    /**
     * 返回上一个菜单
     */
    protected void goBack() {
        if (!history.isEmpty()) {
            BaseGui previous = history.pop();
            previous.open(previous.page);
        } else {
            close();
        }
    }

    /**
     * 记录导航历史
     */
    protected void pushToHistory(BaseGui gui) {
        gui.history.addAll(this.history);
        gui.history.push(this);
    }

    // ==================== 内容构建工具 ====================

    /**
     * 填充背景
     */
    protected void fillBackground() {
        ItemStack glass = createItem(FILL_GLASS, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, glass);
        }
    }

    /**
     * 添加按钮
     */
    protected void setButton(int slot, Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        setButton(slot, item, name, List.of(lore));
    }

    /**
     * 添加按钮（Component版本）
     */
    protected void setButton(int slot, Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        setButton(slot, item, name, lore);
    }

    /**
     * 添加按钮（Component varargs版本）
     */
    protected void setButton(int slot, Material material, Component name, Component... lore) {
        setButton(slot, material, name, Arrays.asList(lore));
    }

    /**
     * 添加按钮
     */
    protected void setButton(int slot, ItemStack item, String name, List<String> lore) {
        setButton(slot, item, Component.text(name), lore.stream().map(Component::text).collect(Collectors.toList()));
    }

    /**
     * 添加按钮（Component版本）
     */
    protected void setButton(int slot, ItemStack item, Component name, List<Component> lore) {
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            item.setItemMeta(meta);
        }

        // audit C-056: setItem 越界守卫
        if (inventory == null || slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
    }

    /**
     * 添加可点击按钮
     */
    protected void setClickableButton(int slot, Material material, String name,
                                      List<String> lore, Consumer<Player> onClick) {
        ItemStack item = new ItemStack(material);
        setButton(slot, item, name, lore);
        clickHandlers.put(slot, onClick);
    }

    /**
     * 添加可点击按钮（Component版本）
     */
    protected void setClickableButton(int slot, Material material, Component name,
                                      List<Component> lore, Consumer<Player> onClick) {
        ItemStack item = new ItemStack(material);
        setButton(slot, item, name, lore);
        clickHandlers.put(slot, onClick);
    }

    /**
     * 添加可点击按钮（带条件）
     */
    protected void setClickableButton(int slot, Material material, String name,
                                      List<String> lore, Consumer<Player> onClick,
                                      Predicate<Player> condition) {
        ItemStack item = new ItemStack(material);
        setButton(slot, item, name, lore);
        clickHandlers.put(slot, onClick);
        conditionHandlers.put(slot, condition);

        // 如果条件不满足，灰色化
        if (condition != null && !condition.test(player)) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(COLOR_DISABLED + name));
                meta.lore(lore.stream()
                    .map(l -> Component.text(COLOR_DISABLED + l))
                    .collect(Collectors.toList()));
                item.setItemMeta(meta);
            }
            // audit C-056: 越界守卫
            if (inventory != null && slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item);
            }
        }
    }

    /**
     * 添加可点击按钮（带条件，Component版本）
     */
    protected void setClickableButton(int slot, Material material, Component name,
                                      List<Component> lore, Consumer<Player> onClick,
                                      Predicate<Player> condition) {
        ItemStack item = new ItemStack(material);
        setButton(slot, item, name, lore);
        clickHandlers.put(slot, onClick);
        conditionHandlers.put(slot, condition);

        // 如果条件不满足，灰色化
        if (condition != null && !condition.test(player)) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(name.color(NamedTextColor.GRAY));
                meta.lore(lore.stream()
                    .map(l -> l.color(NamedTextColor.GRAY))
                    .collect(Collectors.toList()));
                item.setItemMeta(meta);
            }
            // audit C-056: 越界守卫
            if (inventory != null && slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item);
            }
        }
    }

    /**
     * 添加空槽（背景）
     */
    protected void setEmpty(int slot) {
        setButton(slot, FILL_GLASS, " ");
    }

    /**
     * 创建物品
     */
    protected ItemStack createItem(Material material, String name, String... lore) {
        return createItem(material, Component.text(name),
            Arrays.stream(lore).map(Component::text).collect(Collectors.toList()));
    }

    /**
     * 创建物品（Component版本）
     */
    protected ItemStack createItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建带颜色的玻璃板
     */
    protected ItemStack createGlass(Material glassMaterial, String name) {
        return createItem(glassMaterial, name);
    }

    // ==================== 导航构建 ====================

    /**
     * 构建导航栏
     */
    protected void buildNavigation() {
        int size = inventory.getSize();

        // 返回按钮（第45槽）
        if (size > 45) {
            setClickableButton(SLOT_BACK, Material.BARRIER,
                Component.text("返回"),
                List.of(Component.text("返回上一个菜单")),
                p -> { goBack(); });
        }

        // 信息按钮（第49槽）
        if (size > 49) {
            setButton(SLOT_INFO, Material.BOOK,
                Component.text("菜单信息"),
                List.of(
                    Component.text("当前页: " + (page + 1) + "/" + totalPages),
                    Component.text("菜单ID: " + menuId)
                ));
        }

        // 上一页按钮
        if (size > 48 && page > 0) {
            setClickableButton(SLOT_PREV, Material.ARROW,
                Component.text("上一页"),
                List.of(Component.text("第 " + page + " 页")),
                p -> {
                    page--;
                    refresh();
                });
        }

        // 下一页按钮
        if (size > 50 && page < totalPages - 1) {
            setClickableButton(SLOT_NEXT, Material.ARROW,
                Component.text("下一页"),
                List.of(Component.text("第 " + (page + 2) + " 页")),
                p -> {
                    page++;
                    refresh();
                });
        }

        // 关闭按钮
        if (size == 54) {
            setClickableButton(SLOT_CLOSE, Material.BEDROCK,
                Component.text("关闭菜单"),
                List.of(Component.text("点击关闭")),
                p -> close());
        }
    }

    // ==================== 分页工具 ====================

    /**
     * 计算总页数
     */
    protected int calculatePages(int totalItems, int itemsPerPage) {
        return (int) Math.ceil((double) totalItems / itemsPerPage);
    }

    /**
     * 获取当前页的项目列表
     */
    protected <T> List<T> getPageItems(List<T> allItems, int itemsPerPage) {
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, allItems.size());

        if (start >= allItems.size()) {
            return Collections.emptyList();
        }

        return allItems.subList(start, end);
    }

    /**
     * 设置分页
     */
    protected void setTotalPages(int totalPages) {
        this.totalPages = Math.max(1, totalPages);
        this.page = Math.min(page, totalPages - 1);
    }

    // ==================== 事件处理 ====================

    /**
     * 处理点击事件
     * 由 GuiListener 调用
     */
    public boolean handleClick(Player clicker, int slot) {
        if (!clicker.equals(player)) {
            return false;
        }

        // 检查条件
        Predicate<Player> condition = conditionHandlers.get(slot);
        if (condition != null && !condition.test(clicker)) {
            // 播放失败音效
            if (animations != null) {
                animations.playFailureSound(clicker);
            }
            return true;
        }

        // 执行处理器
        Consumer<Player> handler = clickHandlers.get(slot);
        if (handler != null) {
            // 播放点击音效
            if (animations != null) {
                animations.playSelectSound(clicker);
            }

            handler.accept(clicker);
            return true;
        }

        return false;
    }

    /**
     * 处理关闭事件
     */
    public void handleClose() {
        if (animations != null) {
            animations.playMenuClose(player);
        }
    }

    /**
     * 检查是否是此GUI的库存
     */
    public boolean isOwnInventory(Inventory inventory) {
        return this.inventory != null && this.inventory.equals(inventory);
    }

    // ==================== 动画效果 ====================

    /**
     * 播放成功反馈
     */
    protected void playSuccess(String message) {
        if (animations != null) {
            animations.playSuccess(player, message);
        } else {
            player.sendMessage(Component.text("§a✓ " + message));
        }
    }

    /**
     * 播放失败反馈
     */
    protected void playFailure(String message) {
        if (animations != null) {
            animations.playFailure(player, message);
        } else {
            player.sendMessage(Component.text("§c✗ " + message));
        }
    }

    /**
     * 播放稀有物品特效
     */
    protected void playRareEffect(String itemName) {
        if (animations != null) {
            animations.playRareItem(player, itemName);
        }
    }

    /**
     * 播放粒子效果
     */
    protected void playParticle(ParticleEffectManager.ParticlePreset preset) {
        if (animations != null) {
            animations.playParticle(player, preset);
        }
    }

    /**
     * 播放过渡动画并切换菜单
     */
    protected void playTransition(BaseGui targetGui) {
        if (animations != null && inventory != null) {
            pushToHistory(targetGui);
            targetGui.open();
        } else {
            targetGui.open();
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 将 Component 转换为纯文本字符串
     */
    private String convertComponentToString(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(component);
    }

    /**
     * 发送消息
     */
    protected void sendMessage(String message) {
        player.sendMessage(Component.text(message));
    }

    /**
     * 发送彩色消息
     */
    protected void sendColoredMessage(String message) {
        player.sendMessage(Component.text(message.replace("&", "§")));
    }

    /**
     * 获取玩家名称
     */
    protected String getPlayerName() {
        return player.getName();
    }

    /**
     * 检查玩家是否有权限
     */
    protected boolean hasPermission(String permission) {
        return player.hasPermission(permission);
    }

    /**
     * 格式化数字（带千位分隔符）
     */
    protected String formatNumber(long number) {
        return String.format("%,d", number);
    }

    /**
     * 格式化数字（double）
     */
    protected String formatNumber(double number) {
        return String.format("%,.2f", number);
    }

    /**
     * 创建进度条
     */
    protected String createProgressBar(double current, double max, int length) {
        int filled = (int) ((current / max) * length);
        int empty = length - filled;

        StringBuilder bar = new StringBuilder();
        bar.append("§a");
        for (int i = 0; i < filled; i++) bar.append("█");
        bar.append("§7");
        for (int i = 0; i < empty; i++) bar.append("█");

        return bar.toString();
    }

    // ==================== InventoryHolder 实现 ====================

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
