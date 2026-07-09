package dev.starcore.starcore.module.dynasty;
import java.util.Optional;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.foundation.player.PlayerProfileService;
import dev.starcore.starcore.module.dynasty.event.DynastyCreatedEvent;
import dev.starcore.starcore.module.dynasty.event.InterregnumEvent;
import dev.starcore.starcore.module.dynasty.event.SuccessionEvent;
import dev.starcore.starcore.module.dynasty.model.Dynasty;
import dev.starcore.starcore.module.dynasty.model.SuccessionType;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 王位继承服务实现
 */
public final class DynastyServiceImpl implements DynastyService {
    private final Plugin plugin;
    private final DatabaseService databaseService;
    private final NationService nationService;
    private final PlayerProfileService playerProfiles;
    private final StarCoreEventBus eventBus;
    private final Logger logger;

    private final Map<NationId, Dynasty> dynastiesByNation = new ConcurrentHashMap<>();
    private final Set<NationId> interregnumNations = ConcurrentHashMap.newKeySet();

    public DynastyServiceImpl(
            Plugin plugin,
            DatabaseService databaseService,
            NationService nationService,
            PlayerProfileService playerProfiles,
            StarCoreEventBus eventBus) {
        this.plugin = plugin;
        this.databaseService = databaseService;
        this.nationService = nationService;
        this.playerProfiles = playerProfiles;
        this.eventBus = eventBus;
        this.logger = plugin.getLogger();
    }

    /**
     * 初始化数据库表
     */
    public void initializeTables() {
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                String sql = """
                    CREATE TABLE IF NOT EXISTS starcore_dynasties (
                        nation_id VARCHAR(36) PRIMARY KEY,
                        dynasty_name VARCHAR(100) NOT NULL,
                        current_monarch_id VARCHAR(36),
                        current_monarch_name VARCHAR(100),
                        succession_type VARCHAR(50) NOT NULL,
                        succession_order TEXT,
                        created_at TIMESTAMP NOT NULL,
                        monarch_since TIMESTAMP,
                        reign_count INT DEFAULT 0,
                        interregnum_start TIMESTAMP,
                        succession_title VARCHAR(50) DEFAULT 'Monarch'
                    )
                """;
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }

                // 创建继承人表
                String heirSql = """
                    CREATE TABLE IF NOT EXISTS starcore_dynasty_heirs (
                        nation_id VARCHAR(36) NOT NULL,
                        player_id VARCHAR(36) NOT NULL,
                        player_name VARCHAR(100) NOT NULL,
                        position INT NOT NULL,
                        added_at TIMESTAMP NOT NULL,
                        PRIMARY KEY (nation_id, player_id),
                        FOREIGN KEY (nation_id) REFERENCES starcore_dynasties(nation_id) ON DELETE CASCADE
                    )
                """;
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(heirSql);
                }

                logger.info("Dynasty tables initialized");
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to initialize dynasty tables", e);
            }
        });
    }

    @Override
    public Optional<Dynasty> getDynasty(NationId nationId) {
        return Optional.ofNullable(dynastiesByNation.get(nationId));
    }

    @Override
    public Dynasty createDynasty(NationId nationId, UUID founderId, String founderName) {
        Dynasty dynasty = new Dynasty(nationId, founderId, founderName, null);
        dynastiesByNation.put(nationId, dynasty);

        // 保存到数据库
        saveDynasty(dynasty);

        // 发布事件
        plugin.getServer().getPluginManager().callEvent(
            new DynastyCreatedEvent(nationId, dynasty, founderId, founderName)
        );

        return dynasty;
    }

    @Override
    public SuccessionResult abdicate(NationId nationId, UUID currentMonarch, UUID newMonarch, String reason) {
        Dynasty dynasty = dynastiesByNation.get(nationId);
        if (dynasty == null) {
            return SuccessionResult.fail("dynasty.not-found");
        }

        if (!dynasty.isMonarch(currentMonarch)) {
            return SuccessionResult.fail("dynasty.not-monarch");
        }

        // 获取新君主名称
        String newMonarchName = getPlayerName(newMonarch);
        String previousMonarchName = dynasty.currentMonarchName();

        // 更新王朝状态
        dynasty.setMonarch(newMonarch, newMonarchName);

        // 如果继承人列表中没有新君主，添加到列表末尾
        if (!dynasty.hasHeir(newMonarch)) {
            dynasty.addHeir(currentMonarch, previousMonarchName);
        }

        // 保存状态
        saveDynasty(dynasty);

        // 发布继承事件
        plugin.getServer().getPluginManager().callEvent(new SuccessionEvent(
            nationId,
            dynasty,
            SuccessionEvent.SuccessionKind.ABDICATION,
            currentMonarch,
            previousMonarchName,
            newMonarch,
            newMonarchName,
            reason
        ));

        return SuccessionResult.ok("dynasty.abdication.success", dynasty);
    }

    @Override
    public SuccessionResult inherit(NationId nationId, UUID inheritorId) {
        Dynasty dynasty = dynastiesByNation.get(nationId);
        if (dynasty == null) {
            return SuccessionResult.fail("dynasty.not-found");
        }

        if (dynasty.hasMonarch()) {
            return SuccessionResult.fail("dynasty.has-monarch");
        }

        String inheritorName = getPlayerName(inheritorId);

        // 检查继承人资格
        SuccessionResult validation = validateInheritance(dynasty, inheritorId);
        if (!validation.success()) {
            return validation;
        }

        // 执行继承
        dynasty.setMonarch(inheritorId, inheritorName);
        interregnumNations.remove(nationId);

        // 保存状态
        saveDynasty(dynasty);

        // 发布继承事件
        plugin.getServer().getPluginManager().callEvent(new SuccessionEvent(
            nationId,
            dynasty,
            SuccessionEvent.SuccessionKind.INHERITANCE,
            null,
            null,
            inheritorId,
            inheritorName,
            "Automatic succession"
        ));

        return SuccessionResult.ok("dynasty.inheritance.success", dynasty);
    }

    @Override
    public SuccessionResult addHeir(NationId nationId, UUID monarchId, UUID heirId) {
        Dynasty dynasty = dynastiesByNation.get(nationId);
        if (dynasty == null) {
            return SuccessionResult.fail("dynasty.not-found");
        }

        if (!dynasty.isMonarch(monarchId)) {
            return SuccessionResult.fail("dynasty.not-monarch");
        }

        if (dynasty.hasHeir(heirId)) {
            return SuccessionResult.fail("dynasty.heir-exists");
        }

        String heirName = getPlayerName(heirId);
        dynasty.addHeir(heirId, heirName);

        saveDynasty(dynasty);
        return SuccessionResult.ok("dynasty.heir.added", dynasty);
    }

    @Override
    public SuccessionResult removeHeir(NationId nationId, UUID monarchId, UUID heirId) {
        Dynasty dynasty = dynastiesByNation.get(nationId);
        if (dynasty == null) {
            return SuccessionResult.fail("dynasty.not-found");
        }

        if (!dynasty.isMonarch(monarchId)) {
            return SuccessionResult.fail("dynasty.not-monarch");
        }

        if (!dynasty.hasHeir(heirId)) {
            return SuccessionResult.fail("dynasty.heir-not-found");
        }

        dynasty.removeHeir(heirId);

        saveDynasty(dynasty);
        return SuccessionResult.ok("dynasty.heir.removed", dynasty);
    }

    @Override
    public List<HeirRecord> getHeirs(NationId nationId) {
        Dynasty dynasty = dynastiesByNation.get(nationId);
        if (dynasty == null) {
            return List.of();
        }

        List<HeirRecord> heirs = new ArrayList<>();
        List<Dynasty.HeirInfo> successionOrder = dynasty.successionOrder();
        for (int i = 0; i < successionOrder.size(); i++) {
            Dynasty.HeirInfo heir = successionOrder.get(i);
            heirs.add(new HeirRecord(heir.playerId(), heir.playerName(), i + 1, heir.addedAt()));
        }
        return heirs;
    }

    @Override
    public SuccessionResult setSuccessionType(NationId nationId, UUID monarchId, SuccessionType type) {
        Dynasty dynasty = dynastiesByNation.get(nationId);
        if (dynasty == null) {
            return SuccessionResult.fail("dynasty.not-found");
        }

        if (!dynasty.isMonarch(monarchId)) {
            return SuccessionResult.fail("dynasty.not-monarch");
        }

        dynasty.setSuccessionType(type);
        saveDynasty(dynasty);

        return SuccessionResult.ok("dynasty.succession-type.set", dynasty);
    }

    @Override
    public boolean isInterregnum(NationId nationId) {
        Dynasty dynasty = dynastiesByNation.get(nationId);
        return dynasty != null && dynasty.isInInterregnum();
    }

    @Override
    public Optional<Instant> getInterregnumStart(NationId nationId) {
        Dynasty dynasty = dynastiesByNation.get(nationId);
        return dynasty != null ? dynasty.interregnumStart() : Optional.empty();
    }

    @Override
    public SuccessionResult claimCrown(NationId nationId, UUID claimantId) {
        Dynasty dynasty = dynastiesByNation.get(nationId);
        if (dynasty == null) {
            return SuccessionResult.fail("dynasty.not-found");
        }

        if (dynasty.hasMonarch()) {
            return SuccessionResult.fail("dynasty.has-monarch");
        }

        // 检查索取者是否是第一位继承人
        Optional<Dynasty.HeirInfo> firstHeir = dynasty.getFirstHeir();
        if (firstHeir.isEmpty() || !firstHeir.get().playerId().equals(claimantId)) {
            return SuccessionResult.fail("dynasty.not-first-heir");
        }

        String claimantName = getPlayerName(claimantId);
        dynasty.setMonarch(claimantId, claimantName);
        interregnumNations.remove(nationId);

        saveDynasty(dynasty);

        // 发布加冕事件
        plugin.getServer().getPluginManager().callEvent(new SuccessionEvent(
            nationId,
            dynasty,
            SuccessionEvent.SuccessionKind.CORONATION,
            null,
            null,
            claimantId,
            claimantName,
            "Claimed the crown"
        ));

        return SuccessionResult.ok("dynasty.crown.claimed", dynasty);
    }

    @Override
    public void renounceClaim(NationId nationId, UUID claimantId) {
        Dynasty dynasty = dynastiesByNation.get(nationId);
        if (dynasty != null) {
            dynasty.removeHeir(claimantId);
            saveDynasty(dynasty);
        }
    }

    @Override
    public Collection<NationId> getInterregnumNations() {
        return Set.copyOf(interregnumNations);
    }

    @Override
    public void saveState() {
        for (Dynasty dynasty : dynastiesByNation.values()) {
            saveDynasty(dynasty);
        }
    }

    @Override
    public String summary() {
        long withMonarch = dynastiesByNation.values().stream().filter(Dynasty::hasMonarch).count();
        long inInterregnum = interregnumNations.size();
        return String.format("Dynasty System: %d dynasties (%d with monarch, %d in interregnum)",
                dynastiesByNation.size(), withMonarch, inInterregnum);
    }

    /**
     * 验证继承资格
     */
    private SuccessionResult validateInheritance(Dynasty dynasty, UUID inheritorId) {
        // 检查继承类型
        SuccessionType type = dynasty.successionType();

        switch (type) {
            case MALE_PREMIogeniture, ULTIMogeniture, PRIMOGENITURE, HEREDITARY -> {
                // 这些类型需要继承人列表中有玩家
                if (!dynasty.hasHeir(inheritorId)) {
                    return SuccessionResult.fail("dynasty.not-in-heir-list");
                }
                // 检查是否是第一位
                Optional<Integer> position = dynasty.getHeirPosition(inheritorId);
                if (position.isEmpty() || position.get() > 1) {
                    return SuccessionResult.fail("dynasty.not-first-heir");
                }
            }
            case ELECTIVE_MONARCHY, PARLIAMENTARY -> {
                // 这些类型需要有多位继承人
                if (dynasty.successionOrder().isEmpty()) {
                    return SuccessionResult.fail("dynasty.no-heirs-for-election");
                }
            }
            case APPOINTMENT, ABSOLUTE -> {
                // 这些类型较宽松，只需要是国家成员即可
                // 可以在这里添加额外的国家成员检查
            }
        }

        return SuccessionResult.ok("dynasty.validation.passed", dynasty);
    }

    /**
     * 获取玩家名称（从缓存或数据库）
     */
    private String getPlayerName(UUID playerId) {
        // 尝试从 playerProfiles 获取
        var snapshot = playerProfiles.snapshot();
        if (snapshot.containsKey(playerId)) {
            return snapshot.get(playerId);
        }
        // 回退到 Bukkit
        var offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer(playerId);
        return offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";
    }

    /**
     * 保存王朝到数据库
     */
    private void saveDynasty(Dynasty dynasty) {
        databaseService.dataSource().ifPresent(ds -> {
            String sql = """
                INSERT INTO starcore_dynasties (nation_id, dynasty_name, current_monarch_id, current_monarch_name,
                    succession_type, created_at, monarch_since, reign_count, interregnum_start, succession_title)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (nation_id) DO UPDATE SET
                    dynasty_name = EXCLUDED.dynasty_name,
                    current_monarch_id = EXCLUDED.current_monarch_id,
                    current_monarch_name = EXCLUDED.current_monarch_name,
                    succession_type = EXCLUDED.succession_type,
                    monarch_since = EXCLUDED.monarch_since,
                    reign_count = EXCLUDED.reign_count,
                    interregnum_start = EXCLUDED.interregnum_start,
                    succession_title = EXCLUDED.succession_title
            """;

            try (Connection conn = ds.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, dynasty.nationId().toString());
                stmt.setString(2, dynasty.dynastyName());
                stmt.setString(3, dynasty.currentMonarchId() != null ? dynasty.currentMonarchId().toString() : null);
                stmt.setString(4, dynasty.currentMonarchName());
                stmt.setString(5, dynasty.successionType().name());
                stmt.setTimestamp(6, Timestamp.from(dynasty.createdAt()));
                stmt.setTimestamp(7, dynasty.monarchSince() != null ? Timestamp.from(dynasty.monarchSince()) : null);
                stmt.setInt(8, dynasty.reignCount());
                stmt.setTimestamp(9, dynasty.interregnumStart().isPresent() ? Timestamp.from(dynasty.interregnumStart().get()) : null);
                stmt.setString(10, dynasty.successionTitle());
                stmt.executeUpdate();

                // 保存继承人列表
                saveHeirs(dynasty);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to save dynasty: " + dynasty.nationId(), e);
            }
        });
    }

    /**
     * 保存继承人列表
     */
    private void saveHeirs(Dynasty dynasty) {
        databaseService.dataSource().ifPresent(ds -> {
            // 先删除旧的继承人
            String deleteSql = "DELETE FROM starcore_dynasty_heirs WHERE nation_id = ?";
            try (Connection conn = ds.getConnection();
                 PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setString(1, dynasty.nationId().toString());
                deleteStmt.executeUpdate();

                // 插入新的继承人
                String insertSql = """
                    INSERT INTO starcore_dynasty_heirs (nation_id, player_id, player_name, position, added_at)
                    VALUES (?, ?, ?, ?, ?)
                """;
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    for (int i = 0; i < dynasty.successionOrder().size(); i++) {
                        Dynasty.HeirInfo heir = dynasty.successionOrder().get(i);
                        insertStmt.setString(1, dynasty.nationId().toString());
                        insertStmt.setString(2, heir.playerId().toString());
                        insertStmt.setString(3, heir.playerName());
                        insertStmt.setInt(4, i + 1);
                        insertStmt.setTimestamp(5, Timestamp.from(heir.addedAt()));
                        insertStmt.addBatch();
                    }
                    insertStmt.executeBatch();
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to save dynasty heirs: " + dynasty.nationId(), e);
            }
        });
    }

    /**
     * 从数据库加载所有王朝
     */
    public void loadAll() {
        databaseService.dataSource().ifPresent(ds -> {
            String sql = "SELECT * FROM starcore_dynasties";
            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    loadDynasty(rs);
                }
                logger.info("Loaded " + dynastiesByNation.size() + " dynasties from database");
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to load dynasties", e);
            }
        });
    }

    /**
     * 从ResultSet加载单个王朝
     */
    private void loadDynasty(ResultSet rs) throws SQLException {
        NationId nationId = NationId.of(UUID.fromString(rs.getString("nation_id")));
        String dynastyName = rs.getString("dynasty_name");
        UUID monarchId = rs.getString("current_monarch_id") != null
                ? UUID.fromString(rs.getString("current_monarch_id")) : null;
        String monarchName = rs.getString("current_monarch_name");
        SuccessionType successionType = SuccessionType.valueOf(rs.getString("succession_type"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp monarchSince = rs.getTimestamp("monarch_since");
        int reignCount = rs.getInt("reign_count");
        Timestamp interregnumStart = rs.getTimestamp("interregnum_start");
        String successionTitle = rs.getString("succession_title");

        Dynasty dynasty = new Dynasty(nationId, monarchId != null ? monarchId : UUID.randomUUID(),
                monarchName != null ? monarchName : "Unknown", dynastyName);

        // 使用反射设置字段（因为构造函数会自动添加君主）
        if (monarchId == null) {
            dynasty.clearMonarch();
        }

        dynasty.setSuccessionType(successionType);
        if (monarchSince != null) {
            // 需要通过 setMonarch 来更新时间
            if (monarchId != null) {
                dynasty.setMonarch(monarchId, monarchName);
            }
        }

        // 设置其他字段
        java.lang.reflect.Method setTitle = null;
        try {
            setTitle = Dynasty.class.getMethod("setSuccessionTitle", String.class);
            setTitle.invoke(dynasty, successionTitle);
        } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            // 方法不存在或无法调用，忽略
        }

        dynastiesByNation.put(nationId, dynasty);

        // 如果处于空缺期，添加到空缺列表
        if (dynasty.isInInterregnum()) {
            interregnumNations.add(nationId);
        }

        // 加载继承人
        loadHeirs(nationId);
    }

    /**
     * 加载继承人列表
     */
    private void loadHeirs(NationId nationId) {
        databaseService.dataSource().ifPresent(ds -> {
            String sql = "SELECT * FROM starcore_dynasty_heirs WHERE nation_id = ? ORDER BY position";
            try (Connection conn = ds.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, nationId.toString());
                ResultSet rs = stmt.executeQuery();
                Dynasty dynasty = dynastiesByNation.get(nationId);
                if (dynasty != null) {
                    while (rs.next()) {
                        UUID playerId = UUID.fromString(rs.getString("player_id"));
                        String playerName = rs.getString("player_name");
                        Instant addedAt = rs.getTimestamp("added_at").toInstant();
                        dynasty.addHeir(playerId, playerName);
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to load dynasty heirs: " + nationId, e);
            }
        });
    }

    /**
     * 触发王位空缺事件
     */
    public void triggerInterregnum(NationId nationId, InterregnumEvent.InterregnumCause cause) {
        Dynasty dynasty = dynastiesByNation.get(nationId);
        if (dynasty == null || !dynasty.hasMonarch()) {
            return;
        }

        UUID formerMonarchId = dynasty.currentMonarchId();
        String formerMonarchName = dynasty.currentMonarchName();
        Instant startTime = Instant.now();

        dynasty.clearMonarch();
        interregnumNations.add(nationId);
        saveDynasty(dynasty);

        plugin.getServer().getPluginManager().callEvent(new InterregnumEvent(
            nationId, dynasty, cause, startTime, formerMonarchId, formerMonarchName
        ));
    }

    /**
     * 为国家创建王朝（当国家被创建时调用）
     */
    public Dynasty createForNation(Nation nation) {
        return createDynasty(nation.id(), nation.founderId(), nation.founderId().toString());
    }
}