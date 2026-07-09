package dev.starcore.starcore.module.merge.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.merge.MergeReferendumService;
import dev.starcore.starcore.module.merge.model.MergeReferendum;
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

import java.util.*;

/**
 * 合并公投命令处理器
 * /merge <子命令>
 */
public final class MergeCommand implements CommandExecutor, TabCompleter {
    private final MergeReferendumService mergeService;
    private final NationService nationService;
    private final MessageService messages;

    public MergeCommand(MergeReferendumService mergeService, NationService nationService, MessageService messages) {
        this.mergeService = mergeService;
        this.nationService = nationService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(messages.format("merge.command.player-only"), NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "propose", "p" -> handlePropose(player, args);
                case "list", "ls" -> handleList(player, args);
                case "info", "i" -> handleInfo(player, args);
                case "vote", "v" -> handleVote(player, args);
                case "approve", "yes" -> handleVote(player, args, true);
                case "reject", "no" -> handleVote(player, args, false);
                case "cancel", "c" -> handleCancel(player, args);
                case "history", "h" -> handleHistory(player, args);
                default -> showHelp(player);
            }
        } catch (IllegalStateException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    private void handlePropose(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text(
                messages.format("merge.propose.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 获取玩家国家
        Optional<Nation> playerNation = nationService.nationOf(player.getUniqueId());
        if (playerNation.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("merge.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = playerNation.get();

        // 检查是否是国家创始人
        if (!nation.founderId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text(
                messages.format("merge.founder-only"),
                NamedTextColor.RED
            ));
            return;
        }

        // 获取目标国家
        String targetNationName = args[2];
        Optional<Nation> targetNation = nationService.nationByName(targetNationName);
        if (targetNation.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("merge.target-not-found", targetNationName),
                NamedTextColor.RED
            ));
            return;
        }

        // 获取新国家名称
        String newNationName = args[3];

        // 发起公投
        mergeService.propose(
            player.getUniqueId(),
            player.getName(),
            nation.id().value(),
            targetNation.get().id().value(),
            newNationName
        );

        player.sendMessage(Component.text(
            messages.format("merge.proposed", nation.name(), targetNationName, newNationName),
            NamedTextColor.GREEN
        ));
    }

    private void handleList(Player player, String[] args) {
        Optional<Nation> playerNation = nationService.nationOf(player.getUniqueId());
        if (playerNation.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("merge.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Collection<MergeReferendum> referendums = mergeService.getNationReferendums(playerNation.get().id().value());

        if (referendums.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("merge.no-active"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("merge.list.header"), NamedTextColor.GOLD));
        for (MergeReferendum ref : referendums) {
            String info = String.format("%s -> %s = %s [ %s ]",
                ref.proposerNationId().value().toString().substring(0, 8),
                ref.targetNationId().value().toString().substring(0, 8),
                ref.newNationName(),
                ref.state().name()
            );
            player.sendMessage(Component.text(info, NamedTextColor.GRAY));
        }
        player.sendMessage(Component.text(""));
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("merge.info.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID referendumId = parseReferendumId(player, args[1]);
        Optional<MergeReferendum> ref = mergeService.get(referendumId);

        if (ref.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("merge.not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        MergeReferendum referendum = ref.get();
        int[] stats = mergeService.getVoteStats(referendumId);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("merge.info.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(
            messages.format("merge.info.target", referendum.proposerNationId().value().toString().substring(0, 8),
                referendum.targetNationId().value().toString().substring(0, 8), referendum.newNationName()),
            NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
            messages.format("merge.info.votes", stats[0], stats[1], stats[2]),
            NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
            messages.format("merge.info.state", referendum.state().name()),
            NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(""));
    }

    private void handleVote(Player player, String[] args) {
        handleVote(player, args, true);
    }

    private void handleVote(Player player, String[] args, boolean approve) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("merge.vote.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID referendumId = parseReferendumId(player, args[1]);

        if (!mergeService.isParticipant(player.getUniqueId(), referendumId)) {
            player.sendMessage(Component.text(
                messages.format("merge.not-participant"),
                NamedTextColor.RED
            ));
            return;
        }

        if (mergeService.hasVoted(player.getUniqueId(), referendumId)) {
            player.sendMessage(Component.text(
                messages.format("merge.already-voted"),
                NamedTextColor.RED
            ));
            return;
        }

        boolean success = mergeService.vote(player.getUniqueId(), referendumId, approve);
        if (success) {
            player.sendMessage(Component.text(
                messages.format(approve ? "merge.voted-approve" : "merge.voted-reject"),
                NamedTextColor.GREEN
            ));
        }
    }

    private void handleCancel(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("merge.cancel.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID referendumId = parseReferendumId(player, args[1]);
        boolean success = mergeService.cancel(referendumId, player.getUniqueId());

        if (success) {
            player.sendMessage(Component.text(
                messages.format("merge.cancelled"),
                NamedTextColor.GREEN
            ));
        }
    }

    private void handleHistory(Player player, String[] args) {
        Optional<Nation> playerNation = nationService.nationOf(player.getUniqueId());
        if (playerNation.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("merge.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        int limit = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        List<MergeReferendum> history = mergeService.getHistory(playerNation.get().id().value(), limit);

        if (history.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("merge.no-history"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("merge.history.header"), NamedTextColor.GOLD));
        for (MergeReferendum ref : history) {
            String entry = String.format("%s = %s [ %s ]",
                ref.newNationName(),
                ref.state().displayName(),
                ref.createdAt().toString().substring(0, 10)
            );
            player.sendMessage(Component.text(entry, NamedTextColor.GRAY));
        }
        player.sendMessage(Component.text(""));
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("merge.help.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("merge.help.propose"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("merge.help.list"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("merge.help.info"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("merge.help.vote"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("merge.help.cancel"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("merge.help.history"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private UUID parseReferendumId(Player player, String idStr) {
        if (idStr.length() == 36) {
            return UUID.fromString(idStr);
        }
        // 支持短ID
        if (idStr.length() == 8) {
            for (MergeReferendum ref : mergeService.getAllActive()) {
                if (ref.id().toString().startsWith(idStr)) {
                    return ref.id();
                }
            }
            throw new IllegalArgumentException("Referendum not found: " + idStr);
        }
        throw new IllegalArgumentException("Invalid referendum ID format: " + idStr);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("propose", "list", "info", "vote", "approve", "reject", "cancel", "history");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            Optional<Nation> playerNation = nationService.nationOf(((Player) sender).getUniqueId());
            if (playerNation.isPresent()) {
                return mergeService.getNationReferendums(playerNation.get().id().value())
                    .stream()
                    .map(r -> r.id().toString().substring(0, 8))
                    .toList();
            }
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("vote") || args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("cancel"))) {
            Optional<Nation> playerNation = nationService.nationOf(((Player) sender).getUniqueId());
            if (playerNation.isPresent()) {
                return mergeService.getNationReferendums(playerNation.get().id().value())
                    .stream()
                    .map(r -> r.id().toString().substring(0, 8))
                    .toList();
            }
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("propose")) {
            return nationService.nations().stream()
                .map(Nation::name)
                .toList();
        }

        return List.of();
    }
}