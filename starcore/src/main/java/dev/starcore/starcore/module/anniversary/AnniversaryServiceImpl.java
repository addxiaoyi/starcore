package dev.starcore.starcore.module.anniversary;

import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.anniversary.event.AnniversaryApproachingEvent;
import dev.starcore.starcore.module.anniversary.event.AnniversaryCelebratedEvent;
import dev.starcore.starcore.module.anniversary.event.AnniversaryCreatedEvent;
import dev.starcore.starcore.module.anniversary.event.AnniversaryDeletedEvent;
import dev.starcore.starcore.module.anniversary.model.AnniversaryType;
import dev.starcore.starcore.module.anniversary.model.NationAnniversary;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 国家纪念日服务实现
 */
public final class AnniversaryServiceImpl implements AnniversaryService {
    private static final String PERSISTENCE_NAMESPACE = "anniversary";
    private static final String STATE_FILE = "anniversaries.dat";

    private final Plugin plugin;
    private final MessageService messages;
    private final PersistenceService persistenceService;
    private final Logger logger;

    // 所有纪念日（内存中）
    private final Map<UUID, NationAnniversary> anniversaries = new ConcurrentHashMap<>();
    // 国家索引
    private final Map<UUID, Set<UUID>> nationAnniversaries = new ConcurrentHashMap<>();
    // 缓存
    private final Map<UUID, List<NationAnniversary>> upcomingCache = new ConcurrentHashMap<>();
    private long lastCacheUpdate = 0;
    private static final long CACHE_DURATION_MS = 60 * 1000; // 1分钟缓存

    public AnniversaryServiceImpl(
        Plugin plugin,
        MessageService messages,
        PersistenceService persistenceService
    ) {
        this.plugin = plugin;
        this.messages = messages;
        this.persistenceService = persistenceService;
        this.logger = plugin.getLogger();
    }

    @Override
    public NationAnniversary createAnniversary(UUID nationId, String name, LocalDate date,
                                               AnniversaryType type, String description) {
        NationAnniversary anniversary = NationAnniversary.create(nationId, name, date, type, description);

        anniversaries.put(anniversary.id(), anniversary);
        nationAnniversaries.computeIfAbsent(nationId, k -> ConcurrentHashMap.newKeySet())
            .add(anniversary.id());

        // 触发事件
        Bukkit.getPluginManager().callEvent(new AnniversaryCreatedEvent(anniversary));

        // 持久化
        persistAnniversary(anniversary);

        logger.info("Created anniversary: " + name + " for nation " + nationId);

        return anniversary;
    }

    @Override
    public boolean deleteAnniversary(UUID anniversaryId) {
        NationAnniversary anniversary = anniversaries.remove(anniversaryId);
        if (anniversary == null) {
            return false;
        }

        Set<UUID> nationAnns = nationAnniversaries.get(anniversary.nationId());
        if (nationAnns != null) {
            nationAnns.remove(anniversaryId);
        }

        // 触发事件
        Bukkit.getPluginManager().callEvent(new AnniversaryDeletedEvent(anniversary));

        // 从持久化中移除
        removePersistedAnniversary(anniversaryId);

        logger.info("Deleted anniversary: " + anniversary.name());

        return true;
    }

    @Override
    public List<NationAnniversary> getAnniversaries(UUID nationId) {
        Set<UUID> ids = nationAnniversaries.getOrDefault(nationId, Collections.emptySet());
        return ids.stream()
            .map(anniversaries::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Override
    public List<NationAnniversary> getUpcomingAnniversaries(UUID nationId, int days) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(days);

        return getAnniversaries(nationId).stream()
            .filter(ann -> {
                LocalDate thisYearDate = ann.getThisYearDate();
                return !thisYearDate.isBefore(today) && !thisYearDate.isAfter(endDate);
            })
            .sorted(Comparator.comparing(NationAnniversary::getThisYearDate))
            .collect(Collectors.toList());
    }

    @Override
    public List<NationAnniversary> getTodayAnniversaries(UUID nationId) {
        return getAnniversaries(nationId).stream()
            .filter(NationAnniversary::isToday)
            .collect(Collectors.toList());
    }

    @Override
    public List<NationAnniversary> getAllTodayAnniversaries() {
        return anniversaries.values().stream()
            .filter(NationAnniversary::isToday)
            .collect(Collectors.toList());
    }

    @Override
    public List<NationAnniversary> getAllUpcomingAnniversaries(int days) {
        refreshCacheIfNeeded();

        List<NationAnniversary> all = new ArrayList<>();
        for (Set<UUID> ids : nationAnniversaries.values()) {
            for (UUID id : ids) {
                NationAnniversary ann = anniversaries.get(id);
                if (ann != null && ann.daysUntil() >= 0 && ann.daysUntil() <= days) {
                    all.add(ann);
                }
            }
        }

        return all.stream()
            .sorted(Comparator.comparing(NationAnniversary::daysUntil))
            .collect(Collectors.toList());
    }

    @Override
    public Optional<NationAnniversary> getAnniversary(UUID anniversaryId) {
        return Optional.ofNullable(anniversaries.get(anniversaryId));
    }

    @Override
    public boolean updateAnniversary(NationAnniversary anniversary) {
        if (!anniversaries.containsKey(anniversary.id())) {
            return false;
        }
        anniversaries.put(anniversary.id(), anniversary);
        persistAnniversary(anniversary);
        return true;
    }

    @Override
    public NationAnniversary getFoundingAnniversary(UUID nationId, LocalDate foundingDate) {
        // 检查是否已存在成立纪念日
        List<NationAnniversary> existing = getAnniversaries(nationId).stream()
            .filter(a -> a.type() == AnniversaryType.FOUNDING)
            .toList();

        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        // 创建新的成立纪念日
        return createAnniversary(nationId, "国家成立纪念日", foundingDate,
            AnniversaryType.FOUNDING, "庆祝国家成立的日子");
    }

    @Override
    public int daysUntilAnniversary(NationAnniversary anniversary) {
        return anniversary.daysUntil();
    }

    @Override
    public int getAnniversaryYear(NationAnniversary anniversary) {
        return anniversary.getCurrentYear();
    }

    @Override
    public boolean isMilestoneAnniversary(NationAnniversary anniversary) {
        return anniversary.isMilestone();
    }

    @Override
    public void markAsCelebrated(UUID anniversaryId, LocalDateTime celebratedAt) {
        NationAnniversary anniversary = anniversaries.get(anniversaryId);
        if (anniversary != null) {
            anniversary.setLastCelebratedAt(celebratedAt);
            persistAnniversary(anniversary);

            // 触发庆祝事件
            Bukkit.getPluginManager().callEvent(new AnniversaryCelebratedEvent(anniversary));
        }
    }

    @Override
    public void saveState() {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = new Properties();
            for (Map.Entry<UUID, NationAnniversary> entry : anniversaries.entrySet()) {
                String key = entry.getKey().toString();
                String value = encodeAnniversary(entry.getValue());
                props.setProperty(key, value);
            }
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, STATE_FILE, props);
            logger.info("Saved " + anniversaries.size() + " anniversaries");
        } catch (Exception e) {
            logger.warning("Failed to save anniversaries: " + e.getMessage());
        }
    }

    @Override
    public void loadState() {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, STATE_FILE);
            for (String key : props.stringPropertyNames()) {
                try {
                    UUID id = UUID.fromString(key);
                    NationAnniversary anniversary = decodeAnniversary(id, props.getProperty(key));
                    anniversaries.put(id, anniversary);
                    nationAnniversaries.computeIfAbsent(anniversary.nationId(),
                        k -> ConcurrentHashMap.newKeySet()).add(id);
                } catch (Exception e) {
                    logger.warning("Failed to load anniversary " + key + ": " + e.getMessage());
                }
            }
            logger.info("Loaded " + anniversaries.size() + " anniversaries");
        } catch (Exception e) {
            logger.warning("Failed to load anniversaries: " + e.getMessage());
        }
    }

    @Override
    public String summary() {
        return anniversaries.size() + " anniversary(ies), " + nationAnniversaries.size() + " nation(s)";
    }

    // ==================== 私有方法 ====================

    private void refreshCacheIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCacheUpdate > CACHE_DURATION_MS) {
            upcomingCache.clear();
            lastCacheUpdate = now;
        }
    }

    private void persistAnniversary(NationAnniversary anniversary) {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, STATE_FILE);
            props.setProperty(anniversary.id().toString(), encodeAnniversary(anniversary));
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, STATE_FILE, props);
        } catch (Exception e) {
            logger.warning("Failed to persist anniversary " + anniversary.id() + ": " + e.getMessage());
        }
    }

    private void removePersistedAnniversary(UUID anniversaryId) {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, STATE_FILE);
            props.remove(anniversaryId.toString());
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, STATE_FILE, props);
        } catch (Exception e) {
            logger.warning("Failed to remove anniversary " + anniversaryId + ": " + e.getMessage());
        }
    }

    private String encodeAnniversary(NationAnniversary ann) {
        return String.join("|",
            ann.id().toString(),
            ann.nationId().toString(),
            ann.name(),
            ann.date().toString(),
            ann.type().name(),
            ann.description() != null ? ann.description() : "",
            ann.createdAt().toString(),
            ann.lastCelebratedAt() != null ? ann.lastCelebratedAt().toString() : "",
            String.valueOf(ann.isRecurring()),
            ann.celebrationMessage() != null ? ann.celebrationMessage() : ""
        );
    }

    private NationAnniversary decodeAnniversary(UUID id, String data) {
        String[] parts = data.split("\\|", -1);
        if (parts.length < 6) {
            throw new IllegalArgumentException("Invalid anniversary data");
        }

        return new NationAnniversary(
            id,
            UUID.fromString(parts[1]),
            parts[2],
            LocalDate.parse(parts[3]),
            AnniversaryType.valueOf(parts[4]),
            parts.length > 5 ? parts[5] : "",
            parts.length > 6 && !parts[6].isEmpty() ? LocalDateTime.parse(parts[6]) : LocalDateTime.now(),
            parts.length > 7 && !parts[7].isEmpty() ? LocalDateTime.parse(parts[7]) : null,
            parts.length > 8 ? Boolean.parseBoolean(parts[8]) : true,
            parts.length > 9 ? parts[9] : ""
        );
    }

    /**
     * 检查并触发即将到来的纪念日事件
     * 应该在定时任务中调用
     */
    public void checkUpcomingAnniversaries() {
        LocalDate today = LocalDate.now();

        for (NationAnniversary ann : anniversaries.values()) {
            LocalDate thisYearDate = ann.getThisYearDate();
            int daysUntil = ann.daysUntil();

            // 提前1天和7天触发事件
            if (daysUntil == 1 || daysUntil == 7) {
                Bukkit.getPluginManager().callEvent(
                    new AnniversaryApproachingEvent(ann, daysUntil)
                );
            }

            // 今天触发庆祝事件
            if (thisYearDate.equals(today) && !ann.isCelebrated()) {
                Bukkit.getPluginManager().callEvent(
                    new AnniversaryCelebratedEvent(ann)
                );
            }
        }
    }
}