package dev.starcore.starcore.module.economy;

import dev.starcore.starcore.module.event.EventService;
import dev.starcore.starcore.module.event.NationEventRecord;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.module.treasury.TreasuryService.FinanceHealthReport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 经济趋势分析服务
 * 提供历史数据分析、趋势计算和预测功能
 */
public class EconomyTrendService {

    private static final int SCALE = 2;
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("amount=([\\d.]+)");

    private final EventService eventService;
    private final TreasuryService treasuryService;

    // 缓存的历史数据
    private final Map<NationId, List<DailySnapshot>> snapshotCache = new ConcurrentHashMap<>();
    private static final Duration CACHE_DURATION = Duration.ofMinutes(5);

    // 缓存时间戳
    private final Map<NationId, Long> cacheTimestamp = new ConcurrentHashMap<>();

    public EconomyTrendService(EventService eventService, TreasuryService treasuryService) {
        this.eventService = eventService;
        this.treasuryService = treasuryService;
    }

    /**
     * 获取每日经济快照
     */
    public List<DailySnapshot> getDailySnapshots(NationId nationId, int days) {
        refreshCacheIfNeeded(nationId);

        List<DailySnapshot> allSnapshots = snapshotCache.getOrDefault(nationId, List.of());
        if (allSnapshots.size() <= days) {
            return allSnapshots;
        }
        return allSnapshots.subList(allSnapshots.size() - days, allSnapshots.size());
    }

    /**
     * 计算收入趋势
     * @return 正数表示上升趋势，负数表示下降趋势
     */
    public TrendInfo calculateIncomeTrend(NationId nationId, int days) {
        List<DailySnapshot> snapshots = getDailySnapshots(nationId, days);
        return calculateTrend(snapshots, SnapshotField.INCOME);
    }

    /**
     * 计算支出趋势
     */
    public TrendInfo calculateExpenseTrend(NationId nationId, int days) {
        List<DailySnapshot> snapshots = getDailySnapshots(nationId, days);
        return calculateTrend(snapshots, SnapshotField.EXPENSE);
    }

    /**
     * 计算余额趋势
     */
    public TrendInfo calculateBalanceTrend(NationId nationId, int days) {
        List<DailySnapshot> snapshots = getDailySnapshots(nationId, days);
        return calculateTrend(snapshots, SnapshotField.BALANCE);
    }

    /**
     * 获取综合财务报告
     */
    public EconomyTrendReport getTrendReport(NationId nationId) {
        BigDecimal currentBalance = treasuryService != null ?
            treasuryService.balance(nationId) : BigDecimal.ZERO;

        // 计算各维度趋势
        TrendInfo incomeTrend = calculateIncomeTrend(nationId, 7);
        TrendInfo expenseTrend = calculateExpenseTrend(nationId, 7);
        TrendInfo balanceTrend = calculateBalanceTrend(nationId, 7);

        // 计算月度对比
        List<DailySnapshot> weekSnapshots = getDailySnapshots(nationId, 7);
        List<DailySnapshot> monthSnapshots = getDailySnapshots(nationId, 30);

        BigDecimal weekIncome = weekSnapshots.stream()
            .map(DailySnapshot::income)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthIncome = monthSnapshots.stream()
            .map(DailySnapshot::income)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal weekExpense = weekSnapshots.stream()
            .map(DailySnapshot::expense)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthExpense = monthSnapshots.stream()
            .map(DailySnapshot::expense)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 计算平均每日收支
        int weekDays = Math.max(1, weekSnapshots.size());
        int monthDays = Math.max(1, monthSnapshots.size());

        BigDecimal avgDailyIncome = monthIncome.divide(BigDecimal.valueOf(monthDays), SCALE, RoundingMode.HALF_UP);
        BigDecimal avgDailyExpense = monthExpense.divide(BigDecimal.valueOf(monthDays), SCALE, RoundingMode.HALF_UP);

        // 计算预计维持天数
        int daysUntilBankruptcy = avgDailyExpense.signum() > 0 ?
            currentBalance.divide(avgDailyExpense, RoundingMode.HALF_UP).intValue() : Integer.MAX_VALUE;

        // 获取财政健康报告
        FinanceHealthReport healthReport = treasuryService != null ?
            treasuryService.getFinanceHealthReport(nationId) : null;

        return new EconomyTrendReport(
            nationId,
            currentBalance,
            incomeTrend,
            expenseTrend,
            balanceTrend,
            weekIncome,
            monthIncome,
            weekExpense,
            monthExpense,
            avgDailyIncome,
            avgDailyExpense,
            daysUntilBankruptcy,
            healthReport,
            Instant.now()
        );
    }

    /**
     * 生成ASCII走势图
     * @param snapshots 数据点
     * @param field 要绘制的字段
     * @param width 图表宽度（字符数）
     * @param height 图表高度（字符数）
     */
    public String generateChart(List<DailySnapshot> snapshots, SnapshotField field, int width, int height) {
        if (snapshots == null || snapshots.isEmpty()) {
            return "§7暂无数据";
        }

        // 提取数据值
        List<BigDecimal> values = snapshots.stream()
            .map(s -> field.getValue(s))
            .map(v -> v == null ? BigDecimal.ZERO : v)
            .collect(Collectors.toList());

        // 计算最小值和最大值
        BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.valueOf(100));
        BigDecimal range = max.subtract(min);
        if (range.compareTo(BigDecimal.ZERO) == 0) {
            range = BigDecimal.ONE;
        }

        // 构建图表矩阵
        char[][] matrix = new char[height][width];
        for (int i = 0; i < height; i++) {
            Arrays.fill(matrix[i], ' ');
        }

        // 绘制数据点
        int dataPoints = Math.min(values.size(), width);
        for (int i = 0; i < dataPoints; i++) {
            double normalized = values.get(i).subtract(min).divide(range, 4, RoundingMode.HALF_UP).doubleValue();
            int y = height - 1 - (int) (normalized * (height - 1));
            y = Math.max(0, Math.min(height - 1, y));
            matrix[y][i] = '█';
        }

        // 绘制连接线
        for (int i = 1; i < dataPoints; i++) {
            int y1 = height - 1 - (int) ((values.get(i-1).subtract(min).divide(range, 4, RoundingMode.HALF_UP).doubleValue()) * (height - 1));
            int y2 = height - 1 - (int) ((values.get(i).subtract(min).divide(range, 4, RoundingMode.HALF_UP).doubleValue()) * (height - 1));
            y1 = Math.max(0, Math.min(height - 1, y1));
            y2 = Math.max(0, Math.min(height - 1, y2));

            if (y1 == y2) {
                matrix[y1][i-1] = '─';
            } else {
                int minY = Math.min(y1, y2);
                int maxY = Math.max(y1, y2);
                for (int y = minY; y <= maxY; y++) {
                    matrix[y][i-1] = y == y1 || y == y2 ? '│' : '┼';
                }
            }
        }

        // 转换为字符串
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                sb.append(matrix[y][x]);
            }
            if (y == 0) {
                sb.append(" §e↑ ").append(formatNumber(max));
            } else if (y == height - 1) {
                sb.append(" §c↓ ").append(formatNumber(min));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 获取趋势箭头表示
     */
    public String getTrendArrow(TrendInfo trend) {
        if (trend == null) {
            return "§7-";
        }
        double changePercent = trend.getChangePercent();
        if (changePercent > 5) {
            return "§a▲";
        } else if (changePercent < -5) {
            return "§c▼";
        } else {
            return "§e●";
        }
    }

    /**
     * 获取趋势描述
     */
    public String getTrendDescription(TrendInfo trend) {
        if (trend == null) {
            return "§7数据不足";
        }
        double changePercent = trend.getChangePercent();
        String direction = changePercent > 5 ? "上升" : changePercent < -5 ? "下降" : "平稳";

        String health;
        if (Math.abs(changePercent) > 20) {
            health = "§a良好";
        } else if (Math.abs(changePercent) > 10) {
            health = "§e正常";
        } else if (Math.abs(changePercent) > 5) {
            health = "§6需注意";
        } else {
            health = "§c波动较大";
        }

        return "§7趋势" + direction + " " + health + " §7(" + String.format("%.1f%%", changePercent) + ")";
    }

    // ==================== 私有方法 ====================

    private void refreshCacheIfNeeded(NationId nationId) {
        long now = System.currentTimeMillis();
        Long lastRefresh = cacheTimestamp.get(nationId);

        if (lastRefresh == null || now - lastRefresh > CACHE_DURATION.toMillis()) {
            refreshCache(nationId);
            cacheTimestamp.put(nationId, now);
        }
    }

    private void refreshCache(NationId nationId) {
        if (eventService == null) {
            return;
        }

        Collection<NationEventRecord> allEvents = eventService.eventsOf(nationId);
        if (allEvents.isEmpty()) {
            snapshotCache.put(nationId, List.of());
            return;
        }

        // 按日期分组
        Map<LocalDate, DailySnapshot> snapshotsByDate = new TreeMap<>();
        ZoneId zoneId = ZoneId.systemDefault();

        for (NationEventRecord event : allEvents) {
            LocalDate date = event.occurredAt().atZone(zoneId).toLocalDate();

            DailySnapshot existing = snapshotsByDate.get(date);
            BigDecimal income = BigDecimal.ZERO;
            BigDecimal expense = BigDecimal.ZERO;
            BigDecimal amount = parseAmountFromContext(event.context());

            if (isIncomeEvent(event.type())) {
                income = amount;
            } else if (isExpenseEvent(event.type())) {
                expense = amount;
            }

            if (existing != null) {
                snapshotsByDate.put(date, new DailySnapshot(
                    date,
                    existing.income().add(income),
                    existing.expense().add(expense),
                    existing.balance().add(income).subtract(expense)
                ));
            } else {
                snapshotsByDate.put(date, new DailySnapshot(date, income, expense, amount));
            }
        }

        // 填充缺失的日期（使用前一天的余额）
        List<DailySnapshot> result = new ArrayList<>();
        BigDecimal runningBalance = BigDecimal.ZERO;
        LocalDate today = LocalDate.now();

        for (int i = 30; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            DailySnapshot snapshot = snapshotsByDate.get(date);

            if (snapshot != null) {
                runningBalance = snapshot.balance();
                result.add(snapshot);
            } else {
                result.add(new DailySnapshot(date, BigDecimal.ZERO, BigDecimal.ZERO, runningBalance));
            }
        }

        snapshotCache.put(nationId, result);
    }

    private boolean isIncomeEvent(String type) {
        return type != null && (
            type.contains("income") ||
            type.contains("reward") ||
            type.contains("tax") ||
            type.contains("deposit") ||
            type.contains("reimbursement")
        );
    }

    private boolean isExpenseEvent(String type) {
        return type != null && (
            type.contains("expense") ||
            type.contains("spending") ||
            type.contains("withdraw") ||
            type.contains("loan") ||
            type.contains("repayment") ||
            type.contains("payment") ||
            type.contains("upkeep")
        );
    }

    private BigDecimal parseAmountFromContext(String context) {
        if (context == null || context.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            Matcher matcher = AMOUNT_PATTERN.matcher(context);
            if (matcher.find()) {
                return new BigDecimal(matcher.group(1)).setScale(SCALE, RoundingMode.DOWN);
            }
        } catch (NumberFormatException ignored) {
        }
        return BigDecimal.ZERO;
    }

    private TrendInfo calculateTrend(List<DailySnapshot> snapshots, SnapshotField field) {
        if (snapshots == null || snapshots.size() < 2) {
            return new TrendInfo(BigDecimal.ZERO, BigDecimal.ZERO, 0, TrendDirection.STABLE);
        }

        int size = snapshots.size();
        List<BigDecimal> values = snapshots.stream()
            .map(s -> field.getValue(s))
            .map(v -> v == null ? BigDecimal.ZERO : v)
            .collect(Collectors.toList());

        // 简单线性回归计算趋势
        BigDecimal sumX = BigDecimal.ZERO;
        BigDecimal sumY = BigDecimal.ZERO;
        BigDecimal sumXY = BigDecimal.ZERO;
        BigDecimal sumX2 = BigDecimal.ZERO;

        for (int i = 0; i < size; i++) {
            BigDecimal x = BigDecimal.valueOf(i);
            BigDecimal y = values.get(i);
            sumX = sumX.add(x);
            sumY = sumY.add(y);
            sumXY = sumXY.add(x.multiply(y));
            sumX2 = sumX2.add(x.multiply(x));
        }

        BigDecimal n = BigDecimal.valueOf(size);
        BigDecimal slope = n.multiply(sumXY).subtract(sumX.multiply(sumY))
            .divide(n.multiply(sumX2).subtract(sumX.multiply(sumX)), 4, RoundingMode.HALF_UP);

        BigDecimal firstHalfAvg = values.subList(0, size / 2).stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(size / 2), SCALE, RoundingMode.HALF_UP);

        BigDecimal secondHalfAvg = values.subList(size / 2, size).stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(size - size / 2), SCALE, RoundingMode.HALF_UP);

        BigDecimal change = secondHalfAvg.subtract(firstHalfAvg);
        BigDecimal changePercent = firstHalfAvg.signum() > 0 ?
            change.multiply(BigDecimal.valueOf(100)).divide(firstHalfAvg, 2, RoundingMode.HALF_UP) :
            BigDecimal.ZERO;

        TrendDirection direction = changePercent.compareTo(BigDecimal.valueOf(5)) > 0 ? TrendDirection.UP :
            changePercent.compareTo(BigDecimal.valueOf(-5)) < 0 ? TrendDirection.DOWN :
            TrendDirection.STABLE;

        return new TrendInfo(change, changePercent, size, direction);
    }

    private String formatNumber(BigDecimal value) {
        if (value == null) return "0";
        if (value.compareTo(BigDecimal.valueOf(1000000)) >= 0) {
            return String.format("%.1fM", value.divide(BigDecimal.valueOf(1000000), 1, RoundingMode.HALF_UP).doubleValue());
        } else if (value.compareTo(BigDecimal.valueOf(1000)) >= 0) {
            return String.format("%.1fK", value.divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP).doubleValue());
        } else {
            return value.setScale(0, RoundingMode.HALF_UP).toPlainString();
        }
    }

    // ==================== 内部类 ====================

    /**
     * 每日经济快照
     */
    public record DailySnapshot(
        LocalDate date,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal balance
    ) {
        public BigDecimal net() {
            return income.subtract(expense);
        }
    }

    /**
     * 趋势信息
     */
    public record TrendInfo(
        BigDecimal change,
        BigDecimal changePercent,
        int dataPoints,
        TrendDirection direction
    ) {
        public double getChangePercent() {
            return changePercent != null ? changePercent.doubleValue() : 0;
        }
    }

    /**
     * 趋势方向
     */
    public enum TrendDirection {
        UP, DOWN, STABLE
    }

    /**
     * 快照字段
     */
    public enum SnapshotField {
        INCOME(DailySnapshot::income),
        EXPENSE(DailySnapshot::expense),
        BALANCE(DailySnapshot::balance);

        private final java.util.function.Function<DailySnapshot, BigDecimal> extractor;

        SnapshotField(java.util.function.Function<DailySnapshot, BigDecimal> extractor) {
            this.extractor = extractor;
        }

        public BigDecimal getValue(DailySnapshot snapshot) {
            return extractor.apply(snapshot);
        }
    }

    /**
     * 综合经济趋势报告
     */
    public record EconomyTrendReport(
        NationId nationId,
        BigDecimal currentBalance,
        TrendInfo incomeTrend,
        TrendInfo expenseTrend,
        TrendInfo balanceTrend,
        BigDecimal weeklyIncome,
        BigDecimal monthlyIncome,
        BigDecimal weeklyExpense,
        BigDecimal monthlyExpense,
        BigDecimal avgDailyIncome,
        BigDecimal avgDailyExpense,
        int daysUntilBankruptcy,
        FinanceHealthReport healthReport,
        Instant generatedAt
    ) {
        public boolean isHealthy() {
            return balanceTrend != null &&
                balanceTrend.direction() != TrendDirection.DOWN &&
                daysUntilBankruptcy > 7;
        }

        public String getHealthStatus() {
            if (daysUntilBankruptcy <= 0) {
                return "§4破产风险";
            } else if (daysUntilBankruptcy <= 3) {
                return "§c危急";
            } else if (daysUntilBankruptcy <= 7) {
                return "§6警告";
            } else if (daysUntilBankruptcy <= 14) {
                return "§e注意";
            } else {
                return "§a正常";
            }
        }
    }
}
