package dev.starcore.starcore.event.random;

import org.bukkit.entity.Player;

import java.util.*;

/**
 * 事件稀有度等级系统
 * 定义事件的稀有程度，影响触发概率和出现条件
 */
public enum EventRarity {
    // 普通事件 - 最常见，影响小
    COMMON("§7普通", "普通事件", 0.60, 0.0010, 10),

    // 不常见事件 - 偶发，有一定影响
    UNCOMMON("§a稀有", "稀有事件", 0.25, 0.0005, 30),

    // 罕见事件 - 很少出现，有较大影响
    RARE("§b罕见", "罕见事件", 0.10, 0.0002, 60),

    // 史诗事件 - 极少出现，影响巨大
    EPIC("§5史诗", "史诗事件", 0.04, 0.00008, 120),

    // 传说事件 - 几乎不出现，改变游戏格局
    LEGENDARY("§6传说", "传说事件", 0.01, 0.00002, 300);

    private final String prefix;           // 显示前缀
    private final String displayName;      // 显示名称
    private final double baseWeight;      // 基础权重
    private final double baseProbability;  // 基础触发概率(每次检查)
    private final int minStageLevel;      // 最低需要的发展等级

    EventRarity(String prefix, String displayName, double weight, double probability, int minStageLevel) {
        this.prefix = prefix;
        this.displayName = displayName;
        this.baseWeight = weight;
        this.baseProbability = probability;
        this.minStageLevel = minStageLevel;
    }

    public String getPrefix() { return prefix; }
    public String getDisplayName() { return displayName; }
    public double getBaseWeight() { return baseWeight; }
    public double getBaseProbability() { return baseProbability; }
    public int getMinStageLevel() { return minStageLevel; }

    /**
     * 根据稀有度获取颜色代码
     */
    public String getColorCode() {
        return switch (this) {
            case COMMON -> "§7";
            case UNCOMMON -> "§a";
            case RARE -> "§b";
            case EPIC -> "§5";
            case LEGENDARY -> "§6";
        };
    }

    /**
     * 计算实际触发概率
     * 基于稀有度、在线玩家数、国家发展度进行调整
     */
    public double calculateActualProbability(NationEventContext context, int onlinePlayerCount) {
        double prob = baseProbability;

        // 国家发展阶段加成：越高级越容易触发稀有事件
        if (context != null) {
            int stageLevel = context.getStageLevel();

            // 发展阶段对稀有事件概率的倍率
            if (stageLevel < minStageLevel) {
                return 0; // 未达到最低等级，不触发
            }

            // 发展阶段加成：每超出一级，概率提高
            int levelDiff = stageLevel - minStageLevel;
            double stageBonus = 1.0 + (levelDiff * 0.15);
            prob *= stageBonus;

            // 国家实力加成：强大的国家更容易遇到重大事件
            double powerMultiplier = context.getPowerMultiplier();
            prob *= (1.0 + powerMultiplier * 0.5);

            // 战争状态加成：战争期间事件更频繁
            if (context.isAtWar()) {
                prob *= 2.0;
            }

            // 繁荣度加成：高繁荣度国家事件更丰富
            if (context.getProsperityLevel() >= 4) {
                prob *= 1.5;
            }
        }

        // 全服玩家数量调整：人少时稀有事件概率降低
        if (onlinePlayerCount < 5) {
            prob *= (onlinePlayerCount / 5.0);
        } else if (onlinePlayerCount > 20) {
            prob *= 1.5; // 人多时稀有事件稍微提高
        }

        return Math.min(prob, 0.1); // 最高10%概率
    }

    /**
     * 获取显示名称（带颜色）
     */
    public String getColoredName() {
        return prefix + displayName;
    }

    /**
     * 获取稀有度的音效类型
     */
    public String getSoundEffect() {
        return switch (this) {
            case COMMON -> "entity.experience_orb.pickup";
            case UNCOMMON -> "ui.toast.challenge_complete";
            case RARE -> "entity.wither.break";
            case EPIC -> "entity.ender_dragon.growl";
            case LEGENDARY -> "entity.ender_dragon.flap";
        };
    }
}
