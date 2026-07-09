package dev.starcore.starcore.module.economy.gui;

import dev.starcore.starcore.foundation.animation.GuiAnimationManager;
import dev.starcore.starcore.foundation.animation.SoundFeedbackManager;
import dev.starcore.starcore.foundation.gui.ButtonFactory;
import dev.starcore.starcore.module.economy.EconomyTrendService;
import dev.starcore.starcore.module.economy.EconomyTrendService.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 经济趋势分析 GUI
 * 显示走势图、趋势数据和预测信息
 */
public class EconomyTrendGui {

    public static final String MAIN_TITLE = "§6§l📈 经济趋势分析";
    public static final String CHART_TITLE = "§b§l📊 收支走势图";
    public static final String ANALYSIS_TITLE = "§d§l🔍 深度分析";

    private static final int SCALE = 2;

    private final EconomyTrendService trendService;
    private final TreasuryService treasuryService;
    private final NationService nationService;
    private final GuiAnimationManager animationManager;
    private final SoundFeedbackManager soundManager;

    // 玩家当前查看的国家（用于跨服场景）
    private final Map<UUID, NationId> playerViewingNation = new ConcurrentHashMap<>();

    public EconomyTrendGui(
            EconomyTrendService trendService,
            TreasuryService treasuryService,
            NationService nationService,
            GuiAnimationManager animationManager,
            SoundFeedbackManager soundManager
    ) {
        this.trendService = trendService;
        this.treasuryService = treasuryService;
        this.nationService = nationService;
        this.animationManager = animationManager;
        this.soundManager = soundManager;
    }

    /**
     * 打开经济趋势主菜单
     */
    public void openMainMenu(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "经济趋势分析");
        }

        Inventory inv = Bukkit.createInventory(null, 45, Component.text(MAIN_TITLE));
        fillBorder(inv, Material.CYAN_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, createTitleItem("§6§l📈 经济趋势分析中心"));

        NationId nationId = getPlayerNationId(player);
        if (nationId == null) {
            inv.setItem(22, ButtonFactory.createInfoButton(
                "§c你没有所属国家",
                "无法查看经济趋势"
            ));
            inv.setItem(40, ButtonFactory.createBackButton());
            player.openInventory(inv);
            return;
        }

        // 保存玩家查看的国家
        playerViewingNation.put(player.getUniqueId(), nationId);

        // 获取趋势报告
        EconomyTrendReport report = trendService.getTrendReport(nationId);

        // 当前余额（突出显示）
        inv.setItem(19, createBalanceItem(report.currentBalance()));

        // 健康状态
        inv.setItem(21, createHealthItem(report));

        // ===== 趋势预览（简化显示）=====
        // 收入趋势
        inv.setItem(23, createTrendPreviewItem(
            Material.EMERALD,
            "§a📈 收入趋势",
            report.incomeTrend(),
            "近7天收入变化"
        ));

        // ===== 功能按钮 =====
        inv.setItem(28, ButtonFactory.createStyledButton(
            "§b📊 收支走势图",
            Material.MAP,
            ButtonFactory.BUTTON_STYLE_INFO,
            "查看详细的收支走势图",
            "支持7天/30天切换"
        ));

        inv.setItem(30, ButtonFactory.createStyledButton(
            "§e🔍 深度分析",
            Material.BEACON,
            ButtonFactory.BUTTON_STYLE_PRIMARY,
            "深入分析财务数据",
            "趋势预测和健康诊断"
        ));

        inv.setItem(32, ButtonFactory.createStyledButton(
            "§a📋 历史明细",
            Material.WRITABLE_BOOK,
            ButtonFactory.BUTTON_STYLE_SUCCESS,
            "查看每日收支明细",
            "历史交易记录"
        ));

        // ===== 底部统计 =====
        inv.setItem(37, createStatItem(
            Material.GOLD_INGOT,
            "§e本周收入",
            formatMoney(report.weeklyIncome()),
            "过去7天累计"
        ));

        inv.setItem(38, createStatItem(
            Material.IRON_INGOT,
            "§7本周支出",
            formatMoney(report.weeklyExpense()),
            "过去7天累计"
        ));

        inv.setItem(42, createStatItem(
            Material.DIAMOND,
            "§b月均日收",
            formatMoney(report.avgDailyIncome()),
            "过去30天平均"
        ));

        inv.setItem(43, createStatItem(
            Material.REDSTONE,
            "§c月均日支",
            formatMoney(report.avgDailyExpense()),
            "过去30天平均"
        ));

        player.openInventory(inv);
    }

    /**
     * 打开收支走势图
     */
    public void openChartMenu(Player player, int days) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "收支走势图");
        }

        // 验证天数
        if (days != 7 && days != 30) {
            days = 7;
        }

        Inventory inv = Bukkit.createInventory(null, 54, Component.text(CHART_TITLE + " §7(" + days + "天)"));
        fillBorder(inv, Material.BLUE_STAINED_GLASS_PANE);

        NationId nationId = playerViewingNation.getOrDefault(player.getUniqueId(), getPlayerNationId(player));
        if (nationId == null) {
            inv.setItem(22, ButtonFactory.createInfoButton("§c你没有所属国家"));
            inv.setItem(49, ButtonFactory.createBackButton());
            player.openInventory(inv);
            return;
        }

        // 标题栏
        inv.setItem(4, createTitleItem("§b§l📊 " + days + "天收支走势图"));

        // 获取数据
        List<DailySnapshot> snapshots = trendService.getDailySnapshots(nationId, days);
        EconomyTrendReport report = trendService.getTrendReport(nationId);

        // ===== 余额走势图 =====
        String balanceChart = trendService.generateChart(snapshots, SnapshotField.BALANCE, 35, 7);
        inv.setItem(19, createChartItem(
            Material.GOLD_BLOCK,
            "§6💰 余额走势",
            balanceChart,
            "蓝=余额变化"
        ));

        // ===== 收入走势图 =====
        String incomeChart = trendService.generateChart(snapshots, SnapshotField.INCOME, 35, 5);
        inv.setItem(21, createChartItem(
            Material.EMERALD,
            "§a📈 收入走势",
            incomeChart,
            "绿=每日收入"
        ));

        // ===== 支出走势图 =====
        String expenseChart = trendService.generateChart(snapshots, SnapshotField.EXPENSE, 35, 5);
        inv.setItem(23, createChartItem(
            Material.REDSTONE_BLOCK,
            "§c📉 支出走势",
            expenseChart,
            "红=每日支出"
        ));

        // ===== 净收支走势图 =====
        // 计算净收支
        List<NetSnapshot> netSnapshots = new ArrayList<>();
        for (DailySnapshot s : snapshots) {
            netSnapshots.add(new NetSnapshot(s.date(), s.income().subtract(s.expense())));
        }
        String netChart = generateNetChart(netSnapshots, 35, 5);
        inv.setItem(25, createChartItem(
            Material.BLUE_STAINED_GLASS,
            "§b⚖️ 净收支走势",
            netChart,
            "蓝=收入-支出"
        ));

        // ===== 统计汇总 =====
        BigDecimal totalIncome = snapshots.stream()
            .map(DailySnapshot::income)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = snapshots.stream()
            .map(DailySnapshot::expense)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netIncome = totalIncome.subtract(totalExpense);

        inv.setItem(37, createStatItem(
            Material.EMERALD,
            "§a总流入",
            formatMoney(totalIncome),
            days + "天总收入"
        ));

        inv.setItem(38, createStatItem(
            Material.REDSTONE,
            "§c总流出",
            formatMoney(totalExpense),
            days + "天总支出"
        ));

        inv.setItem(42, createStatItem(
            netIncome.signum() >= 0 ? Material.DIAMOND : Material.BEDROCK,
            netIncome.signum() >= 0 ? "§b§l净流入" : "§4§l净流出",
            formatMoney(netIncome.abs()),
            "收支相抵"
        ));

        // ===== 时间切换按钮 =====
        inv.setItem(46, ButtonFactory.createStyledButton(
            "§e📅 7天视图",
            Material.PAPER,
            days == 7 ? ButtonFactory.BUTTON_STYLE_PRIMARY : ButtonFactory.BUTTON_STYLE_SECONDARY,
            "查看近7天走势"
        ));

        inv.setItem(47, ButtonFactory.createStyledButton(
            "§e📅 30天视图",
            Material.MAP,
            days == 30 ? ButtonFactory.BUTTON_STYLE_PRIMARY : ButtonFactory.BUTTON_STYLE_SECONDARY,
            "查看近30天走势"
        ));

        // 收支对比图
        inv.setItem(52, createComparisonItem(report, days));

        // 返回
        inv.setItem(49, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    /**
     * 打开深度分析菜单
     */
    public void openAnalysisMenu(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "深度分析");
        }

        Inventory inv = Bukkit.createInventory(null, 54, Component.text(ANALYSIS_TITLE));
        fillBorder(inv, Material.PURPLE_STAINED_GLASS_PANE);

        NationId nationId = playerViewingNation.getOrDefault(player.getUniqueId(), getPlayerNationId(player));
        if (nationId == null) {
            inv.setItem(22, ButtonFactory.createInfoButton("§c你没有所属国家"));
            inv.setItem(49, ButtonFactory.createBackButton());
            player.openInventory(inv);
            return;
        }

        EconomyTrendReport report = trendService.getTrendReport(nationId);

        // 标题
        inv.setItem(4, createTitleItem("§d§l🔍 经济健康诊断报告"));

        // ===== 健康状态总览 =====
        inv.setItem(20, createHealthDashboard(report));

        // ===== 收支趋势分析 =====
        inv.setItem(22, createTrendAnalysisItem(
            Material.EMERALD,
            "§a收入健康度",
            report.incomeTrend(),
            report.avgDailyIncome()
        ));

        inv.setItem(24, createTrendAnalysisItem(
            Material.REDSTONE,
            "§c支出健康度",
            report.expenseTrend(),
            report.avgDailyExpense()
        ));

        // ===== 预测信息 =====
        inv.setItem(30, createPredictionItem(report));

        // ===== 建议 =====
        inv.setItem(32, createSuggestionItem(report));

        // ===== 详细指标 =====
        inv.setItem(39, createStatItem(
            Material.GOLD_INGOT,
            "§6月度收入",
            formatMoney(report.monthlyIncome()),
            "过去30天总收入"
        ));

        inv.setItem(40, createStatItem(
            Material.IRON_INGOT,
            "§7月度支出",
            formatMoney(report.monthlyExpense()),
            "过去30天总支出"
        ));

        inv.setItem(41, createStatItem(
            Material.NETHER_STAR,
            "§d周转天数",
            report.daysUntilBankruptcy() > 365 ? "365+" : String.valueOf(report.daysUntilBankruptcy()),
            "资金耗尽天数"
        ));

        // ===== 债务信息（如果有）=====
        if (report.healthReport() != null) {
            var health = report.healthReport();
            inv.setItem(43, createStatItem(
                health.totalDebt().signum() > 0 ? Material.BARRIER : Material.DIAMOND,
                health.totalDebt().signum() > 0 ? "§c⚠️国债" : "§a✓无债务",
                formatMoney(health.totalDebt()),
                "当前国债余额"
            ));

            inv.setItem(44, createStatItem(
                Material.HEART_OF_THE_SEA,
                "§d债务健康",
                health.debtHealthScore() + "/100",
                "信用健康度"
            ));
        }

        // 返回
        inv.setItem(49, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    /**
     * 打开历史明细菜单
     */
    public void openHistoryMenu(Player player, int page) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "历史明细");
        }

        int pageSize = 28; // 每页显示28条（排除边框）
        Inventory inv = Bukkit.createInventory(null, 54, Component.text("§6§l📋 历史明细 §7(第" + page + "页)"));
        fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE);

        NationId nationId = playerViewingNation.getOrDefault(player.getUniqueId(), getPlayerNationId(player));
        if (nationId == null) {
            inv.setItem(22, ButtonFactory.createInfoButton("§c你没有所属国家"));
            inv.setItem(49, ButtonFactory.createBackButton());
            player.openInventory(inv);
            return;
        }

        List<DailySnapshot> snapshots = trendService.getDailySnapshots(nationId, 30);
        int totalPages = Math.max(1, (int) Math.ceil((double) snapshots.size() / pageSize));
        page = Math.max(1, Math.min(page, totalPages));

        int startIdx = (page - 1) * pageSize;
        int endIdx = Math.min(startIdx + pageSize, snapshots.size());

        // 标题
        inv.setItem(4, createTitleItem("§e§l📋 每日收支明细"));

        // 表头
        inv.setItem(9, createTableHeader("§e日期", Material.CLOCK));
        inv.setItem(10, createTableHeader("§a收入", Material.EMERALD));
        inv.setItem(11, createTableHeader("§c支出", Material.REDSTONE));
        inv.setItem(12, createTableHeader("§b净额", Material.DIAMOND));

        // 数据行
        int slot = 18;
        for (int i = startIdx; i < endIdx && slot < 45; i++) {
            DailySnapshot snapshot = snapshots.get(i);
            inv.setItem(slot, createHistoryRow(snapshot));
            slot++;
        }

        // ===== 分页导航 =====
        inv.setItem(45, ButtonFactory.createPrevButton("上一页"));
        inv.setItem(48, ButtonFactory.createPageButton(page, page, totalPages));
        inv.setItem(49, ButtonFactory.createBackButton());
        inv.setItem(50, ButtonFactory.createNextButton("下一页"));

        // 汇总行
        BigDecimal totalIncome = snapshots.stream()
            .map(DailySnapshot::income)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = snapshots.stream()
            .map(DailySnapshot::expense)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        inv.setItem(52, createSummaryItem(totalIncome, totalExpense));

        player.openInventory(inv);
    }

    // ==================== 物品创建方法 ====================

    private ItemStack createTitleItem(String name) {
        return ButtonFactory.createStyledButton(name, Material.NETHER_STAR, ButtonFactory.BUTTON_STYLE_PRIMARY);
    }

    private ItemStack createBalanceItem(BigDecimal balance) {
        ItemStack item = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§6💰 当前国库余额", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§e" + formatMoney(balance) + " 金币", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
            lore.add(Component.text("§7国库可用资金", NamedTextColor.GRAY));
            lore.add(Component.text("§7点击查看趋势详情", NamedTextColor.DARK_GRAY));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createHealthItem(EconomyTrendReport report) {
        Material material = report.isHealthy() ? Material.DIAMOND : Material.BEDROCK;
        String status = report.getHealthStatus();
        String healthName = report.isHealthy() ? "§a§l财务健康" : "§c§l需要关注";

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(healthName, NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(status));
            lore.add(Component.text("§7预计维持: " + (report.daysUntilBankruptcy() > 365 ? "365+天" : report.daysUntilBankruptcy() + "天"), NamedTextColor.GRAY));
            lore.add(Component.text("§7收支趋势: " + trendService.getTrendArrow(report.balanceTrend()), NamedTextColor.GRAY));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createTrendPreviewItem(Material material, String name, TrendInfo trend, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7" + description, NamedTextColor.GRAY));
            lore.add(Component.text("§7" + trendService.getTrendArrow(trend) + " " + trendService.getTrendDescription(trend), NamedTextColor.WHITE));
            if (trend != null && trend.dataPoints() > 0) {
                lore.add(Component.text("§8基于" + trend.dataPoints() + "天数据", NamedTextColor.DARK_GRAY));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createChartItem(Material material, String name, String chartText, String legend) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            // 将ASCII图表转换为多行描述
            String[] lines = chartText.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    lore.add(Component.text("§f" + line.trim(), NamedTextColor.WHITE));
                }
            }
            lore.add(Component.text("§8" + legend, NamedTextColor.DARK_GRAY));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createComparisonItem(EconomyTrendReport report, int days) {
        ItemStack item = new ItemStack(Material.ENDER_CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§e📊 收支对比", NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();

            // 计算收支比
            BigDecimal totalIncome = report.weeklyIncome();
            BigDecimal totalExpense = report.weeklyExpense();
            BigDecimal net = totalIncome.subtract(totalExpense);

            String ratioText = totalExpense.signum() > 0 ?
                String.format("%.1f:1.0", totalIncome.divide(totalExpense, 2, RoundingMode.HALF_UP).doubleValue()) :
                "N/A";

            lore.add(Component.text("§7收入/支出比: §e" + ratioText, NamedTextColor.GRAY));
            lore.add(Component.text(net.signum() >= 0 ? "§a盈余" : "§c亏损", NamedTextColor.WHITE));
            lore.add(Component.text("§8" + days + "天累计", NamedTextColor.DARK_GRAY));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createHealthDashboard(EconomyTrendReport report) {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§d🏥 财务健康总览", NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(report.getHealthStatus(), NamedTextColor.YELLOW));
            lore.add(Component.text("§7", NamedTextColor.GRAY));
            lore.add(Component.text("§7预计维持: §e" + (report.daysUntilBankruptcy() > 365 ? "365+天" : report.daysUntilBankruptcy() + "天"), NamedTextColor.GRAY));
            lore.add(Component.text("§7健康趋势: " + trendService.getTrendArrow(report.balanceTrend()), NamedTextColor.GRAY));
            lore.add(Component.text("§7" + trendService.getTrendDescription(report.balanceTrend()), NamedTextColor.WHITE));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createTrendAnalysisItem(Material material, String name, TrendInfo trend, BigDecimal avgValue) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7日均: §e" + formatMoney(avgValue), NamedTextColor.GRAY));
            lore.add(Component.text(trend != null ? trendService.getTrendArrow(trend) + " " + trendService.getTrendDescription(trend) : "§7数据不足", NamedTextColor.WHITE));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPredictionItem(EconomyTrendReport report) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§b🔮 趋势预测", NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();

            // 基于当前趋势预测
            BigDecimal projectedWeeklyIncome = report.avgDailyIncome().multiply(BigDecimal.valueOf(7));
            BigDecimal projectedWeeklyExpense = report.avgDailyExpense().multiply(BigDecimal.valueOf(7));
            BigDecimal projectedWeeklyNet = projectedWeeklyIncome.subtract(projectedWeeklyExpense);

            lore.add(Component.text("§7预计下周:", NamedTextColor.GRAY));
            lore.add(Component.text("§a收入: §e" + formatMoney(projectedWeeklyIncome), NamedTextColor.WHITE));
            lore.add(Component.text("§c支出: §e" + formatMoney(projectedWeeklyExpense), NamedTextColor.WHITE));
            lore.add(Component.text(projectedWeeklyNet.signum() >= 0 ? "§b净收: §a" : "§b净收: §c", NamedTextColor.WHITE)
                .append(Component.text(formatMoney(projectedWeeklyNet.abs()), NamedTextColor.WHITE)));
            lore.add(Component.text("§7" + (report.daysUntilBankruptcy() > 365 ? "资金充足" : "预计" + report.daysUntilBankruptcy() + "天后资金紧张"), NamedTextColor.DARK_GRAY));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSuggestionItem(EconomyTrendReport report) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§a💡 优化建议", NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();

            // 根据健康状态生成建议
            if (!report.isHealthy() || report.daysUntilBankruptcy() < 30) {
                lore.add(Component.text("§c⚠️ 建议:", NamedTextColor.RED));
                lore.add(Component.text("§71. 降低不必要开支", NamedTextColor.GRAY));
                lore.add(Component.text("§72. 提高税收或寻找新收入", NamedTextColor.GRAY));
                lore.add(Component.text("§73. 暂停大型建设项目", NamedTextColor.GRAY));
            } else if (report.balanceTrend() != null && report.balanceTrend().direction() == TrendDirection.UP) {
                lore.add(Component.text("§a✓ 建议:", NamedTextColor.GREEN));
                lore.add(Component.text("§71. 经济状况良好", NamedTextColor.GRAY));
                lore.add(Component.text("§72. 可考虑投资建设", NamedTextColor.GRAY));
                lore.add(Component.text("§73. 储备资金以备不时之需", NamedTextColor.GRAY));
            } else {
                lore.add(Component.text("§e➤ 建议:", NamedTextColor.YELLOW));
                lore.add(Component.text("§71. 保持当前收支平衡", NamedTextColor.GRAY));
                lore.add(Component.text("§72. 关注趋势变化", NamedTextColor.GRAY));
                lore.add(Component.text("§73. 适当增加收入来源", NamedTextColor.GRAY));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createStatItem(Material material, String name, String value, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§e" + value, NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
            lore.add(Component.text("§7" + description, NamedTextColor.GRAY));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createTableHeader(String text, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(text, NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createHistoryRow(DailySnapshot snapshot) {
        Material material = snapshot.net().signum() >= 0 ? Material.LIGHT_BLUE_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String dateStr = snapshot.date().format(DateTimeFormatter.ofPattern("MM/dd"));
            meta.displayName(Component.text("§f" + dateStr, NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§a+" + formatMoney(snapshot.income()), NamedTextColor.GREEN));
            lore.add(Component.text("§c-" + formatMoney(snapshot.expense()), NamedTextColor.RED));
            lore.add(Component.text(snapshot.net().signum() >= 0 ? "§b净: §a" : "§b净: §c", NamedTextColor.WHITE)
                .append(Component.text(formatMoney(snapshot.net().abs()), NamedTextColor.WHITE)));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSummaryItem(BigDecimal totalIncome, BigDecimal totalExpense) {
        ItemStack item = new ItemStack(Material.ENDER_CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§e📊 30天汇总", NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            BigDecimal net = totalIncome.subtract(totalExpense);
            lore.add(Component.text("§a总收入: §e" + formatMoney(totalIncome), NamedTextColor.GREEN));
            lore.add(Component.text("§c总支出: §e" + formatMoney(totalExpense), NamedTextColor.RED));
            lore.add(Component.text(net.signum() >= 0 ? "§b净盈余: §a" : "§b净亏损: §c", NamedTextColor.WHITE)
                .append(Component.text(formatMoney(net.abs()), NamedTextColor.WHITE)));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String generateNetChart(List<NetSnapshot> snapshots, int width, int height) {
        if (snapshots == null || snapshots.isEmpty()) {
            return "§7暂无数据";
        }

        List<BigDecimal> values = snapshots.stream()
            .map(NetSnapshot::net)
            .collect(java.util.stream.Collectors.toList());

        BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.valueOf(100));
        BigDecimal range = max.subtract(min);
        if (range.compareTo(BigDecimal.ZERO) == 0) {
            range = BigDecimal.ONE;
        }

        char[][] matrix = new char[height][width];
        for (int i = 0; i < height; i++) {
            Arrays.fill(matrix[i], ' ');
        }

        int zeroLine = height / 2;
        // 绘制零线
        for (int x = 0; x < width; x++) {
            matrix[zeroLine][x] = '─';
        }

        int dataPoints = Math.min(values.size(), width);
        for (int i = 0; i < dataPoints; i++) {
            BigDecimal val = values.get(i);
            double normalized = val.subtract(min).divide(range, 4, RoundingMode.HALF_UP).doubleValue();
            int y = height - 1 - (int) (normalized * (height - 1));
            y = Math.max(0, Math.min(height - 1, y));
            matrix[y][i] = val.signum() >= 0 ? '█' : '▓';
        }

        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                sb.append(matrix[y][x]);
            }
            if (y == 0) {
                sb.append(" §e↑ ").append(formatNumber(max));
            } else if (y == zeroLine) {
                sb.append(" §70");
            } else if (y == height - 1) {
                sb.append(" §c↓ ").append(formatNumber(min));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private void fillBorder(Inventory inv, Material material) {
        ItemStack border = ButtonFactory.createBorder(material);
        int size = inv.getSize();
        int rows = size / 9;

        for (int row = 0; row < rows; row++) {
            inv.setItem(row * 9, border);
            inv.setItem(row * 9 + 8, border);
        }
        for (int col = 1; col < 8; col++) {
            inv.setItem(col, border);
            inv.setItem(size - 9 + col, border);
        }
    }

    private NationId getPlayerNationId(Player player) {
        return nationService.nationOf(player.getUniqueId()).map(Nation::getId).orElse(null);
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) return "0.00";
        return value.setScale(SCALE, RoundingMode.HALF_UP).toPlainString();
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

    // 辅助记录
    private record NetSnapshot(java.time.LocalDate date, BigDecimal net) {}
}
