package dev.starcore.starcore.module.war.reparations.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.module.war.reparations.ReparationsService;
import dev.starcore.starcore.war.WarReparation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 战争赔款命令处理器
 * /reparations <create|pay|info|list|forgive|default|status> [args]
 */
public final class ReparationsCommand implements CommandExecutor, TabCompleter {
    private static final String PERMISSION_USE = "starcore.reparations.use";
    private static final String PERMISSION_ADMIN = "starcore.reparations.admin";
    private static final String PERMISSION_CREATE = "starcore.reparations.create";
    private static final String PERMISSION_FORGIVE = "starcore.reparations.forgive";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault());

    private final ReparationsService reparationsService;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final MessageService messages;

    public ReparationsCommand(
        ReparationsService reparationsService,
        NationService nationService,
        TreasuryService treasuryService,
        MessageService messages
    ) {
        this.reparationsService = reparationsService;
        this.nationService = nationService;
        this.treasuryService = treasuryService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "create" -> handleCreate(sender, args);
                case "pay" -> handlePay(sender, args);
                case "info", "i" -> handleInfo(sender, args);
                case "list", "ls" -> handleList(sender, args);
                case "status", "s" -> handleStatus(sender, args);
                case "forgive" -> handleForgive(sender, args);
                case "default" -> handleDefault(sender, args);
                case "my", "mine" -> {
                    if (sender instanceof Player player) {
                        handleMyReparations(player);
                    } else {
                        sender.sendMessage(Component.text("This command must be used by a player!", NamedTextColor.RED));
                    }
                }
                default -> showHelp(sender);
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        } catch (IllegalStateException e) {
            sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        } catch (Exception e) {
            sender.sendMessage(Component.text("An error occurred: " + e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    /**
     * 创建赔款记录
     * /reparations create <treatyId> <payerNation> <receiverNation> <amount> [installments]
     */
    private void handleCreate(CommandSender sender, String[] args) {
        // 检查权限
        if (!sender.hasPermission(PERMISSION_CREATE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            sender.sendMessage(Component.text("You don't have permission to create reparations!", NamedTextColor.RED));
            return;
        }

        if (args.length < 5) {
            sender.sendMessage(Component.text("Usage: /reparations create <treatyId> <payerNation> <receiverNation> <amount> [installments]", NamedTextColor.YELLOW));
            return;
        }

        String treatyIdStr = args[1];
        String payerName = args[2];
        String receiverName = args[3];
        String amountStr = args[4];
        int installments = args.length > 5 ? Integer.parseInt(args[5]) : 12;

        // 验证条约ID
        UUID treatyId;
        try {
            treatyId = UUID.fromString(treatyIdStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Invalid treaty ID format!", NamedTextColor.RED));
            return;
        }

        // 查找国家
        Optional<Nation> payerOpt = nationService.nationByName(payerName);
        Optional<Nation> receiverOpt = nationService.nationByName(receiverName);

        if (payerOpt.isEmpty()) {
            sender.sendMessage(Component.text("Payer nation not found: " + payerName, NamedTextColor.RED));
            return;
        }

        if (receiverOpt.isEmpty()) {
            sender.sendMessage(Component.text("Receiver nation not found: " + receiverName, NamedTextColor.RED));
            return;
        }

        Nation payer = payerOpt.get();
        Nation receiver = receiverOpt.get();

        // 验证金额
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid amount format!", NamedTextColor.RED));
            return;
        }

        // 创建赔款
        try {
            WarReparation reparation = reparationsService.createReparation(
                treatyId, payer.id(), receiver.id(), amount, installments);

            sender.sendMessage(Component.text("Reparation created successfully!", NamedTextColor.GREEN));
            sender.sendMessage(Component.text("Reparation ID: " + reparation.id(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("Payer: " + payer.name(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("Receiver: " + receiver.name(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("Amount: " + amount + " (in " + installments + " installments)", NamedTextColor.GRAY));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Failed to create reparation: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    /**
     * 支付赔款
     * /reparations pay <reparationId> [amount]
     */
    private void handlePay(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_USE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            sender.sendMessage(Component.text("You don't have permission to pay reparations!", NamedTextColor.RED));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command must be used by a player!", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /reparations pay <reparationId> [amount]", NamedTextColor.YELLOW));
            return;
        }

        // 获取玩家国家
        Optional<Nation> playerNationOpt = nationService.nationOf(player.getUniqueId());
        if (playerNationOpt.isEmpty()) {
            sender.sendMessage(Component.text("You are not a member of any nation!", NamedTextColor.RED));
            return;
        }

        Nation playerNation = playerNationOpt.get();

        // 解析赔款ID
        UUID reparationId;
        try {
            reparationId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Invalid reparation ID format!", NamedTextColor.RED));
            return;
        }

        // 获取赔款记录
        Optional<WarReparation> reparationOpt = reparationsService.getReparation(reparationId);
        if (reparationOpt.isEmpty()) {
            sender.sendMessage(Component.text("Reparation not found!", NamedTextColor.RED));
            return;
        }

        WarReparation reparation = reparationOpt.get();

        // 检查是否是支付方
        if (!reparation.payerId().equals(playerNation.id().value())) {
            sender.sendMessage(Component.text("Your nation is not the payer of this reparation!", NamedTextColor.RED));
            return;
        }

        // 检查状态
        if (reparation.status() != WarReparation.ReparationStatus.ACTIVE) {
            sender.sendMessage(Component.text("This reparation is not active!", NamedTextColor.RED));
            return;
        }

        // 确定支付金额
        BigDecimal amount;
        if (args.length > 2) {
            try {
                amount = new BigDecimal(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid amount format!", NamedTextColor.RED));
                return;
            }
        } else {
            // 默认支付分期金额
            amount = reparation.installmentAmount();
        }

        // 检查国库余额
        BigDecimal balance = treasuryService.balance(playerNation.id());
        if (balance.compareTo(amount) < 0) {
            sender.sendMessage(Component.text("Insufficient treasury balance! Your balance: " + balance, NamedTextColor.RED));
            return;
        }

        // 执行支付
        boolean success = reparationsService.payReparation(reparationId, amount);

        if (success) {
            sender.sendMessage(Component.text("Payment successful!", NamedTextColor.GREEN));
            sender.sendMessage(Component.text("Amount paid: " + amount, NamedTextColor.GRAY));
            sender.sendMessage(Component.text("Progress: " + String.format("%.1f%%", reparation.progressPercentage()), NamedTextColor.GRAY));

            if (reparation.isCompleted()) {
                sender.sendMessage(Component.text("Reparation fully paid!", NamedTextColor.GOLD));
            }
        } else {
            sender.sendMessage(Component.text("Payment failed!", NamedTextColor.RED));
        }
    }

    /**
     * 查看赔款详情
     * /reparations info <reparationId>
     */
    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /reparations info <reparationId>", NamedTextColor.YELLOW));
            return;
        }

        UUID reparationId;
        try {
            reparationId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Invalid reparation ID format!", NamedTextColor.RED));
            return;
        }

        Optional<WarReparation> reparationOpt = reparationsService.getReparation(reparationId);
        if (reparationOpt.isEmpty()) {
            sender.sendMessage(Component.text("Reparation not found!", NamedTextColor.RED));
            return;
        }

        WarReparation reparation = reparationOpt.get();
        NationId payerId = NationId.of(reparation.payerId());
        NationId receiverId = NationId.of(reparation.receiverId());

        String payerName = nationService.nationById(payerId).map(Nation::name).orElse("Unknown");
        String receiverName = nationService.nationById(receiverId).map(Nation::name).orElse("Unknown");

        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("=== Reparation Details ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("ID: " + reparation.id(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Status: " + reparation.status().displayName(), getStatusColor(reparation.status())));
        sender.sendMessage(Component.text("Payer: " + payerName, NamedTextColor.RED));
        sender.sendMessage(Component.text("Receiver: " + receiverName, NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Total Amount: " + reparation.totalAmount(), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Paid: " + reparation.paidAmount(), NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Remaining: " + reparation.remainingAmount(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Progress: " + String.format("%.1f%%", reparation.progressPercentage()), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Installments: " + reparation.paidInstallments() + "/" + reparation.totalInstallments(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Installment Amount: " + reparation.installmentAmount(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Start Date: " + DATE_FORMATTER.format(reparation.startDate()), NamedTextColor.GRAY));
        if (reparation.lastPaymentDate() != null) {
            sender.sendMessage(Component.text("Last Payment: " + DATE_FORMATTER.format(reparation.lastPaymentDate()), NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text(""));
    }

    /**
     * 列出所有赔款
     * /reparations list [page]
     */
    private void handleList(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                // 忽略
            }
        }

        Collection<WarReparation> allReparations = reparationsService.getAllReparations();
        List<WarReparation> reparationsList = new ArrayList<>(allReparations);

        int itemsPerPage = 10;
        int totalPages = (int) Math.ceil((double) reparationsList.size() / itemsPerPage);
        page = Math.max(1, Math.min(page, Math.max(1, totalPages)));

        int start = (page - 1) * itemsPerPage;
        int end = Math.min(start + itemsPerPage, reparationsList.size());

        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("=== Reparations List (Page " + page + "/" + Math.max(1, totalPages) + ") ===", NamedTextColor.GOLD));

        if (reparationsList.isEmpty()) {
            sender.sendMessage(Component.text("No reparations found.", NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("Total: " + reparationsList.size() + " reparations", NamedTextColor.GRAY));
            sender.sendMessage(Component.text(""));

            for (int i = start; i < end; i++) {
                WarReparation reparation = reparationsList.get(i);
                String payerName = nationService.nationById(NationId.of(reparation.payerId()))
                    .map(Nation::name).orElse("Unknown");
                String receiverName = nationService.nationById(NationId.of(reparation.receiverId()))
                    .map(Nation::name).orElse("Unknown");

                Component item = Component.text()
                    .append(Component.text("[" + reparation.id().toString().substring(0, 8) + "] ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(payerName, NamedTextColor.RED))
                    .append(Component.text(" -> ", NamedTextColor.GRAY))
                    .append(Component.text(receiverName, NamedTextColor.GREEN))
                    .append(Component.text(" ", NamedTextColor.GRAY))
                    .append(Component.text(reparation.totalAmount().toPlainString(), NamedTextColor.AQUA))
                    .append(Component.text(" (", NamedTextColor.GRAY))
                    .append(Component.text(reparation.status().displayName(), getStatusColor(reparation.status())))
                    .append(Component.text(")", NamedTextColor.GRAY))
                    .build();

                sender.sendMessage(item);
            }
        }

        sender.sendMessage(Component.text(""));
        if (totalPages > 1) {
            sender.sendMessage(Component.text("Use /reparations list " + (page + 1) + " for more.", NamedTextColor.GRAY));
        }
    }

    /**
     * 查看国家的赔款状态
     * /reparations status [nation]
     */
    private void handleStatus(CommandSender sender, String[] args) {
        NationId nationId;

        if (args.length > 1) {
            // 指定国家
            Optional<Nation> nationOpt = nationService.nationByName(args[1]);
            if (nationOpt.isEmpty()) {
                sender.sendMessage(Component.text("Nation not found: " + args[1], NamedTextColor.RED));
                return;
            }
            nationId = nationOpt.get().id();
        } else if (sender instanceof Player player) {
            // 使用玩家所在国家
            Optional<Nation> playerNationOpt = nationService.nationOf(player.getUniqueId());
            if (playerNationOpt.isEmpty()) {
                sender.sendMessage(Component.text("You are not a member of any nation!", NamedTextColor.RED));
                return;
            }
            nationId = playerNationOpt.get().id();
        } else {
            sender.sendMessage(Component.text("You must specify a nation when using from console!", NamedTextColor.RED));
            return;
        }

        String nationName = nationService.nationById(nationId).map(Nation::name).orElse("Unknown");

        List<WarReparation> asPayer = reparationsService.getReparationsAsPayer(nationId);
        List<WarReparation> asReceiver = reparationsService.getReparationsAsReceiver(nationId);

        BigDecimal totalDebt = reparationsService.calculateTotalReparationDebt(nationId);
        BigDecimal totalPaid = reparationsService.calculateTotalReparationPaid(nationId);

        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("=== Reparation Status: " + nationName + " ===", NamedTextColor.GOLD));

        // 作为支付方
        sender.sendMessage(Component.text("As Payer:", NamedTextColor.RED));
        long activePayer = asPayer.stream().filter(r -> r.status() == WarReparation.ReparationStatus.ACTIVE).count();
        sender.sendMessage(Component.text("  Active: " + activePayer, NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  Total Debt: " + totalDebt, NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  Total Paid: " + totalPaid, NamedTextColor.GREEN));

        if (asPayer.stream().anyMatch(r -> r.status() == WarReparation.ReparationStatus.ACTIVE)) {
            sender.sendMessage(Component.text("  Active Reparations:", NamedTextColor.GRAY));
            for (WarReparation r : asPayer.stream().filter(re -> re.status() == WarReparation.ReparationStatus.ACTIVE).limit(5).toList()) {
                String receiverName = nationService.nationById(NationId.of(r.receiverId()))
                    .map(Nation::name).orElse("Unknown");
                sender.sendMessage(Component.text()
                    .append(Component.text("    - " + receiverName + ": ", NamedTextColor.RED))
                    .append(Component.text(r.remainingAmount().toPlainString(), NamedTextColor.YELLOW))
                    .append(Component.text(" remaining (", NamedTextColor.GRAY))
                    .append(Component.text(String.format("%.1f%%", r.progressPercentage()), NamedTextColor.GREEN))
                    .append(Component.text(")", NamedTextColor.GRAY))
                    .build());
            }
        }

        // 作为接收方
        sender.sendMessage(Component.text("As Receiver:", NamedTextColor.GREEN));
        long activeReceiver = asReceiver.stream().filter(r -> r.status() == WarReparation.ReparationStatus.ACTIVE).count();
        sender.sendMessage(Component.text("  Active: " + activeReceiver, NamedTextColor.GRAY));

        BigDecimal totalReceivable = asReceiver.stream()
            .filter(r -> r.status() == WarReparation.ReparationStatus.ACTIVE)
            .map(WarReparation::remainingAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        sender.sendMessage(Component.text("  Total Receivable: " + totalReceivable, NamedTextColor.AQUA));

        sender.sendMessage(Component.text(""));
    }

    /**
     * 查看自己的赔款
     */
    private void handleMyReparations(Player player) {
        Optional<Nation> playerNationOpt = nationService.nationOf(player.getUniqueId());
        if (playerNationOpt.isEmpty()) {
            player.sendMessage(Component.text("You are not a member of any nation!", NamedTextColor.RED));
            return;
        }

        Nation playerNation = playerNationOpt.get();
        List<WarReparation> asPayer = reparationsService.getReparationsAsPayer(playerNation.id());
        List<WarReparation> asReceiver = reparationsService.getReparationsAsReceiver(playerNation.id());

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== My Nation's Reparations ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Your Nation: " + playerNation.name(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text(""));

        // 作为支付方
        List<WarReparation> activeAsPayer = asPayer.stream()
            .filter(r -> r.status() == WarReparation.ReparationStatus.ACTIVE)
            .toList();

        player.sendMessage(Component.text("You Owe (" + activeAsPayer.size() + "):", NamedTextColor.RED));
        if (activeAsPayer.isEmpty()) {
            player.sendMessage(Component.text("  No active reparations.", NamedTextColor.GRAY));
        } else {
            for (WarReparation r : activeAsPayer) {
                String receiverName = nationService.nationById(NationId.of(r.receiverId()))
                    .map(Nation::name).orElse("Unknown");
                BigDecimal nextPayment = r.installmentAmount();
                Instant nextDue = r.lastPaymentDate() != null
                    ? r.lastPaymentDate().plusSeconds(30L * 24 * 60 * 60) // 假设30天一期
                    : r.startDate().plusSeconds(30L * 24 * 60 * 60);

                player.sendMessage(Component.text()
                    .append(Component.text("  -> " + receiverName + ": ", NamedTextColor.RED))
                    .append(Component.text(r.remainingAmount().toPlainString(), NamedTextColor.YELLOW))
                    .append(Component.text(" remaining", NamedTextColor.GRAY))
                    .build());
                player.sendMessage(Component.text()
                    .append(Component.text("    Next payment: " + nextPayment + " (due: " + DATE_FORMATTER.format(nextDue) + ")", NamedTextColor.GRAY))
                    .build());
            }
        }

        player.sendMessage(Component.text(""));

        // 作为接收方
        List<WarReparation> activeAsReceiver = asReceiver.stream()
            .filter(r -> r.status() == WarReparation.ReparationStatus.ACTIVE)
            .toList();

        player.sendMessage(Component.text("Owed to You (" + activeAsReceiver.size() + "):", NamedTextColor.GREEN));
        if (activeAsReceiver.isEmpty()) {
            player.sendMessage(Component.text("  No pending reparations owed to you.", NamedTextColor.GRAY));
        } else {
            for (WarReparation r : activeAsReceiver) {
                String payerName = nationService.nationById(NationId.of(r.payerId()))
                    .map(Nation::name).orElse("Unknown");

                player.sendMessage(Component.text()
                    .append(Component.text("  <- " + payerName + ": ", NamedTextColor.GREEN))
                    .append(Component.text(r.remainingAmount().toPlainString(), NamedTextColor.AQUA))
                    .append(Component.text(" remaining", NamedTextColor.GRAY))
                    .build());
            }
        }

        player.sendMessage(Component.text(""));
    }

    /**
     * 免除赔款
     * /reparations forgive <reparationId>
     */
    private void handleForgive(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_FORGIVE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            sender.sendMessage(Component.text("You don't have permission to forgive reparations!", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /reparations forgive <reparationId>", NamedTextColor.YELLOW));
            return;
        }

        UUID reparationId;
        try {
            reparationId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Invalid reparation ID format!", NamedTextColor.RED));
            return;
        }

        Optional<WarReparation> reparationOpt = reparationsService.getReparation(reparationId);
        if (reparationOpt.isEmpty()) {
            sender.sendMessage(Component.text("Reparation not found!", NamedTextColor.RED));
            return;
        }

        WarReparation reparation = reparationOpt.get();

        if (reparation.status() == WarReparation.ReparationStatus.COMPLETED ||
            reparation.status() == WarReparation.ReparationStatus.FORGIVEN) {
            sender.sendMessage(Component.text("This reparation is already completed or forgiven!", NamedTextColor.RED));
            return;
        }

        boolean success = reparationsService.forgiveReparation(reparationId);

        if (success) {
            sender.sendMessage(Component.text("Reparation forgiven!", NamedTextColor.GREEN));

            String payerName = nationService.nationById(NationId.of(reparation.payerId()))
                .map(Nation::name).orElse("Unknown");
            String receiverName = nationService.nationById(NationId.of(reparation.receiverId()))
                .map(Nation::name).orElse("Unknown");

            sender.sendMessage(Component.text("Payer: " + payerName, NamedTextColor.RED));
            sender.sendMessage(Component.text("Receiver: " + receiverName, NamedTextColor.GREEN));
            sender.sendMessage(Component.text("Amount: " + reparation.remainingAmount(), NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Failed to forgive reparation!", NamedTextColor.RED));
        }
    }

    /**
     * 标记赔款违约
     * /reparations default <reparationId>
     */
    private void handleDefault(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            sender.sendMessage(Component.text("You don't have permission to mark reparations as defaulted!", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /reparations default <reparationId>", NamedTextColor.YELLOW));
            return;
        }

        UUID reparationId;
        try {
            reparationId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Invalid reparation ID format!", NamedTextColor.RED));
            return;
        }

        Optional<WarReparation> reparationOpt = reparationsService.getReparation(reparationId);
        if (reparationOpt.isEmpty()) {
            sender.sendMessage(Component.text("Reparation not found!", NamedTextColor.RED));
            return;
        }

        WarReparation reparation = reparationOpt.get();

        if (reparation.status() != WarReparation.ReparationStatus.ACTIVE) {
            sender.sendMessage(Component.text("This reparation is not active!", NamedTextColor.RED));
            return;
        }

        boolean success = reparationsService.markDefault(reparationId);

        if (success) {
            sender.sendMessage(Component.text("Reparation marked as defaulted!", NamedTextColor.RED));

            String payerName = nationService.nationById(NationId.of(reparation.payerId()))
                .map(Nation::name).orElse("Unknown");
            sender.sendMessage(Component.text("Payer: " + payerName, NamedTextColor.RED));
            sender.sendMessage(Component.text("Outstanding Amount: " + reparation.remainingAmount(), NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Failed to mark reparation as defaulted!", NamedTextColor.RED));
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("=== Reparations Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/reparations create <treatyId> <payer> <receiver> <amount> [installments] - Create reparation", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/reparations pay <reparationId> [amount] - Pay reparation", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/reparations info <reparationId> - Show reparation details", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/reparations list [page] - List all reparations", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/reparations status [nation] - Show nation's reparation status", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/reparations my - Show your nation's reparations", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(""));
    }

    private TextColor getStatusColor(WarReparation.ReparationStatus status) {
        return switch (status) {
            case ACTIVE -> NamedTextColor.YELLOW;
            case COMPLETED -> NamedTextColor.GREEN;
            case DEFAULTED -> NamedTextColor.RED;
            case FORGIVEN -> NamedTextColor.GRAY;
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            subCommands.add("create");
            subCommands.add("pay");
            subCommands.add("info");
            subCommands.add("list");
            subCommands.add("status");
            subCommands.add("my");
            if (sender.hasPermission(PERMISSION_FORGIVE) || sender.hasPermission(PERMISSION_ADMIN)) {
                subCommands.add("forgive");
            }
            if (sender.hasPermission(PERMISSION_ADMIN)) {
                subCommands.add("default");
            }
            return subCommands;
        }

        if (args.length == 2) {
            String input = args[1].toLowerCase();
            return switch (args[0].toLowerCase()) {
                case "info", "pay", "forgive", "default" -> {
                    // 赔款ID补全
                    Collection<WarReparation> reparations = reparationsService.getAllReparations();
                    yield reparations.stream()
                        .map(r -> r.id().toString())
                        .filter(id -> id.toLowerCase().startsWith(input))
                        .limit(10)
                        .collect(Collectors.toList());
                }
                case "status" -> nationService.nations().stream()
                    .map(Nation::name)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .limit(10)
                    .collect(Collectors.toList());
                default -> List.of();
            };
        }

        if (args.length == 3) {
            if ("create".equalsIgnoreCase(args[0])) {
                return nationService.nations().stream()
                    .map(Nation::name)
                    .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                    .limit(10)
                    .collect(Collectors.toList());
            }
        }

        if (args.length == 4) {
            if ("create".equalsIgnoreCase(args[0])) {
                return nationService.nations().stream()
                    .map(Nation::name)
                    .filter(name -> name.toLowerCase().startsWith(args[3].toLowerCase()))
                    .limit(10)
                    .collect(Collectors.toList());
            }
        }

        return List.of();
    }
}