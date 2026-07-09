package dev.starcore.starcore.event.random;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * 高级事件效果器
 * 根据事件稀有度和国家发展阶段应用不同强度的效果
 */
public class AdvancedEventEffectApplier {

    /**
     * 根据稀有度和阶段调整效果值
     */
    public static double adjustEffect(double baseValue, EventRarity rarity, NationEventContext context) {
        if (context == null) {
            return baseValue;
        }

        double multiplier = rarity.getBaseWeight();  // 使用稀有度基础权重

        // 国家阶段加成
        double stageBonus = 1.0 + (context.getStageLevel() * 0.1);
        multiplier *= stageBonus;

        // 繁荣国家效果增强
        if (context.isProsperous()) {
            multiplier *= 1.2;
        }

        // 战争状态效果增强
        if (context.isAtWar()) {
            multiplier *= 1.5;
        }

        return baseValue * multiplier;
    }

    /**
     * 获取事件持续时间（tick）
     * 稀有事件持续更长时间
     */
    public static int getEffectDuration(int baseTicks, EventRarity rarity) {
        return (int) (baseTicks * rarity.getBaseWeight() * 60);
    }

    /**
     * 获取玩家可见效果消息
     */
    public static String getEffectMessage(String baseMessage, EventRarity rarity, NationEventContext context) {
        StringBuilder msg = new StringBuilder();

        // 添加稀有度标记
        msg.append(rarity.getPrefix()).append("[强度: ").append(rarity.getDisplayName()).append("] ");

        // 添加阶段标记（如果适用）
        if (context != null && context.getStageLevel() >= 5) {
            msg.append("§7[阶段").append(context.getStageLevel()).append("] ");
        }

        msg.append(baseMessage);

        return msg.toString();
    }

    /**
     * 判断是否显示详细效果
     * 高级国家/稀有事件显示更多细节
     */
    public static boolean shouldShowDetails(EventRarity rarity, NationEventContext context) {
        // 普通事件总是简单显示
        if (rarity == EventRarity.COMMON) {
            return false;
        }

        // 低级国家事件简单显示
        if (context != null && context.getStageLevel() <= 3) {
            return rarity.ordinal() >= EventRarity.EPIC.ordinal();
        }

        // 史诗及以上总是详细
        return rarity.ordinal() >= EventRarity.EPIC.ordinal();
    }
}
