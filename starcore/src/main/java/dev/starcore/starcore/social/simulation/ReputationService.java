package dev.starcore.starcore.social.simulation;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 声望服务接口
 *
 * 管理玩家的社会声望，包括:
 * - 整体声望值
 * - 各维度声望 (道德/能力/财富/魅力)
 * - 声望历史记录
 * - 声望变化事件
 */
public interface ReputationService {

    // ==================== 声望操作 ====================

    /**
     * 获取玩家整体声望
     */
    int getReputation(UUID playerId);

    /**
     * 获取玩家各维度声望
     */
    ReputationProfile getProfile(UUID playerId);

    /**
     * 修改声望值
     * @param playerId 玩家ID
     * @param amount 变化量 (正数增加, 负数减少)
     * @param reason 原因
     */
    CompletableFuture<Boolean> modifyReputation(UUID playerId, int amount, ReputationReason reason);

    /**
     * 增加道德声望 (做好事)
     */
    CompletableFuture<Void> addMoral(UUID playerId, int amount, String description);

    /**
     * 增加能力声望 (完成任务/战斗胜利)
     */
    CompletableFuture<Void> addAbility(UUID playerId, int amount, String description);

    /**
     * 增加财富声望 (捐赠/交易)
     */
    CompletableFuture<Void> addWealth(UUID playerId, int amount, String description);

    /**
     * 增加魅力声望 (社交/领导)
     */
    CompletableFuture<Void> addCharisma(UUID playerId, int amount, String description);

    // ==================== 声望等级 ====================

    /**
     * 获取声望等级
     */
    ReputationLevel getLevel(UUID playerId);

    /**
     * 获取声望等级名称
     */
    String getLevelName(UUID playerId);

    // ==================== 历史记录 ====================

    /**
     * 获取声望变化历史
     */
    Map<Long, ReputationChange> getHistory(UUID playerId, int limit);

    /**
     * 获取最近的变化
     */
    ReputationChange getLastChange(UUID playerId);

    // ==================== 查询 ====================

    /**
     * 获取某维度最高声望的玩家
     */
    UUID getTopPlayer(ReputationDimension dimension, int limit);

    /**
     * 获取玩家的声望排名
     */
    int getRank(UUID playerId, ReputationDimension dimension);

    /**
     * 检查玩家是否在特定阵营
     */
    boolean hasStanding(UUID playerId, ReputationStanding standing);

    // ==================== 数据类 ====================

    public enum ReputationDimension {
        MORAL("道德"),      // 善良行为
        ABILITY("能力"),     // 个人能力
        WEALTH("财富"),      // 经济实力
        CHARISMA("魅力");    // 社交影响力

        private final String displayName;
        ReputationDimension(String name) { this.displayName = name; }
        public String displayName() { return displayName; }
    }

    public enum ReputationLevel {
        RENEGADE(-100, "§4堕落者", "声名狼藉"),
        OUTCAST(-50, "§c被驱逐者", "不受欢迎"),
        COMMONER(0, "§7普通人", "默默无闻"),
        RESPECTED(50, "§a受尊敬", "德高望重"),
        ELITE(100, "§b精英", "社会栋梁"),
        LEGEND(200, "§6传说", "千古流芳");

        private final int minReputation;
        private final String color;
        private final String description;

        ReputationLevel(int min, String color, String desc) {
            this.minReputation = min;
            this.color = color;
            this.description = desc;
        }

        public int minReputation() { return minReputation; }
        public String color() { return color; }
        public String description() { return description; }

        public static ReputationLevel fromReputation(int rep) {
            for (ReputationLevel level : values()) {
                if (rep >= level.minReputation) return level;
            }
            return RENEGADE;
        }
    }

    public enum ReputationStanding {
        VILLAIN,      // 恶人
        NEUTRAL,      // 中立
        HERO,         // 英雄
        CHAMPION      // 冠军
    }

    public enum ReputationReason {
        // 道德
        HELP_PLAYER("帮助玩家", ReputationDimension.MORAL, 5),
        DONATE_CHARITY("慈善捐赠", ReputationDimension.MORAL, 10),
        VOLUNTEER_WORK("志愿服务", ReputationDimension.MORAL, 8),
        PROTECT_WEAK("保护弱者", ReputationDimension.MORAL, 15),
        LEAD_NATION("领导国家", ReputationDimension.CHARISMA, 12),

        // 能力
        WIN_BATTLE("战斗胜利", ReputationDimension.ABILITY, 10),
        COMPLETE_QUEST("完成任务", ReputationDimension.ABILITY, 5),
        WIN_DUEL("决斗胜利", ReputationDimension.ABILITY, 8),
        RESEARCH_TECH("研发科技", ReputationDimension.ABILITY, 15),

        // 财富
        DONATE_TREASURY("捐入国库", ReputationDimension.WEALTH, 20),
        TRADE_SUCCESS("成功交易", ReputationDimension.WEALTH, 3),
        HOST_EVENT("举办活动", ReputationDimension.WEALTH, 10),

        // 魅力
        RECRUIT_MEMBER("招募成员", ReputationDimension.CHARISMA, 8),
        MEDIATE_DISPUTE("调解纠纷", ReputationDimension.CHARISMA, 12),
        GIVE_GIFT("赠送礼物", ReputationDimension.CHARISMA, 5),

        // 负面
        ATTACK_PLAYER("攻击玩家", ReputationDimension.MORAL, -5),
        THEFT("盗窃", ReputationDimension.MORAL, -15),
        BETRAYAL("背叛", ReputationDimension.MORAL, -20),
        GRIEFING("破坏行为", ReputationDimension.MORAL, -25),
        SPAM("垃圾信息", ReputationDimension.CHARISMA, -3),

        // 自定义 (用于动态计算的声誉变动，如八卦影响)
        CUSTOM("社交事件", ReputationDimension.MORAL, 0);

        private final String description;
        private final ReputationDimension dimension;
        private final int baseAmount;

        ReputationReason(String desc, ReputationDimension dim, int amount) {
            this.description = desc;
            this.dimension = dim;
            this.baseAmount = amount;
        }

        public String description() { return description; }
        public ReputationDimension dimension() { return dimension; }
        public int baseAmount() { return baseAmount; }
    }

    record ReputationProfile(
        UUID playerId,
        int overallReputation,
        Map<ReputationDimension, Integer> dimensions,
        ReputationLevel level,
        long lastUpdated
    ) {}

    record ReputationChange(
        long timestamp,
        ReputationDimension dimension,
        int amount,
        int newValue,
        ReputationReason reason,
        String description,
        UUID sourcePlayer
    ) {}
}
