package dev.starcore.starcore.territory;

import dev.starcore.starcore.foundation.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 领土租赁服务
 * 管理领土的租赁、租金收取和到期处理
 */
public class TerritoryLeaseService {

    private final JavaPlugin plugin;
    private final TerritoryService territoryService;
    private final EconomyService economyService;

    // 租约存储 - ID -> Lease
    private final Map<UUID, TerritoryLease> leases = new ConcurrentHashMap<>();

    // 支付记录存储 - ID -> Payment
    private final Map<UUID, LeasePayment> payments = new ConcurrentHashMap<>();

    // 领土索引 - TerritoryID -> LeaseID
    private final Map<UUID, UUID> territoryIndex = new ConcurrentHashMap<>();

    // 房东索引 - LandlordID -> Set<LeaseID>
    private final Map<UUID, Set<UUID>> landlordIndex = new ConcurrentHashMap<>();

    // 租客索引 - TenantID -> Set<LeaseID>
    private final Map<UUID, Set<UUID>> tenantIndex = new ConcurrentHashMap<>();

    // 租约-支付索引 - LeaseID -> Set<PaymentID>
    private final Map<UUID, Set<UUID>> leasePaymentIndex = new ConcurrentHashMap<>();

    // 定时任务
    private BukkitTask rentCollectionTask;

    // 配置
    private double minRent = 100.0;
    private int maxDuration = 30; // 天
    private boolean autoRenew = true;
    private int evictionGraceDays = 3;

    public TerritoryLeaseService(JavaPlugin plugin, TerritoryService territoryService, EconomyService economyService) {
        this.plugin = plugin;
        this.territoryService = territoryService;
        this.economyService = economyService;
    }

    /**
     * 兼容旧构造函数（无 EconomyService）
     */
    public TerritoryLeaseService(JavaPlugin plugin, TerritoryService territoryService) {
        this(plugin, territoryService, null);
    }

    // ==================== 启动和关闭 ====================

    /**
     * 启动服务
     */
    public void start() {
        // 启动租金收取定时任务（每小时检查一次）
        rentCollectionTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::processRentCollection,
            20L * 60 * 60, // 1小时后首次执行
            20L * 60 * 60  // 每小时执行一次
        );

        plugin.getLogger().info("领土租赁服务已启动");
    }

    /**
     * 停止服务
     */
    public void stop() {
        if (rentCollectionTask != null) {
            rentCollectionTask.cancel();
            rentCollectionTask = null;
        }

        plugin.getLogger().info("领土租赁服务已停止");
    }

    // ==================== 创建和删除租约 ====================

    /**
     * 发布租赁（房东）
     */
    public TerritoryLease createLease(UUID territoryId, UUID landlordId,
                                      double rentAmount, TerritoryLease.RentPeriod rentPeriod,
                                      int leaseDuration) {
        // 验证领土存在
        Territory territory = territoryService.getTerritory(territoryId);
        if (territory == null) {
            plugin.getLogger().warning("领土不存在: " + territoryId);
            return null;
        }

        // 验证房东是否为领土所有者
        if (!territory.getOwnerId().equals(landlordId)) {
            plugin.getLogger().warning("只有领土所有者才能发布租赁");
            return null;
        }

        // 检查是否已有租约
        if (territoryIndex.containsKey(territoryId)) {
            plugin.getLogger().warning("该领土已有租约");
            return null;
        }

        // 验证租金
        if (rentAmount < minRent) {
            plugin.getLogger().warning("租金低于最低要求: " + minRent);
            return null;
        }

        // 验证租期
        if (leaseDuration > maxDuration) {
            plugin.getLogger().warning("租期超过最大限制: " + maxDuration);
            leaseDuration = maxDuration;
        }

        // 创建租约
        TerritoryLease lease = new TerritoryLease(territoryId, landlordId,
            rentAmount, rentPeriod, leaseDuration);

        // 存储租约
        leases.put(lease.getId(), lease);
        territoryIndex.put(territoryId, lease.getId());
        landlordIndex.computeIfAbsent(landlordId, k -> ConcurrentHashMap.newKeySet())
            .add(lease.getId());

        plugin.getLogger().info(String.format("创建租约: 领土=%s, 租金=%.2f/%s, 租期=%d天",
            territory.getName(), rentAmount, rentPeriod, leaseDuration));

        return lease;
    }

    /**
     * 租客接受租约
     */
    public boolean acceptLease(UUID leaseId, UUID tenantId) {
        TerritoryLease lease = leases.get(leaseId);
        if (lease == null) {
            return false;
        }

        if (lease.getStatus() != LeaseStatus.PENDING) {
            plugin.getLogger().warning("租约状态不正确: " + lease.getStatus());
            return false;
        }

        // 设置租客
        lease.setTenantId(tenantId);

        // 更新索引
        tenantIndex.computeIfAbsent(tenantId, k -> ConcurrentHashMap.newKeySet())
            .add(leaseId);

        // 审计 A-052: 将租客加入领土成员列表，授予 BUILD/INTERACT 权限
        Territory territory = territoryService.getTerritory(lease.getTerritoryId());
        if (territory != null) {
            territory.addMember(tenantId, dev.starcore.starcore.territory.PermissionLevel.MEMBER);
        }

        plugin.getLogger().info("租客 " + tenantId + " 接受租约 " + leaseId);
        return true;
    }

    /**
     * 取消租约
     */
    public boolean cancelLease(UUID leaseId, UUID requesterId) {
        TerritoryLease lease = leases.get(leaseId);
        if (lease == null) {
            return false;
        }

        // 验证权限（房东或租客）
        if (!lease.getLandlordId().equals(requesterId) &&
            !lease.getTenantId().equals(requesterId)) {
            plugin.getLogger().warning("无权取消租约");
            return false;
        }

        lease.cancel();
        plugin.getLogger().info("租约已取消: " + leaseId);
        return true;
    }

    /**
     * 删除租约
     */
    public boolean deleteLease(UUID leaseId) {
        TerritoryLease lease = leases.remove(leaseId);
        if (lease == null) {
            return false;
        }

        // 移除索引
        territoryIndex.remove(lease.getTerritoryId());

        Set<UUID> landlordLeases = landlordIndex.get(lease.getLandlordId());
        if (landlordLeases != null) {
            landlordLeases.remove(leaseId);
        }

        if (lease.getTenantId() != null) {
            Set<UUID> tenantLeases = tenantIndex.get(lease.getTenantId());
            if (tenantLeases != null) {
                tenantLeases.remove(leaseId);
            }
        }

        plugin.getLogger().info("删除租约: " + leaseId);
        return true;
    }

    // ==================== 租金支付 ====================

    /**
     * 支付租金
     */
    public LeasePayment payRent(UUID leaseId, UUID payerId, String paymentMethod) {
        TerritoryLease lease = leases.get(leaseId);
        if (lease == null) {
            plugin.getLogger().warning("租约不存在: " + leaseId);
            return null;
        }

        // 验证支付者（审计 A-050: 防止 tenantId 为 null 时 equals 抛 NPE）
        UUID tenantId = lease.getTenantId();
        if (tenantId == null || !tenantId.equals(payerId)) {
            plugin.getLogger().warning("只有租客才能支付租金");
            return null;
        }

        // 计算支付金额
        BigDecimal amount = BigDecimal.valueOf(lease.getNextPaymentAmount());

        // 审计 A-051: 接入 economyService 真实扣款和入账
        if (economyService != null) {
            // 先扣租客房款（同步写入确保扣款成功）
            if (!economyService.withdraw(payerId, amount)) {
                plugin.getLogger().warning("租客房款不足，支付失败: " + payerId);
                return null;
            }
            // 再给房东入账
            UUID landlordId = lease.getLandlordId();
            economyService.deposit(landlordId, amount);
            plugin.getLogger().info(String.format("真实经济交易: 租客=%s 扣除 %.2f, 房东=%s 入账 %.2f",
                payerId, amount, landlordId, amount));
        } else {
            plugin.getLogger().warning("EconomyService 未注入，模拟支付成功: " + leaseId);
        }

        // 创建支付记录
        long periodStart = lease.getNextPaymentTime() == 0 ?
            System.currentTimeMillis() : lease.getNextPaymentTime();
        long periodEnd = periodStart + lease.getRentPeriodMillis();

        LeasePayment payment = new LeasePayment(leaseId, payerId, amount,
            periodStart, periodEnd, paymentMethod);

        // 存储支付记录
        payments.put(payment.getId(), payment);
        leasePaymentIndex.computeIfAbsent(leaseId, k -> ConcurrentHashMap.newKeySet())
            .add(payment.getId());

        // 更新租约
        lease.recordPayment();

        plugin.getLogger().info(String.format("租金支付成功: 租约=%s, 金额=%.2f, 支付者=%s",
            leaseId, amount, payerId));

        return payment;
    }

    /**
     * 自动续租
     * 审计 A-053: 必须先实际扣款成功才能续约
     */
    public boolean autoRenewLease(UUID leaseId) {
        TerritoryLease lease = leases.get(leaseId);
        if (lease == null) {
            return false;
        }

        if (!lease.isAutoRenew() || !autoRenew) {
            return false;
        }

        // 审计 A-053: 先尝试扣款，扣款成功才续约
        UUID tenantId = lease.getTenantId();
        if (tenantId != null) {
            LeasePayment payment = payRent(leaseId, tenantId, "AUTO_RENEW");
            if (payment == null) {
                plugin.getLogger().warning("自动续租扣款失败: " + leaseId);
                return false;
            }
        }

        // 续租原租期
        int renewDays = lease.getLeaseDuration();
        lease.renew(renewDays);

        plugin.getLogger().info("自动续租: 租约=" + leaseId + ", 延长=" + renewDays + "天");
        return true;
    }

    // ==================== 定时任务 ====================

    /**
     * 处理租金收取和过期检查
     */
    private void processRentCollection() {
        int paidCount = 0;
        int overdueCount = 0;
        int expiredCount = 0;

        for (TerritoryLease lease : leases.values()) {
            if (!lease.isActive()) {
                continue;
            }

            // 检查是否需要支付
            if (lease.needsPayment()) {
                // 审计 A-054: 增量更新逾期天数，避免从 nextPaymentTime 累加全量天数
                // TODO audit A-054: 需在 TerritoryLease 中记录 lastOverdueCheckTime，增量更新
                // 临时方案：仅记录一次逾期，跳过累加（后续需完善）
                if (lease.getOverdueDays() == 0) {
                    lease.addOverdueDays(1);
                    overdueCount++;
                }

                // 检查是否超过宽限期
                if (lease.getOverdueDays() > evictionGraceDays) {
                    // 驱逐租客
                    evictTenant(lease.getId());
                    expiredCount++;
                    plugin.getLogger().warning("租客被驱逐（欠租）: " + lease.getTenantId());
                }
            }

            // 检查是否到期
            if (lease.isExpired()) {
                if (lease.isAutoRenew() && autoRenew) {
                    autoRenewLease(lease.getId());
                    paidCount++;
                } else {
                    lease.markExpired();
                    expiredCount++;
                    plugin.getLogger().info("租约已过期: " + lease.getId());
                }
            }
        }

        if (paidCount > 0 || overdueCount > 0 || expiredCount > 0) {
            plugin.getLogger().info(String.format("租金收取完成: 续租=%d, 欠租=%d, 过期=%d",
                paidCount, overdueCount, expiredCount));
        }
    }

    /**
     * 驱逐租客
     */
    private void evictTenant(UUID leaseId) {
        TerritoryLease lease = leases.get(leaseId);
        if (lease == null) {
            return;
        }

        // 标记为过期
        lease.markExpired();

        // 移除租客的领土权限
        Territory territory = territoryService.getTerritory(lease.getTerritoryId());
        if (territory != null && lease.getTenantId() != null) {
            territory.removeMember(lease.getTenantId());
        }
    }

    // ==================== 查询方法 ====================

    /**
     * 获取租约
     */
    public TerritoryLease getLease(UUID leaseId) {
        return leases.get(leaseId);
    }

    /**
     * 根据领土获取租约
     */
    public TerritoryLease getLeaseByTerritory(UUID territoryId) {
        UUID leaseId = territoryIndex.get(territoryId);
        return leaseId != null ? leases.get(leaseId) : null;
    }

    /**
     * 获取房东的所有租约
     */
    public List<TerritoryLease> getLeasesByLandlord(UUID landlordId) {
        Set<UUID> leaseIds = landlordIndex.get(landlordId);
        if (leaseIds == null) {
            return Collections.emptyList();
        }

        return leaseIds.stream()
            .map(leases::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 获取租客的所有租约
     */
    public List<TerritoryLease> getLeasesByTenant(UUID tenantId) {
        Set<UUID> leaseIds = tenantIndex.get(tenantId);
        if (leaseIds == null) {
            return Collections.emptyList();
        }

        return leaseIds.stream()
            .map(leases::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 获取租约的所有支付记录
     */
    public List<LeasePayment> getPaymentsByLease(UUID leaseId) {
        Set<UUID> paymentIds = leasePaymentIndex.get(leaseId);
        if (paymentIds == null) {
            return Collections.emptyList();
        }

        return paymentIds.stream()
            .map(payments::get)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingLong(LeasePayment::getPaymentTime).reversed())
            .collect(Collectors.toList());
    }

    /**
     * 获取所有租约
     */
    public Collection<TerritoryLease> getAllLeases() {
        return Collections.unmodifiableCollection(leases.values());
    }

    /**
     * 获取所有支付记录
     */
    public Collection<LeasePayment> getAllPayments() {
        return Collections.unmodifiableCollection(payments.values());
    }

    // ==================== 配置 ====================

    public void setMinRent(double minRent) {
        this.minRent = minRent;
    }

    public double getMinRent() {
        return minRent;
    }

    public void setMaxDuration(int maxDuration) {
        this.maxDuration = maxDuration;
    }

    public int getMaxDuration() {
        return maxDuration;
    }

    public void setAutoRenew(boolean autoRenew) {
        this.autoRenew = autoRenew;
    }

    public boolean isAutoRenew() {
        return autoRenew;
    }

    public void setEvictionGraceDays(int evictionGraceDays) {
        this.evictionGraceDays = evictionGraceDays;
    }

    public int getEvictionGraceDays() {
        return evictionGraceDays;
    }

    // ==================== 统计方法 ====================

    /**
     * 获取租约总数
     */
    public int getLeaseCount() {
        return leases.size();
    }

    /**
     * 获取生效中的租约数量
     */
    public int getActiveLeaseCount() {
        return (int) leases.values().stream()
            .filter(TerritoryLease::isActive)
            .count();
    }

    /**
     * 获取欠租的租约数量
     */
    public int getOverdueLeaseCount() {
        return (int) leases.values().stream()
            .filter(TerritoryLease::isOverdue)
            .count();
    }

    /**
     * 计算总租金收入
     */
    public double getTotalRentIncome() {
        return payments.values().stream()
            .mapToDouble(LeasePayment::getAmount)
            .sum();
    }

    /**
     * 清空所有数据
     */
    public void clearAll() {
        leases.clear();
        payments.clear();
        territoryIndex.clear();
        landlordIndex.clear();
        tenantIndex.clear();
        leasePaymentIndex.clear();
        plugin.getLogger().info("清空所有租赁数据");
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_leases", leases.size());
        stats.put("active_leases", getActiveLeaseCount());
        stats.put("overdue_leases", getOverdueLeaseCount());
        stats.put("total_payments", payments.size());
        stats.put("total_income", getTotalRentIncome());
        stats.put("landlords", landlordIndex.size());
        stats.put("tenants", tenantIndex.size());
        return stats;
    }
}
