package dev.starcore.starcore.war;

import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 战争配置 - 从配置文件加载
 */
public class WarConfig {
    private final Plugin plugin;
    private final Logger logger;

    // 战争准备时间（小时）
    private int preparationHours = 24;

    // 最小动员率
    private double minMobilizationRate = 0.1;

    // 最大战争持续时间（天）
    private int maxWarDurationDays = 30;

    // 宣战冷却时间（小时）
    private int declarationCooldownHours = 12;

    // 战争赔款比例上限
    private double maxWarReparationsRatio = 0.5;

    // 最小宣战费用
    private double minDeclarationCost = 1000.0;

    // 允许同时进行的战争数量上限
    private int maxSimultaneousWars = 3;

    // 战争胜利条件
    private VictoryCondition victoryCondition = VictoryCondition.OCCUPATION;

    // 占领胜利所需天数
    private int occupationVictoryDays = 7;

    // 征服胜利所需首都占领天数
    private int conquestVictoryDays = 3;

    // 允许投降
    private boolean surrenderAllowed = true;

    // 投降所需赔款比例
    private double surrenderReparationsRatio = 0.2;

    // 允许联盟参战
    private boolean allianceWarEnabled = true;

    // 中立国宣战惩罚
    private double neutralDeclarationPenalty = 1.5;

    // 战争期间税收加成
    private double warTaxBonusMultiplier = 1.0;

    // 资源产出惩罚
    private double warResourcePenalty = 0.5;

    // 战争债券利率
    private double warBondInterestRate = 0.05;

    // 战后和平期（天）
    private int postWarPeaceDays = 7;

    // 战争公告消息
    private Map<String, String> announcementMessages = new ConcurrentHashMap<>();

    // 战争通知开关
    private boolean enableWarAnnouncements = true;
    private boolean enableWarTitleNotifications = true;

    public WarConfig(Plugin plugin) {
        this(plugin, plugin.getLogger());
    }

    public WarConfig(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        loadConfig();
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        loadConfig();
    }

    /**
     * 从配置文件加载设置
     */
    private void loadConfig() {
        // 加载 war 配置节
        preparationHours = plugin.getConfig().getInt("war.preparation_hours", 24);
        minMobilizationRate = plugin.getConfig().getDouble("war.min_mobilization_rate", 0.1);
        maxWarDurationDays = plugin.getConfig().getInt("war.max_duration_days", 30);

        // 宣战设置
        declarationCooldownHours = plugin.getConfig().getInt("war.declaration_cooldown_hours", 12);
        maxWarReparationsRatio = plugin.getConfig().getDouble("war.max_reparations_ratio", 0.5);
        minDeclarationCost = plugin.getConfig().getDouble("war.min_declaration_cost", 1000.0);
        maxSimultaneousWars = plugin.getConfig().getInt("war.max_simultaneous_wars", 3);

        // 胜利条件
        String victoryType = plugin.getConfig().getString("war.victory_condition", "OCCUPATION");
        try {
            victoryCondition = VictoryCondition.valueOf(victoryType);
        } catch (IllegalArgumentException e) {
            victoryCondition = VictoryCondition.OCCUPATION;
        }
        occupationVictoryDays = plugin.getConfig().getInt("war.occupation_victory_days", 7);
        conquestVictoryDays = plugin.getConfig().getInt("war.conquest_victory_days", 3);

        // 投降设置
        surrenderAllowed = plugin.getConfig().getBoolean("war.surrender_allowed", true);
        surrenderReparationsRatio = plugin.getConfig().getDouble("war.surrender_reparations_ratio", 0.2);

        // 联盟设置
        allianceWarEnabled = plugin.getConfig().getBoolean("war.alliance_war_enabled", true);
        neutralDeclarationPenalty = plugin.getConfig().getDouble("war.neutral_declaration_penalty", 1.5);

        // 战争影响
        warTaxBonusMultiplier = plugin.getConfig().getDouble("war.tax_bonus_multiplier", 1.0);
        warResourcePenalty = plugin.getConfig().getDouble("war.resource_penalty", 0.5);
        warBondInterestRate = plugin.getConfig().getDouble("war.bond_interest_rate", 0.05);

        // 和平期
        postWarPeaceDays = plugin.getConfig().getInt("war.post_war_peace_days", 7);

        // 通知设置
        enableWarAnnouncements = plugin.getConfig().getBoolean("war.notifications.announcements", true);
        enableWarTitleNotifications = plugin.getConfig().getBoolean("war.notifications.title_notifications", true);

        // 加载自定义消息
        loadCustomMessages();

        logger.info("WarConfig loaded: preparation=" + preparationHours + "h, maxDuration=" + maxWarDurationDays + "d");
    }

    /**
     * 加载自定义战争消息
     */
    private void loadCustomMessages() {
        announcementMessages.clear();

        // 宣战消息
        announcementMessages.put("declaration",
            plugin.getConfig().getString("war.messages.declaration", "§c§l[战争] §6%attacker%§e 向 §6%defender%§e 宣战！"));

        // 战争开始消息
        announcementMessages.put("start",
            plugin.getConfig().getString("war.messages.start", "§c§l[战争] §e战争正式开始！准备时间结束。"));

        // 战争结束消息
        announcementMessages.put("end",
            plugin.getConfig().getString("war.messages.end", "§a§l[和平] §e战争已结束，%winner%§e 获胜！"));

        // 投降消息
        announcementMessages.put("surrender",
            plugin.getConfig().getString("war.messages.surrender", "§e§l[投降] §6%surrenderer%§e 向 §6%receiver%§e 投降。"));

        // 和平协议消息
        announcementMessages.put("peace",
            plugin.getConfig().getString("war.messages.peace", "§a§l[和平] §e和平协议已签订。"));
    }

    // ==================== Getters ====================

    public int getPreparationHours() {
        return preparationHours;
    }

    public double getMinMobilizationRate() {
        return minMobilizationRate;
    }

    public int getMaxWarDurationDays() {
        return maxWarDurationDays;
    }

    public int getDeclarationCooldownHours() {
        return declarationCooldownHours;
    }

    public double getMaxWarReparationsRatio() {
        return maxWarReparationsRatio;
    }

    public double getMinDeclarationCost() {
        return minDeclarationCost;
    }

    public int getMaxSimultaneousWars() {
        return maxSimultaneousWars;
    }

    public VictoryCondition getVictoryCondition() {
        return victoryCondition;
    }

    public int getOccupationVictoryDays() {
        return occupationVictoryDays;
    }

    public int getConquestVictoryDays() {
        return conquestVictoryDays;
    }

    public boolean isSurrenderAllowed() {
        return surrenderAllowed;
    }

    public double getSurrenderReparationsRatio() {
        return surrenderReparationsRatio;
    }

    public boolean isAllianceWarEnabled() {
        return allianceWarEnabled;
    }

    public double getNeutralDeclarationPenalty() {
        return neutralDeclarationPenalty;
    }

    public double getWarTaxBonusMultiplier() {
        return warTaxBonusMultiplier;
    }

    public double getWarResourcePenalty() {
        return warResourcePenalty;
    }

    public double getWarBondInterestRate() {
        return warBondInterestRate;
    }

    public int getPostWarPeaceDays() {
        return postWarPeaceDays;
    }

    public boolean isEnableWarAnnouncements() {
        return enableWarAnnouncements;
    }

    public boolean isEnableWarTitleNotifications() {
        return enableWarTitleNotifications;
    }

    public String getAnnouncementMessage(String key) {
        return announcementMessages.getOrDefault(key, "");
    }

    public Map<String, String> getAllAnnouncementMessages() {
        return new ConcurrentHashMap<>(announcementMessages);
    }

    // ==================== 配置验证 ====================

    /**
     * 验证配置完整性
     */
    public boolean validate() {
        if (preparationHours < 0) {
            logger.warning("Invalid war.preparation_hours: " + preparationHours);
            return false;
        }
        if (minMobilizationRate < 0 || minMobilizationRate > 1) {
            logger.warning("Invalid war.min_mobilization_rate: " + minMobilizationRate);
            return false;
        }
        if (maxWarDurationDays <= 0) {
            logger.warning("Invalid war.max_duration_days: " + maxWarDurationDays);
            return false;
        }
        if (declarationCooldownHours < 0) {
            logger.warning("Invalid war.declaration_cooldown_hours: " + declarationCooldownHours);
            return false;
        }
        return true;
    }

    // ==================== 枚举类型 ====================

    /**
     * 胜利条件类型
     */
    public enum VictoryCondition {
        OCCUPATION,      // 占领胜利 - 占领对方首都一定天数
        CONQUEST,         // 征服胜利 - 消灭对方所有领土
        ELIMINATION,     // 歼灭胜利 - 消灭对方所有成员
        NEGOTIATION,     // 谈判胜利 - 消耗战到一方投降
        TIMEOUT          // 超时平局
    }

    // ==================== 辅助方法 ====================

    /**
     * 计算战争准备时间（毫秒）
     */
    public long getPreparationMillis() {
        return (long) preparationHours * 60 * 60 * 1000;
    }

    /**
     * 计算最大战争持续时间（毫秒）
     */
    public long getMaxWarDurationMillis() {
        return (long) maxWarDurationDays * 24 * 60 * 60 * 1000;
    }

    /**
     * 计算宣战冷却时间（毫秒）
     */
    public long getDeclarationCooldownMillis() {
        return (long) declarationCooldownHours * 60 * 60 * 1000;
    }

    /**
     * 计算和平期时间（毫秒）
     */
    public long getPostWarPeaceMillis() {
        return (long) postWarPeaceDays * 24 * 60 * 60 * 1000;
    }

    /**
     * 计算赔款金额
     */
    public double calculateReparations(double defenderBalance, boolean isSurrender) {
        double ratio = isSurrender ? surrenderReparationsRatio : maxWarReparationsRatio;
        return Math.min(defenderBalance * ratio, defenderBalance);
    }
}
