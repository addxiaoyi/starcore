package dev.starcore.starcore.foundation.economy.event;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 经济事件监听器接口
 */
@FunctionalInterface
public interface EconomyEventListener {
    /**
     * 处理经济事件
     */
    void onEvent(EconomyEvent event);

    /**
     * 默认实现：仅监听存款事件
     */
    static EconomyEventListener onDeposit(Consumer<DepositEvent> handler) {
        return event -> {
            if (event instanceof EconomyEvent.Deposit deposit) {
                handler.accept(new DepositEvent(
                    deposit.playerId(),
                    deposit.amount(),
                    deposit.balanceAfter(),
                    deposit.timestamp()
                ));
            }
        };
    }

    /**
     * 默认实现：仅监听取款事件
     */
    static EconomyEventListener onWithdraw(Consumer<WithdrawEvent> handler) {
        return event -> {
            if (event instanceof EconomyEvent.Withdraw withdraw) {
                handler.accept(new WithdrawEvent(
                    withdraw.playerId(),
                    withdraw.amount(),
                    withdraw.balanceAfter(),
                    withdraw.timestamp()
                ));
            }
        };
    }

    /**
     * 默认实现：仅监听转账事件
     */
    static EconomyEventListener onTransfer(Consumer<TransferEvent> handler) {
        return event -> {
            if (event instanceof EconomyEvent.Transfer transfer) {
                handler.accept(new TransferEvent(
                    transfer.fromPlayerId(),
                    transfer.toPlayerId(),
                    transfer.amount(),
                    transfer.fromBalanceAfter(),
                    transfer.toBalanceAfter(),
                    transfer.timestamp()
                ));
            }
        };
    }

    // 便捷事件记录类
    record DepositEvent(UUID playerId, BigDecimal amount, BigDecimal balanceAfter, long timestamp) {}
    record WithdrawEvent(UUID playerId, BigDecimal amount, BigDecimal balanceAfter, long timestamp) {}
    record TransferEvent(UUID fromPlayerId, UUID toPlayerId, BigDecimal amount,
                         BigDecimal fromBalanceAfter, BigDecimal toBalanceAfter, long timestamp) {}
}
