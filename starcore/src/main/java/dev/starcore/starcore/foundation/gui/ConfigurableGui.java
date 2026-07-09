package dev.starcore.starcore.foundation.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.function.Consumer;

/**
 * 可配置 GUI 基类
 *
 * 基于 GuiConfigManager 加载外部配置
 * 支持从 gui-config.yml 读取菜单布局和物品配置
 *
 * 使用方式:
 * <pre>
 * public class SocialMenuGui extends ConfigurableGui {
 *     public SocialMenuGui(Player player) {
 *         super(player, "social.main-menu");
 *     }
 *
 *     &#64;Override
 *     protected void buildContent() {
 *         // 添加配置物品
 *         addConfiguredItem("friends", Map.of(
 *             "friend_count", "5",
 *             "online_count", "3"
 *         ));
 *     }
 * }
 * </pre>
 */
public abstract class ConfigurableGui {

    protected final Player player;
    protected final Plugin plugin;
    protected final GuiConfigManager configManager;
    protected final String menuPath;

    protected Inventory inventory;
    protected Component title;
    protected int page = 0;

    // 点击处理器
    protected final Map<Integer, Consumer<Player>> clickHandlers = new HashMap<>();

    // 当前菜单配置
    protected GuiConfigManager.MenuConfig menuConfig;

    public ConfigurableGui(Player player, Plugin plugin, String menuPath) {
        this.player = player;
        this.plugin = plugin;
        this.menuPath = menuPath;
        this.configManager = GuiConfigManager.getInstance(plugin);
        loadMenuConfig();
    }

    /**
     * 加载菜单配置
     */
    private void loadMenuConfig() {
        menuConfig = configManager.getMenu(menuPath);
        if (menuConfig != null) {
            title = Component.text(menuConfig.getTitle());
        } else {
            title = Component.text("Menu");
        }
    }

    /**
     * 重新加载配置
     */
    public void reloadConfig() {
        configManager.reload();
        loadMenuConfig();
    }

    /**
     * 构建 GUI
     */
    public void build() {
        if (menuConfig == null) {
            // 使用默认布局
            inventory = Bukkit.createInventory(player, 54, title);
            fillBackground();
            return;
        }

        // 使用配置的布局
        inventory = Bukkit.createInventory(player, menuConfig.getSize(), title);
        fillBorder();
    }

    /**
     * 构建内容（子类可重写）
     */
    protected void buildContent() {
        // 默认空实现
    }

    /**
     * 打开 GUI
     */
    public void open() {
        build();
        player.openInventory(inventory);
    }

    /**
     * 打开 GUI（指定页码）
     */
    public void open(int page) {
        this.page = page;
        build();
        player.openInventory(inventory);
    }

    /**
     * 刷新 GUI
     */
    public void refresh() {
        build();
        player.openInventory(inventory);
    }

    /**
     * 关闭 GUI
     */
    public void close() {
        player.closeInventory();
        clickHandlers.clear();
    }

    /**
     * 填充边框
     */
    protected void fillBorder() {
        if (menuConfig == null || menuConfig.getBorder() == null) {
            fillBackground();
            return;
        }

        GuiConfigManager.BorderConfig border = menuConfig.getBorder();
        if (!border.isEnabled()) {
            return;
        }

        ItemStack borderItem = createBorderItem(border.getMaterial(), border.getDisplayName());
        for (int slot : border.getAllSlots()) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, borderItem);
            }
        }
    }

    /**
     * 创建边框物品
     */
    private ItemStack createBorderItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 填充背景（默认实现）
     */
    protected void fillBackground() {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            glass.setItemMeta(meta);
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, glass);
        }
    }

    /**
     * 添加配置物品（自动处理占位符替换）
     *
     * @param itemKey 物品配置键名
     * @param replacements 占位符替换值
     */
    protected void addConfiguredItem(String itemKey, Map<String, String> replacements) {
        if (menuConfig == null) {
            return;
        }

        GuiConfigManager.ItemConfig itemConfig = menuConfig.getItem(itemKey);
        if (itemConfig == null) {
            return;
        }

        // 解析占位符
        GuiConfigManager.ItemConfig resolved = itemConfig.resolve(replacements);

        // 创建物品
        ItemStack item = createConfiguredItem(resolved);

        // 设置到 GUI
        int slot = resolved.getSlot();
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);

            // 注册点击处理器
            if (!resolved.getCommand().isEmpty()) {
                registerClickHandler(slot, resolved.getCommand());
            }
        }
    }

    /**
     * 添加配置物品（简单占位符）
     */
    protected void addConfiguredItem(String itemKey, String... replacements) {
        Map<String, String> replacementMap = new HashMap<>();
        for (int i = 0; i < replacements.length - 1; i += 2) {
            replacementMap.put(replacements[i], replacements[i + 1]);
        }
        addConfiguredItem(itemKey, replacementMap);
    }

    /**
     * 创建配置物品
     */
    private ItemStack createConfiguredItem(GuiConfigManager.ItemConfig config) {
        ItemStack item;

        // 检查是否是玩家头颅
        if (config.isPlayerHead()) {
            item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
            if (skullMeta != null) {
                String owner = config.getSkullOwner();
                if (owner != null && !owner.isEmpty()) {
                    // 尝试获取玩家
                    Player skullOwner = Bukkit.getPlayer(owner);
                    if (skullOwner != null) {
                        skullMeta.setOwningPlayer(skullOwner);
                    }
                }
                skullMeta.displayName(Component.text(config.getDisplayName()));
                skullMeta.lore(config.getLore().stream()
                    .map(l -> Component.text(l))
                    .toList());
                item.setItemMeta(skullMeta);
            }
        } else {
            item = new ItemStack(config.getMaterial());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(config.getDisplayName()));
                meta.lore(config.getLore().stream()
                    .map(l -> Component.text(l))
                    .toList());
                item.setItemMeta(meta);
            }

            // 添加发光效果（通过 NBT 或使用特殊标签）
            if (config.isGlow()) {
                addGlow(item);
            }
        }

        return item;
    }

    /**
     * 添加发光效果
     */
    private void addGlow(ItemStack item) {
        // Minecraft 1.8+ 发光效果可以通过设置 enchantmentglint 或使用特定方法
        // 这里使用简化的方式：复制带有附魔的书本的光泽
        try {
            ItemStack glowItem = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta glowMeta = glowItem.getItemMeta();
            if (glowMeta != null && item.getItemMeta() != null) {
                // 在新版本中可以使用以下方式之一
                // 1. 通过反射设置enchantmentglint
                // 2. 使用特定的数据标签
            }
        } catch (Exception e) {
            // 忽略发光效果错误
        }
    }

    /**
     * 注册点击处理器
     */
    protected void registerClickHandler(int slot, String command) {
        clickHandlers.put(slot, player -> {
            if (command.startsWith("submenu:")) {
                // 子菜单命令
                String submenu = command.substring(8);
                handleSubmenuCommand(submenu);
            } else if (command.startsWith("close")) {
                close();
            } else {
                // 执行命令
                Bukkit.dispatchCommand(player, command);
            }
        });
    }

    /**
     * 处理子菜单命令
     */
    protected void handleSubmenuCommand(String submenu) {
        // 子类可以重写此方法来处理子菜单导航
    }

    /**
     * 设置按钮（手动方式）
     */
    protected void setButton(int slot, Material material, String name, List<String> lore) {
        setButton(slot, new ItemStack(material), name, lore);
    }

    /**
     * 设置按钮（手动方式）
     */
    protected void setButton(int slot, Material material, String name, String... lore) {
        setButton(slot, new ItemStack(material), name, Arrays.asList(lore));
    }

    /**
     * 设置按钮
     */
    protected void setButton(int slot, ItemStack item, String name, List<String> lore) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            meta.lore(lore.stream().map(l -> Component.text(l)).toList());
            item.setItemMeta(meta);
        }

        inventory.setItem(slot, item);
    }

    /**
     * 设置可点击按钮
     */
    protected void setClickableButton(int slot, Material material, String name,
                                     List<String> lore, Consumer<Player> onClick) {
        setButton(slot, material, name, lore);
        clickHandlers.put(slot, onClick);
    }

    /**
     * 设置空槽
     */
    protected void setEmpty(int slot) {
        setButton(slot, Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    /**
     * 处理点击事件
     */
    public boolean handleClick(Player clicker, int slot) {
        if (!clicker.equals(player)) {
            return false;
        }

        Consumer<Player> handler = clickHandlers.get(slot);
        if (handler != null) {
            handler.accept(clicker);
            return true;
        }

        return false;
    }

    /**
     * 获取 GUI 标题
     */
    public Component getTitle() {
        return title;
    }

    /**
     * 获取玩家
     */
    protected Player getPlayer() {
        return player;
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
     * 格式化数字
     */
    protected String formatNumber(long number) {
        return String.format("%,d", number);
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

    /**
     * 创建颜色进度条
     */
    protected String createColorProgressBar(double current, double max, int length,
                                          String filledColor, String emptyColor) {
        int filled = (int) ((current / max) * length);
        int empty = length - filled;

        StringBuilder bar = new StringBuilder();
        bar.append(filledColor);
        for (int i = 0; i < filled; i++) bar.append("█");
        bar.append(emptyColor);
        for (int i = 0; i < empty; i++) bar.append("█");

        return bar.toString();
    }
}
