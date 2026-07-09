package dev.starcore.starcore.module.weather.command;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.weather.WeatherControlService;
import dev.starcore.starcore.module.weather.WeatherForecastService;
import dev.starcore.starcore.module.weather.model.WeatherForecastEntry;
import dev.starcore.starcore.module.weather.model.WeatherType;
import dev.starcore.starcore.module.weather.model.WorldWeatherState;
import dev.starcore.starcore.module.weather.model.NationWeatherPermission;
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
 * 天气命令执行器
 */
public class WeatherCommand implements CommandExecutor, TabCompleter {

    private final WeatherControlService weatherService;
    private final WeatherForecastService forecastService;
    private final NationService nationService;
    private final OnlinePlayerDirectory onlinePlayerDirectory;
    private final EconomyService economyService;

    public WeatherCommand(
            WeatherControlService weatherService,
            WeatherForecastService forecastService,
            NationService nationService,
            OnlinePlayerDirectory onlinePlayerDirectory,
            EconomyService economyService) {
        this.weatherService = weatherService;
        this.forecastService = forecastService;
        this.nationService = nationService;
        this.onlinePlayerDirectory = onlinePlayerDirectory;
        this.economyService = economyService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help" -> sendHelp(sender);
            case "set" -> handleSet(sender, args);
            case "current" -> handleCurrent(sender, args);
            case "forecast" -> handleForecast(sender, args);
            case "worlds" -> handleWorlds(sender);
            case "control" -> handleControl(sender, args);
            case "release" -> handleRelease(sender, args);
            case "permission" -> handlePermission(sender, args);
            case "auto" -> handleAuto(sender, args);
            case "info" -> handleInfo(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6====== 天气控制系统 ======");
        sender.sendMessage("§e/weather set <weather> [world] §7- 设置天气");
        sender.sendMessage("§e  天气类型: clear, rain, thunder, snow, storm");
        sender.sendMessage("§e/weather current [world] §7- 查看当前天气");
        sender.sendMessage("§e/weather forecast [nation] §7- 查看天气预报");
        sender.sendMessage("§e/weather worlds §7- 列出所有世界");
        sender.sendMessage("§e/weather control <world> §7- 控制世界天气");
        sender.sendMessage("§e/weather release <world> §7- 释放世界控制");
        sender.sendMessage("§e/weather auto <on|off> §7- 设置自动天气");
        sender.sendMessage("§e/weather info [nation] §7- 查看天气信息");
        sender.sendMessage("§6=========================");
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /weather set <weather> [world]");
            return;
        }

        // 获取玩家所在国家
        Nation nation = getPlayerNation(player);
        if (nation == null) {
            sender.sendMessage("§c你还没有加入任何国家");
            return;
        }

        // 检查天气权限
        if (!weatherService.hasWeatherControlPermission(nation.id())) {
            sender.sendMessage("§c你的国家没有天气控制权限");
            return;
        }

        // 解析天气类型
        WeatherType weather;
        try {
            weather = WeatherType.fromName(args[1]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的天气类型: " + args[1]);
            sender.sendMessage("§e可用类型: clear, rain, thunder, snow, storm");
            return;
        }

        // 确定世界
        String worldName = args.length > 2 ? args[2] : player.getWorld().getName();

        // 检查世界是否被控制
        WorldWeatherState worldState = weatherService.getWorldWeatherState(worldName);
        if (worldState == null) {
            sender.sendMessage("§c世界 '" + worldName + "' 未注册到天气系统");
            return;
        }

        if (!worldState.isControlled() || !worldState.getControlledByNation().equals(nation.id())) {
            sender.sendMessage("§c你的国家没有控制这个世界天气的权限");
            return;
        }

        // 设置天气
        boolean success = weatherService.setWeather(nation.id(), worldName, weather);
        if (success) {
            sender.sendMessage("§a成功将 " + worldName + " 的天气设置为 " + weather.getIcon() + " " + weather.getDisplayName());
        } else {
            sender.sendMessage("§c设置天气失败，可能还在冷却中");
        }
    }

    private void handleCurrent(CommandSender sender, String[] args) {
        String worldName;

        if (args.length > 1) {
            worldName = args[1];
        } else if (sender instanceof Player player) {
            worldName = player.getWorld().getName();
        } else {
            sender.sendMessage("§c请指定世界名称");
            return;
        }

        WorldWeatherState state = weatherService.getWorldWeatherState(worldName);
        if (state == null) {
            sender.sendMessage("§c世界 '" + worldName + "' 未注册到天气系统");
            return;
        }

        sender.sendMessage("§6====== " + worldName + " 天气 ======");
        sender.sendMessage("§e当前天气: " + state.getCurrentWeather().getIcon() + " " + state.getCurrentWeather().getDisplayName());

        if (state.isControlled()) {
            sender.sendMessage("§e控制者: " + state.getControlledByNation());
            sender.sendMessage("§e自动天气: " + (state.getWeatherDurationMinutes() > 0 ? "已启用" : "已禁用"));
        } else {
            sender.sendMessage("§e状态: 自然天气");
        }

        // 显示资源影响
        Map<String, Double> modifiers = weatherService.getResourceModifiers(state.getCurrentWeather());
        sender.sendMessage("§7资源影响:");
        modifiers.forEach((resource, modifier) -> {
            int percent = (int) Math.round(modifier * 100);
            String symbol = percent > 100 ? "§a+" : percent < 100 ? "§c" : "§7";
            sender.sendMessage("§7  - " + resource + ": " + symbol + (percent - 100) + "%");
        });
    }

    private void handleForecast(CommandSender sender, String[] args) {
        NationId nationId;

        if (args.length > 1) {
            // 指定国家
            String nationName = args[1];
            Nation nation = nationService.nationByName(nationName).orElse(null);
            if (nation == null) {
                sender.sendMessage("§c未找到国家: " + nationName);
                return;
            }
            nationId = nation.id();
        } else if (sender instanceof Player player) {
            Nation nation = getPlayerNation(player);
            if (nation == null) {
                sender.sendMessage("§c你还没有加入任何国家");
                return;
            }
            nationId = nation.id();
        } else {
            sender.sendMessage("§c请指定国家名称");
            return;
        }

        List<WeatherForecastEntry> forecast = forecastService.getForecast(nationId);
        String nationNameStr = nationService.nationById(nationId).map(Nation::name).orElse("?");

        sender.sendMessage("§6====== " + nationNameStr + " 天气预报 ======");

        int count = 0;
        for (WeatherForecastEntry entry : forecast) {
            if (count >= 7) break;

            StringBuilder sb = new StringBuilder();
            sb.append("§e").append(entry.getFormattedDate());
            sb.append(": §f").append(entry.getIcon()).append(" ").append(entry.getDescription());

            sender.sendMessage(sb.toString());
            count++;
        }
    }

    private void handleWorlds(CommandSender sender) {
        Map<String, WorldWeatherState> worlds = weatherService.getAllWorldWeatherStates();

        sender.sendMessage("§6====== 已注册的世界 ======");
        sender.sendMessage("§e总数: " + worlds.size());

        worlds.forEach((worldName, state) -> {
            StringBuilder sb = new StringBuilder();
            sb.append("§f").append(worldName);
            sb.append(" §7- ");
            sb.append(state.getCurrentWeather().getIcon());
            sb.append(" ").append(state.getCurrentWeather().getDisplayName());

            if (state.isControlled()) {
                sb.append(" §c[已控制] ");
            } else {
                sb.append(" §a[自然]");
            }

            sender.sendMessage(sb.toString());
        });
    }

    private void handleControl(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /weather control <world>");
            return;
        }

        Nation nation = getPlayerNation(player);
        if (nation == null) {
            sender.sendMessage("§c你还没有加入任何国家");
            return;
        }

        String worldName = args[1];

        // 检查世界是否存在
        WorldWeatherState state = weatherService.getWorldWeatherState(worldName);
        if (state == null) {
            sender.sendMessage("§c世界 '" + worldName + "' 未注册到天气系统");
            return;
        }

        // 检查是否已被控制
        if (state.isControlled()) {
            sender.sendMessage("§c这个世界已经被控制");
            return;
        }

        // 控制世界
        boolean success = weatherService.controlWorld(nation.id(), worldName);
        if (success) {
            sender.sendMessage("§a成功控制 " + worldName + " 的天气");
        } else {
            sender.sendMessage("§c控制失败");
        }
    }

    private void handleRelease(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /weather release <world>");
            return;
        }

        Nation nation = getPlayerNation(player);
        if (nation == null) {
            sender.sendMessage("§c你还没有加入任何国家");
            return;
        }

        String worldName = args[1];

        WorldWeatherState state = weatherService.getWorldWeatherState(worldName);
        if (state == null) {
            sender.sendMessage("§c世界 '" + worldName + "' 未注册到天气系统");
            return;
        }

        if (!state.isControlled() || !state.getControlledByNation().equals(nation.id())) {
            sender.sendMessage("§c你的国家没有控制这个世界");
            return;
        }

        boolean success = weatherService.releaseWorld(worldName);
        if (success) {
            sender.sendMessage("§a成功释放 " + worldName + " 的控制权");
        } else {
            sender.sendMessage("§c释放失败");
        }
    }

    private void handlePermission(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return;
        }

        // 检查是否为管理员
        if (!player.hasPermission("starcore.weather.admin")) {
            sender.sendMessage("§c你没有权限使用此命令");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§c用法: /weather permission <nation> <level>");
            sender.sendMessage("§e等级: none, basic, advanced, full");
            return;
        }

        String nationName = args[1];
        Nation nation = nationService.nationByName(nationName).orElse(null);
        if (nation == null) {
            sender.sendMessage("§c未找到国家: " + nationName);
            return;
        }

        String levelStr = args[2].toLowerCase();
        NationWeatherPermission permission;

        switch (levelStr) {
            case "none" -> permission = NationWeatherPermission.NONE;
            case "basic" -> permission = NationWeatherPermission.CONTROL_BASIC;
            case "advanced" -> permission = NationWeatherPermission.CONTROL_ADVANCED;
            case "full" -> permission = NationWeatherPermission.CONTROL_FULL;
            default -> {
                sender.sendMessage("§c无效的权限等级: " + levelStr);
                return;
            }
        }

        weatherService.setPermission(nation.id(), permission);
        sender.sendMessage("§a已设置 " + nation.name() + " 的天气控制权限为: " + permission.getDescription());
    }

    private void handleAuto(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /weather auto <on|off>");
            return;
        }

        Nation nation = getPlayerNation(player);
        if (nation == null) {
            sender.sendMessage("§c你还没有加入任何国家");
            return;
        }

        String setting = args[1].toLowerCase();
        boolean auto;

        switch (setting) {
            case "on", "true", "enable" -> auto = true;
            case "off", "false", "disable" -> auto = false;
            default -> {
                sender.sendMessage("§c无效设置: " + setting + " (使用 on 或 off)");
                return;
            }
        }

        boolean success = weatherService.setAutoWeather(nation.id(), auto);
        if (success) {
            sender.sendMessage("§a自动天气已" + (auto ? "启用" : "禁用"));
        } else {
            sender.sendMessage("§c设置失败");
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        NationId nationId;

        if (args.length > 1) {
            String nationName = args[1];
            Nation nation = nationService.nationByName(nationName).orElse(null);
            if (nation == null) {
                sender.sendMessage("§c未找到国家: " + nationName);
                return;
            }
            nationId = nation.id();
        } else if (sender instanceof Player player) {
            Nation nation = getPlayerNation(player);
            if (nation == null) {
                sender.sendMessage("§c你还没有加入任何国家");
                return;
            }
            nationId = nation.id();
        } else {
            sender.sendMessage("§c请指定国家名称");
            return;
        }

        sender.sendMessage("§6====== 天气控制信息 ======");

        NationWeatherPermission permission = weatherService.getPermission(nationId);
        String nationNameStr = nationService.nationById(nationId).map(Nation::name).orElse("?");
        sender.sendMessage("§e国家: " + nationNameStr);
        sender.sendMessage("§e权限等级: " + permission.getDescription());
        sender.sendMessage("§e自动天气: " + (weatherService.isAutoWeather(nationId) ? "§a启用" : "§c禁用"));
        sender.sendMessage("§e当前天气: " + weatherService.getCurrentWeather(nationId).getIcon() +
                          " " + weatherService.getCurrentWeather(nationId).getDisplayName());

        // 显示可用的天气类型
        sender.sendMessage("§e可用天气:");
        switch (permission) {
            case NONE -> sender.sendMessage("§7  无可用天气");
            case CONTROL_BASIC -> sender.sendMessage("§7  晴天、小雨");
            case CONTROL_ADVANCED -> sender.sendMessage("§7  晴天、小雨、雷暴、降雪");
            case CONTROL_FULL -> sender.sendMessage("§7  所有天气类型");
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filterStartsWith(args[0], "help", "set", "current", "forecast", "worlds", "control", "release", "auto", "info", "permission");
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "set" -> filterStartsWith(args[1], "clear", "rain", "thunder", "snow", "storm");
                case "current", "control", "release" -> suggestWorlds();
                case "forecast", "info", "permission" -> suggestNations();
                case "auto" -> filterStartsWith(args[1], "on", "off");
                default -> Collections.emptyList();
            };
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("set")) {
                return suggestWorlds();
            }
            if (args[0].equalsIgnoreCase("permission")) {
                return filterStartsWith(args[2], "none", "basic", "advanced", "full");
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterStartsWith(String input, String... options) {
        String lower = input.toLowerCase();
        return Arrays.stream(options)
            .filter(opt -> opt.toLowerCase().startsWith(lower))
            .collect(Collectors.toList());
    }

    private List<String> suggestWorlds() {
        return weatherService.getAllWorldWeatherStates().keySet().stream()
            .sorted()
            .collect(Collectors.toList());
    }

    private List<String> suggestNations() {
        if (nationService == null) {
            return Collections.emptyList();
        }
        return nationService.nations().stream()
            .map(Nation::name)
            .sorted()
            .collect(Collectors.toList());
    }

    private Nation getPlayerNation(Player player) {
        if (nationService == null || player == null) {
            return null;
        }
        return nationService.nationOf(player.getUniqueId()).orElse(null);
    }
}
