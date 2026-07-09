package dev.starcore.starcore.quest;

import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.mechanics.ReputationService;
import dev.starcore.starcore.mechanics.ReputationSource;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 委托服务
 * 管理玩家发布和接取的委托任务
 *
 * @author StarCore Team
 * @since 1.0.0
 */
public class CommissionService {

    private static final String NAMESPACE = "quest";

    private final QuestService questService;
    private final EconomyService economyService;
    private final PersistenceService persistenceService;
    private final ReputationService reputationService;
    private final Logger logger;
    private final Map<String, Commission> activeCommissions; // 活跃的委托
    private final Map<UUID, List<String>> playerCommissions; // 玩家发布的委托
    private final Map<UUID, List<String>> acceptedCommissions; // 玩家接取的委托
    private final CommissionBoard commissionBoard;

    // 排行榜相关数据
    private final Map<UUID, CommissionStats> commissionLeaderboard; // 玩家委托统计
    private BukkitTask expirationCheckTask; // 过期检查任务
    private BukkitTask progressNotifyTask; // 进度通知任务

    private QuestPersistenceHandler persistenceHandler;

    private double commissionCreationCost = 100.0; // 发布委托的基础费用
    private double commissionTax = 0.1; // 委托税率（10%）
    private int maxCommissionsPerPlayer = 3; // 每个玩家最多发布的委托数
    private int maxAcceptedCommissions = 5; // 每个玩家最多接取的委托数
    private long commissionExpireTime = 7 * 24 * 60 * 60 * 1000L; // 7天过期

    /**
     * 构造函数
     */
    public CommissionService(QuestService questService, EconomyService economyService,
                            PersistenceService persistenceService, ReputationService reputationService,
                            Logger logger) {
        this.questService = questService;
        this.economyService = economyService;
        this.persistenceService = persistenceService;
        this.reputationService = reputationService;
        this.logger = logger;
        this.activeCommissions = new ConcurrentHashMap<>();
        this.playerCommissions = new ConcurrentHashMap<>();
        this.acceptedCommissions = new ConcurrentHashMap<>();
        this.commissionBoard = new CommissionBoard();
        this.commissionLeaderboard = new ConcurrentHashMap<>();
    }

    /**
     * 初始化持久化
     */
    public void initialize() {
        if (persistenceService != null) {
            QuestStateStorage stateStorage = new PersistenceQuestStateStorage(NAMESPACE, persistenceService);
            this.persistenceHandler = new QuestPersistenceHandler(stateStorage, logger);

            // 加载已保存的委托数据
            persistenceHandler.loadCommissions(activeCommissions, playerCommissions, acceptedCommissions);

            // 加载排行榜数据
            persistenceHandler.loadCommissionLeaderboard(commissionLeaderboard);

            // 清理过期委托
            cleanupExpiredCommissions();

            // 重新构建委托板
            commissionBoard.clear();
            for (Commission commission : activeCommissions.values()) {
                if (!commission.isCompleted() && !commission.isExpired()) {
                    commissionBoard.addCommission(commission);
                }
            }
        }
    }

    /**
     * 设置调度器（供QuestModule调用）
     */
    public void setScheduler(org.bukkit.plugin.java.JavaPlugin plugin) {
        // 启动过期检查任务（每5分钟检查一次）
        expirationCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            checkAndNotifyExpirations();
        }, 60 * 20L, 5 * 60 * 20L); // 延迟1分钟，之后每5分钟

        // 启动进度通知任务（每30秒检查一次）
        progressNotifyTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            checkAndNotifyProgress();
        }, 30 * 20L, 30 * 20L); // 延迟30秒，之后每30秒

        logger.info("[CommissionService] 定时任务已启动");
    }

    /**
     * 关闭定时任务
     */
    public void shutdown() {
        if (expirationCheckTask != null) {
            expirationCheckTask.cancel();
            expirationCheckTask = null;
        }
        if (progressNotifyTask != null) {
            progressNotifyTask.cancel();
            progressNotifyTask = null;
        }
        logger.info("[CommissionService] 定时任务已关闭");
    }

    /**
     * 检查并通知即将过期的委托
     */
    private void checkAndNotifyExpirations() {
        long now = System.currentTimeMillis();
        long oneHour = 60 * 60 * 1000L;
        long oneDay = 24 * 60 * 60 * 1000L;

        for (Commission commission : activeCommissions.values()) {
            if (commission.isCompleted() || commission.isExpired()) {
                continue;
            }

            long remaining = commission.getRemainingTime();
            UUID acceptorId = commission.getAcceptorId();

            // 提前1小时通知
            if (remaining > 0 && remaining <= oneHour && !commission.isAcceptorNotified()) {
                if (acceptorId != null) {
                    Player player = Bukkit.getPlayer(acceptorId);
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§c[委托警告] 委托 \"" + commission.getTitle() + "\" 还有1小时就要过期了！");
                        commission.setAcceptorNotified(true);
                    }
                }
            }

            // 提前1天通知
            if (remaining > 0 && remaining <= oneDay && remaining > oneHour) {
                if (acceptorId != null) {
                    Player player = Bukkit.getPlayer(acceptorId);
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§e[委托提醒] 委托 \"" + commission.getTitle() + "\" 还有1天就要过期了！");
                    }
                }
            }
        }
    }

    /**
     * 检查并通知进度
     */
    private void checkAndNotifyProgress() {
        for (Commission commission : activeCommissions.values()) {
            if (commission.isCompleted() || commission.isExpired() || !commission.isAccepted()) {
                continue;
            }

            // 检查是否已完成目标但还未提交
            if (commission.isTargetComplete() && commission.needsVerification()) {
                UUID acceptorId = commission.getAcceptorId();
                if (acceptorId != null) {
                    Player player = Bukkit.getPlayer(acceptorId);
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§a[委托提示] 委托 \"" + commission.getTitle() + "\" 已达到目标！请使用 /commission complete 提交完成。");
                    }
                }
            }
        }
    }

    /**
     * 持久化委托数据（异步）
     */
    public void saveAsync() {
        if (persistenceHandler != null) {
            persistenceHandler.saveCommissionsAsync(
                new HashMap<>(activeCommissions),
                new HashMap<>(playerCommissions),
                new HashMap<>(acceptedCommissions),
                new HashMap<>(commissionLeaderboard)
            );
        }
    }

    /**
     * 持久化委托数据（同步）
     */
    public void save() {
        if (persistenceHandler != null) {
            persistenceHandler.saveCommissions(
                new HashMap<>(activeCommissions),
                new HashMap<>(playerCommissions),
                new HashMap<>(acceptedCommissions)
            );
            // 同时保存排行榜
            persistenceHandler.saveCommissionLeaderboard(new HashMap<>(commissionLeaderboard));
        }
    }

    /**
     * 创建委托
     */
    public CommissionCreateResult createCommission(Player publisher, Commission commission) {
        UUID publisherId = publisher.getUniqueId();

        // 检查委托数量限制
        List<String> playerCommissionList = playerCommissions.getOrDefault(publisherId, new ArrayList<>());
        if (playerCommissionList.size() >= maxCommissionsPerPlayer) {
            return new CommissionCreateResult(false, "您已达到委托发布数量上限");
        }

        // 检查并扣除发布费用
        double totalCost = commissionCreationCost + (commission.getReward() * commissionTax);
        if (economyService != null) {
            if (!economyService.has(publisherId, BigDecimal.valueOf(totalCost))) {
                return new CommissionCreateResult(false, "金币不足，需要 " + totalCost + " 金币");
            }
            // 扣除费用
            economyService.withdraw(publisherId, BigDecimal.valueOf(totalCost));
            publisher.sendMessage("§e发布委托消耗 " + totalCost + " 金币");
        }

        // 注册委托
        String commissionId = UUID.randomUUID().toString();
        commission.setId(commissionId);
        commission.setPublisherId(publisherId);
        commission.setPublishTime(System.currentTimeMillis());

        activeCommissions.put(commissionId, commission);
        playerCommissionList.add(commissionId);
        playerCommissions.put(publisherId, playerCommissionList);

        // 添加到委托板
        commissionBoard.addCommission(commission);

        // 自动保存
        saveAsync();

        return new CommissionCreateResult(true, "委托发布成功", commissionId);
    }

    /**
     * 接取委托。D-072: 同步对象防止“两玩家同时通过 isAccepted=false 校验后双重接取”。
     */
    public CommissionAcceptResult acceptCommission(Player player, String commissionId) {
        UUID playerId = player.getUniqueId();
        Commission commission = activeCommissions.get(commissionId);

        if (commission == null) {
            return new CommissionAcceptResult(false, "委托不存在");
        }

        // D-072: 同步 commission 对象做原子接取（CAS）—— 在锁内重新校验 isAccepted
        synchronized (commission) {
            if (commission.getPublisherId().equals(playerId)) {
                return new CommissionAcceptResult(false, "不能接取自己发布的委托");
            }

            if (commission.isAccepted()) {
                return new CommissionAcceptResult(false, "委托已被接取");
            }

            if (commission.isExpired()) {
                return new CommissionAcceptResult(false, "委托已过期");
            }

            // 检查接取数量限制
            List<String> accepted = acceptedCommissions.getOrDefault(playerId, new ArrayList<>());
            if (accepted.size() >= maxAcceptedCommissions) {
                return new CommissionAcceptResult(false, "已达到委托接取数量上限");
            }

            // 检查等级要求
            if (player.getLevel() < commission.getMinLevel()) {
                return new CommissionAcceptResult(false, "等级不足");
            }

            // 检查声望要求
            if (commission.getMinReputation() > 0) {
                if (reputationService == null) {
                    return new CommissionAcceptResult(false, "声望系统未启用");
                }
                int playerReputation = reputationService.getReputation(playerId).getTotalReputation();
                if (playerReputation < commission.getMinReputation()) {
                    return new CommissionAcceptResult(false, "声望不足，需要 " + commission.getMinReputation() + " 声望");
                }
            }

            // 接取委托
            commission.setAcceptorId(playerId);
            commission.setAcceptTime(System.currentTimeMillis());

            accepted.add(commissionId);
            acceptedCommissions.put(playerId, accepted);

            // 自动保存
            saveAsync();

            return new CommissionAcceptResult(true, "委托接取成功");
        }
    }

    /**
     * 完成委托
     * @param commissionId 委托ID
     * @param playerId 声称完成的玩家ID（用于验证是否是其接取的委托）
     */
    public CommissionCompleteResult completeCommission(String commissionId, UUID playerId) {
        Commission commission = activeCommissions.get(commissionId);

        if (commission == null) {
            return new CommissionCompleteResult(false, "委托不存在");
        }

        if (!commission.isAccepted()) {
            return new CommissionCompleteResult(false, "委托未被接取");
        }

        if (commission.isCompleted()) {
            return new CommissionCompleteResult(false, "委托已完成");
        }

        // 验证：只有接取者可以提交完成
        if (!commission.getAcceptorId().equals(playerId)) {
            return new CommissionCompleteResult(false, "只有接取者可以完成此委托");
        }

        // ===== 核心修复：目标验证 =====
        if (commission.needsVerification() && !commission.isTargetComplete()) {
            // 根据委托类型提供具体提示
            String hint = getProgressHint(commission);
            return new CommissionCompleteResult(false, "目标未完成！当前进度: " +
                commission.getCurrentProgress() + "/" + commission.getTargetAmount() + hint);
        }

        // BUILD类型需要发布者确认
        if (commission.getType() == Commission.CommissionType.BUILD && !commission.isPublisherConfirmed()) {
            return new CommissionCompleteResult(false, "等待发布者确认完成，请使用 /commission confirm <ID> 让发布者确认");
        }

        // 标记为完成
        commission.setCompleted(true);
        commission.setCompleteTime(System.currentTimeMillis());

        // ===== 完善奖励发放 =====
        UUID acceptorId = commission.getAcceptorId();
        double rewardAmount = commission.getReward();

        // 发放金币奖励
        if (economyService != null) {
            economyService.deposit(acceptorId, BigDecimal.valueOf(rewardAmount));
            Player acceptor = Bukkit.getPlayer(acceptorId);
            if (acceptor != null && acceptor.isOnline()) {
                acceptor.sendMessage("§a[委托] 完成委托，获得 §6" + String.format("%.2f", rewardAmount) + " §a金币奖励！");
            }
        }

        // 发放声望奖励
        int reputationReward = calculateReputationReward(commission);
        if (reputationReward > 0 && reputationService != null) {
            reputationService.addReputation(acceptorId, ReputationSource.QUEST, reputationReward);
            Player acceptor = Bukkit.getPlayer(acceptorId);
            if (acceptor != null && acceptor.isOnline()) {
                acceptor.sendMessage("§6[委托] 获得 §e" + reputationReward + " §6声望奖励！");
            }
        }

        // ===== 更新排行榜统计 =====
        updateLeaderboardStats(acceptorId, commission);

        // 通知发布者委托完成
        UUID publisherId = commission.getPublisherId();
        Player publisher = Bukkit.getPlayer(publisherId);
        if (publisher != null && publisher.isOnline()) {
            publisher.sendMessage("§a[委托] 您发布的委托 \"" + commission.getTitle() + "\" 已被完成！");
        }

        // 从列表中移除
        acceptedCommissions.getOrDefault(acceptorId, new ArrayList<>()).remove(commissionId);
        // D-075: 同时从发布者的 playerCommissions 列表移除，避免列表持续增长
        if (publisherId != null) {
            playerCommissions.getOrDefault(publisherId, new ArrayList<>()).remove(commissionId);
        }

        // 自动保存
        saveAsync();

        return new CommissionCompleteResult(true, "委托完成", commission.getReward());
    }

    /**
     * 发布者确认委托完成（BUILD类型）
     */
    public CommissionCompleteResult confirmCommission(String commissionId, UUID publisherId) {
        Commission commission = activeCommissions.get(commissionId);

        if (commission == null) {
            return new CommissionCompleteResult(false, "委托不存在");
        }

        if (!commission.getPublisherId().equals(publisherId)) {
            return new CommissionCompleteResult(false, "只有发布者可以确认委托完成");
        }

        if (commission.getType() != Commission.CommissionType.BUILD) {
            return new CommissionCompleteResult(false, "只有建造类委托需要确认");
        }

        if (!commission.isAccepted()) {
            return new CommissionCompleteResult(false, "委托还未被接取");
        }

        if (commission.isCompleted()) {
            return new CommissionCompleteResult(false, "委托已完成");
        }

        // 确认完成
        commission.confirmByPublisher();

        // 通知接取者
        UUID acceptorId = commission.getAcceptorId();
        if (acceptorId != null) {
            Player acceptor = Bukkit.getPlayer(acceptorId);
            if (acceptor != null && acceptor.isOnline()) {
                acceptor.sendMessage("§a[委托] 发布者已确认您的建造委托完成！请使用 /commission complete 领取奖励。");
            }
        }

        saveAsync();

        return new CommissionCompleteResult(true, "已确认委托完成");
    }

    // ========== 进度追踪相关方法 ==========

    /**
     * 获取玩家所有接取委托的进度摘要
     */
    public List<CommissionProgress> getPlayerProgressSummary(UUID playerId) {
        List<CommissionProgress> summary = new ArrayList<>();
        List<Commission> accepted = getPlayerAcceptedCommissions(playerId);

        for (Commission commission : accepted) {
            if (!commission.isCompleted() && !commission.isExpired()) {
                summary.add(new CommissionProgress(
                    commission.getId(),
                    commission.getTitle(),
                    commission.getType(),
                    commission.getCurrentProgress(),
                    commission.getTargetAmount(),
                    commission.getRemainingTime(),
                    commission.getStatus()
                ));
            }
        }

        return summary;
    }

    /**
     * 获取进度详情
     */
    public String getProgressDetails(Commission commission) {
        if (!commission.needsVerification() && commission.getType() != Commission.CommissionType.BUILD) {
            return "此委托无需进度追踪";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("§e委托: §f").append(commission.getTitle()).append("\n");
        sb.append("§7类型: §f").append(commission.getType().getDisplayName()).append("\n");

        if (commission.needsVerification()) {
            int current = commission.getCurrentProgress();
            int target = commission.getTargetAmount();
            double percent = target > 0 ? (double) current / target * 100 : 0;

            sb.append("§7进度: §a").append(current).append("§7/§e").append(target);
            sb.append(String.format(" §7(%.1f%%)", percent)).append("\n");

            switch (commission.getType()) {
                case KILL:
                    sb.append("§7目标: §f击杀 ").append(commission.getTargetEntity()).append("\n");
                    break;
                case COLLECT:
                    sb.append("§7目标: §f收集 ").append(commission.getTargetItem()).append("\n");
                    break;
                case BUILD:
                    sb.append("§7目标: §f建造 ").append(commission.getTargetItem()).append("\n");
                    break;
                case EXPLORE:
                    sb.append("§7目标: §f探索 ").append(commission.getTargetLocation()).append("\n");
                    break;
            }
        }

        if (commission.getType() == Commission.CommissionType.BUILD) {
            if (commission.isPublisherConfirmed()) {
                sb.append("§a状态: 发布者已确认\n");
            } else {
                sb.append("§e状态: 等待发布者确认\n");
            }
        }

        sb.append("§7剩余时间: §f").append(commission.getRemainingTimeText());

        return sb.toString();
    }

    // ========== 内部类 ==========

    /**
     * 完成委托（兼容旧API，仅检查是否已接取）
     * @deprecated 使用 {@link #completeCommission(String, UUID)} 代替以启用安全验证
     */
    @Deprecated
    public CommissionCompleteResult completeCommission(String commissionId) {
        Commission commission = activeCommissions.get(commissionId);
        if (commission != null && commission.isAccepted()) {
            return completeCommission(commissionId, commission.getAcceptorId());
        }
        return new CommissionCompleteResult(false, "委托不存在或未被接取");
    }

    /**
     * 获取进度提示文本
     */
    private String getProgressHint(Commission commission) {
        switch (commission.getType()) {
            case KILL:
                String entityName = commission.getTargetEntity() != null ? commission.getTargetEntity() : "目标";
                return " (" + entityName + " " + commission.getCurrentProgress() + "/" + commission.getTargetAmount() + ")";
            case COLLECT:
                return " (收集物品: " + commission.getCurrentProgress() + "/" + commission.getTargetAmount() + ")";
            case BUILD:
                return " (请等待发布者确认)";
            case EXPLORE:
                return " (探索位置: " + commission.getTargetLocation() + ")";
            default:
                return "";
        }
    }

    /**
     * 更新委托进度（供事件监听器调用）
     * @param playerId 玩家ID
     * @param commissionId 委托ID
     * @param progress 增加的进度
     * @return 是否成功更新
     */
    public boolean updateProgress(UUID playerId, String commissionId, int progress) {
        Commission commission = activeCommissions.get(commissionId);
        if (commission == null) return false;
        if (!commission.isAccepted()) return false;
        if (!commission.getAcceptorId().equals(playerId)) return false;
        if (commission.isCompleted()) return false;

        commission.addProgress(progress);

        // 如果是击杀类委托且达到目标，通知玩家
        if (commission.getType() == Commission.CommissionType.KILL && commission.isTargetComplete()) {
            Player player = org.bukkit.Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage("§a[委托] 击杀目标已完成！可使用 /commission complete <ID> 提交完成");
            }
        }

        saveAsync();
        return true;
    }

    /**
     * 取消委托
     */
    public boolean cancelCommission(UUID publisherId, String commissionId) {
        Commission commission = activeCommissions.get(commissionId);

        if (commission == null) {
            return false;
        }

        if (!commission.getPublisherId().equals(publisherId)) {
            return false;
        }

        if (commission.isAccepted()) {
            return false; // 已被接取的委托不能取消
        }

        // D-073: 退还应含税金，避免发布者通过先高赏金再取消套利。
        // 创建时扣除 totalCost = commissionCreationCost + reward * tax；这里按相同公式退还 80%
        double totalCostCharged = commissionCreationCost + (commission.getReward() * commissionTax);
        double refundAmount = totalCostCharged * 0.8; // 退还80%（含税金部分）

        // 移除委托
        activeCommissions.remove(commissionId);
        playerCommissions.getOrDefault(publisherId, new ArrayList<>()).remove(commissionId);
        commissionBoard.removeCommission(commissionId);

        // 退还部分费用
        if (economyService != null) {
            economyService.deposit(publisherId, BigDecimal.valueOf(refundAmount));
            Player publisher = org.bukkit.Bukkit.getPlayer(publisherId);
            if (publisher != null && publisher.isOnline()) {
                publisher.sendMessage("§a[委托] 委托已取消，退还 " + refundAmount + " 金币");
            }
        }

        // 自动保存
        saveAsync();

        return true;
    }

    /**
     * 放弃委托。
     * D-074: 通知发布者委托已被放弃，重新挂回可接取状态（不退还保证金给发布者，
     * 但委托可被新玩家接取，最终完成时保证金照样发挥作用）。
     */
    public boolean abandonCommission(UUID playerId, String commissionId) {
        Commission commission = activeCommissions.get(commissionId);

        if (commission == null) {
            return false;
        }

        if (!playerId.equals(commission.getAcceptorId())) {
            return false;
        }

        // 重置委托状态使其可被重新接取
        commission.setAcceptorId(null);
        commission.setAcceptTime(0);

        acceptedCommissions.getOrDefault(playerId, new ArrayList<>()).remove(commissionId);

        // D-074: 通知发布者
        UUID publisherId = commission.getPublisherId();
        if (publisherId != null) {
            Player publisher = org.bukkit.Bukkit.getPlayer(publisherId);
            if (publisher != null && publisher.isOnline()) {
                String title = commission.getTitle() != null ? commission.getTitle() : commissionId;
                publisher.sendMessage("§e[委托] 接取者已放弃你的委托 §f" + title
                    + " §e，该委托已重新挂回可被接取状态。");
            }
        }

        // 自动保存
        saveAsync();

        return true;
    }

    // ========== 排行榜相关方法 ==========

    /**
     * 更新玩家排行榜统计
     */
    private void updateLeaderboardStats(UUID playerId, Commission commission) {
        CommissionStats stats = commissionLeaderboard.computeIfAbsent(playerId, k -> new CommissionStats(playerId));
        // D-079: 同步 playerName，排行榜 /f 的玩家名不再为空
        if (stats.getPlayerName() == null || stats.getPlayerName().isEmpty()) {
            org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(playerId);
            String name = off.getName();
            if (name != null) stats.setPlayerName(name);
        }
        stats.recordCompletion(commission.getReward(), commission.getDifficulty());
    }

    /**
     * 获取玩家委托统计
     */
    public CommissionStats getPlayerStats(UUID playerId) {
        CommissionStats stats = commissionLeaderboard.computeIfAbsent(playerId, k -> new CommissionStats(playerId));
        // D-079: 懒加载 playerName
        if (stats.getPlayerName() == null || stats.getPlayerName().isEmpty()) {
            org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(playerId);
            String name = off.getName();
            if (name != null) stats.setPlayerName(name);
        }
        return stats;
    }

    /**
     * 获取委托排行榜
     * @param type 排行榜类型: "completed"=完成数, "earned"=收益, "streak"=连续天数
     * @param limit 返回数量限制
     */
    public List<CommissionStats> getLeaderboard(String type, int limit) {
        List<CommissionStats> sorted = new ArrayList<>(commissionLeaderboard.values());

        switch (type.toLowerCase()) {
            case "earned":
            case "reward":
                sorted.sort((a, b) -> Double.compare(b.getTotalEarned(), a.getTotalEarned()));
                break;
            case "streak":
                sorted.sort((a, b) -> Integer.compare(b.getCurrentStreak(), a.getCurrentStreak()));
                break;
            case "completed":
            default:
                sorted.sort((a, b) -> Integer.compare(b.getTotalCompleted(), a.getTotalCompleted()));
                break;
        }

        return sorted.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * 获取玩家排行榜排名
     */
    public int getPlayerRank(UUID playerId, String type) {
        List<CommissionStats> leaderboard = getLeaderboard(type, 100);
        for (int i = 0; i < leaderboard.size(); i++) {
            if (leaderboard.get(i).getPlayerId().equals(playerId)) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * 获取排行榜类型列表
     */
    public static String[] getLeaderboardTypes() {
        return new String[]{"completed", "earned", "streak"};
    }

    /**
     * 获取委托板
     */
    public CommissionBoard getCommissionBoard() {
        return commissionBoard;
    }

    /**
     * 获取玩家发布的委托
     */
    public List<Commission> getPlayerPublishedCommissions(UUID playerId) {
        return playerCommissions.getOrDefault(playerId, Collections.emptyList())
                .stream()
                .map(activeCommissions::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取玩家接取的委托
     */
    public List<Commission> getPlayerAcceptedCommissions(UUID playerId) {
        return acceptedCommissions.getOrDefault(playerId, Collections.emptyList())
                .stream()
                .map(activeCommissions::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 根据ID获取委托
     */
    public Commission getCommission(String commissionId) {
        return activeCommissions.get(commissionId);
    }

    /**
     * 计算声望奖励
     * 根据难度和委托类型计算奖励声望
     */
    private int calculateReputationReward(Commission commission) {
        // 基础声望奖励
        int baseReward = 10;

        // 根据难度加成
        switch (commission.getDifficulty()) {
            case EASY:
                baseReward = 5;
                break;
            case NORMAL:
                baseReward = 10;
                break;
            case HARD:
                baseReward = 20;
                break;
            case LEGENDARY:
                baseReward = 50;
                break;
            case NIGHTMARE:
                baseReward = 100;
                break;
        }

        // 根据委托类型加成
        switch (commission.getType()) {
            case KILL:
                baseReward = (int) (baseReward * 1.5);
                break;
            case EXPLORE:
            case ESCORT:
                baseReward = (int) (baseReward * 1.3);
                break;
            case BUILD:
                baseReward = (int) (baseReward * 1.2);
                break;
            default:
                break;
        }

        // 根据赏金加成（每100金币+1声望）
        baseReward += (int) (commission.getReward() / 100);

        return baseReward;
    }

    /**
     * 清理过期委托
     */
    public void cleanupExpiredCommissions() {
        List<String> expiredIds = new ArrayList<>();

        for (Commission commission : activeCommissions.values()) {
            if (commission.isExpired()) {
                expiredIds.add(commission.getId());
            }
        }

        for (String commissionId : expiredIds) {
            Commission commission = activeCommissions.remove(commissionId);
            if (commission != null) {
                playerCommissions.getOrDefault(commission.getPublisherId(), new ArrayList<>())
                        .remove(commissionId);
                commissionBoard.removeCommission(commissionId);
            }
        }
    }

    // Getters and Setters

    public double getCommissionCreationCost() {
        return commissionCreationCost;
    }

    public void setCommissionCreationCost(double commissionCreationCost) {
        this.commissionCreationCost = commissionCreationCost;
    }

    public double getCommissionTax() {
        return commissionTax;
    }

    public void setCommissionTax(double commissionTax) {
        this.commissionTax = commissionTax;
    }

    public int getMaxCommissionsPerPlayer() {
        return maxCommissionsPerPlayer;
    }

    public void setMaxCommissionsPerPlayer(int maxCommissionsPerPlayer) {
        this.maxCommissionsPerPlayer = maxCommissionsPerPlayer;
    }

    public int getMaxAcceptedCommissions() {
        return maxAcceptedCommissions;
    }

    public void setMaxAcceptedCommissions(int maxAcceptedCommissions) {
        this.maxAcceptedCommissions = maxAcceptedCommissions;
    }

    public long getCommissionExpireTime() {
        return commissionExpireTime;
    }

    public void setCommissionExpireTime(long commissionExpireTime) {
        this.commissionExpireTime = commissionExpireTime;
    }

    /**
     * 委托创建结果
     */
    public static class CommissionCreateResult {
        private final boolean success;
        private final String message;
        private final String commissionId;

        public CommissionCreateResult(boolean success, String message) {
            this(success, message, null);
        }

        public CommissionCreateResult(boolean success, String message, String commissionId) {
            this.success = success;
            this.message = message;
            this.commissionId = commissionId;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getCommissionId() {
            return commissionId;
        }
    }

    /**
     * 委托接取结果
     */
    public static class CommissionAcceptResult {
        private final boolean success;
        private final String message;

        public CommissionAcceptResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 委托完成结果
     */
    public static class CommissionCompleteResult {
        private final boolean success;
        private final String message;
        private final double reward;

        public CommissionCompleteResult(boolean success, String message) {
            this(success, message, 0);
        }

        public CommissionCompleteResult(boolean success, String message, double reward) {
            this.success = success;
            this.message = message;
            this.reward = reward;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public double getReward() {
            return reward;
        }
    }

    // ========== 排行榜统计类 ==========

    /**
     * 委托排行榜统计类
     */
    public static class CommissionStats {
        private final UUID playerId;
        private String playerName;
        private int totalCompleted; // 总完成数
        private double totalEarned; // 总获得赏金
        private int currentStreak; // 当前连续完成天数
        private int longestStreak; // 最长连续天数
        private int weeklyCompleted; // 本周完成数
        private int monthlyCompleted; // 本月完成数
        private long lastCompletedTime; // 上次完成时间
        private Map<QuestDifficulty, Integer> completedByDifficulty; // 按难度统计

        public CommissionStats(UUID playerId) {
            this.playerId = playerId;
            this.playerName = "";
            this.totalCompleted = 0;
            this.totalEarned = 0;
            this.currentStreak = 0;
            this.longestStreak = 0;
            this.weeklyCompleted = 0;
            this.monthlyCompleted = 0;
            this.lastCompletedTime = 0;
            this.completedByDifficulty = new EnumMap<>(QuestDifficulty.class);
        }

        /**
         * 记录完成
         */
        public void recordCompletion(double reward, QuestDifficulty difficulty) {
            this.totalCompleted++;
            this.totalEarned += reward;

            // 更新难度统计
            completedByDifficulty.merge(difficulty, 1, Integer::sum);

            // D-076: updateStreak 必须使用“更新前”的 lastCompletedTime；否则逻辑判断会失效。
            long prevTime = this.lastCompletedTime;
            this.lastCompletedTime = System.currentTimeMillis();
            updateStreak(prevTime);

            // D-077: updatePeriodicStats 同步重置周/月边界
            updatePeriodicStats();
        }

        /**
         * 更新连续天数。D-076: 接收 prevTime 为更新前时间，避免顺序错位。
         */
        private void updateStreak(long prevTime) {
            long now = System.currentTimeMillis();
            long oneDay = 24L * 60 * 60 * 1000;

            if (prevTime == 0) {
                this.currentStreak = 1;
            } else {
                long daysSinceLast = (now - prevTime) / oneDay;
                if (daysSinceLast <= 1) {
                    this.currentStreak++;
                } else {
                    this.currentStreak = 1;
                }
            }

            if (currentStreak > longestStreak) {
                this.longestStreak = currentStreak;
            }
        }

        /**
         * 更新周期统计。D-077: 区分周/月边界并在跨周期时重置，否则weekly/monthly永远累计。
         */
        private void updatePeriodicStats() {
            long now = System.currentTimeMillis();
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(now);
            int weekOfYear = cal.get(java.util.Calendar.WEEK_OF_YEAR);
            int month = cal.get(java.util.Calendar.MONTH);

            if (this.weekResetWeek != weekOfYear) {
                this.weeklyCompleted = 0;
                this.weekResetWeek = weekOfYear;
            }
            if (this.monthResetMonth != month) {
                this.monthlyCompleted = 0;
                this.monthResetMonth = month;
            }
            this.weeklyCompleted++;
            this.monthlyCompleted++;
        }

        // D-077: 记录周/月重置边界
        private int weekResetWeek = -1;
        private int monthResetMonth = -1;

        /**
         * 获取平均赏金
         */
        public double getAverageReward() {
            return totalCompleted > 0 ? totalEarned / totalCompleted : 0;
        }

        // Getters
        public UUID getPlayerId() {
            return playerId;
        }

        public String getPlayerName() {
            return playerName;
        }

        public void setPlayerName(String playerName) {
            this.playerName = playerName;
        }

        public int getTotalCompleted() {
            return totalCompleted;
        }

        public double getTotalEarned() {
            return totalEarned;
        }

        public int getCurrentStreak() {
            return currentStreak;
        }

        public int getLongestStreak() {
            return longestStreak;
        }

        public int getWeeklyCompleted() {
            return weeklyCompleted;
        }

        public int getMonthlyCompleted() {
            return monthlyCompleted;
        }

        public long getLastCompletedTime() {
            return lastCompletedTime;
        }

        public Map<QuestDifficulty, Integer> getCompletedByDifficulty() {
            return new EnumMap<>(completedByDifficulty);
        }

        /**
         * 获取排行榜条目文本
         */
        public String getLeaderboardEntry(int rank, String displayType) {
            String suffix = "";
            switch (displayType) {
                case "earned":
                    suffix = String.format("%.2f 金币", totalEarned);
                    break;
                case "streak":
                    suffix = currentStreak + " 天连续";
                    break;
                default:
                    suffix = totalCompleted + " 次";
            }
            return String.format("§e#%d §f%s §7- §a%s", rank, playerName, suffix);
        }
    }

    // ========== 进度追踪类 ==========

    /**
     * 委托进度类
     */
    public static class CommissionProgress {
        private final String commissionId;
        private final String title;
        private final Commission.CommissionType type;
        private final int currentProgress;
        private final int targetProgress;
        private final long remainingTime;
        private final String status;

        public CommissionProgress(String commissionId, String title, Commission.CommissionType type,
                                  int currentProgress, int targetProgress, long remainingTime, String status) {
            this.commissionId = commissionId;
            this.title = title;
            this.type = type;
            this.currentProgress = currentProgress;
            this.targetProgress = targetProgress;
            this.remainingTime = remainingTime;
            this.status = status;
        }

        public String getCommissionId() {
            return commissionId;
        }

        public String getTitle() {
            return title;
        }

        public Commission.CommissionType getType() {
            return type;
        }

        public int getCurrentProgress() {
            return currentProgress;
        }

        public int getTargetProgress() {
            return targetProgress;
        }

        public long getRemainingTime() {
            return remainingTime;
        }

        public String getStatus() {
            return status;
        }

        public double getProgressPercent() {
            return targetProgress > 0 ? (double) currentProgress / targetProgress * 100 : 0;
        }

        public String getProgressText() {
            if (targetProgress <= 0) {
                return status;
            }
            return String.format("%d/%d (%.0f%%)", currentProgress, targetProgress, getProgressPercent());
        }

        public String getRemainingTimeText() {
            if (remainingTime <= 0) {
                return "已过期";
            }
            long hours = remainingTime / (60 * 60 * 1000);
            long minutes = (remainingTime % (60 * 60 * 1000)) / (60 * 1000);
            if (hours > 0) {
                return hours + "小时" + minutes + "分钟";
            }
            return minutes + "分钟";
        }
    }
}
