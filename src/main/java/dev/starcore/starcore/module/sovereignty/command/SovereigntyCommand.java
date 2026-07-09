package dev.starcore.starcore.module.sovereignty.command;
import java.util.Optional;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.sovereignty.Sovereignty;
import dev.starcore.starcore.module.sovereignty.SovereigntyService;
import dev.starcore.starcore.module.sovereignty.model.SovereigntyClaim;
import dev.starcore.starcore.module.sovereignty.model.SovereigntyRecord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 主权声明命令处理器
 * /sc sovereignty <子命令>
 */
public final class SovereigntyCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SovereigntyService sovereigntyService;
    private final NationService nationService;
    private final MessageService messages;

    public SovereigntyCommand(SovereigntyService sovereigntyService, NationService nationService, MessageService messages) {
        this.sovereigntyService = sovereigntyService;
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
                case "declare", "d" -> handleDeclare(player, args);
                case "revoke", "r" -> handleRevoke(player, args);
                case "list", "ls" -> handleList(player, args);
                case "info", "i" -> handleInfo(player, args);
                case "status", "s" -> handleStatus(player, args);
                case "confirm", "c" -> handleConfirm(player, args);
                case "addclaim", "ac" -> handleAddClaim(player, args);
                case "removeclaim", "rc" -> handleRemoveClaim(player, args);
                case "strength", "str" -> handleStrength(player, args);
                default -> showHelp(player);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    /**
     * 声明主权: /sovereignty declare <区域名> <描述> [重要性]
     */
    private void handleDeclare(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.declare.usage"),
                    NamedTextColor.YELLOW
            ));
            return;
        }

        // 获取玩家国家
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.not-in-nation"),
                    NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();
        String regionName = args[1];
        String description = args[2];

        // 解析重要性等级
        SovereigntyService.SovereigntySignificance significance = SovereigntyService.SovereigntySignificance.MEDIUM;
        if (args.length > 3) {
            try {
                significance = SovereigntyService.SovereigntySignificance.valueOf(args[3].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage(Component.text(
                        messages.format("sovereignty.invalid-significance"),
                        NamedTextColor.RED
                ));
                return;
            }
        }

        SovereigntyRecord sovereignty = sovereigntyService.declareSovereignty(
                nation.id(),
                regionName,
                description,
                significance
        );

        player.sendMessage(Component.text(
                messages.format("sovereignty.declared", regionName, significance.displayName()),
                NamedTextColor.GREEN
        ));
    }

    /**
     * 撤销主权: /sovereignty revoke <主权ID>
     */
    private void handleRevoke(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.revoke.usage"),
                    NamedTextColor.YELLOW
            ));
            return;
        }

        UUID sovereigntyId = parseSovereigntyId(player, args[1]);
        Optional<SovereigntyRecord> sovereigntyOpt = sovereigntyService.getSovereignty(sovereigntyId);

        if (sovereigntyOpt.isEmpty()) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.not-found"),
                    NamedTextColor.RED
            ));
            return;
        }

        SovereigntyRecord sovereignty = sovereigntyOpt.get();

        // 检查是否是国家领袖
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty() || !nationOpt.get().id().equals(sovereignty.nationId())) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.not-owner"),
                    NamedTextColor.RED
            ));
            return;
        }

        sovereigntyService.revokeSovereignty(sovereigntyId);

        player.sendMessage(Component.text(
                messages.format("sovereignty.revoked", sovereignty.regionName()),
                NamedTextColor.GREEN
        ));
    }

    /**
     * 列出国家主权声明: /sovereignty list [国家名]
     */
    private void handleList(Player player, String[] args) {
        NationId targetNationId;

        if (args.length > 1) {
            // 查看其他国家的主权
            Optional<Nation> nationOpt = nationService.nationByName(args[1]);
            if (nationOpt.isEmpty()) {
                player.sendMessage(Component.text(
                        messages.format("nation.not-found"),
                        NamedTextColor.RED
                ));
                return;
            }
            targetNationId = nationOpt.get().id();
        } else {
            // 查看自己国家的主权
            Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isEmpty()) {
                player.sendMessage(Component.text(
                        messages.format("sovereignty.not-in-nation"),
                        NamedTextColor.RED
                ));
                return;
            }
            targetNationId = nationOpt.get().id();
        }

        Collection<SovereigntyRecord> sovereignties = sovereigntyService.getNationSovereignties(targetNationId);

        if (sovereignties.isEmpty()) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.list.empty"),
                    NamedTextColor.YELLOW
            ));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("sovereignty.list.header"), NamedTextColor.GOLD));
        for (SovereigntyRecord sovereignty : sovereignties) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.list.entry",
                            sovereignty.regionName(),
                            sovereignty.status().displayName(),
                            sovereignty.significance().displayName(),
                            sovereignty.strength(),
                            sovereignty.declaredAt().atZone(java.time.ZoneId.systemDefault()).format(DATE_FORMAT)
                    ),
                    NamedTextColor.GRAY
            ));
        }
        player.sendMessage(Component.text(""));
    }

    /**
     * 查看主权详情: /sovereignty info <主权ID或区域名>
     */
    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.info.usage"),
                    NamedTextColor.YELLOW
            ));
            return;
        }

        Optional<SovereigntyRecord> sovereigntyOpt = findSovereignty(player, args[1]);

        if (sovereigntyOpt.isEmpty()) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.not-found"),
                    NamedTextColor.RED
            ));
            return;
        }

        SovereigntyRecord sovereignty = sovereigntyOpt.get();
        Nation nation = nationService.nationById(sovereignty.nationId()).orElse(null);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("sovereignty.info.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(
                messages.format("sovereignty.info.region", sovereignty.regionName()),
                NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
                messages.format("sovereignty.info.nation", nation != null ? nation.name() : "Unknown"),
                NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
                messages.format("sovereignty.info.description", sovereignty.description()),
                NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
                messages.format("sovereignty.info.significance", sovereignty.significance().displayName()),
                NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
                messages.format("sovereignty.info.status", sovereignty.status().displayName()),
                NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
                messages.format("sovereignty.info.strength", sovereignty.strength()),
                NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
                messages.format("sovereignty.info.declared", sovereignty.declaredAt().atZone(java.time.ZoneId.systemDefault()).format(DATE_FORMAT)),
                NamedTextColor.GRAY
        ));

        // 显示领土声明
        Collection<SovereigntyClaim> claims = sovereigntyService.getClaimedTerritories(sovereignty.id());
        if (!claims.isEmpty()) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.info.claims", claims.size()),
                    NamedTextColor.GRAY
            ));
        }

        player.sendMessage(Component.text(""));
    }

    /**
     * 更新主权状态: /sovereignty status <主权ID> <状态>
     */
    private void handleStatus(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.status.usage"),
                    NamedTextColor.YELLOW
            ));
            return;
        }

        UUID sovereigntyId = parseSovereigntyId(player, args[1]);

        try {
            SovereigntyService.SovereigntyStatus newStatus = SovereigntyService.SovereigntyStatus.valueOf(args[2].toUpperCase());
            sovereigntyService.updateStatus(sovereigntyId, newStatus);

            player.sendMessage(Component.text(
                    messages.format("sovereignty.status.updated", newStatus.displayName()),
                    NamedTextColor.GREEN
            ));
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.invalid-status"),
                    NamedTextColor.RED
            ));
        }
    }

    /**
     * 确认主权: /sovereignty confirm <主权ID> [争议ID列表]
     */
    private void handleConfirm(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.confirm.usage"),
                    NamedTextColor.YELLOW
            ));
            return;
        }

        UUID sovereigntyId = parseSovereigntyId(player, args[1]);
        List<UUID> disputedIds = new ArrayList<>();

        if (args.length > 2) {
            for (int i = 2; i < args.length; i++) {
                try {
                    disputedIds.add(parseSovereigntyId(player, args[i]));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        boolean success = sovereigntyService.confirmSovereignty(sovereigntyId, disputedIds);

        if (success) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.confirmed"),
                    NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.confirm-failed"),
                    NamedTextColor.RED
            ));
        }
    }

    /**
     * 添加领土声明: /sovereignty addclaim <主权ID>
     */
    private void handleAddClaim(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.addclaim.usage"),
                    NamedTextColor.YELLOW
            ));
            return;
        }

        UUID sovereigntyId = parseSovereigntyId(player, args[1]);
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        boolean success = sovereigntyService.addClaimedTerritory(sovereigntyId, world, chunkX, chunkZ);

        if (success) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.addclaim.added", world, chunkX, chunkZ),
                    NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.addclaim-failed"),
                    NamedTextColor.RED
            ));
        }
    }

    /**
     * 移除领土声明: /sovereignty removeclaim <主权ID>
     */
    private void handleRemoveClaim(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.removeclaim.usage"),
                    NamedTextColor.YELLOW
            ));
            return;
        }

        UUID sovereigntyId = parseSovereigntyId(player, args[1]);
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        boolean success = sovereigntyService.removeClaimedTerritory(sovereigntyId, world, chunkX, chunkZ);

        if (success) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.removeclaim.removed", world, chunkX, chunkZ),
                    NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.removeclaim-failed"),
                    NamedTextColor.RED
            ));
        }
    }

    /**
     * 调整主权强度: /sovereignty strength <主权ID> <调整值>
     */
    private void handleStrength(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.strength.usage"),
                    NamedTextColor.YELLOW
            ));
            return;
        }

        UUID sovereigntyId = parseSovereigntyId(player, args[1]);

        try {
            int delta = Integer.parseInt(args[2]);
            SovereigntyRecord sovereignty = sovereigntyService.updateStrength(sovereigntyId, delta);

            player.sendMessage(Component.text(
                    messages.format("sovereignty.strength.updated", sovereignty.strength()),
                    NamedTextColor.GREEN
            ));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text(
                    messages.format("sovereignty.invalid-number", args[2]),
                    NamedTextColor.RED
            ));
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("sovereignty.help.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("sovereignty.help.declare"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("sovereignty.help.revoke"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("sovereignty.help.list"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("sovereignty.help.info"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("sovereignty.help.status"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("sovereignty.help.confirm"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("sovereignty.help.addclaim"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("sovereignty.help.removeclaim"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("sovereignty.help.strength"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private UUID parseSovereigntyId(Player player, String idStr) {
        if (idStr.length() == 36) {
            return UUID.fromString(idStr);
        }
        if (idStr.length() == 8) {
            // 短ID查找
            for (SovereigntyRecord sovereignty : sovereigntyService.getAllActiveSovereignties()) {
                if (sovereignty.id().toString().startsWith(idStr)) {
                    return sovereignty.id();
                }
            }
        }
        throw new IllegalArgumentException("Invalid sovereignty ID format: " + idStr);
    }

    private Optional<SovereigntyRecord> findSovereignty(Player player, String query) {
        // 先尝试按区域名查找
        Optional<SovereigntyRecord> byRegion = sovereigntyService.getSovereigntyByRegion(query);
        if (byRegion.isPresent()) {
            return byRegion;
        }

        // 再尝试按ID查找
        try {
            UUID id = parseSovereigntyId(player, query);
            return sovereigntyService.getSovereignty(id);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("declare", "revoke", "list", "info", "status", "confirm", "addclaim", "removeclaim", "strength");
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "declare" -> List.of("<region-name>");
                case "revoke", "info", "status", "confirm", "addclaim", "removeclaim", "strength" -> sovereigntyService.getAllActiveSovereignties()
                        .stream()
                        .map(s -> s.id().toString().substring(0, 8))
                        .collect(Collectors.toList());
                case "list" -> nationService.nations().stream()
                        .map(Nation::name)
                        .collect(Collectors.toList());
                default -> List.of();
            };
        }

        if (args.length == 3) {
            return switch (args[0].toLowerCase()) {
                case "declare" -> List.of("<description>", "LOW", "MEDIUM", "HIGH", "CRITICAL");
                case "status" -> List.of("CLAIMED", "RECOGNIZED", "CONTESTED", "DISPUTED", "REVOKED");
                case "strength" -> List.of("+10", "-10", "+50", "-50");
                default -> List.of();
            };
        }

        return List.of();
    }
}