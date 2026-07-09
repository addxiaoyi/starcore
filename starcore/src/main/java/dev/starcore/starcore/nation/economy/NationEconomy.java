package dev.starcore.starcore.nation.economy;

import dev.starcore.starcore.util.ColorCodes;
import java.util.UUID;

/**
 * Nation经济系统
 * 基于Towny的债务破产机制
 *
 * @deprecated 功能已迁移到 TreasuryModule，请使用 TreasuryService 代替
 */
@Deprecated
public class NationEconomy {

    private final UUID nationId;

    // 经济数据
    private double balance = 0.0;
    private double debt = 0.0;
    private double debtLimit = 10000.0; // 债务上限

    // 状态
    private boolean bankrupt = false;
    private long bankruptSince = 0;

    // 税收
    private double taxRate = 0.0;
    private double dailyUpkeep = 100.0; // 每日维护费

    public NationEconomy(UUID nationId) {
        this.nationId = nationId;
    }

    // ==================== 基础操作 ====================

    /**
     * 存款
     */
    public boolean deposit(double amount, String reason) {
        if (amount <= 0) {
            return false;
        }

        balance += amount;

        // 如果有债务，优先还债
        if (debt > 0) {
            double payment = Math.min(amount, debt);
            debt -= payment;
            balance -= payment;

            // 检查是否脱离破产
            if (debt < debtLimit * 0.5 && bankrupt) {
                exitBankruptcy();
            }
        }

        return true;
    }

    /**
     * 取款（支持债务）
     */
    public boolean withdraw(double amount, String reason) {
        if (amount <= 0) {
            return false;
        }

        // 余额足够
        if (balance >= amount) {
            balance -= amount;
            return true;
        }

        // 允许借债
        if (canEnterDebt()) {
            double overdraft = amount - balance;

            // 检查债务上限
            if (debt + overdraft <= debtLimit) {
                balance = 0;
                debt += overdraft;

                // 检查破产
                checkBankruptcy();

                return true;
            }
        }

        return false;
    }

    /**
     * 强制取款（不检查余额，用于税收等）
     */
    public void forceWithdraw(double amount, String reason) {
        if (balance >= amount) {
            balance -= amount;
        } else {
            double overdraft = amount - balance;
            balance = 0;
            debt += overdraft;
            checkBankruptcy();
        }
    }

    // ==================== 债务系统 ====================

    /**
     * 是否可以借债
     */
    public boolean canEnterDebt() {
        return !bankrupt && debt < debtLimit;
    }

    /**
     * 检查破产状态
     */
    public void checkBankruptcy() {
        if (debt >= debtLimit && !bankrupt) {
            enterBankruptcy();
        }
    }

    /**
     * 进入破产状态
     */
    private void enterBankruptcy() {
        bankrupt = true;
        bankruptSince = System.currentTimeMillis();

        // 触发破产事件（限制功能）
        // - 禁止领取新领地
        // - 禁止宣战
        // - 禁止创建联盟
    }

    /**
     * 脱离破产状态
     */
    private void exitBankruptcy() {
        bankrupt = false;
        bankruptSince = 0;

        // 恢复功能
    }

    /**
     * 还债
     */
    public boolean payDebt(double amount) {
        if (amount <= 0 || debt <= 0) {
            return false;
        }

        double payment = Math.min(amount, Math.min(debt, balance));
        if (payment > 0) {
            balance -= payment;
            debt -= payment;

            // 检查是否脱离破产
            if (debt < debtLimit * 0.5 && bankrupt) {
                exitBankruptcy();
            }

            return true;
        }

        return false;
    }

    // ==================== 每日维护 ====================

    /**
     * 每日维护费扣除
     * 由定时任务调用
     */
    public void dailyUpkeep() {
        forceWithdraw(dailyUpkeep, "每日维护费");
    }

    /**
     * 计算每日维护费
     * 基于Nation规模
     */
    public double calculateUpkeep(int cityCount, int memberCount, int territoryCount) {
        double base = 100.0;
        double cityCost = cityCount * 50.0;
        double memberCost = memberCount * 2.0;
        double territoryCost = territoryCount * 1.0;

        dailyUpkeep = base + cityCost + memberCost + territoryCost;
        return dailyUpkeep;
    }

    // ==================== 税收系统 ====================

    /**
     * 设置税率
     */
    public void setTaxRate(double rate) {
        this.taxRate = Math.max(0, Math.min(1.0, rate)); // 0-100%
    }

    /**
     * 收取税款
     */
    public double collectTax(double playerIncome) {
        return playerIncome * taxRate;
    }

    // ==================== Getter/Setter ====================

    public double getBalance() {
        return balance;
    }

    public double getDebt() {
        return debt;
    }

    public double getDebtLimit() {
        return debtLimit;
    }

    public void setDebtLimit(double debtLimit) {
        this.debtLimit = debtLimit;
    }

    public boolean isBankrupt() {
        return bankrupt;
    }

    public long getBankruptDays() {
        if (!bankrupt) return 0;
        long diff = System.currentTimeMillis() - bankruptSince;
        return diff / (1000 * 60 * 60 * 24);
    }

    public double getTaxRate() {
        return taxRate;
    }

    public double getDailyUpkeep() {
        return dailyUpkeep;
    }

    /**
     * 获取经济健康度（0-100）
     */
    public double getHealthScore() {
        if (bankrupt) return 0;

        // 考虑余额和债务比例
        double balanceScore = Math.min(balance / (dailyUpkeep * 30), 1.0) * 50; // 50分
        double debtScore = (1.0 - (debt / debtLimit)) * 50; // 50分

        return balanceScore + debtScore;
    }

    /**
     * 获取经济状态描述
     */
    public EconomyStatus getStatus() {
        if (bankrupt) {
            return EconomyStatus.BANKRUPT;
        } else if (debt > debtLimit * 0.8) {
            return EconomyStatus.CRITICAL;
        } else if (debt > debtLimit * 0.5) {
            return EconomyStatus.WARNING;
        } else if (balance < dailyUpkeep * 7) {
            return EconomyStatus.LOW_FUNDS;
        } else if (balance > dailyUpkeep * 30) {
            return EconomyStatus.WEALTHY;
        } else {
            return EconomyStatus.HEALTHY;
        }
    }

    /**
     * 经济状态枚举
     */
    public enum EconomyStatus {
        BANKRUPT("§c§l破产", "国家已破产，功能受限"),
        CRITICAL("§4危急", "债务过高，即将破产"),
        WARNING("§6警告", "债务较高，需要注意"),
        LOW_FUNDS("§e资金不足", "余额较低"),
        HEALTHY("§a健康", "经济状况良好"),
        WEALTHY("§2§l富裕", "国库充盈");

        private final String displayName;
        private final String description;

        EconomyStatus(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    @Override
    public String toString() {
        return String.format(
            "NationEconomy[balance=%.2f, debt=%.2f, status=%s, health=%.1f%%]",
            balance, debt, getStatus(), getHealthScore()
        );
    }
}
