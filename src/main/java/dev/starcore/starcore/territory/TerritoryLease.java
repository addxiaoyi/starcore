package dev.starcore.starcore.territory;

import java.util.UUID;

/**
 * 领土租赁合同类
 * 记录领土的租赁信息
 */
public class TerritoryLease {

    private final UUID id;
    private final UUID territoryId;
    private final UUID landlordId;
    private UUID tenantId;

    // 租金设置
    private double rentAmount;
    private RentPeriod rentPeriod;

    // 租期
    private final long leaseStartTime;
    private long leaseEndTime;
    private int leaseDuration; // 天数

    // 自动续租
    private boolean autoRenew;

    // 状态
    private LeaseStatus status;

    // 最后支付时间
    private long lastPaymentTime;

    // 下次支付时间
    private long nextPaymentTime;

    // 欠租天数
    private int overdueDays;

    // 上次逾期检查时间（用于增量计算逾期天数）
    private long lastOverdueCheckTime;

    // 押金
    private double deposit;

    // 备注
    private String notes;

    public TerritoryLease(UUID territoryId, UUID landlordId, double rentAmount,
                         RentPeriod rentPeriod, int leaseDuration) {
        this.id = UUID.randomUUID();
        this.territoryId = territoryId;
        this.landlordId = landlordId;
        this.rentAmount = rentAmount;
        this.rentPeriod = rentPeriod;
        this.leaseDuration = leaseDuration;
        this.leaseStartTime = System.currentTimeMillis();
        this.leaseEndTime = leaseStartTime + (leaseDuration * 24L * 60 * 60 * 1000);
        this.status = LeaseStatus.PENDING;
        this.autoRenew = false;
        this.overdueDays = 0;
        this.deposit = 0;
    }

    // ==================== 状态检查 ====================

    /**
     * 检查租约是否生效中
     */
    public boolean isActive() {
        return status == LeaseStatus.ACTIVE &&
               System.currentTimeMillis() < leaseEndTime;
    }

    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        return status == LeaseStatus.EXPIRED ||
               System.currentTimeMillis() >= leaseEndTime;
    }

    /**
     * 检查是否需要支付租金
     */
    public boolean needsPayment() {
        if (status != LeaseStatus.ACTIVE) {
            return false;
        }
        return System.currentTimeMillis() >= nextPaymentTime;
    }

    /**
     * 检查是否欠租
     */
    public boolean isOverdue() {
        return overdueDays > 0;
    }

    /**
     * 获取剩余时间（毫秒）
     */
    public long getRemainingTime() {
        if (isExpired()) {
            return 0;
        }
        return leaseEndTime - System.currentTimeMillis();
    }

    /**
     * 获取剩余天数
     */
    public int getRemainingDays() {
        long remaining = getRemainingTime();
        return (int) (remaining / (24L * 60 * 60 * 1000));
    }

    /**
     * 获取格式化剩余时间
     */
    public String getFormattedRemainingTime() {
        if (isExpired()) {
            return "已过期";
        }

        int days = getRemainingDays();
        if (days > 0) {
            return days + " 天";
        }

        long hours = getRemainingTime() / (60 * 60 * 1000);
        return hours + " 小时";
    }

    // ==================== 租金计算 ====================

    /**
     * 计算租金周期的毫秒数
     */
    public long getRentPeriodMillis() {
        return rentPeriod.getMillis();
    }

    /**
     * 计算下次支付金额
     */
    public double getNextPaymentAmount() {
        // 基础租金
        double amount = rentAmount;

        // 如果有欠租，加上滞纳金
        if (overdueDays > 0) {
            double lateFee = rentAmount * 0.1 * overdueDays; // 每天10%滞纳金
            amount += lateFee;
        }

        return amount;
    }

    /**
     * 计算总租金（整个租期）
     */
    public double getTotalRent() {
        long totalPeriods = leaseDuration * 24L * 60 * 60 * 1000 / getRentPeriodMillis();
        return rentAmount * totalPeriods;
    }

    // ==================== 支付操作 ====================

    /**
     * 记录支付
     */
    public void recordPayment() {
        this.lastPaymentTime = System.currentTimeMillis();
        this.nextPaymentTime = lastPaymentTime + getRentPeriodMillis();
        resetOverdueStatus(); // 重置逾期状态

        // 如果是第一次支付，激活租约
        if (status == LeaseStatus.PENDING) {
            this.status = LeaseStatus.ACTIVE;
        }
    }

    /**
     * 增加欠租天数（增量更新）
     * @param days 增加的天数
     */
    public void addOverdueDays(int days) {
        this.overdueDays += days;
        this.lastOverdueCheckTime = System.currentTimeMillis();
    }

    /**
     * 增量更新逾期天数（基于上次检查时间）
     * @param currentTime 当前时间戳
     */
    public void updateOverdueDays(long currentTime) {
        if (lastOverdueCheckTime > 0) {
            long millisSinceLastCheck = currentTime - lastOverdueCheckTime;
            int daysSinceLastCheck = (int) (millisSinceLastCheck / (24L * 60 * 60 * 1000));
            if (daysSinceLastCheck > 0) {
                this.overdueDays += daysSinceLastCheck;
                this.lastOverdueCheckTime = currentTime;
            }
        } else {
            // 首次检查，初始化
            this.lastOverdueCheckTime = currentTime;
            if (this.overdueDays == 0) {
                this.overdueDays = 1;
            }
        }
    }

    /**
     * 重置逾期状态（支付后调用）
     */
    public void resetOverdueStatus() {
        this.overdueDays = 0;
        this.lastOverdueCheckTime = 0;
    }

    /**
     * 续租
     */
    public void renew(int additionalDays) {
        this.leaseDuration += additionalDays;
        this.leaseEndTime += (additionalDays * 24L * 60 * 60 * 1000);

        if (status == LeaseStatus.EXPIRED) {
            status = LeaseStatus.ACTIVE;
        }
    }

    // ==================== 状态操作 ====================

    /**
     * 激活租约
     */
    public void activate() {
        this.status = LeaseStatus.ACTIVE;
        if (lastPaymentTime == 0) {
            recordPayment();
        }
    }

    /**
     * 取消租约
     */
    public void cancel() {
        this.status = LeaseStatus.CANCELLED;
    }

    /**
     * 标记为已过期
     */
    public void markExpired() {
        this.status = LeaseStatus.EXPIRED;
    }

    // ==================== Getter/Setter ====================

    public UUID getId() {
        return id;
    }

    public UUID getTerritoryId() {
        return territoryId;
    }

    public UUID getLandlordId() {
        return landlordId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public double getRentAmount() {
        return rentAmount;
    }

    public void setRentAmount(double rentAmount) {
        this.rentAmount = rentAmount;
    }

    public RentPeriod getRentPeriod() {
        return rentPeriod;
    }

    public void setRentPeriod(RentPeriod rentPeriod) {
        this.rentPeriod = rentPeriod;
    }

    public long getLeaseStartTime() {
        return leaseStartTime;
    }

    public long getLeaseEndTime() {
        return leaseEndTime;
    }

    public int getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(int leaseDuration) {
        this.leaseDuration = leaseDuration;
        this.leaseEndTime = leaseStartTime + (leaseDuration * 24L * 60 * 60 * 1000);
    }

    public boolean isAutoRenew() {
        return autoRenew;
    }

    public void setAutoRenew(boolean autoRenew) {
        this.autoRenew = autoRenew;
    }

    public LeaseStatus getStatus() {
        return status;
    }

    public void setStatus(LeaseStatus status) {
        this.status = status;
    }

    public long getLastPaymentTime() {
        return lastPaymentTime;
    }

    public long getNextPaymentTime() {
        return nextPaymentTime;
    }

    public int getOverdueDays() {
        return overdueDays;
    }

    public double getDeposit() {
        return deposit;
    }

    public void setDeposit(double deposit) {
        this.deposit = deposit;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        return String.format("TerritoryLease[id=%s, territory=%s, landlord=%s, tenant=%s, " +
                           "rent=%.2f/%s, duration=%dd, status=%s, remaining=%s]",
            id, territoryId, landlordId, tenantId, rentAmount, rentPeriod,
            leaseDuration, status, getFormattedRemainingTime());
    }

    // ==================== 内部枚举 ====================

    /**
     * 租金周期
     */
    public enum RentPeriod {
        DAILY("每日", 1),
        WEEKLY("每周", 7),
        MONTHLY("每月", 30);

        private final String displayName;
        private final int days;

        RentPeriod(String displayName, int days) {
            this.displayName = displayName;
            this.days = days;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getDays() {
            return days;
        }

        public long getMillis() {
            return days * 24L * 60 * 60 * 1000;
        }
    }
}
