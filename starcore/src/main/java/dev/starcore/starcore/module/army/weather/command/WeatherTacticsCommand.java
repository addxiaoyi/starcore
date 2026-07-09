package dev.starcore.starcore.module.army.weather.command;

import dev.starcore.starcore.module.army.weather.WeatherTacticsService;
import dev.starcore.starcore.module.army.weather.WeatherTacticsServiceImpl;
import dev.starcore.starcore.module.army.weather.model.WeatherTacticsBoost;
import dev.starcore.starcore.module.army.weather.model.WeatherTacticsEffect;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.weather.WeatherControlService;
import dev.starcore.starcore.module.weather.model.WeatherType;
import dev.starcore.starcore.foundation.message.MessageService;
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
import java.util.stream.Collectors;

/**
 * 天气战术命令处理器
 * /weathertactics <子命令>
 */
public final class WeatherTacticsCommand implements CommandExecutor, TabCompleter {

    private final WeatherTacticsService tacticsService;
    private final WeatherControlService weatherService;
    private final NationService nationService;
    private final MessageService messages;

    public WeatherTacticsCommand(
        WeatherTacticsService tacticsService,
        WeatherControlService weatherService,
        NationService nationService,
        MessageService messages
    ) {
        this.tacticsService = tacticsService;
        this.weatherService = weatherService;
        this.nationService = nationService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用此命令", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "info", "i" -> handleInfo(player, args);
                case "boost", "b" -> handleBoost(player, args);
                case "upgrade", "u" -> handleUpgrade(player, args);
                case "list", "ls" -> handleList(player, args);
                case "effect", "e" -> handleEffect(player, args);
                case "weather", "w" -> handleWeather(player, args);
                case "adaptation", "a" -> handleAdaptation(player, args);
                case "help", "h" -> showHelp(player);
                default -> player.sendMessage(Component.text("未知命令，使用 /weathertactics help 查看帮助", NamedTextColor.RED));
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("执行失败: " + e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    /**
     * 显示帮助信息
     */
    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 天气战术系统 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/wt info [天气] - 查看天气战术信息", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/wt boost <天气> - 查看国家战术加成", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/wt upgrade <战术> - 升级天气战术", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/wt list - 查看所有可用战术", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/wt effect <兵种> - 查看兵种天气适应性", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/wt weather - 查看当前天气", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/wt adaptation <兵种> - 查看兵种适应性", NamedTextColor.GRAY));
        player.sendMessage(Component.empty());
    }

    /**
     * 处理 info 命令 - 显示天气战术信息
     */
    private void handleInfo(Player player, String[] args) {
        WeatherType weather = WeatherType.CLEAR;

        if (args.length > 1) {
            weather = parseWeatherType(args[1]);
        } else {
            // 获取玩家国家的当前天气
            Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isPresent()) {
                weather = weatherService.getNationWeather(nationOpt.get().id());
            }
        }

        WeatherTacticsService.WeatherTacticsInfo info = tacticsService.getTacticsInfo(weather);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 天气战术信息 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("天气: " + weather.getIcon() + " " + weather.getDisplayName(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text(info.description(), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("基础加成:", NamedTextColor.AQUA));
        player.sendMessage(Component.text("  攻击力: " + formatPercent(info.baseAttackModifier()), NamedTextColor.GRAY));
        player.sendMessage(Component.text("  防御力: " + formatPercent(info.baseDefenseModifier()), NamedTextColor.GRAY));
        player.sendMessage(Component.text("  移动力: " + formatPercent(info.movementModifier()), NamedTextColor.GRAY));
        player.sendMessage(Component.empty());

        if (info.effectiveUnits().length > 0) {
            player.sendMessage(Component.text("优势兵种: " + String.join(", ", info.effectiveUnits()), NamedTextColor.GREEN));
        }
        if (info.weakUnits().length > 0) {
            player.sendMessage(Component.text("劣势兵种: " + String.join(", ", info.weakUnits()), NamedTextColor.RED));
        }
        player.sendMessage(Component.text(""));
    }

    /**
     * 处理 boost 命令 - 查看/设置国家战术加成
     */
    private void handleBoost(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你必须加入一个国家才能使用此功能", NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();
        NationId nationId = nation.id();

        if (args.length > 1) {
            // 设置战术加成
            WeatherType weather = parseWeatherType(args[1]);
            WeatherTacticsBoost boost = createBoostFromArgs(args, weather);
            tacticsService.setTacticsBoost(nationId, weather, boost);
            player.sendMessage(Component.text("已为国家 " + nation.name() + " 设置 " + weather.getDisplayName() + " 战术加成", NamedTextColor.GREEN));
        } else {
            // 显示当前加成
            Map<WeatherType, WeatherTacticsBoost> boosts = tacticsService.getNationTacticsBoosts(nationId);

            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("=== 国家战术加成 ===", NamedTextColor.GOLD));
            player.sendMessage(Component.text("国家: " + nation.name(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text(""));

            if (boosts.isEmpty()) {
                player.sendMessage(Component.text("当前无自定义战术加成", NamedTextColor.GRAY));
            } else {
                for (Map.Entry<WeatherType, WeatherTacticsBoost> entry : boosts.entrySet()) {
                    WeatherTacticsBoost boost = entry.getValue();
                    player.sendMessage(Component.text(
                        entry.getKey().getIcon() + " " + entry.getKey().getDisplayName() + ": " +
                        boost.getFormattedBonus(), NamedTextColor.GRAY));
                }
            }
            player.sendMessage(Component.text(""));
        }
    }

    /**
     * 处理 upgrade 命令 - 升级天气战术
     */
    private void handleUpgrade(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你必须加入一个国家才能使用此功能", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /wt upgrade <战术类型>", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("可用战术: " + String.join(", ", tacticsService.getAvailableTacticsTypes())));
            return;
        }

        Nation nation = nationOpt.get();
        NationId nationId = nation.id();
        String tacticsType = args[1].toLowerCase();

        int currentLevel = tacticsService.getTacticsLevel(nationId, tacticsType);
        double cost = tacticsService.getUpgradeCost(tacticsType, currentLevel);

        if (cost < 0) {
            player.sendMessage(Component.text("该战术已达到最高等级", NamedTextColor.RED));
            return;
        }

        int newLevel = tacticsService.upgradeTactics(nationId, tacticsType);
        if (newLevel > 0) {
            player.sendMessage(Component.text(
                String.format("成功升级 %s 至等级 %d!", getTacticsDisplayName(tacticsType), newLevel), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("升级失败", NamedTextColor.RED));
        }
    }

    /**
     * 处理 list 命令 - 列出所有可用战术
     */
    private void handleList(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        NationId nationId = nationOpt.map(Nation::id).orElse(null);

        String[] types = tacticsService.getAvailableTacticsTypes();

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 天气战术列表 ===", NamedTextColor.GOLD));

        for (String type : types) {
            int level = nationId != null ? tacticsService.getTacticsLevel(nationId, type) : 0;
            double cost = tacticsService.getUpgradeCost(type, level);
            String status = level >= 5 ? " (已满级)" : String.format(" (升级费用: %.0f)", cost);

            player.sendMessage(Component.text(
                "- " + getTacticsDisplayName(type) + " 等级 " + level + status,
                level > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        }
        player.sendMessage(Component.text(""));
    }

    /**
     * 处理 effect 命令 - 显示兵种天气适应性
     */
    private void handleEffect(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /wt effect <兵种类型>", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("可用兵种: infantry, cavalry, archer, siege, defensive"));
            return;
        }

        String unitType = args[1].toLowerCase();

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== " + getUnitDisplayName(unitType) + " 天气适应性 ===", NamedTextColor.GOLD));

        for (WeatherType weather : WeatherType.values()) {
            double adaptation = tacticsService.getUnitAdaptation(weather, unitType);
            String bar = createProgressBar(adaptation);
            NamedTextColor color = adaptation >= 1.0 ? NamedTextColor.GREEN :
                                   adaptation >= 0.5 ? NamedTextColor.YELLOW : NamedTextColor.RED;

            player.sendMessage(Component.text(
                weather.getIcon() + " " + weather.getDisplayName() + ": " + bar + " " + formatPercent(adaptation),
                color));
        }
        player.sendMessage(Component.text(""));
    }

    /**
     * 处理 weather 命令 - 显示当前天气
     */
    private void handleWeather(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你必须加入一个国家才能查看天气", NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();
        WeatherType weather = weatherService.getNationWeather(nation.id());
        WeatherTacticsEffect effect = tacticsService.getTacticsEffect(UUID.randomUUID(), weather);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 当前天气 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("国家: " + nation.name(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("天气: " + weather.getIcon() + " " + weather.getDisplayName(), NamedTextColor.AQUA));
        player.sendMessage(Component.text("战术评级: " + effect.getTacticsRating(), NamedTextColor.GOLD));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text(effect.getDetailedReport(), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    /**
     * 处理 adaptation 命令 - 显示兵种适应性详情
     */
    private void handleAdaptation(Player player, String[] args) {
        handleEffect(player, args);
    }

    // ==================== 辅助方法 ====================

    private WeatherType parseWeatherType(String input) {
        try {
            return WeatherType.fromName(input);
        } catch (IllegalArgumentException e) {
            return WeatherType.CLEAR;
        }
    }

    private String formatPercent(double value) {
        return String.format("%.0f%%", value * 100);
    }

    private String createProgressBar(double value) {
        int bars = (int) Math.min(10, value * 10);
        return "[" + "=".repeat(bars) + "-".repeat(Math.max(0, 10 - bars)) + "]";
    }

    private String getTacticsDisplayName(String tacticsType) {
        return switch (tacticsType) {
            case "weather_mastery" -> "天气掌控";
            case "rain_warfare" -> "雨天战术";
            case "thunder_tactics" -> "雷暴战术";
            case "snow_operations" -> "雪地作战";
            case "storm_assault" -> "风暴突击";
            default -> tacticsType;
        };
    }

    private String getUnitDisplayName(String unitType) {
        return switch (unitType) {
            case "infantry" -> "步兵";
            case "cavalry" -> "骑兵";
            case "archer" -> "弓箭手";
            case "siege" -> "攻城器械";
            case "defensive" -> "守军";
            default -> unitType;
        };
    }

    private WeatherTacticsBoost createBoostFromArgs(String[] args, WeatherType weather) {
        double atk = 1.0, def = 1.0, mov = 1.0, morale = 1.0;

        if (args.length > 2) atk = parseDouble(args[2], 1.0);
        if (args.length > 3) def = parseDouble(args[3], 1.0);
        if (args.length > 4) mov = parseDouble(args[4], 1.0);
        if (args.length > 5) morale = parseDouble(args[5], 1.0);

        return new WeatherTacticsBoost(weather, atk, def, mov, morale, "自定义", "玩家设置");
    }

    private double parseDouble(String input, double defaultValue) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("info", "boost", "upgrade", "list", "effect", "weather", "adaptation", "help");
        }

        if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            if (subCmd.equals("info") || subCmd.equals("boost")) {
                return Arrays.stream(WeatherType.values())
                    .map(WeatherType::getId)
                    .collect(Collectors.toList());
            }
            if (subCmd.equals("upgrade")) {
                return Arrays.asList(tacticsService.getAvailableTacticsTypes());
            }
            if (subCmd.equals("effect") || subCmd.equals("adaptation")) {
                return List.of("infantry", "cavalry", "archer", "siege", "defensive");
            }
        }

        return List.of();
    }
}