package dev.starcore.starcore.nation.command;
import java.util.Optional;

import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.module.business.BusinessService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.ResourceTradeService;
import dev.starcore.starcore.module.treasury.TaxationService;
import dev.starcore.starcore.module.treasury.TaxationService.*;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.nation.permission.NationPermission;
import dev.starcore.starcore.nation.permission.NationPermissionChecker;
import dev.starcore.starcore.nation.permission.PermissionLevel;
import dev.starcore.starcore.nation.tax.TaxCollectionService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 税收管理命令
 * 命令: /tax <子命令>
 *
 * 优先使用 TreasuryService (内部 TaxationService)，
 * TaxCollectionService 仅用于兼容和记录
 */
public class TaxCommand implements CommandExecutor, TabCompleter {

    private final TreasuryService treasuryService;
    private final TaxCollectionService taxCollectionService; // 保留用于记录
    private final NationService nationService;
    private final InternalEconomyService economyService;
    private final OnlinePlayerDirectory onlinePlayerDirectory;
    private final NationPermissionChecker permissionChecker;
    private final ResourceTradeService resourceTradeService;
    private final BusinessService businessService;

    public TaxCommand(
            TreasuryService treasuryService,
            TaxCollectionService taxCollectionService,
            NationService nationService,
            InternalEconomyService economyService,
            OnlinePlayerDirectory onlinePlayerDirectory,
            ResourceTradeService resourceTradeService,
            BusinessService businessService
    ) {
        this.treasuryService = treasuryService;
        this.taxCollectionService = taxCollectionService;
        this.nationService = nationService;
        this.economyService = economyService;
        this.onlinePlayerDirectory = onlinePlayerDirectory;
        this.permissionChecker = new NationPermissionChecker();
        this.resourceTradeService = resourceTradeService;
        this.businessService = businessService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (args.length == 0) {
            return handleInfo(player);
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "set" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /tax set <税率%>");
                    return true;
                }
                try {
                    double rate = Double.parseDouble(args[1]);
                    return handleSetRate(player, rate);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的税率");
                    return true;
                }
            }
            case "collect" -> {
                return handleCollect(player);
            }
            case "info" -> {
                return handleInfo(player);
            }
            case "history" -> {
                return handleHistory(player);
            }
            case "toggle" -> {
                return handleToggle(player);
            }
            default -> {
                player.sendMessage("§c未知子命令: " + subCommand);
                sendHelp(player);
                return true;
            }
        }
    }

    private boolean handleSetRate(Player player, double rate) {
        // 获取玩家的Nation
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation nation = nationOpt.get();
        NationId nationId = nation.id();

        // 检查权限:税率设置需要 LEADER 级别
        if (!checkNationPermission(player, nation, NationPermission.TAX_SET)) {
            player.sendMessage("§c你没有权限设置税率");
            return true;
        }

        if (rate < 0 || rate > 100) {
            player.sendMessage("§c税率必须在0-100之间");
            return true;
        }

        // 将税率存储到 TaxationService (使用 INCOME_TAX 类型)
        BigDecimal taxRate = BigDecimal.valueOf(rate / 100.0);
        TaxConfig config = new TaxConfig(
            TaxType.INCOME_TAX,
            true,                          // 启用
            BigDecimal.ZERO,               // 无固定金额
            taxRate,                       // 百分比
            BigDecimal.ZERO,               // 无最低余额
            1440,                          // 每天
            false                          // 不自动征收 (手动命令触发)
        );
        treasuryService.setTaxConfig(nationId, TaxType.INCOME_TAX, config);

        // 同时更新兼容层的缓存
        taxCollectionService.setManualTaxRate(nationId.value(), rate / 100.0);

        player.sendMessage(String.format("§a税率已设置为 %.1f%%", rate));

        // 广播给Nation成员
        broadcastToNation(nationId.value(), String.format(
            "§6[税收] §e%s §7将税率设置为 §e%.1f%%",
            player.getName(),
            rate
        ));

        return true;
    }

    private boolean handleCollect(Player player) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation nation = nationOpt.get();
        NationId nationId = nation.id();

        if (!checkNationPermission(player, nation, NationPermission.TAX_COLLECT)) {
            player.sendMessage("§c你没有权限手动收税");
            return true;
        }

        player.sendMessage("§a正在收集税款...");

        // 构建税收上下文
        List<PlayerIncome> playerIncomes = new ArrayList<>();
        for (var member : nation.members()) {
            BigDecimal balance = getPlayerBalance(member.playerId());

            // 当前估算: 余额 * 0.0333 (约 1/30)
            BigDecimal income = balance
                .multiply(BigDecimal.valueOf(0.0333))
                .setScale(2, java.math.RoundingMode.DOWN);

            playerIncomes.add(new PlayerIncome(member.playerId(), income, balance));
        }

        // 计算真实贸易额（从贸易服务获取最近30天数据）
        BigDecimal totalTradeValue = BigDecimal.ZERO;
        if (resourceTradeService != null) {
            totalTradeValue = resourceTradeService.calculateTradeVolume(nationId, Duration.ofDays(30));
        }

        // 计算真实商业活跃商家数（从 BusinessService 获取最近30天数据）
        int businessCount = 0;
        if (businessService != null) {
            businessCount = businessService.getActiveBusinessCount(nationId, 30);
        }

        TaxContext context = new TaxContext(
            Math.max(0, nationService.claimCount(nationId)),
            nation.members().size(),
            businessCount,
            totalTradeValue,
            playerIncomes
        );

        // 通过 TreasuryService 征收所有税种
        Map<TaxType, BigDecimal> results = treasuryService.collectAllTaxes(nationId, context);

        // 计算总金额
        BigDecimal totalAmount = results.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        int collected = 0;
        for (var entry : results.entrySet()) {
            if (entry.getValue().signum() > 0) {
                collected++;
            }
        }

        // 记录到兼容层
        var compatResult = new TaxCollectionService.TaxCollectionResult(
            collected, 0, totalAmount.doubleValue()
        );

        player.sendMessage("§6§l==== 税收收集结果 ====");
        for (var entry : results.entrySet()) {
            if (entry.getValue().signum() > 0) {
                player.sendMessage("§7" + entry.getKey().getDisplayName() + ": §6" +
                    String.format("%.2f", entry.getValue().doubleValue()) + " §7金币");
            }
        }
        // 分隔
        player.sendMessage("§7总金额: §6" + String.format("%.2f", totalAmount.doubleValue()) + " §7金币");

        // 广播给Nation
        broadcastToNation(nationId.value(), String.format(
            "§6[税收] §7已收集税款 §6%.2f §7金币",
            totalAmount.doubleValue()
        ));

        return true;
    }

    private boolean handleInfo(Player player) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation nation = nationOpt.get();
        NationId nationId = nation.id();

        // 获取所有税种配置
        Map<TaxType, TaxConfig> configs = treasuryService.getTaxConfigs(nationId);

        // 获取国库余额
        BigDecimal balance = treasuryService.balance(nationId);

        player.sendMessage("§6§l==== 税收信息 ====");
        player.sendMessage("§7国库余额: §e" + String.format("%.2f", balance.doubleValue()) + " §7金币");
        player.sendMessage("§7总成员: §e" + nation.members().size());
        // 分隔
        player.sendMessage("§e§l税种配置:");

        boolean hasEnabled = false;
        for (Map.Entry<TaxType, TaxConfig> entry : configs.entrySet()) {
            TaxConfig config = entry.getValue();
            String status = config != null && config.enabled() ? "§a启用" : "§c禁用";
            String details = "";

            if (config != null && config.enabled()) {
                hasEnabled = true;
                if (config.fixedAmount().signum() > 0) {
                    details += " 固定:" + String.format("%.2f", config.fixedAmount().doubleValue());
                }
                if (config.percentRate().signum() > 0) {
                    details += " 比例:" + String.format("%.1f%%", config.percentRate().doubleValue() * 100);
                }
            }

            player.sendMessage("§7" + entry.getKey().getDisplayName() + ": " + status + details);
        }

        if (!hasEnabled) {
            player.sendMessage("§7  (无启用的税种，使用 /tax set 启用所得税)");
        }

        // 获取兼容层的历史记录
        var record = taxCollectionService.getTaxRecord(nationId.value());
        if (record != null) {
            // 分隔
            player.sendMessage("§e§l历史统计:");
            player.sendMessage("§7总收集次数: §f" + record.getTotalCollections());
            player.sendMessage("§7累计收集: §6" + String.format("%.2f", record.getTotalAmount()) + " §7金币");
            player.sendMessage("§7平均每次: §6" + String.format("%.2f", record.getAveragePerCollection()) + " §7金币");
        }

        return true;
    }

    private boolean handleHistory(Player player) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }
        Nation nation = nationOpt.get();
        NationId nationId = nation.id();

        // 获取税收历史
        List<TaxRevenue> history = treasuryService.getTaxHistory(nationId, null, 20);

        if (history.isEmpty()) {
            player.sendMessage("§7暂无税收历史");
            return true;
        }

        player.sendMessage("§6§l==== 税收历史 ====");
        player.sendMessage("§7最近 " + Math.min(10, history.size()) + " 条记录:");
        // 分隔

        for (int i = 0; i < Math.min(10, history.size()); i++) {
            TaxRevenue revenue = history.get(i);
            String timeAgo = getTimeAgo(revenue.collectedAt());
            player.sendMessage(String.format(
                "§7[%s] §e%s §7: §6%.2f",
                timeAgo,
                revenue.type().getDisplayName(),
                revenue.amount().doubleValue()
            ));
        }

        return true;
    }

    private boolean handleToggle(Player player) {
        if (!player.hasPermission("starcore.admin")) {
            player.sendMessage("§c你没有权限");
            return true;
        }

        taxCollectionService.setEnabled(!taxCollectionService.isEnabled());
        player.sendMessage("§a税收功能已" + (taxCollectionService.isEnabled() ? "启用" : "禁用"));

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§7可用命令:");
        player.sendMessage("  §e/tax set <税率%> §7- 设置所得税率");
        player.sendMessage("  §e/tax collect §7- 手动收集税款");
        player.sendMessage("  §e/tax info §7- 查看税收信息");
        player.sendMessage("  §e/tax history §7- 查看税收历史");
        player.sendMessage("  §e/tax toggle §7- 切换功能（管理员）");
    }

    // ==================== 辅助方法 ====================

    private BigDecimal getPlayerBalance(UUID playerId) {
        return economyService.balance(playerId);
    }

    private String getTimeAgo(java.time.Instant instant) {
        if (instant == null) return "未知";
        long seconds = java.time.Duration.between(instant, java.time.Instant.now()).getSeconds();
        if (seconds < 60) return seconds + "秒前";
        if (seconds < 3600) return (seconds / 60) + "分钟前";
        if (seconds < 86400) return (seconds / 3600) + "小时前";
        return (seconds / 86400) + "天前";
    }

    /**
     * 检查玩家是否拥有 Nation 权限
     */
    private boolean checkNationPermission(Player player, Nation nation, NationPermission permission) {
        // 创始人拥有所有权限
        if (nation.founderId().equals(player.getUniqueId())) {
            return true;
        }

        // 获取玩家的层级
        PermissionLevel level = getPlayerPermissionLevel(player, nation);
        if (level == null) {
            return false;
        }

        // 检查层级是否满足权限要求
        return level.isAtLeast(permission.getDefaultLevel());
    }

    /**
     * 获取玩家在 Nation 中的权限层级
     */
    private PermissionLevel getPlayerPermissionLevel(Player player, Nation nation) {
        UUID playerId = player.getUniqueId();

        // 创始人
        if (nation.founderId().equals(playerId)) {
            return PermissionLevel.FOUNDER;
        }

        // 检查成员
        for (var member : nation.members()) {
            if (member.playerId().equals(playerId)) {
                // 简单实现:默认所有成员都是 MEMBER 级别
                String rank = member.rank();
                if (rank == null) {
                    return PermissionLevel.MEMBER;
                }
                // 根据 rank 字符串判断层级
                switch (rank.toLowerCase()) {
                    case "leader", "officer", "admin" -> {
                        return PermissionLevel.LEADER;
                    }
                    case "trusted", "elder", "moderator" -> {
                        return PermissionLevel.TRUSTED;
                    }
                    default -> {
                        return PermissionLevel.MEMBER;
                    }
                }
            }
        }

        return null;
    }

    private void broadcastToNation(UUID nationId, String message) {
        // 获取 Nation
        Optional<Nation> nationOpt = nationService.nationById(NationId.of(nationId));
        if (nationOpt.isEmpty()) {
            return;
        }

        Nation nation = nationOpt.get();

        // 遍历所有成员，向在线成员发送消息
        for (var member : nation.members()) {
            Optional<? extends Player> playerOpt = onlinePlayerDirectory.findOnlinePlayer(member.playerId());
            playerOpt.ifPresent(player -> player.sendMessage(message));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("set", "collect", "info", "history", "toggle")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return Arrays.asList("0", "5", "10", "15", "20");
        }

        return Collections.emptyList();
    }
}
