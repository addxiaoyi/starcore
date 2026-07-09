package dev.starcore.starcore.event.random;

import java.util.concurrent.ThreadLocalRandom;
import java.util.*;

/**
 * 事件条件接口
 * 支持复合条件检查
 */
public interface EventCondition {

    boolean check(NationEventContext context);

    // ==================== 组合条件 ====================

    static EventCondition and(EventCondition... conditions) {
        return ctx -> {
            for (EventCondition cond : conditions) {
                if (!cond.check(ctx)) return false;
            }
            return true;
        };
    }

    static EventCondition or(EventCondition... conditions) {
        return ctx -> {
            for (EventCondition cond : conditions) {
                if (cond.check(ctx)) return true;
            }
            return false;
        };
    }

    static EventCondition not(EventCondition condition) {
        return ctx -> !condition.check(ctx);
    }

    // ==================== 基础条件工厂 ====================

    static EventCondition stageGreaterThan(int level) {
        return ctx -> ctx.getStageLevel() > level;
    }

    static EventCondition stageLessThan(int level) {
        return ctx -> ctx.getStageLevel() < level;
    }

    static EventCondition atWar() {
        return NationEventContext::isAtWar;
    }

    static EventCondition peaceful() {
        return ctx -> !ctx.isAtWar();
    }

    static EventCondition prosperous() {
        return NationEventContext::isProsperous;
    }

    static EventCondition hasAlly() {
        return NationEventContext::hasAlly;
    }

    static EventCondition underSiege() {
        return NationEventContext::isUnderSiege;
    }

    static EventCondition hasFamine() {
        return NationEventContext::hasFamine;
    }

    static EventCondition onlinePlayers(int min) {
        return ctx -> ctx.getOnlinePlayerCount() >= min;
    }

    static EventCondition onlinePlayers(int min, int max) {
        return ctx -> {
            int count = ctx.getOnlinePlayerCount();
            return count >= min && count <= max;
        };
    }

    static EventCondition treasuryGreaterThan(int amount) {
        return ctx -> ctx.getTreasuryBalance() > amount;
    }

    static EventCondition armySize(int min) {
        return ctx -> ctx.getArmySize() >= min;
    }

    static EventCondition territory(int minChunks) {
        return ctx -> ctx.getTerritoryChunks() >= minChunks;
    }

    static EventCondition recentEvents(int maxInMinutes) {
        return ctx -> ctx.getRecentEvents() < maxInMinutes;
    }

    // ==================== 稀有度条件 ====================

    static EventCondition rarity(EventRarity min) {
        return ctx -> ctx.getPreferredRarity().ordinal() >= min.ordinal();
    }

    static EventCondition rarityExact(EventRarity rarity) {
        return ctx -> ctx.getPreferredRarity() == rarity;
    }

    // ==================== 随机条件 ====================

    static EventCondition chance(double probability) {
        return ctx -> ThreadLocalRandom.current().nextDouble() < probability;
    }

    // 稀有度加权随机
    static EventCondition weightedChance(EventRarity rarity) {
        return ctx -> {
            // 普通事件60%，稀有度越高概率越低
            double baseProb = rarity.getBaseProbability();
            int stageBonus = Math.max(0, ctx.getStageLevel() - rarity.getMinStageLevel());
            double bonus = 1.0 + stageBonus * 0.1;
            return ThreadLocalRandom.current().nextDouble() < baseProb * bonus;
        };
    }
}
