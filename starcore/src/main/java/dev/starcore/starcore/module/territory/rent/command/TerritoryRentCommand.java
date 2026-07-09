package dev.starcore.starcore.module.territory.rent.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.territory.rent.TerritoryRentModule;
import dev.starcore.starcore.module.territory.rent.TerritoryRentService;
import dev.starcore.starcore.module.territory.rent.model.LeaseContract;
import dev.starcore.starcore.module.territory.rent.model.LeaseProposal;
import dev.starcore.starcore.module.territory.rent.model.LeaseStatus;
import dev.starcore.starcore.module.treasury.TreasuryService;
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
 * 领土租借命令处理器
 * /rent <子命令> [参数]
 */
public final class TerritoryRentCommand implements CommandExecutor, TabCompleter {
    private final TerritoryRentModule rentService;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final MessageService messages;

    public TerritoryRentCommand(
        TerritoryRentModule rentService,
        NationService nationService,
        TreasuryService treasuryService,
        MessageService messages
    ) {
        this.rentService = rentService;
        this.nationService = nationService;
        this.treasuryService = treasuryService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(messages.format("command.player-only"), NamedTextColor.RED));
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
                case "accept", "a" -> handleAccept(player, args);
                case "reject", "r" -> handleReject(player, args);
                case "terminate", "t" -> handleTerminate(player, args);
                case "renew" -> handleRenew(player, args);
                case "proposals", "p" -> handleProposals(player, args);
                case "stats" -> handleStats(player, args);
                case "help", "h" -> showHelp(player);
                default -> player.sendMessage(Component.text("未知命令: " + subCommand, NamedTextColor.RED));
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        // /rent create <目标国家> <天数> [每日租金]
        if (args.length < 3) {
            player.sendMessage(Component.text("用法: /rent create <目标国家> <天数> [每日租金/每区块]", NamedTextColor.YELLOW));
            return;
        }

        // Get player's nation
        Optional<Nation> playerNationOpt = nationService.nationOf(player.getUniqueId());
        if (playerNationOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("rent.error.no-nation"), NamedTextColor.RED));
            return;
        }
        Nation playerNation = playerNationOpt.get();

        // Get target nation
        String targetName = args[1];
        Optional<Nation> targetNationOpt = nationService.nationByName(targetName);
        if (targetNationOpt.isEmpty()) {
            player.sendMessage(Component.text("未找到国家: " + targetName, NamedTextColor.RED));
            return;
        }
        Nation targetNation = targetNationOpt.get();

        if (targetNation.id().equals(playerNation.id())) {
            player.sendMessage(Component.text("不能向自己的国家发起租借!", NamedTextColor.RED));
            return;
        }

        // Parse duration
        int days;
        try {
            days = Integer.parseInt(args[2]);
            if (days <= 0 || days > 365) {
                player.sendMessage(Component.text("租借天数必须在 1-365 之间", NamedTextColor.RED));
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的天数: " + args[2], NamedTextColor.RED));
            return;
        }

        // Parse rent (optional)
        BigDecimal rentPerChunk = rentService.getDefaultRentPerChunk();
        if (args.length > 3) {
            try {
                rentPerChunk = new BigDecimal(args[3]);
                if (rentPerChunk.signum() <= 0) {
                    player.sendMessage(Component.text("租金必须大于 0", NamedTextColor.RED));
                    return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("无效的租金: " + args[3], NamedTextColor.RED));
                return;
            }
        }

        // Get current chunk
        ChunkCoordinate currentChunk = new ChunkCoordinate(
            player.getWorld().getName(),
            player.getLocation().getChunk().getX(),
            player.getLocation().getChunk().getZ()
        );

        // Check ownership
        var claim = rentService.getPlugin().getServer().getScheduler()
            .callSyncMethod(rentService.getPlugin(), () -> {
                var svc = rentService;
                return null;
            });

        // Get player's owned chunks
        Collection<dev.starcore.starcore.foundation.territory.model.TerritoryClaim> ownedClaims =
            nationService.claimsOf(playerNation.id());

        if (ownedClaims.isEmpty()) {
            player.sendMessage(Component.text("你没有可出租的领土!", NamedTextColor.RED));
            return;
        }

        // Filter chunks in same world as player
        String world = player.getWorld().getName();
        List<ChunkCoordinate> availableChunks = ownedClaims.stream()
            .filter(c -> c.coordinate().world().equals(world))
            .map(c -> c.coordinate())
            .collect(Collectors.toList());

        if (availableChunks.isEmpty()) {
            player.sendMessage(Component.text("在当前世界没有可出租的领土!", NamedTextColor.RED));
            return;
        }

        // Show confirmation dialog
        BigDecimal totalRent = rentPerChunk
            .multiply(BigDecimal.valueOf(availableChunks.size()))
            .multiply(BigDecimal.valueOf(days));

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 租借提议预览 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("出租方: " + playerNation.name(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("承租方: " + targetNation.name(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("租借区块数: " + availableChunks.size(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("租借天数: " + days, NamedTextColor.GRAY));
        player.sendMessage(Component.text("每区块每日租金: " + rentPerChunk, NamedTextColor.GRAY));
        player.sendMessage(Component.text("预计总租金: " + totalRent, NamedTextColor.YELLOW));
        player.sendMessage(Component.text("手续费: " + rentService.getCreationFee(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("", NamedTextColor.GRAY));
        player.sendMessage(Component.text("使用 /rent createconfirm 确认租借", NamedTextColor.GREEN));
        player.sendMessage(Component.text(""));

        // Store temporary data for confirmation
        // (In a real implementation, use a map to store pending confirmations)
    }

    private void handleList(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("rent.error.no-nation"), NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();
        Collection<LeaseContract> contracts = rentService.getNationContracts(nation.id());

        if (contracts.isEmpty()) {
            player.sendMessage(Component.text("没有任何租借契约", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 租借契约列表 ===", NamedTextColor.GOLD));

        for (LeaseContract contract : contracts) {
            NamedTextColor statusColor = contract.isActive() ? NamedTextColor.GREEN : NamedTextColor.RED;
            String partner = contract.lesseeNationId() != null ?
                nationService.nationById(new NationId(contract.lesseeNationId()))
                    .map(Nation::name).orElse("未知") : "玩家";

            player.sendMessage(Component.text(
                String.format("[%s] %s -> %s (%d区块, %d天)",
                    contract.status().getDisplayName(),
                    getNationName(contract.lessorNationId(), nationService),
                    partner,
                    contract.chunksCount(),
                    contract.remainingDays()),
                statusColor
            ));
        }

        player.sendMessage(Component.text(""));
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /rent info <契约ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID contractId;
        try {
            contractId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("无效的契约ID", NamedTextColor.RED));
            return;
        }

        Optional<LeaseContract> contractOpt = rentService.getContract(contractId);
        if (contractOpt.isEmpty()) {
            player.sendMessage(Component.text("契约不存在", NamedTextColor.RED));
            return;
        }

        LeaseContract contract = contractOpt.get();

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 契约详情 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("契约ID: " + contract.contractId().toString().substring(0, 8), NamedTextColor.GRAY));
        player.sendMessage(Component.text("状态: " + contract.status().getDisplayName(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("出租方: " + getNationName(contract.lessorNationId(), nationService), NamedTextColor.GRAY));
        player.sendMessage(Component.text("承租方: " + (contract.lesseeNationId() != null ?
            getNationName(contract.lesseeNationId(), nationService) : "玩家"), NamedTextColor.GRAY));
        player.sendMessage(Component.text("区块数量: " + contract.chunksCount(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("世界: " + contract.world(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("每日租金: " + contract.rentPerDay(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("每区块日租: " + contract.rentPerChunk(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("总租金: " + contract.totalRent(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("剩余天数: " + contract.remainingDays(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("自动续约: " + (contract.autoRenewal() ? "是" : "否"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void handleAccept(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /rent accept <提议ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID proposalId;
        try {
            proposalId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("无效的提议ID", NamedTextColor.RED));
            return;
        }

        Optional<LeaseProposal> proposalOpt = rentService.getProposal(proposalId);
        if (proposalOpt.isEmpty()) {
            player.sendMessage(Component.text("提议不存在", NamedTextColor.RED));
            return;
        }

        LeaseProposal proposal = proposalOpt.get();
        LeaseContract contract = rentService.acceptProposal(proposalId, player.getUniqueId());

        player.sendMessage(Component.text("已接受租借提议! 契约已生效.", NamedTextColor.GREEN));
        player.sendMessage(Component.text("使用 /rent info " + contract.contractId() + " 查看详情", NamedTextColor.GRAY));
    }

    private void handleReject(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /rent reject <提议ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID proposalId;
        try {
            proposalId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("无效的提议ID", NamedTextColor.RED));
            return;
        }

        rentService.rejectProposal(proposalId, player.getUniqueId());
        player.sendMessage(Component.text("已拒绝租借提议", NamedTextColor.YELLOW));
    }

    private void handleTerminate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /rent terminate <契约ID> [原因]", NamedTextColor.YELLOW));
            return;
        }

        UUID contractId;
        try {
            contractId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("无效的契约ID", NamedTextColor.RED));
            return;
        }

        String reason = args.length > 2 ? args[2] : "用户主动终止";

        Optional<LeaseContract> contractOpt = rentService.getContract(contractId);
        if (contractOpt.isEmpty()) {
            player.sendMessage(Component.text("契约不存在", NamedTextColor.RED));
            return;
        }

        LeaseContract contract = contractOpt.get();

        // Check permission (must be lessor or lessee)
        Optional<Nation> playerNation = nationService.nationOf(player.getUniqueId());
        if (playerNation.isEmpty()) {
            player.sendMessage(Component.text("你不在任何国家中", NamedTextColor.RED));
            return;
        }

        NationId playerNationId = playerNation.get().id();
        boolean isLessor = contract.lessorNationId().equals(playerNationId.value());
        boolean isLessee = contract.lesseeNationId() != null &&
                           contract.lesseeNationId().equals(playerNationId.value());

        if (!isLessor && !isLessee) {
            player.sendMessage(Component.text("你无权终止此契约", NamedTextColor.RED));
            return;
        }

        LeaseContract terminated = rentService.terminateContract(contractId, player.getUniqueId(), reason);
        player.sendMessage(Component.text("契约已终止: " + terminated.terminationReason(), NamedTextColor.YELLOW));
    }

    private void handleRenew(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("用法: /rent renew <契约ID> <天数>", NamedTextColor.YELLOW));
            return;
        }

        UUID contractId;
        try {
            contractId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("无效的契约ID", NamedTextColor.RED));
            return;
        }

        int days;
        try {
            days = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的天数", NamedTextColor.RED));
            return;
        }

        Optional<LeaseContract> contractOpt = rentService.getContract(contractId);
        if (contractOpt.isEmpty()) {
            player.sendMessage(Component.text("契约不存在", NamedTextColor.RED));
            return;
        }

        LeaseContract renewed = rentService.renewContract(contractId, player.getUniqueId(), days);
        player.sendMessage(Component.text("契约已续约! 新到期日: " + renewed.endTime(), NamedTextColor.GREEN));
    }

    private void handleProposals(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("rent.error.no-nation"), NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();
        Collection<LeaseProposal> proposals = rentService.getPendingProposals(nation.id());

        if (proposals.isEmpty()) {
            player.sendMessage(Component.text("没有待处理的租借提议", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 待处理提议 ===", NamedTextColor.GOLD));

        for (LeaseProposal proposal : proposals) {
            String proposer = getNationName(proposal.lessorNationId(), nationService);
            String partner = proposal.isNationProposal() ?
                getNationName(proposal.lesseeNationId(), nationService) : "玩家";

            player.sendMessage(Component.text(
                String.format("[%s] %s -> %s (%d区块, %d天)",
                    proposal.proposalId().toString().substring(0, 8),
                    proposer, partner, proposal.chunkCoords().size(), proposal.durationDays()),
                NamedTextColor.GRAY
            ));
        }

        player.sendMessage(Component.text(""));
    }

    private void handleStats(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(messages.format("rent.error.no-nation"), NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();
        TerritoryRentService.LeaseStats stats = rentService.getStats(nation.id());

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 租借统计 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("作为出租方: " + stats.activeContractsAsLessor() + " 个契约", NamedTextColor.GRAY));
        player.sendMessage(Component.text("作为承租方: " + stats.activeContractsAsLessee() + " 个契约", NamedTextColor.GRAY));
        player.sendMessage(Component.text("出租区块: " + stats.chunksLeasedOut(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("承租区块: " + stats.chunksLeasedIn(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("总收入租金: " + stats.totalRentEarned(), NamedTextColor.GREEN));
        player.sendMessage(Component.text("总支出租金: " + stats.totalRentPaid(), NamedTextColor.RED));
        player.sendMessage(Component.text(""));
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 领土租借系统 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/rent create <国家> <天数> [每区块日租] - 创建租借提议", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/rent list - 查看所有契约", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/rent info <契约ID> - 查看契约详情", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/rent accept <提议ID> - 接受租借提议", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/rent reject <提议ID> - 拒绝租借提议", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/rent terminate <契约ID> [原因] - 终止契约", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/rent renew <契约ID> <天数> - 续约", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/rent proposals - 查看待处理提议", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/rent stats - 查看租借统计", NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private String getNationName(UUID nationId, NationService service) {
        return service.nationById(new NationId(nationId))
            .map(Nation::name)
            .orElse("未知");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("create", "list", "info", "accept", "reject", "terminate", "renew", "proposals", "stats", "help");
        }

        if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            switch (subCmd) {
                case "info", "terminate", "renew" -> {
                    Optional<Nation> nationOpt = nationService.nationOf(
                        sender instanceof Player ? ((Player) sender).getUniqueId() : null
                    );
                    if (nationOpt.isPresent()) {
                        return rentService.getNationContracts(nationOpt.get().id()).stream()
                            .map(c -> c.contractId().toString().substring(0, 8))
                            .collect(Collectors.toList());
                    }
                }
                case "accept", "reject" -> {
                    Optional<Nation> nationOpt = nationService.nationOf(
                        sender instanceof Player ? ((Player) sender).getUniqueId() : null
                    );
                    if (nationOpt.isPresent()) {
                        return rentService.getPendingProposals(nationOpt.get().id()).stream()
                            .map(p -> p.proposalId().toString().substring(0, 8))
                            .collect(Collectors.toList());
                    }
                }
            }
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return nationService.nations().stream()
                .map(Nation::name)
                .collect(Collectors.toList());
        }

        return List.of();
    }
}
