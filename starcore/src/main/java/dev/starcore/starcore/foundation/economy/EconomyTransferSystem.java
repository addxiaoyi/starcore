package dev.starcore.starcore.foundation.economy;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 经济转账系统
 */
public final class EconomyTransferSystem {
    private final InternalEconomyService economyService;
    private final Plugin plugin;
    private final Logger logger;

    // 转账税率（0.0-1.0，0表示无税）
    private double transferTax = 0.05; // 5%

    // 最小转账金额
    private double minTransferAmount = 1.0;

    // 每日转账限制
    private double dailyTransferLimit = 100000.0;

    // 是否启用每日限额
    private boolean dailyLimitEnabled = true;

    // 是否启用转账税
    private boolean taxEnabled = true;

    // 配置文件
    private final File configFile;

    // 玩家每日转账记录
    private final Map<UUID, DailyTransferRecord> dailyRecords = new ConcurrentHashMap<>();

    public EconomyTransferSystem(InternalEconomyService economyService, Plugin plugin) {
        this(economyService, plugin, plugin.getLogger());
    }

    public EconomyTransferSystem(InternalEconomyService economyService, Plugin plugin, Logger logger) {
        this.economyService = economyService;
        this.plugin = plugin;
        this.logger = logger;
        this.configFile = new File(plugin.getDataFolder(), "economy-transfer.yml");
        loadConfig();
    }

    /**
     * 从配置文件加载转账配置
     */
    public void loadConfig() {
        // 优先使用独立的配置文件
        if (configFile.exists()) {
            FileConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
            try {
                config.load(configFile);
                applyConfig(config);
                logInfo("Loaded economy transfer config from: " + configFile.getName());
                return;
            } catch (Exception e) {
                logWarning("Failed to load config from " + configFile.getName() + ", using plugin config: " + e.getMessage());
            }
        }

        // 回退到插件主配置
        FileConfiguration config = plugin.getConfig();
        applyConfig(config);
        saveDefaultConfig(); // 保存默认值到独立配置文件
        logInfo("Loaded economy transfer config from plugin config");
    }

    /**
     * 应用配置值
     */
    private void applyConfig(FileConfiguration config) {
        // 从配置读取转账税率（0.0-1.0）
        this.transferTax = config.getDouble("economy.transfer.tax-rate", 0.05);
        this.transferTax = Math.max(0.0, Math.min(1.0, this.transferTax));

        // 从配置读取最小转账金额
        this.minTransferAmount = config.getDouble("economy.transfer.min-amount", 1.0);

        // 从配置读取每日转账限额
        this.dailyTransferLimit = config.getDouble("economy.transfer.daily-limit", 100000.0);

        // 从配置读取是否启用每日限额
        this.dailyLimitEnabled = config.getBoolean("economy.transfer.daily-limit-enabled", true);

        // 从配置读取是否启用转账税
        this.taxEnabled = config.getBoolean("economy.transfer.tax-enabled", true);
    }

    /**
     * 保存默认配置到独立文件
     */
    private void saveDefaultConfig() {
        if (configFile.exists()) {
            return;
        }

        FileConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("economy.transfer.tax-rate", transferTax);
        config.set("economy.transfer.min-amount", minTransferAmount);
        config.set("economy.transfer.daily-limit", dailyTransferLimit);
        config.set("economy.transfer.daily-limit-enabled", dailyLimitEnabled);
        config.set("economy.transfer.tax-enabled", taxEnabled);

        try {
            config.save(configFile);
            logInfo("Saved default economy transfer config to: " + configFile.getName());
        } catch (IOException e) {
            logWarning("Failed to save default config: " + e.getMessage());
        }
    }

    /**
     * 保存当前配置到文件
     */
    public void saveConfig() {
        FileConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("economy.transfer.tax-rate", transferTax);
        config.set("economy.transfer.min-amount", minTransferAmount);
        config.set("economy.transfer.daily-limit", dailyTransferLimit);
        config.set("economy.transfer.daily-limit-enabled", dailyLimitEnabled);
        config.set("economy.transfer.tax-enabled", taxEnabled);

        try {
            config.save(configFile);
            logInfo("Saved economy transfer config");
        } catch (IOException e) {
            logWarning("Failed to save config: " + e.getMessage());
        }
    }

    /**
     * 重载配置
     */
    public void reloadConfig() {
        loadConfig();
        // 清除每日记录（因为限额可能已更改）
        dailyRecords.clear();
    }

    /**
     * 转账
     */
    public TransferResult transfer(UUID senderId, UUID receiverId, double amount) {
        // audit B-009: 用 BigDecimal 替代 double 进行精度与限额计算
        BigDecimal amountBd = BigDecimal.valueOf(amount);
        BigDecimal minTransferBd = BigDecimal.valueOf(minTransferAmount);
        BigDecimal dailyLimitBd = BigDecimal.valueOf(dailyTransferLimit);

        // 验证金额
        if (amountBd.compareTo(minTransferBd) < 0) {
            return TransferResult.failure("最小转账金额为 " + minTransferAmount + " 星尘");
        }

        // 计算税额（B-009：BigDecimal 计算）
        BigDecimal tax = taxEnabled ? BigDecimal.valueOf(calculateTax(amount)) : BigDecimal.ZERO;
        BigDecimal totalAmountBd = amountBd.add(tax);

        // 检查每日限额（audit B-010：compute 原子化 todayTotal 校验+预占，避免并发双检绕过每日限额）
        if (dailyLimitEnabled) {
            BigDecimal[] currentTotal = new BigDecimal[1];
            dailyRecords.compute(senderId, (id, record) -> {
                long today = System.currentTimeMillis() / (1000L * 60 * 60 * 24);
                if (record == null || record.day != today) {
                    record = new DailyTransferRecord(today);
                    record.todayTotalBd = BigDecimal.ZERO;
                }
                record.todayTotalBd = record.todayTotalBd == null ? BigDecimal.ZERO : record.todayTotalBd;
                currentTotal[0] = record.todayTotalBd;
                return record;
            });
            // 校验：若超限直接拒绝，不保留副作用（todayTotalBd 未修改）
            if (currentTotal[0].add(amountBd).compareTo(dailyLimitBd) > 0) {
                BigDecimal remaining = dailyLimitBd.subtract(currentTotal[0]);
                return TransferResult.failure("超过每日转账限额（剩余 " + remaining.doubleValue() + " 星尘）");
            }
        }

        // 检查余额
        BigDecimal senderBalance = economyService.getBalance(senderId);
        if (senderBalance.compareTo(totalAmountBd) < 0) {
            return TransferResult.failure("余额不足（需要 " + totalAmountBd.doubleValue() + " 星尘，包含手续费）");
        }

        // 执行转账：先 withdraw，校验返回值（audit B-008：必须检查 withdraw 返回值，失败则不 deposit）
        boolean withdrawn = economyService.withdraw(senderId, totalAmountBd);
        if (!withdrawn) {
            return TransferResult.failure("取款失败，转账中止");
        }
        boolean deposited = economyService.deposit(receiverId, amountBd);
        if (!deposited) {
            // audit B-008: deposit 失败主动回滚取款，避免凭空送钱/销毁
            economyService.deposit(senderId, totalAmountBd);
            return TransferResult.failure("收款方存款失败，已回滚取款");
        }

        // 原子累加每日限额计数（B-010：compute 原子化）
        if (dailyLimitEnabled) {
            dailyRecords.compute(senderId, (id, record) -> {
                long today = System.currentTimeMillis() / (1000L * 60 * 60 * 24);
                if (record == null || record.day != today) {
                    record = new DailyTransferRecord(today);
                    record.todayTotalBd = BigDecimal.ZERO;
                }
                record.todayTotalBd = (record.todayTotalBd == null ? BigDecimal.ZERO : record.todayTotalBd).add(amountBd);
                record.transferCount++;
                return record;
            });
        }

        logInfo("Transfer completed: " + amount + " from " + senderId + " to " + receiverId + " (tax: " + tax + ")");
        return TransferResult.success(amount, tax.doubleValue(), amount);
    }

    /**
     * 计算总金额（包含税）
     */
    public double calculateTotalAmount(double amount) {
        return amount + calculateTax(amount);
    }

    /**
     * 计算税额
     */
    public double calculateTax(double amount) {
        return amount * transferTax;
    }

    /**
     * 获取或创建每日记录
     */
    private DailyTransferRecord getOrCreateRecord(UUID playerId) {
        long today = System.currentTimeMillis() / (1000 * 60 * 60 * 24);

        DailyTransferRecord record = dailyRecords.get(playerId);
        if (record == null || record.day != today) {
            record = new DailyTransferRecord(today);
            dailyRecords.put(playerId, record);
        }

        return record;
    }

    /**
     * 获取今日剩余额度
     */
    public double getRemainingLimit(UUID playerId) {
        DailyTransferRecord record = getOrCreateRecord(playerId);
        BigDecimal total = record.todayTotalBd == null ? BigDecimal.ZERO : record.todayTotalBd;
        BigDecimal remaining = BigDecimal.valueOf(dailyTransferLimit).subtract(total);
        return remaining.max(BigDecimal.ZERO).doubleValue();
    }

    /**
     * 获取今日转账统计
     */
    public DailyTransferStats getStats(UUID playerId) {
        DailyTransferRecord record = getOrCreateRecord(playerId);
        return new DailyTransferStats(
            record.todayTotalBd == null ? 0 : record.todayTotalBd.doubleValue(),
            record.transferCount,
            getRemainingLimit(playerId)
        );
    }

    // Getters and Setters
    public void setTransferTax(double tax) {
        this.transferTax = Math.max(0, Math.min(1, tax));
    }

    public void setMinTransferAmount(double amount) {
        this.minTransferAmount = amount;
    }

    public void setDailyTransferLimit(double limit) {
        this.dailyTransferLimit = limit;
    }

    public double getTransferTax() {
        return transferTax;
    }

    public double getMinTransferAmount() {
        return minTransferAmount;
    }

    public double getDailyTransferLimit() {
        return dailyTransferLimit;
    }

    public boolean isDailyLimitEnabled() {
        return dailyLimitEnabled;
    }

    public void setDailyLimitEnabled(boolean enabled) {
        this.dailyLimitEnabled = enabled;
    }

    public boolean isTaxEnabled() {
        return taxEnabled;
    }

    public void setTaxEnabled(boolean enabled) {
        this.taxEnabled = enabled;
    }

    // ==================== 日志辅助 ====================

    private void logInfo(String message) {
        if (logger != null) {
            logger.info("[EconomyTransfer] " + message);
        }
    }

    private void logWarning(String message) {
        if (logger != null) {
            logger.warning("[EconomyTransfer] " + message);
        }
    }

    /**
     * 每日转账记录
     */
    private static class DailyTransferRecord {
        long day;
        double todayTotal = 0;
        /** audit B-009/B-010: BigDecimal 用于精确累加与并发安全 */
        BigDecimal todayTotalBd = BigDecimal.ZERO;
        int transferCount = 0;

        DailyTransferRecord(long day) {
            this.day = day;
        }
    }

    /**
     * 转账结果
     */
    public record TransferResult(
        boolean success,
        String message,
        double amount,
        double tax,
        double receiverAmount
    ) {
        public static TransferResult success(double amount, double tax, double receiverAmount) {
            return new TransferResult(true, "转账成功", amount, tax, receiverAmount);
        }

        public static TransferResult failure(String message) {
            return new TransferResult(false, message, 0, 0, 0);
        }
    }

    /**
     * 每日转账统计
     */
    public record DailyTransferStats(
        double todayTotal,
        int transferCount,
        double remaining
    ) {}
}
