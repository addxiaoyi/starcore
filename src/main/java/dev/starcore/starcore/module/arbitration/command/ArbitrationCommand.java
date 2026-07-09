package dev.starcore.starcore.module.arbitration.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.module.arbitration.ArbitrationService;
import dev.starcore.starcore.module.arbitration.model.ArbitrationCase;
import dev.starcore.starcore.module.arbitration.model.ArbitrationCaseType;
import dev.starcore.starcore.module.arbitration.model.ArbitrationResult;
import dev.starcore.starcore.module.arbitration.model.ArbitrationStatus;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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
 * 领土仲裁命令处理器
 * /arb <子命令> [参数]
 */
public final class ArbitrationCommand implements CommandExecutor, TabCompleter {
    private final ArbitrationService arbitrationService;
    private final NationService nationService;
    private final MessageService messages;
    private final OnlinePlayerDirectory onlinePlayerDirectory;

    public ArbitrationCommand(
        ArbitrationService arbitrationService,
        NationService nationService,
        MessageService messages,
        OnlinePlayerDirectory onlinePlayerDirectory
    ) {
        this.arbitrationService = arbitrationService;
        this.nationService = nationService;
        this.messages = messages;
        this.onlinePlayerDirectory = onlinePlayerDirectory;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "submit", "file" -> handleSubmit(sender, args);
                case "list", "ls" -> handleList(sender, args);
                case "info", "i" -> handleInfo(sender, args);
                case "accept" -> handleAccept(sender, args);
                case "defense", "respond" -> handleDefense(sender, args);
                case "evidence", "ev" -> handleEvidence(sender, args);
                case "rule", "ruling" -> handleRuling(sender, args);
                case "withdraw", "cancel" -> handleWithdraw(sender, args);
                case "pending", "p" -> handlePending(sender);
                case "help", "h" -> showHelp(sender);
                default -> sender.sendMessage(Component.text("Unknown command: " + subCommand, NamedTextColor.RED));
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
        } catch (IllegalStateException e) {
            sender.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    /**
     * 提交仲裁申请
     * /arb submit <被告国家> <案件类型> [费用]
     */
    private void handleSubmit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players", NamedTextColor.RED));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /arb submit <respondent> <caseType> [fee]", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Case types: boundary, invasion, ownership, resource, military, diplomatic, trade, other", NamedTextColor.GRAY));
            return;
        }

        // 获取玩家国家
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            sender.sendMessage(Component.text("You must be in a nation to file an arbitration case", NamedTextColor.RED));
            return;
        }
        Nation claimantNation = nationOpt.get();
        NationId claimantId = claimantNation.id();

        // 解析被告国家
        String respondentName = args[1];
        Optional<Nation> respondentOpt = nationService.nationByName(respondentName);
        if (respondentOpt.isEmpty()) {
            sender.sendMessage(Component.text("Nation not found: " + respondentName, NamedTextColor.RED));
            return;
        }
        NationId respondentId = respondentOpt.get().id();

        // 解析案件类型
        ArbitrationCaseType caseType = parseCaseType(args[2]);
        if (caseType == null) {
            sender.sendMessage(Component.text("Invalid case type: " + args[2], NamedTextColor.RED));
            sender.sendMessage(Component.text("Valid types: boundary, invasion, ownership, resource, military, diplomatic, trade, other", NamedTextColor.YELLOW));
            return;
        }

        // 解析费用（可选）
        BigDecimal claimFee = arbitrationService.getMinimumClaimFee();
        if (args.length >= 4) {
            try {
                claimFee = new BigDecimal(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid fee: " + args[3], NamedTextColor.RED));
                return;
            }
        }

        // 提交案件
        try {
            ArbitrationCase arbitrationCase = arbitrationService.submitCase(
                claimantId,
                respondentId,
                caseType,
                List.of(), // 可以在后续通过选择工具添加
                "Filed by " + player.getName(),
                claimFee
            );

            sender.sendMessage(Component.text(""));
            sender.sendMessage(Component.text("Arbitration case submitted successfully!", NamedTextColor.GREEN));
            sender.sendMessage(Component.text("Case ID: " + arbitrationCase.id(), NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Respondent: " + respondentOpt.get().name(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("Type: " + caseType.displayName(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("Fee: " + claimFee, NamedTextColor.GRAY));
            sender.sendMessage(Component.text(""));
        } catch (IllegalStateException e) {
            sender.sendMessage(Component.text("Failed to submit case: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    /**
     * 列出国家的仲裁案件
     */
    private void handleList(CommandSender sender, String[] args) {
        NationId nationId = null;

        if (sender instanceof Player player) {
            Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isPresent()) {
                nationId = nationOpt.get().id();
            }
        }

        if (nationId == null) {
            sender.sendMessage(Component.text("You must be in a nation to view cases", NamedTextColor.RED));
            return;
        }

        Collection<ArbitrationCase> cases = arbitrationService.getCasesForNation(nationId);

        if (cases.isEmpty()) {
            sender.sendMessage(Component.text("No arbitration cases found for your nation", NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("=== Arbitration Cases ===", NamedTextColor.GOLD));
        for (ArbitrationCase arbitrationCase : cases) {
            String statusColor = arbitrationCase.status().isTerminal() ? "GRAY" : "YELLOW";
            sender.sendMessage(Component.text(
                String.format("[%s] %s - %s (%s)",
                    arbitrationCase.id().toString().substring(0, 8),
                    arbitrationCase.caseType().displayName(),
                    arbitrationCase.status().displayName(),
                    arbitrationCase.isClaimant(nationId) ? "Claimant" : "Respondent"
                ),
                NamedTextColor.NAMES.value(statusColor)
            ));
        }
        sender.sendMessage(Component.text(""));
    }

    /**
     * 查看案件详情
     */
    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /arb info <caseId>", NamedTextColor.YELLOW));
            return;
        }

        UUID caseId = parseCaseId(args[1]);
        Optional<ArbitrationCase> caseOpt = arbitrationService.getCase(caseId);

        if (caseOpt.isEmpty()) {
            sender.sendMessage(Component.text("Case not found: " + args[1], NamedTextColor.RED));
            return;
        }

        ArbitrationCase arbitrationCase = caseOpt.get();

        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("=== Case Details ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("ID: " + arbitrationCase.id(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Type: " + arbitrationCase.caseType().displayName(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Status: " + arbitrationCase.status().displayName(), NamedTextColor.YELLOW));

        Optional<Nation> claimantNation = nationService.nationById(arbitrationCase.claimant());
        Optional<Nation> respondentNation = nationService.nationById(arbitrationCase.respondent());
        sender.sendMessage(Component.text("Claimant: " + claimantNation.map(Nation::name).orElse("Unknown"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Respondent: " + respondentNation.map(Nation::name).orElse("Unknown"), NamedTextColor.GRAY));

        if (arbitrationCase.arbitrator() != null) {
            String arbitratorName = onlinePlayerDirectory.findOnlinePlayer(arbitrationCase.arbitrator())
                .map(Player::getName)
                .orElse(arbitrationCase.arbitrator().toString().substring(0, 8));
            sender.sendMessage(Component.text("Arbitrator: " + arbitratorName, NamedTextColor.GRAY));
        }

        sender.sendMessage(Component.text("Disputed Chunks: " + arbitrationCase.disputedChunkCount(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Evidence Count: " + arbitrationCase.totalEvidenceCount(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Filing Fee: " + arbitrationCase.claimFee(), NamedTextColor.GRAY));

        if (arbitrationCase.result() != null) {
            sender.sendMessage(Component.text("Result: " + arbitrationCase.result().displayName(), NamedTextColor.GREEN));
            if (arbitrationCase.ruling() != null) {
                sender.sendMessage(Component.text("Ruling: " + arbitrationCase.ruling(), NamedTextColor.GRAY));
            }
        }

        sender.sendMessage(Component.text(""));
    }

    /**
     * 接受仲裁案件（仲裁员）
     */
    private void handleAccept(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /arb accept <caseId>", NamedTextColor.YELLOW));
            return;
        }

        UUID caseId = parseCaseId(args[1]);

        if (arbitrationService.acceptCase(caseId, player.getUniqueId())) {
            sender.sendMessage(Component.text("Case accepted successfully!", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Failed to accept case. It may already be assigned or not found.", NamedTextColor.RED));
        }
    }

    /**
     * 提交答辩（被告）
     */
    private void handleDefense(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players", NamedTextColor.RED));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /arb defense <caseId> <defense text>", NamedTextColor.YELLOW));
            return;
        }

        UUID caseId = parseCaseId(args[1]);
        String defense = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            sender.sendMessage(Component.text("You must be in a nation", NamedTextColor.RED));
            return;
        }

        if (arbitrationService.submitDefense(caseId, nationOpt.get().id(), defense)) {
            sender.sendMessage(Component.text("Defense submitted successfully!", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Failed to submit defense. You may not be the respondent or defense already submitted.", NamedTextColor.RED));
        }
    }

    /**
     * 添加证据
     */
    private void handleEvidence(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players", NamedTextColor.RED));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /arb evidence <caseId> <evidence text>", NamedTextColor.YELLOW));
            return;
        }

        UUID caseId = parseCaseId(args[1]);
        String evidence = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            sender.sendMessage(Component.text("You must be in a nation", NamedTextColor.RED));
            return;
        }

        if (arbitrationService.addEvidence(caseId, nationOpt.get().id(), evidence)) {
            sender.sendMessage(Component.text("Evidence added successfully!", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Failed to add evidence. You must be a party to this case.", NamedTextColor.RED));
        }
    }

    /**
     * 做出裁决（仲裁员）
     */
    private void handleRuling(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players", NamedTextColor.RED));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /arb rule <caseId> <result> [ruling text]", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Results: claimant, respondent, split, neutral, partial, settled, invalid, dismissed", NamedTextColor.GRAY));
            return;
        }

        UUID caseId = parseCaseId(args[1]);
        ArbitrationResult result = parseResult(args[2]);

        if (result == null) {
            sender.sendMessage(Component.text("Invalid result: " + args[2], NamedTextColor.RED));
            sender.sendMessage(Component.text("Valid results: claimant, respondent, split, neutral, partial, settled, invalid, dismissed", NamedTextColor.YELLOW));
            return;
        }

        String ruling = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "";

        if (arbitrationService.makeRuling(caseId, player.getUniqueId(), result, ruling)) {
            sender.sendMessage(Component.text("Ruling made successfully!", NamedTextColor.GREEN));
            sender.sendMessage(Component.text("Result: " + result.displayName(), NamedTextColor.GOLD));
        } else {
            sender.sendMessage(Component.text("Failed to make ruling. You may not be the assigned arbitrator.", NamedTextColor.RED));
        }
    }

    /**
     * 撤诉
     */
    private void handleWithdraw(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /arb withdraw <caseId>", NamedTextColor.YELLOW));
            return;
        }

        UUID caseId = parseCaseId(args[1]);

        if (arbitrationService.withdrawCase(caseId)) {
            sender.sendMessage(Component.text("Case withdrawn successfully!", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Failed to withdraw case.", NamedTextColor.RED));
        }
    }

    /**
     * 查看待处理案件
     */
    private void handlePending(CommandSender sender) {
        Collection<ArbitrationCase> pendingCases = arbitrationService.getPendingCases();

        if (pendingCases.isEmpty()) {
            sender.sendMessage(Component.text("No pending cases", NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("=== Pending Cases ===", NamedTextColor.GOLD));
        for (ArbitrationCase arbitrationCase : pendingCases) {
            Optional<Nation> claimant = nationService.nationById(arbitrationCase.claimant());
            Optional<Nation> respondent = nationService.nationById(arbitrationCase.respondent());
            sender.sendMessage(Component.text(
                String.format("[%s] %s vs %s - %s (%s)",
                    arbitrationCase.id().toString().substring(0, 8),
                    claimant.map(Nation::name).orElse("Unknown"),
                    respondent.map(Nation::name).orElse("Unknown"),
                    arbitrationCase.caseType().displayName(),
                    arbitrationCase.disputedChunkCount() + " chunks"
                ),
                NamedTextColor.GRAY
            ));
        }
        sender.sendMessage(Component.text(""));
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("=== Territory Arbitration Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/arb submit <respondent> <type> [fee] - Submit a case", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/arb list - List your nation's cases", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/arb info <caseId> - View case details", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/arb accept <caseId> - Accept a case (arbitrator)", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/arb defense <caseId> <text> - Submit defense", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/arb evidence <caseId> <text> - Add evidence", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/arb rule <caseId> <result> [text] - Make ruling", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/arb withdraw <caseId> - Withdraw your case", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/arb pending - View pending cases", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("Case Types: boundary, invasion, ownership, resource, military, diplomatic, trade, other", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("Results: claimant, respondent, split, neutral, partial, settled, invalid, dismissed", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text(""));
    }

    // ==================== Helper Methods ====================

    private UUID parseCaseId(String idStr) {
        if (idStr.length() == 36) {
            return UUID.fromString(idStr);
        }
        // 尝试从部分ID查找
        for (ArbitrationCase arbitrationCase : arbitrationService.getPendingCases()) {
            if (arbitrationCase.id().toString().startsWith(idStr)) {
                return arbitrationCase.id();
            }
        }
        throw new IllegalArgumentException("Invalid case ID: " + idStr);
    }

    private @Nullable ArbitrationCaseType parseCaseType(String typeStr) {
        return switch (typeStr.toLowerCase()) {
            case "boundary", "边界" -> ArbitrationCaseType.TERRITORY_BOUNDARY;
            case "invasion", "侵占", "入侵" -> ArbitrationCaseType.TERRITORY_INVASION;
            case "ownership", "所有权" -> ArbitrationCaseType.TERRITORY_OWNERSHIP;
            case "resource", "资源" -> ArbitrationCaseType.RESOURCE_RIGHTS;
            case "military", "军事" -> ArbitrationCaseType.MILITARY_ACTIVITY;
            case "diplomatic", "外交" -> ArbitrationCaseType.DIPLOMATIC_BREACH;
            case "trade", "贸易" -> ArbitrationCaseType.TRADE_DISPUTE;
            case "other", "其他" -> ArbitrationCaseType.OTHER;
            default -> null;
        };
    }

    private @Nullable ArbitrationResult parseResult(String resultStr) {
        return switch (resultStr.toLowerCase()) {
            case "claimant", "申诉方" -> ArbitrationResult.CLAIMANT_FAVOR;
            case "respondent", "被申诉方" -> ArbitrationResult.RESPONDENT_FAVOR;
            case "split", "分割" -> ArbitrationResult.SPLIT_DECISION;
            case "neutral", "中立" -> ArbitrationResult.NEUTRAL;
            case "partial", "部分" -> ArbitrationResult.PARTIAL;
            case "settled", "和解" -> ArbitrationResult.SETTLED;
            case "invalid", "无效" -> ArbitrationResult.INVALID;
            case "dismissed", "驳回" -> ArbitrationResult.DISMISSED;
            default -> null;
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("submit", "list", "info", "accept", "defense", "evidence", "rule", "withdraw", "pending", "help");
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "info", "accept", "defense", "evidence", "rule", "withdraw" -> arbitrationService.getPendingCases().stream()
                    .map(c -> c.id().toString().substring(0, 8))
                    .collect(Collectors.toList());
                case "submit" -> nationService.nations().stream()
                    .map(Nation::name)
                    .collect(Collectors.toList());
                default -> List.of();
            };
        }

        if (args.length == 3) {
            return switch (args[0].toLowerCase()) {
                case "submit" -> List.of("boundary", "invasion", "ownership", "resource", "military", "diplomatic", "trade", "other");
                case "rule" -> List.of("claimant", "respondent", "split", "neutral", "partial", "settled", "invalid", "dismissed");
                default -> List.of();
            };
        }

        return List.of();
    }
}
