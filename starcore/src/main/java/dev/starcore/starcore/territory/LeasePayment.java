package dev.starcore.starcore.territory;

import dev.starcore.starcore.util.ColorCodes;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.UUID;

/**
 * 租金支付记录类
 * 记录每次租金支付的详细信息
 */
public class LeasePayment {

    private final UUID id;
    private final UUID leaseId;
    private final UUID payerId;
    // 审计 A-048：amount 改用 BigDecimal 避免浮点累加精度丢失
    private final BigDecimal amount;
    private final long paymentTime;
    // 审计 A-047：status 改为构造器参数，支持 PENDING/FAILED 状态
    private final PaymentStatus status;
    private final String paymentMethod;
    private String notes;

    // 支付的租金周期
    private final long periodStart;
    private final long periodEnd;

    public LeasePayment(UUID leaseId, UUID payerId, BigDecimal amount,
                       long periodStart, long periodEnd, String paymentMethod) {
        this(leaseId, payerId, amount, periodStart, periodEnd, paymentMethod, PaymentStatus.COMPLETED);
    }

    public LeasePayment(UUID leaseId, UUID payerId, BigDecimal amount,
                       long periodStart, long periodEnd, String paymentMethod, PaymentStatus status) {
        this.id = UUID.randomUUID();
        this.leaseId = leaseId;
        this.payerId = payerId;
        this.amount = amount;
        this.paymentTime = System.currentTimeMillis();
        this.status = status;
        this.paymentMethod = paymentMethod;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    /**
     * 兼容旧代码的 double 参数构造器（内部转为 BigDecimal）
     */
    public static LeasePayment fromDouble(UUID leaseId, UUID payerId, double amount,
                       long periodStart, long periodEnd, String paymentMethod) {
        return new LeasePayment(leaseId, payerId, BigDecimal.valueOf(amount),
            periodStart, periodEnd, paymentMethod, PaymentStatus.COMPLETED);
    }

    // ==================== 格式化方法 ====================

    /**
     * 获取格式化的支付时间
     */
    public String getFormattedPaymentTime() {
        long now = System.currentTimeMillis();
        long diff = now - paymentTime;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + " 天前";
        } else if (hours > 0) {
            return hours + " 小时前";
        } else if (minutes > 0) {
            return minutes + " 分钟前";
        } else {
            return "刚刚";
        }
    }

    /**
     * 获取格式化的租金周期（审计 A-049：用 Calendar 计算准确的月/周数，避免 /30 的粗略估算）
     */
    public String getFormattedPeriod() {
        Calendar startCal = Calendar.getInstance();
        startCal.setTimeInMillis(periodStart);
        Calendar endCal = Calendar.getInstance();
        endCal.setTimeInMillis(periodEnd);

        // 计算月数差异
        int months = 0;
        Calendar tmp = (Calendar) startCal.clone();
        while (tmp.before(endCal)) {
            tmp.add(Calendar.MONTH, 1);
            if (!tmp.after(endCal)) {
                months++;
            }
        }

        if (months >= 1) {
            return months + " 个月";
        }

        long duration = periodEnd - periodStart;
        long days = duration / (24L * 60 * 60 * 1000);

        if (days >= 7) {
            int weeks = (int) (days / 7);
            return weeks + " 周";
        } else {
            return days + " 天";
        }
    }

    // ==================== Getter ====================

    public UUID getId() {
        return id;
    }

    public UUID getLeaseId() {
        return leaseId;
    }

    public UUID getPayerId() {
        return payerId;
    }

    public double getAmount() {
        return amount.doubleValue();
    }

    public BigDecimal getAmountBigDecimal() {
        return amount;
    }

    public long getPaymentTime() {
        return paymentTime;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public long getPeriodStart() {
        return periodStart;
    }

    public long getPeriodEnd() {
        return periodEnd;
    }

    @Override
    public String toString() {
        return String.format("LeasePayment[id=%s, lease=%s, payer=%s, amount=%.2f, time=%s, status=%s]",
            id, leaseId, payerId, amount, getFormattedPaymentTime(), status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LeasePayment that = (LeasePayment) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    // ==================== 内部枚举 ====================

    /**
     * 支付状态
     */
    public enum PaymentStatus {
        COMPLETED("§a已完成", "支付成功"),
        PENDING("§e处理中", "支付处理中"),
        FAILED("§c失败", "支付失败"),
        REFUNDED("§7已退款", "已退款");

        private final String displayName;
        private final String description;

        PaymentStatus(String displayName, String description) {
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
}
