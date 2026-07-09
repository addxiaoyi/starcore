package dev.starcore.starcore.module.donation.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.donation.DonationService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 献金命令处理器
 * /donate <国家名> <金额> [附言]
 * /donate rank [国家名]
 * /donate tier [国家名]
 * /donate history [页数]
 * /donate rewards [国家名]
 */
public final class DonationCommand implements CommandExecutor, TabCompleter {
    private final DonationService donationService;
    private final NationService nationService;
    private final MessageService messages;

    public DonationCommand(DonationService donationService, NationService nationService, MessageService messages) {
        this.donationService = donationService;
        this.nationService = nationService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
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
                case "donate", "d" -> handleDonate(player, args);
                case "rank", "r" -> handleRank(player, args);
                case "tier", "t" -> handleTier(player, args);
                case "history", "h" -> handleHistory(player, args);
                case "rewards" -> handleRewards(player, args);
                case "top" -> handleTop(player, args);
                default -> showHelp(player);
            }
        } catch (Exception e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    private void handleDonate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("donation.donate.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 解析国家名
        String nationName = args[1];
        Optional<Nation> nationOpt = nationService.nationByName(nationName);
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("donation.error.nation-not-found", nationName),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();
        NationId nationId = nation.id();

        // 解析金额
        BigDecimal amount;
        try {
            amount = new BigDecimal(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text(
                messages.format("donation.error.invalid-amount", args[2]),
                NamedTextColor.RED
            ));
            return;
        }

        // 解析附言（可选）
        String message = null;
        if (args.length > 3) {
            message = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
        }

        // 执行献金
        DonationService.DonationResult result;
        if (message != null) {
            result = donationService.donate(player.getUniqueId(), nationId, amount, message);
        } else {
            result = donationService.donate(player.getUniqueId(), nationId, amount);
        }

        if (!result.success()) {
            player.sendMessage(Component.text(
                messages.format(result.errorMessage()),
                NamedTextColor.RED
            ));
            return;
        }

        // 显示成功消息
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(
            messages.format("donation.success.header"),
            NamedTextColor.GREEN
        ));
        player.sendMessage(Component.text(
            messages.format("donation.success.amount",
                amount.setScale(2, RoundingMode.DOWN).toPlainString(),
                nation.name()),
            NamedTextColor.GRAY
        ));

        // 显示等级提升信息
        if (result.tierUpgraded()) {
            player.sendMessage(Component.text(
                messages.format("donation.tier.upgraded",
                    result.previousTier().name(),
                    result.newTier().name()),
                NamedTextColor.GOLD
            ));
        }

        // 显示当前等级
        player.sendMessage(Component.text(
            messages.format("donation.tier.current", result.newTier().name()),
            NamedTextColor.GRAY
        ));

        // 显示玩家余额
        BigDecimal playerBalance = result.record().amount(); // This is not player balance, need to fix
        player.sendMessage(Component.text(""));

        // 公告献金（如果启用）
        // Note: The actual announcement should be handled via event system
    }

    private void handleRank(Player player, String[] args) {
        // 获取玩家所在国家
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty() && args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("donation.rank.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        NationId nationId;
        String nationName;

        if (args.length >= 2) {
            // 指定国家
            nationName = args[1];
            nationOpt = nationService.nationByName(nationName);
            if (nationOpt.isEmpty()) {
                player.sendMessage(Component.text(
                    messages.format("donation.error.nation-not-found", nationName),
                    NamedTextColor.RED
                ));
                return;
            }
            nationId = nationOpt.get().id();
        } else {
            nationId = nationOpt.get().id();
            nationName = nationOpt.get().name();
        }

        // 获取玩家排名
        Optional<Integer> rankOpt = donationService.getPlayerRanking(player.getUniqueId(), nationId);
        BigDecimal total = donationService.getTotalDonations(player.getUniqueId(), nationId);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(
            messages.format("donation.rank.header", nationName),
            NamedTextColor.GOLD
        ));

        if (rankOpt.isPresent()) {
            player.sendMessage(Component.text(
                messages.format("donation.rank.position", rankOpt.get()),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("donation.rank.none"),
                NamedTextColor.GRAY
            ));
        }

        player.sendMessage(Component.text(
            messages.format("donation.rank.total", total.setScale(2, RoundingMode.DOWN).toPlainString()),
            NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(""));
    }

    private void handleTier(Player player, String[] args) {
        // 获取玩家所在国家
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        NationId nationId = nationOpt.map(Nation::id).orElse(null);

        // 获取玩家等级
        DonationService.DonationTier tier;
        if (nationId != null) {
            tier = donationService.getPlayerTier(player.getUniqueId(), nationId);
        } else {
            tier = donationService.getPlayerTier(player.getUniqueId());
        }

        // 显示等级信息
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(
            messages.format("donation.tier.header"),
            NamedTextColor.GOLD
        ));
        player.sendMessage(Component.text(
            messages.format("donation.tier.name", tier.name()),
            NamedTextColor.GREEN
        ));

        // 显示等级特权
        if (!tier.benefits().isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("donation.tier.benefits"),
                NamedTextColor.GRAY
            ));
            for (String benefit : tier.benefits()) {
                player.sendMessage(Component.text("  - " + benefit, NamedTextColor.DARK_GRAY));
            }
        }

        // 显示下一级所需金额
        DonationService.DonationTier nextTier = getNextTier(tier);
        if (nextTier != null) {
            BigDecimal needed = donationService.getAmountNeededForTier(player.getUniqueId(), nextTier.id());
            player.sendMessage(Component.text(
                messages.format("donation.tier.next",
                    nextTier.name(),
                    needed.setScale(2, RoundingMode.DOWN).toPlainString()),
                NamedTextColor.YELLOW
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("donation.tier.max"),
                NamedTextColor.GOLD
            ));
        }

        player.sendMessage(Component.text(""));
    }

    private DonationService.DonationTier getNextTier(DonationService.DonationTier current) {
        return donationService.getAllTiers().values().stream()
            .filter(t -> t.priority() > current.priority())
            .min((a, b) -> Integer.compare(a.priority(), b.priority()))
            .orElse(null);
    }

    private void handleHistory(Player player, String[] args) {
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                page = 1;
            }
        }

        int limit = 10;
        int offset = (page - 1) * limit;

        List<DonationService.DonationRecord> records = donationService.getPlayerDonations(
            player.getUniqueId(), limit, offset
        );

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(
            messages.format("donation.history.header", page),
            NamedTextColor.GOLD
        ));

        if (records.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("donation.history.empty"),
                NamedTextColor.GRAY
            ));
        } else {
            for (DonationService.DonationRecord record : records) {
                String date = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(java.time.ZoneId.systemDefault())
                    .format(record.donatedAt());
                player.sendMessage(Component.text(
                    messages.format("donation.history.entry",
                        record.amount().setScale(2, RoundingMode.DOWN).toPlainString(),
                        record.nationName(),
                        record.tier().name(),
                        date),
                    NamedTextColor.GRAY
                ));
            }
        }

        player.sendMessage(Component.text(
            messages.format("donation.history.page", page),
            NamedTextColor.DARK_GRAY
        ));
        player.sendMessage(Component.text(""));
    }

    private void handleRewards(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty() && args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("donation.rewards.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        NationId nationId;
        String nationName;

        if (args.length >= 2) {
            nationName = args[1];
            nationOpt = nationService.nationByName(nationName);
            if (nationOpt.isEmpty()) {
                player.sendMessage(Component.text(
                    messages.format("donation.error.nation-not-found", nationName),
                    NamedTextColor.RED
                ));
                return;
            }
            nationId = nationOpt.get().id();
        } else {
            nationId = nationOpt.get().id();
            nationName = nationOpt.get().name();
        }

        List<DonationService.DonationReward> rewards = donationService.getAvailableRewards(
            player.getUniqueId(), nationId
        );

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(
            messages.format("donation.rewards.header", nationName),
            NamedTextColor.GOLD
        ));

        if (rewards.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("donation.rewards.empty"),
                NamedTextColor.GRAY
            ));
        } else {
            for (DonationService.DonationReward reward : rewards) {
                boolean claimable = donationService.isRewardClaimable(player.getUniqueId(), nationId, reward.id());
                String status = claimable ? messages.format("donation.rewards.claimable") : messages.format("donation.rewards.locked");
                NamedTextColor color = claimable ? NamedTextColor.GREEN : NamedTextColor.GRAY;

                player.sendMessage(Component.text(
                    messages.format("donation.rewards.entry",
                        reward.name(),
                        reward.requiredTier().name(),
                        status),
                    color
                ));
                player.sendMessage(Component.text(
                    "  " + reward.description(),
                    NamedTextColor.DARK_GRAY
                ));
            }
        }

        player.sendMessage(Component.text(""));
    }

    private void handleTop(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty() && args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("donation.top.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        NationId nationId;
        String nationName;

        if (args.length >= 2) {
            nationName = args[1];
            nationOpt = nationService.nationByName(nationName);
            if (nationOpt.isEmpty()) {
                player.sendMessage(Component.text(
                    messages.format("donation.error.nation-not-found", nationName),
                    NamedTextColor.RED
                ));
                return;
            }
            nationId = nationOpt.get().id();
        } else {
            nationId = nationOpt.get().id();
            nationName = nationOpt.get().name();
        }

        List<DonationService.DonationRankingEntry> ranking = donationService.getDonationRanking(nationId, 10);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(
            messages.format("donation.top.header", nationName),
            NamedTextColor.GOLD
        ));

        if (ranking.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("donation.top.empty"),
                NamedTextColor.GRAY
            ));
        } else {
            for (DonationService.DonationRankingEntry entry : ranking) {
                player.sendMessage(Component.text(
                    messages.format("donation.top.entry",
                        entry.rank(),
                        entry.playerName(),
                        entry.totalAmount().setScale(2, RoundingMode.DOWN).toPlainString(),
                        entry.tier().name()),
                    NamedTextColor.GRAY
                ));
            }
        }

        player.sendMessage(Component.text(""));
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("donation.help.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("donation.help.donate"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("donation.help.rank"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("donation.help.tier"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("donation.help.history"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("donation.help.rewards"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("donation.help.top"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("donate");
            completions.add("rank");
            completions.add("tier");
            completions.add("history");
            completions.add("rewards");
            completions.add("top");
            return completions;
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "donate", "rewards", "top", "rank" -> {
                    // 返回国家名称列表
                    return nationService.nations().stream()
                        .map(Nation::name)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
                case "history" -> {
                    // 返回页码建议
                    return List.of("1", "2", "3", "4", "5");
                }
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("donate")) {
            // 返回建议金额
            return List.of("100", "500", "1000", "5000", "10000");
        }

        return List.of();
    }
}