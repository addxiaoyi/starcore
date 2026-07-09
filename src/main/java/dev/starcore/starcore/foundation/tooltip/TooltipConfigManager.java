package dev.starcore.starcore.foundation.tooltip;

import dev.starcore.starcore.StarCorePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 提示系统配置管理器
 */
public class TooltipConfigManager {

    private final StarCorePlugin plugin;
    private final SmartTooltipService tooltipService;
    private FileConfiguration config;
    private File configFile;

    // 运行时配置覆盖
    private final Map<String, Object> runtimeOverrides;

    public TooltipConfigManager(@NotNull StarCorePlugin plugin, @NotNull SmartTooltipService tooltipService) {
        this.plugin = plugin;
        this.tooltipService = tooltipService;
        this.runtimeOverrides = new ConcurrentHashMap<>();
        loadConfig();
    }

    /**
     * 加载配置文件
     */
    public void loadConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "tooltip-config.yml");
        }

        if (!configFile.exists()) {
            plugin.saveResource("tooltip-config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        applyConfig();
    }

    /**
     * 应用配置到服务
     */
    private void applyConfig() {
        TooltipConfig serviceConfig = tooltipService.getConfig();

        // 启用/禁用
        serviceConfig.setHotbarHintsEnabled(getBoolean("hotbar-hints.enabled", true));
        serviceConfig.setCustomTooltipsEnabled(getBoolean("custom-tooltips.enabled", true));
        serviceConfig.setRarityColorsEnabled(getBoolean("rarity-colors.enabled", true));
        serviceConfig.setItemStatsEnabled(getBoolean("item-stats.enabled", true));
        serviceConfig.setEnchantmentInfoEnabled(getBoolean("enchantment-info.enabled", true));

        // ActionBar 设置
        serviceConfig.setHotbarHintDuration(getInt("hotbar-hints.duration", 3));
        serviceConfig.setHotbarHintCooldown(getInt("hotbar-hints.cooldown", 1000));
        serviceConfig.setHotbarHintOnShift(getBoolean("hotbar-hints.show-on-shift", true));

        // 提示样式
        serviceConfig.setShowItemId(getBoolean("tooltip-style.show-item-id", true));
        serviceConfig.setShowDurability(getBoolean("tooltip-style.show-durability", true));
        serviceConfig.setShowEnchantments(getBoolean("tooltip-style.show-enchantments", true));
        serviceConfig.setShowRarity(getBoolean("tooltip-style.show-rarity", true));
        serviceConfig.setShowLore(getBoolean("tooltip-style.show-lore", true));

        // 稀有度颜色
        serviceConfig.setRarityCommon(getString("rarity-colors.common", "#9E9E9E"));
        serviceConfig.setRarityUncommon(getString("rarity-colors.uncommon", "#4CAF50"));
        serviceConfig.setRarityRare(getString("rarity-colors.rare", "#2196F3"));
        serviceConfig.setRarityEpic(getString("rarity-colors.epic", "#9C27B0"));
        serviceConfig.setRarityLegendary(getString("rarity-colors.legendary", "#FF9800"));
        serviceConfig.setRarityMythic(getString("rarity-colors.mythic", "#F44336"));

        // 性能设置
        serviceConfig.setMaxTooltipLines(getInt("performance.max-tooltip-lines", 20));
        serviceConfig.setCacheExpirationMs(getInt("performance.cache-expiration-ms", 60000));
    }

    /**
     * 保存配置
     */
    public void saveConfig() {
        if (config != null && configFile != null) {
            try {
                config.save(configFile);
            } catch (Exception e) {
                plugin.getLogger().severe("无法保存 tooltip-config.yml: " + e.getMessage());
            }
        }
    }

    /**
     * 获取布尔值（支持运行时覆盖）
     */
    public boolean getBoolean(@NotNull String path, boolean defaultValue) {
        if (runtimeOverrides.containsKey(path)) {
            return (Boolean) runtimeOverrides.get(path);
        }
        return config.getBoolean(path, defaultValue);
    }

    /**
     * 获取整数值（支持运行时覆盖）
     */
    public int getInt(@NotNull String path, int defaultValue) {
        if (runtimeOverrides.containsKey(path)) {
            return (Integer) runtimeOverrides.get(path);
        }
        return config.getInt(path, defaultValue);
    }

    /**
     * 获取字符串值（支持运行时覆盖）
     */
    @NotNull
    public String getString(@NotNull String path, @NotNull String defaultValue) {
        if (runtimeOverrides.containsKey(path)) {
            return (String) runtimeOverrides.get(path);
        }
        return config.getString(path, defaultValue);
    }

    /**
     * 获取字符串列表（支持运行时覆盖）
     */
    @NotNull
    public List<String> getStringList(@NotNull String path, @NotNull List<String> defaultValue) {
        if (runtimeOverrides.containsKey(path)) {
            Object override = runtimeOverrides.get(path);
            if (override instanceof List<?> list) {
                return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .collect(Collectors.toList());
            }
            return defaultValue;
        }
        List<String> values = config.getStringList(path);
        return values.isEmpty() ? defaultValue : values;
    }

    /**
     * 设置运行时配置覆盖
     */
    public void setRuntimeOverride(@NotNull String path, @NotNull Object value) {
        runtimeOverrides.put(path, value);
        applyConfig();
    }

    /**
     * 清除所有运行时覆盖
     */
    public void clearRuntimeOverrides() {
        runtimeOverrides.clear();
        applyConfig();
    }

    /**
     * 获取默认配置文件内容
     */
    @NotNull
    public static String getDefaultConfigContent() {
        return """
# StarCore 智能物品提示配置文件
# 快捷栏提示和物品说明优化

# ==================== 快捷栏提示设置 ====================
hotbar-hints:
  # 是否启用快捷栏提示
  enabled: true
  # 提示显示时长（秒）
  duration: 3
  # 提示冷却时间（毫秒）
  cooldown: 1000
  # 是否在按住Shift时显示
  show-on-shift: true

# ==================== 自定义提示设置 ====================
custom-tooltips:
  # 是否启用自定义物品提示
  enabled: true

# ==================== 稀有度颜色设置 ====================
rarity-colors:
  # 是否启用稀有度颜色
  enabled: true
  # 稀有度颜色（支持HEX颜色格式 #RRGGBB）
  common: "#9E9E9E"        # 普通 - 灰色
  uncommon: "#4CAF50"      # 优秀 - 绿色
  rare: "#2196F3"          # 稀有 - 蓝色
  epic: "#9C27B0"          # 史诗 - 紫色
  legendary: "#FF9800"     # 传说 - 橙色
  mythic: "#F44336"        # 神话 - 红色

# ==================== 物品信息显示设置 ====================
item-stats:
  # 是否显示物品统计信息
  enabled: true

enchantment-info:
  # 是否显示附魔信息
  enabled: true

# ==================== 提示样式设置 ====================
tooltip-style:
  # 显示物品ID
  show-item-id: true
  # 显示耐久度
  show-durability: true
  # 显示附魔
  show-enchantments: true
  # 显示稀有度标签
  show-rarity: true
  # 显示自定义Lore
  show-lore: true

# ==================== 性能设置 ====================
performance:
  # 最大提示行数
  max-tooltip-lines: 20
  # 提示缓存过期时间（毫秒）
  cache-expiration-ms: 60000
""";
    }

    /**
     * 保存默认配置
     */
    public void saveDefaultConfig() {
        try {
            configFile.getParentFile().mkdirs();
            configFile.createNewFile();
            java.io.FileWriter writer = new java.io.FileWriter(configFile);
            writer.write(getDefaultConfigContent());
            writer.close();
        } catch (Exception e) {
            plugin.getLogger().severe("无法创建默认 tooltip-config.yml: " + e.getMessage());
        }
    }
}
