package dev.starcore.starcore.module.lease.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.lease.LeaseService;
import dev.starcore.starcore.module.lease.model.LeaseContract;
import dev.starcore.starcore.module.lease.model.LeaseStatus;
import dev.starcore.starcore.module.lease.model.LeaseType;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 租约契约命令处理器
 * /lease <子命令>
 */
public final class LeaseCommand implements CommandExecutor, TabCompleter {

    private final LeaseService leaseService;
    private final NationService nationService;
    private final MessageService messages;

    public LeaseCommand(LeaseService leaseService, NationService nationService, MessageService messages) {
        this.leaseService = leaseService;
        this.nationService = nationService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(messages.format("lease.player-only"), NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "create", "c" -> handleCreate(player, args);
                case "list", "ls" -> handleList(player, args);
                case "info", "i" -> handleInfo(player, args);
                case "sign", "s" -> handleSign(player, args);
                case "reject", "r" -> handleReject(player, args);
                case "terminate", "t" -> handleTerminate(player, args);
                case "renew", "rn" -> handleRenew(player, args);
                case "pay" -> handlePay(player, args);
                case "accept" -> handleAccept(player, args);
                default -> showHelp(player);
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        // /lease create <type> <regionId> <rent> <duration>
        if (args.length < 5) {
            player.sendMessage(Component.text("用法: /lease create <类型> <区域ID> <月租金> <天数>", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("类型: territory, resource, building, military, port, trade, farm, mine", NamedTextColor.GRAY));
            return;
        }

        // 检查玩家是否在国家中
        Optional<Nation> nationOpt = nationService.getNationByMember(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("lease.not-in-nation"), NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();

        // 解析租约类型
        LeaseType type;
        try {
            type = LeaseType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("无效的租约类型: " + args[1], NamedTextColor.RED));
            return;
        }

        String regionId = args[2];

        // 解析租金
        BigDecimal rent;
        try {
            rent = new BigDecimal(args[3]);
            if (rent.compareTo(BigDecimal.ZERO) <= 0) {
                player.sendMessage(Component.text("租金必须大于0", NamedTextColor.RED));
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的租金金额: " + args[3], NamedTextColor.RED));
            return;
        }

        // 解析租期
        int duration;
        try {
            duration = Integer.parseInt(args[4]);
            if (duration <= 0) {
                player.sendMessage(Component.text("租期必须大于0", NamedTextColor.RED));
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的租期天数: " + args[4], NamedTextColor.RED));
            return;
        }

        // 创建租约（出租方为当前国家，承租方为空）
        LeaseContract contract = leaseService.createLease(
            nation.id(),
            player.getUniqueId(),
            null,
            player.getUniqueId(),
            type,
            regionId,
            rent,
            duration
        );

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 租约已创建 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("租约ID: " + contract.id().toString().substring(0, 8), NamedTextColor.GRAY));
        player.sendMessage(Component.text("类型: " + type.getZhName(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("区域: " + regionId, NamedTextColor.GRAY));
        player.sendMessage(Component.text("月租金: " + rent, NamedTextColor.GRAY));
        player.sendMessage(Component.text("租期: " + duration + "天", NamedTextColor.GRAY));
        player.sendMessage(Component.text("状态: " + LeaseStatus.PENDING.getZhName(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("等待其他方签署后生效", NamedTextColor.AQUA));
    }

    private void handleList(Player player, String[] args) {
        // /lease list [lessor|tenant|all]
        String filter = args.length > 1 ? args[1].toLowerCase() : "all";

        Optional<Nation> nationOpt = nationService.getNationByMember(player.getUniqueId());
        Collection<LeaseContract> leases;

        if (filter.equals("lessor") && nationOpt.isPresent()) {
            leases = leaseService.getLeasesByLessor(nationOpt.get().id());
        } else if (filter.equals("tenant") && nationOpt.isPresent()) {
            leases = leaseService.getLeasesByTenant(nationOpt.get().id());
        } else if (filter.equals("pending")) {
            // 只显示待签署的
            Collection<LeaseContract> all = leaseService.getLeasesByPlayer(player.getUniqueId());
            leases = all.stream()
                .filter(c -> c.status() == LeaseStatus.PENDING)
                .collect(Collectors.toList());
        } else {
            leases = leaseService.getLeasesByPlayer(player.getUniqueId());
        }

        if (leases.isEmpty()) {
            player.sendMessage(Component.text("暂无租约", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 租约列表 ===", NamedTextColor.GOLD));
        for (LeaseContract lease : leases) {
            NamedTextColor statusColor = getStatusColor(lease.status());
            player.sendMessage(Component.text(
                String.format("[%s] %s - %s | %s/%s | 剩余%d天",
                    lease.id().toString().substring(0, 8),
                    lease.type().getZhName(),
                    lease.regionId(),
                    lease.status().getZhName(),
                    formatCurrency(lease.monthlyRent()),
                    lease.getRemainingDays()),
                statusColor
            ));
        }
        player.sendMessage(Component.text(""));
    }

    private void handleInfo(Player player, String[] args) {
        // /lease info <contractId>
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /lease info <租约ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID contractId = parseContractId(player, args[1]);
        Optional<LeaseContract> contractOpt = leaseService.getLease(contractId);

        if (contractOpt.isEmpty()) {
            player.sendMessage(Component.text("租约不存在: " + args[1], NamedTextColor.RED));
            return;
        }

        LeaseContract contract = contractOpt.get();

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 租约详情 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("ID: " + contract.id().toString(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("类型: " + contract.type().getZhName(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("区域: " + contract.regionId(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("月租金: " + formatCurrency(contract.monthlyRent()), NamedTextColor.GRAY));
        player.sendMessage(Component.text("总价值: " + formatCurrency(contract.totalValue()), NamedTextColor.GRAY));
        player.sendMessage(Component.text("状态: " + contract.status().getZhName(), getStatusColor(contract.status())));
        player.sendMessage(Component.text("出租方: " + (contract.lessorNationId() != null ? "国家" : "个人"), NamedTextColor.GRAY));
        player.sendMessage(Component.text("承租方: " + (contract.tenantNationId() != null ? "国家" : (contract.tenantPlayerId() != null ? "个人" : "无")), NamedTextColor.GRAY));

        if (contract.startDate() != null) {
            player.sendMessage(Component.text("开始日期: " + contract.startDate().toString(), NamedTextColor.GRAY));
        }
        if (contract.endDate() != null) {
            player.sendMessage(Component.text("结束日期: " + contract.endDate().toString(), NamedTextColor.GRAY));
            player.sendMessage(Component.text("剩余天数: " + contract.getRemainingDays(), NamedTextColor.GRAY));
        }
        if (contract.nextPaymentDue() != null) {
            player.sendMessage(Component.text("下次付款: " + contract.nextPaymentDue().toString(), NamedTextColor.GRAY));
        }

        player.sendMessage(Component.text(""));
    }

    private void handleSign(Player player, String[] args) {
        // /lease sign <contractId>
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /lease sign <租约ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID contractId = parseContractId(player, args[1]);

        if (leaseService.signLease(contractId, player.getUniqueId())) {
            player.sendMessage(Component.text("租约签署成功!", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("签署失败，租约不存在或已签署", NamedTextColor.RED));
        }
    }

    private void handleReject(Player player, String[] args) {
        // /lease reject <contractId>
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /lease reject <租约ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID contractId = parseContractId(player, args[1]);

        if (leaseService.rejectLease(contractId, player.getUniqueId())) {
            player.sendMessage(Component.text("已拒绝租约", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("拒绝失败，租约不存在或无法拒绝", NamedTextColor.RED));
        }
    }

    private void handleTerminate(Player player, String[] args) {
        // /lease terminate <contractId> [reason]
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /lease terminate <租约ID> [原因]", NamedTextColor.YELLOW));
            return;
        }

        UUID contractId = parseContractId(player, args[1]);
        String reason = args.length > 2 ? args[2] : "Terminated by party";

        if (leaseService.terminateLease(contractId, player.getUniqueId(), reason)) {
            player.sendMessage(Component.text("租约已终止", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("终止失败，您可能不是租约相关方", NamedTextColor.RED));
        }
    }

    private void handleRenew(Player player, String[] args) {
        // /lease renew <contractId> <days>
        if (args.length < 3) {
            player.sendMessage(Component.text("用法: /lease renew <租约ID> <天数>", NamedTextColor.YELLOW));
            return;
        }

        UUID contractId = parseContractId(player, args[1]);
        int days;
        try {
            days = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的天数: " + args[2], NamedTextColor.RED));
            return;
        }

        if (leaseService.renewLease(contractId, player.getUniqueId(), days)) {
            player.sendMessage(Component.text("租约已续期 " + days + " 天", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("续期失败，只有承租方可以续租", NamedTextColor.RED));
        }
    }

    private void handlePay(Player player, String[] args) {
        // /lease pay <contractId> [months]
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /lease pay <租约ID> [月数]", NamedTextColor.YELLOW));
            return;
        }

        UUID contractId = parseContractId(player, args[1]);
        int months = args.length > 2 ? Integer.parseInt(args[2]) : 1;

        if (leaseService.payRent(contractId, player.getUniqueId(), months)) {
            Optional<LeaseContract> contractOpt = leaseService.getLease(contractId);
            if (contractOpt.isPresent()) {
                LeaseContract contract = contractOpt.get();
                player.sendMessage(Component.text("已支付 " + months + " 个月租金: " + formatCurrency(contract.monthlyRent().multiply(BigDecimal.valueOf(months))), NamedTextColor.GREEN));
            }
        } else {
            player.sendMessage(Component.text("支付失败，资金不足或您不是承租方", NamedTextColor.RED));
        }
    }

    private void handleAccept(Player player, String[] args) {
        // /lease accept <contractId> - 签署并接受租约
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /lease accept <租约ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID contractId = parseContractId(player, args[1]);

        // 检查租约是否需要玩家签署
        Optional<LeaseContract> contractOpt = leaseService.getLease(contractId);
        if (contractOpt.isEmpty()) {
            player.sendMessage(Component.text("租约不存在", NamedTextColor.RED));
            return;
        }

        LeaseContract contract = contractOpt.get();

        // 签署租约
        if (leaseService.signLease(contractId, player.getUniqueId())) {
            player.sendMessage(Component.text("租约签署成功!", NamedTextColor.GREEN));
            if (contract.isActive()) {
                player.sendMessage(Component.text("租约已生效!", NamedTextColor.AQUA));
            }
        } else {
            player.sendMessage(Component.text("签署失败", NamedTextColor.RED));
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 租约系统 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/lease create <类型> <区域> <租金> <天数> - 创建租约", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/lease list [lessor|tenant|pending] - 查看租约列表", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/lease info <ID> - 查看租约详情", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/lease sign <ID> - 签署租约", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/lease reject <ID> - 拒绝租约", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/lease terminate <ID> [原因] - 终止租约", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/lease renew <ID> <天数> - 续租", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/lease pay <ID> [月数] - 支付租金", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/lease accept <ID> - 签署并接受租约", NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("类型: territory, resource, building, military, port, trade, farm, mine", NamedTextColor.DARK_GRAY));
    }

    private UUID parseContractId(Player player, String idStr) {
        // 支持完整UUID
        if (idStr.length() == 36) {
            return UUID.fromString(idStr);
        }
        // 支持短ID（前8位）
        if (idStr.length() == 8) {
            Collection<LeaseContract> playerLeases = leaseService.getLeasesByPlayer(player.getUniqueId());
            Optional<UUID> match = playerLeases.stream()
                .map(LeaseContract::id)
                .filter(id -> id.toString().startsWith(idStr))
                .findFirst();
            if (match.isPresent()) {
                return match.get();
            }
        }
        throw new IllegalArgumentException("租约不存在: " + idStr);
    }

    private NamedTextColor getStatusColor(LeaseStatus status) {
        return switch (status) {
            case PENDING -> NamedTextColor.YELLOW;
            case ACTIVE -> NamedTextColor.GREEN;
            case EXPIRED -> NamedTextColor.RED;
            case TERMINATED -> NamedTextColor.DARK_GRAY;
            case OVERDUE -> NamedTextColor.DARK_RED;
            case COMPLETED -> NamedTextColor.AQUA;
        };
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0.00";
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toString();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("create", "list", "info", "sign", "reject", "terminate", "renew", "pay", "accept");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return Arrays.stream(LeaseType.values())
                .map(t -> t.name().toLowerCase())
                .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            return List.of("all", "lessor", "tenant", "pending");
        }

        return List.of();
    }
}
