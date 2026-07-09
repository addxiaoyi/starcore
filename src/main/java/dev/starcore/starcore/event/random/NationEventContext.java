package dev.starcore.starcore.event.random;

import java.util.concurrent.ThreadLocalRandom;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.module.war.WarService;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * 国家事件上下文
 * 包含所有影响事件触发的国家状态数据
 */
public class NationEventContext {
    // 国家基础信息
    private final NationId nationId;
    private final String nationName;
    private final int memberCount;
    private final int territoryChunks;
    private final int treasuryBalance;
    private final int totalPower;

    // 发展阶段（通过多种指标计算）
    private final int stageLevel;

    // 状态标志
    private final boolean atWar;
    private final boolean hasAlly;
    private final boolean isProsperous;
    private final int prosperityLevel;
    private final int technologyLevel;
    private final int armySize;

    // 在线玩家
    private final List<Player> onlinePlayers;

    // 历史统计
    private final int recentWars;
    private final int recentEvents;
    private final long lastEventTime;

    // 特殊状态
    private final boolean underSiege;
    private final boolean economicBoom;
    private final boolean hasFamine;

    private NationEventContext(Builder builder) {
        this.nationId = builder.nationId;
        this.nationName = builder.nationName;
        this.memberCount = builder.memberCount;
        this.territoryChunks = builder.territoryChunks;
        this.treasuryBalance = builder.treasuryBalance;
        this.totalPower = builder.totalPower;
        this.stageLevel = builder.stageLevel;
        this.atWar = builder.atWar;
        this.hasAlly = builder.hasAlly;
        this.isProsperous = builder.prosperous;
        this.prosperityLevel = builder.prosperityLevel;
        this.technologyLevel = builder.technologyLevel;
        this.armySize = builder.armySize;
        this.onlinePlayers = builder.onlinePlayers;
        this.recentWars = builder.recentWars;
        this.recentEvents = builder.recentEvents;
        this.lastEventTime = builder.lastEventTime;
        this.underSiege = builder.underSiege;
        this.economicBoom = builder.economicBoom;
        this.hasFamine = builder.hasFamine;
    }

    // ==================== 计算属性 ====================

    /**
     * 获取发展阶段等级 (1-10)
     * 综合考虑领土、人口、财富、科技、军事
     */
    public int getStageLevel() {
        return stageLevel;
    }

    /**
     * 获取国家实力倍率 (0.5 - 3.0)
     * 影响稀有事件触发概率
     */
    public double getPowerMultiplier() {
        return Math.min(3.0, Math.max(0.5, totalPower / 100.0));
    }

    /**
     * 获取综合国力评分
     */
    public int getTotalPower() {
        return totalPower;
    }

    /**
     * 获取事件经验值
     * 稀有事件会增加这个值
     */
    public int getEventExperience() {
        return (int) (stageLevel * 10 + treasuryBalance / 1000 + armySize);
    }

    /**
     * 检查是否应该显示事件通知
     * 新手国家事件更少、更温和
     */
    public boolean shouldShowEvent() {
        // 阶段1-2的国家很少看到事件
        if (stageLevel <= 2) {
            return ThreadLocalRandom.current().nextDouble() < 0.3; // 只有30%概率显示
        }
        // 阶段3-5正常
        if (stageLevel <= 5) {
            return ThreadLocalRandom.current().nextDouble() < 0.7;
        }
        // 阶段6+ 完整显示
        return true;
    }

    // ==================== 事件类型偏好 ====================

    /**
     * 获取该国家倾向的事件类型
     */
    public EventRarity getPreferredRarity() {
        // 低级国家偏向普通事件
        if (stageLevel <= 3) {
            if (ThreadLocalRandom.current().nextDouble() < 0.7) return EventRarity.COMMON;
            return EventRarity.UNCOMMON;
        }

        // 中级国家
        if (stageLevel <= 6) {
            double r = ThreadLocalRandom.current().nextDouble();
            if (r < 0.5) return EventRarity.COMMON;
            if (r < 0.8) return EventRarity.UNCOMMON;
            if (r < 0.95) return EventRarity.RARE;
            return EventRarity.EPIC;
        }

        // 高级国家
        if (stageLevel <= 8) {
            double r = ThreadLocalRandom.current().nextDouble();
            if (r < 0.3) return EventRarity.COMMON;
            if (r < 0.5) return EventRarity.UNCOMMON;
            if (r < 0.75) return EventRarity.RARE;
            if (r < 0.95) return EventRarity.EPIC;
            return EventRarity.LEGENDARY;
        }

        // 顶级国家 - 传说事件
        double r = ThreadLocalRandom.current().nextDouble();
        if (r < 0.2) return EventRarity.COMMON;
        if (r < 0.35) return EventRarity.UNCOMMON;
        if (r < 0.55) return EventRarity.RARE;
        if (r < 0.8) return EventRarity.EPIC;
        return EventRarity.LEGENDARY;
    }

    /**
     * 获取适合该国家的事件类型集合
     */
    public List<EventRarity> getAvailableRarities() {
        List<EventRarity> available = new ArrayList<>();
        for (EventRarity rarity : EventRarity.values()) {
            if (stageLevel >= rarity.getMinStageLevel()) {
                available.add(rarity);
            }
        }
        return available;
    }

    // ==================== Getters ====================

    public NationId getNationId() { return nationId; }
    public String getNationName() { return nationName; }
    public int getMemberCount() { return memberCount; }
    public int getTerritoryChunks() { return territoryChunks; }
    public int getTreasuryBalance() { return treasuryBalance; }
    public boolean isAtWar() { return atWar; }
    public boolean hasAlly() { return hasAlly; }
    public boolean isProsperous() { return isProsperous; }
    public int getProsperityLevel() { return prosperityLevel; }
    public int getTechnologyLevel() { return technologyLevel; }
    public int getArmySize() { return armySize; }
    public List<Player> getOnlinePlayers() { return onlinePlayers; }
    public int getOnlinePlayerCount() { return onlinePlayers.size(); }
    public int getRecentWars() { return recentWars; }
    public int getRecentEvents() { return recentEvents; }
    public long getLastEventTime() { return lastEventTime; }
    public boolean isUnderSiege() { return underSiege; }
    public boolean isEconomicBoom() { return economicBoom; }
    public boolean hasFamine() { return hasFamine; }

    /**
     * 计算距离上次事件的时间（分钟）
     */
    public long getMinutesSinceLastEvent() {
        return (System.currentTimeMillis() - lastEventTime) / 60000;
    }

    /**
     * 检查是否可以触发稀有事件
     */
    public boolean canTriggerRarity(EventRarity rarity) {
        // 最低等级检查
        if (stageLevel < rarity.getMinStageLevel()) {
            return false;
        }

        // 冷却检查
        int cooldownMinutes = rarity.getMinStageLevel() * 30; // 等级越高冷却越短
        if (getMinutesSinceLastEvent() < cooldownMinutes) {
            return false;
        }

        // 最近事件过多时降低稀有事件概率
        if (recentEvents > 5 && ThreadLocalRandom.current().nextDouble() > 0.5) {
            return false;
        }

        return true;
    }

    /**
     * 获取国家描述
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(nationName);
        sb.append(" [阶段").append(stageLevel).append("]");

        if (atWar) sb.append(" ⚔️交战中");
        if (hasAlly) sb.append(" 🤝同盟中");
        if (isProsperous) sb.append(" 💰繁荣中");
        if (underSiege) sb.append(" 🔥围困中");
        if (economicBoom) sb.append(" 📈经济繁荣");
        if (hasFamine) sb.append(" ⚠️饥荒中");

        return sb.toString();
    }

    // ==================== Builder ====================

    public static class Builder {
        private NationId nationId;
        private String nationName = "";
        private int memberCount = 1;
        private int territoryChunks = 0;
        private int treasuryBalance = 0;
        private int totalPower = 0;
        private int stageLevel = 1;
        private boolean atWar = false;
        private boolean hasAlly = false;
        private boolean prosperous = false;
        private int prosperityLevel = 1;
        private int technologyLevel = 1;
        private int armySize = 0;
        private List<Player> onlinePlayers = new ArrayList<>();
        private int recentWars = 0;
        private int recentEvents = 0;
        private long lastEventTime = 0;
        private boolean underSiege = false;
        private boolean economicBoom = false;
        private boolean hasFamine = false;

        public Builder nationId(NationId id) { this.nationId = id; return this; }
        public Builder nationName(String name) { this.nationName = name; return this; }
        public Builder memberCount(int count) { this.memberCount = count; return this; }
        public Builder territoryChunks(int chunks) { this.territoryChunks = chunks; return this; }
        public Builder treasuryBalance(int balance) { this.treasuryBalance = balance; return this; }
        public Builder totalPower(int power) { this.totalPower = power; return this; }
        public Builder atWar(boolean atWar) { this.atWar = atWar; return this; }
        public Builder hasAlly(boolean hasAlly) { this.hasAlly = hasAlly; return this; }
        public Builder prosperityLevel(int level) { this.prosperityLevel = level; return this; }
        public Builder technologyLevel(int level) { this.technologyLevel = level; return this; }
        public Builder armySize(int size) { this.armySize = size; return this; }
        public Builder onlinePlayers(List<Player> players) { this.onlinePlayers = players; return this; }
        public Builder recentWars(int count) { this.recentWars = count; return this; }
        public Builder recentEvents(int count) { this.recentEvents = count; return this; }
        public Builder lastEventTime(long time) { this.lastEventTime = time; return this; }
        public Builder underSiege(boolean under) { this.underSiege = under; return this; }
        public Builder economicBoom(boolean boom) { this.economicBoom = boom; return this; }
        public Builder hasFamine(boolean famine) { this.hasFamine = famine; return this; }

        /**
         * 自动计算发展阶段
         */
        public Builder calculateStageLevel() {
            // 综合评分公式
            int score = 0;
            score += Math.min(memberCount, 50) / 5;           // 人口贡献
            score += Math.min(territoryChunks, 100) / 10;      // 领土贡献
            score += Math.min(treasuryBalance / 10000, 30);    // 财富贡献
            score += prosperityLevel * 3;                     // 繁荣贡献
            score += technologyLevel * 2;                   // 科技贡献
            score += Math.min(armySize / 10, 20);           // 军事贡献

            this.stageLevel = Math.min(10, Math.max(1, score / 3));
            return this;
        }

        /**
         * 自动计算总国力
         */
        public Builder calculateTotalPower() {
            this.totalPower = (int) (
                memberCount * 5 +
                territoryChunks * 2 +
                treasuryBalance / 1000 +
                armySize * 3 +
                prosperityLevel * 20 +
                technologyLevel * 15
            );
            return this;
        }

        /**
         * 检查繁荣状态
         */
        public Builder checkProsperity() {
            this.prosperous = prosperityLevel >= 5 && treasuryBalance > 10000;
            return this;
        }

        public NationEventContext build() {
            if (nationName == null || nationName.isEmpty()) {
                nationName = "未知国家";
            }
            calculateStageLevel();
            calculateTotalPower();
            checkProsperity();
            return new NationEventContext(this);
        }
    }
}
