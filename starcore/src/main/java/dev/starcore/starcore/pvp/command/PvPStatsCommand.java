package dev.starcore.starcore.pvp.command;

import dev.starcore.starcore.pvp.stats.PvPStats;
import dev.starcore.starcore.pvp.stats.PvPStatsService;
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

import java.util.ArrayList;
import java.util.List;

/**
 * PvP统计命令
 * /pvpstats [玩家]
 */
public final class PvPStatsCommand implements CommandExecutor, TabCompleter {
    private final PvPStatsService statsService;

    public PvPStatsCommand(PvPStatsService statsService) {
        this.statsService = statsService;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        Player target;

        if (args.length > 0) {
            // 查看其他玩家
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text(
                    "玩家不在线: " + args[0],
                    NamedTextColor.RED
                ));
                return true;
            }
        } else {
            // 查看自己
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("请指定玩家", NamedTextColor.RED));
                return true;
            }
            target = player;
        }

        // 获取统计
        PvPStats stats = statsService.getStats(target.getUniqueId());

        if (stats == null) {
            sender.sendMessage(Component.text(
                "该玩家没有PvP数据",
                NamedTextColor.YELLOW
            ));
            return true;
        }

        // 显示统计
        displayStats(sender, target.getName(), stats);

        return true;
    }

    /**
     * 显示统计信息
     */
    private void displayStats(CommandSender sender, String playerName, PvPStats stats) {
        sender.sendMessage(Component.text(
            "========== " + playerName + " 的PvP统计 ==========",
            NamedTextColor.GOLD
        ));

        sender.sendMessage(Component.text(
            String.format("击杀: %d | 死亡: %d | 助攻: %d",
                stats.getKills(),
                stats.getDeaths(),
                stats.getAssists()),
            NamedTextColor.YELLOW
        ));

        sender.sendMessage(Component.text(
            String.format("K/D比率: %.2f | KDA: %.2f",
                stats.getKDRatio(),
                stats.getKDA()),
            NamedTextColor.YELLOW
        ));

        sender.sendMessage(Component.text(
            String.format("当前连杀: %d | 最高连杀: %d",
                stats.getCurrentKillStreak(),
                stats.getBestKillStreak()),
            NamedTextColor.YELLOW
        ));

        sender.sendMessage(Component.text(
            String.format("决斗: %d胜 %d负 (胜率: %.1f%%)",
                stats.getDuelWins(),
                stats.getDuelLosses(),
                stats.getDuelWinRate()),
            NamedTextColor.YELLOW
        ));

        sender.sendMessage(Component.text(
            String.format("造成伤害: %.0f | 受到伤害: %.0f",
                stats.getTotalDamageDealt(),
                stats.getTotalDamageTaken()),
            NamedTextColor.GRAY
        ));
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (args.length == 1) {
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                players.add(p.getName());
            }
            return players;
        }

        return List.of();
    }
}
