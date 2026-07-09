package dev.starcore.starcore.module.emergency;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.emergency.event.EmergencyDeclaredEvent;
import dev.starcore.starcore.module.emergency.event.EmergencyCancelledEvent;
import dev.starcore.starcore.module.emergency.event.EmergencyExpiredEvent;
import dev.starcore.starcore.module.emergency.model.EmergencyState;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 紧急状态服务实现
 */
public final class EmergencyServiceImpl implements EmergencyService {

    private final Map<NationId, EmergencyState> emergencies = new ConcurrentHashMap<>();
    private final AtomicInteger checkCounter = new AtomicInteger(0);

    private final Plugin plugin;
    private final NationService nationService;
    private final MessageService messages;
    private final StarCoreEventBus eventBus;
    private final PersistenceService persistenceService;
    private final StarCoreScheduler scheduler;
    private final EmergencyStateStorage stateStorage;

    private int cleanupTaskId = -1;

    public EmergencyServiceImpl(
        Plugin plugin,
        NationService nationService,
        MessageService messages,
        StarCoreEventBus eventBus,
        PersistenceService persistenceService,
        StarCoreScheduler scheduler
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.messages = messages;
        this.eventBus = eventBus;
        this.persistenceService = persistenceService;
        this.scheduler = scheduler;
        this.stateStorage = new DatabaseAwareEmergencyStateStorage(plugin, scheduler);
    }

    @Override
    public boolean declareEmergency(NationId nationId, EmergencyState.EmergencyType type, String reason, int durationMinutes) {
        // 验证国家存在
        if (nationService.nationById(nationId).isEmpty()) {
            return false;
        }

        // 检查是否已存在活跃的紧急状态
        Optional<EmergencyState> existing = getEmergencyState(nationId);
        if (existing.isPresent() && existing.get().isActive()) {
            // 检查是否可以叠加
            EmergencyState current = existing.get();
            if (!type.canStackWith(current.type())) {
                return false; // 不能叠加
            }
        }

        // 创建新的紧急状态
        EmergencyState emergency = new EmergencyState(nationId, type, reason, durationMinutes, null);
        emergencies.put(nationId, emergency);

        // 保存状态
        saveState();

        // 发布事件
        EmergencyDeclaredEvent event = new EmergencyDeclaredEvent(emergency);
        Bukkit.getPluginManager().callEvent(event);
        if (eventBus != null) {
            eventBus.publish(event);
        }

        return true;
    }

    @Override
    public boolean cancelEmergency(NationId nationId) {
        EmergencyState emergency = emergencies.get(nationId);
        if (emergency == null || !emergency.isActive()) {
            return false;
        }

        emergency.cancel(null); // 取消者信息可通过事件获取
        emergencies.remove(nationId);

        // 保存状态
        saveState();

        // 发布事件
        EmergencyCancelledEvent event = new EmergencyCancelledEvent(emergency);
        Bukkit.getPluginManager().callEvent(event);
        if (eventBus != null) {
            eventBus.publish(event);
        }

        return true;
    }

    @Override
    public Optional<EmergencyState> getEmergencyState(NationId nationId) {
        EmergencyState emergency = emergencies.get(nationId);
        if (emergency != null && emergency.isActive()) {
            return Optional.of(emergency);
        }
        // 清理过期状态
        if (emergency != null && emergency.isExpired()) {
            handleExpiredEmergency(emergency);
        }
        return Optional.empty();
    }

    @Override
    public boolean isInEmergency(NationId nationId) {
        return getEmergencyState(nationId).isPresent();
    }

    @Override
    public Collection<EmergencyState> getAllEmergencies() {
        // 清理过期状态
        emergencies.values().removeIf(this::isExpiredAndShouldRemove);
        return emergencies.values().stream()
            .filter(EmergencyState::isActive)
            .toList();
    }

    @Override
    public boolean extendEmergency(NationId nationId, int additionalMinutes) {
        EmergencyState emergency = emergencies.get(nationId);
        if (emergency == null || !emergency.isActive()) {
            return false;
        }

        emergency.extend(additionalMinutes);
        saveState();
        return true;
    }

    @Override
    public String summary() {
        long activeCount = emergencies.values().stream().filter(EmergencyState::isActive).count();
        return "Emergency module: " + activeCount + " active emergency state(s)";
    }

    /**
     * 加载状态
     */
    public void loadState() {
        Properties properties = stateStorage.load();
        emergencies.clear();
        for (String key : properties.stringPropertyNames()) {
            try {
                EmergencyState emergency = parseFromProperties(key, properties.getProperty(key));
                if (emergency != null && emergency.isActive()) {
                    emergencies.put(emergency.nationId(), emergency);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load emergency state: " + key);
            }
        }
        plugin.getLogger().info("Loaded " + emergencies.size() + " emergency states");
    }

    /**
     * 保存状态
     */
    public void saveState() {
        Properties properties = new Properties();
        for (Map.Entry<NationId, EmergencyState> entry : emergencies.entrySet()) {
            String key = entry.getKey().toString();
            String value = serializeToString(entry.getValue());
            properties.setProperty(key, value);
        }
        stateStorage.save(properties);
    }

    /**
     * 启动定时检查任务
     */
    public void startCleanupTask() {
        cleanupTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            checkAndCleanupExpired();
        }, 600L, 600L).getTaskId(); // 每30秒检查一次
    }

    /**
     * 停止定时检查任务
     */
    public void stopCleanupTask() {
        if (cleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cleanupTaskId);
            cleanupTaskId = -1;
        }
    }

    /**
     * 检查并清理过期紧急状态
     */
    private void checkAndCleanupExpired() {
        int count = checkCounter.incrementAndGet();
        if (count % 2 != 0) {
            return; // 每分钟执行一次完整检查
        }

        for (EmergencyState emergency : emergencies.values().toArray(new EmergencyState[0])) {
            if (emergency.isExpired() && !emergency.isCancelled()) {
                handleExpiredEmergency(emergency);
            }
        }
    }

    private boolean isExpiredAndShouldRemove(EmergencyState emergency) {
        if (emergency.isExpired() && !emergency.isCancelled()) {
            handleExpiredEmergency(emergency);
            return true;
        }
        return false;
    }

    private void handleExpiredEmergency(EmergencyState emergency) {
        // 发布过期事件
        EmergencyExpiredEvent event = new EmergencyExpiredEvent(emergency);
        Bukkit.getPluginManager().callEvent(event);
        if (eventBus != null) {
            eventBus.publish(event);
        }
        emergencies.remove(emergency.nationId());
        saveState();
    }

    /**
     * 序列化紧急状态为字符串
     */
    private String serializeToString(EmergencyState emergency) {
        return String.join("|",
            emergency.id().toString(),
            emergency.nationId().toString(),
            emergency.type().name(),
            emergency.reason() != null ? emergency.reason() : "",
            emergency.declaredAt().toString(),
            emergency.expiresAt().toString(),
            emergency.declaredBy() != null ? emergency.declaredBy() : "",
            emergency.cancelledAt() != null ? emergency.cancelledAt().toString() : "",
            emergency.cancelledBy() != null ? emergency.cancelledBy() : ""
        );
    }

    /**
     * 从字符串解析紧急状态
     */
    private EmergencyState parseFromProperties(String nationIdStr, String data) {
        try {
            String[] parts = data.split("\\|", -1);
            if (parts.length < 6) {
                return null;
            }
            return new EmergencyState(
                UUID.fromString(parts[0]),
                NationId.fromString(nationIdStr),
                EmergencyState.EmergencyType.valueOf(parts[2]),
                parts[3].isEmpty() ? null : parts[3],
                Instant.parse(parts[4]),
                Instant.parse(parts[5]),
                parts[6].isEmpty() ? null : parts[6],
                parts.length > 7 && !parts[7].isEmpty() ? Instant.parse(parts[7]) : null,
                parts.length > 8 && !parts[8].isEmpty() ? parts[8] : null
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse emergency state: " + data);
            return null;
        }
    }

    /**
     * 内部方法：直接设置紧急状态（用于测试或管理员操作）
     */
    public void setEmergencyState(NationId nationId, EmergencyState state) {
        emergencies.put(nationId, state);
        saveState();
    }

    /**
     * 内部方法：清除所有紧急状态
     */
    public void clearAllEmergencies() {
        emergencies.clear();
        saveState();
    }
}