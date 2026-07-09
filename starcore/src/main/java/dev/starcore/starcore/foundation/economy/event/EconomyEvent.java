package dev.starcore.starcore.foundation.economy.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 经济事件基类
 */
public sealed interface EconomyEvent permits
    EconomyEvent.Deposit,
    EconomyEvent.Withdraw,
    EconomyEvent.Transfer,
    EconomyEvent.BalanceSet,
    EconomyEvent.AccountCreated {

    /**
     * 获取事件类型
     */
    String eventType();

    /**
     * 获取涉及的玩家ID
     */
    UUID playerId();

    /**
     * 获取金额
     */
    BigDecimal amount();

    /**
     * 获取事件时间戳
     */
    long timestamp();

    /**
     * 获取变化后的余额
     */
    BigDecimal balanceAfter();

    /**
     * 存款事件
     */
    record Deposit(
        UUID playerId,
        BigDecimal amount,
        BigDecimal balanceAfter,
        long timestamp
    ) implements EconomyEvent {
        @Override
        public String eventType() { return "deposit"; }
    }

    /**
     * 取款事件
     */
    record Withdraw(
        UUID playerId,
        BigDecimal amount,
        BigDecimal balanceAfter,
        long timestamp
    ) implements EconomyEvent {
        @Override
        public String eventType() { return "withdraw"; }
    }

    /**
     * 转账事件
     */
    record Transfer(
        UUID fromPlayerId,
        UUID toPlayerId,
        BigDecimal amount,
        BigDecimal fromBalanceAfter,
        BigDecimal toBalanceAfter,
        long timestamp
    ) implements EconomyEvent {
        @Override
        public String eventType() { return "transfer"; }

        @Override
        public UUID playerId() { return fromPlayerId; }

        @Override
        public BigDecimal balanceAfter() { return fromBalanceAfter; }
    }

    /**
     * 余额设置事件
     */
    record BalanceSet(
        UUID playerId,
        BigDecimal previousBalance,
        BigDecimal newBalance,
        long timestamp
    ) implements EconomyEvent {
        @Override
        public String eventType() { return "balance_set"; }

        @Override
        public BigDecimal amount() { return newBalance; }

        @Override
        public BigDecimal balanceAfter() { return newBalance; }
    }

    /**
     * 账户创建事件
     */
    record AccountCreated(
        UUID playerId,
        BigDecimal initialBalance,
        long timestamp
    ) implements EconomyEvent {
        @Override
        public String eventType() { return "account_created"; }

        @Override
        public BigDecimal amount() { return initialBalance; }

        @Override
        public BigDecimal balanceAfter() { return initialBalance; }
    }
}
