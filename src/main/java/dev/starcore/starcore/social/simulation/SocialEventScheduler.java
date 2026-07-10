package dev.starcore.starcore.social.simulation;

import java.util.concurrent.ThreadLocalRandom;
import dev.starcore.starcore.foundation.util.RandomProvider;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.util.ColorCodes;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.database.DatabaseUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 社会事件调度器
 *
 * 管理周期性社会事件:
 * - 节日庆典
 * - 社会动荡
 * - 名人效应
 * - 流行趋势
 *
 * 数据库持久化支持活动事件保存
 */
public class SocialEventScheduler {

    private static final String TABLE_NAME = "starcore_social_events";
    private static final Gson GSON = new GsonBuilder().create();

    private final JavaPlugin plugin;
    private ReputationService reputationService;
    private RelationshipNetwork relationshipNetwork;
    private CultureService cultureService;
    private DatabaseService databaseService;

    private final Map<String, SocialEvent> activeEvents = new ConcurrentHashMap<>();
    private BukkitRunnable schedulerTask;
    private boolean persistenceEnabled = false;

    /**
     * 简化构造函数 - 需要手动设置服务
     */
    public SocialEventScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.reputationService = null;
        this.relationshipNetwork = null;
        this.cultureService = null;
        start();
    }

    /**
     * 全量构造函数 - 所有服务都已初始化
     */
    public SocialEventScheduler(JavaPlugin plugin, ReputationService reputationService,
                               RelationshipNetwork relationshipNetwork, CultureService cultureService) {
        this.plugin = plugin;
        this.reputationService = reputationService;
        this.relationshipNetwork = relationshipNetwork;
        this.cultureService = cultureService;
        start();
    }

    /**
     * 设置延迟初始化的服务
     */
    public void setServices(ReputationService reputationService,
                           RelationshipNetwork relationshipNetwork,
                           CultureService cultureService) {
        this.reputationService = reputationService;
        this.relationshipNetwork = relationshipNetwork;
        this.cultureService = cultureService;
    }

    /**
     * 设置声望服务（单独设置）
     */
    public void setReputationService(ReputationService service) {
        this.reputationService = service;
    }

    /**
     * 设置关系网络服务（单独设置）
     */
    public void setRelationshipNetwork(RelationshipNetwork network) {
        this.relationshipNetwork = network;
    }

    /**
     * 设置文化服务（单独设置）
     */
    public void setCultureService(CultureService service) {
        this.cultureService = service;
    }

    /**
     * 初始化数据库持久化
     */
    public void initializePersistence(JavaPlugin plugin, DatabaseService databaseService) {
        if (plugin == null || databaseService == null || !databaseService.isRunning()) {
            return;
        }

        this.databaseService = databaseService;
        initializeTables();
        loadActiveEvents();
        this.persistenceEnabled = true;
        plugin.getLogger().info("[SocialEventScheduler] 数据库持久化已启用");
    }

    private void initializeTables() {
        if (databaseService == null || !databaseService.isRunning()) return;

        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                boolean isSQLite = DatabaseUtils.detectDatabaseType(conn) == DatabaseUtils.DatabaseType.SQLITE;

                String sql;
                if (isSQLite) {
                    sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                          "id TEXT PRIMARY KEY," +
                          "name TEXT NOT NULL," +
                          "description TEXT," +
                          "type TEXT NOT NULL," +
                          "start_time INTEGER NOT NULL," +
                          "duration INTEGER NOT NULL," +
                          "effects_json TEXT," +
                          "updated_at INTEGER NOT NULL" +
                          ")";
                } else {
                    sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                          "id VARCHAR(100) PRIMARY KEY," +
                          "name VARCHAR(100) NOT NULL," +
                          "description TEXT," +
                          "type VARCHAR(32) NOT NULL," +
                          "start_time BIGINT NOT NULL," +
                          "duration BIGINT NOT NULL," +
                          "effects_json TEXT," +
                          "updated_at BIGINT NOT NULL" +
                          ")";
                }

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[SocialEventScheduler] 创建表失败: " + e.getMessage());
            }
        });
    }

    private void loadActiveEvents() {
        if (databaseService == null || !databaseService.isRunning()) return;

        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                String sql = "SELECT * FROM " + TABLE_NAME;
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {

                    long now = System.currentTimeMillis();
                    while (rs.next()) {
                        String id = rs.getString("id");
                        String name = rs.getString("name");
                        String description = rs.getString("description");
                        SocialEventType type = SocialEventType.valueOf(rs.getString("type"));
                        long startTime = rs.getLong("start_time");
                        long duration = rs.getLong("duration");
                        EventEffects effects = parseEffects(rs.getString("effects_json"));

                        // 检查是否过期
                        if (now <= startTime + duration) {
                            SocialEvent event = new SocialEvent(id, name, description, type, startTime, duration, effects);
                            activeEvents.put(id, event);
                        }
                    }
                }

                // 删除过期事件
                cleanupExpiredEventsFromDb();
                plugin.getLogger().info("[SocialEventScheduler] 已加载 " + activeEvents.size() + " 个活跃事件");
            } catch (Exception e) {
                plugin.getLogger().warning("[SocialEventScheduler] 加载事件失败: " + e.getMessage());
            }
        });
    }

    private void cleanupExpiredEventsFromDb() {
        if (databaseService == null || !databaseService.isRunning()) return;

        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                long now = System.currentTimeMillis();
                String sql = "DELETE FROM " + TABLE_NAME + " WHERE start_time + duration < ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, now);
                    int deleted = stmt.executeUpdate();
                    if (deleted > 0) {
                        plugin.getLogger().info("[SocialEventScheduler] 清理了 " + deleted + " 个过期事件");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[SocialEventScheduler] 清理过期事件失败: " + e.getMessage());
            }
        });
    }

    private void saveEvent(SocialEvent event) {
        if (!persistenceEnabled || databaseService == null || !databaseService.isRunning()) return;

        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                boolean isSQLite = DatabaseUtils.detectDatabaseType(conn) == DatabaseUtils.DatabaseType.SQLITE;
                String effectsJson = GSON.toJson(event.effects());
                long updatedAt = System.currentTimeMillis();

                String sql;
                if (isSQLite) {
                    sql = "INSERT INTO " + TABLE_NAME + " (id, name, description, type, start_time, duration, effects_json, updated_at) " +
                          "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                          "ON CONFLICT(id) DO UPDATE SET name=excluded.name, description=excluded.description, " +
                          "type=excluded.type, start_time=excluded.start_time, duration=excluded.duration, " +
                          "effects_json=excluded.effects_json, updated_at=excluded.updated_at";
                } else {
                    sql = "INSERT INTO " + TABLE_NAME + " (id, name, description, type, start_time, duration, effects_json, updated_at) " +
                          "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                          "ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description), " +
                          "type=VALUES(type), start_time=VALUES(start_time), duration=VALUES(duration), " +
                          "effects_json=VALUES(effects_json), updated_at=VALUES(updated_at)";
                }

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, event.id());
                    stmt.setString(2, event.name());
                    stmt.setString(3, event.description());
                    stmt.setString(4, event.type().name());
                    stmt.setLong(5, event.startTime());
                    stmt.setLong(6, event.duration());
                    stmt.setString(7, effectsJson);
                    stmt.setLong(8, updatedAt);
                    stmt.executeUpdate();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[SocialEventScheduler] 保存事件失败: " + e.getMessage());
            }
        });
    }

    private void deleteEvent(String eventId) {
        if (!persistenceEnabled || databaseService == null || !databaseService.isRunning()) return;

        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                String sql = "DELETE FROM " + TABLE_NAME + " WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, eventId);
                    stmt.executeUpdate();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[SocialEventScheduler] 删除事件失败: " + e.getMessage());
            }
        });
    }

    private EventEffects parseEffects(String json) {
        if (json == null || json.isEmpty()) {
            return new EventEffects(0, 1.0, 1.0);
        }
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            return new EventEffects(
                obj.has("reputationBonus") ? obj.get("reputationBonus").getAsDouble() : 0,
                obj.has("cultureBonus") ? obj.get("cultureBonus").getAsDouble() : 1.0,
                obj.has("influenceBonus") ? obj.get("influenceBonus").getAsDouble() : 1.0
            );
        } catch (Exception e) {
            return new EventEffects(0, 1.0, 1.0);
        }
    }

    public void start() {
        schedulerTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        schedulerTask.runTaskTimer(plugin, 1200 * 60, 1200 * 60); // 每小时检查
    }

    public void stop() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
        }
    }

    private void tick() {
        // 检查节日
        checkFestivals();

        // 检查社会动荡
        checkSocialUnrest();

        // 触发名人效应
        triggerCelebrityEffect();

        // 更新流行趋势
        updateTrends();

        // 清理过期事件
        cleanupExpiredEvents();
    }

    private void checkFestivals() {
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);

        // 检查节日并触发
        String festivalId = getFestival(month, day);
        if (festivalId != null && !activeEvents.containsKey(festivalId)) {
            startFestival(festivalId);
        }
    }

    private String getFestival(int month, int day) {
        // 简化的节日检测
        if (month == 0 && day == 1) return "new_year";        // 元旦
        if (month == 1 && day == 14) return "valentine";     // 情人节
        if (month == 2 && day == 8) return "women_day";       // 妇女节
        if (month == 3 && day == 1) return "april_fool";       // 愚人节
        if (month == 3 && day == 5) return "qingming";        // 清明节
        if (month == 4 && day == 1) return "labor_day";        // 劳动节
        if (month == 4 && day == 4) return "youth_day";       // 青年节
        if (month == 5 && day == 1) return "children_day";     // 儿童节
        if (month == 6 && day == 1) return "party_day";        // 建党节
        if (month == 7 && day == 1) return "army_day";        // 建军节
        if (month == 8 && day == 15) return "mid_autumn";      // 中秋节
        if (month == 9 && day == 1) return "national_day";      // 国庆节
        if (month == 9 && day == 10) return "teachers_day";    // 教师节
        if (month == 10 && day == 11) return "singles_day";    // 光棍节
        if (month == 11 && day == 25) return "christmas";      // 圣诞节
        return null;
    }

    private void startFestival(String festivalId) {
        FestivalConfig config = getFestivalConfig(festivalId);
        if (config == null) return;

        SocialEvent event = new SocialEvent(
            festivalId,
            config.name,
            config.description,
            SocialEventType.FESTIVAL,
            System.currentTimeMillis(),
            config.durationHours * 60 * 60 * 1000L,
            config.effects
        );
        activeEvents.put(festivalId, event);

        // 保存到数据库
        saveEvent(event);

        // 广播节日开始
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(ColorCodes.TITLE + "【节日庆典】" + ColorCodes.YELLOW + config.name + " " + ColorCodes.GRAY + "开始了！");
            player.sendMessage(ColorCodes.GRAY + config.description);
            player.sendMessage(ColorCodes.GREEN + "活动期间: 声望获取 +" + (int)(config.effects.reputationBonus * 100) + "%");
        }
    }

    private FestivalConfig getFestivalConfig(String festivalId) {
        return switch (festivalId) {
            case "new_year" -> new FestivalConfig("新年庆典", "新的一年开始了！祝大家新年快乐！", 72,
                new EventEffects(0.5, 1.0, 1.0));
            case "valentine" -> new FestivalConfig("情人节", "爱情的日子，与心爱的人共度时光！", 24,
                new EventEffects(0.3, 1.0, 1.5));
            case "mid_autumn" -> new FestivalConfig("中秋节", "月圆人团圆，赏月吃月饼！", 48,
                new EventEffects(0.4, 1.0, 1.0));
            case "national_day" -> new FestivalConfig("国庆节", "举国欢庆的日子！", 168,
                new EventEffects(0.8, 1.0, 1.0));
            case "christmas" -> new FestivalConfig("圣诞节", "圣诞快乐！Merry Christmas!", 48,
                new EventEffects(0.5, 1.0, 1.0));
            case "single_day" -> new FestivalConfig("光棍节", "单身贵族的节日！", 24,
                new EventEffects(0.3, 1.0, 1.0));
            default -> new FestivalConfig(festivalId, "特殊节日活动", 24,
                new EventEffects(0.3, 1.0, 1.0));
        };
    }

    private void checkSocialUnrest() {
        if (relationshipNetwork == null) return;

        // 检测高负面关系比例
        for (Player player : Bukkit.getOnlinePlayers()) {
            Set<UUID> enemies = relationshipNetwork.getEnemies(player.getUniqueId());
            Set<UUID> friends = relationshipNetwork.getFriends(player.getUniqueId());

            // 如果敌人比朋友多，可能发生冲突事件
            if (enemies.size() > friends.size() * 2) {
                if (ThreadLocalRandom.current().nextDouble() < 0.1) {
                    triggerConflictEvent(player, enemies);
                }
            }
        }
    }

    private void triggerConflictEvent(Player player, Set<UUID> enemies) {
        SocialEvent event = new SocialEvent(
            "conflict_" + player.getName() + "_" + System.currentTimeMillis(),
            "社交冲突",
            player.getName() + " 与多人发生冲突！",
            SocialEventType.CONFLICT,
            System.currentTimeMillis(),
            60 * 60 * 1000L,
            new EventEffects(-0.5, 0.5, 1.0)
        );
        activeEvents.put(event.id(), event);

        // 保存到数据库
        saveEvent(event);

        // 广播
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(ColorCodes.ERROR + ColorCodes.BOLD + "【社会事件】" + ColorCodes.YELLOW + player.getName() + " " + ColorCodes.RED + "卷入了一场冲突！");
        }
    }

    private void triggerCelebrityEffect() {
        if (reputationService == null) return;

        // 找出高声望玩家
        int highestRep = 0;
        UUID celebrity = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            int rep = reputationService.getReputation(player.getUniqueId());
            if (rep > highestRep) {
                highestRep = rep;
                celebrity = player.getUniqueId();
            }
        }

        // 名人有概率触发名人效应
        if (celebrity != null && ThreadLocalRandom.current().nextDouble() < 0.05) {
            String playerName = Bukkit.getPlayer(celebrity) != null ? Bukkit.getPlayer(celebrity).getName() : "某玩家";
            SocialEvent event = new SocialEvent(
                "celebrity_" + celebrity + "_" + System.currentTimeMillis(),
                "名人效应",
                playerName + " 的声望引发了广泛关注！",
                SocialEventType.CELEBRITY,
                System.currentTimeMillis(),
                30 * 60 * 1000L,
                new EventEffects(1.0, 1.0, 1.0)
            );
            activeEvents.put(event.id(), event);

            // 保存到数据库
            saveEvent(event);

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(ColorCodes.TITLE + "【名人效应】" + ColorCodes.YELLOW + playerName + " " + ColorCodes.GOLD + "成为了焦点人物！");
            }
        }
    }

    private void updateTrends() {
        // 随机流行趋势
        if (RandomProvider.nextDouble() < 0.2) {
            String[] trends = {"复古风", "高科技", "极简主义", "自然主义", "赛博朋克", "古典风"};
            String trend = trends[RandomProvider.nextInt(trends.length)];

            SocialEvent event = new SocialEvent(
                "trend_" + System.currentTimeMillis(),
                "流行趋势: " + trend,
                "当前最流行的风格是: " + trend,
                SocialEventType.TREND,
                System.currentTimeMillis(),
                6 * 60 * 60 * 1000L,
                new EventEffects(0.2, 1.0, 1.0)
            );
            activeEvents.put(event.id(), event);

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(ColorCodes.LIGHT_PURPLE + ColorCodes.BOLD + "【流行趋势】" + ColorCodes.DARK_PURPLE + trend + " " + ColorCodes.LIGHT_PURPLE + "成为了新时尚！");
            }
        }
    }

    private void cleanupExpiredEvents() {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, SocialEvent> entry : activeEvents.entrySet()) {
            if (now > entry.getValue().startTime() + entry.getValue().duration()) {
                toRemove.add(entry.getKey());
            }
        }

        for (String id : toRemove) {
            SocialEvent event = activeEvents.get(id);
            if (event.type() == SocialEventType.FESTIVAL) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(ColorCodes.TITLE + "【节日结束】" + ColorCodes.GRAY + event.name() + " " + ColorCodes.GRAY + "已结束，期待下次相遇！");
                }
            }
            activeEvents.remove(id);
            // 从数据库删除
            deleteEvent(id);
        }
    }

    /**
     * 获取当前活跃事件
     */
    public List<SocialEvent> getActiveEvents() {
        return new ArrayList<>(activeEvents.values());
    }

    /**
     * 获取事件加成
     */
    public EventEffects getActiveEffects() {
        EventEffects total = new EventEffects(0, 1.0, 1.0);
        for (SocialEvent event : activeEvents.values()) {
            total = total.add(event.effects());
        }
        return total;
    }

    // ==================== 数据类 ====================

    public enum SocialEventType {
        FESTIVAL,     // 节日
        CONFLICT,     // 冲突
        CELEBRITY,    // 名人效应
        TREND,        // 趋势
        VIRAL,        // 病毒式传播
        SCANDAL       // 丑闻
    }

    public record SocialEvent(
        String id,
        String name,
        String description,
        SocialEventType type,
        long startTime,
        long duration,
        EventEffects effects
    ) {
        public boolean isExpired() {
            return System.currentTimeMillis() > startTime + duration;
        }
    }

    public record EventEffects(
        double reputationBonus,  // 声望加成
        double cultureBonus,     // 文化加成
        double influenceBonus    // 影响力加成
    ) {
        public EventEffects add(EventEffects other) {
            return new EventEffects(
                reputationBonus + other.reputationBonus,
                cultureBonus * other.cultureBonus,
                influenceBonus * other.influenceBonus
            );
        }
    }

    private record FestivalConfig(
        String name,
        String description,
        int durationHours,
        EventEffects effects
    ) {}
}
