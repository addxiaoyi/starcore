package dev.starcore.starcore.war;
import java.util.Optional;

import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.event.WarDeclaredEvent;
import dev.starcore.starcore.module.war.event.WarEndedEvent;
import dev.starcore.starcore.module.war.event.WarStartedEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 战争服务实现
 */
public final class WarServiceImpl implements dev.starcore.starcore.module.war.WarService {
    private final Plugin plugin;
    private final DiplomacyService diplomacyService;
    private final WarStateStorage storage;
    private final Logger logger;
    private final WarConfig config;
    private final StarCoreEventBus eventBus;

    // 内存中的战争数据
    private final ConcurrentHashMap<UUID, War> wars = new ConcurrentHashMap<>();
    // 国家参与的战争索引
    private final ConcurrentHashMap<NationId, Set<UUID>> nationWars = new ConcurrentHashMap<>();

    public WarServiceImpl(
        Plugin plugin,
        DiplomacyService diplomacyService,
        WarStateStorage storage,
        WarConfig config
    ) {
        this(plugin, diplomacyService, storage, config, null);
    }

    public WarServiceImpl(
        Plugin plugin,
        DiplomacyService diplomacyService,
        WarStateStorage storage,
        WarConfig config,
        StarCoreEventBus eventBus
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.diplomacyService = Objects.requireNonNull(diplomacyService, "diplomacyService");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.config = Objects.requireNonNull(config, "config");
        this.eventBus = eventBus;
        this.logger = plugin.getLogger();

        loadWars();
        startPeriodicTasks();
    }

    @Override
    public boolean declareWar(NationId aggressor, NationId defender) {
        War war = declareWar(aggressor, defender, WarGoal.TERRITORIAL);
        return war != null;
    }

    /**
     * 宣战（带战争目标）
     */
    public War declareWar(NationId aggressor, NationId defender, WarGoal goal) {
        Objects.requireNonNull(aggressor, "aggressor");
        Objects.requireNonNull(defender, "defender");
        Objects.requireNonNull(goal, "goal");

        if (aggressor.equals(defender)) {
            throw new IllegalArgumentException("Nation cannot declare war on itself");
        }

        // 检查是否已经在战争中
        if (atWar(aggressor, defender)) {
            throw new IllegalStateException("Nations are already at war");
        }

        // 检查冷却时间
        if (!canDeclareWar(aggressor, defender)) {
            throw new IllegalStateException("War cooldown not expired");
        }

        // 创建战争
        String warName = generateWarName(aggressor, defender);
        War war = War.declare(warName, aggressor, defender, goal);

        // 保存
        wars.put(war.id(), war);
        indexWar(war);
        storage.saveWar(war);

        logger.info(String.format("War declared: %s vs %s (Goal: %s)",
            aggressor, defender, goal.displayName()));

        // 发布战争宣战事件
        publishEvent(new WarDeclaredEvent(war, War.Declarer.NATION));

        return war;
    }

    @Override
    public boolean endWar(NationId nation1, NationId nation2) {
        Optional<War> warOpt = findActiveWarInternal(nation1, nation2);
        if (warOpt.isEmpty()) {
            return false;
        }

        War war = warOpt.get();
        war.end();
        storage.saveWar(war);

        // 发布战争结束事件
        publishEvent(new WarEndedEvent(war, WarEndedEvent.WarEndReason.UNKNOWN));

        logger.info(String.format("War ended: %s", war.name()));
        return true;
    }

    @Override
    public boolean atWar(NationId nation1, NationId nation2) {
        return findActiveWarInternal(nation1, nation2).isPresent();
    }

    @Override
    public Collection<dev.starcore.starcore.module.war.WarSnapshot> activeWars() {
        return wars.values().stream()
            .filter(War::isActive)
            .map(this::toSnapshot)
            .collect(Collectors.toList());
    }

    @Override
    public Collection<dev.starcore.starcore.module.war.WarSnapshot> activeWarsOf(NationId nationId) {
        Set<UUID> warIds = nationWars.getOrDefault(nationId, Collections.emptySet());
        return warIds.stream()
            .map(wars::get)
            .filter(Objects::nonNull)
            .filter(War::isActive)
            .map(this::toSnapshot)
            .collect(Collectors.toList());
    }

    @Override
    public Collection<dev.starcore.starcore.module.war.WarSnapshot> warHistory(NationId nationId) {
        Set<UUID> warIds = nationWars.getOrDefault(nationId, Collections.emptySet());
        return warIds.stream()
            .map(wars::get)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing((War w) -> w.declaredAt()).reversed())
            .limit(20)
            .map(this::toSnapshot)
            .collect(Collectors.toList());
    }

    @Override
    public String summary() {
        long activeCount = wars.values().stream().filter(War::isActive).count();
        return String.format("Wars: %d active, %d total", activeCount, wars.size());
    }

    @Override
    public Optional<dev.starcore.starcore.module.war.WarSnapshot> findActiveWar(NationId nation1, NationId nation2) {
        return findActiveWarInternal(nation1, nation2).map(this::toSnapshot);
    }

    // ==================== 扩展方法 ====================

    /**
     * 获取战争
     */
    public Optional<War> getWar(UUID warId) {
        return Optional.ofNullable(wars.get(warId));
    }

    /**
     * 查找活跃战争
     */
    public Optional<War> findActiveWarInternal(NationId nation1, NationId nation2) {
        Set<UUID> wars1 = nationWars.getOrDefault(nation1, Collections.emptySet());
        Set<UUID> wars2 = nationWars.getOrDefault(nation2, Collections.emptySet());

        return wars1.stream()
            .filter(wars2::contains)
            .map(wars::get)
            .filter(Objects::nonNull)
            .filter(War::isActive)
            .findFirst();
    }

    /**
     * 添加战争盟友
     */
    public void addAlly(UUID warId, NationId allyNationId, boolean joinAggressorSide) {
        War war = wars.get(warId);
        if (war == null) {
            throw new IllegalArgumentException("War not found");
        }

        if (!war.isActive()) {
            throw new IllegalStateException("War is not active");
        }

        war.addAlly(allyNationId, joinAggressorSide);
        indexNationWar(allyNationId, warId);
        storage.saveWar(war);

        logger.info(String.format("Nation %s joined war %s on %s side",
            allyNationId, war.name(), joinAggressorSide ? "aggressor" : "defender"));
    }

    /**
     * 增加战争积分
     */
    public void addWarScore(UUID warId, NationId nationId, int points, String reason) {
        War war = wars.get(warId);
        if (war == null) {
            return;
        }

        war.addWarScore(nationId, points);
        storage.saveWar(war);

        logger.fine(String.format("War score added: %s gained %d points in %s (%s)",
            nationId, points, war.name(), reason));
    }

    /**
     * 开始战争（准备期结束）
     */
    public void startWar(UUID warId) {
        War war = wars.get(warId);
        if (war == null) {
            throw new IllegalArgumentException("War not found");
        }

        war.start();
        storage.saveWar(war);

        // 发布战争开始事件
        publishEvent(new WarStartedEvent(war));

        logger.info(String.format("War started: %s", war.name()));
    }

    /**
     * 停火
     */
    public void ceasefire(UUID warId) {
        War war = wars.get(warId);
        if (war == null) {
            throw new IllegalArgumentException("War not found");
        }

        war.ceasefire();
        storage.saveWar(war);

        logger.info(String.format("Ceasefire: %s", war.name()));
    }

    /**
     * 检查是否可以宣战
     */
    public boolean canDeclareWar(NationId aggressor, NationId defender) {
        // 检查冷却时间
        Optional<War> lastWar = findLastWar(aggressor, defender);
        if (lastWar.isPresent()) {
            War war = lastWar.get();
            if (war.endedAt() != null) {
                Duration cooldown = Duration.between(war.endedAt(), Instant.now());
                if (cooldown.compareTo(config.warCooldown()) < 0) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 查找最近的战争
     */
    public Optional<War> findLastWar(NationId nation1, NationId nation2) {
        Set<UUID> wars1 = nationWars.getOrDefault(nation1, Collections.emptySet());
        Set<UUID> wars2 = nationWars.getOrDefault(nation2, Collections.emptySet());

        return wars1.stream()
            .filter(wars2::contains)
            .map(wars::get)
            .filter(Objects::nonNull)
            .filter(War::isEnded)
            .max(Comparator.comparing(War::endedAt));
    }

    // ==================== 内部方法 ====================

    private void loadWars() {
        Collection<War> loaded = storage.loadAllWars();
        for (War war : loaded) {
            wars.put(war.id(), war);
            indexWar(war);
        }
        logger.info(String.format("Loaded %d wars from storage", loaded.size()));
    }

    private void indexWar(War war) {
        for (NationId nationId : war.allParticipants()) {
            indexNationWar(nationId, war.id());
        }
    }

    private void indexNationWar(NationId nationId, UUID warId) {
        nationWars.computeIfAbsent(nationId, k -> ConcurrentHashMap.newKeySet()).add(warId);
    }

    private String generateWarName(NationId aggressor, NationId defender) {
        return String.format("War_%s_%s_%d", aggressor.value(), defender.value(), System.currentTimeMillis());
    }

    private dev.starcore.starcore.module.war.WarSnapshot toSnapshot(War war) {
        return new dev.starcore.starcore.module.war.WarSnapshot(
            war.aggressor(),
            war.defender(),
            war.declaredAt(),
            war.endedAt()
        );
    }

    private void startPeriodicTasks() {
        // 每5分钟检查一次战争状态
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            updateWars();
        }, 20L * 60 * 5, 20L * 60 * 5);
    }

    private void updateWars() {
        Instant now = Instant.now();

        for (War war : wars.values()) {
            if (war.status() == WarStatus.PREPARATION) {
                // 检查是否准备期结束
                Duration preparationTime = Duration.between(war.declaredAt(), now);
                if (preparationTime.compareTo(config.preparationDuration()) >= 0) {
                    war.start();
                    storage.saveWar(war);
                    publishEvent(new WarStartedEvent(war));
                    logger.info(String.format("War preparation ended, war started: %s", war.name()));
                }
            }

            // 自动结束长期战争
            if (war.isActive() && war.startedAt() != null) {
                Duration warDuration = Duration.between(war.startedAt(), now);
                if (warDuration.compareTo(config.maxWarDuration()) >= 0) {
                    war.end();
                    storage.saveWar(war);
                    publishEvent(new WarEndedEvent(war, WarEndedEvent.WarEndReason.MAX_DURATION));
                    logger.info(String.format("War auto-ended due to max duration: %s", war.name()));
                }
            }
        }
    }

    /**
     * 发布事件到事件总线
     */
    private void publishEvent(Object event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    /**
     * 战争配置
     */
    public record WarConfig(
        Duration preparationDuration,    // 准备期时长
        Duration warCooldown,            // 战争冷却时间
        Duration maxWarDuration,         // 最大战争时长
        int minWarScore                  // 最小战争积分
    ) {
        public static WarConfig defaults() {
            return new WarConfig(
                Duration.ofHours(24),    // 24小时准备期
                Duration.ofDays(7),      // 7天冷却期
                Duration.ofDays(30),     // 最长30天
                100                       // 最少100积分
            );
        }
    }
}
