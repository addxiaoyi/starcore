package dev.starcore.starcore.module.tournament.command;

import dev.starcore.starcore.module.tournament.Tournament;
import dev.starcore.starcore.module.tournament.TournamentStatus;
import dev.starcore.starcore.module.tournament.TournamentType;
import dev.starcore.starcore.module.tournament.gui.TournamentGui;
import dev.starcore.starcore.module.tournament.TournamentService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 锦标赛命令
 * /tournament <create|join|leave|list|info|start|cancel>
 */
public class TournamentCommand implements CommandExecutor, TabCompleter {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final TournamentService tournamentService;
    private final TournamentGui tournamentGui;

    public TournamentCommand(org.bukkit.plugin.java.JavaPlugin plugin,
                            TournamentService tournamentService,
                            TournamentGui tournamentGui) {
        this.plugin = plugin;
        this.tournamentService = tournamentService;
        this.tournamentGui = tournamentGui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                           @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create", "new" -> handleCreate(player, args);
            case "join", "j" -> handleJoin(player, args);
            case "leave", "l" -> handleLeave(player);
            case "list", "lobby" -> handleList(player);
            case "info", "i" -> handleInfo(player, args);
            case "start", "begin" -> handleStart(player);
            case "cancel", "stop" -> handleCancel(player);
            case "gui", "menu" -> tournamentGui.openMainGui(player);
            default -> sendHelp(player);
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /tournament create <类型> <名称>");
            player.sendMessage("§7类型: pvp1v1, pvpffa, team, speedrun, parkour");
            return;
        }

        TournamentType type = parseType(args[1]);
        if (type == null) {
            player.sendMessage("§c未知类型: " + args[1]);
            player.sendMessage("§7可用类型: pvp1v1, pvpffa, team, speedrun, parkour");
            return;
        }

        String name = args.length > 2 ? args[2] : type.displayName();

        Tournament tournament = tournamentService.createTournament(name, type, player);
        player.sendMessage("§a比赛已创建: §e" + name);
        player.sendMessage("§7类型: §f" + type.displayName());
        player.sendMessage("§7使用 §e/tournament join §7加入比赛");
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            // 显示等待中的比赛列表
            var waiting = tournamentService.getActiveTournaments().stream()
                .filter(t -> t.getStatus() == TournamentStatus.WAITING)
                .toList();

            if (waiting.isEmpty()) {
                player.sendMessage("§7当前没有等待中的比赛");
                player.sendMessage("§e使用 §f/tournament create §e创建新比赛");
                return;
            }

            player.sendMessage("§6§l等待中的比赛:");
            for (Tournament t : waiting) {
                player.sendMessage("§e- " + t.getName() + " §7(" + t.getType().displayName() + ") " +
                    t.getParticipants().size() + "/" + t.getConfig().maxPlayers());
                player.sendMessage("  §a/tournament join " + t.getId());
            }
            return;
        }

        String tournamentId = args[1];
        if (tournamentService.joinTournament(tournamentId, player)) {
            // 打开比赛信息界面
            tournamentService.getTournament(tournamentId)
                .ifPresent(t -> tournamentGui.openTournamentInfo(player, t));
        }
    }

    private void handleLeave(Player player) {
        if (tournamentService.leaveTournament(player)) {
            player.sendMessage("§e已离开当前比赛");
        }
    }

    private void handleList(Player player) {
        var tournaments = tournamentService.getActiveTournaments();

        if (tournaments.isEmpty()) {
            player.sendMessage("§7当前没有进行中的比赛");
            return;
        }

        player.sendMessage("§6§l===== 比赛列表 =====");
        for (Tournament t : tournaments) {
            String statusColor = switch (t.getStatus()) {
                case WAITING -> "§e";
                case IN_PROGRESS -> "§a";
                default -> "§7";
            };

            // 分隔
            player.sendMessage(statusColor + "§l" + t.getName());
            player.sendMessage("  §7类型: §f" + t.getType().displayName());
            player.sendMessage("  §7状态: " + statusColor + t.getStatus().displayName());
            player.sendMessage("  §7参赛者: §f" + t.getParticipants().size() +
                "/" + t.getConfig().maxPlayers());

            if (t.getStatus() == TournamentStatus.WAITING) {
                player.sendMessage("  §a/tournament join " + t.getId());
            }
        }
    }

    private void handleInfo(Player player, String[] args) {
        Optional<Tournament> current = tournamentService.getPlayerTournament(player);

        if (current.isEmpty()) {
            player.sendMessage("§c你不在任何比赛中");
            return;
        }

        tournamentGui.openTournamentInfo(player, current.get());
    }

    private void handleStart(Player player) {
        Optional<Tournament> current = tournamentService.getPlayerTournament(player);

        if (current.isEmpty()) {
            player.sendMessage("§c你不在任何比赛中");
            return;
        }

        Tournament tournament = current.get();
        if (!tournament.getCreatorId().equals(player.getUniqueId())) {
            player.sendMessage("§c只有创建者可以开始比赛");
            return;
        }

        if (tournament.getParticipants().size() < 2) {
            player.sendMessage("§c需要至少 2 名玩家才能开始比赛");
            return;
        }

        if (tournamentService.startTournament(tournament.getId())) {
            player.sendMessage("§a比赛已开始！");
        } else {
            player.sendMessage("§c无法开始比赛");
        }
    }

    private void handleCancel(Player player) {
        Optional<Tournament> current = tournamentService.getPlayerTournament(player);

        if (current.isEmpty()) {
            player.sendMessage("§c你不在任何比赛中");
            return;
        }

        Tournament tournament = current.get();
        if (!tournament.getCreatorId().equals(player.getUniqueId())) {
            player.sendMessage("§c只有创建者可以取消比赛");
            return;
        }

        if (tournamentService.cancelTournament(tournament.getId(), "被创建者取消")) {
            player.sendMessage("§c比赛已取消");
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6§l===== 锦标赛帮助 =====");
        player.sendMessage("§e/tournament gui §7- 打开锦标赛菜单");
        player.sendMessage("§e/tournament create <类型> <名称> §7- 创建比赛");
        player.sendMessage("§e/tournament join [ID] §7- 加入比赛");
        player.sendMessage("§e/tournament leave §7- 离开比赛");
        player.sendMessage("§e/tournament list §7- 查看比赛列表");
        player.sendMessage("§e/tournament info §7- 当前比赛信息");
        // 分隔
        player.sendMessage("§7比赛类型:");
        player.sendMessage("  §bpvp1v1 §7- PvP单挑");
        player.sendMessage("  §cpvpffa §7- PvP乱斗");
        player.sendMessage("  §9team §7- 团队赛");
        player.sendMessage("  §aspeedrun §7- 速通挑战");
        player.sendMessage("  §eparkour §7- 跑酷挑战");
    }

    private TournamentType parseType(String type) {
        return switch (type.toLowerCase()) {
            case "pvp1v1", "1v1", "solo" -> TournamentType.PVP_1V1;
            case "pvpffa", "ffa", "battle" -> TournamentType.PVP_FFA;
            case "team", "teams" -> TournamentType.PVP_TEAM;
            case "speedrun", "speed" -> TournamentType.SPEEDRUN;
            case "parkour", "pk" -> TournamentType.PARKOUR;
            case "elimination", "elim" -> TournamentType.ELIMINATION;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                     @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("gui");
            completions.add("create");
            completions.add("join");
            completions.add("leave");
            completions.add("list");
            completions.add("info");
            completions.add("start");
            completions.add("cancel");
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "create" -> {
                    completions.add("pvp1v1");
                    completions.add("pvpffa");
                    completions.add("team");
                    completions.add("speedrun");
                    completions.add("parkour");
                }
                case "join" -> {
                    tournamentService.getActiveTournaments().stream()
                        .filter(t -> t.getStatus() == TournamentStatus.WAITING)
                        .forEach(t -> completions.add(t.getId()));
                }
            }
        }

        return completions;
    }
}
