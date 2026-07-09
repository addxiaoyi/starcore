package dev.starcore.starcore.module.territory.rent.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 租借付款记录
 */
public final class LeasePayment {
    private final UUID paymentId;
    private final UUID contractId;
    private final UUID payerId;
    private final PaymentPayerType payerType;
    private final BigDecimal amount;
    private final PaymentType paymentType;
    private final Instant paymentPeriodStart;
    private final Instant paymentPeriodEnd;
    private final Instant paymentTime;

    public LeasePayment(
        UUID paymentId,
        UUID contractId,
        UUID payerId,
        PaymentPayerType payerType,
        BigDecimal amount,
        PaymentType paymentType,
        Instant paymentPeriodStart,
        Instant paymentPeriodEnd,
        Instant paymentTime
    ) {
        this.paymentId = paymentId;
        this.contractId = contractId;
        this.payerId = payerId;
        this.payerType = payerType;
        this.amount = amount;
        this.paymentType = paymentType;
        this.paymentPeriodStart = paymentPeriodStart;
        this.paymentPeriodEnd = paymentPeriodEnd;
        this.paymentTime = paymentTime;
    }

    /**
     * Create a new payment record.
     */
    public static LeasePayment create(
        UUID contractId,
        UUID payerId,
        PaymentPayerType payerType,
        BigDecimal amount,
        PaymentType paymentType,
        Instant periodStart,
        Instant periodEnd
    ) {
        return new LeasePayment(
            UUID.randomUUID(),
            contractId,
            payerId,
            payerType,
            amount,
            paymentType,
            periodStart,
            periodEnd,
            Instant.now()
        );
    }

    public UUID paymentId() { return paymentId; }
    public UUID contractId() { return contractId; }
    public UUID payerId() { return payerId; }
    public PaymentPayerType payerType() { return payerType; }
    public BigDecimal amount() { return amount; }
    public PaymentType paymentType() { return paymentType; }
    public Instant paymentPeriodStart() { return paymentPeriodStart; }
    public Instant paymentPeriodEnd() { return paymentPeriodEnd; }
    public Instant paymentTime() { return paymentTime; }
}
