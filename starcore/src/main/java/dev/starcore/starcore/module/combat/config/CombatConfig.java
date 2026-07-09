package dev.starcore.starcore.module.combat.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * 战斗系统配置
 */
public final class CombatConfig {
    private final Plugin plugin;
    private FileConfiguration config;

    // 战斗标记设置
    private long tagTimeout;                    // 战斗标记超时时间（毫秒）
    private boolean combatTagEnabled;           // 是否启用战斗标记
    private boolean combatLogoutEnabled;       // 是否禁止战斗时退出
    private long combatLogoutTimeout;           // 战斗退出超时时间（毫秒）

    // PvP设置
    private boolean pvpEnabled;                 // 是否启用PvP
    private boolean pvpEnabledInSafeZones;     // 安全区是否允许PvP
    private boolean friendlyFireEnabled;        // 友军伤害是否启用

    // 战斗会话设置
    private long sessionTimeout;                // 战斗会话超时时间（秒）
    private int maxParticipantsPerSession;      // 每个会话最大参与者数

    // 战场设置
    private boolean battlefieldAutoCreate;      // 是否自动创建战场
    private double defaultBattlefieldRadius;    // 默认战场半径

    // 伤害显示设置
    private boolean damageIndicatorEnabled;     // 是否显示伤害指示器
    private boolean combatTagNotification;      // 是否显示战斗标记通知

    // 性能设置
    private int maxActiveSessions;              // 最大活跃会话数
    private int cleanupIntervalSeconds;         // 清理间隔（秒）

    // 战斗标签消息
    private String combatTagMessage;
    private String combatTagExpiredMessage;
    private String combatLogoutBlockedMessage;
    private String combatDeathMessage;

    // 战斗物品限制设置
    private boolean combatItemRestrictionEnabled; // 是否启用战斗物品限制
    private boolean blockGoldenAppleInCombat;      // 是否禁止金苹果
    private boolean blockPotionsInCombat;         // 是否禁止药水
    private boolean blockFoodInCombat;            // 是否禁止食物
    private java.util.Set<String> blockedItemsInCombat; // 被禁止的物品列表

    // 战斗区域定义
    private Map<String, CombatZone> combatZones;

    public CombatConfig(Plugin plugin) {
        this.plugin = plugin;
        this.combatZones = new HashMap<>();
        this.blockedItemsInCombat = new java.util.HashSet<>();
        loadConfig();
    }

    public void loadConfig() {
        // 保存默认配置
        File configFile = new File(plugin.getDataFolder(), "combat_config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("combat_config.yml", false);
        }

        this.config = YamlConfiguration.loadConfiguration(configFile);

        // 加载战斗标记设置
        this.tagTimeout = config.getLong("combat-tag.timeout", 15000); // 15秒
        this.combatTagEnabled = config.getBoolean("combat-tag.enabled", true);
        this.combatLogoutEnabled = config.getBoolean("combat-tag.logout-block.enabled", true);
        this.combatLogoutTimeout = config.getLong("combat-tag.logout-block.timeout", 30000); // 30秒

        // 加载PvP设置
        this.pvpEnabled = config.getBoolean("pvp.enabled", true);
        this.pvpEnabledInSafeZones = config.getBoolean("pvp.allow-in-safe-zones", false);
        this.friendlyFireEnabled = config.getBoolean("pvp.friendly-fire", false);

        // 加载战斗会话设置
        this.sessionTimeout = config.getLong("session.timeout", 300); // 5分钟
        this.maxParticipantsPerSession = config.getInt("session.max-participants", 10);

        // 加载战场设置
        this.battlefieldAutoCreate = config.getBoolean("battlefield.auto-create", true);
        this.defaultBattlefieldRadius = config.getDouble("battlefield.default-radius", 100.0);

        // 加载显示设置
        this.damageIndicatorEnabled = config.getBoolean("display.damage-indicator", true);
        this.combatTagNotification = config.getBoolean("display.combat-tag-notification", true);

        // 加载性能设置
        this.maxActiveSessions = config.getInt("performance.max-active-sessions", 1000);
        this.cleanupIntervalSeconds = config.getInt("performance.cleanup-interval", 10);

        // 加载消息
        this.combatTagMessage = config.getString("messages.combat-tag", "&c你正处于战斗状态！(%time%)");
        this.combatTagExpiredMessage = config.getString("messages.combat-tag-expired", "&a你已经脱离战斗状态。");
        this.combatLogoutBlockedMessage = config.getString("messages.logout-blocked", "&c你正处于战斗状态，无法退出游戏！");
        this.combatDeathMessage = config.getString("messages.combat-death", "&c你在战斗中死亡！");

        // 加载战斗物品限制设置
        this.combatItemRestrictionEnabled = config.getBoolean("item-restriction.enabled", true);
        this.blockGoldenAppleInCombat = config.getBoolean("item-restriction.block-golden-apple", true);
        this.blockPotionsInCombat = config.getBoolean("item-restriction.block-potions", false);
        this.blockFoodInCombat = config.getBoolean("item-restriction.block-food", false);
        this.blockedItemsInCombat = new java.util.HashSet<>(config.getStringList("item-restriction.blocked-items"));

        // 加载战斗区域
        loadCombatZones();

        plugin.getLogger().info("CombatConfig loaded.");
    }

    private void loadCombatZones() {
        combatZones.clear();

        if (config.contains("zones")) {
            for (String key : config.getConfigurationSection("zones").getKeys(false)) {
                String path = "zones." + key + ".";
                String world = config.getString(path + "world");
                double x = config.getDouble(path + "x");
                double y = config.getDouble(path + "y");
                double z = config.getDouble(path + "z");
                double radius = config.getDouble(path + "radius", 50.0);
                boolean pvpEnabled = config.getBoolean(path + "pvp-enabled", true);
                String zoneType = config.getString(path + "type", "PVP_ZONE");

                if (world != null) {
                    combatZones.put(key, new CombatZone(key, world, x, y, z, radius, pvpEnabled, CombatZoneType.valueOf(zoneType)));
                }
            }
        }
    }

    /**
     * 保存配置
     */
    public void saveConfig() {
        File configFile = new File(plugin.getDataFolder(), "combat_config.yml");
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save combat config!", e);
        }
    }

    // Getters

    public long getTagTimeout() {
        return tagTimeout;
    }

    public boolean isCombatTagEnabled() {
        return combatTagEnabled;
    }

    public boolean isCombatLogoutEnabled() {
        return combatLogoutEnabled;
    }

    public long getCombatLogoutTimeout() {
        return combatLogoutTimeout;
    }

    public boolean isPvPEnabled() {
        return pvpEnabled;
    }

    public boolean isPvPEnabledInSafeZones() {
        return pvpEnabledInSafeZones;
    }

    public boolean isFriendlyFireEnabled() {
        return friendlyFireEnabled;
    }

    public long getSessionTimeout() {
        return sessionTimeout;
    }

    public int getMaxParticipantsPerSession() {
        return maxParticipantsPerSession;
    }

    public boolean isBattlefieldAutoCreate() {
        return battlefieldAutoCreate;
    }

    public double getDefaultBattlefieldRadius() {
        return defaultBattlefieldRadius;
    }

    public boolean isDamageIndicatorEnabled() {
        return damageIndicatorEnabled;
    }

    public boolean isCombatTagNotificationEnabled() {
        return combatTagNotification;
    }

    public int getMaxActiveSessions() {
        return maxActiveSessions;
    }

    public int getCleanupIntervalSeconds() {
        return cleanupIntervalSeconds;
    }

    public String getCombatTagMessage() {
        return combatTagMessage;
    }

    public String getCombatTagExpiredMessage() {
        return combatTagExpiredMessage;
    }

    public String getCombatLogoutBlockedMessage() {
        return combatLogoutBlockedMessage;
    }

    public String getCombatDeathMessage() {
        return combatDeathMessage;
    }

    public Map<String, CombatZone> getCombatZones() {
        return combatZones;
    }

    public CombatZone getCombatZone(String key) {
        return combatZones.get(key);
    }

    // 战斗物品限制 Getters

    public boolean isCombatItemRestrictionEnabled() {
        return combatItemRestrictionEnabled;
    }

    public boolean isBlockGoldenAppleInCombat() {
        return blockGoldenAppleInCombat;
    }

    public boolean isBlockPotionsInCombat() {
        return blockPotionsInCombat;
    }

    public boolean isBlockFoodInCombat() {
        return blockFoodInCombat;
    }

    public java.util.Set<String> getBlockedItemsInCombat() {
        return blockedItemsInCombat;
    }

    // Setters

    public void setTagTimeout(long timeout) {
        this.tagTimeout = timeout;
    }

    public void setCombatTagEnabled(boolean enabled) {
        this.combatTagEnabled = enabled;
    }

    public void setCombatLogoutEnabled(boolean enabled) {
        this.combatLogoutEnabled = enabled;
    }

    public void setPvPEnabled(boolean enabled) {
        this.pvpEnabled = enabled;
    }

    public void setFriendlyFireEnabled(boolean enabled) {
        this.friendlyFireEnabled = enabled;
    }

    public void setSessionTimeout(long timeout) {
        this.sessionTimeout = timeout;
    }

    public void setCombatItemRestrictionEnabled(boolean enabled) {
        this.combatItemRestrictionEnabled = enabled;
    }

    public void setBlockGoldenAppleInCombat(boolean block) {
        this.blockGoldenAppleInCombat = block;
    }

    public void setBlockPotionsInCombat(boolean block) {
        this.blockPotionsInCombat = block;
    }

    public void setBlockFoodInCombat(boolean block) {
        this.blockFoodInCombat = block;
    }

    public void setBlockedItemsInCombat(java.util.Set<String> items) {
        this.blockedItemsInCombat = items;
    }

    // 格式化消息
    public String formatMessage(String message, Map<String, String> placeholders) {
        String result = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    /**
     * 战斗区域
     */
    public record CombatZone(
        String id,
        String world,
        double x,
        double y,
        double z,
        double radius,
        boolean pvpEnabled,
        CombatZoneType type
    ) {
        public boolean isInside(String worldName, double px, double pz) {
            if (!world.equals(worldName)) return false;
            double dx = px - x;
            double dz = pz - z;
            return (dx * dx + dz * dz) <= radius * radius;
        }
    }

    /**
     * 战斗区域类型
     */
    public enum CombatZoneType {
        SAFE_ZONE,      // 安全区（禁止PVP）
        PVP_ZONE,       // PvP区
        WAR_ZONE,       // 战争区
        ARENA,          // 竞技场
        EVENT           // 活动区
    }
}
