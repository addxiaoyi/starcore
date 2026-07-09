package dev.starcore.starcore.title;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 称号命令处理器
 * 处理 /title 相关命令
 */
public class TitleCommand implements CommandExecutor, TabCompleter {
    private final TitleService titleService;
    private final TitleDisplayService displayService;

    public TitleCommand(TitleService titleService, TitleDisplayService displayService) {
        this.titleService = titleService;
        this.displayService = displayService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> handleList(player);
            case "equip" -> handleEquip(player, args);
            case "unequip" -> handleUnequip(player);
            case "info" -> handleInfo(player, args);
            case "badge" -> handleBadge(player, args);
            case "unlock" -> handleUnlock(player, args);
            default -> sendUsage(player);
        }

        return true;
    }

    /**
     * 显示使用说明
     */
    private void sendUsage(Player player) {
        player.sendMessage(Component.text("§6=== StarCore 称号系统 ==="));
        player.sendMessage(Component.text("§e/title list §7- 查看所有称号"));
        player.sendMessage(Component.text("§e/title equip <ID> §7- 装备称号"));
        player.sendMessage(Component.text("§e/title unequip §7- 卸下称号"));
        player.sendMessage(Component.text("§e/title info <ID> §7- 查看称号详情"));
        player.sendMessage(Component.text("§e/title badge list §7- 查看所有徽章"));
        player.sendMessage(Component.text("§e/title badge equip <ID> §7- 装备徽章"));
        player.sendMessage(Component.text("§e/title badge unequip §7- 卸下徽章"));
    }

    /**
     * 列出所有称号
     */
    private void handleList(Player player) {
        titleService.getPlayerData(player.getUniqueId()).thenAccept(data -> {
            player.sendMessage(Component.text("§6=== 你的称号 ==="));

            var titles = titleService.getAllTitles();
            if (titles.isEmpty()) {
                player.sendMessage(Component.text("§7暂无可用称号"));
                return;
            }

            for (Title title : titles) {
                boolean unlocked = data.hasTitleUnlocked(title.id());
                boolean equipped = data.getEquippedTitle().map(id -> id.equals(title.id())).orElse(false);

                var lineBuilder = Component.text();
                if (equipped) {
                    lineBuilder.append(Component.text("§a[已装备] "));
                } else if (unlocked) {
                    lineBuilder.append(Component.text("§7[已解锁] "));
                } else {
                    lineBuilder.append(Component.text("§8[未解锁] "));
                }

                lineBuilder.append(title.getFormattedName())
                          .append(Component.text(" §7- " + title.id()));

                player.sendMessage(lineBuilder.build());
            }

            player.sendMessage(Component.text("§7已解锁: §e" + data.getTitleCount() +
                                            " §7/ §e" + titles.size()));
        });
    }

    /**
     * 装备称号
     */
    private void handleEquip(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("§c用法: /title equip <称号ID>"));
            return;
        }

        String titleId = args[1];

        // 检查称号是否存在
        if (titleService.getTitle(titleId).isEmpty()) {
            player.sendMessage(Component.text("§c称号不存在: " + titleId));
            return;
        }

        titleService.equipTitle(player.getUniqueId(), titleId).thenAccept(success -> {
            if (success) {
                Title title = titleService.getTitle(titleId).get();
                player.sendMessage(Component.text("§a✓ 已装备称号: ").append(title.getFormattedName()));

                // 刷新显示
                displayService.updateAllDisplays(player);
            } else {
                player.sendMessage(Component.text("§c你还没有解锁此称号"));
            }
        });
    }

    /**
     * 卸下称号
     */
    private void handleUnequip(Player player) {
        titleService.equipTitle(player.getUniqueId(), null).thenAccept(success -> {
            if (success) {
                player.sendMessage(Component.text("§7已卸下称号"));
                displayService.updateAllDisplays(player);
            }
        });
    }

    /**
     * 查看称号详情
     */
    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("§c用法: /title info <称号ID>"));
            return;
        }

        String titleId = args[1];
        var titleOpt = titleService.getTitle(titleId);

        if (titleOpt.isEmpty()) {
            player.sendMessage(Component.text("§c称号不存在: " + titleId));
            return;
        }

        Title title = titleOpt.get();

        titleService.getPlayerData(player.getUniqueId()).thenAccept(data -> {
            player.sendMessage(Component.text("§6=== 称号详情 ==="));
            player.sendMessage(Component.text("§e名称: ").append(title.getFormattedName()));
            player.sendMessage(Component.text("§e描述: ").append(title.description()));
            player.sendMessage(Component.text("§e类型: §7" + title.type().name()));
            player.sendMessage(Component.text("§e优先级: §7" + title.priority()));

            boolean unlocked = data.hasTitleUnlocked(title.id());
            player.sendMessage(Component.text("§e状态: " + (unlocked ? "§a已解锁" : "§c未解锁")));

            if (unlocked) {
                data.getTitleUnlockTime(title.id()).ifPresent(time -> {
                    player.sendMessage(Component.text("§e解锁时间: §7" + time.toString()));
                });
            } else {
                if (!title.unlockConditions().isEmpty()) {
                    player.sendMessage(Component.text("§e解锁条件:"));
                    for (String condition : title.unlockConditions()) {
                        player.sendMessage(Component.text("  §7- " + condition));
                    }
                }
            }
        });
    }

    /**
     * 处理徽章命令
     */
    private void handleBadge(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("§c用法: /title badge <list|equip|unequip>"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "list" -> handleBadgeList(player);
            case "equip" -> handleBadgeEquip(player, args);
            case "unequip" -> handleBadgeUnequip(player);
            default -> player.sendMessage(Component.text("§c未知子命令: " + args[1]));
        }
    }

    /**
     * 列出所有徽章
     */
    private void handleBadgeList(Player player) {
        titleService.getPlayerData(player.getUniqueId()).thenAccept(data -> {
            player.sendMessage(Component.text("§6=== 你的徽章 ==="));

            var badges = titleService.getAllBadges();
            if (badges.isEmpty()) {
                player.sendMessage(Component.text("§7暂无可用徽章"));
                return;
            }

            for (Badge badge : badges) {
                boolean unlocked = data.hasBadgeUnlocked(badge.id());
                boolean equipped = data.getEquippedBadge().map(id -> id.equals(badge.id())).orElse(false);

                var lineBuilder = Component.text();
                if (equipped) {
                    lineBuilder.append(Component.text("§a[已装备] "));
                } else if (unlocked) {
                    lineBuilder.append(Component.text("§7[已解锁] "));
                } else {
                    lineBuilder.append(Component.text("§8[未解锁] "));
                }

                lineBuilder.append(Component.text(badge.getFormatted() + " "))
                          .append(badge.getColoredName())
                          .append(Component.text(" §7- " + badge.id()));

                player.sendMessage(lineBuilder.build());
            }

            player.sendMessage(Component.text("§7已解锁: §e" + data.getBadgeCount() +
                                            " §7/ §e" + badges.size()));
        });
    }

    /**
     * 装备徽章
     */
    private void handleBadgeEquip(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("§c用法: /title badge equip <徽章ID>"));
            return;
        }

        String badgeId = args[2];

        if (titleService.getBadge(badgeId).isEmpty()) {
            player.sendMessage(Component.text("§c徽章不存在: " + badgeId));
            return;
        }

        titleService.equipBadge(player.getUniqueId(), badgeId).thenAccept(success -> {
            if (success) {
                Badge badge = titleService.getBadge(badgeId).get();
                player.sendMessage(Component.text("§a✓ 已装备徽章: ").append(badge.getColoredName()));
                displayService.updateAllDisplays(player);
            } else {
                player.sendMessage(Component.text("§c你还没有解锁此徽章"));
            }
        });
    }

    /**
     * 卸下徽章
     */
    private void handleBadgeUnequip(Player player) {
        titleService.equipBadge(player.getUniqueId(), null).thenAccept(success -> {
            if (success) {
                player.sendMessage(Component.text("§7已卸下徽章"));
                displayService.updateAllDisplays(player);
            }
        });
    }

    /**
     * 解锁称号（管理员命令）。D-091: 支持为他人解锁（/title unlock <称号ID> [玩家名]）。
     */
    private void handleUnlock(Player player, String[] args) {
        if (!player.hasPermission("starcore.title.admin")) {
            player.sendMessage(Component.text("§c你没有权限使用此命令"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("§c用法: /title unlock <称号ID> [玩家名]"));
            return;
        }

        String titleId = args[1];
        // D-091: 若提供第二个参数（玩家名），则为该玩家解锁
        java.util.UUID targetId;
        String targetLabel;
        if (args.length >= 3) {
            org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[2]);
            if (target == null || (!target.hasPlayedBefore() && target.getFirstPlayed() <= 0)) {
                player.sendMessage(Component.text("§c玩家不存在: " + args[2]));
                return;
            }
            targetId = target.getUniqueId();
            targetLabel = args[2];
        } else {
            targetId = player.getUniqueId();
            targetLabel = "你";
        }

        titleService.unlockTitle(targetId, titleId).thenAccept(success -> {
            if (success) {
                player.sendMessage(Component.text("§a✓ 已为 " + targetLabel + " 解锁称号: " + titleId));
            } else {
                player.sendMessage(Component.text("§c解锁失败或称号不存在"));
            }
        });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("list", "equip", "unequip", "info", "badge"));
            if (sender.hasPermission("starcore.title.admin")) {
                completions.add("unlock");
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("equip") || args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("unlock")) {
                completions.addAll(titleService.getAllTitles().stream()
                    .map(Title::id)
                    .collect(Collectors.toList()));
            } else if (args[0].equalsIgnoreCase("badge")) {
                completions.addAll(List.of("list", "equip", "unequip"));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("badge")) {
            if (args[1].equalsIgnoreCase("equip")) {
                completions.addAll(titleService.getAllBadges().stream()
                    .map(Badge::id)
                    .collect(Collectors.toList()));
            }
        }

        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
            .collect(Collectors.toList());
    }
}
