package dev.starcore.starcore.foundation.economy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * 经济服务接口
 * 统一经济系统访问
 */
public interface EconomyService {
    /**
     * 获取余额
     */
    BigDecimal getBalance(UUID playerId);

    /**
     * 检查是否有足够余额
     */
    boolean has(UUID playerId, BigDecimal amount);

    /**
     * 存款
     */
    boolean deposit(UUID playerId, BigDecimal amount);

    /**
     * 取款
     */
    boolean withdraw(UUID playerId, BigDecimal amount);

    /**
     * 设置余额
     */
    boolean setBalance(UUID playerId, BigDecimal amount);

    /**
     * 转账
     */
    boolean transfer(UUID from, UUID to, BigDecimal amount);

    /**
     * 获取所有余额（用于排行榜）
     */
    Map<UUID, BigDecimal> getAllBalances();

    /**
     * 检查账户是否存在
     */
    boolean hasAccount(UUID playerId);

    /**
     * 创建账户
     */
    void createAccount(UUID playerId);
}
