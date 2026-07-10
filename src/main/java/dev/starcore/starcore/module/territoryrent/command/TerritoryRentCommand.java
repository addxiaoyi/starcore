package dev.starcore.starcore.module.territoryrent.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.territoryrent.TerritoryRentService;
import dev.starcore.starcore.module.territoryrent.model.Rental;
import dev.starcore.starcore.module.territoryrent.model.RentalRequest;
import dev.starcore.starcore.module.territoryrent.model.RentalStatus;
import dev.starcore.starcore.module.treasury.TreasuryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 领土租借命令处理
 */
public final class TerritoryRentCommand implements CommandExecutor, TabCompleter {

    private final TerritoryRentService service;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final MessageService messages;
    private final DecimalFormat df = new DecimalFormat("#,##0.00");

    public TerritoryRentCommand(TerritoryRentService service, NationService nationService,
                              TreasuryService treasuryService, MessageService messages) {
        this.service = service;
        this.nationService = nationService;
        this.treasuryService = treasuryService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                           @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(player, args);
            case "accept" -> handleAccept(player, args);
            case "reject" -> handleReject(player, args);
            case "cancel" -> handleCancel(player, args);
            case "renew" -> handleRenew(player, args);
            case "terminate" -> handleTerminate(player, args);
            case "list" -> handleList(player, args);
            case "info" -> handleInfo(player, args);
            case "requests" -> handleRequests(player);
            default -> sendHelp(player);
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text("用法: /trent create <目标国家> <每日租金> <天数>", NamedTextColor.RED));
            return;
        }

        // 检查玩家是否在国家中
        var nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你必须属于一个国家才能发起租借请求", NamedTextColor.RED));
            return;
        }

        // 解析目标国家
        String targetName = args[1];
        var targetNationOpt = nationService.nationByName(targetName);
        if (targetNationOpt.isEmpty()) {
            player.sendMessage(Component.text("国家 " + targetName + " 不存在", NamedTextColor.RED));
            return;
        }
        Nation targetNation = targetNationOpt.get();

        // 解析租金
        BigDecimal dailyRent;
        try {
            dailyRent = new BigDecimal(args[2]);
            if (dailyRent.compareTo(BigDecimal.ZERO) <= 0) {
                player.sendMessage(Component.text("租金必须大于0", NamedTextColor.RED));
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的租金金额", NamedTextColor.RED));
            return;
        }

        // 解析天数
        int days;
        try {
            days = Integer.parseInt(args[3]);
            if (days <= 0 || days > 365) {
                player.sendMessage(Component.text("租借天数必须在1-365之间", NamedTextColor.RED));
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的天数", NamedTextColor.RED));
            return;
        }

        // 获取玩家脚下区块
        ChunkCoordinate coordinate = new ChunkCoordinate(
            player.getWorld().getName(),
            player.getLocation().getChunk().getX(),
            player.getLocation().getChunk().getZ()
        );

        // 检查区块是否已被租借
        if (service.isChunkRented(coordinate)) {
            player.sendMessage(Component.text("该区块已被租借", NamedTextColor.RED));
            return;
        }

        // 创建请求
        try {
            RentalRequest request = service.createRentalRequest(
                player.getUniqueId(),
                nationOpt.get().id(),
                targetNation.id(),
                coordinate,
                days,
                dailyRent
            );

            BigDecimal total = dailyRent.multiply(BigDecimal.valueOf(days));
            player.sendMessage(Component.text("租借请求已发送!", NamedTextColor.GREEN));
            player.sendMessage(Component.text("目标国家: " + targetNation.name(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("区块: " + coordinate.world() + " (" + coordinate.x() + ", " + coordinate.z() + ")", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("每日租金: " + df.format(dailyRent), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("租借天数: " + days, NamedTextColor.YELLOW));
            player.sendMessage(Component.text("总租金: " + df.format(total), NamedTextColor.AQUA));
        } catch (Exception e) {
            player.sendMessage(Component.text("创建租借请求失败: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleAccept(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /trent accept <请求ID>", NamedTextColor.RED));
            return;
        }

        UUID requestId;
        try {
            requestId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("无效的请求ID", NamedTextColor.RED));
            return;
        }

        var requests = service.getPendingRequestsForNation(getPlayerNationId(player));
        var requestOpt = requests.stream().filter(r -> r.id().equals(requestId)).findFirst();

        if (requestOpt.isEmpty()) {
            player.sendMessage(Component.text("请求不存在或已过期", NamedTextColor.RED));
            return;
        }

        var rentalOpt = service.acceptRequest(requestId, player.getUniqueId());
        if (rentalOpt.isEmpty()) {
            player.sendMessage(Component.text("接受租借请求失败", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("已接受租借请求!", NamedTextColor.GREEN));
        Rental rental = rentalOpt.get();
        player.sendMessage(Component.text("区块已租借给 " + getNationName(rental.tenantNationId()) + "，租期 " + rental.durationDays() + " 天", NamedTextColor.YELLOW));
    }

    private void handleReject(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /trent reject <请求ID> [原因]", NamedTextColor.RED));
            return;
        }

        UUID requestId;
        try {
            requestId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("无效的请求ID", NamedTextColor.RED));
            return;
        }

        if (!service.rejectRequest(requestId, player.getUniqueId())) {
            player.sendMessage(Component.text("拒绝租借请求失败", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("已拒绝租借请求", NamedTextColor.YELLOW));
    }

    private void handleCancel(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /trent cancel <请求ID>", NamedTextColor.RED));
            return;
        }

        UUID requestId;
        try {
            requestId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("无效的请求ID", NamedTextColor.RED));
            return;
        }

        if (!service.cancelRequest(requestId, player.getUniqueId())) {
            player.sendMessage(Component.text("取消租借请求失败", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("已取消租借请求", NamedTextColor.YELLOW));
    }

    private void handleRenew(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("用法: /trent renew <租借ID> <天数>", NamedTextColor.RED));
            return;
        }

        UUID rentalId;
        try {
            rentalId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("无效的租借ID", NamedTextColor.RED));
            return;
        }

        int days;
        try {
            days = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的天数", NamedTextColor.RED));
            return;
        }

        if (!service.renewRental(rentalId, days, player.getUniqueId())) {
            player.sendMessage(Component.text("续租失败", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("续租成功! 已延长 " + days + " 天", NamedTextColor.GREEN));
    }

    private void handleTerminate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /trent terminate <租借ID>", NamedTextColor.RED));
            return;
        }

        UUID rentalId;
        try {
            rentalId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("无效的租借ID", NamedTextColor.RED));
            return;
        }

        if (!service.terminateRental(rentalId, player.getUniqueId())) {
            player.sendMessage(Component.text("终止租借失败", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("租借已终止", NamedTextColor.YELLOW));
    }

    private void handleList(Player player, String[] args) {
        var nationId = getPlayerNationId(player);
        if (nationId == null) {
            player.sendMessage(Component.text("你必须属于一个国家", NamedTextColor.RED));
            return;
        }

        String type = args.length > 1 ? args[1].toLowerCase() : "all";

        Component header = Component.text("═══════════════════════════════════", NamedTextColor.GOLD)
            .appendNewline()
            .append(Component.text(" 领土租借列表", NamedTextColor.GOLD))
            .appendNewline()
            .append(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));

        player.sendMessage(header);

        List<Rental> rentals = new ArrayList<>();
        switch (type) {
            case "owner" -> rentals.addAll(service.getRentalsAsOwner(nationId.value().toString()));
            case "tenant" -> rentals.addAll(service.getRentalsAsTenant(nationId.value().toString()));
            default -> {
                rentals.addAll(service.getRentalsAsOwner(nationId.value().toString()));
                rentals.addAll(service.getRentalsAsTenant(nationId.value().toString()));
            }
        }

        if (rentals.isEmpty()) {
            player.sendMessage(Component.text("暂无租借记录", NamedTextColor.GRAY));
            return;
        }

        for (Rental rental : rentals) {
            NamedTextColor color = rental.isExpired() ? NamedTextColor.RED :
                            rental.status() == RentalStatus.ACTIVE ? NamedTextColor.GREEN : NamedTextColor.YELLOW;

            String role = rental.ownerNationId().equals(nationId.value().toString()) ? "出租" : "承租";
            Component line = Component.text("[ID: " + rental.id().toString().substring(0, 8) + "] ", NamedTextColor.GRAY)
                .append(Component.text(role + ": ", color))
                .append(Component.text(rental.coordinate().world() + " (" + rental.coordinate().x() + ", " + rental.coordinate().z() + ")", NamedTextColor.WHITE))
                .appendNewline()
                .append(Component.text("  剩余: " + rental.remainingDays() + " 天 | 日租: " + df.format(rental.dailyRent()), color));

            player.sendMessage(line);
        }
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /trent info <租借ID>", NamedTextColor.RED));
            return;
        }

        UUID rentalId;
        try {
            rentalId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("无效的租借ID", NamedTextColor.RED));
            return;
        }

        var rentalOpt = service.getRental(rentalId);
        if (rentalOpt.isEmpty()) {
            player.sendMessage(Component.text("租借不存在", NamedTextColor.RED));
            return;
        }

        Rental rental = rentalOpt.get();
        Component info = Component.text("═══ 租借详情 ═══", NamedTextColor.GOLD)
            .appendNewline()
            .append(Component.text("ID: " + rental.id(), NamedTextColor.YELLOW))
            .appendNewline()
            .append(Component.text("状态: " + rental.status(), rental.isExpired() ? NamedTextColor.RED : NamedTextColor.GREEN))
            .appendNewline()
            .append(Component.text("区块: " + rental.coordinate().world() + " (" + rental.coordinate().x() + ", " + rental.coordinate().z() + ")", NamedTextColor.WHITE))
            .appendNewline()
            .append(Component.text("出租方: " + getNationName(rental.ownerNationId()), NamedTextColor.AQUA))
            .appendNewline()
            .append(Component.text("承租方: " + getNationName(rental.tenantNationId()), NamedTextColor.AQUA))
            .appendNewline()
            .append(Component.text("日租: " + df.format(rental.dailyRent()), NamedTextColor.YELLOW))
            .appendNewline()
            .append(Component.text("租期: " + rental.durationDays() + " 天", NamedTextColor.YELLOW))
            .appendNewline()
            .append(Component.text("剩余: " + rental.remainingDays() + " 天", rental.remainingDays() < 7 ? NamedTextColor.RED : NamedTextColor.GREEN))
            .appendNewline()
            .append(Component.text("权限等级: " + rental.permissionLevel(), NamedTextColor.GRAY));

        player.sendMessage(info);
    }

    private void handleRequests(Player player) {
        var nationId = getPlayerNationId(player);
        if (nationId == null) {
            player.sendMessage(Component.text("你必须属于一个国家", NamedTextColor.RED));
            return;
        }

        var requests = service.getPendingRequestsForNation(nationId);

        Component header = Component.text("═══ 待处理租借请求 ═══", NamedTextColor.GOLD);
        player.sendMessage(header);

        if (requests.isEmpty()) {
            player.sendMessage(Component.text("暂无待处理的请求", NamedTextColor.GRAY));
            return;
        }

        for (RentalRequest request : requests) {
            Component reqInfo = Component.text("[" + request.id().toString().substring(0, 8) + "] ", NamedTextColor.GRAY)
                .append(Component.text("来自: " + getNationName(request.requesterNationId()), NamedTextColor.AQUA))
                .appendNewline()
                .append(Component.text("区块: " + request.coordinate().world() + " (" + request.coordinate().x() + ", " + request.coordinate().z() + ")", NamedTextColor.WHITE))
                .appendNewline()
                .append(Component.text("日租: " + df.format(request.dailyRent()) + " x " + request.durationDays() + "天 = " + df.format(request.totalRent()), NamedTextColor.YELLOW))
                .clickEvent(ClickEvent.suggestCommand("/trent accept " + request.id()))
                .hoverEvent(HoverEvent.showText(Component.text("点击接受此请求")));

            player.sendMessage(reqInfo);
        }
    }

    private void sendHelp(Player player) {
        Component help = Component.text("═══ 领土租借帮助 ═══", NamedTextColor.GOLD)
            .appendNewline()
            .append(Component.text("/trent create <国家> <日租> <天数>", NamedTextColor.YELLOW))
            .append(Component.text(" - 创建租借请求", NamedTextColor.GRAY))
            .appendNewline()
            .append(Component.text("/trent requests", NamedTextColor.YELLOW))
            .append(Component.text(" - 查看收到的请求", NamedTextColor.GRAY))
            .appendNewline()
            .append(Component.text("/trent accept <ID>", NamedTextColor.YELLOW))
            .append(Component.text(" - 接受请求", NamedTextColor.GRAY))
            .appendNewline()
            .append(Component.text("/trent reject <ID>", NamedTextColor.YELLOW))
            .append(Component.text(" - 拒绝请求", NamedTextColor.GRAY))
            .appendNewline()
            .append(Component.text("/trent cancel <ID>", NamedTextColor.YELLOW))
            .append(Component.text(" - 取消我的请求", NamedTextColor.GRAY))
            .appendNewline()
            .append(Component.text("/trent list [owner|tenant|all]", NamedTextColor.YELLOW))
            .append(Component.text(" - 查看租借列表", NamedTextColor.GRAY))
            .appendNewline()
            .append(Component.text("/trent renew <ID> <天数>", NamedTextColor.YELLOW))
            .append(Component.text(" - 续租", NamedTextColor.GRAY))
            .appendNewline()
            .append(Component.text("/trent terminate <ID>", NamedTextColor.YELLOW))
            .append(Component.text(" - 终止租借", NamedTextColor.GRAY))
            .appendNewline()
            .append(Component.text("/trent info <ID>", NamedTextColor.YELLOW))
            .append(Component.text(" - 查看详情", NamedTextColor.GRAY));

        player.sendMessage(help);
    }

    private NationId getPlayerNationId(Player player) {
        return nationService.nationOf(player.getUniqueId())
            .map(Nation::id)
            .orElse(null);
    }

    private String getNationName(String nationIdStr) {
        try {
            return nationService.nationById(NationId.of(UUID.fromString(nationIdStr)))
                .map(Nation::name)
                .orElse("未知国家");
        } catch (Exception e) {
            return "未知国家";
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("create", "accept", "reject", "cancel", "renew", "terminate", "list", "info", "requests"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "accept", "reject" -> {
                    var nationId = getPlayerNationId((Player) sender);
                    if (nationId != null) {
                        service.getPendingRequestsForNation(nationId).stream()
                            .map(r -> r.id().toString().substring(0, 8))
                            .forEach(completions::add);
                    }
                }
                case "list" -> completions.addAll(Arrays.asList("all", "owner", "tenant"));
            }
        }

        return completions;
    }
}
