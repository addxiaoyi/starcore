package dev.starcore.starcore.foundation.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 现代毛玻璃风格 HUD 菜单
 *
 * 特性:
 * - 多层渐变玻璃板模拟毛玻璃效果
 * - 动态背景粒子效果
 * - 呼吸灯动画效果
 * - 流畅的视觉过渡
 * - 多种预设主题风格
 */
public class ModernHudMenu implements InventoryHolder {

    protected final Player player;
    private final Plugin plugin;
    private final String menuId;
    private Component title;
    private int size;

    private Inventory inventory;
    private GlassPaneStyle style;
    private boolean particlesEnabled;
    private boolean breathingEnabled;

    // 点击处理器 - 使用ConcurrentHashMap保证线程安全
    private final Map<Integer, Consumer<Player>> clickHandlers = new ConcurrentHashMap<>();
    private final Map<Integer, Predicate<Player>> conditionHandlers = new ConcurrentHashMap<>();
    private final Map<Integer, ItemStack> buttonItems = new ConcurrentHashMap<>();

    // 动画状态
    private int animationTick = 0;
    private boolean isOpen = false;

    // 关闭回调
    private Runnable onCloseCallback;

    protected ModernHudMenu(Player player, Plugin plugin, String menuId, Component title, int size, GlassPaneStyle style) {
        this.player = player;
        this.plugin = plugin;
        this.menuId = menuId;
        this.title = title;
        this.size = normalizeSize(size);
        this.style = style != null ? style : GlassPaneStyle.NIGHTMARE;
        this.particlesEnabled = true;
        this.breathingEnabled = true;
    }

    // ==================== 工厂方法 ====================

    public static ModernHudMenu create(Player player, Plugin plugin, String menuId, Component title) {
        return new ModernHudMenu(player, plugin, menuId, title, 54, GlassPaneStyle.NIGHTMARE);
    }

    public static ModernHudMenu create(Player player, Plugin plugin, String menuId, Component title, int size) {
        return new ModernHudMenu(player, plugin, menuId, title, size, GlassPaneStyle.NIGHTMARE);
    }

    public static ModernHudMenu create(Player player, Plugin plugin, String menuId, Component title, GlassPaneStyle style) {
        return new ModernHudMenu(player, plugin, menuId, title, 54, style);
    }

    public static ModernHudMenu create(Player player, Plugin plugin, String menuId, Component title, int size, GlassPaneStyle style) {
        return new ModernHudMenu(player, plugin, menuId, title, size, style);
    }

    // ==================== 配置方法 ====================

    public ModernHudMenu setStyle(GlassPaneStyle style) {
        this.style = style != null ? style : GlassPaneStyle.NIGHTMARE;
        return this;
    }

    public ModernHudMenu setParticlesEnabled(boolean enabled) {
        this.particlesEnabled = enabled;
        return this;
    }

    public ModernHudMenu setBreathingEnabled(boolean enabled) {
        this.breathingEnabled = enabled;
        return this;
    }

    public ModernHudMenu setOnClose(Runnable callback) {
        this.onCloseCallback = callback;
        return this;
    }

    // ==================== 菜单操作 ====================

    public void open() {
        build();
        isOpen = true;
        player.openInventory(inventory);

        // 注册到监听器
        ModernHudListener.register(player, this);

        // 启动粒子动画
        if (particlesEnabled) {
            startParticleAnimation();
        }
    }

    public void close() {
        isOpen = false;
        player.closeInventory();

        if (onCloseCallback != null) {
            onCloseCallback.run();
        }
    }

    public void refresh() {
        if (isOpen) {
            build();
            player.openInventory(inventory);
        }
    }

    // ==================== 构建菜单 ====================

    private void build() {
        String titleStr = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(title);

        inventory = Bukkit.createInventory(this, size, titleStr);

        // 清空处理器
        clickHandlers.clear();
        conditionHandlers.clear();
        buttonItems.clear();

        // 填充毛玻璃背景
        fillFrostedGlassBackground();

        // 构建内容（子类实现）
        buildContent();

        // 添加底部导航栏
        buildNavigationBar();

        animationTick++;
    }

    /**
     * 填充毛玻璃背景 - 核心视觉效果
     * 使用多层半透明玻璃板叠加形成毛玻璃效果
     */
    private void fillFrostedGlassBackground() {
        // 计算呼吸灯透明度（0.85 - 0.95 之间变化）
        double breathingFactor = breathingEnabled ?
            0.85 + 0.1 * Math.sin(animationTick * 0.1) : 0.9;

        // 选择背景材质
        Material bgMaterial = getBreathingMaterial(style.getBackgroundMaterial(), breathingFactor);

        // 填充背景（排除边框）
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;

            // 边框区域 - 使用边框样式
            if (row == 0 || row == (size / 9 - 1) || col == 0 || col == 8) {
                if (isCorner(i)) {
                    // 角落 - 使用装饰色
                    inventory.setItem(i, createFrostedPane(style.getAccentMaterial(), " ", true));
                } else {
                    // 边框 - 使用边框样式
                    inventory.setItem(i, createFrostedPane(style.getBorderMaterial(), " ", true));
                }
            } else {
                // 内容区域 - 使用背景样式
                inventory.setItem(i, createFrostedPane(bgMaterial, " ", false));
            }
        }

        // 添加内边框装饰（第二层边框）
        addInnerBorder();
    }

    /**
     * 创建带毛玻璃效果的染色玻璃板
     */
    private ItemStack createFrostedPane(Material material, String name, boolean isBorder) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // 设置名称（空字符实现透明效果）
            meta.displayName(Component.text(" ")
                .color(isBorder ? style.getTitleColor() : NamedTextColor.GRAY));

            // 设置附魔光泽覆盖（用于透明效果）
            meta.setEnchantmentGlintOverride(false);

            // 设置隐藏标志
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 根据呼吸灯因子获取材质
     * 通过不同的染色玻璃板模拟透明度变化
     */
    private Material getBreathingMaterial(Material base, double factor) {
        // 在呼吸周期内切换微妙的材质
        if (factor > 0.92) {
            return Material.WHITE_STAINED_GLASS_PANE;
        } else if (factor > 0.88) {
            return Material.LIGHT_GRAY_STAINED_GLASS_PANE;
        } else {
            return Material.GRAY_STAINED_GLASS_PANE;
        }
    }

    /**
     * 添加内边框装饰
     */
    private void addInnerBorder() {
        int rows = size / 9;

        // 顶部内边框（第9-17槽）
        for (int i = 9; i < 18; i++) {
            if (inventory.getItem(i) != null) {
                inventory.setItem(i, createFrostedPane(style.getAccentMaterial().equals(Material.WHITE_STAINED_GLASS_PANE)
                    ? Material.LIGHT_GRAY_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE, " ", true));
            }
        }

        // 底部内边框
        for (int i = size - 18; i < size - 9; i++) {
            if (inventory.getItem(i) != null) {
                inventory.setItem(i, createFrostedPane(Material.GRAY_STAINED_GLASS_PANE, " ", true));
            }
        }
    }

    /**
     * 检查是否是角落位置
     */
    private boolean isCorner(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        int maxRow = size / 9 - 1;

        return (row == 0 || row == maxRow) && (col == 0 || col == 8);
    }

    /**
     * 构建底部导航栏
     */
    protected void buildNavigationBar() {
        int lastRow = size - 9;

        // 返回按钮
        setButton(lastRow + 3, Material.ARROW,
            "上一页",
            "返回上一页",
            p -> {/* 子类实现或覆盖 */});

        // 关闭按钮
        setButton(lastRow + 4, Material.BARRIER,
            "关闭",
            "关闭菜单",
            p -> close());

        // 刷新按钮
        setButton(lastRow + 5, Material.REPEATER,
            "刷新",
            "刷新菜单内容",
            p -> refresh());
    }

    // ==================== 按钮方法 ====================

    /**
     * 设置按钮
     */
    public ModernHudMenu setButton(int slot, Material material, Component name, List<Component> lore, Consumer<Player> onClick) {
        return setButton(slot, material, name, lore, onClick, null);
    }

    public ModernHudMenu setButton(int slot, Material material, Component name, Component... lore) {
        return setButton(slot, material, name, Arrays.asList(lore), null, null);
    }

    public ModernHudMenu setButton(int slot, Material material, String name, String... lore) {
        return setButton(slot, material, Component.text(name).color(style.getTitleColor()),
            Arrays.stream(lore).<Component>map(l -> Component.text(l).color(NamedTextColor.GRAY)).toList(),
            null, null);
    }

    public ModernHudMenu setButton(int slot, Material material, String name, String lore, Consumer<Player> onClick) {
        return setButton(slot, material, Component.text(name).color(style.getTitleColor()),
            Collections.<Component>singletonList(Component.text(lore).color(NamedTextColor.GRAY)),
            onClick, null);
    }

    public ModernHudMenu setButton(int slot, Material material, Component name, List<Component> lore) {
        return setButton(slot, material, name, lore, null, null);
    }

    public ModernHudMenu setButton(int slot, Material material, String name, List<String> lore, Consumer<Player> onClick) {
        Component nameComponent = Component.text(name).color(style.getTitleColor());
        List<Component> loreComponents = new ArrayList<>();
        for (String l : lore) {
            loreComponents.add(Component.text(l).color(NamedTextColor.GRAY));
        }
        return setButton(slot, material, nameComponent, loreComponents, onClick, null);
    }

    public ModernHudMenu setButton(int slot, Material material, Component name, List<Component> lore, Consumer<Player> onClick, Predicate<Player> condition) {
        if (slot < 0 || slot >= size) return this;

        // 检查条件
        boolean conditionMet = condition == null || condition.test(player);

        // 创建按钮物品
        ItemStack item = new ItemStack(conditionMet ? material : Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // 设置名称
            Component displayName = conditionMet ? name : name.color(NamedTextColor.DARK_GRAY);
            meta.displayName(displayName.decoration(TextDecoration.ITALIC, false));

            // 设置描述
            List<Component> displayLore = conditionMet ? lore : lore.stream()
                .map(l -> l.color(NamedTextColor.DARK_GRAY))
                .toList();
            meta.lore(displayLore);

            // 如果条件不满足，添加禁用提示
            if (!conditionMet && condition != null) {
                meta.lore(List.of(Component.text("条件不满足").color(NamedTextColor.RED)));
            }

            item.setItemMeta(meta);
        }

        inventory.setItem(slot, item);
        buttonItems.put(slot, item);

        if (onClick != null) {
            clickHandlers.put(slot, onClick);
        }

        if (condition != null) {
            conditionHandlers.put(slot, condition);
        }

        return this;
    }

    /**
     * 设置图标按钮（使用特殊材质）
     */
    public ModernHudMenu setIconButton(int slot, Material material, Component name, List<Component> lore, Consumer<Player> onClick) {
        return setButton(slot, material, name, lore, onClick);
    }

    /**
     * 设置分隔线
     */
    public ModernHudMenu setSeparator(int slot, String text) {
        return setButton(slot, Material.IRON_BARS,
            Component.text(text.isEmpty() ? "─".repeat(15) : text).color(style.getAccent().getR() > 128 ? NamedTextColor.WHITE : NamedTextColor.GRAY),
            List.of());
    }

    /**
     * 设置玩家头颅按钮
     */
    public ModernHudMenu setPlayerHead(int slot, Player target, Component name, List<Component> lore, Consumer<Player> onClick) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();

        if (meta != null && meta instanceof org.bukkit.inventory.meta.SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(target);
            skullMeta.displayName(name.decoration(TextDecoration.ITALIC, false));
            skullMeta.lore(lore);
            head.setItemMeta(skullMeta);
        }

        inventory.setItem(slot, head);
        buttonItems.put(slot, head);

        if (onClick != null) {
            clickHandlers.put(slot, onClick);
        }

        return this;
    }

    /**
     * 设置信息面板（只读区域）
     */
    public ModernHudMenu setInfoPanel(int startSlot, int endSlot, Component title, List<Component> lines) {
        // 第一行显示标题
        if (startSlot < size) {
            setButton(startSlot, Material.PAPER, title, List.of());
        }

        // 后续行显示内容
        for (int i = 1; i < lines.size() && (startSlot + i) <= endSlot; i++) {
            setButton(startSlot + i, Material.BLACK_STAINED_GLASS_PANE,
                Component.text(" ").color(NamedTextColor.BLACK),
                lines.get(i));
        }

        return this;
    }

    // ==================== 内容构建（子类实现） ====================

    /**
     * 构建菜单内容 - 子类实现
     */
    protected void buildContent() {
        // 默认实现为空，子类可覆盖
    }

    // ==================== 事件处理 ====================

    public boolean handleClick(Player clicker, int slot, ItemStack item) {
        if (!clicker.equals(player) || slot < 0 || slot >= size) {
            return false;
        }

        // 检查条件
        Predicate<Player> condition = conditionHandlers.get(slot);
        if (condition != null && !condition.test(clicker)) {
            // 播放失败音效
            clicker.playSound(clicker.getLocation(),
                org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        // 执行处理器
        Consumer<Player> handler = clickHandlers.get(slot);
        if (handler != null) {
            // 播放成功音效
            clicker.playSound(clicker.getLocation(),
                org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

            handler.accept(clicker);
            return true;
        }

        return false;
    }

    public void handleClose() {
        isOpen = false;
        if (onCloseCallback != null) {
            onCloseCallback.run();
        }
    }

    // ==================== 粒子动画 ====================

    private void startParticleAnimation() {
        // 粒子动画由 ModernHudListener 统一管理
    }

    // ==================== 工具方法 ====================

    private int normalizeSize(int size) {
        if (size % 9 != 0) {
            size = ((size / 9) + 1) * 9;
        }
        return Math.max(9, Math.min(54, size));
    }

    /**
     * 获取居中位置的槽位
     */
    protected int[] getCenterSlots(int itemCount, int itemsPerRow) {
        int rows = (int) Math.ceil((double) itemCount / itemsPerRow);
        int startRow = Math.max(1, (size / 9 - rows) / 2);
        int startSlot = startRow * 9 + 1;

        int[] slots = new int[itemCount];
        for (int i = 0; i < itemCount; i++) {
            int row = i / itemsPerRow;
            int col = i % itemsPerRow;
            slots[i] = startSlot + row * 9 + col;
        }

        return slots;
    }

    /**
     * 获取可用内容槽位（排除边框）
     */
    protected int[] getContentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int i = 9; i < size - 9; i++) {
            int col = i % 9;
            if (col != 0 && col != 8) {
                slots.add(i);
            }
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    // ==================== InventoryHolder 实现 ====================

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ==================== Getter ====================

    public Player getPlayer() { return player; }
    public Plugin getPlugin() { return plugin; }
    public String getMenuId() { return menuId; }
    public Component getTitle() { return title; }
    public int getSize() { return size; }
    public GlassPaneStyle getStyle() { return style; }
    public boolean isOpen() { return isOpen; }
}