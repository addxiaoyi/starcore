package dev.starcore.starcore.module.faith;
import java.util.Optional;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.module.faith.event.FaithBlessingEvent;
import dev.starcore.starcore.module.faith.event.FaithLevelChangedEvent;
import dev.starcore.starcore.module.faith.event.FaithPrayerEvent;
import dev.starcore.starcore.module.faith.model.*;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 领土信仰服务实现
 */
public class FaithServiceImpl implements FaithService {
    private static final String DATA_FILE = "faith.dat";
    private static final int COOLDOWN_SECONDS = 60;

    private final Plugin plugin;
    private final PersistenceService persistenceService;
    private final Map<NationId, FaithData> faithDataMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> prayerCooldowns = new ConcurrentHashMap<>();
    private final Map<String, PrayerRecord> recentPrayers = new ConcurrentHashMap<>();

    private FaithConfig config;
    private File dataFile;

    public FaithServiceImpl(Plugin plugin, PersistenceService persistenceService, ConfigurationSection configSection) {
        this.plugin = plugin;
        this.persistenceService = persistenceService;
        this.config = FaithConfig.fromConfig(configSection);
    }

    @Override
    public void initialize() {
        this.dataFile = new File(plugin.getDataFolder(), DATA_FILE);
        loadData();
    }

    @Override
    public Optional<FaithData> getFaithData(NationId nationId) {
        return Optional.ofNullable(faithDataMap.get(nationId));
    }

    @Override
    public int getFaith(NationId nationId) {
        return faithDataMap.getOrDefault(nationId,
            FaithData.create(nationId, config.maxFaith() / 2, null)).faith();
    }

    @Override
    public boolean setFaith(NationId nationId, int faith) {
        FaithData current = faithDataMap.get(nationId);
        if (current == null) {
            return false;
        }
        int previousLevel = getFaithLevel(nationId);
        int clampedFaith = Math.max(0, Math.min(config.maxFaith(), faith));
        FaithData updated = current.withFaith(clampedFaith);
        faithDataMap.put(nationId, updated);

        // 检查等级变化
        int newLevel = getFaithLevel(nationId);
        if (previousLevel != newLevel) {
            triggerLevelChange(nationId, previousLevel, newLevel);
        }

        saveData();
        return true;
    }

    @Override
    public int addFaith(NationId nationId, int amount) {
        FaithData current = faithDataMap.get(nationId);
        if (current == null) {
            return 0;
        }
        int previousLevel = getFaithLevel(nationId);
        FaithData updated = current.addFaith(amount);
        faithDataMap.put(nationId, updated);

        // 检查等级变化
        int newLevel = getFaithLevel(nationId);
        if (previousLevel != newLevel) {
            triggerLevelChange(nationId, previousLevel, newLevel);
        }

        saveData();
        return updated.faith();
    }

    @Override
    public int getFaithLevel(NationId nationId) {
        int faith = getFaith(nationId);
        return config.calculateLevel(faith);
    }

    @Override
    public String getFaithLevelName(int level) {
        FaithLevelConfig levelConfig = config.levelConfigs().getOrDefault(level,
            FaithLevelConfig.defaultForLevel(level));
        return levelConfig.name();
    }

    @Override
    public Map<String, Double> getFaithBonuses(NationId nationId) {
        int level = getFaithLevel(nationId);
        FaithLevelConfig levelConfig = config.levelConfigs().getOrDefault(level,
            FaithLevelConfig.defaultForLevel(level));

        Map<String, Double> bonuses = new HashMap<>();
        bonuses.put("resource", levelConfig.resourceBonus());
        bonuses.put("defense", levelConfig.defenseBonus());
        bonuses.put("tax", levelConfig.taxBonus());
        bonuses.put("exp", levelConfig.expBonus());
        return bonuses;
    }

    @Override
    public void recordPrayer(UUID playerId, NationId nationId, int x, int y, int z, String world) {
        // 检查冷却
        long lastPrayer = prayerCooldowns.getOrDefault(playerId, 0L);
        if (System.currentTimeMillis() - lastPrayer < COOLDOWN_SECONDS * 1000L) {
            return;
        }

        FaithData current = faithDataMap.get(nationId);
        if (current == null) {
            return;
        }

        // 检查每日祈祷次数限制
        if (current.todayPrayers() >= config.maxDailyPrayers()) {
            return;
        }

        // 计算信仰获得量
        int faithGained = config.prayerFaithGain();

        // 创建事件
        FaithPrayerEvent event = new FaithPrayerEvent(
            playerId, nationId, x, y, z, world, current, faithGained
        );
        Bukkit.getPluginManager().callEvent(event);

        // 如果事件未被取消
        if (!event.isCancelled()) {
            faithGained = event.getFaithGained();

            // 更新数据
            FaithData updated = current.recordPrayer(playerId).addFaith(faithGained);
            faithDataMap.put(nationId, updated);

            // 记录冷却
            prayerCooldowns.put(playerId, System.currentTimeMillis());

            // 记录祈祷位置
            String key = nationId.toString() + ":" + playerId.toString();
            recentPrayers.put(key, PrayerRecord.create(playerId, nationId.toString(), x, y, z, world, faithGained));

            // 检查信仰事件
            checkFaithEvents(nationId);

            // 保存数据
            saveData();
        }
    }

    @Override
    public void checkFaithEvents(NationId nationId) {
        FaithData data = faithDataMap.get(nationId);
        if (data == null) return;

        int level = getFaithLevel(nationId);

        // 检查是否触发特殊事件
        // 例如：信仰达到特定阈值时的特殊事件
        if (data.faith() >= 100 && level < 5) {
            // 触发满信仰事件
            triggerFaithEvent(nationId, "max-faith-reached");
        }
    }

    @Override
    public FaithStats getStats(NationId nationId) {
        FaithData data = faithDataMap.get(nationId);
        if (data == null) {
            return FaithStats.empty();
        }

        int level = getFaithLevel(nationId);
        String levelName = getFaithLevelName(level);
        Map<String, Double> bonuses = getFaithBonuses(nationId);

        return new FaithStats(
            data.faith(),
            level,
            levelName,
            data.totalPrayers(),
            data.todayPrayers(),
            data.consecutiveDays(),
            bonuses.getOrDefault("resource", 0.0),
            bonuses.getOrDefault("defense", 0.0),
            bonuses.getOrDefault("tax", 0.0),
            bonuses.getOrDefault("exp", 0.0)
        );
    }

    @Override
    public boolean useFaithBlessing(NationId nationId, String blessingType) {
        FaithData data = faithDataMap.get(nationId);
        if (data == null) {
            return false;
        }

        Double cost = config.blessingCosts().get(blessingType);
        if (cost == null) {
            return false;
        }

        int faithCost = cost.intValue();
        if (data.faith() < faithCost) {
            return false;
        }

        // 扣除信仰值
        boolean updated = setFaith(nationId, data.faith() - faithCost);

        // 触发事件
        FaithBlessingEvent event = new FaithBlessingEvent(nationId, blessingType, faithCost, updated);
        Bukkit.getPluginManager().callEvent(event);

        return updated;
    }

    @Override
    public FaithConfig getConfig() {
        return config;
    }

    @Override
    public void saveAll() {
        saveData();
    }

    @Override
    public void initializeFaith(NationId nationId, UUID founderId) {
        if (!faithDataMap.containsKey(nationId)) {
            FaithData initialData = FaithData.create(nationId, founderId);
            faithDataMap.put(nationId, initialData);
            saveData();
        }
    }

    @Override
    public void removeFaith(NationId nationId) {
        faithDataMap.remove(nationId);
        saveData();
    }

    @Override
    public int getMaxFaith() {
        return config.maxFaith();
    }

    @Override
    public int getFaithThreshold(int level) {
        return config.getThreshold(level);
    }

    private void triggerLevelChange(NationId nationId, int previousLevel, int newLevel) {
        String previousName = getFaithLevelName(previousLevel);
        String newName = getFaithLevelName(newLevel);

        FaithLevelChangedEvent event = new FaithLevelChangedEvent(
            nationId, previousLevel, newLevel, previousName, newName
        );
        Bukkit.getPluginManager().callEvent(event);
    }

    private void triggerFaithEvent(NationId nationId, String eventType) {
        FaithData data = faithDataMap.get(nationId);
        if (data == null) return;

        // 检查冷却
        long cooldown = config.faithEventCooldownMinutes() * 60 * 1000L;
        if (System.currentTimeMillis() - data.lastEventTime() < cooldown) {
            return;
        }

        FaithData updated = data.withLastEventTime(System.currentTimeMillis());
        faithDataMap.put(nationId, updated);
    }

    private void loadData() {
        if (!dataFile.exists()) {
            return;
        }

        try (DataInputStream dis = new DataInputStream(new FileInputStream(dataFile))) {
            int version = dis.readInt();
            int count = dis.readInt();

            for (int i = 0; i < count; i++) {
                try {
                    String nationIdStr = dis.readUTF();
                    NationId nationId = NationId.of(UUID.fromString(nationIdStr));
                    int faith = dis.readInt();
                    int totalPrayers = dis.readInt();
                    int todayPrayers = dis.readInt();
                    long lastPrayerTime = dis.readLong();
                    long lastEventTime = dis.readLong();
                    int consecutiveDays = dis.readInt();
                    String playerIdStr = dis.readUTF();
                    UUID lastPrayingPlayer = playerIdStr.isEmpty() ? null : UUID.fromString(playerIdStr);

                    FaithData data = new FaithData(
                        nationId, faith, totalPrayers, todayPrayers,
                        lastPrayerTime, lastEventTime, consecutiveDays, lastPrayingPlayer
                    );
                    faithDataMap.put(nationId, data);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load faith data for entry " + i);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load faith data: " + e.getMessage());
        }
    }

    private void saveData() {
        try {
            // 确保目录存在
            File dir = dataFile.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }

            try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(dataFile))) {
                dos.writeInt(1); // 版本号
                dos.writeInt(faithDataMap.size());

                for (Map.Entry<NationId, FaithData> entry : faithDataMap.entrySet()) {
                    FaithData data = entry.getValue();
                    dos.writeUTF(entry.getKey().toString());
                    dos.writeInt(data.faith());
                    dos.writeInt(data.totalPrayers());
                    dos.writeInt(data.todayPrayers());
                    dos.writeLong(data.lastPrayerTime());
                    dos.writeLong(data.lastEventTime());
                    dos.writeInt(data.consecutiveDays());
                    dos.writeUTF(data.lastPrayingPlayer() != null ? data.lastPrayingPlayer().toString() : "");
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save faith data: " + e.getMessage());
        }
    }
}