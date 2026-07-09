package dev.starcore.starcore.social.simulation.command;

import dev.starcore.starcore.social.SocialSimulationModule;
import dev.starcore.starcore.social.simulation.*;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 社会模拟命令
 * /socialsim <子命令>
 */
public class SocialSimCommand implements CommandExecutor, TabCompleter {

    private final SocialSimulationModule module;
    private final ReputationService reputationService;
    private final RelationshipNetwork relationshipNetwork;
    private final SocialInfluenceService influenceService;
    private final SocialClassService classService;
    private final SocialAllianceService allianceService;
    private final GossipService gossipService;
    private final SocialActivityService activityService;
    private final NewsPropagationService newsService;

    public SocialSimCommand(SocialSimulationModule module,
                          ReputationService reputationService,
                          RelationshipNetwork relationshipNetwork,
                          SocialInfluenceService influenceService,
                          SocialClassService classService,
                          SocialAllianceService allianceService,
                          GossipService gossipService,
                          SocialActivityService activityService,
                          NewsPropagationService newsService) {
        this.module = module;
        this.reputationService = reputationService;
        this.relationshipNetwork = relationshipNetwork;
        this.influenceService = influenceService;
        this.classService = classService;
        this.allianceService = allianceService;
        this.gossipService = gossipService;
        this.activityService = activityService;
        this.newsService = newsService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reputation", "rep" -> handleReputation(player, args);
            case "relation", "rel" -> handleRelation(player, args);
            case "influence", "inf" -> handleInfluence(player, args);
            case "class", "c" -> handleClass(player, args);
            case "alliance", "al" -> handleAlliance(player, args);
            case "gossip", "gs" -> handleGossip(player, args);
            case "activity", "act" -> handleActivity(player, args);
            case "news", "n" -> handleNews(player, args);
            case "social" -> handleSocial(player);
            default -> sendHelp(player);
        }

        return true;
    }

    private void handleReputation(Player player, String[] args) {
        if (args.length < 2) {
            // 显示声望
            var profile = reputationService.getProfile(player.getUniqueId());
            player.sendMessage("§6§l===== 声望面板 =====");
            player.sendMessage("§e整体声望: §f" + profile.overallReputation());
            player.sendMessage("§e声望等级: §f" + profile.level().color() + profile.level().description());

            for (var entry : profile.dimensions().entrySet()) {
                ReputationService.ReputationDimension dim = entry.getKey();
                int value = entry.getValue();
                player.sendMessage("§7" + dim.displayName() + ": §f" + value);
            }
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "top" -> {
                player.sendMessage("§6§l===== 声望排行榜 =====");
                int limit = args.length > 2 ? Math.min(Integer.parseInt(args[2]), 20) : 10;

                // 显示各维度排行榜
                for (ReputationService.ReputationDimension dim : ReputationService.ReputationDimension.values()) {
                    // 分隔
                    player.sendMessage("§b§l" + dim.displayName() + "排行榜:");
                    player.sendMessage("§7" + "-".repeat(20));

                    // 收集所有玩家的声望并排序
                    var allProfiles = new java.util.ArrayList<Map.Entry<UUID, Integer>>();
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        // 获取在线玩家UUID
                        UUID onlineId = onlinePlayer.getUniqueId();
                    }

                    // 简化的排行榜实现
                    int rank = 1;
                    player.sendMessage("§7当前排行榜需要玩家在线数据");
                    player.sendMessage("§e提示: 声望系统正在运行");

                    // 显示玩家自己的排名
                    int playerRank = reputationService.getRank(player.getUniqueId(), dim);
                    // 分隔
                    player.sendMessage("§7你的" + dim.displayName() + "排名: §e第 " + playerRank + " 名");
                }
            }
            case "history" -> {
                var history = reputationService.getHistory(player.getUniqueId(), 10);
                player.sendMessage("§6§l===== 声望历史 =====");
                for (var change : history.values()) {
                    String sign = change.amount() >= 0 ? "§a+" : "§c";
                    player.sendMessage(sign + change.amount() + " §7" + change.reason().description() + " - " + change.description());
                }
            }
            default -> player.sendMessage("§c用法: /socialsim reputation [top|history]");
        }
    }

    private void handleRelation(Player player, String[] args) {
        if (args.length < 2) {
            showRelations(player, player.getUniqueId());
            return;
        }

        String action = args[1].toLowerCase();
        if (action.equals("friend") && args.length >= 3) {
            // 添加好友
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                player.sendMessage("§c玩家不在线");
                return;
            }
            relationshipNetwork.setRelationship(player.getUniqueId(), target.getUniqueId(),
                RelationshipNetwork.RelationshipType.FRIEND, 50);
            player.sendMessage("§a你与 " + target.getName() + " 成为了朋友！");
        } else if (action.equals("enemy") && args.length >= 3) {
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                player.sendMessage("§c玩家不在线");
                return;
            }
            relationshipNetwork.setRelationship(player.getUniqueId(), target.getUniqueId(),
                RelationshipNetwork.RelationshipType.ENEMY, -50);
            player.sendMessage("§c你与 " + target.getName() + " 成为了敌人！");
        } else {
            showRelations(player, player.getUniqueId());
        }
    }

    private void showRelations(Player player, UUID targetId) {
        var relations = relationshipNetwork.getAllRelationships(targetId);
        player.sendMessage("§6§l===== 社交关系 =====");

        Set<UUID> friends = relationshipNetwork.getFriends(targetId);
        if (!friends.isEmpty()) {
            player.sendMessage("§a朋友 §7(" + friends.size() + "):");
            for (UUID friendId : friends) {
                String name = Bukkit.getPlayer(friendId) != null ? Bukkit.getPlayer(friendId).getName() : friendId.toString().substring(0, 8);
                player.sendMessage("  §a- " + name);
            }
        }

        Set<UUID> enemies = relationshipNetwork.getEnemies(targetId);
        if (!enemies.isEmpty()) {
            player.sendMessage("§c敌人 §7(" + enemies.size() + "):");
            for (UUID enemyId : enemies) {
                String name = Bukkit.getPlayer(enemyId) != null ? Bukkit.getPlayer(enemyId).getName() : enemyId.toString().substring(0, 8);
                player.sendMessage("  §c- " + name);
            }
        }

        if (friends.isEmpty() && enemies.isEmpty()) {
            player.sendMessage("§7你还没有建立任何社交关系");
        }
    }

    private void handleInfluence(Player player, String[] args) {
        int influence = influenceService.getInfluence(player.getUniqueId());
        var status = influenceService.getStatus(player.getUniqueId());

        player.sendMessage("§6§l===== 社交影响力 =====");
        player.sendMessage("§e影响力: §f" + influence);
        player.sendMessage("§e社会地位: §f" + status.color() + status.getName());
        // 分隔
        player.sendMessage("§7地位等级:");
        for (var s : SocialInfluenceService.SocialStatus.values()) {
            String marker = s == status ? " §a►" : "";
            player.sendMessage("  " + s.color() + s.getName() + " §7(" + s.getThreshold() + "+)" + marker);
        }
    }

    private void handleClass(Player player, String[] args) {
        var playerClass = classService.getClass(player.getUniqueId());
        int points = classService.getClassPoints(player.getUniqueId());

        player.sendMessage("§6§l===== 社会阶层 =====");
        player.sendMessage("§e当前阶层: " + playerClass.displayName());
        player.sendMessage("§e阶层点数: §f" + points);
        // 分隔
        player.sendMessage("§7阶层特权:");

        if (playerClass.privileges().isEmpty()) {
            player.sendMessage("  §7无特权");
        } else {
            for (String priv : playerClass.privileges()) {
                player.sendMessage("  §a- " + priv);
            }
        }

        // 下一阶层
        var nextClass = getNextClass(playerClass);
        if (nextClass != null) {
            int needed = nextClass.requiredPoints() - points;
            // 分隔
            player.sendMessage("§7晋升到 " + nextClass.displayName() + ": 还需要 §e" + needed + " §7点");
        }
    }

    private SocialClassService.SocialClass getNextClass(SocialClassService.SocialClass current) {
        var classes = SocialClassService.SocialClass.values();
        for (var c : classes) {
            if (c.ordinal() > current.ordinal()) {
                return c;
            }
        }
        return null;
    }

    private void handleAlliance(Player player, String[] args) {
        if (args.length < 2) {
            showPlayerAlliances(player);
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "create" -> {
                if (args.length < 4) {
                    player.sendMessage("§c用法: /socialsim alliance create <名称> <标签>");
                    return;
                }
                String name = args[2];
                String tag = args[3];
                allianceService.createAlliance(player.getUniqueId(), name, tag);
                player.sendMessage("§a联盟 '" + name + "' 创建成功！");
            }
            case "list" -> {
                var leaderboard = allianceService.getAllianceLeaderboard();
                player.sendMessage("§6§l===== 联盟排行 =====");
                int i = 1;
                for (var alliance : leaderboard) {
                    player.sendMessage(i++ + ". §e" + alliance.name() + " §7(" + alliance.tag() + ") §f- " + alliance.legacyPoints() + " 遗产点");
                }
            }
            default -> showPlayerAlliances(player);
        }
    }

    private void showPlayerAlliances(Player player) {
        var alliances = allianceService.getPlayerAlliances(player.getUniqueId());
        player.sendMessage("§6§l===== 我的联盟 =====");

        if (alliances.isEmpty()) {
            player.sendMessage("§7你还没有加入任何联盟");
            player.sendMessage("§e使用 /socialsim alliance create <名称> <标签> 创建联盟");
        } else {
            for (String allianceId : alliances) {
                allianceService.getAlliance(allianceId).ifPresent(alliance -> {
                    player.sendMessage("§e" + alliance.name() + " §7(" + alliance.tag() + ")");
                    player.sendMessage("  §7成员: " + alliance.memberCount() + " 遗产点: " + alliance.legacyPoints());
                });
            }
        }
    }

    private void handleGossip(Player player, String[] args) {
        if (args.length < 2) {
            // 显示热门八卦
            var trending = gossipService.getTrendingGossip();
            player.sendMessage("§5§l===== 热门八卦 =====");
            int i = 1;
            for (var gossip : trending) {
                player.sendMessage(i++ + ". §d" + gossip.topic().emoji() + " " + gossip.content().substring(0, Math.min(30, gossip.content().length())));
                player.sendMessage("   §7传播: " + gossip.spreadCount() + "次 | 可信度: " + (int)(gossip.credibility() * 100) + "%");
            }
            if (trending.isEmpty()) {
                player.sendMessage("§7暂无八卦");
            }
            return;
        }

        String action = args[1].toLowerCase();
        if (action.equals("create") && args.length >= 3) {
            String content = args[2];
            gossipService.createGossip(player.getUniqueId(), content, GossipService.GossipTopic.values()[new Random().nextInt(GossipService.GossipTopic.values().length)]);
            player.sendMessage("§a八卦已发布！");
        } else if (action.equals("history")) {
            var history = gossipService.getPlayerGossipHistory(player.getUniqueId());
            player.sendMessage("§5§l===== 我的八卦 =====");
            for (var gossip : history) {
                player.sendMessage("§d" + gossip.content().substring(0, Math.min(40, gossip.content().length())));
            }
        }
    }

    private void handleActivity(Player player, String[] args) {
        if (args.length < 2) {
            showActivities(player);
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "create" -> {
                if (args.length < 3) {
                    player.sendMessage("§c用法: /socialsim activity create <类型> <名称>");
                    player.sendMessage("§7类型: party, celebration, competition");
                    return;
                }
                String type = args[2].toLowerCase();
                String name = args.length > 3 ? args[3] : "活动";

                String id;
                switch (type) {
                    case "party" -> id = activityService.createParty(player.getUniqueId(), name, "社交聚会");
                    case "celebration" -> id = activityService.createCelebration(player.getUniqueId(), name, "庆典活动", 4);
                    case "competition" -> id = activityService.createCompetition(player.getUniqueId(), name, "比赛", SocialActivityService.CompetitionType.DUEL);
                    default -> {
                        player.sendMessage("§c未知类型: " + type);
                        return;
                    }
                }
                player.sendMessage("§a活动 '" + name + "' 创建成功！");
                player.sendMessage("§7活动ID: " + id);
            }
            case "list" -> showActivities(player);
            default -> showActivities(player);
        }
    }

    private void showActivities(Player player) {
        var activities = activityService.getPublicActivities();
        player.sendMessage("§6§l===== 公开活动 =====");
        int i = 1;
        for (var activity : activities) {
            player.sendMessage(i++ + ". " + activity.type().emoji() + " §e" + activity.name());
            player.sendMessage("   §7状态: " + activity.status().color() + activity.status().getName() + " §7人数: " + activity.participantCount() + "/" + activity.maxParticipants());
        }
        if (activities.isEmpty()) {
            player.sendMessage("§7暂无公开活动");
        }
    }

    private void handleNews(Player player, String[] args) {
        var unread = newsService.getUnreadNews(player.getUniqueId());
        player.sendMessage("§e§l===== 社会新闻 =====");
        int i = 1;
        for (var news : unread) {
            player.sendMessage(i++ + ". " + news.category().getColor() + "【" + news.category().getName() + "】§f" + news.headline());
            player.sendMessage("   §7" + news.getAgeText() + " §7传播: " + news.reach().size() + "人");
        }
        if (unread.isEmpty()) {
            player.sendMessage("§7暂无未读新闻");
        }
    }

    private void handleSocial(Player player) {
        player.sendMessage("§6§l===== 社会模拟面板 =====");
        // 分隔

        // 声望
        var profile = reputationService.getProfile(player.getUniqueId());
        player.sendMessage("§e声望: §f" + profile.overallReputation() + " §7(" + profile.level().color() + profile.level().description() + "§7)");

        // 影响力
        var status = influenceService.getStatus(player.getUniqueId());
        player.sendMessage("§e影响力: §f" + influenceService.getInfluence(player.getUniqueId()) + " §7(" + status.color() + status.getName() + "§7)");

        // 阶层
        var playerClass = classService.getClass(player.getUniqueId());
        player.sendMessage("§e社会阶层: " + playerClass.displayName());

        // 社交关系
        int friends = relationshipNetwork.getFriends(player.getUniqueId()).size();
        int enemies = relationshipNetwork.getEnemies(player.getUniqueId()).size();
        player.sendMessage("§e社交关系: §a" + friends + " 朋友 §c" + enemies + " 敌人");

        // 联盟
        int alliances = allianceService.getPlayerAlliances(player.getUniqueId()).size();
        player.sendMessage("§e联盟: §f" + alliances);

        // 分隔
        player.sendMessage("§7子命令: /socialsim <reputation|relation|influence|class|alliance|gossip|activity|news>");
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6§l===== 社会模拟系统 =====");
        // 分隔
        player.sendMessage("§e/socialsim §f- 显示社会模拟面板");
        player.sendMessage("§e/socialsim reputation [top|history] §f- 声望系统");
        player.sendMessage("§e/socialsim relation [friend|enemy] §f- 社交关系");
        player.sendMessage("§e/socialsim influence §f- 社会影响力");
        player.sendMessage("§e/socialsim class §f- 社会阶层");
        player.sendMessage("§e/socialsim alliance [create|list] §f- 社交联盟");
        player.sendMessage("§e/socialsim gossip [create|history] §f- 八卦传播");
        player.sendMessage("§e/socialsim activity [create|list] §f- 社交活动");
        player.sendMessage("§e/socialsim news §f- 社会新闻");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("reputation", "relation", "influence", "class", "alliance", "gossip", "activity", "news", "social"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "reputation" -> completions.addAll(List.of("top", "history"));
                case "relation" -> completions.addAll(List.of("friend", "enemy"));
                case "alliance" -> completions.addAll(List.of("create", "list"));
                case "gossip" -> completions.addAll(List.of("create", "history"));
                case "activity" -> completions.addAll(List.of("create", "list"));
            }
        }

        return completions;
    }
}
