package dev.starcore.starcore.module.event;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EventModule implements StarCoreModule, EventService {
    private static final String FILE_NAME = "events.properties";

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "event",
        "事件核心",
        ModuleLayer.MODULE,
        List.of("nation"),
        List.of(EventService.class),
        "Owns national event logs and strategic audit history."
    );

    private final ConcurrentMap<NationId, CopyOnWriteArrayList<NationEventRecord>> events = new ConcurrentHashMap<>();
    private EventStateStorage stateStorage;
    private EventModuleListener eventModuleListener;
    private NationService nationService;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        context.persistenceService().ensureNamespace(metadata().id());
        this.stateStorage = new DatabaseAwareEventStateStorage(
            metadata().id(),
            context.databaseService(),
            context.persistenceService(),
            context.plugin().getLogger()
        );

        // 获取 NationService 用于 NationId 解析
        this.nationService = context.serviceRegistry().find(NationService.class).orElse(null);

        // 创建并注册事件监听器
        registerListeners(context);

        loadState();

        // 注册到服务注册表
        context.serviceRegistry().register(EventService.class, this);
    }

    /**
     * 注册事件监听器
     */
    private void registerListeners(StarCoreContext context) {
        if (!(context.plugin() instanceof JavaPlugin plugin)) {
            context.plugin().getLogger().warning("EventModule: plugin is not a JavaPlugin, cannot register listeners");
            return;
        }

        // 创建 NationId 提供者
        EventModuleListener.NationIdProvider nationIdProvider = null;
        if (nationService != null) {
            nationIdProvider = new EventModuleListener.NationIdProvider() {
                @Override
                public NationId getNationId(Player player) {
                    if (player == null) return null;
                    return nationService.nationOf(player.getUniqueId()).map(n -> n.id()).orElse(null);
                }

                @Override
                public NationId getNationId(String playerName) {
                    if (playerName == null) return null;
                    // 通过在线玩家查找
                    Player player = Bukkit.getPlayer(playerName);
                    if (player != null) {
                        return nationService.nationOf(player.getUniqueId()).map(n -> n.id()).orElse(null);
                    }
                    return null;
                }
            };
        }

        // 创建并注册事件监听器
        this.eventModuleListener = new EventModuleListener(this, nationIdProvider);
        plugin.getServer().getPluginManager().registerEvents(eventModuleListener, plugin);
        plugin.getLogger().info("EventModule listeners registered");
    }

    @Override
    public void disable(StarCoreContext context) {
        flushState();
        this.eventModuleListener = null;
        this.nationService = null;
    }

    @Override
    public NationEventRecord record(NationId nationId, String type, String message) {
        return record(nationId, type, message, "");
    }

    @Override
    public NationEventRecord record(NationId nationId, String type, String message, String context) {
        String normalizedType = normalizeType(type);
        String normalizedMessage = message == null ? "" : message.trim();
        String normalizedContext = context == null ? "" : context.trim();
        if (normalizedType.isBlank() || normalizedMessage.isBlank()) {
            throw new IllegalArgumentException("event type and message are required");
        }
        NationEventRecord record = new NationEventRecord(UUID.randomUUID(), nationId, Instant.now(), normalizedType, normalizedMessage, normalizedContext);
        events.computeIfAbsent(nationId, ignored -> new CopyOnWriteArrayList<>()).add(record);
        saveState();
        return record;
    }

    @Override
    public Collection<NationEventRecord> eventsOf(NationId nationId) {
        return NationEventRecord.newestFirst(events.getOrDefault(nationId, new CopyOnWriteArrayList<>()));
    }

    @Override
    public boolean clear(NationId nationId) {
        boolean removed = events.remove(nationId) != null;
        if (removed) {
            saveState();
        }
        return removed;
    }

    @Override
    public String summary() {
        long total = events.values().stream().mapToLong(List::size).sum();
        return events.size() + " nation event log(s), " + total + " event(s)";
    }

    private void saveState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.saveAsync(EventStateCodec.toProperties(snapshotByNation()));
    }

    private void flushState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.save(EventStateCodec.toProperties(snapshotByNation()));
    }

    private void loadState() {
        events.clear();
        EventStateCodec.fromProperties(stateStorage == null ? new java.util.Properties() : stateStorage.load())
            .forEach((nationId, records) -> events.put(nationId, new CopyOnWriteArrayList<>(records)));
    }

    private Map<NationId, List<NationEventRecord>> snapshotByNation() {
        Map<NationId, List<NationEventRecord>> snapshot = new ConcurrentHashMap<>();
        events.forEach((nationId, records) -> {
            if (!records.isEmpty()) {
                snapshot.put(nationId, List.copyOf(records));
            }
        });
        return snapshot;
    }

    private static String normalizeType(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }

}
