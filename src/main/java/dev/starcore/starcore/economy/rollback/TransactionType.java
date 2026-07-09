package dev.starcore.starcore.economy.rollback;

/**
 * 交易类型
 */
public enum TransactionType {
    TRANSFER,   // 转账
    DEPOSIT,    // 存款
    WITHDRAW,   // 取款
    PURCHASE,   // 购买
    SELL,       // 出售
    REWARD,     // 奖励
    TAX,        // 税收
    ADMIN       // 管理员操作
}
