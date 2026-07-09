package dev.starcore.starcore.social.simulation.command;

import dev.starcore.starcore.social.SocialSimulationModule;
import dev.starcore.starcore.social.simulation.*;
import dev.starcore.starcore.social.simulation.RelationshipNetwork.Relationship;
import dev.starcore.starcore.social.simulation.RelationshipNetwork.RelationshipType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 社会模拟命令
 * /social <reputation|relation|influence|culture|news>
 */
public class SocialCommand implements CommandExecutor, TabCompleter {

    private final SocialSimulationModule module;

    public SocialCommand(SocialSimulationModule module) {
        this.module = module;
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
            case "rep", "reputation" -> handleReputation(player, args);
            case "rel", "relation" -> handleRelation(player, args);
            case "inf", "influence" -> handleInfluence(player, args);
            case "cul", "culture" -> handleCulture(player, args);
            case "news" -> handleNews(player, args);
            case "status" -> handleStatus(player);
            default -> sendHelp(player);
        }

        return true;
    }

    private void handleReputation(Player player, String[] args) {
        ReputationService service = module.reputationService();
        UUID playerId = player.getUniqueId();

        if (args.length == 1) {
            // 显示自己的声望
            ReputationService.ReputationProfile profile = service.getProfile(playerId);
            ReputationService.ReputationLevel level = profile.level();

            player.sendMessage("§6§l==== 声望档案 ====");
            player.sendMessage("§e等级: " + level.color() + level.description() + " §7(" + profile.overallReputation() + ")");
            // 分隔
            player.sendMessage("§7各维度声望:");
            for (ReputationService.ReputationDimension dim : ReputationService.ReputationDimension.values()) {
                int value = profile.dimensions().getOrDefault(dim, 0);
                String bar = getRepBar(value);
                player.sendMessage("  " + dim.displayName() + ": §e" + value + " " + bar);
            }
            // 分隔

            // 显示最近变化
            Map<Long, ReputationService.ReputationChange> history = service.getHistory(playerId, 5);
            if (!history.isEmpty()) {
                player.sendMessage("§7最近变化:");
                for (var change : history.values()) {
                    String sign = change.amount() > 0 ? "§a+" : "§c";
                    player.sendMessage("  " + sign + change.amount() + " §7" + change.reason().description());
                }
            }
        } else if (args.length >= 2) {
            if (args[1].equalsIgnoreCase("top")) {
                // 显示声望排行榜
                int limit = args.length > 2 ? Integer.parseInt(args[2]) : 10;
                player.sendMessage("§6§l==== 声望排行榜 ====");
                player.sendMessage("§e(各维度最高)");

                for (ReputationService.ReputationDimension dim : ReputationService.ReputationDimension.values()) {
                    // 分隔
                    player.sendMessage("§b" + dim.displayName() + ":");
                    UUID topId = service.getTopPlayer(dim, limit);
                    int rank = service.getRank(playerId, dim);
                    player.sendMessage("  你的排名: §e第 " + rank + " 名");
                    if (topId != null) {
                        String topName = Bukkit.getPlayer(topId) != null ? Bukkit.getPlayer(topId).getName() : "未知";
                        int topRep = service.getProfile(topId).dimensions().getOrDefault(dim, 0);
                        player.sendMessage("  第一名: §e" + topName + " §7(" + topRep + ")");
                    }
                }
            }
        }
    }

    private void handleRelation(Player player, String[] args) {
        RelationshipNetwork network = module.relationshipNetwork();
        UUID playerId = player.getUniqueId();

        Map<UUID, Relationship> relations = network.getAllRelationships(playerId);

        player.sendMessage("§6§l==== 社交关系 ====");
        player.sendMessage("§7共有 " + relations.size() + " 个关系");

        // 分类显示
        Set<UUID> friends = network.getFriends(playerId);
        Set<UUID> enemies = network.getEnemies(playerId);

        if (!friends.isEmpty()) {
            // 分隔
            player.sendMessage("§a§l朋友 §7(" + friends.size() + "):");
            for (UUID friendId : friends) {
                Relationship rel = network.getRelationship(playerId, friendId);
                String name = Bukkit.getPlayer(friendId) != null ? Bukkit.getPlayer(friendId).getName() : "离线";
                String bar = getRelationBar(rel.strength());
                player.sendMessage("  " + name + " §7" + rel.type().displayName() + ": §a" + rel.strength() + " " + bar);
            }
        }

        if (!enemies.isEmpty()) {
            // 分隔
            player.sendMessage("§c§l敌人 §7(" + enemies.size() + "):");
            for (UUID enemyId : enemies) {
                Relationship rel = network.getRelationship(playerId, enemyId);
                String name = Bukkit.getPlayer(enemyId) != null ? Bukkit.getPlayer(enemyId).getName() : "离线";
                String bar = getRelationBar(rel.strength());
                player.sendMessage("  " + name + " §7" + rel.type().displayName() + ": §c" + rel.strength() + " " + bar);
            }
        }

        // 显示社交影响力分数
        int influenceScore = network.calculateInfluenceScore(playerId);
        // 分隔
        player.sendMessage("§e社交影响力分数: §f" + influenceScore);
    }

    private void handleInfluence(Player player, String[] args) {
        SocialInfluenceService service = module.influenceService();
        UUID playerId = player.getUniqueId();

        SocialInfluenceService.SocialStatus status = service.getStatus(playerId);
        int influence = service.getInfluence(playerId);

        player.sendMessage("§6§l==== 社会影响力 ====");
        player.sendMessage("§e当前影响力: §f" + influence);
        player.sendMessage("§e社会地位: " + status.getColor() + status.getName());
        // 分隔

        // 显示影响力范围预览
        Set<UUID> sphere = service.getInfluenceSphere(playerId, 2);
        player.sendMessage("§7你的影响力覆盖 §e" + sphere.size() + " §7位玩家 (2度以内)");

        if (args.length > 1 && args[1].equalsIgnoreCase("sphere")) {
            int levels = args.length > 2 ? Integer.parseInt(args[2]) : 2;
            Set<UUID> fullSphere = service.getInfluenceSphere(playerId, levels);
            player.sendMessage("§7" + levels + "度影响力覆盖 §e" + fullSphere.size() + " §7位玩家:");
            for (UUID id : fullSphere) {
                String name = Bukkit.getPlayer(id) != null ? Bukkit.getPlayer(id).getName() : "离线玩家";
                player.sendMessage("  §e- " + name);
            }
        }
    }

    private void handleCulture(Player player, String[] args) {
        // 文化信息需要国家
        player.sendMessage("§6§l==== 文化系统 ====");
        player.sendMessage("§7使用 /nation culture 查看国家文化");
        // 分隔
        player.sendMessage("§e文化等级:");
        for (CultureService.CultureLevel level : CultureService.CultureLevel.values()) {
            player.sendMessage("  " + level.getColor() + level.getName() + " §7(需要 " + level.getThreshold() + " 文化值)");
        }
    }

    private void handleNews(Player player, String[] args) {
        NewsPropagationService service = module.newsService();
        UUID playerId = player.getUniqueId();

        List<NewsPropagationService.NewsItem> unread = service.getUnreadNews(playerId);

        player.sendMessage("§6§l==== 社会新闻 ====");

        if (unread.isEmpty()) {
            player.sendMessage("§7暂无新新闻");
        } else {
            for (NewsPropagationService.NewsItem item : unread) {
                // 分隔
                player.sendMessage(item.category().getColor() + "【" + item.category().getName() + "】§e" + item.headline());
                player.sendMessage("§7" + item.content());
                player.sendMessage("§8" + item.getAgeText() + " §8- 传播范围: " + item.reach().size() + "人");

                // 标记已读
                service.markAsRead(playerId, item.id());
            }
        }
    }

    private void handleStatus(Player player) {
        ReputationService.ReputationProfile repProfile = module.reputationService().getProfile(player.getUniqueId());
        RelationshipNetwork.RelationshipType bestFriend = null;
        RelationshipNetwork.RelationshipType worstEnemy = null;

        RelationshipNetwork network = module.relationshipNetwork();
        var bestFriendOpt = network.getBestFriend(player.getUniqueId());
        var worstEnemyOpt = network.getWorstEnemy(player.getUniqueId());

        player.sendMessage("§6§l==== 社会模拟状态 ====");
        // 分隔
        player.sendMessage("§b声望等级: " + repProfile.level().color() + repProfile.level().description());
        player.sendMessage("§b社会地位: " + module.influenceService().getStatus(player.getUniqueId()).getColor() +
            module.influenceService().getStatus(player.getUniqueId()).getName());
        // 分隔

        if (bestFriendOpt.isPresent()) {
            String name = Bukkit.getPlayer(bestFriendOpt.get()) != null ? Bukkit.getPlayer(bestFriendOpt.get()).getName() : "离线";
            Relationship rel = network.getRelationship(player.getUniqueId(), bestFriendOpt.get());
            player.sendMessage("§a§l挚友: §f" + name + " §7(关系: " + rel.strength() + ")");
        }

        if (worstEnemyOpt.isPresent()) {
            String name = Bukkit.getPlayer(worstEnemyOpt.get()) != null ? Bukkit.getPlayer(worstEnemyOpt.get()).getName() : "离线";
            Relationship rel = network.getRelationship(player.getUniqueId(), worstEnemyOpt.get());
            player.sendMessage("§c§l宿敌: §f" + name + " §7(关系: " + rel.strength() + ")");
        }

        // 显示活跃事件
        List<SocialEventScheduler.SocialEvent> events = module.eventScheduler().getActiveEvents();
        if (!events.isEmpty()) {
            // 分隔
            player.sendMessage("§e活跃事件:");
            for (SocialEventScheduler.SocialEvent event : events) {
                player.sendMessage("  §6" + event.name() + " §7- " + event.description());
            }
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6§l==== 社会模拟 ====");
        player.sendMessage("§e/social rep §7- 查看声望档案");
        player.sendMessage("§e/social rel §7- 查看社交关系");
        player.sendMessage("§e/social inf §7- 查看社会影响力");
        player.sendMessage("§e/social cul §7- 查看文化信息");
        player.sendMessage("§e/social news §7- 查看社会新闻");
        player.sendMessage("§e/social status §7- 查看综合状态");
    }

    private String getRepBar(int value) {
        int bars = Math.min(20, Math.abs(value) / 10);
        StringBuilder sb = new StringBuilder("§7[");
        for (int i = 0; i < bars; i++) sb.append("§a█");
        for (int i = bars; i < 20; i++) sb.append("§8░");
        sb.append("§7]");
        return sb.toString();
    }

    private String getRelationBar(int value) {
        int bars = Math.min(20, Math.abs(value) / 5);
        StringBuilder sb = new StringBuilder("§7[");
        String color = value > 0 ? "§a" : "§c";
        for (int i = 0; i < bars; i++) sb.append(color).append("█");
        for (int i = bars; i < 20; i++) sb.append("§8░");
        sb.append("§7]");
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("rep", "rel", "inf", "cul", "news", "status");
        }
        return List.of();
    }
}
