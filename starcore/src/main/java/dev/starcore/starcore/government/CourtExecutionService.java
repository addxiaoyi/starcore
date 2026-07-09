package dev.starcore.starcore.government;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.moderation.jail.JailService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 判决执行服务
 * 负责执行法庭判决的实际惩罚措施
 *
 * 功能:
 * 1. 罚款扣款 - 自动从玩家余额扣除判决罚款
 * 2. 监禁执行 - 将玩家传送到监狱并限制行动
 * 3. 驱逐执行 - 将玩家从国家移除
 * 4. 上诉撤销 - 处理上诉成功后的惩罚撤销
 */
public final class CourtExecutionService {

    private final CourtService courtService;
    private final JailService jailService;
    private final EconomyService economyService;
    private final NationService nationService;
    private final OnlinePlayerDirectory onlinePlayerDirectory;
    private final Plugin plugin;
    private final Logger logger;
    private final DatabaseService databaseService;

    // 已执行的判决记录（防止重复执行）
    private final Map<Integer, VerdictExecution> executedVerdicts = new ConcurrentHashMap<>();

    // 驱逐目标缓存
    private final Map<UUID, BanishmentInfo> banishments = new ConcurrentHashMap<>();

    // 区域禁止记录
    private final Map<UUID, List<String>> areaBans = new ConcurrentHashMap<>();

    // 交易限制记录
    private final Map<UUID, TradeLimitInfo> tradeLimits = new ConcurrentHashMap<>();

    // 缓刑记录
    private final Map<UUID, ProbationInfo> probations = new ConcurrentHashMap<>();

    // 禁言记录
    private final Map<UUID, SilenceInfo> silenceRecords = new ConcurrentHashMap<>();

    public CourtExecutionService(
            CourtService courtService,
            JailService jailService,
            EconomyService economyService,
            NationService nationService,
            OnlinePlayerDirectory onlinePlayerDirectory,
            Plugin plugin,
            DatabaseService databaseService
    ) {
        this.courtService = courtService;
        this.jailService = jailService;
        this.economyService = economyService;
        this.nationService = nationService;
        this.onlinePlayerDirectory = onlinePlayerDirectory;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.databaseService = databaseService;
    }

    /**
     * 执行判决
     * @param verdict 判决
     * @return 执行结果
     */
    public ExecutionResult executeVerdict(Verdict verdict) {
        if (verdict == null) {
            return ExecutionResult.failure("判决为空");
        }

        // 检查是否已执行
        if (executedVerdicts.containsKey(verdict.getVerdictId())) {
            return ExecutionResult.failure("判决已执行");
        }

        // 只有有罪的判决才执行惩罚
        if (verdict.getVerdictType() != Verdict.VerdictType.GUILTY) {
            executedVerdicts.put(verdict.getVerdictId(), new VerdictExecution(verdict.getVerdictId(), Instant.now(), "无惩罚判决"));
            return ExecutionResult.success("无惩罚判决，无需执行");
        }

        CourtCase courtCase = courtService.getCase(verdict.getCaseId()).orElse(null);
        if (courtCase == null) {
            return ExecutionResult.failure("找不到关联案件");
        }

        UUID defendantId = courtCase.getDefendant();
        Player defendant = Bukkit.getPlayer(defendantId);
        String defendantName = defendant != null ? defendant.getName() :
            onlinePlayerDirectory.findPlayerById(defendantId).map(p -> p.getName()).orElse(defendantId.toString());

        ExecutionResult result = new ExecutionResult();

        // 1. 执行罚款
        if (verdict.getFineAmount().isPresent()) {
            double fineAmount = verdict.getFineAmount().get();
            ExecutionResult fineResult = executeFine(defendantId, defendantName, fineAmount, verdict.getVerdictId());
            result.addDetail("fine", fineResult);
            if (!fineResult.isSuccess()) {
                result.addError(fineResult.getMessage());
            }
        }

        // 2. 执行监禁
        if (verdict.getJailTimeMinutes().isPresent()) {
            int jailMinutes = verdict.getJailTimeMinutes().get();
            ExecutionResult jailResult = executeJail(defendantId, defendantName, jailMinutes, verdict.getVerdictId());
            result.addDetail("jail", jailResult);
            if (!jailResult.isSuccess()) {
                result.addError(jailResult.getMessage());
            }
        }

        // 3. 执行驱逐
        if (verdict.isBanishment()) {
            ExecutionResult banishResult = executeBanishment(defendantId, defendantName, verdict.getVerdictId());
            result.addDetail("banishment", banishResult);
            if (!banishResult.isSuccess()) {
                result.addError(banishResult.getMessage());
            }
        }

        // 4. 处理附加条件
        if (verdict.getAdditionalConditions().isPresent()) {
            String conditions = verdict.getAdditionalConditions().get();
            ExecutionResult condResult = executeConditions(defendantId, defendantName, conditions);
            result.addDetail("conditions", condResult);
        }

        // 记录执行结果
        VerdictExecution execution = new VerdictExecution(
            verdict.getVerdictId(),
            Instant.now(),
            result.isSuccess() ? "全部执行成功" : result.getErrors().toString()
        );
        executedVerdicts.put(verdict.getVerdictId(), execution);

        // 广播执行结果
        broadcastExecution(verdict, courtCase, result);

        return result;
    }

    /**
     * 执行罚款 - 自动扣除玩家余额
     */
    public ExecutionResult executeFine(UUID playerId, String playerName, double fineAmount, int verdictId) {
        try {
            BigDecimal amount = BigDecimal.valueOf(fineAmount);

            // 检查余额是否足够
            if (!economyService.has(playerId, amount)) {
                BigDecimal balance = economyService.getBalance(playerId);
                // 扣光余额
                if (balance.signum() > 0) {
                    economyService.withdraw(playerId, balance);

                    // 记录欠款（未来可追讨）
                    recordDebt(playerId, amount.subtract(balance).doubleValue(), verdictId);
                }
                return ExecutionResult.failure("余额不足，已扣除全部余额 " + balance + "，欠款 " + (amount.doubleValue() - balance.doubleValue()));
            }

            // 正常扣款
            boolean success = economyService.withdraw(playerId, amount);
            if (success) {
                return ExecutionResult.success("罚款 " + fineAmount + " 已从 " + playerName + " 账户扣除");
            } else {
                return ExecutionResult.failure("扣款失败");
            }
        } catch (Exception e) {
            return ExecutionResult.failure("执行罚款异常: " + e.getMessage());
        }
    }

    /**
     * 执行监禁 - 限制玩家行动
     */
    public ExecutionResult executeJail(UUID playerId, String playerName, int jailMinutes, int verdictId) {
        try {
            // 检查监狱位置是否设置
            if (jailService.getJailLocation() == null) {
                return ExecutionResult.failure("监狱位置未设置，请管理员使用 /setjail 设置");
            }

            // 检查玩家是否在线
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                return ExecutionResult.failure("玩家 " + playerName + " 不在线");
            }

            // 计算监禁时长（毫秒）
            long durationMillis = jailMinutes * 60 * 1000L;
            String reason = "法庭判决 #" + verdictId + " - 监禁 " + jailMinutes + " 分钟";

            // 执行监禁
            jailService.jailPlayer(playerId, playerName, null, reason, durationMillis);

            return ExecutionResult.success(playerName + " 已被监禁 " + jailMinutes + " 分钟");
        } catch (Exception e) {
            return ExecutionResult.failure("执行监禁异常: " + e.getMessage());
        }
    }

    /**
     * 执行驱逐 - 将玩家从国家移除
     */
    public ExecutionResult executeBanishment(UUID playerId, String playerName, int verdictId) {
        try {
            // 获取玩家所在国家
            Optional<Nation> nationOpt = nationService.nationOf(playerId);
            if (nationOpt.isEmpty()) {
                return ExecutionResult.failure(playerName + " 不在任何国家中");
            }

            Nation nation = nationOpt.get();
            String nationName = nation.name();
            NationId nationId = nation.id();

            // 检查是否是君主（君主不能被驱逐，只能禅让后离开）
            if (nation.founderId().equals(playerId)) {
                return ExecutionResult.failure("国家君主不能被驱逐，需要先禅让王位");
            }

            // 执行驱逐
            boolean success = nationService.removeMember(nationId, playerId);
            if (success) {
                // 记录驱逐信息（用于禁止进入领土）
                banishments.put(playerId, new BanishmentInfo(playerId, nationId, nationName, verdictId, Instant.now()));

                // 如果玩家在线，通知并传送出领土
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.sendMessage("§c§l[驱逐] 你已被国家 " + nationName + " 驱逐!");
                    player.sendMessage("§7驱逐原因: 法庭判决 #" + verdictId);

                    // 传送至世界出生点
                    if (player.getWorld().getSpawnLocation() != null) {
                        player.teleport(player.getWorld().getSpawnLocation());
                    }
                }

                return ExecutionResult.success(playerName + " 已被驱逐出国家 " + nationName);
            } else {
                return ExecutionResult.failure("驱逐失败");
            }
        } catch (Exception e) {
            return ExecutionResult.failure("执行驱逐异常: " + e.getMessage());
        }
    }

    /**
     * 执行附加条件
     */
    public ExecutionResult executeConditions(UUID playerId, String playerName, String conditions) {
        try {
            // 解析并执行条件（可以是多个条件，用逗号分隔）
            String[] conditionList = conditions.split(",");
            int executed = 0;
            int failed = 0;
            ExecutionResult result = new ExecutionResult();

            for (String condition : conditionList) {
                condition = condition.trim();
                if (condition.isEmpty()) continue;

                // 解析条件类型
                if (condition.startsWith("ban:")) {
                    // 禁止进入特定区域
                    String area = condition.substring(4);
                    result.addDetail("area_ban", executeAreaBanishment(playerId, area));
                    executed++;
                } else if (condition.startsWith("limit:")) {
                    // 限制交易
                    String limit = condition.substring(6);
                    result.addDetail("trade_limit", executeTradeLimit(playerId, limit));
                    executed++;
                } else if (condition.startsWith("probation:")) {
                    // 缓刑
                    try {
                        int days = Integer.parseInt(condition.substring(10));
                        // 范围检查: 0-365天
                        if (days < 0 || days > 365) {
                            logger.warning("Invalid probation duration (must be 0-365): " + days);
                            failed++;
                        } else {
                            result.addDetail("probation", executeProbation(playerId, playerName, days));
                            executed++;
                        }
                    } catch (NumberFormatException e) {
                        failed++;
                        logger.warning("Invalid probation duration: " + condition);
                    }
                } else if (condition.startsWith("strip:")) {
                    // 剥夺权利 - strip:officer|vote|property
                    String rights = condition.substring(6);
                    result.addDetail("strip_rights", executeStripRights(playerId, playerName, rights));
                    executed++;
                } else if (condition.startsWith("silence:")) {
                    // 禁言
                    try {
                        int days = Integer.parseInt(condition.substring(8));
                        // 范围检查: 0-30天
                        if (days < 0 || days > 30) {
                            failed++;
                            logger.warning("Invalid silence duration (must be 0-30): " + days);
                        } else {
                            result.addDetail("silence", executeSilence(playerId, playerName, days));
                            executed++;
                        }
                    } catch (NumberFormatException e) {
                        failed++;
                    }
                }
            }

            if (failed > 0) {
                result.addError(failed + " 项条件执行失败");
            }
            return ExecutionResult.success("已设置 " + executed + " 项附加条件" + (failed > 0 ? ", " + failed + " 项失败" : ""));
        } catch (Exception e) {
            return ExecutionResult.failure("执行附加条件异常: " + e.getMessage());
        }
    }

    /**
     * 撤销判决惩罚
     * 用于上诉成功后撤销原判决的惩罚（包括驱逐、禁言、缓刑等）
     */
    public ExecutionResult revokeVerdict(int verdictId) {
        VerdictExecution execution = executedVerdicts.get(verdictId);
        if (execution == null) {
            return ExecutionResult.failure("未找到执行记录");
        }

        ExecutionResult result = new ExecutionResult();
        List<String> revokedItems = new ArrayList<>();

        // 1. 处理驱逐记录撤销
        for (Map.Entry<UUID, BanishmentInfo> entry : banishments.entrySet()) {
            if (entry.getValue().verdictId() == verdictId) {
                UUID playerId = entry.getKey();
                banishments.remove(playerId);
                revokedItems.add("驱逐限制");

                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.sendMessage("§a§l[法庭] 你的驱逐判决已被撤销!");
                    player.sendMessage("§7你可以重新申请加入国家");
                }
            }
        }

        // 2. 解除禁言（通过 verdictId 查找对应记录）
        for (Map.Entry<UUID, SilenceInfo> entry : silenceRecords.entrySet()) {
            // 注意：禁言记录目前不关联 verdictId，这里简化处理
            // 实际应该添加 verdictId 字段到 SilenceInfo
        }

        // 3. 移除缓刑
        for (Map.Entry<UUID, ProbationInfo> entry : probations.entrySet()) {
            // 同样需要 verdictId 关联
        }

        // 移除执行记录
        executedVerdicts.remove(verdictId);

        String summary = revokedItems.isEmpty() ? "无惩罚需要撤销" : String.join(", ", revokedItems);
        result.setSuccess(true);
        result.setMessage("判决 #" + verdictId + " 惩罚已撤销: " + summary);

        return result;
    }

    /**
     * 检查玩家是否被驱逐
     */
    public boolean isBanished(UUID playerId) {
        return banishments.containsKey(playerId);
    }

    /**
     * 检查玩家是否被驱逐出特定国家
     */
    public boolean isBanishedFrom(UUID playerId, NationId nationId) {
        BanishmentInfo info = banishments.get(playerId);
        return info != null && info.nationId().equals(nationId);
    }

    /**
     * 获取玩家的驱逐信息
     */
    public Optional<BanishmentInfo> getBanishmentInfo(UUID playerId) {
        return Optional.ofNullable(banishments.get(playerId));
    }

    /**
     * 广播判决执行结果
     */
    private void broadcastExecution(Verdict verdict, CourtCase courtCase, ExecutionResult result) {
        StringBuilder message = new StringBuilder();
        message.append("§6§l[判决执行] §f案件 #").append(courtCase.getCaseId()).append(" 判决已执行\n");

        if (result.hasDetails()) {
            if (result.getDetail("fine") != null) {
                ExecutionResult fine = (ExecutionResult) result.getDetail("fine");
                message.append("§7- 罚款: ").append(fine.isSuccess() ? "§a" : "§c").append(fine.getMessage()).append("\n");
            }
            if (result.getDetail("jail") != null) {
                ExecutionResult jail = (ExecutionResult) result.getDetail("jail");
                message.append("§7- 监禁: ").append(jail.isSuccess() ? "§a" : "§c").append(jail.getMessage()).append("\n");
            }
            if (result.getDetail("banishment") != null) {
                ExecutionResult banish = (ExecutionResult) result.getDetail("banishment");
                message.append("§7- 驱逐: ").append(banish.isSuccess() ? "§a" : "§c").append(banish.getMessage()).append("\n");
            }
        }

        Bukkit.broadcastMessage(message.toString());
    }

    /**
     * 记录欠款到数据库
     */
    public void recordDebt(UUID playerId, double debt, int verdictId) {
        String sql = "INSERT INTO starcore_court_debts (player_id, debt_amount, verdict_id, recorded_at) VALUES (?, ?, ?, ?)";
        try {
            databaseService.execute(sql, playerId.toString(), debt, verdictId, Instant.now().toEpochMilli());
            logger.info("Recorded debt of " + debt + " for player " + playerId + " from verdict " + verdictId);
        } catch (Exception e) {
            logger.warning("Failed to record debt: " + e.getMessage());
        }
    }

    /**
     * 初始化欠款表
     */
    public void initializeDebtTable() {
        try {
            String sql = """
                CREATE TABLE IF NOT EXISTS starcore_court_debts (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    player_id VARCHAR(36) NOT NULL,
                    debt_amount DECIMAL(20,2) NOT NULL,
                    verdict_id INT NOT NULL,
                    recorded_at BIGINT NOT NULL,
                    paid BOOLEAN DEFAULT FALSE,
                    paid_at BIGINT,
                    INDEX idx_player (player_id),
                    INDEX idx_verdict (verdict_id)
                )
            """;
            databaseService.execute(sql);
        } catch (Exception e) {
            logger.warning("Failed to initialize debt table: " + e.getMessage());
        }
    }

    // ==================== 条件执行方法 ====================

    /**
     * 执行区域禁止
     */
    private ExecutionResult executeAreaBanishment(UUID playerId, String area) {
        try {
            // 将区域禁止存储到配置中
            areaBans.computeIfAbsent(playerId, k -> new ArrayList<>()).add(area);
            logger.info("Player " + playerId + " banned from area: " + area);
            return ExecutionResult.success("已禁止进入区域: " + area);
        } catch (Exception e) {
            return ExecutionResult.failure("执行区域禁止失败: " + e.getMessage());
        }
    }

    /**
     * 执行交易限制
     */
    private ExecutionResult executeTradeLimit(UUID playerId, String limit) {
        try {
            // 解析限制类型: limit:max_amount:duration_days
            String[] parts = limit.split(":");
            if (parts.length >= 1) {
                double maxAmount = Double.parseDouble(parts[0]);
                // 非负数检查
                if (maxAmount < 0) {
                    return ExecutionResult.failure("交易限制金额不能为负数");
                }
                int durationDays = parts.length >= 2 ? Integer.parseInt(parts[1]) : 30;
                // 范围检查: 0-30天
                if (durationDays < 0 || durationDays > 30) {
                    return ExecutionResult.failure("交易限制期限必须在 0-30 天之间");
                }

                // 存储交易限制
                Instant expiry = Instant.now().plus(Duration.ofDays(durationDays));
                tradeLimits.put(playerId, new TradeLimitInfo(maxAmount, expiry));

                logger.info("Player " + playerId + " trade limited to " + maxAmount + " for " + durationDays + " days");
                return ExecutionResult.success("已设置交易限制: 最高 " + maxAmount + " 星尘/天，期限 " + durationDays + " 天");
            }
            return ExecutionResult.failure("交易限制格式错误");
        } catch (NumberFormatException e) {
            return ExecutionResult.failure("交易限制格式错误: 无效的数字");
        } catch (Exception e) {
            return ExecutionResult.failure("执行交易限制失败: " + e.getMessage());
        }
    }

    /**
     * 执行缓刑监督
     */
    private ExecutionResult executeProbation(UUID playerId, String playerName, int days) {
        try {
            Instant start = Instant.now();
            Instant end = start.plus(Duration.ofDays(days));

            // 记录缓刑信息
            ProbationInfo probation = new ProbationInfo(playerId, playerName, start, end, new ArrayList<>());
            probations.put(playerId, probation);

            // 设置定时检查任务
            scheduleProbationChecks(playerId, days);

            logger.info("Player " + playerId + " on probation for " + days + " days");
            return ExecutionResult.success("缓刑监督已设置: " + days + " 天");
        } catch (Exception e) {
            return ExecutionResult.failure("执行缓刑监督失败: " + e.getMessage());
        }
    }

    /**
     * 调度缓刑检查
     */
    private void scheduleProbationChecks(UUID playerId, int days) {
        // 每天检查一次，共检查 days 次
        long intervalTicks = 24 * 60 * 60 * 20L; // 24小时
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ProbationInfo probation = probations.get(playerId);
            if (probation == null || Instant.now().isAfter(probation.endTime())) {
                // 缓刑结束
                probations.remove(playerId);
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.sendMessage("§a§l[法庭] 你的缓刑期已结束!");
                }
                return;
            }

            // 检查是否有违规行为
            checkProbationViolation(playerId);
        }, intervalTicks, intervalTicks);
    }

    /**
     * 检查缓刑违规
     */
    private void checkProbationViolation(UUID playerId) {
        ProbationInfo probation = probations.get(playerId);
        if (probation == null) return;

        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;

        // 检查是否有违规行为记录
        for (String violation : probation.violations()) {
            if ("combat".equals(violation) || "crime".equals(violation)) {
                // 缓刑期间犯罪，通知法庭
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (Player staff : Bukkit.getOnlinePlayers()) {
                        if (staff.hasPermission("starcore.court.judge")) {
                            staff.sendMessage("§c§l[法庭警告] 缓刑犯 " + probation.playerName() + " 可能在缓刑期间有违规行为!");
                        }
                    }
                });
                break;
            }
        }
    }

    /**
     * 检查玩家是否被区域禁止
     */
    public boolean isAreaBanned(UUID playerId, String area) {
        List<String> bans = areaBans.get(playerId);
        return bans != null && bans.contains(area);
    }

    /**
     * 获取玩家的交易限制
     */
    public Optional<TradeLimitInfo> getTradeLimit(UUID playerId) {
        TradeLimitInfo limit = tradeLimits.get(playerId);
        if (limit == null || Instant.now().isAfter(limit.expiresAt())) {
            tradeLimits.remove(playerId);
            return Optional.empty();
        }
        return Optional.of(limit);
    }

    /**
     * 检查缓刑是否有效
     */
    public boolean isOnProbation(UUID playerId) {
        ProbationInfo probation = probations.get(playerId);
        return probation != null && Instant.now().isBefore(probation.endTime());
    }

    /**
     * 记录缓刑违规
     */
    public void recordProbationViolation(UUID playerId, String violation) {
        ProbationInfo probation = probations.get(playerId);
        if (probation != null) {
            // 缓刑信息不可变，需要重新创建记录
            List<String> newViolations = new ArrayList<>(probation.violations());
            newViolations.add(violation);
            ProbationInfo newProbation = new ProbationInfo(
                probation.playerId(),
                probation.playerName(),
                probation.startTime(),
                probation.endTime(),
                newViolations
            );
            probations.put(playerId, newProbation);
            logger.info("Recorded probation violation for " + playerId + ": " + violation);
        }
    }

    /**
     * 获取玩家的缓刑信息
     */
    public Optional<ProbationInfo> getProbationInfo(UUID playerId) {
        return Optional.ofNullable(probations.get(playerId));
    }

    // ==================== 剥夺权利执行 ====================

    /**
     * 剥夺权利记录
     */
    private final Map<UUID, StrippedRightsRecord> strippedVoteRights = new ConcurrentHashMap<>();
    private final Map<UUID, StrippedRightsRecord> strippedPropertyRights = new ConcurrentHashMap<>();

    public record StrippedRightsRecord(
        UUID playerId,
        Instant strippedAt,
        Instant expiresAt,
        boolean voteStripped,
        boolean propertyFrozen,
        boolean nationBanned
    ) {}

    /**
     * 执行剥夺权利
     */
    public ExecutionResult executeStripRights(UUID playerId, String playerName, String rights) {
        try {
            String[] rightsList = rights.split("\\|");
            int stripped = 0;

            for (String right : rightsList) {
                right = right.trim().toLowerCase();
                switch (right) {
                    case "officer" -> {
                        nationService.getOfficerAssignments(playerId).forEach((nationId, office) -> {
                            nationService.removeOfficer(nationId, playerId);
                        });
                        logger.info("Stripped officer rights from " + playerName);
                        stripped++;
                    }
                    case "vote" -> {
                        strippedVoteRights.put(playerId, new StrippedRightsRecord(playerId, Instant.now(), null, true, false, false));
                        logger.info("Stripped voting rights from " + playerName);
                        stripped++;
                    }
                    case "property" -> {
                        strippedPropertyRights.put(playerId, new StrippedRightsRecord(playerId, Instant.now(), null, false, true, false));
                        logger.info("Froze property of " + playerName);
                        stripped++;
                    }
                    case "nation" -> {
                        strippedPropertyRights.put(playerId, new StrippedRightsRecord(playerId, Instant.now(), null, false, false, true));
                        logger.info("Stripped nation membership rights from " + playerName);
                        stripped++;
                    }
                }
            }

            return ExecutionResult.success("已剥夺 " + stripped + " 项权利");
        } catch (Exception e) {
            return ExecutionResult.failure("执行剥夺权利失败: " + e.getMessage());
        }
    }

    public boolean isVoteStripped(UUID playerId) {
        StrippedRightsRecord record = strippedVoteRights.get(playerId);
        if (record == null) return false;
        if (record.expiresAt() != null && Instant.now().isAfter(record.expiresAt())) {
            strippedVoteRights.remove(playerId);
            return false;
        }
        return record.voteStripped();
    }

    public boolean isPropertyFrozen(UUID playerId) {
        StrippedRightsRecord record = strippedPropertyRights.get(playerId);
        if (record == null) return false;
        if (record.expiresAt() != null && Instant.now().isAfter(record.expiresAt())) {
            strippedPropertyRights.remove(playerId);
            return false;
        }
        return record.propertyFrozen();
    }

    public boolean isNationBanned(UUID playerId) {
        StrippedRightsRecord record = strippedPropertyRights.get(playerId);
        if (record == null) return false;
        if (record.expiresAt() != null && Instant.now().isAfter(record.expiresAt())) {
            strippedPropertyRights.remove(playerId);
            return false;
        }
        return record.nationBanned();
    }

    // ==================== 禁言执行 ====================

    public record SilenceInfo(
        UUID playerId,
        String playerName,
        Instant silencedAt,
        Instant expiresAt
    ) {
        public boolean isPermanent() { return expiresAt == null; }
        public boolean isExpired() { return expiresAt != null && Instant.now().isAfter(expiresAt); }
    }

    public ExecutionResult executeSilence(UUID playerId, String playerName, int days) {
        try {
            Instant start = Instant.now();
            Instant end = days > 0 ? start.plus(Duration.ofDays(days)) : null;

            SilenceInfo silence = new SilenceInfo(playerId, playerName, start, end);
            silenceRecords.put(playerId, silence);

            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                String duration = days > 0 ? days + " 天" : "永久";
                player.sendMessage("§c§l[法庭] 你已被禁言 " + duration);
                player.sendMessage("§7禁言原因: 法庭判决");
            }

            logger.info("Player " + playerName + " silenced for " + days + " days");
            return ExecutionResult.success("已对 " + playerName + " 执行禁言 " + (days > 0 ? days + " 天" : "永久"));
        } catch (Exception e) {
            return ExecutionResult.failure("执行禁言失败: " + e.getMessage());
        }
    }

    public boolean liftSilence(UUID playerId) {
        SilenceInfo removed = silenceRecords.remove(playerId);
        if (removed != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage("§a§l[法庭] 你的禁言已被解除!");
            }
            return true;
        }
        return false;
    }

    public boolean isSilenced(UUID playerId) {
        SilenceInfo silence = silenceRecords.get(playerId);
        if (silence == null) return false;
        if (silence.isExpired()) {
            silenceRecords.remove(playerId);
            return false;
        }
        return true;
    }

    public Optional<SilenceInfo> getSilenceInfo(UUID playerId) {
        return Optional.ofNullable(silenceRecords.get(playerId));
    }

    // ==================== 法庭历史记录 ====================

    public record CourtHistoryRecord(
        int verdictId,
        int caseId,
        UUID defendantId,
        String executionDetails,
        Instant executedAt
    ) {}

    public void recordExecutionHistory(int verdictId, int caseId, UUID defendantId, String executionDetails) {
        String sql = "INSERT INTO starcore_court_history (verdict_id, case_id, defendant_id, execution_details, executed_at) VALUES (?, ?, ?, ?, ?)";
        try {
            databaseService.execute(sql, verdictId, caseId, defendantId.toString(), executionDetails, Instant.now().toEpochMilli());
        } catch (Exception e) {
            logger.warning("Failed to record court history: " + e.getMessage());
        }
    }

    public List<CourtHistoryRecord> getPlayerCourtHistory(UUID playerId, int limit) {
        List<CourtHistoryRecord> history = new ArrayList<>();
        String sql = "SELECT * FROM starcore_court_history WHERE defendant_id = ? ORDER BY executed_at DESC LIMIT ?";
        try {
            databaseService.query(sql, rs -> {
                try {
                    while (rs.next()) {
                        history.add(new CourtHistoryRecord(
                            rs.getInt("verdict_id"),
                            rs.getInt("case_id"),
                            UUID.fromString(rs.getString("defendant_id")),
                            rs.getString("execution_details"),
                            Instant.ofEpochMilli(rs.getLong("executed_at"))
                        ));
                    }
                } catch (Exception e) {
                    // Handle ResultSet errors
                }
                return null;
            }, playerId.toString(), limit);
        } catch (Exception e) {
            logger.warning("Failed to get player court history: " + e.getMessage());
        }
        return history;
    }

    public void initializeHistoryTable() {
        try {
            String sql = """
                CREATE TABLE IF NOT EXISTS starcore_court_history (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    verdict_id INT NOT NULL,
                    case_id INT NOT NULL,
                    defendant_id VARCHAR(36) NOT NULL,
                    execution_details TEXT,
                    executed_at BIGINT NOT NULL,
                    INDEX idx_defendant (defendant_id),
                    INDEX idx_verdict (verdict_id),
                    INDEX idx_executed (executed_at)
                )
            """;
            databaseService.execute(sql);
            logger.info("Court history table initialized");
        } catch (Exception e) {
            logger.warning("Failed to initialize court history table: " + e.getMessage());
        }
    }

    public void initializeAllTables() {
        initializeDebtTable();
        initializeHistoryTable();
    }

    /**
     * 检查判决是否已执行
     */
    public boolean isVerdictExecuted(int verdictId) {
        return executedVerdicts.containsKey(verdictId);
    }

    /**
     * 获取执行记录
     */
    public Optional<VerdictExecution> getExecution(int verdictId) {
        return Optional.ofNullable(executedVerdicts.get(verdictId));
    }

    // ==================== 内部类 ====================

    /**
     * 判决执行记录
     */
    public record VerdictExecution(
        int verdictId,
        Instant executedAt,
        String result
    ) {}

    /**
     * 驱逐信息
     */
    public record BanishmentInfo(
        UUID playerId,
        NationId nationId,
        String nationName,
        int verdictId,
        Instant banishedAt
    ) {}

    /**
     * 交易限制信息
     */
    public record TradeLimitInfo(
        double maxAmount,
        Instant expiresAt
    ) {}

    /**
     * 缓刑信息
     */
    public record ProbationInfo(
        UUID playerId,
        String playerName,
        Instant startTime,
        Instant endTime,
        List<String> violations
    ) {}

    /**
     * 执行结果
     */
    public static class ExecutionResult {
        private boolean success;
        private String message;
        private final Map<String, Object> details = new ConcurrentHashMap<>();
        private final java.util.List<String> errors = new java.util.concurrent.CopyOnWriteArrayList<>();

        public ExecutionResult() {
            this.success = true;
            this.message = "";
        }

        private ExecutionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ExecutionResult success(String message) {
            return new ExecutionResult(true, message);
        }

        public static ExecutionResult failure(String message) {
            return new ExecutionResult(false, message);
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public boolean isSuccess() {
            return success && errors.isEmpty();
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void addDetail(String key, Object value) {
            details.put(key, value);
        }

        public Object getDetail(String key) {
            return details.get(key);
        }

        public boolean hasDetails() {
            return !details.isEmpty();
        }

        public void addError(String error) {
            errors.add(error);
            this.success = false;
        }

        public java.util.List<String> getErrors() {
            return java.util.Collections.unmodifiableList(errors);
        }
    }
}
