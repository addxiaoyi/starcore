package dev.starcore.starcore.foundation.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * PlayerVaultEconomyService - Vault 经济集成实现
 * 使用 Vault API 访问第三方经济插件（如 EssentialsX）
 * 当 Vault Economy 服务不可用时返回默认值并记录警告
 */
public final class PlayerVaultEconomyService implements EconomyService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private final Logger logger;
    /** audit B-013: 仅记录一次 Vault provider 名，避免每次资金操作刷屏日志 */
    private String loggedProviderName = null;

    public PlayerVaultEconomyService(Logger logger) {
        this.logger = logger;
    }

    private Economy getVaultEconomy() {
        org.bukkit.plugin.RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            if (logger != null) {
                logger.warning("Vault Economy service not registered. Using fallback zero-balance economy.");
            }
            return null;
        }
        Economy economy = rsp.getProvider();
        // audit B-013: 仅首次解析 provider 时记录日志，避免每次资金操作刷屏
        if (economy != null && loggedProviderName == null) {
            loggedProviderName = economy.getName();
            if (logger != null) {
                logger.info("Vault Economy provider found: " + loggedProviderName);
            }
        }
        return economy;
    }

    private OfflinePlayer getOfflinePlayer(UUID playerId) {
        return Bukkit.getOfflinePlayer(playerId);
    }

    @Override
    public BigDecimal getBalance(UUID playerId) {
        Economy economy = getVaultEconomy();
        if (economy == null) {
            return ZERO;
        }
        OfflinePlayer player = getOfflinePlayer(playerId);
        if (player == null) {
            return ZERO;
        }
        try {
            return BigDecimal.valueOf(economy.getBalance(player));
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Failed to get balance for " + playerId + ": " + e.getMessage());
            }
            return ZERO;
        }
    }

    @Override
    public boolean has(UUID playerId, BigDecimal amount) {
        Economy economy = getVaultEconomy();
        if (economy == null) {
            return false;
        }
        OfflinePlayer player = getOfflinePlayer(playerId);
        if (player == null) {
            return false;
        }
        try {
            // audit B-012: 避免直接用 amount.doubleValue() 调用 Vault.has 而丢失大数精度；
            // 改为取出余额 BigDecimal 比较，避免 BigDecimal->double 转换误差
            BigDecimal current = BigDecimal.valueOf(economy.getBalance(player)).setScale(2, java.math.RoundingMode.DOWN);
            return current.compareTo(amount) >= 0;
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Failed to check balance for " + playerId + ": " + e.getMessage());
            }
            return false;
        }
    }

    @Override
    public boolean deposit(UUID playerId, BigDecimal amount) {
        Economy economy = getVaultEconomy();
        if (economy == null) {
            return false;
        }
        if (amount == null || amount.signum() <= 0) {
            return false;
        }
        OfflinePlayer player = getOfflinePlayer(playerId);
        if (player == null) {
            return false;
        }
        try {
            EconomyResponse response = economy.depositPlayer(player, amount.doubleValue());
            return response.type == EconomyResponse.ResponseType.SUCCESS;
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Failed to deposit for " + playerId + ": " + e.getMessage());
            }
            return false;
        }
    }

    @Override
    public boolean withdraw(UUID playerId, BigDecimal amount) {
        Economy economy = getVaultEconomy();
        if (economy == null) {
            return false;
        }
        if (amount == null || amount.signum() <= 0) {
            return false;
        }
        OfflinePlayer player = getOfflinePlayer(playerId);
        if (player == null) {
            return false;
        }
        try {
            EconomyResponse response = economy.withdrawPlayer(player, amount.doubleValue());
            return response.type == EconomyResponse.ResponseType.SUCCESS;
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Failed to withdraw for " + playerId + ": " + e.getMessage());
            }
            return false;
        }
    }

    @Override
    public boolean setBalance(UUID playerId, BigDecimal amount) {
        // Vault Economy 不直接支持设置余额，需要通过差额计算
        Economy economy = getVaultEconomy();
        if (economy == null) {
            return false;
        }
        if (amount == null || amount.signum() < 0) {
            return false;
        }
        OfflinePlayer player = getOfflinePlayer(playerId);
        if (player == null) {
            return false;
        }
        try {
            BigDecimal currentBalance = BigDecimal.valueOf(economy.getBalance(player));
            BigDecimal diff = amount.subtract(currentBalance);

            if (diff.signum() > 0) {
                // 需要存款
                EconomyResponse response = economy.depositPlayer(player, diff.doubleValue());
                return response.type == EconomyResponse.ResponseType.SUCCESS;
            } else if (diff.signum() < 0) {
                // 需要取款
                EconomyResponse response = economy.withdrawPlayer(player, diff.abs().doubleValue());
                return response.type == EconomyResponse.ResponseType.SUCCESS;
            }
            // 余额相同，无需操作
            return true;
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Failed to set balance for " + playerId + ": " + e.getMessage());
            }
            return false;
        }
    }

    @Override
    public boolean transfer(UUID from, UUID to, BigDecimal amount) {
        Economy economy = getVaultEconomy();
        if (economy == null) {
            return false;
        }
        if (amount == null || amount.signum() <= 0) {
            return false;
        }
        if (from.equals(to)) {
            return false;
        }
        OfflinePlayer fromPlayer = getOfflinePlayer(from);
        OfflinePlayer toPlayer = getOfflinePlayer(to);
        if (fromPlayer == null || toPlayer == null) {
            return false;
        }
        try {
            // 先取款
            EconomyResponse withdrawResponse = economy.withdrawPlayer(fromPlayer, amount.doubleValue());
            if (withdrawResponse.type != EconomyResponse.ResponseType.SUCCESS) {
                return false;
            }
            // 再存款
            EconomyResponse depositResponse = economy.depositPlayer(toPlayer, amount.doubleValue());
            if (depositResponse.type != EconomyResponse.ResponseType.SUCCESS) {
                // 回滚取款
                EconomyResponse rollbackResponse = economy.depositPlayer(fromPlayer, amount.doubleValue());
                if (rollbackResponse.type != EconomyResponse.ResponseType.SUCCESS) {
                    // audit B-011: 回滚也失败时不能默默吞掉，必须告警并标记待人工处理
                    if (logger != null) {
                        logger.severe("[Economy] CRITICAL: rollback failed after transfer deposit failure."
                            + " from=" + from + " to=" + to + " amount=" + amount
                            + " Player " + from + " may have lost funds; manual intervention required.");
                    }
                }
                return false;
            }
            return true;
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Failed to transfer from " + from + " to " + to + ": " + e.getMessage());
            }
            return false;
        }
    }

    @Override
    public Map<UUID, BigDecimal> getAllBalances() {
        // Vault Economy 不支持批量查询所有玩家余额
        // 返回空 Map 并记录警告（已被日志记录）
        // 如需实现此功能，可通过以下方式：
        // 1. 枚举所有已知的在线玩家
        // 2. 维护一个内部缓存来跟踪已知账户
        // 3. 使用第三方经济插件提供的批量查询 API（如果有）
        return Map.of();
    }

    /**
     * 检查此服务是否支持特定功能
     * @return UnsupportedOperationException 如果调用不支持的方法
     */
    public boolean isFeatureSupported(Feature feature) {
        switch (feature) {
            case BATCH_BALANCE_QUERY:
                return false; // Vault 不支持批量查询
            case BALANCE_SET:
                return true;  // 通过差额计算支持
            default:
                return true;
        }
    }

    /**
     * 经济服务支持的特性枚举
     */
    public enum Feature {
        BATCH_BALANCE_QUERY,  // getAllBalances()
        BALANCE_SET,          // setBalance()
        TRANSFER,             // transfer()
        DAILY_LIMIT,          // 每日限额
        TAX                   // 税率
    }

    @Override
    public boolean hasAccount(UUID playerId) {
        Economy economy = getVaultEconomy();
        if (economy == null) {
            return false;
        }
        OfflinePlayer player = getOfflinePlayer(playerId);
        if (player == null) {
            return false;
        }
        try {
            return economy.hasAccount(player);
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Failed to check account for " + playerId + ": " + e.getMessage());
            }
            return false;
        }
    }

    @Override
    public void createAccount(UUID playerId) {
        Economy economy = getVaultEconomy();
        if (economy == null) {
            return;
        }
        OfflinePlayer player = getOfflinePlayer(playerId);
        if (player == null) {
            return;
        }
        try {
            economy.createPlayerAccount(player);
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Failed to create account for " + playerId + ": " + e.getMessage());
            }
        }
    }
}
