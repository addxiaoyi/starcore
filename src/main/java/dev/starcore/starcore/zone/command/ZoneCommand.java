package dev.starcore.starcore.zone.command;
import java.util.Optional;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.zone.ZoneEffect;
import dev.starcore.starcore.zone.ZoneModule;
import dev.starcore.starcore.zone.ZoneService;
import dev.starcore.starcore.zone.ZoneSnapshot;
import dev.starcore.starcore.zone.ZoneType;
import dev.starcore.starcore.zone.gui.ZoneGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 经济区命令处理器
 */
public class ZoneCommand implements CommandExecutor, TabCompleter {

    private final ZoneModule zoneModule;
    private final NationService nationService;
    private final MessageService messages;

    public ZoneCommand(ZoneModule zoneModule, NationService nationService, MessageService messages) {
        this.zoneModule = zoneModule;
        this.nationService = nationService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家才能使用此命令");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create", "创建" -> handleCreate(player, args);
            case "delete", "删除" -> handleDelete(player, args);
            case "list", "列表" -> handleList(player);
            case "info", "信息" -> handleInfo(player, args);
            case "upgrade", "升级" -> handleUpgrade(player, args);
            case "effect", "特效" -> handleEffect(player, args);
            case "enable", "启用" -> handleEnable(player, args);
            case "disable", "停用" -> handleDisable(player, args);
            case "gui", "菜单" -> handleGui(player);
            case "help", "帮助" -> sendHelp(player);
            default -> player.sendMessage("§c未知命令，请使用 /zone help 查看帮助");
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        NationId nationId = getPlayerNation(player);
        if (nationId == null) {
            player.sendMessage("§c你不在任何国家中");
            return;
        }

        if (args.length < 3) {
            player.sendMessage("§c用法: /zone create <名称> <类型>");
            player.sendMessage("§7可用类型:");
            for (ZoneType type : ZoneType.values()) {
                player.sendMessage("  §f- §e" + type.name().toLowerCase() + " §7(" + type.getDisplayName() + ")");
            }
            return;
        }

        String name = args[1];
        String typeStr = args[2].toUpperCase();

        ZoneType type;
        try {
            type = ZoneType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c无效的经济区类型: " + typeStr);
            return;
        }

        try {
            ZoneSnapshot zone = zoneModule.createZone(nationId, name, type);
            player.sendMessage("§a经济区 '" + name + "' 创建成功!");
            player.sendMessage("§7类型: " + type.getDisplayName());
            player.sendMessage("§7税收加成: +" + String.format("%.1f%%", zone.taxBonus() * 100));
            player.sendMessage("§7产出加成: +" + String.format("%.1f%%", zone.productionBonus() * 100));
        } catch (IllegalStateException e) {
            player.sendMessage("§c" + e.getMessage());
        }
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /zone delete <经济区名称>");
            return;
        }

        String name = args[1];
        NationId nationId = getPlayerNation(player);
        if (nationId == null) {
            player.sendMessage("§c你不在任何国家中");
            return;
        }

        zoneModule.zonesOf(nationId).stream()
            .filter(z -> z.name().equalsIgnoreCase(name))
            .findFirst()
            .ifPresentOrElse(
                zone -> {
                    if (zoneModule.deleteZone(zone.id())) {
                        player.sendMessage("§a经济区 '" + name + "' 已删除");
                    }
                },
                () -> player.sendMessage("§c未找到经济区: " + name)
            );
    }

    private void handleList(Player player) {
        NationId nationId = getPlayerNation(player);
        if (nationId == null) {
            player.sendMessage("§c你不在任何国家中");
            return;
        }

        Collection<ZoneSnapshot> zones = zoneModule.zonesOf(nationId);

        if (zones.isEmpty()) {
            player.sendMessage("§7你的国家还没有经济区");
            player.sendMessage("§7使用 /zone create <名称> <类型> 创建第一个经济区");
            return;
        }

        player.sendMessage("§6§l=== 经济区列表 ===");
        player.sendMessage("§7数量: §f" + zones.size() + "/" + zoneModule.zoneLimitFor(nationId));

        for (ZoneSnapshot zone : zones) {
            String status = zone.active() ? "§a启用" : "§c停用";
            player.sendMessage(String.format(
                "§e- §f%s §7[%sLv.%d] §8| §7税收: §a+%.1f%% §8| §7产出: §a+%.1f%% §8| §7状态: %s",
                zone.name(),
                zone.type().getDisplayName(),
                zone.level(),
                zone.taxBonus() * 100,
                zone.productionBonus() * 100,
                status
            ));
        }
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /zone info <经济区名称>");
            return;
        }

        String name = args[1];
        NationId nationId = getPlayerNation(player);
        if (nationId == null) {
            player.sendMessage("§c你不在任何国家中");
            return;
        }

        zoneModule.zonesOf(nationId).stream()
            .filter(z -> z.name().equalsIgnoreCase(name))
            .findFirst()
            .ifPresentOrElse(
                zone -> sendZoneInfo(player, zone),
                () -> player.sendMessage("§c未找到经济区: " + name)
            );
    }

    private void sendZoneInfo(Player player, ZoneSnapshot zone) {
        player.sendMessage("§6§l=== 经济区详情 ===");
        player.sendMessage("§7名称: §f" + zone.name());
        player.sendMessage("§7类型: §f" + zone.type().getDisplayName());
        player.sendMessage("§7等级: §f" + zone.level() + "/" + zone.type().getMaxLevel());
        player.sendMessage("§7━━━━━━━━━━━━━━━");
        player.sendMessage("§7税收加成: §a+" + String.format("%.1f%%", zone.taxBonus() * 100));
        player.sendMessage("§7产出加成: §a+" + String.format("%.1f%%", zone.productionBonus() * 100));
        player.sendMessage("§7━━━━━━━━━━━━━━━");

        if (zone.effects().isEmpty()) {
            player.sendMessage("§7特效: §f无");
        } else {
            player.sendMessage("§7特效:");
            for (ZoneEffect effect : zone.effects()) {
                player.sendMessage("  §e- §f" + effect.getDisplayName() + " §8(" + effect.getDescription() + ")");
            }
        }

        player.sendMessage("§7━━━━━━━━━━━━━━━");
        player.sendMessage("§7状态: " + (zone.active() ? "§a启用" : "§c停用"));
    }

    private void handleUpgrade(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /zone upgrade <经济区名称>");
            return;
        }

        String name = args[1];
        NationId nationId = getPlayerNation(player);
        if (nationId == null) {
            player.sendMessage("§c你不在任何国家中");
            return;
        }

        zoneModule.zonesOf(nationId).stream()
            .filter(z -> z.name().equalsIgnoreCase(name))
            .findFirst()
            .ifPresentOrElse(
                zone -> {
                    if (zoneModule.upgradeZone(zone.id())) {
                        player.sendMessage("§a经济区升级成功!");
                        zoneModule.zoneById(zone.id()).ifPresent(z -> sendZoneInfo(player, z));
                    } else {
                        player.sendMessage("§c升级失败，请检查国库余额或是否已达最高等级");
                    }
                },
                () -> player.sendMessage("§c未找到经济区: " + name)
            );
    }

    private void handleEffect(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /zone effect <经济区名称> <add|remove> <特效名>");
            player.sendMessage("§7可用特效:");
            for (ZoneEffect effect : ZoneEffect.values()) {
                player.sendMessage("  §f- §e" + effect.name().toLowerCase() + " §7(" + effect.getDescription() + ")");
            }
            return;
        }

        String name = args[1];
        String action = args[2].toLowerCase();
        String effectName = args.length > 3 ? args[3].toUpperCase() : "";

        NationId nationId = getPlayerNation(player);
        if (nationId == null) {
            player.sendMessage("§c你不在任何国家中");
            return;
        }

        Optional<ZoneSnapshot> zoneOpt = zoneModule.zonesOf(nationId).stream()
            .filter(z -> z.name().equalsIgnoreCase(name))
            .findFirst();

        if (zoneOpt.isEmpty()) {
            player.sendMessage("§c未找到经济区: " + name);
            return;
        }

        ZoneSnapshot zone = zoneOpt.get();
        UUID zoneId = zone.id();

        if (action.equals("add")) {
            if (effectName.isEmpty()) {
                player.sendMessage("§c请指定特效名称");
                return;
            }

            ZoneEffect effect;
            try {
                effect = ZoneEffect.valueOf(effectName);
            } catch (IllegalArgumentException e) {
                player.sendMessage("§c无效的特效: " + effectName);
                return;
            }

            if (zone.effects().contains(effect)) {
                player.sendMessage("§c该经济区已拥有此特效");
                return;
            }

            if (zoneModule.addEffect(zoneId, effect)) {
                player.sendMessage("§a已添加特效: " + effect.getDisplayName());
            } else {
                player.sendMessage("§c添加特效失败");
            }
        } else if (action.equals("remove")) {
            if (effectName.isEmpty()) {
                player.sendMessage("§c请指定特效名称");
                return;
            }

            ZoneEffect effect;
            try {
                effect = ZoneEffect.valueOf(effectName);
            } catch (IllegalArgumentException e) {
                player.sendMessage("§c无效的特效: " + effectName);
                return;
            }

            if (!zone.effects().contains(effect)) {
                player.sendMessage("§c该经济区没有此特效");
                return;
            }

            if (zoneModule.removeEffect(zoneId, effect)) {
                player.sendMessage("§c已移除特效: " + effect.getDisplayName());
            } else {
                player.sendMessage("§c移除特效失败");
            }
        } else {
            player.sendMessage("§c无效操作，请使用 add 或 remove");
        }
    }

    private void handleEnable(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /zone enable <经济区名称>");
            return;
        }

        String name = args[1];
        NationId nationId = getPlayerNation(player);
        if (nationId == null) {
            player.sendMessage("§c你不在任何国家中");
            return;
        }

        zoneModule.zonesOf(nationId).stream()
            .filter(z -> z.name().equalsIgnoreCase(name))
            .findFirst()
            .ifPresentOrElse(
                zone -> {
                    zoneModule.enableZone(zone.id());
                    player.sendMessage("§a经济区 '" + name + "' 已启用");
                },
                () -> player.sendMessage("§c未找到经济区: " + name)
            );
    }

    private void handleDisable(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /zone disable <经济区名称>");
            return;
        }

        String name = args[1];
        NationId nationId = getPlayerNation(player);
        return;
    }

    private void handleGui(Player player) {
        NationId nationId = getPlayerNation(player);
        if (nationId == null) {
            player.sendMessage("§c你不在任何国家中");
            return;
        }

        ZoneGui gui = new ZoneGui(zoneModule, messages, player, nationId);
        gui.openMainMenu();
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6§l=== 经济区命令帮助 ===");
        player.sendMessage("§e/zone gui §7- 打开经济区管理菜单");
        player.sendMessage("§e/zone create <名称> <类型> §7- 创建经济区");
        player.sendMessage("§e/zone delete <名称> §7- 删除经济区");
        player.sendMessage("§e/zone list §7- 查看经济区列表");
        player.sendMessage("§e/zone info <名称> §7- 查看经济区详情");
        player.sendMessage("§e/zone upgrade <名称> §7- 升级经济区");
        player.sendMessage("§e/zone effect <名称> add <特效> §7- 添加特效");
        player.sendMessage("§e/zone effect <名称> remove <特效> §7- 移除特效");
        player.sendMessage("§e/zone enable <名称> §7- 启用经济区");
        player.sendMessage("§e/zone disable <名称> §7- 停用经济区");
    }

    private NationId getPlayerNation(Player player) {
        return nationService.nationOf(player.getUniqueId()).map(n -> n.id()).orElse(null);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        NationId nationId = getPlayerNation(player);
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("create", "delete", "list", "info", "upgrade", "effect", "enable", "disable", "gui"));
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("effect")) {
                completions.add("add");
                completions.add("remove");
            } else if (nationId != null) {
                if (subCommand.equals("delete") || subCommand.equals("info") ||
                    subCommand.equals("upgrade") || subCommand.equals("effect") ||
                    subCommand.equals("enable") || subCommand.equals("disable")) {
                    zoneModule.zonesOf(nationId).stream()
                        .map(ZoneSnapshot::name)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .forEach(completions::add);
                }
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("create")) {
                for (ZoneType type : ZoneType.values()) {
                    if (type.name().toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(type.name().toLowerCase());
                    }
                }
            } else if (subCommand.equals("effect")) {
                for (ZoneEffect effect : ZoneEffect.values()) {
                    if (effect.name().toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(effect.name().toLowerCase());
                    }
                }
            }
        }

        return completions;
    }
}
