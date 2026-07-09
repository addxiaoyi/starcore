package dev.starcore.starcore.social.simulation;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.Optional;

/**
 * 新闻传播服务
 *
 * 管理信息在社会中的传播:
 * - 新闻创建
 * - 传播算法 (基于社交网络)
 * - 真实性与偏见
 * - 新闻衰减
 */
public class NewsPropagationService {

    private final RelationshipNetwork relationshipNetwork;
    private final Map<String, NewsItem> news = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerNewsSeen = new ConcurrentHashMap<>();

    // 评论系统
    private final Map<String, List<NewsComment>> newsComments = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> commentLikes = new ConcurrentHashMap<>();

    // 新闻分享记录
    private final Map<String, NewsShare> newsShares = new ConcurrentHashMap<>();

    private BukkitRunnable spreadTask;

    public NewsPropagationService(org.bukkit.plugin.java.JavaPlugin plugin, RelationshipNetwork relationshipNetwork) {
        this.relationshipNetwork = relationshipNetwork;
        start();
    }

    public void start() {
        spreadTask = new BukkitRunnable() {
            @Override
            public void run() {
                processNewsSpread();
            }
        };
        spreadTask.runTaskTimerAsynchronously(org.bukkit.Bukkit.getPluginManager().getPlugins()[0], 6000, 6000);
    }

    public void stop() {
        if (spreadTask != null) {
            spreadTask.cancel();
        }
    }

    /**
     * 发布新闻
     */
    public String publishNews(String headline, String content, UUID source, NewsCategory category) {
        String id = UUID.randomUUID().toString();
        NewsItem newsItem = new NewsItem(
            id, headline, content, source, category,
            System.currentTimeMillis(),
            1.0, // 初始真实性
            new HashSet<>()
        );
        news.put(id, newsItem);

        // 立即推送给源玩家的社交圈
        if (source != null) {
            Set<UUID> sphere = relationshipNetwork.getSocialCircle(source, 20);
            for (UUID playerId : sphere) {
                deliverNews(playerId, newsItem);
            }
        }

        return id;
    }

    /**
     * 获取玩家未读新闻
     */
    public List<NewsItem> getUnreadNews(UUID playerId) {
        Set<String> seen = playerNewsSeen.getOrDefault(playerId, Set.of());
        return news.values().stream()
            .filter(n -> !seen.contains(n.id()))
            .filter(n -> n.reach().size() > 0) // 只显示已经传播到玩家的新闻
            .sorted(Comparator.comparing(NewsItem::timestamp).reversed())
            .limit(10)
            .toList();
    }

    /**
     * 标记新闻已读
     */
    public void markAsRead(UUID playerId, String newsId) {
        playerNewsSeen.computeIfAbsent(playerId, k -> new HashSet<>()).add(newsId);
    }

    // ==================== 评论功能 ====================

    /**
     * 添加评论
     */
    public NewsComment addComment(String newsId, UUID commenter, String content) {
        if (!news.containsKey(newsId)) {
            return null;
        }

        String commentId = UUID.randomUUID().toString();
        NewsComment comment = new NewsComment(
            commentId,
            newsId,
            commenter,
            content,
            System.currentTimeMillis(),
            new HashSet<>()
        );

        newsComments.computeIfAbsent(newsId, k -> new ArrayList<>()).add(comment);
        return comment;
    }

    /**
     * 获取新闻的所有评论
     * @param newsId 新闻ID
     * @param sortOrder 排序方式 (NEWEST, HOTTEST)
     */
    public List<NewsComment> getComments(String newsId, CommentSortOrder sortOrder) {
        List<NewsComment> comments = newsComments.getOrDefault(newsId, new ArrayList<>());

        return switch (sortOrder) {
            case NEWEST -> comments.stream()
                .sorted(Comparator.comparingLong(NewsComment::timestamp).reversed())
                .collect(Collectors.toList());
            case HOTTEST -> comments.stream()
                .sorted(Comparator.comparingLong((NewsComment c) -> c.likes().size())
                    .reversed()
                    .thenComparingLong(NewsComment::timestamp).reversed())
                .collect(Collectors.toList());
        };
    }

    /**
     * 获取评论数量
     */
    public int getCommentCount(String newsId) {
        return newsComments.getOrDefault(newsId, List.of()).size();
    }

    /**
     * 点赞评论
     */
    public boolean likeComment(String newsId, UUID liker, String commentId) {
        List<NewsComment> comments = newsComments.get(newsId);
        if (comments == null) return false;

        for (int i = 0; i < comments.size(); i++) {
            NewsComment comment = comments.get(i);
            if (comment.id().equals(commentId)) {
                Set<UUID> likes = new HashSet<>(comment.likes());
                if (likes.contains(liker)) {
                    // 取消点赞
                    likes.remove(liker);
                } else {
                    // 添加点赞
                    likes.add(liker);
                }

                NewsComment updated = new NewsComment(
                    comment.id(),
                    comment.newsId(),
                    comment.commenter(),
                    comment.content(),
                    comment.timestamp(),
                    likes
                );
                comments.set(i, updated);
                return true;
            }
        }
        return false;
    }

    /**
     * 检查玩家是否已点赞评论
     */
    public boolean hasLikedComment(String newsId, UUID playerId, String commentId) {
        List<NewsComment> comments = newsComments.get(newsId);
        if (comments == null) return false;

        return comments.stream()
            .filter(c -> c.id().equals(commentId))
            .anyMatch(c -> c.likes().contains(playerId));
    }

    // ==================== 新闻分享功能 ====================

    /**
     * 分享新闻给其他玩家
     */
    public boolean shareNews(String newsId, UUID sharer, UUID targetPlayer) {
        if (!news.containsKey(newsId)) {
            return false;
        }

        NewsItem newsItem = news.get(newsId);
        String shareId = UUID.randomUUID().toString();

        NewsShare share = new NewsShare(
            shareId,
            newsId,
            sharer,
            targetPlayer,
            System.currentTimeMillis()
        );

        newsShares.put(shareId, share);

        // 通知目标玩家
        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugins()[0], () -> {
            org.bukkit.entity.Player player = Bukkit.getPlayer(targetPlayer);
            if (player != null && player.isOnline()) {
                player.sendMessage("§b§l【新闻分享】§f" +
                    Bukkit.getPlayer(sharer).getName() + " §7分享了一篇新闻给你:");
                player.sendMessage("§6" + newsItem.headline());
            }
        });

        return true;
    }

    /**
     * 获取玩家收到的新闻分享
     */
    public List<NewsShare> getSharedNews(UUID playerId) {
        return newsShares.values().stream()
            .filter(s -> s.targetPlayer().equals(playerId))
            .sorted(Comparator.comparing(NewsShare::timestamp).reversed())
            .collect(Collectors.toList());
    }

    /**
     * 获取玩家发起的分享记录
     */
    public List<NewsShare> getPlayerShares(UUID playerId) {
        return newsShares.values().stream()
            .filter(s -> s.sharer().equals(playerId))
            .sorted(Comparator.comparing(NewsShare::timestamp).reversed())
            .collect(Collectors.toList());
    }

    // ==================== 情感分析 ====================

    /**
     * 分析新闻情感
     */
    public NewsSentiment analyzeSentiment(String content) {
        String lower = content.toLowerCase();

        int positiveScore = 0;
        int negativeScore = 0;

        for (String word : POSITIVE_WORDS) {
            if (lower.contains(word)) positiveScore++;
        }
        for (String word : NEGATIVE_WORDS) {
            if (lower.contains(word)) negativeScore++;
        }

        if (positiveScore > negativeScore + 1) {
            return NewsSentiment.POSITIVE;
        } else if (negativeScore > positiveScore + 1) {
            return NewsSentiment.NEGATIVE;
        }
        return NewsSentiment.NEUTRAL;
    }

    private static final Set<String> POSITIVE_WORDS = Set.of(
        "胜利", "成功", "和平", "繁荣", "伟大", "喜悦", "幸福", "增长",
        "victory", "success", "peace", "prosperity", "great", "joy", "happy", "growth"
    );

    private static final Set<String> NEGATIVE_WORDS = Set.of(
        "失败", "战争", "灾难", "危机", "死亡", "悲伤", "贫困", "衰退",
        "defeat", "war", "disaster", "crisis", "death", "sad", "poverty", "decline"
    );

    private void deliverNews(UUID playerId, NewsItem newsItem) {
        Set<UUID> reach = new HashSet<>(newsItem.reach());
        if (reach.add(playerId)) {
            // 更新新闻的触及范围
            NewsItem updated = new NewsItem(
                newsItem.id(), newsItem.headline(), newsItem.content(),
                newsItem.source(), newsItem.category(), newsItem.timestamp(),
                newsItem.credibility(),
                reach
            );
            news.put(newsItem.id(), updated);

            // 发送通知
            Bukkit.getScheduler().runTask(org.bukkit.Bukkit.getPluginManager().getPlugins()[0], () -> {
                org.bukkit.entity.Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.sendMessage("§6§l【社会新闻】§e" + newsItem.headline());
                    player.sendMessage("§7" + newsItem.content().substring(0, Math.min(50, newsItem.content().length())) + "...");
                }
            });
        }
    }

    private void processNewsSpread() {
        long now = System.currentTimeMillis();
        List<NewsItem> toRemove = new ArrayList<>();

        for (NewsItem item : news.values()) {
            // 新闻存活时间 (24小时)
            if (now - item.timestamp() > 24 * 60 * 60 * 1000) {
                toRemove.add(item);
                continue;
            }

            // 衰减真实性
            if (item.credibility() > 0.3) {
                double newCredibility = item.credibility() * 0.99;
                NewsItem updated = new NewsItem(
                    item.id(), item.headline(), item.content(),
                    item.source(), item.category(), item.timestamp(),
                    newCredibility,
                    item.reach()
                );
                news.put(item.id(), updated);
            }

            // 传播到新玩家
            spreadToNewPlayers(item);
        }

        // 清理过期新闻
        for (NewsItem item : toRemove) {
            news.remove(item.id());
        }
    }

    private void spreadToNewPlayers(NewsItem item) {
        if (item.credibility() < 0.5) return; // 低真实性不传播

        // 获取当前触及玩家的朋友
        Set<UUID> newTargets = new HashSet<>();
        for (UUID reachedPlayer : item.reach()) {
            Set<UUID> friends = relationshipNetwork.getFriends(reachedPlayer);
            for (UUID friend : friends) {
                if (!item.reach().contains(friend)) {
                    // 检查是否应该传播 (基于关系强度)
                    RelationshipNetwork.Relationship rel = relationshipNetwork.getRelationship(reachedPlayer, friend);
                    if (rel != null && rel.strength() > 30 && ThreadLocalRandom.current().nextDouble() < item.credibility()) {
                        newTargets.add(friend);
                    }
                }
            }
        }

        // 传播给新目标
        for (UUID target : newTargets) {
            deliverNews(target, item);
        }
    }

    // ==================== 数据类 ====================

    public enum NewsCategory {
        POLITICS("政治", "§c"),
        MILITARY("军事", "§4"),
        ECONOMY("经济", "§6"),
        SOCIAL("社会", "§e"),
        CULTURE("文化", "§d"),
        SCANDAL("丑闻", "§5"),
        HEROIC("英雄事迹", "§a"),
        TRAGEDY("悲剧", "§7"),
        CONFLICT("冲突", "§4");

        private final String name;
        private final String color;

        NewsCategory(String name, String color) {
            this.name = name;
            this.color = color;
        }

        public String getName() { return name; }
        public String getColor() { return color; }
    }

    public record NewsItem(
        String id,
        String headline,
        String content,
        UUID source,
        NewsCategory category,
        long timestamp,
        double credibility,
        Set<UUID> reach
    ) {
        public String getAgeText() {
            long age = System.currentTimeMillis() - timestamp();
            long hours = age / (60 * 60 * 1000);
            if (hours < 1) return "刚刚";
            if (hours < 24) return hours + "小时前";
            return (hours / 24) + "天前";
        }

        public int getCommentCount(NewsPropagationService service) {
            return service.getCommentCount(id);
        }

        public NewsSentiment getSentiment(NewsPropagationService service) {
            return service.analyzeSentiment(content);
        }
    }

    // ==================== 评论数据结构 ====================

    public record NewsComment(
        String id,          // 评论ID
        String newsId,      // 所属新闻ID
        UUID commenter,    // 评论者
        String content,     // 评论内容
        long timestamp,     // 评论时间
        Set<UUID> likes     // 点赞玩家集合
    ) {
        public String getAgeText() {
            long age = System.currentTimeMillis() - timestamp();
            long minutes = age / (60 * 1000);
            if (minutes < 1) return "刚刚";
            if (minutes < 60) return minutes + "分钟前";
            long hours = minutes / 60;
            if (hours < 24) return hours + "小时前";
            return (hours / 24) + "天前";
        }

        public int getLikeCount() {
            return likes.size();
        }
    }

    // ==================== 新闻分享数据结构 ====================

    public record NewsShare(
        String id,          // 分享ID
        String newsId,      // 分享的新闻ID
        UUID sharer,        // 分享者
        UUID targetPlayer,  // 接收者
        long timestamp      // 分享时间
    ) {
        public String getAgeText() {
            long age = System.currentTimeMillis() - timestamp();
            long minutes = age / (60 * 1000);
            if (minutes < 1) return "刚刚";
            if (minutes < 60) return minutes + "分钟前";
            long hours = minutes / 60;
            if (hours < 24) return hours + "小时前";
            return (hours / 24) + "天前";
        }
    }

    // ==================== 枚举定义 ====================

    /**
     * 新闻情感分析
     */
    public enum NewsSentiment {
        POSITIVE("积极", "§a"),
        NEUTRAL("中性", "§f"),
        NEGATIVE("消极", "§c");

        private final String name;
        private final String color;

        NewsSentiment(String name, String color) {
            this.name = name;
            this.color = color;
        }

        public String getName() { return name; }
        public String getColor() { return color; }
    }

    /**
     * 评论排序方式
     */
    public enum CommentSortOrder {
        NEWEST("最新优先"),
        HOTTEST("最热优先");

        private final String name;

        CommentSortOrder(String name) {
            this.name = name;
        }

        public String getName() { return name; }
    }
}
