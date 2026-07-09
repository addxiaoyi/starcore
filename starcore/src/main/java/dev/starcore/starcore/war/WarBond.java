package dev.starcore.starcore.war;

import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 战争债券
 * 国家为筹集战争资金发行的债券
 */
public final class WarBond {
    private final UUID id;
    private final UUID warId;
    private final NationId nationId;
    private final BigDecimal faceValue;
    private final double interestRate;      // 年利率
    private final int termMonths;           // 期限（月）
    private final Instant issuedAt;
    private final Instant maturityDate;
    private final Map<UUID, BondHolder> holders;
    private BondStatus status;

    public WarBond(
        UUID id,
        UUID warId,
        NationId nationId,
        BigDecimal faceValue,
        double interestRate,
        int termMonths,
        Instant issuedAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.warId = Objects.requireNonNull(warId, "warId");
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.faceValue = Objects.requireNonNull(faceValue, "faceValue");
        this.interestRate = interestRate;
        this.termMonths = termMonths;
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.maturityDate = issuedAt.plus(Duration.ofDays(termMonths * 30L));
        this.holders = new LinkedHashMap<>();
        this.status = BondStatus.ACTIVE;
    }

    public UUID id() {
        return id;
    }

    public UUID warId() {
        return warId;
    }

    public NationId nationId() {
        return nationId;
    }

    public BigDecimal faceValue() {
        return faceValue;
    }

    public double interestRate() {
        return interestRate;
    }

    public int termMonths() {
        return termMonths;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant maturityDate() {
        return maturityDate;
    }

    public Collection<BondHolder> holders() {
        return Collections.unmodifiableCollection(holders.values());
    }

    public BondStatus status() {
        return status;
    }

    /**
     * 购买债券
     */
    public void purchase(UUID buyerId, String buyerName, BigDecimal amount) {
        if (status != BondStatus.ACTIVE) {
            throw new IllegalStateException("Bond is not active");
        }

        BondHolder existing = holders.get(buyerId);
        if (existing != null) {
            existing.addAmount(amount);
        } else {
            holders.put(buyerId, new BondHolder(buyerId, buyerName, amount, Instant.now()));
        }
    }

    /**
     * 赎回债券
     */
    public void redeem(UUID holderId) {
        holders.remove(holderId);

        if (holders.isEmpty()) {
            this.status = BondStatus.REDEEMED;
        }
    }

    /**
     * 违约
     */
    public void defaultBond() {
        this.status = BondStatus.DEFAULTED;
    }

    /**
     * 到期
     */
    public void mature() {
        this.status = BondStatus.MATURED;
    }

    /**
     * 检查是否到期
     */
    public boolean isMature(Instant now) {
        return !now.isBefore(maturityDate);
    }

    /**
     * 获取持有者持有金额
     */
    public BigDecimal holderAmount(UUID holderId) {
        BondHolder holder = holders.get(holderId);
        return holder != null ? holder.amount() : BigDecimal.ZERO;
    }

    /**
     * 计算赎回价值（本金 + 利息）
     */
    public BigDecimal calculateRedemptionValue(BigDecimal principalAmount) {
        // 简单利息计算：本金 * (1 + 利率 * 年数)
        double years = termMonths / 12.0;
        double multiplier = 1.0 + (interestRate * years);
        return principalAmount.multiply(BigDecimal.valueOf(multiplier));
    }

    /**
     * 获取总发行量
     */
    public BigDecimal totalIssued() {
        return holders.values().stream()
            .map(BondHolder::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 获取发行百分比
     */
    public double issuedPercentage() {
        BigDecimal total = totalIssued();
        if (faceValue.compareTo(BigDecimal.ZERO) == 0) {
            return 100.0;
        }
        return total.divide(faceValue, 4, BigDecimal.ROUND_HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .doubleValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WarBond other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("WarBond{id=%s, nation=%s, value=%s, rate=%.2f%%, term=%d months, status=%s}",
            id, nationId, faceValue, interestRate * 100, termMonths, status);
    }

    /**
     * 债券持有者
     */
    public static final class BondHolder {
        private final UUID holderId;
        private final String holderName;
        private BigDecimal amount;
        private final Instant purchasedAt;

        public BondHolder(UUID holderId, String holderName, BigDecimal amount, Instant purchasedAt) {
            this.holderId = Objects.requireNonNull(holderId, "holderId");
            this.holderName = Objects.requireNonNull(holderName, "holderName");
            this.amount = Objects.requireNonNull(amount, "amount");
            this.purchasedAt = Objects.requireNonNull(purchasedAt, "purchasedAt");
        }

        public UUID holderId() {
            return holderId;
        }

        public String holderName() {
            return holderName;
        }

        public BigDecimal amount() {
            return amount;
        }

        public Instant purchasedAt() {
            return purchasedAt;
        }

        void addAmount(BigDecimal additionalAmount) {
            this.amount = amount.add(additionalAmount);
        }

        @Override
        public String toString() {
            return String.format("BondHolder{name='%s', amount=%s}", holderName, amount);
        }
    }

    /**
     * 债券状态
     */
    public enum BondStatus {
        ACTIVE("活跃"),
        MATURED("已到期"),
        REDEEMED("已赎回"),
        DEFAULTED("已违约");

        private final String displayName;

        BondStatus(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}
