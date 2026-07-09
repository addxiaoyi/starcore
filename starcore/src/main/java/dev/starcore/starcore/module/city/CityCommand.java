package dev.starcore.starcore.module.city;
import java.util.Optional;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.city.model.City;
import dev.starcore.starcore.module.city.model.CityRank;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * 城市命令处理器
 * /city <子命令>
 */
public final class CityCommand implements CommandExecutor, TabCompleter {

    private final CityService cityService;
    private final NationService nationService;
    private final MessageService messages;

    public CityCommand(CityService cityService, NationService nationService, MessageService messages) {
        this.cityService = cityService;
        this.nationService = nationService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用此命令", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            return handleInfo(player);
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /city create <名称>", NamedTextColor.RED));
                    return true;
                }
                String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                return handleCreate(player, name);
            }
            case "delete" -> handleDelete(player);
            case "setspawn" -> handleSetSpawn(player);
            case "spawn" -> handleSpawn(player);
            case "list" -> handleList(player);
            case "info" -> {
                String cityName = args.length >= 2 ? args[1] : null;
                return handleInfo(player, cityName);
            }
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /city invite <玩家>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("玩家不在线", NamedTextColor.RED));
                    return true;
                }
                return handleInvite(player, target);
            }
            case "join" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /city join <城市名>", NamedTextColor.RED));
                    return true;
                }
                return handleJoin(player, args[1]);
            }
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /city kick <玩家>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("玩家不在线", NamedTextColor.RED));
                    return true;
                }
                return handleKick(player, target);
            }
            case "leave" -> handleLeave(player);
            case "promote" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /city promote <玩家>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("玩家不在线", NamedTextColor.RED));
                    return true;
                }
                return handlePromote(player, target);
            }
            case "demote" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /city demote <玩家>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("玩家不在线", NamedTextColor.RED));
                    return true;
                }
                return handleDemote(player, target);
            }
            case "transfer" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /city transfer <玩家>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("玩家不在线", NamedTextColor.RED));
                    return true;
                }
                return handleTransfer(player, target);
            }
            case "deposit" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /city deposit <金额>", NamedTextColor.RED));
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[1]);
                    return handleDeposit(player, amount);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("无效的金额", NamedTextColor.RED));
                    return true;
                }
            }
            case "withdraw" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("用法: /city withdraw <金额>", NamedTextColor.RED));
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[1]);
                    return handleWithdraw(player, amount);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("无效的金额", NamedTextColor.RED));
                    return true;
                }
            }
            case "announcement", "ann" -> {
                String[] announcementArgs = args.length >= 2
                    ? Arrays.copyOfRange(args, 1, args.length)
                    : new String[0];
                return handleAnnouncement(player, announcementArgs);
            }
            case "accept" -> handleAccept(player);
            case "decline" -> handleDecline(player);
            case "top" -> handleTop(player);
            case "help" -> sendHelp(player);
            default -> {
                player.sendMessage(Component.text("未知子命令: " + subCommand, NamedTextColor.RED));
                sendHelp(player);
            }
        }
        return true;
    }

    private boolean handleCreate(Player player, String name) {
        // 检查玩家是否在国家中
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你必须先加入一个国家才能创建城市", NamedTextColor.RED));
            return true;
        }

        Nation nation = nationOpt.get();
        if (!nation.founderId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("只有国家创建者才能创建城市", NamedTextColor.RED));
            return true;
        }

        // 创建城市
        CityService.CreateCityResult result = cityService.createCity(
            nation.id(),
            name,
            player.getUniqueId(),
            player.getLocation()
        );

        if (result.success()) {
            player.sendMessage(Component.text("城市创建成功！", NamedTextColor.GREEN));
            player.sendMessage(Component.text("城市名称: " + result.city().name(), NamedTextColor.GRAY));
            player.sendMessage(Component.text("所属国家: " + nation.name(), NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("创建失败: " + result.message(), NamedTextColor.RED));
        }

        return true;
    }

    private boolean handleDelete(Player player) {
        Optional<City> cityOpt = cityService.getPlayerCity(player.getUniqueId());
        if (cityOpt.isEmpty()) {
            player.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
            return true;
        }

        City city = cityOpt.get();
        if (!city.isMayor(player.getUniqueId())) {
            player.sendMessage(Component.text("只有市长可以删除城市", NamedTextColor.RED));
            return true;
        }

        // 通知所有在线居民
        for (UUID residentId : city.residents().keySet()) {
            Player resident = Bukkit.getPlayer(residentId);
            if (resident != null && resident.isOnline()) {
                resident.sendMessage(Component.text("城市 " + city.name() + " 已被删除", NamedTextColor.RED));
            }
        }

        cityService.deleteCity(city.id());
        player.sendMessage(Component.text("城市已删除", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleSetSpawn(Player player) {
        Optional<City> cityOpt = cityService.getPlayerCity(player.getUniqueId());
        if (cityOpt.isEmpty()) {
            player.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
            return true;
        }

        City city = cityOpt.get();
        if (!city.canSetSpawn(player.getUniqueId())) {
            player.sendMessage(Component.text("你没有权限设置出生点", NamedTextColor.RED));
            return true;
        }

        if (cityService.setSpawn(city.id(), player.getLocation())) {
            player.sendMessage(Component.text("城市出生点已设置", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("设置出生点失败", NamedTextColor.RED));
        }

        return true;
    }

    private boolean handleSpawn(Player player) {
        Optional<City> cityOpt = cityService.getPlayerCity(player.getUniqueId());
        if (cityOpt.isEmpty()) {
            player.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
            return true;
        }

        City city = cityOpt.get();
        if (city.spawnChunk() == null) {
            player.sendMessage(Component.text("该城市尚未设置出生点", NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text("正在传送到城市出生点...", NamedTextColor.YELLOW));
        if (!cityService.teleportToSpawn(player, city.id())) {
            player.sendMessage(Component.text("传送失败", NamedTextColor.RED));
        }

        return true;
    }

    private boolean handleList(Player player) {
        Collection<City> cities = cityService.getAllCities();

        if (cities.isEmpty()) {
            player.sendMessage(Component.text("当前没有任何城市", NamedTextColor.GRAY));
            return true;
        }

        player.sendMessage(Component.text("==== 城市列表 ====", NamedTextColor.GOLD));
        for (City city : cities) {
            Optional<Nation> nation = nationService.nationById(city.nationId());
            String nationName = nation.map(Nation::name).orElse("未知");
            player.sendMessage(Component.text()
                .append(Component.text("- ", NamedTextColor.GRAY))
                .append(Component.text(city.name(), NamedTextColor.WHITE))
                .append(Component.text(" [" + nationName + "] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(city.residentCount() + " 居民", NamedTextColor.GRAY))
            );
        }
        player.sendMessage(Component.text("总计: " + cities.size() + " 个城市", NamedTextColor.GRAY));
        return true;
    }

    private boolean handleInfo(Player player) {
        Optional<City> cityOpt = cityService.getPlayerCity(player.getUniqueId());
        if (cityOpt.isEmpty()) {
            player.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
            player.sendMessage(Component.text("使用 /city info <名称> 查看其他城市", NamedTextColor.GRAY));
            return true;
        }
        return showCityInfo(player, cityOpt.get());
    }

    private boolean handleInfo(Player player, String cityName) {
        if (cityName == null) {
            return handleInfo(player);
        }

        Optional<City> cityOpt = cityService.getCityByName(cityName);
        if (cityOpt.isEmpty()) {
            player.sendMessage(Component.text("找不到城市: " + cityName, NamedTextColor.RED));
            return true;
        }

        return showCityInfo(player, cityOpt.get());
    }

    private boolean showCityInfo(Player player, City city) {
        Optional<Nation> nation = nationService.nationById(city.nationId());
        String nationName = nation.map(Nation::name).orElse("未知");

        player.sendMessage(Component.text("==== 城市信息 ====", NamedTextColor.GOLD));
        player.sendMessage(Component.text("名称: " + city.name(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("所属国家: " + nationName, NamedTextColor.GRAY));
        player.sendMessage(Component.text("等级: " + city.level() + " (经验: " + city.experience() + "/" + city.getLevelUpExperience() + ")", NamedTextColor.GRAY));
        player.sendMessage(Component.text("居民数: " + city.residentCount() + "/" + city.getMaxResidents(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("国库: " + String.format("%.2f", city.treasury()) + " 金币", NamedTextColor.GOLD));
        player.sendMessage(Component.text("领土: " + city.claimCount() + " 块", NamedTextColor.GRAY));
        player.sendMessage(Component.text("出生点: " + (city.spawnChunk() != null ? "已设置" : "未设置"), NamedTextColor.GRAY));

        // 显示公告
        String announcement = city.announcement();
        if (announcement != null && !announcement.isEmpty()) {
            player.sendMessage(Component.text("公告: " + announcement, NamedTextColor.YELLOW));
        }

        // 显示成员列表（仅市长可见完整列表）
        if (city.isMayor(player.getUniqueId())) {
            player.sendMessage(Component.text("成员列表:", NamedTextColor.GRAY));
            for (Map.Entry<UUID, CityRank> entry : city.residents().entrySet()) {
                UUID playerId = entry.getKey();
                CityRank rank = entry.getValue();
                String playerName = Bukkit.getOfflinePlayer(playerId).getName();
                if (playerName == null) {
                    playerName = playerId.toString().substring(0, 8);
                }
                player.sendMessage(Component.text()
                    .append(Component.text("  - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(playerName, NamedTextColor.WHITE))
                    .append(Component.text(" [" + rank.displayName() + "]", NamedTextColor.GRAY))
                );
            }
        }

        return true;
    }

    private boolean handleInvite(Player mayor, Player target) {
        Optional<City> cityOpt = cityService.getPlayerCity(mayor.getUniqueId());
        if (cityOpt.isEmpty()) {
            mayor.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
            return true;
        }

        City city = cityOpt.get();
        if (!city.canInvite(mayor.getUniqueId())) {
            mayor.sendMessage(Component.text("你没有权限邀请成员", NamedTextColor.RED));
            return true;
        }

        // 检查目标是否已在某城市
        if (cityService.getPlayerCity(target.getUniqueId()).isPresent()) {
            mayor.sendMessage(Component.text("该玩家已在其他城市中", NamedTextColor.RED));
            return true;
        }

        // 检查目标是否在国家中
        Optional<Nation> targetNation = nationService.nationOf(target.getUniqueId());
        if (targetNation.isEmpty() || !targetNation.get().id().equals(city.nationId())) {
            mayor.sendMessage(Component.text("目标玩家必须在同一国家中", NamedTextColor.RED));
            return true;
        }

        // 发送邀请（新的邀请-接受机制）
        CityService.InviteResult result = cityService.sendInvite(city.id(), mayor.getUniqueId(), target.getUniqueId());
        if (result.success()) {
            mayor.sendMessage(Component.text("已向 " + target.getName() + " 发送邀请", NamedTextColor.GREEN));
            target.sendMessage(Component.text("你收到了来自 " + city.name() + " 的邀请", NamedTextColor.GREEN));
            target.sendMessage(Component.text("输入 /city accept 接受邀请 或 /city decline 拒绝", NamedTextColor.YELLOW));
        } else {
            mayor.sendMessage(Component.text("邀请失败: " + result.message(), NamedTextColor.RED));
        }

        return true;
    }

    private boolean handleAccept(Player player) {
        CityService.InviteResult result = cityService.acceptInvite(player.getUniqueId());
        if (result.success()) {
            player.sendMessage(Component.text("你已接受邀请，加入了城市！", NamedTextColor.GREEN));

            // 通知城市成员
            Optional<City> cityOpt = cityService.getPlayerCity(player.getUniqueId());
            cityOpt.ifPresent(city -> {
                for (UUID residentId : city.getResidentIds()) {
                    if (!residentId.equals(player.getUniqueId())) {
                        Player resident = Bukkit.getPlayer(residentId);
                        if (resident != null && resident.isOnline()) {
                            resident.sendMessage(Component.text("欢迎 " + player.getName() + " 加入了城市！", NamedTextColor.GREEN));
                        }
                    }
                }
            });
        } else {
            player.sendMessage(Component.text("接受邀请失败: " + result.message(), NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleDecline(Player player) {
        if (cityService.declineInvite(player.getUniqueId())) {
            player.sendMessage(Component.text("你已拒绝邀请", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("没有待处理的邀请", NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleJoin(Player player, String cityName) {
        // 检查玩家是否已在城市中
        if (cityService.getPlayerCity(player.getUniqueId()).isPresent()) {
            player.sendMessage(Component.text("你已在某城市中，请先离开", NamedTextColor.RED));
            return true;
        }

        // 检查玩家是否在国家中
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你必须先加入一个国家", NamedTextColor.RED));
            return true;
        }

        Optional<City> cityOpt = cityService.getCityByName(cityName);
        if (cityOpt.isEmpty()) {
            player.sendMessage(Component.text("找不到城市: " + cityName, NamedTextColor.RED));
            return true;
        }

        City city = cityOpt.get();

        // 检查是否同一国家
        if (!city.nationId().equals(nationOpt.get().id())) {
            player.sendMessage(Component.text("该城市属于其他国家，无法加入", NamedTextColor.RED));
            return true;
        }

        if (cityService.addResident(city.id(), player.getUniqueId(), CityRank.RESIDENT)) {
            player.sendMessage(Component.text("你已加入城市 " + city.name(), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("加入失败", NamedTextColor.RED));
        }

        return true;
    }

    private boolean handleKick(Player mayor, Player target) {
        Optional<City> cityOpt = cityService.getPlayerCity(mayor.getUniqueId());
        if (cityOpt.isEmpty()) {
            mayor.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
            return true;
        }

        City city = cityOpt.get();
        if (!city.canKick(mayor.getUniqueId())) {
            mayor.sendMessage(Component.text("你没有权限踢出成员", NamedTextColor.RED));
            return true;
        }

        if (!city.isResident(target.getUniqueId())) {
            mayor.sendMessage(Component.text("该玩家不在你的城市中", NamedTextColor.RED));
            return true;
        }

        if (cityService.removeResident(city.id(), target.getUniqueId())) {
            mayor.sendMessage(Component.text("已将 " + target.getName() + " 踢出城市", NamedTextColor.GREEN));
            target.sendMessage(Component.text("你已被踢出城市 " + city.name(), NamedTextColor.RED));
        } else {
            mayor.sendMessage(Component.text("踢出失败（可能是市长）", NamedTextColor.RED));
        }

        return true;
    }

    private boolean handleLeave(Player player) {
        Optional<City> cityOpt = cityService.getPlayerCity(player.getUniqueId());
        if (cityOpt.isEmpty()) {
            player.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
            return true;
        }

        City city = cityOpt.get();
        if (city.isMayor(player.getUniqueId())) {
            if (city.residentCount() > 1) {
                player.sendMessage(Component.text("作为市长，你必须先转让职位或删除城市", NamedTextColor.RED));
                player.sendMessage(Component.text("使用 /city delete 删除城市", NamedTextColor.GRAY));
                return true;
            }
        }

        if (cityService.leaveCity(player.getUniqueId())) {
            player.sendMessage(Component.text("你已离开城市 " + city.name(), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("离开失败", NamedTextColor.RED));
        }

        return true;
    }

    private boolean handlePromote(Player mayor, Player target) {
        Optional<City> cityOpt = cityService.getPlayerCity(mayor.getUniqueId());
        if (cityOpt.isEmpty()) {
            mayor.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
            return true;
        }

        City city = cityOpt.get();
        if (!city.isMayor(mayor.getUniqueId())) {
            mayor.sendMessage(Component.text("只有市长可以任命官员", NamedTextColor.RED));
            return true;
        }

        if (!city.isResident(target.getUniqueId())) {
            mayor.sendMessage(Component.text("该玩家不在你的城市中", NamedTextColor.RED));
            return true;
        }

        if (city.isOfficer(target.getUniqueId())) {
            mayor.sendMessage(Component.text("该玩家已是官员", NamedTextColor.YELLOW));
            return true;
        }

        if (cityService.appointOfficer(mayor.getUniqueId(), target.getUniqueId())) {
            mayor.sendMessage(Component.text("已任命 " + target.getName() + " 为官员", NamedTextColor.GREEN));
            target.sendMessage(Component.text("你已被任命为城市官员", NamedTextColor.GREEN));
        } else {
            mayor.sendMessage(Component.text("任命失败", NamedTextColor.RED));
        }

        return true;
    }

    private boolean handleDemote(Player mayor, Player target) {
        Optional<City> cityOpt = cityService.getPlayerCity(mayor.getUniqueId());
        if (cityOpt.isEmpty()) {
            mayor.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
            return true;
        }

        City city = cityOpt.get();
        if (!city.isMayor(mayor.getUniqueId())) {
            mayor.sendMessage(Component.text("只有市长可以移除官员", NamedTextColor.RED));
            return true;
        }

        if (!city.isOfficer(target.getUniqueId()) || city.isMayor(target.getUniqueId())) {
            mayor.sendMessage(Component.text("该玩家不是官员", NamedTextColor.YELLOW));
            return true;
        }

        if (cityService.removeOfficer(mayor.getUniqueId(), target.getUniqueId())) {
            mayor.sendMessage(Component.text("已将 " + target.getName() + " 降为普通居民", NamedTextColor.GREEN));
            target.sendMessage(Component.text("你已被降为普通居民", NamedTextColor.YELLOW));
        } else {
            mayor.sendMessage(Component.text("降级失败", NamedTextColor.RED));
        }

        return true;
    }

    private boolean handleTransfer(Player currentMayor, Player newMayor) {
        Optional<City> cityOpt = cityService.getPlayerCity(currentMayor.getUniqueId());
        if (cityOpt.isEmpty()) {
            currentMayor.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
            return true;
        }

        City city = cityOpt.get();
        if (!city.isMayor(currentMayor.getUniqueId())) {
            currentMayor.sendMessage(Component.text("只有市长可以转让职位", NamedTextColor.RED));
            return true;
        }

        if (!city.isResident(newMayor.getUniqueId())) {
            currentMayor.sendMessage(Component.text("该玩家不在你的城市中", NamedTextColor.RED));
            return true;
        }

        if (cityService.transferMayor(currentMayor.getUniqueId(), newMayor.getUniqueId())) {
            currentMayor.sendMessage(Component.text("已将市长职位转让给 " + newMayor.getName(), NamedTextColor.GREEN));
            newMayor.sendMessage(Component.text("你已成为城市 " + city.name() + " 的新市长", NamedTextColor.GREEN));
        } else {
            currentMayor.sendMessage(Component.text("转让失败", NamedTextColor.RED));
        }

        return true;
    }

    private boolean handleDeposit(Player player, double amount) {
        Optional<City> cityOpt = cityService.getPlayerCity(player.getUniqueId());
        if (cityOpt.isEmpty()) {
            player.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
            return true;
        }

        if (amount <= 0) {
            player.sendMessage(Component.text("金额必须大于0", NamedTextColor.RED));
            return true;
        }

        City city = cityOpt.get();
        if (cityService.deposit(city.id(), amount)) {
            player.sendMessage(Component.text(String.format("已存入 %.2f 金币到城市国库", amount), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("存款失败", NamedTextColor.RED));
        }

        return true;
    }

    private boolean handleWithdraw(Player player, double amount) {
        Optional<City> cityOpt = cityService.getPlayerCity(player.getUniqueId());
        if (cityOpt.isEmpty()) {
            player.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
            return true;
        }

        City city = cityOpt.get();
        if (!city.isMayor(player.getUniqueId())) {
            player.sendMessage(Component.text("只有市长可以取款", NamedTextColor.RED));
            return true;
        }

        if (amount <= 0) {
            player.sendMessage(Component.text("金额必须大于0", NamedTextColor.RED));
            return true;
        }

        if (cityService.withdraw(city.id(), amount)) {
            player.sendMessage(Component.text(String.format("已取出 %.2f 金币", amount), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("余额不足", NamedTextColor.RED));
        }

        return true;
    }

    private boolean handleAnnouncement(Player player, String[] args) {
        Optional<City> cityOpt = cityService.getPlayerCity(player.getUniqueId());
        if (cityOpt.isEmpty()) {
            player.sendMessage(Component.text("你不在任何城市中", NamedTextColor.RED));
            return true;
        }

        City city = cityOpt.get();
        if (!city.isMayor(player.getUniqueId())) {
            player.sendMessage(Component.text("只有市长可以设置公告", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            // 查看公告
            String announcement = cityService.getAnnouncement(city.id());
            if (announcement != null && !announcement.isEmpty()) {
                player.sendMessage(Component.text("当前公告: " + announcement, NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text("该城市暂无公告", NamedTextColor.GRAY));
            }
            player.sendMessage(Component.text("用法: /city announcement <内容> - 设置公告", NamedTextColor.GRAY));
            player.sendMessage(Component.text("用法: /city announcement clear - 清除公告", NamedTextColor.GRAY));
        } else if (args.length == 1 && args[0].equalsIgnoreCase("clear")) {
            // 清除公告
            if (cityService.setAnnouncement(city.id(), player.getUniqueId(), null)) {
                player.sendMessage(Component.text("公告已清除", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("清除公告失败", NamedTextColor.RED));
            }
        } else {
            // 设置公告
            String announcement = String.join(" ", args);
            if (announcement.length() > 100) {
                player.sendMessage(Component.text("公告内容过长（最大100字符）", NamedTextColor.RED));
                return true;
            }
            if (cityService.setAnnouncement(city.id(), player.getUniqueId(), announcement)) {
                player.sendMessage(Component.text("公告已设置为: " + announcement, NamedTextColor.GREEN));

                // 通知所有在线居民
                for (UUID residentId : city.getResidentIds()) {
                    if (!residentId.equals(player.getUniqueId())) {
                        Player resident = Bukkit.getPlayer(residentId);
                        if (resident != null && resident.isOnline()) {
                            resident.sendMessage(Component.text("城市公告已更新: " + announcement, NamedTextColor.YELLOW));
                        }
                    }
                }
            } else {
                player.sendMessage(Component.text("设置公告失败", NamedTextColor.RED));
            }
        }

        return true;
    }

    private boolean handleTop(Player player) {
        player.sendMessage(Component.text("==== 城市排行榜 ====", NamedTextColor.GOLD));
        player.sendMessage(Component.text("按居民数排名:", NamedTextColor.YELLOW));

        List<City> topByResidents = cityService.getTopCitiesByResidents(5);
        for (int i = 0; i < topByResidents.size(); i++) {
            City city = topByResidents.get(i);
            Optional<Nation> nation = nationService.nationById(city.nationId());
            String nationName = nation.map(Nation::name).orElse("未知");
            player.sendMessage(Component.text()
                .append(Component.text((i + 1) + ". ", NamedTextColor.GRAY))
                .append(Component.text(city.name(), NamedTextColor.WHITE))
                .append(Component.text(" [" + nationName + "] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(city.residentCount() + " 居民", NamedTextColor.GOLD))
            );
        }

        player.sendMessage(Component.text("按国库排名:", NamedTextColor.YELLOW));

        List<City> topByTreasury = cityService.getTopCitiesByTreasury(5);
        for (int i = 0; i < topByTreasury.size(); i++) {
            City city = topByTreasury.get(i);
            Optional<Nation> nation = nationService.nationById(city.nationId());
            String nationName = nation.map(Nation::name).orElse("未知");
            player.sendMessage(Component.text()
                .append(Component.text((i + 1) + ". ", NamedTextColor.GRAY))
                .append(Component.text(city.name(), NamedTextColor.WHITE))
                .append(Component.text(" [" + nationName + "] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format("%.2f", city.treasury()) + " 金币", NamedTextColor.GOLD))
            );
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("==== 城市命令帮助 ====", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/city create <名称> - 创建城市", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/city delete - 删除城市（仅市长）", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/city info [名称] - 查看城市信息", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/city list - 列出所有城市", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/city setspawn - 设置城市出生点", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/city spawn - 传送到城市出生点", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/city invite <玩家> - 邀请玩家（仅官员）", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/city accept - 接受邀请", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/city decline - 拒绝邀请", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/city join <城市名> - 加入城市", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/city kick <玩家> - 踢出玩家（仅市长）", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/city leave - 离开城市", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/city promote <玩家> - 任命官员（仅市长）", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/city demote <玩家> - 降级官员（仅市长）", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/city transfer <玩家> - 转让市长（仅市长）", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/city deposit <金额> - 存款到城市国库", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/city withdraw <金额> - 从城市国库取款（仅市长）", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/city announcement [内容|clear] - 设置/查看公告", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/city top - 查看排行榜", NamedTextColor.GRAY));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("create", "delete", "setspawn", "spawn", "list", "info",
                    "invite", "accept", "decline", "join", "kick", "leave", "promote", "demote",
                    "transfer", "deposit", "withdraw", "announcement", "top", "help")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "info", "join" -> {
                    return cityService.getAllCities().stream()
                        .map(City::name)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
                case "invite", "kick", "promote", "demote", "transfer" -> {
                    return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
                case "announcement" -> {
                    return List.of("clear").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
            }
        }

        return List.of();
    }
}
