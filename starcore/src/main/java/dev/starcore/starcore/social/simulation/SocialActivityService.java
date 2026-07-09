package dev.starcore.starcore.social.simulation;
import java.sql.*;
import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.database.DatabaseUtils;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 社交活动服务
 *
 * 管理玩家的社交活动:
 * - 聚会 (Party)
 * - 庆典 (Celebration)
 * - 比赛 (Competition)
 * - 集会 (Gathering)
 * - 社交任务
 */
public class SocialActivityService {

    private final Map<String, SocialActivity> activities = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerActivities = new ConcurrentHashMap<>();
    private final Map<UUID, ActivityReward> pendingRewards = new ConcurrentHashMap<>();

    private JavaPlugin plugin;
    private DatabaseService databaseService;
    private boolean isSQLite = false;

    /**
     * 设置插件和数据库引用（用于持久化）
     */
    public void initialize(JavaPlugin plugin, DatabaseService databaseService) {
        this.plugin = plugin;
        this.databaseService = databaseService;
        if (databaseService != null) {
            initializeTables();
            loadAllActivities();
        }
    }

    private void initializeTables() {
        if (databaseService == null) return;

        try {
            Optional<javax.sql.DataSource> ds = databaseService.dataSource();
            if (ds.isPresent()) {
                isSQLite = DatabaseUtils.detectDatabaseType(ds.get()) == DatabaseUtils.DatabaseType.SQLITE;
            }
        } catch (Exception e) {
            isSQLite = false;
            if (plugin != null) {
                plugin.getLogger().warning("Failed to detect database type, defaulting to MySQL: " + e.getMessage());
            }
        }

        String sql;
        if (isSQLite) {
            sql = """
                CREATE TABLE IF NOT EXISTS social_activities (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT,
                    type TEXT NOT NULL,
                    host TEXT NOT NULL,
                    start_time INTEGER NOT NULL,
                    duration INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    max_participants INTEGER NOT NULL,
                    metadata TEXT
                )
                """;
        } else {
            sql = """
                CREATE TABLE IF NOT EXISTS social_activities (
                    id VARCHAR(100) PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    description TEXT,
                    type VARCHAR(50) NOT NULL,
                    host VARCHAR(36) NOT NULL,
                    start_time BIGINT NOT NULL,
                    duration BIGINT NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    max_participants INT NOT NULL,
                    metadata TEXT
                )
                """;
        }
        databaseService.execute(sql);

        // 活动参与者表
        String memberSql;
        if (isSQLite) {
            memberSql = """
                CREATE TABLE IF NOT EXISTS social_activity_participants (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    activity_id TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    joined_at INTEGER NOT NULL,
                    UNIQUE(activity_id, player_uuid)
                )
                """;
        } else {
            memberSql = """
                CREATE TABLE IF NOT EXISTS social_activity_participants (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    activity_id VARCHAR(100) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    joined_at BIGINT NOT NULL,
                    UNIQUE KEY unique_participant (activity_id, player_uuid)
                )
                """;
        }
        databaseService.execute(memberSql);
    }

    private void loadAllActivities() {
        if (databaseService == null) return;

        String sql = "SELECT * FROM social_activities WHERE status != 'ENDED' AND status != 'CANCELLED'";
        databaseService.query(sql, rs -> {
            try {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String name = rs.getString("name");
                    String description = rs.getString("description");
                    String typeStr = rs.getString("type");
                    UUID host = UUID.fromString(rs.getString("host"));
                    long startTime = rs.getLong("start_time");
                    long duration = rs.getLong("duration");
                    String statusStr = rs.getString("status");
                    int maxParticipants = rs.getInt("max_participants");

                    SocialActivityType type;
                    try {
                        type = SocialActivityType.valueOf(typeStr);
                    } catch (Exception e) {
                        type = SocialActivityType.PARTY;
                    }

                    ActivityStatus status;
                    try {
                        status = ActivityStatus.valueOf(statusStr);
                    } catch (Exception e) {
                        status = ActivityStatus.PREPARING;
                    }

                    // 加载参与者
                    Set<UUID> participants = loadParticipants(id);
                    Set<UUID> invited = new HashSet<>();

                    SocialActivity activity = new SocialActivity(
                        id, name, description, type, host, startTime, duration,
                        participants, new HashSet<>(), invited, status, maxParticipants, new HashMap<>()
                    );
                    activities.put(id, activity);

                    // 更新玩家活动映射
                    for (UUID p : participants) {
                        playerActivities.computeIfAbsent(p, k -> new HashSet<>()).add(id);
                    }
                }
            } catch (Exception e) {
                if (plugin != null) {
                    plugin.getLogger().warning("Failed to load social activities: " + e.getMessage());
                }
            }
            return null;
        });
    }

    private Set<UUID> loadParticipants(String activityId) {
        Set<UUID> participants = new HashSet<>();
        String sql = "SELECT player_uuid FROM social_activity_participants WHERE activity_id = ?";
        databaseService.query(sql, rs -> {
            try {
                while (rs.next()) {
                    participants.add(UUID.fromString(rs.getString("player_uuid")));
                }
            } catch (Exception e) {
                if (plugin != null) {
                    plugin.getLogger().warning("Failed to parse participant UUID for activity " + activityId + ": " + e.getMessage());
                }
            }
            return null;
        }, activityId);
        return participants;
    }

    private void saveActivityToDb(SocialActivity activity) {
        if (databaseService == null) return;

        String sql;
        if (isSQLite) {
            sql = "INSERT OR REPLACE INTO social_activities (id, name, description, type, host, start_time, duration, status, max_participants) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        } else {
            sql = """
                INSERT INTO social_activities (id, name, description, type, host, start_time, duration, status, max_participants)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), status = VALUES(status)
                """;
        }

        databaseService.update(sql,
            activity.id(), activity.name(), activity.description(),
            activity.type().name(), activity.host().toString(),
            activity.startTime(), activity.duration(), activity.status().name(),
            activity.maxParticipants()
        );

        // 保存参与者
        saveParticipants(activity);
    }

    private void saveParticipants(SocialActivity activity) {
        if (databaseService == null) return;

        // 删除旧参与者
        databaseService.update("DELETE FROM social_activity_participants WHERE activity_id = ?", activity.id());

        // 插入新参与者
        String sql = "INSERT INTO social_activity_participants (activity_id, player_uuid, joined_at) VALUES (?, ?, ?)";
        for (UUID p : activity.participants()) {
            databaseService.update(sql, activity.id(), p.toString(), System.currentTimeMillis());
        }
    }

    private void deleteActivityFromDb(String activityId) {
        if (databaseService == null) return;
        databaseService.update("DELETE FROM social_activity_participants WHERE activity_id = ?", activityId);
        databaseService.update("DELETE FROM social_activities WHERE id = ?", activityId);
    }

    /**
     * 创建聚会
     */
    public String createParty(UUID host, String name, String description) {
        String id = "party_" + UUID.randomUUID();
        SocialActivity activity = new SocialActivity(
            id, name, description,
            SocialActivityType.PARTY,
            host,
            System.currentTimeMillis(),
            2 * 60 * 60 * 1000L, // 2小时
            new HashSet<>(Set.of(host)),
            new HashSet<>(),
            new HashSet<>(),
            SocialActivityService.ActivityStatus.PREPARING,
            20, // 最大参与人数
            new HashMap<>()
        );
        activities.put(id, activity);
        playerActivities.computeIfAbsent(host, k -> new HashSet<>()).add(id);
        saveActivityToDb(activity);
        return id;
    }

    /**
     * 创建庆典
     */
    public String createCelebration(UUID host, String name, String description, int duration) {
        String id = "celebration_" + UUID.randomUUID();
        SocialActivity activity = new SocialActivity(
            id, name, description,
            SocialActivityType.CELEBRATION,
            host,
            System.currentTimeMillis(),
            duration * 60 * 60 * 1000L,
            new HashSet<>(Set.of(host)),
            new HashSet<>(),
            new HashSet<>(),
            SocialActivityService.ActivityStatus.PREPARING,
            100,
            new HashMap<>()
        );
        activities.put(id, activity);
        playerActivities.computeIfAbsent(host, k -> new HashSet<>()).add(id);
        saveActivityToDb(activity);
        return id;
    }

    /**
     * 创建比赛
     */
    public String createCompetition(UUID host, String name, String description, CompetitionType type) {
        String id = "comp_" + UUID.randomUUID();
        SocialActivity activity = new SocialActivity(
            id, name, description,
            SocialActivityType.COMPETITION,
            host,
            System.currentTimeMillis(),
            4 * 60 * 60 * 1000L,
            new HashSet<>(Set.of(host)),
            new HashSet<>(),
            new HashSet<>(),
            SocialActivityService.ActivityStatus.PREPARING,
            type.maxParticipants,
            Map.of("type", type.name())
        );
        activities.put(id, activity);
        playerActivities.computeIfAbsent(host, k -> new HashSet<>()).add(id);
        saveActivityToDb(activity);
        return id;
    }

    /**
     * 参加活动
     */
    public boolean joinActivity(UUID playerId, String activityId) {
        SocialActivity activity = activities.get(activityId);
        if (activity == null) return false;

        if (activity.participants().size() >= activity.maxParticipants()) return false;

        activity.participants().add(playerId);
        activity.applicants().remove(playerId);
        playerActivities.computeIfAbsent(playerId, k -> new HashSet<>()).add(activityId);

        // 保存到数据库
        saveActivityToDb(activity);

        // 广播
        broadcastActivity(activity, playerId + " 加入了活动！");

        return true;
    }

    /**
     * 离开活动
     */
    public boolean leaveActivity(UUID playerId, String activityId) {
        SocialActivity activity = activities.get(activityId);
        if (activity == null) return false;

        activity.participants().remove(playerId);
        playerActivities.get(playerId).remove(activityId);

        // 保存到数据库
        saveActivityToDb(activity);

        broadcastActivity(activity, playerId + " 离开了活动");

        return true;
    }

    /**
     * 开始活动
     */
    public boolean startActivity(String activityId) {
        SocialActivity activity = activities.get(activityId);
        if (activity == null) return false;

        if (activity.participants().size() < 2) return false;

        // 更新状态
        activities.put(activityId, new SocialActivity(
            activity.id(), activity.name(), activity.description(),
            activity.type(), activity.host(),
            activity.startTime(), activity.duration(),
            activity.participants(), activity.applicants(),
            activity.invited(), ActivityStatus.ACTIVE,
            activity.maxParticipants(), activity.metadata()
        ));

        // 保存到数据库
        saveActivityToDb(activities.get(activityId));

        broadcastActivity(activity, "活动开始了！");

        return true;
    }

    /**
     * 结束活动
     */
    public void endActivity(String activityId) {
        SocialActivity activity = activities.remove(activityId);
        if (activity == null) return;

        for (UUID participant : activity.participants()) {
            playerActivities.get(participant).remove(activityId);
        }

        // 计算并发放奖励
        calculateAndDistributeRewards(activity);

        // 更新数据库状态
        if (databaseService != null) {
            String sql;
            if (isSQLite) {
                sql = "INSERT OR REPLACE INTO social_activities (id, name, description, type, host, start_time, duration, status, max_participants) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            } else {
                sql = "INSERT INTO social_activities (id, name, description, type, host, start_time, duration, status, max_participants) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE status = VALUES(status)";
            }
            databaseService.update(sql,
                activity.id(), activity.name(), activity.description(),
                activity.type().name(), activity.host().toString(),
                activity.startTime(), activity.duration(), ActivityStatus.ENDED.name(),
                activity.maxParticipants()
            );
        }

        // 广播结束
        for (UUID participant : activity.participants()) {
            Player player = Bukkit.getPlayer(participant);
            if (player != null) {
                player.sendMessage("§6【活动结束】§e" + activity.name() + " §7已结束！");
            }
        }
    }

    /**
     * 取消活动
     */
    public void cancelActivity(String activityId) {
        SocialActivity activity = activities.remove(activityId);
        if (activity == null) return;

        for (UUID participant : activity.participants()) {
            playerActivities.get(participant).remove(activityId);
        }

        deleteActivityFromDb(activityId);

        // 广播取消
        for (UUID participant : activity.participants()) {
            Player player = Bukkit.getPlayer(participant);
            if (player != null) {
                player.sendMessage("§c【活动取消】§e" + activity.name() + " §c已被取消！");
            }
        }
    }

    /**
     * 计算并发放活动奖励
     */
    private void calculateAndDistributeRewards(SocialActivity activity) {
        int participantCount = activity.participants().size();
        if (participantCount == 0) return;

        // 根据活动类型计算奖励
        int baseReputation = switch (activity.type()) {
            case PARTY -> 5;
            case CELEBRATION -> 10;
            case COMPETITION -> 15;
            case GATHERING -> 8;
            case SOCIAL_MISSION -> 12;
            case NETWORKING -> 10;
        };

        // 根据参与人数调整奖励
        int bonus = Math.min(10, participantCount / 5);

        for (UUID participant : activity.participants()) {
            ActivityReward reward = new ActivityReward(
                activity.id(),
                activity.type().getName(),
                baseReputation + bonus,
                activity.type().emoji(),
                System.currentTimeMillis()
            );
            pendingRewards.put(participant, reward);

            // 通知玩家
            Player player = Bukkit.getPlayer(participant);
            if (player != null) {
                player.sendMessage("§6【活动奖励】§e你获得了 §a" + (baseReputation + bonus) + " §e点声誉值！");
            }
        }
    }

    /**
     * 领取活动奖励
     */
    public boolean claimReward(UUID playerId) {
        ActivityReward reward = pendingRewards.remove(playerId);
        if (reward == null) return false;

        // 奖励已经通过事件系统发放，这里只是标记已领取
        return true;
    }

    /**
     * 获取待领取的奖励
     */
    public ActivityReward getPendingReward(UUID playerId) {
        return pendingRewards.get(playerId);
    }

    /**
     * 邀请玩家
     */
    public void inviteToActivity(UUID inviterId, UUID targetId, String activityId) {
        SocialActivity activity = activities.get(activityId);
        if (activity == null) return;

        activity.invited().add(targetId);

        Player target = Bukkit.getPlayer(targetId);
        if (target != null) {
            String inviterName = Bukkit.getPlayer(inviterId) != null ?
                Bukkit.getPlayer(inviterId).getName() : "一位玩家";
            target.sendMessage("§6§l【活动邀请】§e" + inviterName + " 邀请你参加: " + activity.name());
            target.sendMessage("§a使用 /activity accept " + activityId + " 接受邀请");
        }

        // 保存邀请状态
        saveActivityToDb(activity);
    }

    /**
     * 获取活动
     */
    public Optional<SocialActivity> getActivity(String activityId) {
        return Optional.ofNullable(activities.get(activityId));
    }

    /**
     * 获取玩家的活动
     */
    public Set<String> getPlayerActivities(UUID playerId) {
        return playerActivities.getOrDefault(playerId, Set.of());
    }

    /**
     * 获取所有活动
     */
    public List<SocialActivity> getAllActivities() {
        return new ArrayList<>(activities.values());
    }

    /**
     * 获取公开活动
     */
    public List<SocialActivity> getPublicActivities() {
        return activities.values().stream()
            .filter(a -> a.status() == ActivityStatus.PREPARING ||
                        a.status() == ActivityStatus.ACTIVE)
            .sorted(Comparator.comparingLong(SocialActivity::startTime).reversed())
            .toList();
    }

    /**
     * 广播活动消息
     */
    private void broadcastActivity(SocialActivity activity, String message) {
        for (UUID participant : activity.participants()) {
            Player player = Bukkit.getPlayer(participant);
            if (player != null) {
                player.sendMessage("§6[§e" + activity.name() + "§6]§f " + message);
            }
        }
    }

    // ==================== 数据类 ====================

    public record SocialActivity(
        String id,
        String name,
        String description,
        SocialActivityType type,
        UUID host,
        long startTime,
        long duration,
        Set<UUID> participants,
        Set<UUID> applicants,
        Set<UUID> invited,
        ActivityStatus status,
        int maxParticipants,
        Map<String, Object> metadata
    ) {
        public int participantCount() { return participants.size(); }
        public boolean isActive() { return status == ActivityStatus.ACTIVE; }
    }

    public enum SocialActivityType {
        PARTY("聚会", "§b🎉"),
        CELEBRATION("庆典", "§6🎊"),
        COMPETITION("比赛", "§c⚔️"),
        GATHERING("集会", "§a👥"),
        SOCIAL_MISSION("社交任务", "§d📋"),
        NETWORKING("社交联谊", "§e🤝");

        private final String name;
        private final String emoji;

        SocialActivityType(String name, String emoji) {
            this.name = name;
            this.emoji = emoji;
        }

        public String getName() { return name; }
        public String emoji() { return emoji; }
    }

    public enum ActivityStatus {
        PREPARING("准备中", "§e"),
        ACTIVE("进行中", "§a"),
        ENDED("已结束", "§7"),
        CANCELLED("已取消", "§c");

        private final String name;
        private final String color;

        ActivityStatus(String name, String color) {
            this.name = name;
            this.color = color;
        }

        public String getName() { return name; }
        public String color() { return color; }
    }

    public enum CompetitionType {
        DUEL(2, "决斗", "§c"),
        TOURNAMENT(16, "锦标赛", "§6"),
        BATTLE_ROYALE(32, "大乱斗", "§4"),
        SPORTS(20, "运动会", "§b"),
        QUIZ(50, "知识竞赛", "§e");

        private final int maxParticipants;
        private final String name;
        private final String color;

        CompetitionType(int max, String name, String color) {
            this.maxParticipants = max;
            this.name = name;
            this.color = color;
        }

        public int maxParticipants() { return maxParticipants; }
        public String getName() { return name; }
        public String color() { return color; }
    }

    /**
     * 活动奖励记录
     */
    public record ActivityReward(
        String activityId,
        String activityType,
        int reputationAmount,
        String emoji,
        long timestamp
    ) {
        public String getDisplayText() {
            return emoji + " " + activityType + ": +" + reputationAmount + " 声誉";
        }
    }
}
