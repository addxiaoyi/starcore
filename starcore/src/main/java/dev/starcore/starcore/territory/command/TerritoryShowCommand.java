package dev.starcore.starcore.territory.command;

import dev.starcore.starcore.foundation.territory.model.Territory;
import dev.starcore.starcore.territory.visualization.TerritoryVisualizer;
import dev.starcore.starcore.territory.visualization.TerritoryVisualizer.BorderType;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Territory可视化命令 - Bukkit原生实现
 * 命令：/tshow <子命令>
 */
public class TerritoryShowCommand implements CommandExecutor, TabCompleter {

    private final TerritoryVisualizer visualizer;

    public TerritoryShowCommand(TerritoryVisualizer visualizer) {
        this.visualizer = visualizer;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (args.length == 0) {
            return handleShow(player, 10);
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "time" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /tshow time <秒数>");
                    return true;
                }
                try {
                    int seconds = Integer.parseInt(args[1]);
                    if (seconds < 1 || seconds > 60) {
                        player.sendMessage("§c时长必须在1-60秒之间");
                        return true;
                    }
                    return handleShow(player, seconds);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的数字");
                    return true;
                }
            }
            case "stop" -> {
                return handleStop(player);
            }
            case "friendly" -> {
                return handleShowType(player, BorderType.FRIENDLY, 10);
            }
            case "enemy" -> {
                return handleShowType(player, BorderType.ENEMY, 10);
            }
            case "own" -> {
                return handleShowType(player, BorderType.OWN, 10);
            }
            case "neutral" -> {
                return handleShowType(player, BorderType.NEUTRAL, 10);
            }
            default -> {
                player.sendMessage("§c未知子命令: " + subCommand);
                player.sendMessage("§7可用命令: time, stop, friendly, enemy, own, neutral");
                return true;
            }
        }
    }

    private boolean handleShow(Player player, int seconds) {
        Chunk chunk = player.getLocation().getChunk();
        visualizer.showBorder(player, chunk, BorderType.NEUTRAL, seconds);
        player.sendMessage("§a正在显示领地边界（" + seconds + "秒）");
        return true;
    }

    private boolean handleShowType(Player player, BorderType type, int seconds) {
        Chunk chunk = player.getLocation().getChunk();
        visualizer.showBorder(player, chunk, type, seconds);

        String color = switch (type) {
            case OWN -> "蓝色";
            case FRIENDLY -> "绿色";
            case ENEMY -> "红色";
            case NEUTRAL -> "黄色";
        };

        player.sendMessage("§a正在显示" + color + "领地边界（" + seconds + "秒）");
        return true;
    }

    private boolean handleStop(Player player) {
        visualizer.stopVisualization(player);
        player.sendMessage("§a已停止显示领地边界");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("time", "stop", "friendly", "enemy", "own", "neutral")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("time")) {
            return Arrays.asList("10", "20", "30", "60");
        }

        return Collections.emptyList();
    }
}
