package dev.starcore.starcore.social.simulation;
import java.util.Optional;
import java.util.ArrayList;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.social.simulation.SocialActivityService.*;
import dev.starcore.starcore.social.simulation.ReputationService;
import dev.starcore.starcore.social.simulation.ReputationService.ReputationReason;
import dev.starcore.starcore.social.simulation.ReputationService.ReputationDimension;
import dev.starcore.starcore.social.simulation.SocialInfluenceService.*;
import dev.starcore.starcore.title.TitleService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 社交活动奖励服务
 *
 * 管理活动相关的各种奖励:
 * - 活动参与奖励 (声望/影响力/金币)
 * - 活动主持人奖励
 * - 比赛排名奖励
 * - 活动连续参与奖励
 * - 特殊节日活动额外奖励
 */
public class ActivityRewardService {

    private final JavaPlugin plugin;
    private final ReputationService reputationService;
    private final SocialInfluenceService influenceService;
    private final EconomyService economyService;
    private final TitleService titleService;
    private final SocialActivityService activityService;

    // 连续参与记录: 玩家ID -> 活动类型 -> 连续天数
    private final Map<UUID, Map<SocialActivityType, Integer>> participationStreaks = new ConcurrentHashMap<>();

    // 连续参与最后参与日期: 玩家ID -> 活动类型 -> 最后参与日期
    private final Map<UUID, Map<SocialActivityType, LocalDate>> streakLastDates = new ConcurrentHashMap<>();

    // 今日参与记录: 玩家ID -> Set<活动ID>
    private final Map<UUID, Set<String>> todayParticipations = new ConcurrentHashMap<>();

    // 奖励历史记录: 玩家ID -> 奖励记录列表
    private final Map<UUID, List<RewardHistoryEntry>> rewardHistory = new ConcurrentHashMap<>();

    // 节日配置
    private final Map<HolidayType, HolidayConfig> holidayConfigs = new EnumMap<>(HolidayType.class);

    // 奖励配置文件
    private File configFile;
    private FileConfiguration config;

    // 奖励加成倍率 (可用于特殊活动期间全局加成)
    private double globalMultiplier = 1.0;

    public ActivityRewardService(
            JavaPlugin plugin,
            ReputationService reputationService,
            SocialInfluenceService influenceService,
            EconomyService economyService,
            TitleService titleService,
            SocialActivityService activityService) {
        this.plugin = plugin;
        this.reputationService = reputationService;
        this.influenceService = influenceService;
        this.economyService = economyService;
        this.titleService = titleService;
        this.activityService = activityService;

        loadConfig();
        loadHolidayConfigs();
        initializeDatabase();
    }

    // ==================== 奖励类型枚举 ====================

    /**
     * 活动奖励类型
     */
    public enum ActivityRewardType {
        REPUTATION("声望", "§b"),
        INFLUENCE("影响力", "§d"),
        MONEY("金币", "§6"),
        ITEMS("物品", "§e"),
        TITLE("称号", "§c");

        private final String name;
        private final String color;

        ActivityRewardType(String name, String color) {
            this.name = name;
            this.color = color;
        }

        public String getName() { return name; }
        public String color() { return color; }
    }

    /**
     * 节日类型
     */
    public enum HolidayType {
        NEW_YEAR("元旦", 1, 1, 7),           // 1月1日-7日
        SPRING_FESTIVAL("春节", 1, 15, 15),  // 正月初一
        VALENTINE("情人节", 2, 14, 1),       // 2月14日
        LABOR_DAY("劳动节", 5, 1, 7),         // 5月1日-7日
        NATIONAL_DAY("国庆节", 10, 1, 7),    // 10月1日-7日
        CHRISTMAS("圣诞节", 12, 25, 1),      // 12月25日
        ANNIVERSARY("周年庆", 0, 0, 7);      // 服务器周年, 月份可配置

        private final String name;
        private final int month;
        private final int day;
        private final int duration; // 持续天数

        HolidayType(String name, int month, int day, int duration) {
            this.name = name;
            this.month = month;
            this.day = day;
            this.duration = duration;
        }

        public String getName() { return name; }
        public int getMonth() { return month; }
        public int getDay() { return day; }
        public int getDuration() { return duration; }
    }

    /**
     * 排名等级
     */
    public enum RankingLevel {
        FIRST(1, "冠军", "§6🏆 ", 1.5),
        SECOND(2, "亚军", "§f🥈 ", 1.2),
        THIRD(3, "季军", "§f🥉 ", 1.1),
        TOP_TEN(10, "前十", "§b", 1.0),
        PARTICIPANT(0, "参与者", "§7", 0.8);

        private final int maxRank;
        private final String name;
        private final String color;
        private final double multiplier;

        RankingLevel(int maxRank, String name, String color, double multiplier) {
            this.maxRank = maxRank;
            this.name = name;
            this.color = color;
            this.multiplier = multiplier;
        }

        public int getMaxRank() { return maxRank; }
        public String getName() { return name; }
        public String color() { return color; }
        public double getMultiplier() { return multiplier; }

        public static RankingLevel fromRank(int rank) {
            if (rank == 1) return FIRST;
            if (rank == 2) return SECOND;
            if (rank == 3) return THIRD;
            if (rank <= 10) return TOP_TEN;
            return PARTICIPANT;
        }
    }

    // ==================== 奖励配置类 ====================

    public record RewardConfig(
        ActivityRewardType type,
        int baseAmount,
        double variance,       // 随机波动比例
        ReputationDimension dimension  // 声望维度 (仅声望类型使用)
    ) {
        public int calculateReward() {
            if (variance <= 0) return baseAmount;
            int min = (int)(baseAmount * (1 - variance));
            int max = (int)(baseAmount * (1 + variance));
            return ThreadLocalRandom.current().nextInt(min, max + 1);
        }
    }

    public record HolidayConfig(
        HolidayType holiday,
        double bonusMultiplier,
        List<RewardConfig> bonusRewards,
        String titleReward
    ) {
        /**
         * 获取整数配置（用于兼容 anniversary 配置）
         */
        public int getInt(String key, int defaultValue) {
            // Anniversary 配置的特殊处理
            if (key.contains("anniversary.month")) {
                return 6; // 默认月份
            } else if (key.contains("anniversary.day")) {
                return 1; // 默认日期
            }
            return defaultValue;
        }
    }

    public record ActivityRewardResult(
        UUID playerId,
        String activityId,
        Map<ActivityRewardType, Object> rewards,
        boolean hasHolidayBonus,
        int streakBonus,
        RankingLevel ranking
    ) {
        public String formatRewards() {
            StringBuilder sb = new StringBuilder();
            rewards.forEach((type, value) -> {
                sb.append(type.color()).append(type.getName()).append(": ")
                  .append(value).append(" ");
            });
            if (hasHolidayBonus) {
                sb.append("§a[节日加成] ");
            }
            if (streakBonus > 0) {
                sb.append("§d[连续").append(streakBonus).append("天] ");
            }
            return sb.toString();
        }
    }

    /**
     * 奖励历史记录条目
     */
    public record RewardHistoryEntry(
        String activityId,
        SocialActivityType activityType,
        ActivityRewardType rewardType,
        int amount,
        LocalDate timestamp,
        boolean hasHolidayBonus,
        int streakDays,
        RankingLevel ranking
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("activityId", activityId);
            map.put("activityType", activityType.name());
            map.put("rewardType", rewardType.name());
            map.put("amount", amount);
            map.put("timestamp", timestamp.toString());
            map.put("hasHolidayBonus", hasHolidayBonus);
            map.put("streakDays", streakDays);
            map.put("ranking", ranking != null ? ranking.name() : null);
            return map;
        }

        public static RewardHistoryEntry fromMap(Map<String, Object> map) {
            return new RewardHistoryEntry(
                (String) map.get("activityId"),
                SocialActivityType.valueOf((String) map.get("activityType")),
                ActivityRewardType.valueOf((String) map.get("rewardType")),
                ((Number) map.get("amount")).intValue(),
                LocalDate.parse((String) map.get("timestamp")),
                (Boolean) map.getOrDefault("hasHolidayBonus", false),
                ((Number) map.getOrDefault("streakDays", 0)).intValue(),
                map.get("ranking") != null ? RankingLevel.valueOf((String) map.get("ranking")) : null
            );
        }
    }

    // ==================== 核心奖励方法 ====================

    /**
     * 给予活动参与奖励
     *
     * @param playerId 玩家ID
     * @param activityId 活动ID
     * @return 奖励结果
     */
    public ActivityRewardResult grantParticipationReward(UUID playerId, String activityId) {
        SocialActivity activity = activityService.getActivity(activityId).orElse(null);
        if (activity == null) {
            return null;
        }

        Map<ActivityRewardType, Object> rewards = new EnumMap<>(ActivityRewardType.class);
        RankingLevel ranking = RankingLevel.PARTICIPANT;

        // 获取节日加成
        double holidayMultiplier = getHolidayMultiplier();
        boolean hasHolidayBonus = holidayMultiplier > 1.0;

        // 计算连续参与加成
        int streakDays = incrementParticipationStreak(playerId, activity.type());
        int streakBonus = calculateStreakBonus(streakDays);

        // 基础奖励
        RewardConfig repConfig = getBaseRewardConfig(activity.type(), ActivityRewardType.REPUTATION);
        RewardConfig infConfig = getBaseRewardConfig(activity.type(), ActivityRewardType.INFLUENCE);
        RewardConfig moneyConfig = getBaseRewardConfig(activity.type(), ActivityRewardType.MONEY);

        int repAmount = (int)((repConfig.calculateReward() + streakBonus) * holidayMultiplier * globalMultiplier);
        int infAmount = (int)((infConfig.calculateReward() + streakBonus / 2) * holidayMultiplier * globalMultiplier);
        int moneyAmount = (int)((moneyConfig.calculateReward() + streakBonus * 10) * holidayMultiplier * globalMultiplier);

        // 应用声望奖励
        if (repAmount > 0) {
            reputationService.modifyReputation(playerId, repAmount,
                ReputationReason.VOLUNTEER_WORK);
            rewards.put(ActivityRewardType.REPUTATION, repAmount);
        }

        // 应用影响力奖励
        if (infAmount > 0) {
            influenceService.addInfluence(playerId, infAmount, "活动参与: " + activity.name());
            rewards.put(ActivityRewardType.INFLUENCE, infAmount);
        }

        // 应用金币奖励
        if (moneyAmount > 0) {
            economyService.deposit(playerId, BigDecimal.valueOf(moneyAmount));
            rewards.put(ActivityRewardType.MONEY, moneyAmount);
        }

        // 物品奖励 (随机概率)
        if (shouldGrantItemReward(activity.type())) {
            ItemStack item = generateItemReward(activity.type());
            if (item != null) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    giveItemToPlayer(player, item);
                    rewards.put(ActivityRewardType.ITEMS, item.getType().name() + " x" + item.getAmount());
                }
            }
        }

        // 称号奖励 (连续参与达到一定天数)
        String title = checkAndGrantTitleReward(playerId, streakDays);
        if (title != null) {
            rewards.put(ActivityRewardType.TITLE, title);
        }

        // 记录今日参与
        recordTodayParticipation(playerId, activityId);

        // 记录奖励历史
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        for (Map.Entry<ActivityRewardType, Object> reward : rewards.entrySet()) {
            if (reward.getValue() instanceof Number amount) {
                addRewardHistory(playerId, activityId, activity.type(),
                    reward.getKey(), amount.intValue(), today, hasHolidayBonus, streakDays, ranking);
            }
        }

        return new ActivityRewardResult(playerId, activityId, rewards, hasHolidayBonus, streakDays, ranking);
    }

    /**
     * 给予活动主持人奖励
     *
     * @param hostId 主持人ID
     * @param activityId 活动ID
     * @param participantCount 实际参与人数
     * @return 奖励结果
     */
    public ActivityRewardResult grantHostReward(UUID hostId, String activityId, int participantCount) {
        SocialActivity activity = activityService.getActivity(activityId).orElse(null);
        if (activity == null) {
            return null;
        }

        Map<ActivityRewardType, Object> rewards = new EnumMap<>(ActivityRewardType.class);

        // 主持人基础奖励 (根据参与人数加成)
        double participationBonus = Math.min(participantCount / 10.0, 2.0); // 最多2倍
        double holidayMultiplier = getHolidayMultiplier();

        // 主持人声望奖励 (更多)
        int repAmount = (int)(15 * participationBonus * holidayMultiplier * globalMultiplier);
        reputationService.modifyReputation(hostId, repAmount, ReputationReason.HOST_EVENT);
        rewards.put(ActivityRewardType.REPUTATION, repAmount);

        // 主持人影响力奖励
        int infAmount = (int)(20 * participationBonus * holidayMultiplier * globalMultiplier);
        influenceService.addInfluence(hostId, infAmount, "主持活动: " + activity.name());
        rewards.put(ActivityRewardType.INFLUENCE, infAmount);

        // 主持人金币奖励 (参与人数 * 基础金币)
        int moneyAmount = (int)(50 * participantCount * holidayMultiplier * globalMultiplier);
        economyService.deposit(hostId, BigDecimal.valueOf(moneyAmount));
        rewards.put(ActivityRewardType.MONEY, moneyAmount);

        // 主持特殊称号
        String hostTitle = "主持人_" + activity.type().getName();
        titleService.unlockTitle(hostId, hostTitle);
        rewards.put(ActivityRewardType.TITLE, hostTitle);

        // 记录
        Player player = Bukkit.getPlayer(hostId);
        if (player != null) {
            player.sendMessage("§6[活动奖励] §e你作为主持人获得奖励:");
            player.sendMessage(rewardsToString(rewards));
        }

        return new ActivityRewardResult(hostId, activityId, rewards, holidayMultiplier > 1.0, 0, RankingLevel.PARTICIPANT);
    }

    /**
     * 给予比赛排名奖励
     *
     * @param playerId 玩家ID
     * @param activityId 活动ID
     * @param rank 最终排名
     * @return 奖励结果
     */
    public ActivityRewardResult grantRankingReward(UUID playerId, String activityId, int rank) {
        RankingLevel ranking = RankingLevel.fromRank(rank);
        SocialActivity activity = activityService.getActivity(activityId).orElse(null);
        if (activity == null || activity.type() != SocialActivityType.COMPETITION) {
            return null;
        }

        Map<ActivityRewardType, Object> rewards = new EnumMap<>(ActivityRewardType.class);

        // 基础奖励配置
        int baseRep = 50;
        int baseInfluence = 30;
        int baseMoney = 200;

        // 排名加成
        double rankMultiplier = ranking.getMultiplier();
        double holidayMultiplier = getHolidayMultiplier();

        // 声望奖励
        int repAmount = (int)(baseRep * rankMultiplier * holidayMultiplier * globalMultiplier);
        reputationService.modifyReputation(playerId, repAmount, ReputationReason.WIN_BATTLE);
        rewards.put(ActivityRewardType.REPUTATION, repAmount);

        // 影响力奖励
        int infAmount = (int)(baseInfluence * rankMultiplier * holidayMultiplier * globalMultiplier);
        influenceService.addInfluence(playerId, infAmount, "比赛排名: " + ranking.getName());
        rewards.put(ActivityRewardType.INFLUENCE, infAmount);

        // 金币奖励 (根据排名大幅增加)
        int moneyAmount = (int)(baseMoney * rankMultiplier * 10 * holidayMultiplier * globalMultiplier);
        economyService.deposit(playerId, BigDecimal.valueOf(moneyAmount));
        rewards.put(ActivityRewardType.MONEY, moneyAmount);

        // 前三名特殊物品
        if (rank <= 3) {
            ItemStack trophy = createTrophy(ranking);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                giveItemToPlayer(player, trophy);
                rewards.put(ActivityRewardType.ITEMS, trophy.getType().name() + " x1");
            }
        }

        // 冠军专属称号
        if (rank == 1) {
            String championTitle = "冠军_" + getCompetitionTypeName(activity);
            titleService.unlockTitle(playerId, championTitle);
            rewards.put(ActivityRewardType.TITLE, championTitle);
        }

        // 通知玩家
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage("§6[比赛奖励] §e恭喜获得第" + rank + "名!");
            player.sendMessage(rewardsToString(rewards));
        }

        return new ActivityRewardResult(playerId, activityId, rewards, holidayMultiplier > 1.0, 0, ranking);
    }

    /**
     * 计算连续参与奖励
     *
     * @param playerId 玩家ID
     * @param activityType 活动类型
     * @return 连续天数
     */
    public int getConsecutiveParticipationDays(UUID playerId, SocialActivityType activityType) {
        return participationStreaks.getOrDefault(playerId, Map.of())
                .getOrDefault(activityType, 0);
    }

    /**
     * 检查连续参与是否达标称号
     *
     * @param playerId 玩家ID
     * @param streakDays 连续天数
     * @return 获得的称号ID, 无则为null
     */
    private String checkAndGrantTitleReward(UUID playerId, int streakDays) {
        String titleId = null;

        if (streakDays >= 30) {
            titleId = "streak_30_" + streakDays;
        } else if (streakDays >= 14) {
            titleId = "streak_14_" + streakDays;
        } else if (streakDays >= 7) {
            titleId = "streak_7_" + streakDays;
        }

        if (titleId != null) {
            titleService.unlockTitle(playerId, titleId);
        }

        return titleId;
    }

    // ==================== 节日活动 ====================

    /**
     * 检查当前是否为节日期间
     */
    public boolean isHolidayActive() {
        return getHolidayMultiplier() > 1.0;
    }

    /**
     * 获取当前节日类型
     */
    public Optional<HolidayType> getCurrentHoliday() {
        LocalDate now = LocalDate.now(ZoneId.systemDefault());

        for (HolidayConfig config : holidayConfigs.values()) {
            HolidayType holiday = config.holiday;

            if (holiday == HolidayType.ANNIVERSARY) {
                // 周年庆特殊处理（默认6月1日）
                int anniversaryMonth = config.getInt("holiday.anniversary.month", 6);
                int anniversaryDay = config.getInt("holiday.anniversary.day", 1);
                if (now.getMonthValue() == anniversaryMonth &&
                    now.getDayOfMonth() >= anniversaryDay &&
                    now.getDayOfMonth() < anniversaryDay + holiday.getDuration()) {
                    return Optional.of(holiday);
                }
            } else {
                if (now.getMonthValue() == holiday.getMonth() &&
                    now.getDayOfMonth() >= holiday.getDay() &&
                    now.getDayOfMonth() < holiday.getDay() + holiday.getDuration()) {
                    return Optional.of(holiday);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * 获取节日奖励倍率
     */
    public double getHolidayMultiplier() {
        Optional<HolidayType> holiday = getCurrentHoliday();
        if (holiday.isPresent()) {
            HolidayConfig config = holidayConfigs.get(holiday.get());
            if (config != null) {
                return config.bonusMultiplier();
            }
        }
        return 1.0;
    }

    /**
     * 获取节日加成奖励列表
     */
    public List<RewardConfig> getHolidayBonusRewards() {
        Optional<HolidayType> holiday = getCurrentHoliday();
        if (holiday.isPresent()) {
            HolidayConfig config = holidayConfigs.get(holiday.get());
            if (config != null) {
                return config.bonusRewards();
            }
        }
        return Collections.emptyList();
    }

    // ==================== 辅助方法 ====================

    private int incrementParticipationStreak(UUID playerId, SocialActivityType type) {
        // 检查今日是否已参与
        Set<String> today = todayParticipations.get(playerId);
        if (today != null && !today.isEmpty()) {
            // 如果今天已参与同类型活动,不增加连续天数
            return participationStreaks.getOrDefault(playerId, Map.of()).getOrDefault(type, 0);
        }

        // 验证并恢复连续记录（处理跨天情况）
        validateAndRestoreStreak(playerId, type);

        // 增加连续天数
        participationStreaks.computeIfAbsent(playerId, k -> new EnumMap<>(SocialActivityType.class));
        int current = participationStreaks.get(playerId).getOrDefault(type, 0);
        int newStreak = current + 1;
        participationStreaks.get(playerId).put(type, newStreak);

        // 更新最后参与日期
        streakLastDates.computeIfAbsent(playerId, k -> new EnumMap<>(SocialActivityType.class));
        streakLastDates.get(playerId).put(type, LocalDate.now(ZoneId.systemDefault()));

        return newStreak;
    }

    /**
     * 验证并恢复连续参与记录（检查跨天情况）
     */
    private void validateAndRestoreStreak(UUID playerId, SocialActivityType type) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate lastDate = streakLastDates.getOrDefault(playerId, Map.of()).get(type);

        if (lastDate == null) {
            // 从未参与过，直接设置为0
            participationStreaks.computeIfAbsent(playerId, k -> new EnumMap<>(SocialActivityType.class))
                              .put(type, 0);
            return;
        }

        if (lastDate.isBefore(today.minusDays(1))) {
            // 连续参与中断，清零
            participationStreaks.computeIfAbsent(playerId, k -> new EnumMap<>(SocialActivityType.class))
                              .put(type, 0);
        }
        // 如果是今天或昨天，连续记录保持不变
    }

    private int calculateStreakBonus(int streakDays) {
        if (streakDays >= 30) return 20;
        if (streakDays >= 14) return 10;
        if (streakDays >= 7) return 5;
        if (streakDays >= 3) return 2;
        return 0;
    }

    private void recordTodayParticipation(UUID playerId, String activityId) {
        todayParticipations.computeIfAbsent(playerId, k -> new HashSet<>()).add(activityId);
    }

    /**
     * 添加奖励历史记录
     */
    private void addRewardHistory(UUID playerId, String activityId, SocialActivityType activityType,
                                  ActivityRewardType rewardType, int amount, LocalDate timestamp,
                                  boolean hasHolidayBonus, int streakDays, RankingLevel ranking) {
        rewardHistory.computeIfAbsent(playerId, k -> new ArrayList<>());
        RewardHistoryEntry entry = new RewardHistoryEntry(
            activityId, activityType, rewardType, amount, timestamp,
            hasHolidayBonus, streakDays, ranking
        );
        rewardHistory.get(playerId).add(entry);

        // 限制历史记录数量（最多保留100条）
        if (rewardHistory.get(playerId).size() > 100) {
            rewardHistory.get(playerId).remove(0);
        }
    }

    private RewardConfig getBaseRewardConfig(SocialActivityType type, ActivityRewardType rewardType) {
        String path = "rewards." + type.name().toLowerCase() + "." + rewardType.name().toLowerCase();
        int base = config.getInt(path + ".base", getDefaultBaseReward(type, rewardType));
        double variance = config.getDouble(path + ".variance", 0.2);

        ReputationDimension dimension = ReputationDimension.CHARISMA;
        if (rewardType == ActivityRewardType.REPUTATION) {
            dimension = ReputationDimension.CHARISMA;
        }

        return new RewardConfig(rewardType, base, variance, dimension);
    }

    private int getDefaultBaseReward(SocialActivityType type, ActivityRewardType rewardType) {
        return switch (type) {
            case PARTY -> switch (rewardType) {
                case REPUTATION -> 5;
                case INFLUENCE -> 3;
                case MONEY -> 50;
                default -> 0;
            };
            case CELEBRATION -> switch (rewardType) {
                case REPUTATION -> 10;
                case INFLUENCE -> 8;
                case MONEY -> 100;
                default -> 0;
            };
            case COMPETITION -> switch (rewardType) {
                case REPUTATION -> 15;
                case INFLUENCE -> 10;
                case MONEY -> 150;
                default -> 0;
            };
            case GATHERING -> switch (rewardType) {
                case REPUTATION -> 8;
                case INFLUENCE -> 5;
                case MONEY -> 80;
                default -> 0;
            };
            case SOCIAL_MISSION -> switch (rewardType) {
                case REPUTATION -> 12;
                case INFLUENCE -> 7;
                case MONEY -> 120;
                default -> 0;
            };
            case NETWORKING -> switch (rewardType) {
                case REPUTATION -> 6;
                case INFLUENCE -> 10;
                case MONEY -> 60;
                default -> 0;
            };
        };
    }

    private boolean shouldGrantItemReward(SocialActivityType type) {
        double chance = config.getDouble("rewards.item_chance." + type.name().toLowerCase(), 0.1);
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    private ItemStack generateItemReward(SocialActivityType type) {
        return switch (type) {
            case PARTY -> new ItemStack(Material.COOKIE, ThreadLocalRandom.current().nextInt(1, 4));
            case CELEBRATION -> new ItemStack(Material.CAKE, 1);
            case COMPETITION -> new ItemStack(Material.GOLDEN_APPLE, ThreadLocalRandom.current().nextInt(1, 3));
            case GATHERING -> new ItemStack(Material.BREAD, ThreadLocalRandom.current().nextInt(2, 5));
            default -> new ItemStack(Material.APPLE, 1);
        };
    }

    private ItemStack createTrophy(RankingLevel ranking) {
        Material material = switch (ranking) {
            case FIRST -> Material.GOLDEN_APPLE;
            case SECOND -> Material.IRON_INGOT;
            case THIRD -> Material.COPPER_INGOT;
            default -> Material.DIAMOND;
        };
        return new ItemStack(material, 1);
    }

    private String getCompetitionTypeName(SocialActivity activity) {
        Object type = activity.metadata().get("type");
        return type != null ? type.toString() : "通用";
    }

    private void giveItemToPlayer(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        // 如果背包满了,在地上生成物品
        if (!overflow.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow.values().iterator().next());
        }
        player.sendMessage("§a[奖励] 获得物品: " + item.getAmount() + "x " + formatMaterialName(item.getType().name()));
    }

    private String formatMaterialName(String name) {
        StringBuilder formatted = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isUpperCase(c) && formatted.length() > 0) {
                formatted.append(' ').append(c);
            } else {
                formatted.append(c);
            }
        }
        return formatted.toString();
    }

    private String rewardsToString(Map<ActivityRewardType, Object> rewards) {
        StringBuilder sb = new StringBuilder();
        rewards.forEach((type, value) -> {
            sb.append(type.color()).append("  ")
              .append(type.getName()).append(": §f")
              .append(value).append("\n");
        });
        return sb.toString();
    }

    // ==================== 配置加载 ====================

    private void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "activity_rewards.yml");
        if (!configFile.exists()) {
            plugin.saveResource("activity_rewards.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void loadHolidayConfigs() {
        // 加载节日配置
        for (HolidayType holiday : HolidayType.values()) {
            double multiplier = config.getDouble("holidays." + holiday.name().toLowerCase() + ".multiplier", 1.0);
            List<RewardConfig> bonusRewards = new ArrayList<>();

            // 加载额外奖励
            if (config.contains("holidays." + holiday.name().toLowerCase() + ".bonus_rewards")) {
                for (String typeStr : config.getStringList("holidays." + holiday.name().toLowerCase() + ".bonus_rewards")) {
                    String[] parts = typeStr.split(":");
                    if (parts.length >= 2) {
                        ActivityRewardType type = ActivityRewardType.valueOf(parts[0]);
                        int amount = Integer.parseInt(parts[1]);
                        bonusRewards.add(new RewardConfig(type, amount, 0, ReputationDimension.CHARISMA));
                    }
                }
            }

            String titleReward = config.getString("holidays." + holiday.name().toLowerCase() + ".title_reward");

            holidayConfigs.put(holiday, new HolidayConfig(holiday, multiplier, bonusRewards, titleReward));
        }
    }

    private void initializeDatabase() {
        // 初始化并加载持久化数据
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // 加载 YAML 文件中的参与记录
            loadParticipationData();
            plugin.getLogger().info("活动参与数据已加载: " + participationStreaks.size() + " 名玩家的连续记录");
        });
    }

    // ==================== 持久化方法 ====================

    /**
     * 获取数据保存目录
     */
    private File getDataDirectory() {
        File dataDir = new File(plugin.getDataFolder(), "social-simulation");
        if (!dataDir.exists()) dataDir.mkdirs();
        return dataDir;
    }

    /**
     * 保存连续参与数据到磁盘
     */
    public void saveParticipationData() {
        saveStreaks();
        saveRewardHistory();
    }

    /**
     * 保存连续参与数据（只保存streaks部分）
     */
    public void saveStreaks() {
        File dataFile = new File(getDataDirectory(), "activity_participation.yml");
        FileConfiguration dataConfig = new YamlConfiguration();

        try {
            // 保存连续参与记录
            for (Map.Entry<UUID, Map<SocialActivityType, Integer>> entry : participationStreaks.entrySet()) {
                String playerPath = "streaks." + entry.getKey().toString();
                for (Map.Entry<SocialActivityType, Integer> streak : entry.getValue().entrySet()) {
                    dataConfig.set(playerPath + "." + streak.getKey().name(), streak.getValue());
                }
            }

            // 保存最后参与日期
            for (Map.Entry<UUID, Map<SocialActivityType, LocalDate>> entry : streakLastDates.entrySet()) {
                String playerPath = "streak_dates." + entry.getKey().toString();
                for (Map.Entry<SocialActivityType, LocalDate> date : entry.getValue().entrySet()) {
                    dataConfig.set(playerPath + "." + date.getKey().name(), date.getValue().toString());
                }
            }

            // 保存今日参与记录
            for (Map.Entry<UUID, Set<String>> entry : todayParticipations.entrySet()) {
                String playerPath = "today." + entry.getKey().toString();
                dataConfig.set(playerPath, new ArrayList<>(entry.getValue()));
            }

            // 保存全局奖励倍率
            dataConfig.set("global_multiplier", globalMultiplier);

            // 保存最后重置日期
            dataConfig.set("last_reset", LocalDate.now().toString());

            dataConfig.save(dataFile);
            plugin.getLogger().info("活动参与数据已保存: " + participationStreaks.size() + " 名玩家的连续记录");
        } catch (IOException e) {
            plugin.getLogger().warning("保存活动参与数据失败: " + e.getMessage());
        }
    }

    /**
     * 加载连续参与数据
     */
    public void loadParticipationData() {
        loadStreaks();
        loadRewardHistory();
    }

    /**
     * 加载连续参与数据（只加载streaks部分）
     */
    public void loadStreaks() {
        File dataDir = getDataDirectory();
        if (!dataDir.exists()) {
            plugin.getLogger().info("social-simulation 目录不存在,跳过活动参与数据加载");
            return;
        }
        File dataFile = new File(dataDir, "activity_participation.yml");
        if (!dataFile.exists()) {
            plugin.getLogger().info("活动参与数据文件不存在");
            return;
        }

        FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        try {
            // 加载连续参与记录
            if (dataConfig.contains("streaks")) {
                Map<String, Object> streaks = dataConfig.getConfigurationSection("streaks").getValues(false);
                for (Map.Entry<String, Object> entry : streaks.entrySet()) {
                    UUID playerId = UUID.fromString(entry.getKey());
                    Map<SocialActivityType, Integer> playerStreaks = new EnumMap<>(SocialActivityType.class);

                    Map<String, Object> streakData = dataConfig.getConfigurationSection("streaks." + entry.getKey()).getValues(false);
                    for (Map.Entry<String, Object> streak : streakData.entrySet()) {
                        try {
                            SocialActivityType type = SocialActivityType.valueOf(streak.getKey());
                            int days = ((Number) streak.getValue()).intValue();
                            playerStreaks.put(type, days);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("跳过无效的连续天数类型: " + streak.getKey() + " - " + e.getMessage());
                        }
                    }

                    if (!playerStreaks.isEmpty()) {
                        participationStreaks.put(playerId, playerStreaks);
                    }
                }
            }

            // 加载最后参与日期
            if (dataConfig.contains("streak_dates")) {
                Map<String, Object> dates = dataConfig.getConfigurationSection("streak_dates").getValues(false);
                for (Map.Entry<String, Object> entry : dates.entrySet()) {
                    UUID playerId = UUID.fromString(entry.getKey());
                    Map<SocialActivityType, LocalDate> playerDates = new EnumMap<>(SocialActivityType.class);

                    Map<String, Object> dateData = dataConfig.getConfigurationSection("streak_dates." + entry.getKey()).getValues(false);
                    for (Map.Entry<String, Object> date : dateData.entrySet()) {
                        try {
                            SocialActivityType type = SocialActivityType.valueOf(date.getKey());
                            LocalDate lastDate = LocalDate.parse((String) date.getValue());
                            playerDates.put(type, lastDate);

                            // 验证并恢复连续记录（检查跨天情况）
                            validateStreakOnLoad(playerId, type, lastDate);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("跳过无效的日期格式: " + date.getKey() + " - " + e.getMessage());
                        }
                    }

                    if (!playerDates.isEmpty()) {
                        streakLastDates.put(playerId, playerDates);
                    }
                }
            }

            // 加载今日参与记录
            String lastReset = dataConfig.getString("last_reset", "");
            String today = LocalDate.now().toString();

            if (lastReset.equals(today) && dataConfig.contains("today")) {
                Map<String, Object> todayData = dataConfig.getConfigurationSection("today").getValues(false);
                for (Map.Entry<String, Object> entry : todayData.entrySet()) {
                    UUID playerId = UUID.fromString(entry.getKey());
                    List<String> activities = dataConfig.getStringList("today." + entry.getKey());
                    todayParticipations.put(playerId, new HashSet<>(activities));
                }
            }
            // 如果不是今天,今日参与记录不加载

            // 加载全局奖励倍率
            globalMultiplier = dataConfig.getDouble("global_multiplier", 1.0);

            plugin.getLogger().info("活动参与数据已加载: " + participationStreaks.size() + " 名玩家的连续记录");
        } catch (Exception e) {
            plugin.getLogger().warning("加载活动参与数据失败: " + e.getMessage());
        }
    }

    /**
     * 加载时验证连续记录（检查是否中断）
     */
    private void validateStreakOnLoad(UUID playerId, SocialActivityType type, LocalDate lastDate) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate yesterday = today.minusDays(1);

        if (lastDate.isBefore(yesterday)) {
            // 连续参与中断，清零
            participationStreaks.computeIfAbsent(playerId, k -> new EnumMap<>(SocialActivityType.class))
                              .put(type, 0);
        }
    }

    /**
     * 保存奖励历史记录
     */
    public void saveRewardHistory() {
        File historyFile = new File(getDataDirectory(), "reward_history.yml");
        FileConfiguration historyConfig = new YamlConfiguration();

        try {
            int totalEntries = 0;
            for (Map.Entry<UUID, List<RewardHistoryEntry>> entry : rewardHistory.entrySet()) {
                String playerPath = "players." + entry.getKey().toString();
                List<Map<String, Object>> entries = entry.getValue().stream()
                    .map(RewardHistoryEntry::toMap)
                    .collect(Collectors.toList());
                historyConfig.set(playerPath, entries);
                totalEntries += entries.size();
            }

            historyConfig.set("last_saved", Instant.now().toString());
            historyConfig.set("total_entries", totalEntries);

            historyConfig.save(historyFile);
            plugin.getLogger().info("奖励历史已保存: " + totalEntries + " 条记录");
        } catch (IOException e) {
            plugin.getLogger().warning("保存奖励历史失败: " + e.getMessage());
        }
    }

    /**
     * 加载奖励历史记录
     */
    public void loadRewardHistory() {
        File historyFile = new File(getDataDirectory(), "reward_history.yml");
        if (!historyFile.exists()) {
            plugin.getLogger().info("奖励历史文件不存在");
            return;
        }

        FileConfiguration historyConfig = YamlConfiguration.loadConfiguration(historyFile);

        try {
            if (!historyConfig.contains("players")) {
                return;
            }

            Map<String, Object> players = historyConfig.getConfigurationSection("players").getValues(false);
            int loadedCount = 0;

            for (Map.Entry<String, Object> entry : players.entrySet()) {
                UUID playerId = UUID.fromString(entry.getKey());
                List<?> rawList = (List<?>) entry.getValue();
                List<RewardHistoryEntry> entries = new ArrayList<>();

                for (Object item : rawList) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) item;
                        try {
                            entries.add(RewardHistoryEntry.fromMap(map));
                        } catch (Exception e) {
                            plugin.getLogger().warning("跳过无效的奖励历史条目: " + e.getMessage());
                        }
                    }
                }

                if (!entries.isEmpty()) {
                    rewardHistory.put(playerId, entries);
                    loadedCount += entries.size();
                }
            }

            plugin.getLogger().info("奖励历史已加载: " + loadedCount + " 条记录");
        } catch (Exception e) {
            plugin.getLogger().warning("加载奖励历史失败: " + e.getMessage());
        }
    }

    /**
     * 获取玩家的奖励历史记录
     */
    public List<RewardHistoryEntry> getRewardHistory(UUID playerId) {
        return rewardHistory.getOrDefault(playerId, Collections.emptyList());
    }

    /**
     * 获取玩家奖励历史的摘要统计
     */
    public Map<ActivityRewardType, Integer> getRewardSummary(UUID playerId) {
        List<RewardHistoryEntry> history = rewardHistory.get(playerId);
        if (history == null || history.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<ActivityRewardType, Integer> summary = new EnumMap<>(ActivityRewardType.class);
        for (RewardHistoryEntry entry : history) {
            summary.merge(entry.rewardType(), entry.amount(), Integer::sum);
        }
        return summary;
    }

    /**
     * 设置全局奖励倍率 (用于特殊活动期间)
     */
    public void setGlobalMultiplier(double multiplier) {
        this.globalMultiplier = Math.max(0.1, Math.min(5.0, multiplier));
    }

    /**
     * 获取全局奖励倍率
     */
    public double getGlobalMultiplier() {
        return globalMultiplier;
    }

    /**
     * 重置今日参与记录 (每日调用)
     */
    public void resetDailyParticipations() {
        todayParticipations.clear();
    }
}
