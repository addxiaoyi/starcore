package dev.starcore.starcore.module.treasury.gui;

import dev.starcore.starcore.foundation.animation.GuiAnimationManager;
import dev.starcore.starcore.foundation.animation.SoundFeedbackManager;
import dev.starcore.starcore.foundation.gui.ButtonFactory;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryModule;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 国库系统 GUI 菜单
 */
public class TreasuryMenu {

    public static final String MAIN_MENU_TITLE = "§6§l💰 国库管理中心";
    public static final String TAX_MENU_TITLE = "§6§l💵 税收管理";
    public static final String TRANSFER_TITLE = "§6§l💸 转账管理";

    private final TreasuryModule treasuryModule;
    private final TreasuryService treasuryService;
    private final NationService nationService;
    private final GuiAnimationManager animationManager;
    private final SoundFeedbackManager soundManager;

    private final Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();

    /**
     * audit C-037/C-039/C-040: 安全地设置物品，避免越界。
     * 各菜单尺寸不一（45/36/27），部分原代码使用 slot 49 但 inv size 可能 < 50。
     */
    private void setItemSafe(Inventory inv, int slot, ItemStack item) {
        if (slot < 0 || slot >= inv.getSize()) return;
        inv.setItem(slot, item);
    }

    public TreasuryMenu(
            TreasuryModule treasuryModule,
            TreasuryService treasuryService,
            NationService nationService,
            GuiAnimationManager animationManager,
            SoundFeedbackManager soundManager
    ) {
        this.treasuryModule = treasuryModule;
        this.treasuryService = treasuryService;
        this.nationService = nationService;
        this.animationManager = animationManager;
        this.soundManager = soundManager;
    }

    /**
     * Bug修复 #3: 检查玩家是否有财政管理权限
     * @return true if player has permission, false otherwise
     */
    private boolean checkTreasuryPermission(Player player, Inventory inv) {
        NationId playerNationId = getPlayerNationId(player);
        if (playerNationId == null) {
            setItemSafe(inv, 22, ButtonFactory.createInfoButton(
                "§c你没有所属国家",
                "无法使用国库功能"
            ));
            setItemSafe(inv, inv.getSize() - 5, ButtonFactory.createBackButton());
            player.openInventory(inv);
            return false;
        }

        Nation playerNation = nationService.nationById(playerNationId).orElse(null);
        if (playerNation == null) {
            setItemSafe(inv, 22, ButtonFactory.createInfoButton(
                "§c国家数据异常"
            ));
            setItemSafe(inv, inv.getSize() - 5, ButtonFactory.createBackButton());
            player.openInventory(inv);
            return false;
        }

        if (!playerNation.hasPermission(player.getUniqueId(), "treasury.manage")) {
            setItemSafe(inv, 22, ButtonFactory.createInfoButton(
                "§c权限不足",
                "§7只有国家创始人和财政官员才能管理国库"
            ));
            setItemSafe(inv, inv.getSize() - 5, ButtonFactory.createBackButton());
            player.openInventory(inv);
            return false;
        }

        return true;
    }

    /**
     * 打开国库主菜单
     */
    public void openMainMenu(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "国库系统");
        }

        Inventory inv = Bukkit.createInventory(null, 45, Component.text(MAIN_MENU_TITLE));

        fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE);

        // 标题
        setItemSafe(inv, 4, createTitleItem("§6§l💰 国库管理中心"));

        // Bug修复 #3: 权限检查
        if (!checkTreasuryPermission(player, inv)) {
            return;
        }

        // 获取国家信息
        NationId playerNationId = getPlayerNationId(player);
        Nation playerNation = nationService.nationById(playerNationId).orElse(null);

        // 国库余额
        BigDecimal balance = treasuryService != null ?
            treasuryService.balance(playerNationId) : BigDecimal.ZERO;

        // 今日收入/支出 - 使用真实数据
        BigDecimal todayIncome = BigDecimal.ZERO;
        BigDecimal todayExpense = BigDecimal.ZERO;
        if (treasuryService != null && playerNationId != null) {
            try {
                todayIncome = treasuryService.getTodayIncome(playerNationId);
                todayExpense = treasuryService.getTodayExpense(playerNationId);
            } catch (Exception e) {
                // 异常时保持零值
                todayIncome = BigDecimal.ZERO;
                todayExpense = BigDecimal.ZERO;
            }
        }

        setItemSafe(inv, 19, createBalanceItem(balance));

        // 今日收入
        setItemSafe(inv, 21, createStatItem(
            Material.GREEN_STAINED_GLASS,
            "§a今日收入",
            todayIncome.toString(),
            "今日税收和其他收入"
        ));

        // 今日支出
        setItemSafe(inv, 23, createStatItem(
            Material.RED_STAINED_GLASS,
            "§c今日支出",
            todayExpense.toString(),
            "今日支出和消耗"
        ));

        // 菜单选项
        setItemSafe(inv, 28, ButtonFactory.createStyledButton(
            "§a💵 税收管理",
            Material.GOLD_INGOT,
            ButtonFactory.BUTTON_STYLE_SUCCESS,
            "管理国家税收设置",
            "调整税率和征收方式"
        ));

        setItemSafe(inv, 30, ButtonFactory.createStyledButton(
            "§b💸 转账管理",
            Material.PAPER,
            ButtonFactory.BUTTON_STYLE_INFO,
            "国家间资金转账",
            "与其他国家进行交易"
        ));

        setItemSafe(inv, 32, ButtonFactory.createStyledButton(
            "§e📊 财务报表",
            Material.BOOK,
            ButtonFactory.BUTTON_STYLE_INFO,
            "查看财务明细",
            "收入支出历史记录"
        ));

        setItemSafe(inv, 34, ButtonFactory.createStyledButton(
            "§c💰 资金管理",
            Material.CHEST,
            ButtonFactory.BUTTON_STYLE_PRIMARY,
            "存取款操作",
            "国库资金管理"
        ));

        // 经济趋势分析按钮
        setItemSafe(inv, 40, ButtonFactory.createStyledButton(
            "§d📈 趋势分析",
            Material.MAP,
            ButtonFactory.BUTTON_STYLE_INFO,
            "查看经济走势",
            "趋势预测和分析报告"
        ));

        // 底部信息
        setItemSafe(inv, 43, ButtonFactory.createInfoButton(
            "提示: 仅财务官员及以上权限可操作",
            "君主可访问全部功能"
        ));

        // audit C-039: 修复重复 setItem(40) 覆盖趋势分析按钮的 bug —— 删除下方重复块
        player.openInventory(inv);
    }

    /**
     * 打开税收管理菜单
     */
    public void openTaxMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, Component.text(TAX_MENU_TITLE));

        fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, createTitleItem("§a§l💵 税收管理中心"));

        // Bug修复 #3: 权限检查
        if (!checkTreasuryPermission(player, inv)) {
            return;
        }

        NationId playerNationId = getPlayerNationId(player);

        // 税收类型
        inv.setItem(20, createTaxItem(
            Material.DIAMOND,
            "§b土地税",
            "按领土面积征收",
            0.05,
            "LAND"
        ));

        inv.setItem(22, createTaxItem(
            Material.EMERALD,
            "§a商业税",
            "按商业活动征收",
            0.10,
            "BUSINESS"
        ));

        inv.setItem(24, createTaxItem(
            Material.GOLD_INGOT,
            "§e所得税",
            "按成员收入征收",
            0.03,
            "INCOME"
        ));

        inv.setItem(28, createTaxItem(
            Material.IRON_INGOT,
            "§7关税",
            "按对外贸易征收",
            100,
            "TARIFF"
        ));

        // 批量操作
        inv.setItem(34, ButtonFactory.createStyledButton(
            "§6批量设置",
            Material.COMMAND_BLOCK,
            ButtonFactory.BUTTON_STYLE_PRIMARY,
            "批量修改税率"
        ));

        // 返回
        inv.setItem(40, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    // ==================== 转账菜单 ====================

    /**
     * 打开转账管理菜单
     */
    public void openTransferMenu(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "转账系统");
        }

        Inventory inv = Bukkit.createInventory(null, 27, Component.text(TRANSFER_TITLE));

        fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, createTitleItem("§b§l💸 转账管理中心"));

        // Bug修复 #3: 权限检查
        if (!checkTreasuryPermission(player, inv)) {
            return;
        }

        NationId playerNationId = getPlayerNationId(player);

        // 当前余额
        BigDecimal balance = treasuryService != null ?
            treasuryService.balance(playerNationId) : BigDecimal.ZERO;

        inv.setItem(11, createBalanceItem(balance));

        // 转账选项
        inv.setItem(13, ButtonFactory.createStyledButton(
            "§e📤 转账给其他国家",
            Material.PAPER,
            ButtonFactory.BUTTON_STYLE_PRIMARY,
            "向其他国家的国库转账",
            "需要对方同意"
        ));

        // 转账记录
        inv.setItem(15, ButtonFactory.createStyledButton(
            "§a📋 转账记录",
            Material.BOOK,
            ButtonFactory.BUTTON_STYLE_INFO,
            "查看转账历史",
            "收支明细"
        ));

        // 返回
        inv.setItem(22, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    // ==================== 财务报表菜单 ====================

    private static final String REPORT_TITLE = "§6§l📊 财务报表";

    /**
     * 打开财务报表菜单
     */
    public void openReportMenu(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "财务报表");
        }

        Inventory inv = Bukkit.createInventory(null, 45, Component.text(REPORT_TITLE));

        fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, createTitleItem("§e§l📊 财务数据中心"));

        // Bug修复 #3: 权限检查
        if (!checkTreasuryPermission(player, inv)) {
            return;
        }

        NationId playerNationId = getPlayerNationId(player);

        // 获取财政健康报告
        var report = treasuryService != null ?
            treasuryService.getFinanceHealthReport(playerNationId) : null;

        if (report != null) {
            // 国库余额
            inv.setItem(19, createStatItem(
                Material.GOLD_BLOCK,
                "§6💰 国库余额",
                report.balance().toPlainString() + " 金币",
                "当前可用资金"
            ));

            // 总债务
            inv.setItem(21, createStatItem(
                Material.REDSTONE_BLOCK,
                "§c💸 总债务",
                report.totalDebt().toPlainString() + " 金币",
                "当前国债余额"
            ));

            // 债务健康度
            String healthText = report.debtHealthScore() >= 80 ? "§a优秀" :
                                report.debtHealthScore() >= 60 ? "§e良好" :
                                report.debtHealthScore() >= 40 ? "§c一般" : "§4危险";
            inv.setItem(23, createStatItem(
                Material.HEART_OF_THE_SEA,
                "§d❤️ 债务健康度",
                healthText + " (" + report.debtHealthScore() + "/100)",
                "财政健康状况"
            ));

            // 月度收入
            inv.setItem(29, createStatItem(
                Material.EMERALD,
                "§a📈 月度收入",
                report.monthlyIncome().toPlainString() + " 金币",
                "预计月收入"
            ));

            // 月度支出
            inv.setItem(31, createStatItem(
                Material.BARRIER,
                "§c📉 月度支出",
                report.monthlyExpense().toPlainString() + " 金币",
                "实际月支出"
            ));

            // 破产状态
            String bankruptStatus = report.isBankrupt() ? "§4已破产" : "§a正常";
            inv.setItem(33, createStatItem(
                report.isBankrupt() ? Material.BEDROCK : Material.DIAMOND,
                "§6⚠️ 破产状态",
                bankruptStatus,
                report.isBankrupt() ? "国家处于破产状态" : "财政运行正常"
            ));
        }

        // 返回
        inv.setItem(40, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    // ==================== 资金管理菜单 ====================

    private static final String FUND_TITLE = "§6§l💰 资金管理";

    /**
     * 打开资金管理菜单
     */
    public void openFundMenu(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "资金管理");
        }

        Inventory inv = Bukkit.createInventory(null, 36, Component.text(FUND_TITLE));

        fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, createTitleItem("§c§l💰 资金存取中心"));

        // Bug修复 #3: 权限检查
        if (!checkTreasuryPermission(player, inv)) {
            return;
        }

        NationId playerNationId = getPlayerNationId(player);

        // 国库余额
        BigDecimal balance = treasuryService != null ?
            treasuryService.balance(playerNationId) : BigDecimal.ZERO;

        inv.setItem(13, createBalanceItem(balance));

        // 存款选项
        inv.setItem(20, ButtonFactory.createStyledButton(
            "§a📥 存款到国库",
            Material.HOPPER,
            ButtonFactory.BUTTON_STYLE_SUCCESS,
            "将金币存入国家国库",
            "使用 /treasury deposit <金额>"
        ));

        // 取款选项
        inv.setItem(22, ButtonFactory.createStyledButton(
            "§c📤 从国库取款",
            Material.DROPPER,
            ButtonFactory.BUTTON_STYLE_DANGER,
            "从国家国库取出金币",
            "使用 /treasury withdraw <金额>"
        ));

        // 历史记录
        inv.setItem(24, ButtonFactory.createStyledButton(
            "§b📜 交易记录",
            Material.WRITABLE_BOOK,
            ButtonFactory.BUTTON_STYLE_INFO,
            "查看国库交易历史",
            "所有收支明细"
        ));

        // 返回
        inv.setItem(31, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    // ==================== 贷款管理菜单 ====================

    private static final String LOAN_TITLE = "§6§l💳 贷款管理";

    /**
     * 打开贷款管理菜单
     */
    public void openLoanMenu(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "贷款系统");
        }

        Inventory inv = Bukkit.createInventory(null, 45, Component.text(LOAN_TITLE));

        fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, createTitleItem("§d§l💳 贷款服务中心"));

        // Bug修复 #3: 权限检查
        if (!checkTreasuryPermission(player, inv)) {
            return;
        }

        NationId playerNationId = getPlayerNationId(player);

        // 获取债务信息
        var report = treasuryService != null ?
            treasuryService.getFinanceHealthReport(playerNationId) : null;

        // 当前债务
        BigDecimal totalDebt = report != null ? report.totalDebt() : BigDecimal.ZERO;
        inv.setItem(19, createStatItem(
            Material.REDSTONE_BLOCK,
            "§c💸 当前债务",
            totalDebt.toPlainString() + " 金币",
            "尚未偿还的贷款"
        ));

        // 最大可借
        BigDecimal maxBorrow = treasuryService != null ?
            treasuryService.getMaxBorrowableAmount(playerNationId) : BigDecimal.ZERO;
        inv.setItem(21, createStatItem(
            Material.GOLD_INGOT,
            "§e💰 最大可借",
            maxBorrow.toPlainString() + " 金币",
            "信用额度内的可借金额"
        ));

        // 健康度
        int health = report != null ? report.debtHealthScore() : 100;
        String healthText = health >= 80 ? "§a优秀" : health >= 60 ? "§e良好" : health >= 40 ? "§c一般" : "§4危险";
        inv.setItem(23, createStatItem(
            Material.HEART_OF_THE_SEA,
            "§d❤️ 信用健康度",
            healthText + " (" + health + "/100)",
            "影响可贷款额度"
        ));

        // 操作按钮
        inv.setItem(28, ButtonFactory.createStyledButton(
            "§a💵 申请贷款",
            Material.DIAMOND,
            ButtonFactory.BUTTON_STYLE_SUCCESS,
            "向其他国家和银行申请贷款",
            "使用 /treasury loan apply <金额>"
        ));

        inv.setItem(30, ButtonFactory.createStyledButton(
            "§c💳 偿还债务",
            Material.EMERALD,
            ButtonFactory.BUTTON_STYLE_DANGER,
            "偿还部分或全部债务",
            "使用 /treasury loan repay <债务ID> <金额>"
        ));

        inv.setItem(32, ButtonFactory.createStyledButton(
            "§b📋 债务列表",
            Material.PAPER,
            ButtonFactory.BUTTON_STYLE_INFO,
            "查看所有未偿债务详情",
            "债权人、利率、还款计划"
        ));

        inv.setItem(34, ButtonFactory.createStyledButton(
            "§e📊 贷款计算器",
            Material.BOOK,
            ButtonFactory.BUTTON_STYLE_INFO,
            "预估贷款利息和还款",
            "贷款规划工具"
        ));

        // 返回
        inv.setItem(40, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    // ==================== 税种设置菜单 ====================

    private static final String TAX_TYPE_TITLE = "§6§l💵 税种设置";

    /**
     * 打开税种设置菜单
     */
    public void openTaxTypeMenu(Player player, String taxType) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "税种设置");
        }

        String taxTypeName;
        Material taxMaterial;
        dev.starcore.starcore.module.treasury.TaxationService.TaxType type;
        switch (taxType) {
            case "LAND" -> {
                taxTypeName = "§b土地税";
                taxMaterial = Material.DIAMOND;
                type = dev.starcore.starcore.module.treasury.TaxationService.TaxType.LAND_TAX;
            }
            case "BUSINESS" -> {
                taxTypeName = "§a商业税";
                taxMaterial = Material.EMERALD;
                type = dev.starcore.starcore.module.treasury.TaxationService.TaxType.BUSINESS_TAX;
            }
            case "INCOME" -> {
                taxTypeName = "§e所得税";
                taxMaterial = Material.GOLD_INGOT;
                type = dev.starcore.starcore.module.treasury.TaxationService.TaxType.INCOME_TAX;
            }
            case "TARIFF" -> {
                taxTypeName = "§d关税";
                taxMaterial = Material.NETHER_STAR;
                type = dev.starcore.starcore.module.treasury.TaxationService.TaxType.TARIFF;
            }
            default -> {
                taxTypeName = "§f未知税种";
                taxMaterial = Material.PAPER;
                type = null;
            }
        }

        String menuTitle = taxTypeName + " - 税率设置";
        Inventory inv = Bukkit.createInventory(null, 36, Component.text(menuTitle));

        fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, createTitleItem("§a§l" + taxTypeName + " §7税率配置"));

        NationId playerNationId = getPlayerNationId(player);
        BigDecimal currentRate = BigDecimal.ZERO;

        if (treasuryService != null && playerNationId != null && type != null) {
            try {
                var config = treasuryService.getTaxConfig(playerNationId, type);
                if (config != null) {
                    currentRate = config.percentRate();
                }
            } catch (Exception e) {
                currentRate = BigDecimal.ZERO;
            }
        }

        // 当前税率显示
        inv.setItem(11, createStatItem(
            taxMaterial,
            "§e当前税率",
            formatRateForDisplay(currentRate.doubleValue()),
            taxType.equals("FIXED") ? "每人固定缴纳金额" : "按百分比计算"
        ));

        // 税率调节
        inv.setItem(15, ButtonFactory.createStyledButton(
            "§a➕ 增加税率",
            Material.LIME_STAINED_GLASS_PANE,
            ButtonFactory.BUTTON_STYLE_SUCCESS,
            "增加税率 " + formatRateForDisplay(currentRate.doubleValue()),
            "点击增加 1%"
        ));

        inv.setItem(16, ButtonFactory.createStyledButton(
            "§c➖ 降低税率",
            Material.RED_STAINED_GLASS_PANE,
            ButtonFactory.BUTTON_STYLE_DANGER,
            "降低税率 " + formatRateForDisplay(currentRate.doubleValue()),
            "点击减少 1%"
        ));

        // 快速设置
        inv.setItem(22, ButtonFactory.createStyledButton(
            "§e⚡ 快速设置",
            Material.COMMAND_BLOCK,
            ButtonFactory.BUTTON_STYLE_PRIMARY,
            "使用指令快速设置",
            "/treasury tax set " + taxType + " <数值>"
        ));

        // 说明
        inv.setItem(31, ButtonFactory.createInfoButton(
            "§e提示: 税率调整建议",
            "过高税率可能导致成员流失",
            "建议保持平衡的税率水平"
        ));

        // 返回
        inv.setItem(31, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    private String formatRateForDisplay(double rate) {
        if (rate < 1) {
            return (rate * 100) + "%";
        } else {
            return rate + " 金币/人";
        }
    }

    // ==================== 辅助方法 ====================

    private ItemStack createTitleItem(String name) {
        return ButtonFactory.createStyledButton(name, Material.NETHER_STAR, ButtonFactory.BUTTON_STYLE_PRIMARY);
    }

    private ItemStack createBalanceItem(BigDecimal balance) {
        ItemStack item = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§6💰 国库余额", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            meta.lore(List.of(
                Component.text("§e当前余额: §a" + balance + " 金币", NamedTextColor.YELLOW),
                Component.text("§7国家储备资金", NamedTextColor.GRAY),
                Component.text("§7点击查看详情", NamedTextColor.DARK_GRAY)
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createStatItem(Material material, String label, String value, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(label, NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§6" + value, NamedTextColor.GOLD));
            lore.add(Component.text("§7" + description, NamedTextColor.GRAY));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createTaxItem(Material material, String name, String description, double rate, String taxType) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7" + description, NamedTextColor.GRAY));
            lore.add(Component.text("§e当前税率: " + formatRate(rate), NamedTextColor.YELLOW));
            lore.add(Component.text("§7点击调整税率", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("§8taxType=" + taxType));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatRate(double rate) {
        if (rate < 1) {
            return (rate * 100) + "%";
        } else {
            return rate + " 金币";
        }
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
        if (nationService == null) {
            return null;
        }
        return nationService.nationOf(player.getUniqueId()).map(Nation::getId).orElse(null);
    }

    public void handleClick(Player player, int slot, ItemStack item) {
        if (item == null) return;
        if (soundManager != null) {
            soundManager.playMenuSelect(player);
        }
    }

    /**
     * audit C-038: 菜单关闭/玩家退出时清理分页状态缓存，避免泄漏。
     * 由外部 Listener 在 InventoryCloseEvent / PlayerQuitEvent 中调用。
     */
    public void handleClose(Player player) {
        if (player == null) return;
        playerPages.remove(player.getUniqueId());
    }
}
