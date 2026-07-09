package dev.starcore.starcore.module.split.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.split.SplitConfig;
import dev.starcore.starcore.module.split.SplitService;
import dev.starcore.starcore.module.split.SplitServiceImpl;
import dev.starcore.starcore.module.split.model.SplitRequest;
import dev.starcore.starcore.module.split.model.SplitRegion;
import dev.starcore.starcore.module.split.model.SplitResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 国家分裂命令处理器
 * /split <子命令>
 */
public final class SplitCommand implements CommandExecutor, TabCompleter {
    private final SplitService splitService;
    private final NationService nationService;
    private final MessageService messages;

    public SplitCommand(SplitService splitService, NationService nationService, MessageService messages) {
        this.splitService = splitService;
        this.nationService = nationService;
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
                case "approve", "a" -> handleApprove(player, args);
                case "reject", "r" -> handleReject(player, args);
                case "cancel" -> handleCancel(player, args);
                case "list", "l" -> handleList(player, args);
                case "info", "i" -> handleInfo(player, args);
                case "cost" -> handleCost(player, args);
                case "check" -> handleCheck(player);
                case "reload" -> handleReload(sender);
                case "help", "?" -> showHelp(player);
                default -> player.sendMessage(Component.text(
                    messages.format("split.unknown-command", subCommand),
                    NamedTextColor.RED
                ));
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("错误: " + e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    /**
     * 处理创建分裂请求
     */
    private void handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("split.create.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 获取玩家国家
        var nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("split.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();

        // 解析新国家名称
        String newNationName = args[1];

        // 解析区块坐标
        String[] coordArgs = args[2].split(",");
        if (coordArgs.length < 2) {
            player.sendMessage(Component.text(
                messages.format("split.invalid-coords"),
                NamedTextColor.RED
            ));
            return;
        }

        try {
            String[] centerCoords = coordArgs[0].split(":");
            int centerX = Integer.parseInt(centerCoords[0]);
            int centerZ = Integer.parseInt(centerCoords[1]);

            int radius = coordArgs.length > 1 ? Integer.parseInt(coordArgs[1]) : 0;

            // 创建分裂区域
            SplitRegion region;
            if (radius > 0) {
                region = SplitRegion.rectangle(
                    player.getWorld().getName(),
                    centerX - radius,
                    centerX + radius,
                    centerZ - radius,
                    centerZ + radius
                );
            } else {
                region = SplitRegion.single(player.getWorld().getName(), centerX, centerZ);
            }

            // 创建分裂请求
            SplitResult result = splitService.createSplitRequest(
                player.getUniqueId(),
                nation.id(),
                newNationName,
                region
            );

            if (result.isSuccess()) {
                player.sendMessage(Component.text(
                    messages.format("split.create.success", newNationName, result.transferredChunks()),
                    NamedTextColor.GREEN
                ));
                player.sendMessage(Component.text(
                    messages.format("split.create.await-approval"),
                    NamedTextColor.YELLOW
                ));
            } else {
                player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
            }

        } catch (NumberFormatException e) {
            player.sendMessage(Component.text(
                messages.format("split.invalid-number", args[2]),
                NamedTextColor.RED
            ));
        }
    }

    /**
     * 处理批准分裂请求
     */
    private void handleApprove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("split.approve.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        try {
            UUID requestId = UUID.fromString(args[1]);
            SplitResult result = splitService.approveSplitRequest(player.getUniqueId(), requestId);

            if (result.isSuccess()) {
                player.sendMessage(Component.text(
                    messages.format("split.approve.success", result.newNationName()),
                    NamedTextColor.GREEN
                ));
                player.sendMessage(Component.text(
                    messages.format("split.approve.completed",
                        result.originalNationName(),
                        result.newNationName(),
                        result.transferredChunks()),
                    NamedTextColor.GREEN
                ));
            } else {
                player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
            }

        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(
                messages.format("split.invalid-request-id", args[1]),
                NamedTextColor.RED
            ));
        }
    }

    /**
     * 处理拒绝分裂请求
     */
    private void handleReject(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("split.reject.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        try {
            UUID requestId = UUID.fromString(args[1]);
            SplitResult result = splitService.rejectSplitRequest(player.getUniqueId(), requestId);

            if (result.isSuccess()) {
                player.sendMessage(Component.text(
                    messages.format("split.reject.success"),
                    NamedTextColor.GREEN
                ));
            } else {
                player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
            }

        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(
                messages.format("split.invalid-request-id", args[1]),
                NamedTextColor.RED
            ));
        }
    }

    /**
     * 处理取消分裂请求
     */
    private void handleCancel(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("split.cancel.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        try {
            UUID requestId = UUID.fromString(args[1]);
            SplitResult result = splitService.cancelSplitRequest(player.getUniqueId(), requestId);

            if (result.isSuccess()) {
                player.sendMessage(Component.text(
                    messages.format("split.cancel.success"),
                    NamedTextColor.GREEN
                ));
            } else {
                player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
            }

        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(
                messages.format("split.invalid-request-id", args[1]),
                NamedTextColor.RED
            ));
        }
    }

    /**
     * 处理列出分裂请求
     */
    private void handleList(Player player, String[] args) {
        var nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("split.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();
        var requests = splitService.getPendingRequestsForNation(nation.id());

        if (requests.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("split.list.empty"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("split.list.header"), NamedTextColor.GOLD));

        for (SplitRequest request : requests) {
            player.sendMessage(Component.text(
                messages.format("split.list.entry",
                    request.requestId().toString().substring(0, 8),
                    request.newNationName(),
                    request.requesterName(),
                    request.region().chunkCount(),
                    request.durationMinutes()),
                NamedTextColor.GRAY
            ));
        }

        player.sendMessage(Component.text(""));
    }

    /**
     * 处理查看分裂请求详情
     */
    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("split.info.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        try {
            UUID requestId = UUID.fromString(args[1]);
            var requestOpt = splitService.getRequest(requestId);

            if (requestOpt.isEmpty()) {
                player.sendMessage(Component.text(
                    messages.format("split.request-not-found", args[1]),
                    NamedTextColor.RED
                ));
                return;
            }

            SplitRequest request = requestOpt.get();

            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text(messages.format("split.info.header"), NamedTextColor.GOLD));
            player.sendMessage(Component.text(
                messages.format("split.info.id", request.requestId().toString().substring(0, 8)),
                NamedTextColor.GRAY));
            player.sendMessage(Component.text(
                messages.format("split.info.status", request.status().name()),
                NamedTextColor.GRAY));
            player.sendMessage(Component.text(
                messages.format("split.info.source", request.sourceNationName()),
                NamedTextColor.GRAY));
            player.sendMessage(Component.text(
                messages.format("split.info.new", request.newNationName()),
                NamedTextColor.GRAY));
            player.sendMessage(Component.text(
                messages.format("split.info.requester", request.requesterName()),
                NamedTextColor.GRAY));
            player.sendMessage(Component.text(
                messages.format("split.info.chunks", request.region().chunkCount()),
                NamedTextColor.GRAY));
            player.sendMessage(Component.text(
                messages.format("split.info.duration", request.durationMinutes()),
                NamedTextColor.GRAY));
            player.sendMessage(Component.text(""));

        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(
                messages.format("split.invalid-request-id", args[1]),
                NamedTextColor.RED
            ));
        }
    }

    /**
     * 处理计算分裂费用
     */
    private void handleCost(Player player, String[] args) {
        var nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("split.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();

        int chunks = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        java.math.BigDecimal cost = splitService.calculateSplitCost(nation.id(),
            SplitRegion.single(player.getWorld().getName(), 0, 0)).multiply(java.math.BigDecimal.valueOf(chunks));

        player.sendMessage(Component.text(
            messages.format("split.cost.info", chunks, cost.doubleValue()),
            NamedTextColor.GREEN
        ));
    }

    /**
     * 处理检查是否可以分裂
     */
    private void handleCheck(Player player) {
        var nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("split.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();
        String checkResult = splitService.canInitiateSplit(player.getUniqueId(), nation.id());

        if (checkResult == null) {
            player.sendMessage(Component.text(
                messages.format("split.check.allowed", nation.name()),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("split.check.denied", nation.name(), checkResult),
                NamedTextColor.RED
            ));
        }
    }

    /**
     * 处理重载配置
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("starcore.split.admin")) {
            sender.sendMessage(Component.text(
                messages.format("common.no-permission"),
                NamedTextColor.RED
            ));
            return;
        }

        SplitConfig config = splitService.getConfig();
        config.reload();

        sender.sendMessage(Component.text(
            messages.format("split.reload.success"),
            NamedTextColor.GREEN
        ));
    }

    /**
     * 显示帮助信息
     */
    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("split.help.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("split.help.create"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("split.help.approve"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("split.help.reject"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("split.help.cancel"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("split.help.list"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("split.help.info"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("split.help.cost"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("split.help.check"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "approve", "reject", "cancel", "list", "info", "cost", "check", "help");
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            return switch (subCommand) {
                case "approve", "reject", "cancel", "info" -> {
                    var nationOpt = nationService.nationOf(((Player) sender).getUniqueId());
                    if (nationOpt.isPresent()) {
                        var requests = splitService.getPendingRequestsForNation(nationOpt.get().id());
                        yield requests.stream()
                            .map(r -> r.requestId().toString().substring(0, 8))
                            .collect(Collectors.toList());
                    }
                    yield List.of();
                }
                default -> List.of();
            };
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return List.of("x:z", "x:z,radius");
        }

        return List.of();
    }
}