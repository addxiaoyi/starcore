package dev.starcore.starcore.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 宗教服务
 * 管理宗教信仰和圣地系统
 */
public class ReligionService implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Religion> religionCache;
    private final Map<UUID, HolyPlace> holyPlaces;
    private Connection connection;

    private boolean enabled = true;

    public ReligionService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.religionCache = new ConcurrentHashMap<>();  // Bug修复 #5: 线程安全
        this.holyPlaces = new ConcurrentHashMap<>();  // Bug修复 #5: 线程安全
    }

    /**
     * 初始化服务
     */
    public void initialize() {
        initializeDatabase();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // 加载所有在线玩家的宗教信息
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadReligion(player.getUniqueId());
        }

        // 加载所有圣地
        loadAllHolyPlaces();

        // 定时保存数据
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveAllData, 6000L, 6000L);

        // 定时应用祝福效果
        Bukkit.getScheduler().runTaskTimer(plugin, this::applyBlessings, 200L, 200L);

        // 定时增加圣地圣力
        Bukkit.getScheduler().runTaskTimer(plugin, this::regenerateHolyPower, 1200L, 1200L);

        plugin.getLogger().info("宗教系统已启用");
    }

    /**
     * 初始化数据库
     */
    private void initializeDatabase() {
        try {
            String dbPath = plugin.getDataFolder().getAbsolutePath() + "/religion.db";
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            try (Statement stmt = connection.createStatement()) {
                // 玩家宗教表
                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_religion (" +
                    "player_id TEXT PRIMARY KEY," +
                    "religion_type TEXT NOT NULL," +
                    "faith INTEGER DEFAULT 0," +
                    "join_time BIGINT DEFAULT 0," +
                    "contributions INTEGER DEFAULT 0," +
                    "blessed INTEGER DEFAULT 0" +
                    ")"
                );

                // 圣地表
                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS holy_places (" +
                    "id TEXT PRIMARY KEY," +
                    "religion_type TEXT NOT NULL," +
                    "name TEXT NOT NULL," +
                    "world TEXT NOT NULL," +
                    "x DOUBLE," +
                    "y DOUBLE," +
                    "z DOUBLE," +
                    "radius INTEGER DEFAULT 50," +
                    "holy_power INTEGER DEFAULT 100," +
                    "last_blessing BIGINT DEFAULT 0" +
                    ")"
                );
            }

            plugin.getLogger().info("宗教数据库初始化完成");
        } catch (Exception e) {
            plugin.getLogger().severe("宗教数据库初始化失败: " + e.getMessage());
        }
    }

    /**
     * 玩家加入时加载宗教信息
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        loadReligion(event.getPlayer().getUniqueId());
    }

    /**
     * 玩家移动时检查是否进入圣地
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() &&
            from.getBlockY() == to.getBlockY() &&
            from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        Player player = event.getPlayer();
        Religion religion = getReligion(player.getUniqueId());

        if (religion == null) return;

        // 检查是否进入圣地
        for (HolyPlace holyPlace : holyPlaces.values()) {
            boolean wasInRange = holyPlace.isInRange(from);
            boolean isInRange = holyPlace.isInRange(to);

            // 刚进入圣地
            if (!wasInRange && isInRange) {
                onEnterHolyPlace(player, holyPlace);
            }
            // 刚离开圣地
            else if (wasInRange && !isInRange) {
                onLeaveHolyPlace(player, holyPlace);
            }
        }
    }

    /**
     * 进入圣地
     */
    private void onEnterHolyPlace(Player player, HolyPlace holyPlace) {
        Religion religion = getReligion(player.getUniqueId());

        if (religion != null && religion.getType() == holyPlace.getReligion()) {
            player.sendMessage("§a§l你进入了圣地: " + holyPlace.getName());
            player.sendMessage("§e你感受到了 " + holyPlace.getReligion().getDisplayName() + " 的神圣力量");

            // 增加信仰值
            religion.addFaith(1);
        } else {
            player.sendMessage("§7你进入了一片神圣的区域...");
        }
    }

    /**
     * 离开圣地
     */
    private void onLeaveHolyPlace(Player player, HolyPlace holyPlace) {
        Religion religion = getReligion(player.getUniqueId());

        if (religion != null && religion.getType() == holyPlace.getReligion()) {
            player.sendMessage("§7你离开了圣地: " + holyPlace.getName());
        }
    }

    /**
     * 应用宗教祝福效果
     */
    private void applyBlessings() {
        if (!enabled) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            Religion religion = getReligion(player.getUniqueId());
            if (religion == null || !religion.isBlessed()) continue;

            // 根据宗教类型应用不同的祝福效果
            switch (religion.getType()) {
                case NATURE:
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.REGENERATION, 600, 0, true, false));
                    break;
                case SUN:
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.STRENGTH, 600, 0, true, false));
                    break;
                case MOON:
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.LUCK, 600, 0, true, false));
                    break;
                case WAR:
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.RESISTANCE, 600, 0, true, false));
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.STRENGTH, 600, 0, true, false));
                    break;
                case PEACE:
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SATURATION, 600, 0, true, false));
                    break;
                case DEATH:
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.RESISTANCE, 600, 1, true, false));
                    break;
                case OCEAN:
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.WATER_BREATHING, 600, 0, true, false));
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.DOLPHINS_GRACE, 600, 0, true, false));
                    break;
                case MOUNTAIN:
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOW_FALLING, 600, 0, true, false));
                    break;
            }
        }
    }

    /**
     * 圣地圣力恢复
     */
    private void regenerateHolyPower() {
        for (HolyPlace holyPlace : holyPlaces.values()) {
            holyPlace.addHolyPower(5); // 每分钟恢复5点圣力
        }
    }

    /**
     * 加载玩家宗教信息（异步）
     */
    private void loadReligion(UUID playerId) {
        String playerIdStr = playerId.toString();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String sql = "SELECT * FROM player_religion WHERE player_id = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, playerIdStr);
                    ResultSet rs = stmt.executeQuery();

                    Religion religion = null;
                    if (rs.next()) {
                        ReligionType type = ReligionType.valueOf(rs.getString("religion_type"));
                        religion = new Religion(playerId, type);
                        religion.setFaith(rs.getInt("faith"));
                        religion.setJoinTime(rs.getLong("join_time"));
                        religion.setContributions(rs.getInt("contributions"));
                        religion.setBlessed(rs.getInt("blessed") == 1);
                    }

                    // 在主线程更新缓存
                    final Religion finalReligion = religion;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (finalReligion != null) {
                            religionCache.put(playerId, finalReligion);
                        }
                    });
                }
            } catch (Exception e) {
                plugin.getLogger().severe("加载宗教信息失败: " + e.getMessage());
            }
        });
    }

    /**
     * 加载所有圣地（异步）
     */
    private void loadAllHolyPlaces() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String sql = "SELECT * FROM holy_places";
                List<HolyPlace> loadedPlaces = new ArrayList<>();
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {

                    while (rs.next()) {
                        UUID id = UUID.fromString(rs.getString("id"));
                        ReligionType type = ReligionType.valueOf(rs.getString("religion_type"));
                        String name = rs.getString("name");

                        Location location = new Location(
                            Bukkit.getWorld(rs.getString("world")),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z")
                        );

                        int radius = rs.getInt("radius");
                        HolyPlace holyPlace = new HolyPlace(id, type, location, name, radius);
                        holyPlace.setHolyPower(rs.getInt("holy_power"));
                        holyPlace.setLastBlessing(rs.getLong("last_blessing"));

                        loadedPlaces.add(holyPlace);
                    }
                }

                // 在主线程更新缓存
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (HolyPlace hp : loadedPlaces) {
                        holyPlaces.put(hp.getId(), hp);
                    }
                    plugin.getLogger().info("已加载 " + loadedPlaces.size() + " 个圣地");
                });
            } catch (Exception e) {
                plugin.getLogger().severe("加载圣地失败: " + e.getMessage());
            }
        });
    }

    /**
     * 保存所有数据
     */
    private void saveAllData() {
        saveAllReligions();
        saveAllHolyPlaces();
    }

    /**
     * 保存所有玩家宗教信息
     */
    private void saveAllReligions() {
        for (Map.Entry<UUID, Religion> entry : religionCache.entrySet()) {
            saveReligion(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 保存玩家宗教信息（异步）
     */
    private void saveReligion(UUID playerId, Religion religion) {
        // 捕获需要在异步任务中使用的值
        String playerIdStr = playerId.toString();
        String religionTypeName = religion.getType().name();
        int faith = religion.getFaith();
        long joinTime = religion.getJoinTime();
        int contributions = religion.getContributions();
        int blessed = religion.isBlessed() ? 1 : 0;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String sql = "INSERT OR REPLACE INTO player_religion " +
                    "(player_id, religion_type, faith, join_time, contributions, blessed) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";

                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, playerIdStr);
                    stmt.setString(2, religionTypeName);
                    stmt.setInt(3, faith);
                    stmt.setLong(4, joinTime);
                    stmt.setInt(5, contributions);
                    stmt.setInt(6, blessed);

                    stmt.executeUpdate();
                }
            } catch (Exception e) {
                plugin.getLogger().severe("保存宗教信息失败: " + e.getMessage());
            }
        });
    }

    /**
     * 保存所有圣地
     */
    private void saveAllHolyPlaces() {
        for (HolyPlace holyPlace : holyPlaces.values()) {
            saveHolyPlace(holyPlace);
        }
    }

    /**
     * 保存圣地（异步）
     */
    private void saveHolyPlace(HolyPlace holyPlace) {
        // 捕获需要在异步任务中使用的值
        Location loc = holyPlace.getLocation();
        String idStr = holyPlace.getId().toString();
        String religionName = holyPlace.getReligion().name();
        String name = holyPlace.getName();
        String worldName = loc.getWorld().getName();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        int radius = holyPlace.getRadius();
        int holyPower = holyPlace.getHolyPower();
        long lastBlessing = holyPlace.getLastBlessing();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String sql = "INSERT OR REPLACE INTO holy_places " +
                    "(id, religion_type, name, world, x, y, z, radius, holy_power, last_blessing) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, idStr);
                    stmt.setString(2, religionName);
                    stmt.setString(3, name);
                    stmt.setString(4, worldName);
                    stmt.setDouble(5, x);
                    stmt.setDouble(6, y);
                    stmt.setDouble(7, z);
                    stmt.setInt(8, radius);
                    stmt.setInt(9, holyPower);
                    stmt.setLong(10, lastBlessing);

                    stmt.executeUpdate();
                }
            } catch (Exception e) {
                plugin.getLogger().severe("保存圣地失败: " + e.getMessage());
            }
        });
    }

    /**
     * 获取玩家宗教信息
     */
    public Religion getReligion(UUID playerId) {
        return religionCache.get(playerId);
    }

    /**
     * 设置玩家宗教信仰
     */
    public void setReligion(UUID playerId, ReligionType type) {
        Religion religion = religionCache.get(playerId);

        if (religion == null) {
            religion = new Religion(playerId, type);
            religionCache.put(playerId, religion);
        } else {
            religion.changeFaith(type);
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage("§a你已加入 " + type.getColoredName());
            player.sendMessage("§7" + type.getDescription());
        }
    }

    /**
     * 创建圣地
     */
    public HolyPlace createHolyPlace(ReligionType type, Location location, String name, int radius) {
        UUID id = UUID.randomUUID();
        HolyPlace holyPlace = new HolyPlace(id, type, location, name, radius);
        holyPlaces.put(id, holyPlace);
        saveHolyPlace(holyPlace);
        return holyPlace;
    }

    /**
     * 删除圣地（异步）
     */
    public void removeHolyPlace(UUID id) {
        holyPlaces.remove(id);
        String idStr = id.toString();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String sql = "DELETE FROM holy_places WHERE id = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, idStr);
                    stmt.executeUpdate();
                }
            } catch (Exception e) {
                plugin.getLogger().severe("删除圣地失败: " + e.getMessage());
            }
        });
    }

    /**
     * 获取所有圣地
     */
    public Collection<HolyPlace> getHolyPlaces() {
        return new ArrayList<>(holyPlaces.values());
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        saveAllData();

        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("关闭宗教数据库失败: " + e.getMessage());
        }

        religionCache.clear();
        holyPlaces.clear();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
