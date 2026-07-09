package dev.starcore.starcore.social.simulation;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.database.DatabaseUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 社会影响力服务实现
 */
public class SocialInfluenceServiceImpl implements SocialInfluenceService {

    private final JavaPlugin plugin;
    private final ReputationService reputationService;
    private final RelationshipNetwork relationshipNetwork;
    private final DatabaseService databaseService;

    private final Map<UUID, Integer> influenceCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<InfluenceSource>> influenceSources = new ConcurrentHashMap<>();
    private BukkitTask decayTask;

    // 衰减配置
    private static final int DECAY_INTERVAL_TICKS = 1200 * 60; // 每小时衰减一次
    private static final double DECAY_RATE = 0.01; // 每次衰减1%

    public SocialInfluenceServiceImpl(JavaPlugin plugin, ReputationService reputationService,
                                     RelationshipNetwork relationshipNetwork, DatabaseService databaseService) {
        this.plugin = plugin;
        this.reputationService = reputationService;
        this.relationshipNetwork = relationshipNetwork;
        this.databaseService = databaseService;
        if (databaseService != null) {
            initializeTables();
            try {
                loadAllInfluence();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load influence data, starting fresh: " + e.getMessage());
            }
        }
    }

    private void initializeTables() {
        if (databaseService == null) return;
        // 检测数据库类型
        boolean isSQLite = false;
        try {
            Optional<javax.sql.DataSource> ds = databaseService.dataSource();
            if (ds.isPresent()) {
                isSQLite = DatabaseUtils.detectDatabaseType(ds.get()) == DatabaseUtils.DatabaseType.SQLITE;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("检测数据库类型失败，使用 MySQL 语法: " + e.getMessage());
        }

        if (isSQLite) {
            String sql = """
                CREATE TABLE IF NOT EXISTS player_influence (
                    player_id TEXT PRIMARY KEY,
                    influence INTEGER DEFAULT 0,
                    last_decay INTEGER
                )
                """;
            databaseService.execute(sql);
        } else {
            String sql = """
                CREATE TABLE IF NOT EXISTS player_influence (
                    player_id VARCHAR(36) PRIMARY KEY,
                    influence INT DEFAULT 0,
                    last_decay BIGINT,
                    UNIQUE KEY unique_player (player_id)
                )
                """;
            databaseService.execute(sql);
        }

        // 影响力来源表
        if (isSQLite) {
            String sourceSql = """
                CREATE TABLE IF NOT EXISTS influence_sources (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_id TEXT,
                    source TEXT,
                    amount INTEGER,
                    timestamp INTEGER
                )
                """;
            databaseService.execute(sourceSql);
        } else {
            String sourceSql = """
                CREATE TABLE IF NOT EXISTS influence_sources (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    player_id VARCHAR(36),
                    source VARCHAR(100),
                    amount INT,
                    timestamp BIGINT
                )
                """;
            databaseService.execute(sourceSql);
        }
    }

    private void loadAllInfluence() {
        String sql = "SELECT * FROM player_influence";
        databaseService.query(sql, rs -> {
            try {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString("player_id"));
                    int influence = rs.getInt("influence");
                    influenceCache.put(playerId, influence);
                }
            } catch (Exception e) {
                // Handle ResultSet errors
            }
            return null;
        });
    }

    @Override
    public int getInfluence(UUID playerId) {
        return influenceCache.computeIfAbsent(playerId, this::loadInfluence);
    }

    private int loadInfluence(UUID playerId) {
        String sql = "SELECT influence FROM player_influence WHERE player_id = ?";
        List<Integer> results = databaseService.query(sql, rs -> {
            List<Integer> list = new ArrayList<>();
            try {
                if (rs.next()) {
                    list.add(rs.getInt("influence"));
                }
            } catch (Exception e) {
                // Handle ResultSet errors
            }
            return list;
        }, playerId.toString());

        return results.isEmpty() ? 0 : results.get(0);
    }

    @Override
    public SocialStatus getStatus(UUID playerId) {
        return SocialStatus.fromInfluence(getInfluence(playerId));
    }

    @Override
    public void addInfluence(UUID playerId, int amount, String source) {
        int current = getInfluence(playerId);
        int newInfluence = Math.max(0, current + amount);
        influenceCache.put(playerId, newInfluence);

        // 保存到数据库 - 使用 SQLite 兼容语法
        String sql;
        if (databaseService != null && "SQLITE".equalsIgnoreCase(databaseService.settings().type().name())) {
            // SQLite 使用 INSERT OR REPLACE
            sql = """
                INSERT OR REPLACE INTO player_influence (player_id, influence, last_decay)
                VALUES (?, ?, ?)
                """;
        } else {
            // MySQL 使用 ON DUPLICATE KEY UPDATE
            sql = """
                INSERT INTO player_influence (player_id, influence, last_decay)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE influence = VALUES(influence)
                """;
        }
        databaseService.update(sql, playerId.toString(), newInfluence, System.currentTimeMillis());

        // 记录来源
        recordSource(playerId, source, amount);

        // 传播影响力给社交圈
        if (amount > 0) {
            propagateInfluence(playerId, amount, new HashSet<>());
        }
    }

    private void recordSource(UUID playerId, String source, int amount) {
        String sql = "INSERT INTO influence_sources (player_id, source, amount, timestamp) VALUES (?, ?, ?, ?)";
        databaseService.update(sql, playerId.toString(), source, amount, System.currentTimeMillis());

        influenceSources.computeIfAbsent(playerId, k -> new ArrayList<>())
            .add(new InfluenceSource(source, amount, System.currentTimeMillis()));
    }

    private void propagateInfluence(UUID source, int amount, Set<UUID> visited) {
        if (visited.contains(source)) return;
        visited.add(source);

        // 获取源玩家的社交圈
        Set<UUID> friends = relationshipNetwork.getFriends(source);
        int influencePerFriend = amount / Math.max(1, friends.size());

        for (UUID friendId : friends) {
            if (visited.contains(friendId)) continue;

            // 给予朋友一定影响力
            int friendGain = influencePerFriend / 2; // 递减传播
            if (friendGain > 0) {
                int current = getInfluence(friendId);
                int newInfluence = Math.max(0, current + friendGain);
                influenceCache.put(friendId, newInfluence);

                String sql = "UPDATE player_influence SET influence = ? WHERE player_id = ?";
                databaseService.update(sql, newInfluence, friendId.toString());
            }

            // 继续传播 (最多2度)
            if (visited.size() < 10) {
                propagateInfluence(friendId, influencePerFriend / 2, visited);
            }
        }
    }

    @Override
    public void broadcastToInfluenceSphere(UUID source, String message, int maxReach) {
        Set<UUID> sphere = getInfluenceSphere(source, 3);
        SocialStatus sourceStatus = getStatus(source);

        for (UUID targetId : sphere) {
            Player target = Bukkit.getPlayer(targetId);
            if (target != null && target.isOnline()) {
                SocialStatus targetStatus = getStatus(targetId);
                // 高地位玩家更容易收到消息
                if (targetStatus.ordinal() >= SocialStatus.REGIONAL.ordinal()) {
                    target.sendMessage(sourceStatus.getColor() + "【社会新闻】" + message);
                }
            }
        }
    }

    @Override
    public Set<UUID> getInfluenceSphere(UUID playerId, int levels) {
        Set<UUID> sphere = new HashSet<>();
        Set<UUID> visited = new HashSet<>();
        Queue<UUID> queue = new LinkedList<>();

        queue.offer(playerId);
        visited.add(playerId);
        sphere.add(playerId);

        int currentLevel = 0;
        while (!queue.isEmpty() && currentLevel < levels) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                UUID current = queue.poll();
                Set<UUID> friends = relationshipNetwork.getFriends(current);
                for (UUID friend : friends) {
                    if (!visited.contains(friend)) {
                        visited.add(friend);
                        queue.offer(friend);
                        sphere.add(friend);
                    }
                }
            }
            currentLevel++;
        }

        return sphere;
    }

    @Override
    public void startDecayTask() {
        decayTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            applyDecay();
        }, DECAY_INTERVAL_TICKS, DECAY_INTERVAL_TICKS);
    }

    @Override
    public void stopDecayTask() {
        if (decayTask != null) {
            decayTask.cancel();
        }
    }

    private void applyDecay() {
        for (UUID playerId : new ArrayList<>(influenceCache.keySet())) {
            int current = influenceCache.get(playerId);
            if (current > 0) {
                int decay = Math.max(1, (int) (current * DECAY_RATE));
                int newInfluence = Math.max(0, current - decay);
                influenceCache.put(playerId, newInfluence);

                String sql = "UPDATE player_influence SET influence = ?, last_decay = ? WHERE player_id = ?";
                databaseService.update(sql, newInfluence, System.currentTimeMillis(), playerId.toString());
            }
        }
    }

    record InfluenceSource(String source, int amount, long timestamp) {}
}
