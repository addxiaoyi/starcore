package dev.starcore.starcore.social.simulation;
import java.util.Optional;

import dev.starcore.starcore.core.database.DatabaseService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 关系网络实现
 */
public class RelationshipNetworkImpl implements RelationshipNetwork {

    private final JavaPlugin plugin;
    private final DatabaseService databaseService;

    // 内存缓存: player1:player2 -> Relationship
    private final Map<String, Relationship> relationships = new ConcurrentHashMap<>();

    // 玩家关系索引: playerId -> Set<playerId>
    private final Map<UUID, Set<UUID>> playerRelations = new ConcurrentHashMap<>();

    // 衰减配置
    private static final double DECAY_RATE = 0.001; // 每毫秒衰减率
    private static final int MIN_STRENGTH = -100;
    private static final int MAX_STRENGTH = 100;

    public RelationshipNetworkImpl(JavaPlugin plugin, DatabaseService databaseService) {
        this.plugin = plugin;
        this.databaseService = databaseService;
        if (databaseService != null) {
            initializeTables();
            try {
                loadAllRelationships();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load relationships, starting fresh: " + e.getMessage());
            }
        }
    }

    private void initializeTables() {
        if (databaseService == null) return;

        // 检测数据库类型
        boolean isSQLite = "SQLITE".equalsIgnoreCase(databaseService.settings().type().name());

        String sql;
        if (isSQLite) {
            sql = """
                CREATE TABLE IF NOT EXISTS relationships (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player1 TEXT NOT NULL,
                    player2 TEXT NOT NULL,
                    type TEXT NOT NULL,
                    strength INTEGER DEFAULT 50,
                    last_interaction INTEGER,
                    created_at INTEGER,
                    UNIQUE(player1, player2)
                )
                """;
        } else {
            sql = """
                CREATE TABLE IF NOT EXISTS relationships (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    player1 VARCHAR(36) NOT NULL,
                    player2 VARCHAR(36) NOT NULL,
                    type VARCHAR(20) NOT NULL,
                    strength INT DEFAULT 50,
                    last_interaction BIGINT,
                    created_at BIGINT,
                    UNIQUE KEY unique_relationship (player1, player2),
                    INDEX idx_player1 (player1),
                    INDEX idx_player2 (player2)
                )
                """;
        }

        String historySql;
        if (isSQLite) {
            historySql = """
                CREATE TABLE IF NOT EXISTS relationship_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player1 TEXT,
                    player2 TEXT,
                    action TEXT,
                    strength_change INTEGER,
                    description TEXT,
                    timestamp INTEGER
                )
                """;
        } else {
            historySql = """
                CREATE TABLE IF NOT EXISTS relationship_history (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    player1 VARCHAR(36),
                    player2 VARCHAR(36),
                    action VARCHAR(50),
                    strength_change INT,
                    description TEXT,
                    timestamp BIGINT
                )
                """;
        }

        databaseService.execute(sql);
        databaseService.execute(historySql);

        // SQLite 需要创建额外索引
        if (isSQLite) {
            databaseService.execute("CREATE INDEX IF NOT EXISTS idx_relationships_player1 ON relationships(player1)");
            databaseService.execute("CREATE INDEX IF NOT EXISTS idx_relationships_player2 ON relationships(player2)");
        }
    }

    private void loadAllRelationships() {
        String sql = "SELECT * FROM relationships";
        databaseService.query(sql, rs -> {
            try {
                while (rs.next()) {
                    UUID p1 = UUID.fromString(rs.getString("player1"));
                    UUID p2 = UUID.fromString(rs.getString("player2"));
                    RelationshipType type = RelationshipType.valueOf(rs.getString("type"));
                    int strength = rs.getInt("strength");
                    long lastInteraction = rs.getLong("last_interaction");
                    long createdAt = rs.getLong("created_at");

                    Relationship rel = new Relationship(p1, p2, type, strength, lastInteraction, createdAt, new ArrayList<>());
                    String key = makeKey(p1, p2);
                    relationships.put(key, rel);

                    playerRelations.computeIfAbsent(p1, k -> ConcurrentHashMap.newKeySet()).add(p2);
                    playerRelations.computeIfAbsent(p2, k -> ConcurrentHashMap.newKeySet()).add(p1);
                }
            } catch (Exception e) {
                // Handle ResultSet errors
            }
            return null;
        });
    }

    private String makeKey(UUID p1, UUID p2) {
        // 确保key排序一致
        if (p1.compareTo(p2) < 0) {
            return p1.toString() + ":" + p2.toString();
        } else {
            return p2.toString() + ":" + p1.toString();
        }
    }

    @Override
    public Relationship getRelationship(UUID player1, UUID player2) {
        String key = makeKey(player1, player2);
        Relationship rel = relationships.get(key);
        if (rel == null) {
            return new Relationship(player1, player2, RelationshipType.STRANGER, 0,
                System.currentTimeMillis(), System.currentTimeMillis(), new ArrayList<>());
        }
        return rel;
    }

    @Override
    public Map<UUID, Relationship> getAllRelationships(UUID playerId) {
        Map<UUID, Relationship> result = new HashMap<>();
        Set<UUID> related = playerRelations.get(playerId);
        if (related != null) {
            for (UUID other : related) {
                Relationship rel = getRelationship(playerId, other);
                result.put(other, rel);
            }
        }
        return result;
    }

    @Override
    public Set<UUID> getSocialCircle(UUID playerId, int minStrength) {
        Set<UUID> circle = new HashSet<>();
        Map<UUID, Relationship> rels = getAllRelationships(playerId);
        for (Map.Entry<UUID, Relationship> entry : rels.entrySet()) {
            if (Math.abs(entry.getValue().strength()) >= minStrength) {
                circle.add(entry.getKey());
            }
        }
        return circle;
    }

    @Override
    public void setRelationship(UUID player1, UUID player2, RelationshipType type, int strength) {
        String key = makeKey(player1, player2);
        long now = System.currentTimeMillis();

        int finalStrength = Math.max(MIN_STRENGTH, Math.min(MAX_STRENGTH, strength));

        Relationship rel = new Relationship(player1, player2, type, finalStrength, now, now, new ArrayList<>());
        relationships.put(key, rel);

        playerRelations.computeIfAbsent(player1, k -> ConcurrentHashMap.newKeySet()).add(player2);
        playerRelations.computeIfAbsent(player2, k -> ConcurrentHashMap.newKeySet()).add(player1);

        // 保存到数据库 - 使用 SQLite 兼容语法
        String sql;
        if (databaseService != null && "SQLITE".equalsIgnoreCase(databaseService.settings().type().name())) {
            sql = "INSERT OR REPLACE INTO relationships (player1, player2, type, strength, last_interaction, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        } else {
            sql = """
                INSERT INTO relationships (player1, player2, type, strength, last_interaction, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                type = VALUES(type), strength = VALUES(strength), last_interaction = VALUES(last_interaction)
                """;
        }
        databaseService.update(sql,
            player1.toString(), player2.toString(),
            type.name(), finalStrength, now, now
        );

        recordInteraction(player1, player2, "SET_RELATIONSHIP", strength - getRelationship(player1, player2).strength(), type.name());
    }

    @Override
    public void increaseStrength(UUID player1, UUID player2, int amount) {
        Relationship rel = getRelationship(player1, player2);
        int newStrength = Math.min(MAX_STRENGTH, rel.strength() + amount);
        RelationshipType newType = determineType(newStrength);

        setRelationship(player1, player2, newType, newStrength);
        recordInteraction(player1, player2, "INCREASE", amount, "关系提升");
    }

    @Override
    public void decreaseStrength(UUID player1, UUID player2, int amount) {
        Relationship rel = getRelationship(player1, player2);
        int newStrength = Math.max(MIN_STRENGTH, rel.strength() - amount);
        RelationshipType newType = determineType(newStrength);

        setRelationship(player1, player2, newType, newStrength);
        recordInteraction(player1, player2, "DECREASE", -amount, "关系下降");
    }

    @Override
    public void removeRelationship(UUID player1, UUID player2) {
        String key = makeKey(player1, player2);
        relationships.remove(key);

        playerRelations.get(player1).remove(player2);
        playerRelations.get(player2).remove(player1);

        String sql = "DELETE FROM relationships WHERE (player1 = ? AND player2 = ?) OR (player1 = ? AND player2 = ?)";
        databaseService.update(sql,
            player1.toString(), player2.toString(),
            player2.toString(), player1.toString()
        );
    }

    private RelationshipType determineType(int strength) {
        if (strength >= 95) return RelationshipType.BEST_FRIEND;
        if (strength >= 70) return RelationshipType.CLOSE_FRIEND;
        if (strength >= 40) return RelationshipType.FRIEND;
        if (strength >= 15) return RelationshipType.ACQUAINTANCE;
        if (strength > -15) return RelationshipType.STRANGER;
        if (strength > -50) return RelationshipType.RIVAL;
        if (strength > -80) return RelationshipType.ENEMY;
        return RelationshipType.NEMESIS;
    }

    private void recordInteraction(UUID p1, UUID p2, String action, int change, String description) {
        String sql = """
            INSERT INTO relationship_history (player1, player2, action, strength_change, description, timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        databaseService.update(sql,
            p1.toString(), p2.toString(), action, change, description, System.currentTimeMillis()
        );
    }

    @Override
    public Set<UUID> getFriends(UUID playerId) {
        Set<UUID> friends = new HashSet<>();
        Map<UUID, Relationship> rels = getAllRelationships(playerId);
        for (Map.Entry<UUID, Relationship> entry : rels.entrySet()) {
            if (entry.getValue().strength() > 0) {
                friends.add(entry.getKey());
            }
        }
        return friends;
    }

    @Override
    public Set<UUID> getEnemies(UUID playerId) {
        Set<UUID> enemies = new HashSet<>();
        Map<UUID, Relationship> rels = getAllRelationships(playerId);
        for (Map.Entry<UUID, Relationship> entry : rels.entrySet()) {
            if (entry.getValue().strength() < 0) {
                enemies.add(entry.getKey());
            }
        }
        return enemies;
    }

    @Override
    public Optional<UUID> getBestFriend(UUID playerId) {
        return getFriends(playerId).stream()
            .max(Comparator.comparingInt(id -> getRelationship(playerId, id).strength()));
    }

    @Override
    public Optional<UUID> getWorstEnemy(UUID playerId) {
        return getEnemies(playerId).stream()
            .min(Comparator.comparingInt(id -> getRelationship(playerId, id).strength()));
    }

    @Override
    public int calculateInfluenceScore(UUID playerId) {
        int score = 0;
        Set<UUID> circle = getSocialCircle(playerId, 30);
        score += circle.size() * 5; // 每个朋友5分

        for (UUID friendId : circle) {
            score += getRelationship(playerId, friendId).strength();
            // 朋友的朋友也有加成
            Set<UUID> friendOfFriend = getSocialCircle(friendId, 30);
            score += friendOfFriend.size();
        }

        return score;
    }

    @Override
    public Set<UUID> getMutualFriends(UUID player1, UUID player2) {
        Set<UUID> p1Friends = getFriends(player1);
        Set<UUID> p2Friends = getFriends(player2);
        Set<UUID> mutual = new HashSet<>(p1Friends);
        mutual.retainAll(p2Friends);
        return mutual;
    }

    @Override
    public int getSocialDistance(UUID from, UUID to) {
        if (from.equals(to)) return 0;

        Set<UUID> visited = new HashSet<>();
        Queue<UUID> queue = new LinkedList<>();
        Map<UUID, Integer> distance = new HashMap<>();

        queue.offer(from);
        visited.add(from);
        distance.put(from, 0);

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            int dist = distance.get(current);

            if (current.equals(to)) {
                return dist;
            }

            Set<UUID> friends = getFriends(current);
            for (UUID friend : friends) {
                if (!visited.contains(friend)) {
                    visited.add(friend);
                    queue.offer(friend);
                    distance.put(friend, dist + 1);
                }
            }
        }

        return -1; // 无法到达
    }

    @Override
    public void applyDecay(long deltaMillis) {
        for (String key : relationships.keySet()) {
            Relationship rel = relationships.get(key);
            if (rel.strength() > 0) {
                int decay = (int) (rel.strength() * DECAY_RATE * deltaMillis);
                if (decay > 0 && rel.strength() > decay) {
                    int newStrength = rel.strength() - decay;
                    String[] parts = key.split(":");
                    UUID p1 = UUID.fromString(parts[0]);
                    UUID p2 = UUID.fromString(parts[1]);
                    setRelationship(p1, p2, determineType(newStrength), newStrength);
                }
            }
        }
    }
}
