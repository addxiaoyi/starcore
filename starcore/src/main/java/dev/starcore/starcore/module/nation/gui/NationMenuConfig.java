package dev.starcore.starcore.module.nation.gui;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 国家菜单配置加载器
 * 从 nation-menu.yml 加载菜单布局和图标配置
 */
public class NationMenuConfig {
    private final Plugin plugin;
    private final File configFile;
    private YamlConfiguration config;

    // 缓存解析后的配置
    private MenuLayout mainMemberLayout;
    private MenuLayout mainVisitorLayout;
    private ItemConfig visitorGuideItem;
    private Map<String, MenuLayout> submenuLayouts;

    public NationMenuConfig(Plugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "nation-menu.yml");
        this.config = loadConfig();
        reload();
    }

    /**
     * 加载或创建配置文件
     */
    private YamlConfiguration loadConfig() {
        if (!configFile.exists()) {
            // 从 jar 中复制默认配置
            plugin.getDataFolder().mkdirs();
            try (InputStream is = plugin.getResource("nation-menu.yml")) {
                if (is != null) {
                    Files.copy(is, configFile.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to extract nation-menu.yml: " + e.getMessage());
            }
        }

        return YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * 重载配置
     */
    public void reload() {
        if (configFile.exists()) {
            config = YamlConfiguration.loadConfiguration(configFile);
        }

        // 解析成员菜单布局
        mainMemberLayout = parseMenuLayout("main-member");
        // 解析访客菜单布局
        mainVisitorLayout = parseMenuLayout("main-visitor");
        // 解析访客提示
        visitorGuideItem = parseItem("visitor-guide");
        // 解析所有子菜单
        submenuLayouts = parseAllSubmenus();
    }

    /**
     * 解析所有子菜单
     */
    private Map<String, MenuLayout> parseAllSubmenus() {
        Map<String, MenuLayout> result = new HashMap<>();
        if (!config.isConfigurationSection("submenus")) {
            return result;
        }
        for (String key : config.getConfigurationSection("submenus").getKeys(false)) {
            MenuLayout layout = parseMenuLayout("submenus." + key);
            if (layout != null) {
                result.put(key, layout);
            }
        }
        return result;
    }

    /**
     * 解析菜单布局
     */
    private @Nullable MenuLayout parseMenuLayout(String path) {
        String title = config.getString(path + ".title", "⚔ 国家管理");
        int rows = config.getInt(path + ".rows", 6);
        boolean borderEnabled = config.getBoolean(path + ".border.enabled", true);
        Material borderMaterial = parseMaterial(config.getString(path + ".border.material", "GRAY_STAINED_GLASS_PANE"));

        // 解析每个物品
        Map<String, ItemConfig> items = new LinkedHashMap<>();
        if (config.isConfigurationSection(path + ".items")) {
            for (String key : config.getConfigurationSection(path + ".items").getKeys(false)) {
                items.put(key, parseItem(path + ".items." + key));
            }
        }

        return new MenuLayout(title, rows, borderEnabled, borderMaterial, items);
    }

    /**
     * 解析单个物品配置
     */
    private ItemConfig parseItem(String path) {
        int slot = config.getInt(path + ".slot", 0);
        Material material = parseMaterial(config.getString(path + ".material", "STONE"));
        String displayName = config.getString(path + ".display-name", "");
        boolean glow = config.getBoolean(path + ".glow", false);
        List<String> lore = config.getStringList(path + ".lore");
        String command = config.getString(path + ".command", "");
        String permission = config.getString(path + ".permission", "");
        String type = config.getString(path + ".type", "static");
        int pageSize = config.getInt(path + ".page-size", 28);

        return new ItemConfig(slot, material, displayName, glow, lore, command, permission, type, pageSize);
    }

    /**
     * 解析材质（兼容旧版本）
     */
    private Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown material: " + name + ", using STONE");
            return Material.STONE;
        }
    }

    // ==================== Getters ====================

    public MenuLayout getMainMemberLayout() {
        return mainMemberLayout;
    }

    public MenuLayout getMainVisitorLayout() {
        return mainVisitorLayout;
    }

    public ItemConfig getVisitorGuideItem() {
        return visitorGuideItem;
    }

    public Map<String, MenuLayout> getSubmenuLayouts() {
        return submenuLayouts;
    }

    public MenuLayout getSubmenuLayout(String id) {
        return submenuLayouts.get(id);
    }

    // ==================== 配置类 ====================

    /**
     * 菜单布局配置
     */
    public static class MenuLayout {
        private final String title;
        private final int rows;
        private final boolean borderEnabled;
        private final Material borderMaterial;
        private final Map<String, ItemConfig> items;

        public MenuLayout(String title, int rows, boolean borderEnabled, Material borderMaterial, Map<String, ItemConfig> items) {
            this.title = title;
            this.rows = rows;
            this.borderEnabled = borderEnabled;
            this.borderMaterial = borderMaterial;
            this.items = items;
        }

        public String getTitle() {
            return title;
        }

        public int getRows() {
            return rows;
        }

        public boolean isBorderEnabled() {
            return borderEnabled;
        }

        public Material getBorderMaterial() {
            return borderMaterial;
        }

        public Map<String, ItemConfig> getItems() {
            return items;
        }

        public @Nullable ItemConfig getItem(String key) {
            return items.get(key);
        }

        /**
         * 获取所有物品，按 slot 排序
         */
        public List<ItemConfig> getSortedItems() {
            return items.values().stream()
                .sorted(Comparator.comparingInt(ItemConfig::getSlot))
                .collect(Collectors.toList());
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
        private final String type; // "static", "member_list"
        private final int pageSize;

        public ItemConfig(int slot, Material material, String displayName, boolean glow,
                        List<String> lore, String command, String permission) {
            this(slot, material, displayName, glow, lore, command, permission, "static", 28);
        }

        public ItemConfig(int slot, Material material, String displayName, boolean glow,
                        List<String> lore, String command, String permission,
                        String type, int pageSize) {
            this.slot = slot;
            this.material = material;
            this.displayName = displayName;
            this.glow = glow;
            this.lore = lore;
            this.command = command;
            this.permission = permission;
            this.type = type;
            this.pageSize = pageSize;
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

        public int getPageSize() {
            return pageSize;
        }

        /**
         * 检查是否有指定权限
         */
        public boolean hasPermission(String permission) {
            return permission == null || permission.isEmpty() || this.permission.isEmpty();
        }

        /**
         * 处理占位符替换
         */
        public ItemConfig withReplacements(Map<String, String> replacements) {
            String newName = replacePlaceholders(displayName, replacements);
            List<String> newLore = lore.stream()
                .map(l -> replacePlaceholders(l, replacements))
                .collect(Collectors.toList());
            return new ItemConfig(slot, material, newName, glow, newLore, command, permission, type, pageSize);
        }

        private String replacePlaceholders(String text, Map<String, String> replacements) {
            String result = text;
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            return result;
        }
    }
}