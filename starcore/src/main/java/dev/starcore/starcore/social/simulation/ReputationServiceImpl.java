package dev.starcore.starcore.social.simulation;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.social.simulation.events.ReputationChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 声望服务实现
 */
public class ReputationServiceImpl implements ReputationService {

    private final JavaPlugin plugin;
    private final DatabaseService databaseService;

    // 内存缓存
    private final Map<UUID, ReputationProfile> profileCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<ReputationChange>> historyCache = new ConcurrentHashMap<>();

    public ReputationServiceImpl(JavaPlugin plugin, DatabaseService databaseService) {
        this.plugin = plugin;
        this.databaseService = databaseService;
        if (databaseService != null) {
            initializeTables();
            try {
                loadAllProfiles();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load reputation profiles, starting fresh: " + e.getMessage());
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
                CREATE TABLE IF NOT EXISTS player_reputation (
                    player_id TEXT PRIMARY KEY,
                    moral INTEGER DEFAULT 0,
                    ability INTEGER DEFAULT 0,
                    wealth INTEGER DEFAULT 0,
                    charisma INTEGER DEFAULT 0,
                    overall INTEGER DEFAULT 0,
                    last_updated INTEGER
                )
                """;
        } else {
            sql = """
                CREATE TABLE IF NOT EXISTS player_reputation (
                    player_id VARCHAR(36) PRIMARY KEY,
                    moral INT DEFAULT 0,
                    ability INT DEFAULT 0,
                    wealth INT DEFAULT 0,
                    charisma INT DEFAULT 0,
                    overall INT DEFAULT 0,
                    last_updated BIGINT
                )
                """;
        }

        String historySql;
        if (isSQLite) {
            historySql = """
                CREATE TABLE IF NOT EXISTS reputation_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_id TEXT,
                    dimension TEXT,
                    amount INTEGER,
                    new_value INTEGER,
                    reason TEXT,
                    description TEXT,
                    source_player TEXT,
                    timestamp INTEGER
                )
                """;
        } else {
            historySql = """
                CREATE TABLE IF NOT EXISTS reputation_history (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    player_id VARCHAR(36),
                    dimension VARCHAR(20),
                    amount INT,
                    new_value INT,
                    reason VARCHAR(50),
                    description TEXT,
                    source_player VARCHAR(36),
                    timestamp BIGINT,
                    INDEX idx_player_time (player_id, timestamp)
                )
                """;
        }

        databaseService.execute(sql);
        databaseService.execute(historySql);
    }

    private void loadAllProfiles() {
        String sql = "SELECT player_id, moral, ability, wealth, charisma, last_updated FROM player_reputation";
        databaseService.query(sql, rs -> {
            try {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString("player_id"));
                    Map<ReputationDimension, Integer> dims = new EnumMap<>(ReputationDimension.class);
                    dims.put(ReputationDimension.MORAL, rs.getInt("moral"));
                    dims.put(ReputationDimension.ABILITY, rs.getInt("ability"));
                    dims.put(ReputationDimension.WEALTH, rs.getInt("wealth"));
                    dims.put(ReputationDimension.CHARISMA, rs.getInt("charisma"));
                    // 计算 overall 而不是依赖数据库计算
                    int overall = rs.getInt("moral") + rs.getInt("ability") + rs.getInt("wealth") + rs.getInt("charisma");

                    ReputationProfile profile = new ReputationProfile(
                        playerId, overall, dims,
                        ReputationLevel.fromReputation(overall),
                        rs.getLong("last_updated")
                    );
                    profileCache.put(playerId, profile);
                }
            } catch (Exception e) {
                // Handle ResultSet errors
            }
            return null;
        });
    }

    @Override
    public int getReputation(UUID playerId) {
        return profileCache.computeIfAbsent(playerId, this::loadProfile).overallReputation();
    }

    @Override
    public ReputationProfile getProfile(UUID playerId) {
        return profileCache.computeIfAbsent(playerId, this::loadProfile);
    }

    private ReputationProfile loadProfile(UUID playerId) {
        String sql = "SELECT player_id, moral, ability, wealth, charisma, last_updated FROM player_reputation WHERE player_id = ?";
        List<ReputationProfile> results = databaseService.query(sql, rs -> {
            List<ReputationProfile> profiles = new ArrayList<>();
            try {
                while (rs.next()) {
                    Map<ReputationDimension, Integer> dims = new EnumMap<>(ReputationDimension.class);
                    dims.put(ReputationDimension.MORAL, rs.getInt("moral"));
                    dims.put(ReputationDimension.ABILITY, rs.getInt("ability"));
                    dims.put(ReputationDimension.WEALTH, rs.getInt("wealth"));
                    dims.put(ReputationDimension.CHARISMA, rs.getInt("charisma"));
                    // 计算 overall 而不是依赖数据库计算列
                    int overall = rs.getInt("moral") + rs.getInt("ability") + rs.getInt("wealth") + rs.getInt("charisma");

                    profiles.add(new ReputationProfile(
                        playerId, overall, dims,
                        ReputationLevel.fromReputation(overall),
                        rs.getLong("last_updated")
                    ));
                }
            } catch (Exception e) {
                // Handle ResultSet errors
            }
            return profiles;
        }, playerId.toString());

        if (results.isEmpty()) {
            // 创建默认档案
            Map<ReputationDimension, Integer> dims = new EnumMap<>(ReputationDimension.class);
            for (ReputationDimension dim : ReputationDimension.values()) {
                dims.put(dim, 0);
            }
            return new ReputationProfile(playerId, 0, dims, ReputationLevel.COMMONER, System.currentTimeMillis());
        }
        return results.get(0);
    }

    @Override
    public CompletableFuture<Boolean> modifyReputation(UUID playerId, int amount, ReputationReason reason) {
        return CompletableFuture.supplyAsync(() -> {
            ReputationProfile profile = getProfile(playerId);
            ReputationDimension dim = reason.dimension();

            // 计算新值
            int currentDim = profile.dimensions().getOrDefault(dim, 0);
            int newDim = Math.max(-1000, Math.min(1000, currentDim + amount)); // 限制范围

            // 更新数据库
            String updateSql = String.format(
                "UPDATE player_reputation SET %s = ?, last_updated = ? WHERE player_id = ?",
                dim.name().toLowerCase()
            );

            int updated = databaseService.update(updateSql, newDim, System.currentTimeMillis(), playerId.toString());
            if (updated == 0) {
                String insertSql = "INSERT INTO player_reputation (player_id, %s, last_updated) VALUES (?, ?, ?)"
                    .formatted(dim.name().toLowerCase());
                databaseService.update(insertSql, playerId.toString(), newDim, System.currentTimeMillis());
            }

            // 记录历史
            recordHistory(playerId, dim, amount, newDim, reason, null);

            // 更新缓存
            Map<ReputationDimension, Integer> newDims = new EnumMap<>(ReputationDimension.class);
            newDims.putAll(profile.dimensions());
            newDims.put(dim, newDim);
            int newOverall = newDims.values().stream().mapToInt(Integer::intValue).sum();
            ReputationProfile newProfile = new ReputationProfile(
                playerId, newOverall, newDims,
                ReputationLevel.fromReputation(newOverall),
                System.currentTimeMillis()
            );
            profileCache.put(playerId, newProfile);

            // 触发事件
            plugin.getServer().getPluginManager().callEvent(
                new ReputationChangeEvent(playerId, dim, amount, newDim, reason.name())
            );

            return true;
        });
    }

    private void recordHistory(UUID playerId, ReputationDimension dim, int amount,
                               int newValue, ReputationReason reason, UUID sourcePlayer) {
        String sql = """
            INSERT INTO reputation_history
            (player_id, dimension, amount, new_value, reason, description, source_player, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        databaseService.update(sql,
            playerId.toString(),
            dim.name(),
            amount,
            newValue,
            reason.name(),
            reason.description(),
            sourcePlayer != null ? sourcePlayer.toString() : null,
            System.currentTimeMillis()
        );
    }

    @Override
    public CompletableFuture<Void> addMoral(UUID playerId, int amount, String description) {
        return modifyReputation(playerId, amount, ReputationReason.HELP_PLAYER)
            .thenAccept(success -> {
                if (success && plugin != null) {
                    plugin.getServer().broadcast(
                        net.kyori.adventure.text.Component.text("§6【道德声望】§e" +
                            (Bukkit.getPlayer(playerId) != null ? Bukkit.getPlayer(playerId).getName() : playerId) +
                            " §7因 §a" + description + " §7获得 §b" + amount + " §7点道德声望")
                    );
                }
            })
            .thenApply(v -> null);
    }

    @Override
    public CompletableFuture<Void> addAbility(UUID playerId, int amount, String description) {
        return modifyReputation(playerId, amount, ReputationReason.COMPLETE_QUEST)
            .thenAccept(success -> {
                if (success && plugin != null) {
                    plugin.getServer().broadcast(
                        net.kyori.adventure.text.Component.text("§6【能力声望】§e" +
                            (Bukkit.getPlayer(playerId) != null ? Bukkit.getPlayer(playerId).getName() : playerId) +
                            " §7因 §c" + description + " §7获得 §d" + amount + " §7点能力声望")
                    );
                }
            })
            .thenApply(v -> null);
    }

    @Override
    public CompletableFuture<Void> addWealth(UUID playerId, int amount, String description) {
        return modifyReputation(playerId, amount, ReputationReason.DONATE_TREASURY)
            .thenAccept(success -> {
                if (success && plugin != null) {
                    plugin.getServer().broadcast(
                        net.kyori.adventure.text.Component.text("§6【财富声望】§e" +
                            (Bukkit.getPlayer(playerId) != null ? Bukkit.getPlayer(playerId).getName() : playerId) +
                            " §7因 §e" + description + " §7获得 §6" + amount + " §7点财富声望")
                    );
                }
            })
            .thenApply(v -> null);
    }

    @Override
    public CompletableFuture<Void> addCharisma(UUID playerId, int amount, String description) {
        return modifyReputation(playerId, amount, ReputationReason.RECRUIT_MEMBER)
            .thenAccept(success -> {
                if (success && plugin != null) {
                    plugin.getServer().broadcast(
                        net.kyori.adventure.text.Component.text("§6【魅力声望】§e" +
                            (Bukkit.getPlayer(playerId) != null ? Bukkit.getPlayer(playerId).getName() : playerId) +
                            " §7因 §d" + description + " §7获得 §5" + amount + " §7点魅力声望")
                    );
                }
            })
            .thenApply(v -> null);
    }

    @Override
    public ReputationLevel getLevel(UUID playerId) {
        return getProfile(playerId).level();
    }

    @Override
    public String getLevelName(UUID playerId) {
        ReputationLevel level = getLevel(playerId);
        return level.color() + level.description();
    }

    @Override
    public Map<Long, ReputationChange> getHistory(UUID playerId, int limit) {
        String sql = """
            SELECT * FROM reputation_history
            WHERE player_id = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;

        List<ReputationChange> changes = databaseService.query(sql, rs -> {
            List<ReputationChange> list = new ArrayList<>();
            try {
                while (rs.next()) {
                    list.add(new ReputationChange(
                        rs.getLong("timestamp"),
                        ReputationDimension.valueOf(rs.getString("dimension")),
                        rs.getInt("amount"),
                        rs.getInt("new_value"),
                        ReputationReason.valueOf(rs.getString("reason")),
                        rs.getString("description"),
                        rs.getString("source_player") != null ?
                            UUID.fromString(rs.getString("source_player")) : null
                    ));
                }
            } catch (Exception e) {
                // Handle ResultSet errors
            }
            return list;
        }, playerId.toString(), limit);

        Map<Long, ReputationChange> result = new LinkedHashMap<>();
        for (ReputationChange change : changes) {
            result.put(change.timestamp(), change);
        }
        return result;
    }

    @Override
    public ReputationChange getLastChange(UUID playerId) {
        Map<Long, ReputationChange> history = getHistory(playerId, 1);
        return history.isEmpty() ? null : history.values().iterator().next();
    }

    @Override
    public UUID getTopPlayer(ReputationDimension dimension, int limit) {
        String dimCol = dimension.name().toLowerCase();
        String sql = String.format(
            "SELECT player_id FROM player_reputation ORDER BY %s DESC LIMIT ?", dimCol
        );

        List<UUID> results = databaseService.query(sql, rs -> {
            List<UUID> ids = new ArrayList<>();
            try {
                while (rs.next()) {
                    ids.add(UUID.fromString(rs.getString("player_id")));
                }
            } catch (Exception e) {
                // Handle ResultSet errors
            }
            return ids;
        }, limit);

        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public int getRank(UUID playerId, ReputationDimension dimension) {
        String dimCol = dimension.name().toLowerCase();
        ReputationProfile profile = getProfile(playerId);
        int playerValue = profile.dimensions().getOrDefault(dimension, 0);

        String sql = String.format(
            "SELECT COUNT(*) + 1 FROM player_reputation WHERE %s > ?", dimCol
        );

        List<Integer> results = databaseService.query(sql, rs -> {
            List<Integer> ranks = new ArrayList<>();
            try {
                if (rs.next()) {
                    ranks.add(rs.getInt(1));
                }
            } catch (Exception e) {
                // Handle ResultSet errors
            }
            return ranks;
        }, playerValue);

        return results.isEmpty() ? 0 : results.get(0);
    }

    @Override
    public boolean hasStanding(UUID playerId, ReputationStanding standing) {
        int rep = getReputation(playerId);
        return switch (standing) {
            case VILLAIN -> rep < -50;
            case NEUTRAL -> rep >= -50 && rep < 50;
            case HERO -> rep >= 50 && rep < 200;
            case CHAMPION -> rep >= 200;
        };
    }
}
