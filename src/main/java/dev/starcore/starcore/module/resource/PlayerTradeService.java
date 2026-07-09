package dev.starcore.starcore.module.resource;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.model.TradeRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 玩家交易服务
 * 管理玩家间的直接交易和与市场的交易
 */
public interface PlayerTradeService {
    /**
     * 创建玩家间直接交易请求
     */
    Optional<PlayerTradeOffer> createTradeOffer(UUID initiatorId, NationId initiatorNationId,
                                                UUID targetId, NationId targetNationId,
                                                String resourceId, long amount, double pricePerUnit,
                                                long expirySeconds);

    /**
     * 接受交易请求
     */
    Optional<TradeRecord> acceptTrade(UUID offerId);

    /**
     * 拒绝交易请求
     */
    boolean rejectTrade(UUID offerId);

    /**
     * 取消交易请求
     */
    boolean cancelOffer(UUID offerId);

    /**
     * 获取交易请求
     */
    Optional<PlayerTradeOffer> getOffer(UUID offerId);

    /**
     * 获取玩家的发出请求
     */
    List<PlayerTradeOffer> getSentOffers(UUID playerId);

    /**
     * 获取玩家的收到请求
     */
    List<PlayerTradeOffer> getReceivedOffers(UUID playerId);

    /**
     * 获取待处理的交易请求数量
     */
    int getPendingOfferCount(UUID playerId);

    /**
     * 清理过期请求
     */
    int cleanExpiredOffers();

    /**
     * 从市场快速购买
     */
    Optional<TradeRecord> quickBuy(UUID playerId, NationId nationId,
                                   String resourceId, long amount);

    /**
     * 向市场快速出售
     */
    Optional<TradeRecord> quickSell(UUID playerId, NationId nationId,
                                    String resourceId, long amount);

    /**
     * 玩家交易请求
     */
    record PlayerTradeOffer(
        UUID offerId,
        UUID initiatorId,
        UUID targetId,
        NationId initiatorNationId,
        NationId targetNationId,
        String resourceId,
        long amount,
        double pricePerUnit,
        double totalValue,
        double taxAmount,
        Instant createdAt,
        Instant expiresAt,
        OfferStatus status
    ) {
        public enum OfferStatus {
            PENDING("待处理"),
            ACCEPTED("已接受"),
            REJECTED("已拒绝"),
            CANCELLED("已取消"),
            EXPIRED("已过期");

            private final String displayName;

            OfferStatus(String displayName) {
                this.displayName = displayName;
            }

            public String displayName() {
                return displayName;
            }
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        public boolean isPending() {
            return status == OfferStatus.PENDING && !isExpired();
        }
    }
}
