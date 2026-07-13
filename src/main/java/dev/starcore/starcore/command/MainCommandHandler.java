package dev.starcore.starcore.command;

import dev.starcore.starcore.social.friend.FriendService;
import dev.starcore.starcore.social.gui.SocialMenuListener;
import dev.starcore.starcore.util.ColorCodes;
import dev.starcore.starcore.social.guild.GuildService;
import dev.starcore.starcore.social.party.PartyService;
import dev.starcore.starcore.social.chat.PrivateMessageService;
import dev.starcore.starcore.pvp.duel.DuelService;
import dev.starcore.starcore.pvp.stats.PvPStatsService;
import dev.starcore.starcore.moderation.mute.MuteService;
import dev.starcore.starcore.moderation.ban.BanService;
import dev.starcore.starcore.moderation.jail.JailService;
import dev.starcore.starcore.moderation.kick.KickService;
import dev.starcore.starcore.moderation.vanish.VanishService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 主命令处理器
 * 处理所有STARCORE命令
 */
// 设计决策：MainCommandHandler 和 StarCoreCommand 在命令分发上有部分重叠
// 考虑合并为单一路由器（>30 LOC），当前保持分离以支持模块化加载
public final class MainCommandHandler implements CommandExecutor, TabCompleter {
    private final FriendService friendService;
    private final SocialMenuListener socialMenuListener;
    private final GuildService guildService;
    private final PartyService partyService;
    private final PrivateMessageService pmService;
    private final DuelService duelService;
    private final PvPStatsService statsService;
    private final MuteService muteService;
    private final BanService banService;
    private final JailService jailService;
    private final KickService kickService;
    private final VanishService vanishService;

    public MainCommandHandler(FriendService friendService, SocialMenuListener socialMenuListener,
                             GuildService guildService, PartyService partyService,
                             PrivateMessageService pmService, DuelService duelService,
                             PvPStatsService statsService, MuteService muteService,
                             BanService banService, JailService jailService,
                             KickService kickService, VanishService vanishService) {
        this.friendService = friendService;
        this.socialMenuListener = socialMenuListener;
        this.guildService = guildService;
        this.partyService = partyService;
        this.pmService = pmService;
        this.duelService = duelService;
        this.statsService = statsService;
        this.muteService = muteService;
        this.banService = banService;
        this.jailService = jailService;
        this.kickService = kickService;
        this.vanishService = vanishService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        return switch (cmd) {
            case "menu" -> handleMenu(sender);
            case "friend" -> handleFriend(sender, args);
            case "guild" -> handleGuild(sender, args);
            case "party" -> handleParty(sender, args);
            case "msg", "tell", "w" -> handleMessage(sender, args);
            case "r", "reply" -> handleReply(sender, args);
            case "duel" -> handleDuel(sender, args);
            case "pvpstats" -> handlePvPStats(sender, args);
            case "pay" -> {
                // audit C-002: /pay 由 EconomyCommandHandler 注册并处理；此 handler 不应拦截。
                // 仅当 EconomyCommandHandler 未注册该命令时此分支被触达，提示玩家真实入口。
                sender.sendMessage(ColorCodes.ERROR + "/pay 命令由经济模块独立处理。用法: " + ColorCodes.HIGHLIGHT + "/pay <玩家> <金额>");
                yield true;
            }
            case "mute" -> handleMute(sender, args);
            case "unmute" -> handleUnmute(sender, args);
            case "ban" -> handleBan(sender, args);
            case "unban" -> handleUnban(sender, args);
            case "jail" -> handleJail(sender, args);
            case "unjail" -> handleUnjail(sender, args);
            case "kick" -> handleKick(sender, args);
            case "vanish", "v" -> handleVanish(sender);
            default -> {
                // audit C-003: 未知命令返回 true 并提示，避免触发 Bukkit 空 usage
                sender.sendMessage(ColorCodes.ERROR + "未知命令，请使用 " + ColorCodes.HIGHLIGHT + "/menu " + ColorCodes.ERROR + "或 /help 查看可用命令");
                yield true;
            }
        };
    }

    // audit C-001: 实现各子命令的 Tab 补全
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return switch (cmd) {
                case "friend" -> prefixMatches(List.of("add", "remove", "list", "accept", "deny"), prefix);
                case "guild" -> prefixMatches(List.of("create", "disband", "invite", "kick", "leave", "info", "list"), prefix);
                case "party" -> prefixMatches(List.of("create", "disband", "invite", "kick", "leave", "list", "accept"), prefix);
                case "duel" -> prefixMatches(List.of("accept", "deny", "forfeit"), prefix);
                case "pvpstats" -> List.of();
                case "mute", "ban", "jail" -> prefixMatches(List.of("30s", "5m", "1h", "7d", "perm"), "");
                case "unmute", "unban", "unjail", "kick" -> List.of();
                case "msg", "tell", "w", "r", "reply" -> onlinePlayerNames();
                case "pay" -> onlinePlayerNames();
                default -> List.of();
            };
        }
        if (args.length == 2) {
            return switch (cmd) {
                case "mute", "ban", "jail" -> prefixMatches(List.of("30s", "5m", "1h", "7d", "perm"), args[1]);
                default -> onlinePlayerNames();
            };
        }
        return List.of();
    }

    private List<String> prefixMatches(List<String> options, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase().startsWith(p)) result.add(o);
        }
        return result;
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    private boolean handleMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        // 打开社交菜单 GUI
        if (socialMenuListener != null) {
            socialMenuListener.openMenu(player);
        } else {
            // 如果 GUI 不可用，显示文本菜单作为后备
            player.sendMessage("§6§l========== 星核菜单 ==========");
            // 分隔
            player.sendMessage("§e社交系统:");
            player.sendMessage("  §f/friend §7- 好友管理");
            player.sendMessage("  §f/guild §7- 公会管理");
            player.sendMessage("  §f/party §7- 派对管理");
            player.sendMessage("  §f/msg <玩家> <消息> §7- 私信");
            player.sendMessage("  §f/r <消息> §7- 回复私信");
            // 分隔
            player.sendMessage("§e游戏系统:");
            player.sendMessage("  §f/duel <玩家> §7- 发起决斗");
            player.sendMessage("  §f/pvpstats §7- 查看PVP统计");
            player.sendMessage("  §f/pay <玩家> <金额> §7- 转账");
            // 分隔
            player.sendMessage("§e管理功能:");
            player.sendMessage("  §f/mute <玩家> <时长> <原因> §7- 禁言");
            player.sendMessage("  §f/ban <玩家> <时长> <原因> §7- 封禁");
            player.sendMessage("  §f/jail <玩家> <时长> <原因> §7- 监禁");
            player.sendMessage("  §f/kick <玩家> <原因> §7- 踢出");
            player.sendMessage("  §f/vanish §7- 隐身");
            // 分隔
            player.sendMessage("§6§l============================");
        }

        return true;
    }

    private boolean handleFriend(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§e用法: /friend <add|remove|list|accept|deny> [玩家]");
            return true;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "add" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /friend add <玩家>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("§c玩家不在线");
                    return true;
                }
                try {
                    friendService.sendFriendRequest(player.getUniqueId(), target.getUniqueId());
                    player.sendMessage("§a已发送好友请求给 " + target.getName());
                    target.sendMessage("§e" + player.getName() + " §a想要添加你为好友");
                    target.sendMessage("§7使用 §e/friend accept " + player.getName() + " §7接受");
                } catch (Exception e) {
                    // audit C-007: 区分业务异常与系统异常，message 可能为 null
                    player.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "发送好友请求失败"));
                }
            }
            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /friend remove <玩家>");
                    return true;
                }
                // audit C-008: 改用 OfflinePlayer，支持对离线好友删除
                org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (friendService.removeFriend(player.getUniqueId(), target.getUniqueId())) {
                    player.sendMessage("§c已删除好友 " + args[1]);
                } else {
                    player.sendMessage("§c你们不是好友");
                }
            }
            case "list" -> {
                var friends = friendService.getFriends(player.getUniqueId());
                player.sendMessage("§b[星链] §f好友列表 (" + friends.size() + "):");
                for (var friendId : friends) {
                    Player friend = Bukkit.getPlayer(friendId);
                    String name = friend != null ? friend.getName() : friendId.toString();
                    String status = friend != null && friend.isOnline() ? "§a在线" : "§7离线";
                    player.sendMessage("  §f" + name + " " + status);
                }
            }
            case "accept" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /friend accept <玩家>");
                    return true;
                }
                // audit C-009: 改用 OfflinePlayer，允许请求发起者当前离线时仍可接受
                org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                try {
                    friendService.acceptFriendRequest(player.getUniqueId(), target.getUniqueId());
                    player.sendMessage("§a已接受 " + args[1] + " 的好友请求");
                    Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
                    if (onlineTarget != null) {
                        onlineTarget.sendMessage("§a" + player.getName() + " 接受了你的好友请求");
                    }
                } catch (Exception e) {
                    // audit C-007: 区分业务异常与系统异常，message 可能为 null
                    player.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "接受好友请求失败"));
                }
            }
            case "deny" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /friend deny <玩家>");
                    return true;
                }
                // audit C-009: 改用 OfflinePlayer，允许请求发起者离线时仍可拒绝
                org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (friendService.rejectFriendRequest(player.getUniqueId(), target.getUniqueId())) {
                    player.sendMessage("§c已拒绝 " + args[1] + " 的好友请求");
                } else {
                    player.sendMessage("§c没有来自该玩家的好友请求");
                }
            }
            default -> player.sendMessage("§c未知操作: " + action);
        }

        return true;
    }

    private boolean handleGuild(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§6[星座] §e用法:");
            player.sendMessage("  §e/guild create <名称> <标签> §7- 创建公会");
            player.sendMessage("  §e/guild disband §7- 解散公会");
            player.sendMessage("  §e/guild invite <玩家> §7- 邀请玩家");
            player.sendMessage("  §e/guild kick <玩家> §7- 踢出成员");
            player.sendMessage("  §e/guild leave §7- 离开公会");
            player.sendMessage("  §e/guild info §7- 查看公会信息");
            player.sendMessage("  §e/guild list §7- 查看成员列表");
            return true;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "create" -> {
                if (args.length < 3) {
                    player.sendMessage("§c用法: /guild create <名称 1-16 字符> <标签 2-4 字符>");
                    return true;
                }
                // audit C-008: 命令层格式预校验
                String gName = args[1];
                String gTag = args[2];
                if (gName.length() < 1 || gName.length() > 16) {
                    player.sendMessage("§c公会名称长度需在 1-16 字符之间");
                    return true;
                }
                if (gTag.length() < 2 || gTag.length() > 4) {
                    player.sendMessage("§c公会标签长度需在 2-4 字符之间");
                    return true;
                }
                if (!gName.matches("[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+")) {
                    player.sendMessage("§c公会名称仅允许字母、数字、下划线、连字符或中文");
                    return true;
                }
                try {
                    var guild = guildService.createGuild(player.getUniqueId(), gName, gTag);
                    player.sendMessage("§a成功创建公会: §6" + guild.getName() + " §7[" + guild.getTag() + "]");
                } catch (Exception e) {
                    // audit C-007: 区分业务异常与系统异常
                    player.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "创建公会失败，请稍后重试"));
                }
            }
            case "disband" -> {
                try {
                    var guild = guildService.getPlayerGuild(player.getUniqueId());
                    if (guild == null) {
                        player.sendMessage("§c你不在任何公会中");
                        return true;
                    }
                    guildService.disbandGuild(guild.getId(), player.getUniqueId());
                    player.sendMessage("§c公会已解散");
                } catch (Exception e) {
                    // audit C-007: 区分业务异常与系统异常，message 可能为 null
                    player.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "解散公会失败"));
                }
            }
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /guild invite <玩家>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("§c玩家不在线");
                    return true;
                }
                try {
                    var guild = guildService.getPlayerGuild(player.getUniqueId());
                    if (guild == null) {
                        player.sendMessage("§c你不在任何公会中");
                        return true;
                    }
                    guildService.inviteMember(guild.getId(), player.getUniqueId(), target.getUniqueId());
                    player.sendMessage("§a已邀请 " + target.getName() + " 加入公会");
                    target.sendMessage("§6[星座] §e" + player.getName() + " §a邀请你加入公会 §6" + guild.getName());
                } catch (Exception e) {
                    // audit C-007: 区分业务异常与系统异常，message 可能为 null
                    player.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "邀请失败"));
                }
            }
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /guild kick <玩家>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("§c玩家不存在");
                    return true;
                }
                try {
                    var guild = guildService.getPlayerGuild(player.getUniqueId());
                    if (guild == null) {
                        player.sendMessage("§c你不在任何公会中");
                        return true;
                    }
                    guildService.kickMember(guild.getId(), player.getUniqueId(), target.getUniqueId());
                    player.sendMessage("§c已踢出 " + target.getName());
                    target.sendMessage("§c你已被踢出公会");
                } catch (Exception e) {
                    // audit C-007: 区分业务异常与系统异常，message 可能为 null
                    player.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "踢出成员失败"));
                }
            }
            case "leave" -> {
                try {
                    guildService.leaveGuild(player.getUniqueId());
                    player.sendMessage("§c你已离开公会");
                } catch (Exception e) {
                    // audit C-007: 区分业务异常与系统异常，message 可能为 null
                    player.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "离开公会失败"));
                }
            }
            case "info" -> {
                var guild = guildService.getPlayerGuild(player.getUniqueId());
                if (guild == null) {
                    player.sendMessage("§c你不在任何公会中");
                    return true;
                }
                player.sendMessage("§6========== 公会信息 ==========");
                player.sendMessage("§e名称: §f" + guild.getName() + " §7[" + guild.getTag() + "]");
                player.sendMessage("§e等级: §f" + guild.getLevel());
                player.sendMessage("§e经验: §f" + guild.getExperience() + "/" + guild.getRequiredExperience());
                player.sendMessage("§e成员: §f" + guild.getMemberCount());
                player.sendMessage("§e银行: §f" + guild.getBalance() + " 星尘");
            }
            case "list" -> {
                var guild = guildService.getPlayerGuild(player.getUniqueId());
                if (guild == null) {
                    player.sendMessage("§c你不在任何公会中");
                    return true;
                }
                player.sendMessage("§6[星座] §e成员列表:");
                for (var memberId : guild.getMembers()) {
                    Player member = Bukkit.getPlayer(memberId);
                    String name = member != null ? member.getName() : memberId.toString();
                    String status = member != null && member.isOnline() ? "§a在线" : "§7离线";
                    var role = guild.getMemberRole(memberId);
                    player.sendMessage("  §f" + name + " §7[" + role.getDisplayName() + "] " + status);
                }
            }
            default -> player.sendMessage("§c未知操作: " + action);
        }

        return true;
    }

    private boolean handleParty(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§d[派对] §e用法:");
            player.sendMessage("  §e/party create §7- 创建派对");
            player.sendMessage("  §e/party disband §7- 解散派对");
            player.sendMessage("  §e/party invite <玩家> §7- 邀请玩家");
            player.sendMessage("  §e/party kick <玩家> §7- 踢出成员");
            player.sendMessage("  §e/party leave §7- 离开派对");
            player.sendMessage("  §e/party list §7- 查看成员");
            return true;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "create" -> {
                try {
                    var party = partyService.createParty(player.getUniqueId());
                    player.sendMessage("§a成功创建派对");
                } catch (Exception e) {
                    // audit C-007: 区分业务异常与系统异常，message 可能为 null
                    player.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "创建派对失败"));
                }
            }
            case "disband" -> {
                try {
                    var party = partyService.getPlayerParty(player.getUniqueId());
                    if (party == null) {
                        player.sendMessage("§c你不在任何派对中");
                        return true;
                    }
                    partyService.disbandParty(party.getId(), player.getUniqueId());
                    player.sendMessage("§c派对已解散");
                } catch (Exception e) {
                    // audit C-007: 区分业务异常与系统异常，message 可能为 null
                    player.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "解散派对失败"));
                }
            }
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /party invite <玩家>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("§c玩家不在线");
                    return true;
                }
                try {
                    var party = partyService.getPlayerParty(player.getUniqueId());
                    if (party == null) {
                        player.sendMessage("§c你不在任何派对中");
                        return true;
                    }
                    partyService.inviteMember(party.getId(), player.getUniqueId(), target.getUniqueId());
                    player.sendMessage("§a已邀请 " + target.getName() + " 加入派对");
                    target.sendMessage("§d[派对] §e" + player.getName() + " §a邀请你加入派对");
                    target.sendMessage("§7使用 §e/party accept " + player.getName() + " §7接受");
                } catch (Exception e) {
                    // audit C-007: 区分业务异常与系统异常，message 可能为 null
                    player.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "邀请失败"));
                }
            }
            case "accept" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /party accept <玩家>");
                    return true;
                }
                Player inviter = Bukkit.getPlayer(args[1]);
                if (inviter == null) {
                    player.sendMessage("§c玩家不在线");
                    return true;
                }
                try {
                    partyService.acceptInvite(player.getUniqueId(), inviter.getUniqueId());
                    player.sendMessage("§a已加入派对");
                    inviter.sendMessage("§a" + player.getName() + " 加入了派对");
                } catch (Exception e) {
                    // audit C-007: 区分业务异常与系统异常，message 可能为 null
                    player.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "加入派对失败"));
                }
            }
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /party kick <玩家>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("§c玩家不存在");
                    return true;
                }
                try {
                    var party = partyService.getPlayerParty(player.getUniqueId());
                    if (party == null) {
                        player.sendMessage("§c你不在任何派对中");
                        return true;
                    }
                    partyService.kickMember(party.getId(), player.getUniqueId(), target.getUniqueId());
                    player.sendMessage("§c已踢出 " + target.getName());
                    target.sendMessage("§c你已被踢出派对");
                } catch (Exception e) {
                    // audit C-007: 区分业务异常与系统异常，message 可能为 null
                    player.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "踢出成员失败"));
                }
            }
            case "leave" -> {
                try {
                    partyService.leaveParty(player.getUniqueId());
                    player.sendMessage("§c你已离开派对");
                } catch (Exception e) {
                    // audit C-007: 区分业务异常与系统异常，message 可能为 null
                    player.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "离开派对失败"));
                }
            }
            case "list" -> {
                var party = partyService.getPlayerParty(player.getUniqueId());
                if (party == null) {
                    player.sendMessage("§c你不在任何派对中");
                    return true;
                }
                player.sendMessage("§d[派对] §e成员列表 (" + party.getMemberCount() + "/" + party.getMaxMembers() + "):");
                for (var memberId : party.getMembers()) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null) {
                        String role = party.isLeader(memberId) ? "§6[队长]" : "§7[成员]";
                        player.sendMessage("  " + role + " §f" + member.getName());
                    }
                }
            }
            default -> player.sendMessage("§c未知操作: " + action);
        }

        return true;
    }

    private boolean handleMessage(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§c用法: /msg <玩家> <消息>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage("§c玩家不在线");
            return true;
        }

        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        try {
            pmService.sendMessage(player.getUniqueId(), target.getUniqueId(), message);
            player.sendMessage("§7[§e我 §7-> §e" + target.getName() + "§7] §f" + message);
            target.sendMessage("§7[§e" + player.getName() + " §7-> §e我§7] §f" + message);
        } catch (Exception e) {
            // audit C-007: 区分业务异常与系统异常，message 可能为 null
            player.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "发送消息失败"));
        }

        return true;
    }

    private boolean handleReply(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§c用法: /r <消息>");
            return true;
        }

        String message = String.join(" ", args);

        try {
            var targetId = pmService.getLastChatPartner(player.getUniqueId());
            if (targetId == null) {
                player.sendMessage("§c没有最近的聊天对象");
                return true;
            }

            Player target = Bukkit.getPlayer(targetId);
            if (target == null) {
                player.sendMessage("§c玩家不在线");
                return true;
            }

            pmService.quickReply(player.getUniqueId(), message);
            player.sendMessage("§7[§e我 §7-> §e" + target.getName() + "§7] §f" + message);
            target.sendMessage("§7[§e" + player.getName() + " §7-> §e我§7] §f" + message);
        } catch (Exception e) {
            // audit C-007: 区分业务异常与系统异常，message 可能为 null
            player.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "回复消息失败"));
        }

        return true;
    }

    private boolean handleDuel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§c[轨道决斗] §e用法:");
            player.sendMessage("  §e/duel <玩家> [赌注] §7- 发起决斗");
            player.sendMessage("  §e/duel accept <玩家> §7- 接受决斗");
            player.sendMessage("  §e/duel deny <玩家> §7- 拒绝决斗");
            player.sendMessage("  §e/duel forfeit §7- 认输");
            return true;
        }

        String action = args[0].toLowerCase();

        // 发起决斗
        if (action.equals("accept") || action.equals("deny") || action.equals("forfeit")) {
            switch (action) {
                case "accept" -> {
                    if (args.length < 2) {
                        player.sendMessage("§c用法: /duel accept <玩家>");
                        return true;
                    }
                    Player challenger = Bukkit.getPlayer(args[1]);
                    if (challenger == null) {
                        player.sendMessage("§c玩家不在线");
                        return true;
                    }
                    try {
                        var duel = duelService.acceptDuelRequest(player.getUniqueId(), challenger.getUniqueId());
                        player.sendMessage("§a已接受决斗请求");
                        challenger.sendMessage("§a" + player.getName() + " 接受了决斗");

                        // 开始决斗
                        duelService.startDuel(duel.getId(), challenger, player);
                        player.sendMessage("§c决斗开始！");
                        challenger.sendMessage("§c决斗开始！");
                    } catch (Exception e) {
                        // audit C-007/C-012: 区分业务异常与系统异常，message 可能为 null
                        player.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "接受决斗失败"));
                    }
                }
                case "deny" -> {
                    if (args.length < 2) {
                        player.sendMessage("§c用法: /duel deny <玩家>");
                        return true;
                    }
                    Player challenger = Bukkit.getPlayer(args[1]);
                    if (challenger == null) {
                        player.sendMessage("§c玩家不存在");
                        return true;
                    }
                    if (duelService.rejectDuelRequest(player.getUniqueId(), challenger.getUniqueId())) {
                        player.sendMessage("§c已拒绝决斗请求");
                        challenger.sendMessage("§c" + player.getName() + " 拒绝了决斗");
                    }
                }
                case "forfeit" -> {
                    try {
                        duelService.forfeit(player.getUniqueId());
                        player.sendMessage("§c你已认输");
                    } catch (Exception e) {
                        // audit C-007/C-012: 区分业务异常与系统异常，message 可能为 null
                        player.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "认输失败"));
                    }
                }
            }
        } else {
            // 发起决斗
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage("§c玩家不在线");
                return true;
            }

            double wager = 0;
            if (args.length >= 2) {
                try {
                    wager = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的赌注金额");
                    return true;
                }
                // audit C-011: 数值范围校验，拒绝负数 / NaN / Infinity
                if (!Double.isFinite(wager) || wager < 0) {
                    player.sendMessage("§c赌注金额必须为非负有限数字");
                    return true;
                }
                if (wager > 1_000_000_000D) {
                    player.sendMessage("§c赌注金额过大");
                    return true;
                }
            }

            try {
                duelService.sendDuelRequest(player.getUniqueId(), target.getUniqueId(), wager);
                player.sendMessage("§a已向 " + target.getName() + " 发起决斗");
                if (wager > 0) {
                    player.sendMessage("§7赌注: §e" + wager + " 星尘");
                }
                target.sendMessage("§c[轨道决斗] §e" + player.getName() + " §a向你发起决斗");
                if (wager > 0) {
                    target.sendMessage("§7赌注: §e" + wager + " 星尘");
                }
                target.sendMessage("§7使用 §e/duel accept " + player.getName() + " §7接受");
            } catch (Exception e) {
                // audit C-007: 区分业务异常与系统异常，message 可能为 null
                player.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "发起决斗失败"));
            }
        }

        return true;
    }

    private boolean handlePvPStats(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        var stats = statsService.getStats(player.getUniqueId());
        player.sendMessage("§c========== PvP统计 ==========");
        player.sendMessage("§e击杀: §f" + stats.getKills());
        player.sendMessage("§e死亡: §f" + stats.getDeaths());
        player.sendMessage("§e助攻: §f" + stats.getAssists());
        player.sendMessage("§eK/D: §f" + String.format("%.2f", stats.getKDRatio()));
        player.sendMessage("§eKDA: §f" + String.format("%.2f", stats.getKDA()));
        player.sendMessage("§e决斗胜利: §f" + stats.getDuelWins());
        player.sendMessage("§e决斗失败: §f" + stats.getDuelLosses());
        player.sendMessage("§e胜率: §f" + String.format("%.1f%%", stats.getDuelWinRate()));

        return true;
    }

    private boolean handleMute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.mute")) {
            sender.sendMessage("§c你没有权限");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§c用法: /mute <玩家> <时长> <原因>");
            sender.sendMessage("§7时长格式: 30s, 5m, 1h, 7d, perm(永久)");
            return true;
        }

        // audit C-013: 改用 OfflinePlayer，支持对离线玩家禁言
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String targetName = args[0];

        String duration = args[1];
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));

        try {
            if (duration.equalsIgnoreCase("perm")) {
                muteService.mutePermanent(target.getUniqueId(), targetName,
                    sender instanceof Player ? ((Player) sender).getUniqueId() : null, reason);
            } else {
                long time = parseDuration(duration);
                muteService.mutePlayer(target.getUniqueId(), targetName,
                    sender instanceof Player ? ((Player) sender).getUniqueId() : null, reason, time);
            }
            sender.sendMessage("§a已禁言 " + targetName);
        } catch (Exception e) {
            sender.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "禁言失败"));
        }

        return true;
    }

    private boolean handleUnmute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.unmute")) {
            sender.sendMessage("§c你没有权限");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§c用法: /unmute <玩家>");
            return true;
        }

        // audit C-013: 改用 OfflinePlayer，支持对离线玩家解除禁言
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

        if (muteService.unmutePlayer(target.getUniqueId())) {
            sender.sendMessage("§a已解除 " + target.getName() + " 的禁言");
        } else {
            sender.sendMessage("§c该玩家未被禁言");
        }

        return true;
    }

    private boolean handleBan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.ban")) {
            sender.sendMessage("§c你没有权限");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§c用法: /ban <玩家> <时长> <原因>");
            sender.sendMessage("§7时长格式: 30s, 5m, 1h, 7d, perm(永久)");
            return true;
        }

        // audit C-013: 改用 OfflinePlayer，支持对离线玩家封禁（玩家加入时生效）
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String targetName = args[0];

        String duration = args[1];
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));

        try {
            if (duration.equalsIgnoreCase("perm")) {
                banService.banPermanent(target.getUniqueId(), targetName,
                    sender instanceof Player ? ((Player) sender).getUniqueId() : null, reason);
            } else {
                long time = parseDuration(duration);
                banService.banPlayer(target.getUniqueId(), targetName,
                    sender instanceof Player ? ((Player) sender).getUniqueId() : null,
                    reason, time, false, null);
            }
            sender.sendMessage("§a已封禁 " + targetName);
        } catch (Exception e) {
            sender.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "封禁失败"));
        }

        return true;
    }

    private boolean handleUnban(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.unban")) {
            sender.sendMessage("§c你没有权限");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§c用法: /unban <玩家>");
            return true;
        }

        // audit C-016: 必须改为 OfflinePlayer 解析，被封禁玩家本应离线
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

        if (banService.unbanPlayer(target.getUniqueId())) {
            sender.sendMessage("§a已解除 " + args[0] + " 的封禁");
        } else {
            sender.sendMessage("§c该玩家未被封禁");
        }

        return true;
    }

    private boolean handleJail(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.jail")) {
            sender.sendMessage("§c你没有权限");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§c用法: /jail <玩家> <时长> <原因>");
            sender.sendMessage("§7时长格式: 30s, 5m, 1h, 7d, perm(永久)");
            return true;
        }

        // audit C-013: 改用 OfflinePlayer，支持对离线玩家监禁
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String targetName = args[0];

        String duration = args[1];
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));

        try {
            if (duration.equalsIgnoreCase("perm")) {
                jailService.jailPermanent(target.getUniqueId(), targetName,
                    sender instanceof Player ? ((Player) sender).getUniqueId() : null, reason);
            } else {
                long time = parseDuration(duration);
                jailService.jailPlayer(target.getUniqueId(), targetName,
                    sender instanceof Player ? ((Player) sender).getUniqueId() : null, reason, time);
            }
            sender.sendMessage("§a已监禁 " + targetName);
        } catch (Exception e) {
            sender.sendMessage("§c" + (e.getMessage() != null && e.getMessage().length() < 80 ? e.getMessage() : "监禁失败"));
        }

        return true;
    }

    private boolean handleUnjail(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.unjail")) {
            sender.sendMessage("§c你没有权限");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§c用法: /unjail <玩家>");
            return true;
        }

        // audit C-013: 改用 OfflinePlayer，支持对离线玩家释放
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

        if (jailService.releasePlayer(target.getUniqueId())) {
            sender.sendMessage("§a已释放 " + args[0]);
        } else {
            sender.sendMessage("§c该玩家未被监禁");
        }

        return true;
    }

    private boolean handleKick(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.kick")) {
            sender.sendMessage("§c你没有权限");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /kick <玩家> <原因>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§c玩家不在线");
            return true;
        }

        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        kickService.kickPlayer(target, reason);
        sender.sendMessage("§a已踢出 " + target.getName());

        return true;
    }

    private boolean handleVanish(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (!player.hasPermission("starcore.vanish")) {
            player.sendMessage("§c你没有权限");
            return true;
        }

        vanishService.toggle(player);
        return true;
    }

    private static final Pattern DURATION_PATTERN = Pattern.compile("^\\d+[smhd]$", Pattern.CASE_INSENSITIVE);

    private long parseDuration(String duration) {
        // audit C-014: 正则校验 ^\d+[smhd]$，避免 "12x" / "p" 抛 NumberFormatException
        if (duration == null || duration.isEmpty() || !DURATION_PATTERN.matcher(duration.trim()).matches()) {
            throw new IllegalArgumentException("无效的时长格式，应为 <数字><s|m|h|d> 或 perm");
        }
        String d = duration.trim();
        char unit = Character.toLowerCase(d.charAt(d.length() - 1));
        long time = Long.parseLong(d.substring(0, d.length() - 1));

        return switch (unit) {
            case 's' -> TimeUnit.SECONDS.toMillis(time);
            case 'm' -> TimeUnit.MINUTES.toMillis(time);
            case 'h' -> TimeUnit.HOURS.toMillis(time);
            case 'd' -> TimeUnit.DAYS.toMillis(time);
            default -> throw new IllegalArgumentException("无效的时长单位: " + unit);
        };
    }
}
