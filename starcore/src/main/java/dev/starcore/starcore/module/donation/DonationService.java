package dev.starcore.starcore.module.donation;

import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 献金服务接口
 * 玩家可以向国家献金，支持多种献金等级和奖励
 */
public interface DonationService {

    // ==================== 献金操作 ====================

    /**
     * 玩家向国家献金
     * @param playerId 玩家ID
     * @param nationId 目标国家ID
     * @param amount 献金额（必须为正数）
     * @return 献金记录
     */
    DonationResult donate(UUID playerId, NationId nationId, BigDecimal amount);

    /**
     * 玩家向国家献金（带消息）
     * @param playerId 玩家ID
     * @param nationId 目标国家ID
     * @param amount 献金额
     * @param message 附言
     * @return 献金记录
     */
    DonationResult donate(UUID playerId, NationId nationId, BigDecimal amount, String message);

    /**
     * 获取玩家的献金记录
     */
    List<DonationRecord> getPlayerDonations(UUID playerId);

    /**
     * 获取玩家的献金记录（分页）
     * @param playerId 玩家ID
     * @param limit 最大返回数量
     * @param offset 偏移量
     */
    List<DonationRecord> getPlayerDonations(UUID playerId, int limit, int offset);

    /**
     * 获取国家收到的所有献金记录
     */
    List<DonationRecord> getNationDonations(NationId nationId);

    /**
     * 获取国家收到的所有献金记录（分页）
     * @param nationId 国家ID
     * @param limit 最大返回数量
     * @param offset 偏移量
     */
    List<DonationRecord> getNationDonations(NationId nationId, int limit, int offset);

    /**
     * 获取玩家对某个国家的总献金额
     */
    BigDecimal getTotalDonations(UUID playerId, NationId nationId);

    /**
     * 获取玩家总献金额（跨所有国家）
     */
    BigDecimal getTotalDonations(UUID playerId);

    /**
     * 获取国家收到的总献金额
     */
    BigDecimal getTotalDonations(NationId nationId);

    /**
     * 获取玩家献金排名
     * @param nationId 国家ID
     * @param limit 返回前N名
     * @return 排名列表 (玩家ID, 总金额, 排名)
     */
    List<DonationRankingEntry> getDonationRanking(NationId nationId, int limit);

    /**
     * 获取玩家的献金排名
     */
    Optional<Integer> getPlayerRanking(UUID playerId, NationId nationId);

    // ==================== 献金等级 ====================

    /**
     * 获取玩家的献金等级
     */
    DonationTier getPlayerTier(UUID playerId);

    /**
     * 获取玩家的献金等级（针对特定国家）
     */
    DonationTier getPlayerTier(UUID playerId, NationId nationId);

    /**
     * 获取所有献金等级配置
     */
    Map<String, DonationTier> getAllTiers();

    /**
     * 获取指定等级配置
     */
    Optional<DonationTier> getTier(String tierId);

    /**
     * 根据累计献金额获取对应等级
     */
    DonationTier getTierForAmount(BigDecimal amount);

    /**
     * 获取升级到指定等级需要的金额
     */
    BigDecimal getAmountNeededForTier(UUID playerId, String tierId);

    // ==================== 献金奖励 ====================

    /**
     * 获取玩家可用的献金奖励
     */
    List<DonationReward> getAvailableRewards(UUID playerId, NationId nationId);

    /**
     * 领取献金奖励
     * @param playerId 玩家ID
     * @param nationId 国家ID
     * @param rewardId 奖励ID
     * @return 是否成功领取
     */
    boolean claimReward(UUID playerId, NationId nationId, String rewardId);

    /**
     * 检查奖励是否可领取
     */
    boolean isRewardClaimable(UUID playerId, NationId nationId, String rewardId);

    /**
     * 获取玩家的已领取奖励记录
     */
    List<ClaimedReward> getClaimedRewards(UUID playerId, NationId nationId);

    // ==================== 管理功能 ====================

    /**
     * 删除献金记录（管理员）
     */
    boolean deleteDonation(UUID donationId);

    /**
     * 获取服务摘要
     */
    String summary();

    // ==================== 数据记录 ====================

    /**
     * 献金记录
     */
    record DonationRecord(
        UUID id,
        UUID playerId,
        String playerName,
        NationId nationId,
        String nationName,
        BigDecimal amount,
        String message,
        DonationTier tier,
        Instant donatedAt
    ) {}

    /**
     * 献金结果
     */
    record DonationResult(
        boolean success,
        DonationRecord record,
        DonationTier previousTier,
        DonationTier newTier,
        boolean tierUpgraded,
        String errorMessage
    ) {
        public static DonationResult success(DonationRecord record, DonationTier previousTier, DonationTier newTier) {
            return new DonationResult(true, record, previousTier, newTier, !previousTier.id().equals(newTier.id()), null);
        }

        public static DonationResult failure(String errorMessage) {
            return new DonationResult(false, null, null, null, false, errorMessage);
        }
    }

    /**
     * 献金等级
     */
    record DonationTier(
        String id,
        String name,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        List<String> benefits,
        Map<String, Object> perks,
        int priority
    ) implements Comparable<DonationTier> {
        @Override
        public int compareTo(DonationTier other) {
            return Integer.compare(this.priority, other.priority);
        }
    }

    /**
     * 献金排名条目
     */
    record DonationRankingEntry(
        int rank,
        UUID playerId,
        String playerName,
        BigDecimal totalAmount,
        DonationTier tier
    ) {}

    /**
     * 献金奖励
     */
    record DonationReward(
        String id,
        String name,
        String description,
        DonationTier requiredTier,
        List<String> commands,
        Map<String, Object> items,
        boolean oneTime,
        boolean claimable
    ) {}

    /**
     * 已领取的奖励记录
     */
    record ClaimedReward(
        UUID id,
        UUID playerId,
        NationId nationId,
        String rewardId,
        Instant claimedAt
    ) {}
}
