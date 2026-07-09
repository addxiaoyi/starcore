package dev.starcore.starcore.module.army.mercenary.command;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.mercenary.MercenaryContract;
import dev.starcore.starcore.module.army.mercenary.MercenaryRank;
import dev.starcore.starcore.module.army.mercenary.MercenaryService;
import dev.starcore.starcore.module.army.mercenary.MercenarySettings;
import dev.starcore.starcore.module.army.mercenary.MercenaryType;
import dev.starcore.starcore.module.army.mercenary.event.MercenaryContractEndedEvent.ContractEndReason;
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
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 雇佣兵命令处理器
 * /mercenary <子命令>
 * /merc <子命令>
 */
public final class MercenaryCommand implements CommandExecutor, TabCompleter {
    private final MercenaryService mercenaryService;
    private final NationService nationService;
    private final EconomyService economyService;
    private final MessageService messages;

    // 待处理的续约请求缓存
    private static final Map<UUID, RenewalRequest> pendingRenewalRequests = new java.util.concurrent.ConcurrentHashMap<>();

    public MercenaryCommand(
        MercenaryService mercenaryService,
        NationService nationService,
        EconomyService economyService,
        MessageService messages
    ) {
        this.mercenaryService = mercenaryService;
        this.nationService = nationService;
        this.economyService = economyService;
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
        Player player = null;

        // 大多数命令需要玩家身份
        switch (subCommand) {
            case "list", "stats" -> player = requirePlayer(sender);
            default -> {
                Player p = requirePlayer(sender);
                if (p == null) return true;
                player = p;
            }
        }

        try {
            switch (subCommand) {
                case "register" -> handleRegister(player);
                case "unregister" -> handleUnregister(player);
                case "available" -> handleAvailable(player, args);
                case "info" -> handleInfo(player, args);
                case "hire" -> handleHire(player, args);
                case "fire" -> handleFire(player, args);
                case "resign" -> handleResign(player);
                case "list" -> handleList(player, args);
                case "stats" -> handleStats(player, args);
                case "settings" -> handleSettings(player, args);
                case "renew" -> handleRenew(player, args);
                case "accept" -> handleAccept(player);
                case "decline" -> handleDecline(player);
                case "help" -> showHelp(sender);
                default -> sender.sendMessage(Component.text(
                    messages.format("mercenary.unknown-command", subCommand),
                    NamedTextColor.RED
                ));
            }
        } catch (IllegalStateException e) {
            sender.sendMessage(Component.text(
                messages.format(e.getMessage()),
                NamedTextColor.RED
            ));
        } catch (Exception e) {
            sender.sendMessage(Component.text(
                "Error: " + e.getMessage(),
                NamedTextColor.RED
            ));
        }

        return true;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Component.text(messages.format("command.player-only"), NamedTextColor.RED));
        return null;
    }

    // ==================== 命令处理 ====================

    private void handleRegister(Player player) {
        mercenaryService.setMercenaryAvailable(player.getUniqueId(), true);
        player.sendMessage(Component.text(
            messages.format("mercenary.registered"),
            NamedTextColor.GREEN
        ));
    }

    private void handleUnregister(Player player) {
        // 检查是否有活跃合同
        if (mercenaryService.isMercenary(player.getUniqueId())) {
            player.sendMessage(Component.text(
                messages.format("mercenary.cannot-unregister-active"),
                NamedTextColor.RED
            ));
            return;
        }

        mercenaryService.setMercenaryAvailable(player.getUniqueId(), false);
        player.sendMessage(Component.text(
            messages.format("mercenary.unregistered"),
            NamedTextColor.GREEN
        ));
    }

    private void handleAvailable(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("mercenary.available.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        boolean available = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true");
        mercenaryService.setMercenaryAvailable(player.getUniqueId(), available);

        player.sendMessage(Component.text(
            available ? messages.format("mercenary.available.enabled")
                      : messages.format("mercenary.available.disabled"),
            NamedTextColor.GREEN
        ));
    }

    private void handleInfo(Player player, String[] args) {
        UUID targetId = player.getUniqueId();
        if (args.length >= 2) {
            // 查看其他雇佣兵信息
            String playerName = args[1];
            // 通过名称查找玩家
            Player targetPlayer = player.getServer().getPlayer(playerName);
            if (targetPlayer != null) {
                targetId = targetPlayer.getUniqueId();
            } else {
                // 尝试通过离线玩家查找
                targetId = player.getServer().getOfflinePlayer(playerName).getUniqueId();
            }

            // 获取目标雇佣兵合同
            Optional<MercenaryContract> contract = mercenaryService.getMercenaryContract(targetId);

            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text(
                messages.format("mercenary.info.header.other", playerName), NamedTextColor.GOLD));

            if (contract.isEmpty()) {
                player.sendMessage(Component.text(
                    messages.format("mercenary.info.other-not-mercenary", playerName),
                    NamedTextColor.GRAY
                ));
            } else {
                MercenaryContract c = contract.get();
                player.sendMessage(Component.text(
                    messages.format("mercenary.info.contract-id", c.contractId().toString().substring(0, 8)),
                    NamedTextColor.GRAY
                ));
                player.sendMessage(Component.text(
                    messages.format("mercenary.info.type", c.type().displayName()),
                    NamedTextColor.GRAY
                ));
                player.sendMessage(Component.text(
                    messages.format("mercenary.info.rank", c.rank().displayName()),
                    NamedTextColor.GRAY
                ));
                player.sendMessage(Component.text(
                    messages.format("mercenary.info.status", c.status().displayName()),
                    NamedTextColor.GRAY
                ));
                player.sendMessage(Component.text(
                    messages.format("mercenary.info.exp", c.experience(), c.experienceLevel()),
                    NamedTextColor.GRAY
                ));
                player.sendMessage(Component.text(
                    messages.format("mercenary.info.kdr", c.kills(), c.deaths(), String.format("%.2f", c.kdr())),
                    NamedTextColor.GRAY
                ));
                player.sendMessage(Component.text(
                    messages.format("mercenary.info.salary", c.salary()),
                    NamedTextColor.GRAY
                ));
                if (c.isActive()) {
                    player.sendMessage(Component.text(
                        messages.format("mercenary.info.remaining", c.getRemainingDays()),
                        NamedTextColor.YELLOW
                    ));
                }
            }
            player.sendMessage(Component.text(""));
            return;
        }

        // 查看自己的雇佣兵状态
        Optional<MercenaryContract> contract = mercenaryService.getMercenaryContract(player.getUniqueId());

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("mercenary.info.header"), NamedTextColor.GOLD));

        if (contract.isEmpty()) {
            MercenarySettings settings = mercenaryService.getMercenarySettings(player.getUniqueId());
            player.sendMessage(Component.text(
                messages.format("mercenary.info.not-mercenary"),
                NamedTextColor.GRAY
            ));
            player.sendMessage(Component.text(
                messages.format("mercenary.info.available-status",
                    settings.isAvailable() ? messages.format("common.yes") : messages.format("common.no")),
                NamedTextColor.GRAY
            ));
        } else {
            MercenaryContract c = contract.get();
            player.sendMessage(Component.text(
                messages.format("mercenary.info.contract-id", c.contractId().toString().substring(0, 8)),
                NamedTextColor.GRAY
            ));
            player.sendMessage(Component.text(
                messages.format("mercenary.info.type", c.type().displayName()),
                NamedTextColor.GRAY
            ));
            player.sendMessage(Component.text(
                messages.format("mercenary.info.rank", c.rank().displayName()),
                NamedTextColor.GRAY
            ));
            player.sendMessage(Component.text(
                messages.format("mercenary.info.status", c.status().displayName()),
                NamedTextColor.GRAY
            ));
            player.sendMessage(Component.text(
                messages.format("mercenary.info.exp", c.experience(), c.experienceLevel()),
                NamedTextColor.GRAY
            ));
            player.sendMessage(Component.text(
                messages.format("mercenary.info.kdr", c.kills(), c.deaths(), String.format("%.2f", c.kdr())),
                NamedTextColor.GRAY
            ));
            player.sendMessage(Component.text(
                messages.format("mercenary.info.salary", c.salary()),
                NamedTextColor.GRAY
            ));
            if (c.isActive()) {
                player.sendMessage(Component.text(
                    messages.format("mercenary.info.remaining", c.getRemainingDays()),
                    NamedTextColor.YELLOW
                ));
            }
        }
        player.sendMessage(Component.text(""));
    }

    private void handleHire(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("mercenary.hire.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 解析雇佣兵玩家
        String mercenaryName = args[1];
        Player mercenaryPlayer = player.getServer().getPlayer(mercenaryName);

        if (mercenaryPlayer == null) {
            player.sendMessage(Component.text(
                messages.format("mercenary.player-not-online", mercenaryName),
                NamedTextColor.RED
            ));
            return;
        }

        // 解析兵种
        MercenaryType type;
        try {
            type = MercenaryType.fromKey(args[2]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(
                messages.format("mercenary.invalid-type", args[2]),
                NamedTextColor.RED
            ));
            return;
        }

        // 解析天数
        int days = 7; // 默认7天
        if (args.length >= 4) {
            try {
                days = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text(
                    messages.format("mercenary.invalid-days", args[3]),
                    NamedTextColor.RED
                ));
                return;
            }
        }

        // 雇佣
        MercenaryContract contract = mercenaryService.hireMercenary(
            mercenaryPlayer.getUniqueId(),
            player.getUniqueId(),
            type,
            days
        );

        player.sendMessage(Component.text(
            messages.format("mercenary.hired",
                mercenaryPlayer.getName(),
                type.displayName(),
                days,
                contract.salary()),
            NamedTextColor.GREEN
        ));

        // 通知雇佣兵
        mercenaryPlayer.sendMessage(Component.text(
            messages.format("mercenary.being-hired",
                player.getName(),
                type.displayName(),
                days),
            NamedTextColor.GREEN
        ));
    }

    private void handleFire(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("mercenary.fire.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        String mercenaryName = args[1];
        Player mercenaryPlayer = player.getServer().getPlayer(mercenaryName);

        if (mercenaryPlayer == null) {
            player.sendMessage(Component.text(
                messages.format("mercenary.player-not-online", mercenaryName),
                NamedTextColor.RED
            ));
            return;
        }

        mercenaryService.dismissMercenary(mercenaryPlayer.getUniqueId());

        player.sendMessage(Component.text(
            messages.format("mercenary.fired", mercenaryName),
            NamedTextColor.GREEN
        ));

        if (mercenaryPlayer.isOnline()) {
            mercenaryPlayer.sendMessage(Component.text(
                messages.format("mercenary.being-fired", player.getName()),
                NamedTextColor.YELLOW
            ));
        }
    }

    private void handleResign(Player player) {
        if (!mercenaryService.isMercenary(player.getUniqueId())) {
            player.sendMessage(Component.text(
                messages.format("mercenary.not-employed"),
                NamedTextColor.RED
            ));
            return;
        }

        mercenaryService.resignMercenary(player.getUniqueId());

        player.sendMessage(Component.text(
            messages.format("mercenary.resigned"),
            NamedTextColor.GREEN
        ));
    }

    private void handleList(Player player, String[] args) {
        UUID nationId = null;

        // 获取玩家国家
        Optional<Nation> nationOpt = nationService.getNationByMember(player.getUniqueId());
        if (nationOpt.isPresent()) {
            nationId = nationOpt.get().id().value();
        } else if (args.length >= 2) {
            // 指定国家ID
            try {
                nationId = UUID.fromString(args[1]);
            } catch (IllegalArgumentException e) {
                player.sendMessage(Component.text(
                    messages.format("mercenary.invalid-nation-id"),
                    NamedTextColor.RED
                ));
                return;
            }
        }

        if (nationId == null) {
            player.sendMessage(Component.text(
                messages.format("mercenary.no-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        List<MercenaryContract> contracts = mercenaryService.getNationContracts(nationId);

        if (contracts.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("mercenary.no-contracts"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(
            messages.format("mercenary.list.header", contracts.size()),
            NamedTextColor.GOLD
        ));

        for (MercenaryContract contract : contracts) {
            Player mercPlayer = player.getServer().getPlayer(contract.mercenaryId());
            String mercName = mercPlayer != null ? mercPlayer.getName()
                : messages.format("mercenary.offline");

            player.sendMessage(Component.text(
                messages.format("mercenary.list.entry",
                    mercName,
                    contract.type().displayName(),
                    contract.rank().displayName(),
                    contract.status().displayName(),
                    contract.getRemainingDays()),
                NamedTextColor.GRAY
            ));
        }
        player.sendMessage(Component.text(""));
    }

    private void handleStats(Player player, String[] args) {
        // 显示雇佣兵市场统计
        List<MercenaryContract> available = mercenaryService.getAvailableMercenaries();

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(
            messages.format("mercenary.stats.header"),
            NamedTextColor.GOLD
        ));

        // 按类型统计
        Map<MercenaryType, Long> byType = available.stream()
            .collect(Collectors.groupingBy(MercenaryContract::type, Collectors.counting()));

        player.sendMessage(Component.text(
            messages.format("mercenary.stats.total-available", available.size()),
            NamedTextColor.GRAY
        ));

        for (Map.Entry<MercenaryType, Long> entry : byType.entrySet()) {
            player.sendMessage(Component.text(
                messages.format("mercenary.stats.by-type",
                    entry.getKey().displayName(),
                    entry.getValue()),
                NamedTextColor.GRAY
            ));
        }

        player.sendMessage(Component.text(""));
    }

    private void handleSettings(Player player, String[] args) {
        MercenarySettings settings = mercenaryService.getMercenarySettings(player.getUniqueId());

        if (args.length < 2) {
            // 显示当前设置
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text(
                messages.format("mercenary.settings.header"),
                NamedTextColor.GOLD
            ));
            player.sendMessage(Component.text(
                messages.format("mercenary.settings.available",
                    settings.isAvailable() ? messages.format("common.yes") : messages.format("common.no")),
                NamedTextColor.GRAY
            ));
            player.sendMessage(Component.text(
                messages.format("mercenary.settings.contract-days",
                    settings.minContractDays(),
                    settings.maxContractDays()),
                NamedTextColor.GRAY
            ));
            player.sendMessage(Component.text(
                messages.format("mercenary.settings.min-salary", settings.minSalaryPerDay()),
                NamedTextColor.GRAY
            ));
            if (!settings.getPreferredTypes().isEmpty()) {
                String types = settings.getPreferredTypes().stream()
                    .map(MercenaryType::displayName)
                    .collect(Collectors.joining(", "));
                player.sendMessage(Component.text(
                    messages.format("mercenary.settings.preferred-types", types),
                    NamedTextColor.GRAY
                ));
            }
            player.sendMessage(Component.text(""));
        } else {
            // 修改设置
            String action = args[1].toLowerCase();
            switch (action) {
                case "available" -> {
                    if (args.length >= 3) {
                        boolean available = args[2].equalsIgnoreCase("on");
                        settings.setAvailable(available);
                        player.sendMessage(Component.text(
                            available ? messages.format("mercenary.settings.available-enabled")
                                      : messages.format("mercenary.settings.available-disabled"),
                            NamedTextColor.GREEN
                        ));
                    }
                }
                case "mindays" -> {
                    if (args.length >= 3) {
                        settings.setMinContractDays(Integer.parseInt(args[2]));
                        player.sendMessage(Component.text(
                            messages.format("mercenary.settings.min-days-set", settings.minContractDays()),
                            NamedTextColor.GREEN
                        ));
                    }
                }
                case "maxdays" -> {
                    if (args.length >= 3) {
                        settings.setMaxContractDays(Integer.parseInt(args[2]));
                        player.sendMessage(Component.text(
                            messages.format("mercenary.settings.max-days-set", settings.maxContractDays()),
                            NamedTextColor.GREEN
                        ));
                    }
                }
                default -> player.sendMessage(Component.text(
                    messages.format("mercenary.settings.usage"),
                    NamedTextColor.YELLOW
                ));
            }
        }
    }

    private void handleRenew(Player player, String[] args) {
        if (!mercenaryService.isMercenary(player.getUniqueId())) {
            player.sendMessage(Component.text(
                messages.format("mercenary.not-employed"),
                NamedTextColor.RED
            ));
            return;
        }

        int days = 7;
        if (args.length >= 2) {
            try {
                days = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text(
                    messages.format("mercenary.invalid-days", args[1]),
                    NamedTextColor.RED
                ));
                return;
            }
        }

        Optional<MercenaryContract> contractOpt = mercenaryService.getMercenaryContract(player.getUniqueId());
        if (contractOpt.isPresent()) {
            MercenaryContract contract = contractOpt.get();

            // 续约逻辑需要雇主确认
            player.sendMessage(Component.text(
                messages.format("mercenary.renew-request-sent"),
                NamedTextColor.YELLOW
            ));

            // 查找雇主并发送通知
            Player employer = player.getServer().getPlayer(contract.employerId());
            if (employer != null) {
                // 雇主在线，发送即时消息
                Component renewRequest = Component.text("")
                    .append(Component.text("[雇佣兵续约请求] ", NamedTextColor.GOLD))
                    .append(Component.text(player.getName(), NamedTextColor.AQUA))
                    .append(Component.text(" 请求续约 " + days + " 天", NamedTextColor.GRAY))
                    .appendNewline()
                    .append(Component.text("使用 /merc accept 接受 或 /merc decline 拒绝", NamedTextColor.YELLOW));
                employer.sendMessage(renewRequest);

                // 保存待处理的续约请求
                pendingRenewalRequests.put(contract.contractId(), new RenewalRequest(
                    contract.contractId(),
                    player.getUniqueId(),
                    contract.employerId(),
                    days,
                    Instant.now().plusSeconds(86400) // 24小时有效期
                ));

                player.sendMessage(Component.text(
                    messages.format("mercenary.renew-employer-notified"),
                    NamedTextColor.GREEN
                ));
            } else {
                // 雇主不在线，保存请求到缓存以便下次上线通知
                pendingRenewalRequests.put(contract.contractId(), new RenewalRequest(
                    contract.contractId(),
                    player.getUniqueId(),
                    contract.employerId(),
                    days,
                    Instant.now().plusSeconds(86400)
                ));
                player.sendMessage(Component.text(
                    messages.format("mercenary.renew-employer-offline"),
                    NamedTextColor.YELLOW
                ));
            }
        }
    }

    // 待处理的续约请求记录
    private record RenewalRequest(UUID contractId, UUID mercenaryId, UUID employerId, int days, Instant expiresAt) {}

    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text(messages.format("mercenary.help.header"), NamedTextColor.GOLD));
        sender.sendMessage(Component.text(messages.format("mercenary.help.register"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(messages.format("mercenary.help.unregister"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(messages.format("mercenary.help.available"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(messages.format("mercenary.help.info"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(messages.format("mercenary.help.hire"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(messages.format("mercenary.help.fire"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(messages.format("mercenary.help.resign"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(messages.format("mercenary.help.list"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(messages.format("mercenary.help.stats"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(messages.format("mercenary.help.settings"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(messages.format("mercenary.help.renew"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(messages.format("mercenary.help.accept"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(messages.format("mercenary.help.decline"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(""));
    }

    /**
     * 处理接受续约请求
     */
    private void handleAccept(Player player) {
        // 查找该玩家作为雇主的待处理续约请求
        RenewalRequest pendingRequest = null;
        for (RenewalRequest request : pendingRenewalRequests.values()) {
            if (request.employerId().equals(player.getUniqueId()) && request.expiresAt().isAfter(java.time.Instant.now())) {
                pendingRequest = request;
                break;
            }
        }

        if (pendingRequest == null) {
            player.sendMessage(Component.text(
                messages.format("mercenary.accept.no-request"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 获取雇佣兵并处理续约
        Player mercenary = player.getServer().getPlayer(pendingRequest.mercenaryId());
        Optional<MercenaryContract> contractOpt = mercenaryService.getMercenaryContract(pendingRequest.mercenaryId());

        if (contractOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("mercenary.accept.contract-not-found"),
                NamedTextColor.RED
            ));
            pendingRenewalRequests.remove(pendingRequest.contractId());
            return;
        }

        MercenaryContract contract = contractOpt.get();

        // 扣除雇主金币
        int salary = contract.salary();
        if (!economyService.has(player.getUniqueId(), BigDecimal.valueOf(salary * pendingRequest.days()))) {
            player.sendMessage(Component.text(
                messages.format("mercenary.accept.insufficient-funds"),
                NamedTextColor.RED
            ));
            return;
        }

        // 执行续约
        economyService.withdraw(player.getUniqueId(), BigDecimal.valueOf(salary * pendingRequest.days()));
        economyService.deposit(pendingRequest.mercenaryId(), BigDecimal.valueOf(salary * pendingRequest.days()));

        // 更新合同有效期
        contract.setContractExpiresAt(contract.contractExpiresAt().plus(Duration.ofDays(pendingRequest.days())));
        mercenaryService.saveContractDirectly(contract);

        // 移除待处理请求
        pendingRenewalRequests.remove(pendingRequest.contractId());

        // 发送成功消息
        player.sendMessage(Component.text(
            messages.format("mercenary.accept.success", pendingRequest.days()),
            NamedTextColor.GREEN
        ));

        if (mercenary != null) {
            mercenary.sendMessage(Component.text(
                messages.format("mercenary.accept.mercenary-notified", player.getName(), pendingRequest.days()),
                NamedTextColor.GREEN
            ));
        }
    }

    /**
     * 处理拒绝续约请求
     */
    private void handleDecline(Player player) {
        // 查找该玩家作为雇主的待处理续约请求
        RenewalRequest pendingRequest = null;
        for (RenewalRequest request : pendingRenewalRequests.values()) {
            if (request.employerId().equals(player.getUniqueId()) && request.expiresAt().isAfter(java.time.Instant.now())) {
                pendingRequest = request;
                break;
            }
        }

        if (pendingRequest == null) {
            player.sendMessage(Component.text(
                messages.format("mercenary.decline.no-request"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 通知雇佣兵
        Player mercenary = player.getServer().getPlayer(pendingRequest.mercenaryId());
        pendingRenewalRequests.remove(pendingRequest.contractId());

        player.sendMessage(Component.text(
            messages.format("mercenary.decline.success"),
            NamedTextColor.YELLOW
        ));

        if (mercenary != null) {
            mercenary.sendMessage(Component.text(
                messages.format("mercenary.decline.mercenary-notified", player.getName()),
                NamedTextColor.YELLOW
            ));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (args.length == 1) {
            return List.of("register", "unregister", "available", "info", "hire",
                "fire", "resign", "list", "stats", "settings", "renew", "help");
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "available" -> List.of("on", "off");
                case "hire", "fire" -> sender.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
                case "settings" -> List.of("available", "mindays", "maxdays");
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("hire")) {
                return Arrays.stream(MercenaryType.values())
                    .map(MercenaryType::key)
                    .collect(Collectors.toList());
            }
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("hire")) {
                return List.of("7", "14", "30");
            }
        }

        return List.of();
    }
}