package dev.starcore.starcore.city.command;

import dev.starcore.starcore.city.City;
import dev.starcore.starcore.city.CityManager;
import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.module.nation.NationService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * City管理命令
 * 命令：/city <子命令>
 */
public class CityCommand implements CommandExecutor, TabCompleter {

    private final CityManager cityManager;
    private final InternalEconomyService economyService;
    private NationService nationService;

    public CityCommand(CityManager cityManager, InternalEconomyService economyService) {
        this.cityManager = cityManager;
        this.economyService = economyService;
        this.nationService = null;
    }

    public CityCommand(CityManager cityManager, InternalEconomyService economyService, ServiceRegistry serviceRegistry) {
        this.cityManager = cityManager;
        this.economyService = economyService;
        this.nationService = serviceRegistry.find(NationService.class).orElse(null);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (args.length == 0) {
            return handleInfo(player);
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /city create <名称>");
                    return true;
                }
                String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                return handleCreate(player, name);
            }
            case "delete" -> {
                return handleDelete(player);
            }
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /city invite <玩家>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("§c玩家不在线");
                    return true;
                }
                return handleInvite(player, target);
            }
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /city kick <玩家>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("§c玩家不在线");
                    return true;
                }
                return handleKick(player, target);
            }
            case "leave" -> {
                return handleLeave(player);
            }
            case "info" -> {
                String cityName = args.length >= 2 ? args[1] : null;
                return handleInfo(player, cityName);
            }
            case "list" -> {
                return handleList(player);
            }
            case "levelup" -> {
                return handleLevelUp(player);
            }
            case "deposit" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /city deposit <金额>");
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[1]);
                    return handleDeposit(player, amount);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的金额");
                    return true;
                }
            }
            case "withdraw" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /city withdraw <金额>");
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[1]);
                    return handleWithdraw(player, amount);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的金额");
                    return true;
                }
            }
            case "setspawn" -> {
                return handleSetSpawn(player);
            }
            case "spawn" -> {
                return handleSpawn(player);
            }
            case "top" -> {
                return handleTop(player);
            }
            case "toggle" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /city toggle <pvp|public|recruit>");
                    return true;
                }
                return handleToggle(player, args[1]);
            }
            case "accept" -> {
                return handleAccept(player);
            }
            case "decline" -> {
                return handleDecline(player);
            }
            default -> {
                player.sendMessage("§c未知子命令: " + subCommand);
                sendHelp(player);
                return true;
            }
        }
    }

    private boolean handleCreate(Player player, String name) {
        // 从 NationModule 获取玩家的 Nation ID
        if (nationService == null) {
            player.sendMessage("§c系统错误：国家服务未初始化");
            return true;
        }

        var nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c创建 City 需要先加入国家，请使用 /nation 命令");
            return true;
        }

        var nation = nationOpt.get();

        // 检查是否已达城市上限
        int cityCount = cityManager.getNationCities(nation.getId().value()).size();
        // 可以通过国家科技或等级设置上限，这里使用默认限制
        int maxCities = 5;
        if (cityCount >= maxCities) {
            player.sendMessage(String.format("§c你的国家已有 %d 个城市，上限为 %d", cityCount, maxCities));
            return true;
        }

        // 检查玩家是否有权限创建城市
        if (!nationService.isFounder(player.getUniqueId(), nation.getId())) {
            player.sendMessage("§c只有国家创始人可以创建城市");
            return true;
        }

        // 检查名称是否可用
        if (cityManager.getCityByName(name) != null) {
            player.sendMessage("§c该名称已被使用");
            return true;
        }

        // 创建城市
        City city = cityManager.createCity(name, nation.getId().value(), player.getUniqueId());
        if (city == null) {
            player.sendMessage("§c创建失败！名称可能已被使用");
            return true;
        }

        player.sendMessage("§a成功创建城市！");
        player.sendMessage("§7名称: §f" + city.getName());
        player.sendMessage("§7类型: " + city.getColoredTypeName());
        player.sendMessage("§7等级: §e" + city.getLevel());
        return true;
    }

    private boolean handleDelete(Player player) {
        City city = cityManager.getPlayerCity(player.getUniqueId());
        if (city == null) {
            player.sendMessage("§c你不在任何City中");
            return true;
        }

        if (!city.isMayor(player.getUniqueId())) {
            player.sendMessage("§c只有市长可以删除City");
            return true;
        }

        // 通知所有居民
        for (UUID residentId : city.getResidents()) {
            Player resident = Bukkit.getPlayer(residentId);
            if (resident != null && resident.isOnline()) {
                resident.sendMessage("§cCity " + city.getName() + " 已被删除");
            }
        }

        cityManager.deleteCity(city.getId());
        player.sendMessage("§aCity已删除");
        return true;
    }

    private boolean handleInvite(Player mayor, Player target) {
        City city = cityManager.getPlayerCity(mayor.getUniqueId());
        if (city == null) {
            mayor.sendMessage("§c你不在任何City中");
            return true;
        }

        if (!city.isMayor(mayor.getUniqueId())) {
            mayor.sendMessage("§c只有市长可以邀请居民");
            return true;
        }

        if (cityManager.getPlayerCity(target.getUniqueId()) != null) {
            mayor.sendMessage("§c该玩家已在其他City中");
            return true;
        }

        if (cityManager.joinCity(target.getUniqueId(), city.getId())) {
            mayor.sendMessage("§a成功邀请 §e" + target.getName() + " §a加入City");
            target.sendMessage("§a你已加入City " + city.getDisplayName());

            // 通知其他居民
            for (UUID residentId : city.getResidents()) {
                Player resident = Bukkit.getPlayer(residentId);
                if (resident != null && resident.isOnline() &&
                    !resident.equals(mayor) && !resident.equals(target)) {
                    resident.sendMessage("§e" + target.getName() + " §a加入了City");
                }
            }
        } else {
            mayor.sendMessage("§c邀请失败（City可能已满）");
        }

        return true;
    }

    private boolean handleKick(Player mayor, Player target) {
        City city = cityManager.getPlayerCity(mayor.getUniqueId());
        if (city == null) {
            mayor.sendMessage("§c你不在任何City中");
            return true;
        }

        if (!city.isMayor(mayor.getUniqueId())) {
            mayor.sendMessage("§c只有市长可以踢出居民");
            return true;
        }

        if (cityManager.kickResident(mayor.getUniqueId(), target.getUniqueId())) {
            mayor.sendMessage("§a已将 §e" + target.getName() + " §a踢出City");
            target.sendMessage("§c你已被踢出City " + city.getName());
        } else {
            mayor.sendMessage("§c踢出失败");
        }

        return true;
    }

    private boolean handleLeave(Player player) {
        City city = cityManager.getPlayerCity(player.getUniqueId());
        if (city == null) {
            player.sendMessage("§c你不在任何City中");
            return true;
        }

        if (city.isMayor(player.getUniqueId()) && city.getResidentCount() > 1) {
            player.sendMessage("§c作为市长，你必须先转让职位或删除City");
            player.sendMessage("§7使用 /city delete 删除City");
            return true;
        }

        cityManager.leaveCity(player.getUniqueId());
        player.sendMessage("§a你已离开City " + city.getName());

        // 通知其他居民
        for (UUID residentId : city.getResidents()) {
            Player resident = Bukkit.getPlayer(residentId);
            if (resident != null && resident.isOnline()) {
                resident.sendMessage("§e" + player.getName() + " §7离开了City");
            }
        }

        return true;
    }

    private boolean handleInfo(Player player) {
        City city = cityManager.getPlayerCity(player.getUniqueId());
        if (city == null) {
            player.sendMessage("§c你不在任何City中");
            player.sendMessage("§7使用 /city info <名称> 查看其他City");
            return true;
        }
        return showCityInfo(player, city);
    }

    private boolean handleInfo(Player player, String cityName) {
        City city;
        if (cityName == null) {
            return handleInfo(player);
        } else {
            city = cityManager.getCityByName(cityName);
            if (city == null) {
                player.sendMessage("§c找不到City: " + cityName);
                return true;
            }
        }
        return showCityInfo(player, city);
    }

    private boolean showCityInfo(Player player, City city) {
        player.sendMessage("§6§l==== City信息 ====");
        player.sendMessage("§7名称: §f" + city.getName());
        player.sendMessage("§7类型: " + city.getColoredTypeName());
        player.sendMessage("§7等级: §e" + city.getLevel());
        player.sendMessage("§7市长: §e" + Bukkit.getOfflinePlayer(city.getMayor()).getName());
        player.sendMessage("§7居民: §f" + city.getResidentCount() + "/" + city.getMaxResidents());
        // 分隔
        player.sendMessage("§7国库: §6" + String.format("%.2f", city.getTreasury()) + " 金币");
        player.sendMessage("§7每日维护费: §c" + String.format("%.2f", city.calculateDailyUpkeep()) + " 金币");
        player.sendMessage("§7税率: §e" + String.format("%.1f%%", city.getTaxRate() * 100));
        // 分隔
        player.sendMessage("§7PvP: " + (city.isPvpEnabled() ? "§a启用" : "§c禁用"));
        player.sendMessage("§7公开出生点: " + (city.isPublicSpawn() ? "§a是" : "§c否"));
        player.sendMessage("§7开放招募: " + (city.isOpenRecruitment() ? "§a是" : "§c否"));
        player.sendMessage("§7存在天数: §f" + city.getAgeDays());

        // 升级需求
        City.LevelRequirements req = city.getNextLevelRequirements();
        if (req != null) {
            // 分隔
            player.sendMessage("§e§l下一等级需求:");
            player.sendMessage("  §7居民: §f" + city.getResidentCount() + "/" + req.requiredResidents());
            player.sendMessage("  §7领地: §f" + city.getTerritoryCount() + "/" + req.requiredTerritories());
            player.sendMessage("  §7金币: §f" + city.getTreasury() + "/" + req.requiredGold());
        }

        return true;
    }

    private boolean handleList(Player player) {
        Collection<City> cities = cityManager.getAllCities();

        if (cities.isEmpty()) {
            player.sendMessage("§7当前没有任何City");
            return true;
        }

        player.sendMessage("§6§l==== City列表 ====");
        for (City city : cities) {
            player.sendMessage(String.format(
                "%s §f%s §7- §eLv.%d §7| §f%d居民",
                city.getColoredTypeName(),
                city.getName(),
                city.getLevel(),
                city.getResidentCount()
            ));
        }
        player.sendMessage("§7总计: §e" + cities.size() + " §7个City");

        return true;
    }

    private boolean handleLevelUp(Player player) {
        City city = cityManager.getPlayerCity(player.getUniqueId());
        if (city == null) {
            player.sendMessage("§c你不在任何City中");
            return true;
        }

        if (!city.isMayor(player.getUniqueId())) {
            player.sendMessage("§c只有市长可以升级City");
            return true;
        }

        var result = cityManager.levelUpCity(city.getId());
        player.sendMessage(result.success() ? "§a" + result.message() : "§c" + result.message());

        return true;
    }

    private boolean handleDeposit(Player player, double amount) {
        City city = cityManager.getPlayerCity(player.getUniqueId());
        if (city == null) {
            player.sendMessage("§c你不在任何City中");
            return true;
        }

        // 使用 InternalEconomyService 从玩家扣款
        BigDecimal withdrawAmount = BigDecimal.valueOf(amount);
        if (!economyService.withdraw(player.getUniqueId(), withdrawAmount)) {
            player.sendMessage("§c余额不足，无法存款");
            return true;
        }
        city.deposit(amount);
        player.sendMessage(String.format("§a已存入 %.2f 金币到City国库", amount));

        return true;
    }

    private boolean handleWithdraw(Player player, double amount) {
        City city = cityManager.getPlayerCity(player.getUniqueId());
        if (city == null) {
            player.sendMessage("§c你不在任何City中");
            return true;
        }

        if (!city.isMayor(player.getUniqueId())) {
            player.sendMessage("§c只有市长可以取款");
            return true;
        }

        if (city.withdraw(amount)) {
            // 使用 InternalEconomyService 给玩家加钱
            BigDecimal depositAmount = BigDecimal.valueOf(amount);
            economyService.deposit(player.getUniqueId(), depositAmount);
            player.sendMessage(String.format("§a已从City国库取出 %.2f 金币", amount));
        } else {
            player.sendMessage("§c余额不足");
        }

        return true;
    }

    private boolean handleSetSpawn(Player player) {
        City city = cityManager.getPlayerCity(player.getUniqueId());
        if (city == null) {
            player.sendMessage("§c你不在任何City中");
            return true;
        }

        if (!city.isMayor(player.getUniqueId())) {
            player.sendMessage("§c只有市长可以设置出生点");
            return true;
        }

        // 设置 City 出生点
        city.setSpawnPoint(player.getLocation());
        player.sendMessage("§aCity出生点已设置在: " + formatLocation(player.getLocation()));
        return true;
    }

    private boolean handleSpawn(Player player) {
        City city = cityManager.getPlayerCity(player.getUniqueId());
        if (city == null) {
            player.sendMessage("§c你不在任何City中");
            return true;
        }

        Location spawn = city.getSpawnPoint();
        if (spawn == null) {
            player.sendMessage("§c该City还未设置出生点");
            return true;
        }

        // 传送到 City 出生点
        player.teleport(spawn);
        player.sendMessage("§a已传送到City出生点: " + city.getName());
        return true;
    }

    private String formatLocation(Location loc) {
        return String.format("%s (%d, %d, %d)",
            loc.getWorld().getName(),
            loc.getBlockX(),
            loc.getBlockY(),
            loc.getBlockZ());
    }

    private boolean handleTop(Player player) {
        player.sendMessage("§6§l==== City排行榜 ====");
        // 分隔

        player.sendMessage("§e§l按等级排名:");
        List<City> topLevel = cityManager.getTopCitiesByLevel(5);
        for (int i = 0; i < topLevel.size(); i++) {
            City city = topLevel.get(i);
            player.sendMessage(String.format(
                "§f%d. %s §f%s §7- §eLv.%d",
                i + 1,
                city.getColoredTypeName(),
                city.getName(),
                city.getLevel()
            ));
        }

        // 分隔
        player.sendMessage("§e§l按居民数排名:");
        List<City> topResidents = cityManager.getTopCitiesByResidents(5);
        for (int i = 0; i < topResidents.size(); i++) {
            City city = topResidents.get(i);
            player.sendMessage(String.format(
                "§f%d. %s §f%s §7- §f%d居民",
                i + 1,
                city.getColoredTypeName(),
                city.getName(),
                city.getResidentCount()
            ));
        }

        return true;
    }

    private boolean handleToggle(Player player, String setting) {
        City city = cityManager.getPlayerCity(player.getUniqueId());
        if (city == null) {
            player.sendMessage("§c你不在任何City中");
            return true;
        }

        if (!city.isMayor(player.getUniqueId())) {
            player.sendMessage("§c只有市长可以修改设置");
            return true;
        }

        switch (setting.toLowerCase()) {
            case "pvp" -> {
                city.setPvpEnabled(!city.isPvpEnabled());
                player.sendMessage("§aPvP已" + (city.isPvpEnabled() ? "启用" : "禁用"));
            }
            case "public" -> {
                city.setPublicSpawn(!city.isPublicSpawn());
                player.sendMessage("§a公开出生点已" + (city.isPublicSpawn() ? "启用" : "禁用"));
            }
            case "recruit" -> {
                city.setOpenRecruitment(!city.isOpenRecruitment());
                player.sendMessage("§a开放招募已" + (city.isOpenRecruitment() ? "启用" : "禁用"));
            }
            default -> {
                player.sendMessage("§c未知设置: " + setting);
                player.sendMessage("§7可用设置: pvp, public, recruit");
            }
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§7可用命令: create, delete, invite, kick, leave, info, list, levelup, deposit, withdraw, setspawn, spawn, top, toggle, accept, decline");
    }

    private boolean handleAccept(Player player) {
        if (cityManager.hasCity(player.getUniqueId())) {
            player.sendMessage("§c你已在城市中，无法接受邀请");
            return true;
        }

        CityManager.InviteAcceptResult result = cityManager.acceptInvite(player.getUniqueId());
        if (result.success()) {
            player.sendMessage("§a" + result.message());
            City city = cityManager.getCityByResident(player.getUniqueId());
            if (city != null) {
                player.sendMessage("§7城市: " + city.getName());
            }
        } else {
            player.sendMessage("§c" + result.message());
        }
        return true;
    }

    private boolean handleDecline(Player player) {
        if (cityManager.declineInvite(player.getUniqueId())) {
            player.sendMessage("§a已拒绝邀请");
        } else {
            player.sendMessage("§c没有待处理的邀请");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "delete", "invite", "kick", "leave",
                    "info", "list", "levelup", "deposit", "withdraw",
                    "setspawn", "spawn", "top", "toggle", "accept", "decline")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "invite", "kick" -> {
                    return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
                case "info" -> {
                    return cityManager.getAllCities().stream()
                        .map(City::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
                case "toggle" -> {
                    return Arrays.asList("pvp", "public", "recruit")
                        .stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
            }
        }

        return Collections.emptyList();
    }
}
