package dev.starcore.starcore.module.donation.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 玩家献金数据
 * 记录玩家对特定国家的累计献金信息
 */
public record DonationData(
    BigDecimal totalAmount,
    BigDecimal historicalAmount,
    int donationCount
) {
    /**
     * 创建一个新的献金数据记录
     */
    public static DonationData of(BigDecimal amount) {
        return new DonationData(amount, amount, 1);
    }

    /**
     * 创建一个空的献金数据记录
     */
    public static DonationData empty() {
        return new DonationData(BigDecimal.ZERO, BigDecimal.ZERO, 0);
    }

    /**
     * 添加献金额
     */
    public DonationData add(BigDecimal amount) {
        return new DonationData(
            totalAmount.add(amount),
            historicalAmount.add(amount),
            donationCount + 1
        );
    }

    /**
     * 获取平均献金额
     */
    public BigDecimal averageAmount() {
        if (donationCount <= 0) {
            return BigDecimal.ZERO;
        }
        return totalAmount.divide(BigDecimal.valueOf(donationCount), 2, java.math.RoundingMode.DOWN);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DonationData that = (DonationData) o;
        return donationCount == that.donationCount &&
            Objects.equals(totalAmount, that.totalAmount) &&
            Objects.equals(historicalAmount, that.historicalAmount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalAmount, historicalAmount, donationCount);
    }

    @Override
    public String toString() {
        return "DonationData{" +
            "totalAmount=" + totalAmount +
            ", historicalAmount=" + historicalAmount +
            ", donationCount=" + donationCount +
            '}';
    }
}