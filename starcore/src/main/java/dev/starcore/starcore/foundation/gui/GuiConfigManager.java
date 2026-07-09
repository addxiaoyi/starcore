package dev.starcore.starcore.foundation.gui;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 通用 GUI 配置管理器
 *
 * 从 gui-config.yml 加载所有 GUI 的配置
 * 支持占位符替换和动态物品生成
 *
 * 使用方式:
 * <pre>
 * GuiConfigManager config = GuiConfigManager.getInstance(plugin);
 *
 * // 获取社交主菜单配置
 * MenuConfig menu = config.getMenu("social.main-menu");
 *
 * // 获取物品配置并替换占位符
 * ItemConfig item = menu.getItem("friends");
 * ItemConfig resolved = item.resolve(
 *     "friend_count", "5",
 *     "online_count", "3"
 * );
 * </pre>
 */
public class GuiConfigManager {

    private static GuiConfigManager instance;

    private final Plugin plugin;
    private final File configFile;
    private YamlConfiguration config;

    // 缓存解析后的配置
    private final Map<String, MenuConfig> menuCache = new HashMap<>();
    private final Map<String, ItemConfig> itemCache = new HashMap<>();
    private final Map<String, ButtonConfig> buttonCache = new HashMap<>();

    // 占位符正则
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)}");

    private GuiConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "gui-config.yml");
        loadConfig();
    }

    /**
     * 获取单例实例
     */
    public static synchronized GuiConfigManager getInstance(Plugin plugin) {
        if (instance == null) {
            instance = new GuiConfigManager(plugin);
        }
        return instance;
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        menuCache.clear();
        itemCache.clear();
        buttonCache.clear();
        loadConfig();
    }

    /**
     * 加载配置文件
     */
    private void loadConfig() {
        // 如果文件不存在，从 jar 中复制默认配置
        if (!configFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try (InputStream is = plugin.getResource("gui-config.yml")) {
                if (is != null) {
                    Files.copy(is, configFile.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to extract gui-config.yml: " + e.getMessage());
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * 获取菜单配置
     *
     * @param menuPath 菜单路径，如 "social.main-menu"
     * @return 菜单配置，如果没有则返回 null
     */
    public @Nullable MenuConfig getMenu(String menuPath) {
        // 先检查缓存
        if (menuCache.containsKey(menuPath)) {
            return menuCache.get(menuPath);
        }

        // 解析路径
        String[] parts = menuPath.split("\\.");
        if (parts.length < 2) {
            return null;
        }

        // 构建配置路径
        String configPath = String.join(".", parts);

        if (!config.isConfigurationSection(configPath)) {
            return null;
        }

        MenuConfig menu = parseMenuConfig(configPath);
        menuCache.put(menuPath, menu);
        return menu;
    }

    /**
     * 解析菜单配置
     */
    private MenuConfig parseMenuConfig(String path) {
        String title = translateColor(config.getString(path + ".title", "Menu"));
        int rows = config.getInt(path + ".rows", 6);

        // 解析边框配置
        BorderConfig border = parseBorderConfig(path + ".border");

        // 解析物品配置
        Map<String, ItemConfig> items = new LinkedHashMap<>();
        if (config.isConfigurationSection(path + ".items")) {
            for (String key : config.getConfigurationSection(path + ".items").getKeys(false)) {
                String itemPath = path + ".items." + key;
                items.put(key, parseItemConfig(itemPath));
            }
        }

        return new MenuConfig(title, rows, border, items);
    }

    /**
     * 解析边框配置
     */
    private BorderConfig parseBorderConfig(String path) {
        boolean enabled = config.getBoolean(path + ".enabled", true);
        Material material = parseMaterial(config.getString(path + ".material", "BLACK_STAINED_GLASS_PANE"));
        String displayName = translateColor(config.getString(path + ".display-name", " "));

        List<Integer> topRow = config.getIntegerList(path + ".slots.top-row");
        List<Integer> bottomRow = config.getIntegerList(path + ".slots.bottom-row");
        List<Integer> leftColumn = config.getIntegerList(path + ".slots.left-column");
        List<Integer> rightColumn = config.getIntegerList(path + ".slots.right-column");

        return new BorderConfig(enabled, material, displayName, topRow, bottomRow, leftColumn, rightColumn);
    }

    /**
     * 解析物品配置
     */
    private ItemConfig parseItemConfig(String path) {
        int slot = config.getInt(path + ".slot", 0);
        Material material = parseMaterial(config.getString(path + ".material", "STONE"));
        String displayName = translateColor(config.getString(path + ".display-name", ""));
        boolean glow = config.getBoolean(path + ".glow", false);
        List<String> lore = config.getStringList(path + ".lore").stream()
            .map(this::translateColor)
            .collect(Collectors.toList());
        String command = config.getString(path + ".command", "");
        String permission = config.getString(path + ".permission", "");
        String type = config.getString(path + ".type", "static");

        // 解析头颅所有者（如果适用）
        String skullOwner = null;
        if (config.contains(path + ".skull-owner")) {
            skullOwner = config.getString(path + ".skull-owner");
        }

        return new ItemConfig(slot, material, displayName, glow, lore, command, permission, type, skullOwner);
    }

    /**
     * 获取按钮配置
     */
    public ButtonConfig getButton(String buttonName) {
        if (buttonCache.containsKey(buttonName)) {
            return buttonCache.get(buttonName);
        }

        String path = "buttons." + buttonName;
        if (!config.isConfigurationSection(path)) {
            return null;
        }

        ButtonConfig button = new ButtonConfig(
            parseMaterial(config.getString(path + ".material", "STONE")),
            translateColor(config.getString(path + ".display-name", "")),
            config.getStringList(path + ".lore").stream()
                .map(this::translateColor)
                .collect(Collectors.toList())
        );

        buttonCache.put(buttonName, button);
        return button;
    }

    /**
     * 解析材质（支持别名）
     */
    private Material parseMaterial(String name) {
        // 材质别名映射
        Map<String, Material> aliases = new HashMap<>();
        aliases.put("SKULL", Material.PLAYER_HEAD);
        aliases.put("HEAD", Material.PLAYER_HEAD);
        aliases.put("CHEST_MINECART", Material.MINECART);
        aliases.put("COBBLESTONE_STAIRS", Material.COBBLESTONE_STAIRS);
        aliases.put("ENDER_ROLL", Material.EMERALD);

        String normalized = name.toUpperCase().trim();
        if (aliases.containsKey(normalized)) {
            return aliases.get(normalized);
        }

        try {
            return Material.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown material: " + name + ", using STONE");
            return Material.STONE;
        }
    }

    /**
     * 翻译颜色代码
     */
    private String translateColor(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replace("&", "§");
    }

    // ==================== 配置类 ====================

    /**
     * 菜单配置
     */
    public static class MenuConfig {
        private final String title;
        private final int rows;
        private final BorderConfig border;
        private final Map<String, ItemConfig> items;

        public MenuConfig(String title, int rows, BorderConfig border, Map<String, ItemConfig> items) {
            this.title = title;
            this.rows = rows;
            this.border = border;
            this.items = items;
        }

        public String getTitle() {
            return title;
        }

        public int getRows() {
            return rows;
        }

        public int getSize() {
            return rows * 9;
        }

        public BorderConfig getBorder() {
            return border;
        }

        public Map<String, ItemConfig> getItems() {
            return items;
        }

        public @Nullable ItemConfig getItem(String key) {
            return items.get(key);
        }

        /**
         * 获取排序后的物品列表
         */
        public List<ItemConfig> getSortedItems() {
            return items.values().stream()
                .sorted(Comparator.comparingInt(ItemConfig::getSlot))
                .collect(Collectors.toList());
        }
    }

    /**
     * 边框配置
     */
    public static class BorderConfig {
        private final boolean enabled;
        private final Material material;
        private final String displayName;
        private final List<Integer> topRow;
        private final List<Integer> bottomRow;
        private final List<Integer> leftColumn;
        private final List<Integer> rightColumn;

        public BorderConfig(boolean enabled, Material material, String displayName,
                          List<Integer> topRow, List<Integer> bottomRow,
                          List<Integer> leftColumn, List<Integer> rightColumn) {
            this.enabled = enabled;
            this.material = material;
            this.displayName = displayName;
            this.topRow = topRow != null ? topRow : Collections.emptyList();
            this.bottomRow = bottomRow != null ? bottomRow : Collections.emptyList();
            this.leftColumn = leftColumn != null ? leftColumn : Collections.emptyList();
            this.rightColumn = rightColumn != null ? rightColumn : Collections.emptyList();
        }

        public boolean isEnabled() {
            return enabled;
        }

        public Material getMaterial() {
            return material;
        }

        public String getDisplayName() {
            return displayName;
        }

        /**
         * 获取所有边框槽位
         */
        public List<Integer> getAllSlots() {
            List<Integer> slots = new ArrayList<>();
            slots.addAll(topRow);
            slots.addAll(bottomRow);
            slots.addAll(leftColumn);
            slots.addAll(rightColumn);
            return slots.stream().distinct().collect(Collectors.toList());
        }
    }

    /**
     * 物品配置
     */
    public static class ItemConfig {
        private final int slot;
        private final Material material;
        private final String displayName;
        private final boolean glow;
        private final List<String> lore;
        private final String command;
        private final String permission;
        private final String type;
        private final String skullOwner;

        public ItemConfig(int slot, Material material, String displayName, boolean glow,
                         List<String> lore, String command, String permission, String type,
                         String skullOwner) {
            this.slot = slot;
            this.material = material;
            this.displayName = displayName;
            this.glow = glow;
            this.lore = lore;
            this.command = command;
            this.permission = permission;
            this.type = type;
            this.skullOwner = skullOwner;
        }

        public ItemConfig(int slot, Material material, String displayName, boolean glow,
                         List<String> lore, String command, String permission, String type) {
            this(slot, material, displayName, glow, lore, command, permission, type, null);
        }

        public int getSlot() {
            return slot;
        }

        public Material getMaterial() {
            return material;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isGlow() {
            return glow;
        }

        public List<String> getLore() {
            return lore;
        }

        public String getCommand() {
            return command;
        }

        public String getPermission() {
            return permission;
        }

        public String getType() {
            return type;
        }

        public @Nullable String getSkullOwner() {
            return skullOwner;
        }

        public boolean isPlayerHead() {
            return material == Material.PLAYER_HEAD && skullOwner != null;
        }

        /**
         * 检查是否有权限
         */
        public boolean hasPermission(String playerPermission) {
            return permission == null || permission.isEmpty() || playerPermission == null ||
                   playerPermission.isEmpty() || playerPermission.equals(permission);
        }

        /**
         * 解析占位符
         */
        public ItemConfig resolve(String... replacements) {
            if (replacements == null || replacements.length == 0) {
                return this;
            }

            Map<String, String> replacementMap = new HashMap<>();
            for (int i = 0; i < replacements.length - 1; i += 2) {
                String value = (i + 1 < replacements.length) ? replacements[i + 1] : "";
                replacementMap.put(replacements[i], value);
            }

            return resolve(replacementMap);
        }

        /**
         * 解析占位符
         */
        public ItemConfig resolve(Map<String, String> replacements) {
            if (replacements == null || replacements.isEmpty()) {
                return this;
            }

            String newName = displayName;
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                newName = newName.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
            }

            List<String> newLore = lore.stream()
                .map(line -> {
                    String result = line;
                    for (Map.Entry<String, String> entry : replacements.entrySet()) {
                        result = result.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
                    }
                    return result;
                })
                .collect(Collectors.toList());

            String newSkullOwner = skullOwner;
            if (newSkullOwner != null) {
                for (Map.Entry<String, String> entry : replacements.entrySet()) {
                    newSkullOwner = newSkullOwner.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
                }
            }

            return new ItemConfig(slot, material, newName, glow, newLore, command, permission, type, newSkullOwner);
        }
    }

    /**
     * 按钮配置
     */
    public static class ButtonConfig {
        private final Material material;
        private final String displayName;
        private final List<String> lore;

        public ButtonConfig(Material material, String displayName, List<String> lore) {
            this.material = material;
            this.displayName = displayName;
            this.lore = lore;
        }

        public Material getMaterial() {
            return material;
        }

        public String getDisplayName() {
            return displayName;
        }

        public List<String> getLore() {
            return lore;
        }
    }
}
