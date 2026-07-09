package dev.starcore.starcore.war;

import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * 战争基金
 * 国家为战争筹集的专项资金
 */
public final class WarFund {
    private final UUID id;
    private final UUID warId;
    private final NationId nationId;
    private final BigDecimal targetAmount;
    private BigDecimal currentAmount;
    private final Map<UUID, Contribution> contributions;
    private final Instant createdAt;
    private Instant lastContributionAt;

    public WarFund(
        UUID id,
        UUID warId,
        NationId nationId,
        BigDecimal targetAmount,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.warId = Objects.requireNonNull(warId, "warId");
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.targetAmount = Objects.requireNonNull(targetAmount, "targetAmount");
        this.currentAmount = BigDecimal.ZERO;
        this.contributions = new LinkedHashMap<>();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
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

    public BigDecimal targetAmount() {
        return targetAmount;
    }

    public BigDecimal currentAmount() {
        return currentAmount;
    }

    public Collection<Contribution> contributions() {
        return Collections.unmodifiableCollection(contributions.values());
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastContributionAt() {
        return lastContributionAt;
    }

    /**
     * 添加捐献
     */
    public void addContribution(UUID contributorId, String contributorName, BigDecimal amount) {
        Objects.requireNonNull(contributorId, "contributorId");
        Objects.requireNonNull(amount, "amount");

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        Contribution existing = contributions.get(contributorId);
        if (existing != null) {
            existing.addAmount(amount);
        } else {
            contributions.put(contributorId, new Contribution(
                contributorId,
                contributorName,
                amount,
                Instant.now()
            ));
        }

        this.currentAmount = currentAmount.add(amount);
        this.lastContributionAt = Instant.now();
    }

    /**
     * 获取完成百分比
     */
    public double progressPercentage() {
        if (targetAmount.compareTo(BigDecimal.ZERO) == 0) {
            return 100.0;
        }
        return currentAmount.divide(targetAmount, 4, BigDecimal.ROUND_HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .doubleValue();
    }

    /**
     * 是否达到目标
     */
    public boolean isTargetReached() {
        return currentAmount.compareTo(targetAmount) >= 0;
    }

    /**
     * 获取捐献者数量
     */
    public int contributorCount() {
        return contributions.size();
    }

    /**
     * 获取排名前N的捐献者
     */
    public List<Contribution> topContributors(int limit) {
        return contributions.values().stream()
            .sorted(Comparator.comparing(Contribution::amount).reversed())
            .limit(limit)
            .toList();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WarFund other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("WarFund{id=%s, nation=%s, progress=%.1f%% (%s/%s)}",
            id, nationId, progressPercentage(), currentAmount, targetAmount);
    }

    /**
     * 捐献记录
     */
    public static final class Contribution {
        private final UUID contributorId;
        private final String contributorName;
        private BigDecimal amount;
        private final Instant firstContributionAt;
        private Instant lastContributionAt;

        public Contribution(UUID contributorId, String contributorName, BigDecimal amount, Instant firstContributionAt) {
            this.contributorId = Objects.requireNonNull(contributorId, "contributorId");
            this.contributorName = Objects.requireNonNull(contributorName, "contributorName");
            this.amount = Objects.requireNonNull(amount, "amount");
            this.firstContributionAt = Objects.requireNonNull(firstContributionAt, "firstContributionAt");
            this.lastContributionAt = firstContributionAt;
        }

        public UUID contributorId() {
            return contributorId;
        }

        public String contributorName() {
            return contributorName;
        }

        public BigDecimal amount() {
            return amount;
        }

        public Instant firstContributionAt() {
            return firstContributionAt;
        }

        public Instant lastContributionAt() {
            return lastContributionAt;
        }

        void addAmount(BigDecimal additionalAmount) {
            this.amount = amount.add(additionalAmount);
            this.lastContributionAt = Instant.now();
        }

        @Override
        public String toString() {
            return String.format("Contribution{contributor='%s', amount=%s}",
                contributorName, amount);
        }
    }
}
