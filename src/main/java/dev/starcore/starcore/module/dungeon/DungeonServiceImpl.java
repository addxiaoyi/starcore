package dev.starcore.starcore.module.dungeon;
import java.util.Optional;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 副本服务实现
 */
public class DungeonServiceImpl implements DungeonService {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final PersistenceService persistenceService;
    private final StarCoreScheduler scheduler;
    private final EconomyService economyService;
    private final MessageService messages;
    private final StarCoreEventBus eventBus;

    private DungeonConfig config;
    private DungeonRepository repository;
    private DungeonWorldManager worldManager;
    private DungeonEventListener eventListener;
    private DungeonRewardService rewardService;

    // 活跃副本实例
    private final Map<UUID, DungeonInstance> activeInstances = new ConcurrentHashMap<>();
    private final Map<UUID, DungeonInstance> playerToInstance = new ConcurrentHashMap<>();
    private final Map<UUID, DungeonParty> activeParties = new ConcurrentHashMap<>();

    // 玩家冷却时间
    private final Map<UUID, Map<String, Long>> playerCooldowns = new ConcurrentHashMap<>();

    // 统计数据
    private final DungeonStatistics statistics = new DungeonStatistics();

    // 自动保存任务
    private BukkitTask autoSaveTask;
    private BukkitTask cleanupTask;

    public DungeonServiceImpl(
        JavaPlugin plugin,
        StarCoreContext context
    ) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.persistenceService = context.persistenceService();
        this.scheduler = context.scheduler();
        this.economyService = context.economyService();
        this.messages = context.serviceRegistry().require(MessageService.class);
        this.eventBus = context.eventBus();
    }

    /**
     * 初始化服务
     */
    public void initialize() {
        // 确保命名空间存在
        persistenceService.ensureNamespace("dungeon").join();

        // 加载配置
        this.config = new DungeonConfig(plugin);
        config.load();

        // 初始化仓库
        this.repository = new DungeonRepository(plugin, persistenceService);
        repository.load();

        // 初始化世界管理器
        this.worldManager = new DungeonWorldManager(plugin, config);

        // 初始化奖励服务
        this.rewardService = new DungeonRewardService(plugin, economyService);

        // 注册事件监听器
        this.eventListener = new DungeonEventListener(this, config);
        plugin.getServer().getPluginManager().registerEvents(eventListener, plugin);

        // 启动自动保存
        scheduleAutoSave();

        // 启动清理任务
        scheduleCleanup();

        logger.info("副本服务初始化完成!");
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        // 取消定时任务
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }

        // 保存所有数据
        save();

        // 关闭所有副本实例
        closeAllInstances();

        // 清理世界
        worldManager.cleanup();

        logger.info("副本服务已关闭");
    }

    // ==================== 副本定义管理 ====================

    @Override
    public Collection<DungeonDefinition> getAllDungeons() {
        return config.getAllDungeons();
    }

    @Override
    public Optional<DungeonDefinition> getDungeonById(String dungeonId) {
        return config.getDungeon(dungeonId);
    }

    @Override
    public List<DungeonDefinition> getDungeonsByDifficulty(DungeonDifficulty difficulty) {
        return config.getAllDungeons().stream()
            .filter(d -> d.difficulty() == difficulty)
            .toList();
    }

    // ==================== 副本实例管理 ====================

    @Override
    public Optional<DungeonInstance> createInstance(String dungeonId, List<Player> partyMembers) {
        Optional<DungeonDefinition> defOpt = getDungeonById(dungeonId);
        if (defOpt.isEmpty()) {
            logger.warning("副本不存在: " + dungeonId);
            return Optional.empty();
        }

        DungeonDefinition definition = defOpt.get();

        // 检查并发实例数
        if (activeInstances.size() >= config.getMaxConcurrentInstances()) {
            logger.warning("已达到最大并发副本数!");
            return Optional.empty();
        }

        // 创建副本世界
        String worldName = config.getWorldPrefix() + dungeonId + "_" + UUID.randomUUID().toString().substring(0, 8);
        var worldOpt = worldManager.createDungeonWorld(worldName, definition.templateWorld());
        if (worldOpt.isEmpty()) {
            logger.warning("无法创建副本世界: " + worldName);
            return Optional.empty();
        }
        World world = worldOpt.get();

        // 创建副本实例
        UUID instanceId = UUID.randomUUID();
        Set<UUID> playerIds = new HashSet<>();
        partyMembers.forEach(p -> playerIds.add(p.getUniqueId()));

        DungeonInstance instance = new DungeonInstance(
            instanceId,
            dungeonId,
            definition,
            worldName,
            playerIds,
            null
        );

        // 初始化房间进度
        for (DungeonRoom room : definition.rooms()) {
            DungeonRoomProgress progress;
            if (room.type() == DungeonRoomType.BOSS) {
                progress = DungeonRoomProgress.createBoss(room.id(), room.boss() != null ? room.boss().baseHealth() : 500);
            } else if (room.type() == DungeonRoomType.PUZZLE) {
                progress = DungeonRoomProgress.createPuzzle(room.id());
            } else if (room.type() == DungeonRoomType.SURVIVAL) {
                progress = DungeonRoomProgress.createSurvival(room.id(), room.waveCount());
            } else {
                progress = DungeonRoomProgress.create(room.id(), room.mobCount());
            }
            instance.updateRoomProgress(room.id(), progress);
        }

        // 保存实例
        activeInstances.put(instanceId, instance);
        playerIds.forEach(id -> playerToInstance.put(id, instance));

        // 记录审计日志
        playerIds.forEach(id -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                repository.logAction(instanceId, DungeonAuditEntry.createWithLocation(
                    instanceId,
                    id,
                    p.getName(),
                    DungeonAuditAction.INSTANCE_CREATED,
                    "创建副本: " + definition.name(),
                    worldName + ",0,64,0"
                ));
            }
        });

        logger.info("已创建副本实例: " + instanceId + " (" + definition.name() + ")");
        return Optional.of(instance);
    }

    @Override
    public Optional<DungeonInstance> getInstanceByPlayer(UUID playerId) {
        return Optional.ofNullable(playerToInstance.get(playerId));
    }

    @Override
    public Optional<DungeonInstance> getInstance(UUID instanceId) {
        return Optional.ofNullable(activeInstances.get(instanceId));
    }

    @Override
    public Collection<DungeonInstance> getActiveInstances() {
        return List.copyOf(activeInstances.values());
    }

    @Override
    public void closeInstance(UUID instanceId) {
        DungeonInstance instance = activeInstances.remove(instanceId);
        if (instance == null) return;

        // 保存玩家状态
        for (UUID playerId : instance.getPlayers()) {
            playerToInstance.remove(playerId);

            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                // 恢复玩家状态
                DungeonParty party = instance.getParty();
                if (party != null) {
                    PlayerDungeonState state = party.getPlayerState(playerId);
                    if (state != null) {
                        state.restoreToPlayer(player);
                    }
                }

                // 传送回入口点
                teleportToEntrance(player, instance.getDungeonId());

                player.sendMessage(messages.format("dungeon.exit.success"));
            }
        }

        // 卸载世界
        worldManager.unloadDungeonWorld(instance.getWorldName());

        // 清理队伍
        if (instance.getParty() != null) {
            activeParties.remove(instance.getParty().getPartyId());
        }

        logger.info("已关闭副本实例: " + instanceId);
    }

    @Override
    public void closeAllInstances() {
        new HashSet<>(activeInstances.keySet()).forEach(this::closeInstance);
    }

    // ==================== 玩家状态 ====================

    @Override
    public Optional<DungeonProgress> getPlayerProgress(UUID playerId, UUID instanceId) {
        // 从仓库获取进度
        return repository.getProgress(playerId, instanceId);
    }

    @Override
    public List<DungeonCompletionRecord> getPlayerHistory(UUID playerId) {
        return repository.getPlayerHistory(playerId);
    }

    @Override
    public long getPlayerCooldown(UUID playerId, String dungeonId) {
        Map<String, Long> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns == null) return 0;

        Long lastEntry = cooldowns.get(dungeonId);
        if (lastEntry == null) return 0;

        Optional<DungeonDefinition> defOpt = getDungeonById(dungeonId);
        if (defOpt.isEmpty()) return 0;

        DungeonConfig.DifficultySettings settings = config.getDifficultySettings(defOpt.get().difficulty());
        long cooldownMillis = settings.cooldownMinutes() * 60 * 1000L;
        long elapsed = System.currentTimeMillis() - lastEntry;

        return Math.max(0, cooldownMillis - elapsed);
    }

    // ==================== 队伍系统 ====================

    @Override
    public DungeonParty createParty(Player leader, DungeonDefinition dungeon) {
        UUID partyId = UUID.randomUUID();
        DungeonParty party = new DungeonParty(partyId, leader.getUniqueId(), List.of(leader), dungeon.id(), dungeon);
        activeParties.put(partyId, party);
        return party;
    }

    @Override
    public boolean joinParty(UUID partyId, Player player) {
        DungeonParty party = activeParties.get(partyId);
        if (party == null) return false;

        return party.addMember(player.getUniqueId());
    }

    @Override
    public void leaveParty(UUID partyId, Player player) {
        DungeonParty party = activeParties.get(partyId);
        if (party == null) return;

        if (party.isLeader(player.getUniqueId())) {
            // 队长离开，解散队伍
            disbandParty(partyId);
        } else {
            party.removeMember(player.getUniqueId());
        }
    }

    @Override
    public void disbandParty(UUID partyId) {
        DungeonParty party = activeParties.remove(partyId);
        if (party != null) {
            party.setState(DungeonPartyState.DISBANDED);
        }
    }

    @Override
    public Optional<DungeonParty> getPlayerParty(UUID playerId) {
        return activeParties.values().stream()
            .filter(p -> p.hasMember(playerId))
            .findFirst();
    }

    @Override
    public Optional<DungeonParty> getParty(UUID partyId) {
        return Optional.ofNullable(activeParties.get(partyId));
    }

    // ==================== 进入/退出 ====================

    @Override
    public boolean tryEnterDungeon(Player player, String dungeonId) {
        // 检查是否已经在副本中
        if (getInstanceByPlayer(player.getUniqueId()).isPresent()) {
            player.sendMessage(messages.format("dungeon.command.already_in_dungeon"));
            return false;
        }

        Optional<DungeonDefinition> defOpt = getDungeonById(dungeonId);
        if (defOpt.isEmpty()) {
            player.sendMessage(messages.format("dungeon.command.dungeon_not_found")
                .replace("{dungeon}", dungeonId));
            return false;
        }

        DungeonDefinition definition = defOpt.get();

        // 检查入场费
        if (definition.entryFee() > 0) {
            BigDecimal fee = BigDecimal.valueOf(definition.entryFee());
            if (!economyService.has(player.getUniqueId(), fee)) {
                player.sendMessage(messages.format("dungeon.enter.failed_fee")
                    .replace("{cost}", String.valueOf(definition.entryFee())));
                return false;
            }
            economyService.withdraw(player.getUniqueId(), fee);
        }

        // 检查冷却
        long cooldown = getPlayerCooldown(player.getUniqueId(), dungeonId);
        if (cooldown > 0) {
            player.sendMessage(messages.format("dungeon.enter.failed_cooldown")
                .replace("{time}", String.valueOf(cooldown / 60000)));
            return false;
        }

        // 创建副本实例
        Optional<DungeonInstance> instanceOpt = createInstance(dungeonId, List.of(player));
        if (instanceOpt.isEmpty()) {
            // 退还入场费
            if (definition.entryFee() > 0) {
                economyService.deposit(player.getUniqueId(), BigDecimal.valueOf(definition.entryFee()));
            }
            player.sendMessage(messages.format("dungeon.error.instance_create"));
            return false;
        }

        DungeonInstance instance = instanceOpt.get();

        // 保存玩家状态
        PlayerDungeonState state = PlayerDungeonState.fromPlayer(player, instance.getInstanceId());

        // 创建队伍
        DungeonParty party = createParty(player, definition);
        party.savePlayerState(player.getUniqueId(), state);
        instance.updateRoomProgress(definition.rooms().get(0).id(),
            new DungeonRoomProgress(definition.rooms().get(0).id(), RoomStatus.AVAILABLE, 0, 0, 0, 1, System.currentTimeMillis(), 0, Instant.now(), null, false));

        // 设置玩家状态
        player.sendMessage(messages.format("dungeon.enter.success")
            .replace("{dungeon}", definition.name()));

        // 记录冷却
        playerCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
            .put(dungeonId, System.currentTimeMillis());

        // 记录审计日志
        repository.logAction(instance.getInstanceId(), DungeonAuditEntry.create(
            instance.getInstanceId(),
            player.getUniqueId(),
            player.getName(),
            DungeonAuditAction.PLAYER_ENTER,
            "进入副本: " + definition.name()
        ));

        statistics.recordCompletion();

        return true;
    }

    @Override
    public void leaveDungeon(Player player) {
        Optional<DungeonInstance> instanceOpt = getInstanceByPlayer(player.getUniqueId());
        if (instanceOpt.isEmpty()) {
            player.sendMessage(messages.format("dungeon.command.not_in_dungeon"));
            return;
        }

        DungeonInstance instance = instanceOpt.get();

        // 记录审计日志
        repository.logAction(instance.getInstanceId(), DungeonAuditEntry.create(
            instance.getInstanceId(),
            player.getUniqueId(),
            player.getName(),
            DungeonAuditAction.PLAYER_LEAVE,
            "离开副本"
        ));

        // 恢复玩家状态
        DungeonParty party = instance.getParty();
        if (party != null) {
            PlayerDungeonState state = party.getPlayerState(player.getUniqueId());
            if (state != null) {
                state.restoreToPlayer(player);
            }
        }

        // 传送回入口
        teleportToEntrance(player, instance.getDungeonId());

        // 从副本移除
        instance.removePlayer(player.getUniqueId());
        playerToInstance.remove(player.getUniqueId());

        player.sendMessage(messages.format("dungeon.exit.exit"));

        // 如果没有玩家了，关闭副本
        if (instance.getPlayerCount() == 0) {
            closeInstance(instance.getInstanceId());
        }
    }

    @Override
    public void teleportToEntrance(Player player, String dungeonId) {
        Optional<DungeonDefinition> defOpt = getDungeonById(dungeonId);
        if (defOpt.isEmpty()) {
            // 默认传送到主世界出生点
            World world = Bukkit.getWorlds().get(0);
            player.teleport(world.getSpawnLocation());
            return;
        }

        DungeonEntrance entrance = defOpt.get().entrance();
        World world = Bukkit.getWorld(entrance.world());
        if (world == null) {
            world = Bukkit.getWorlds().get(0);
        }

        player.teleport(new Location(world, entrance.x(), entrance.y(), entrance.z(), entrance.yaw(), entrance.pitch()));
    }

    // ==================== 副本进度 ====================

    @Override
    public void handlePlayerDeath(Player player) {
        Optional<DungeonInstance> instanceOpt = getInstanceByPlayer(player.getUniqueId());
        if (instanceOpt.isEmpty()) return;

        DungeonInstance instance = instanceOpt.get();
        DungeonParty party = instance.getParty();
        if (party == null) return;

        PlayerDungeonState state = party.getPlayerState(player.getUniqueId());
        if (state == null) return;

        // 记录死亡
        state.incrementRoomDeaths();
        state.handleDeath();

        // 记录审计日志
        repository.logAction(instance.getInstanceId(), DungeonAuditEntry.createWithLocation(
            instance.getInstanceId(),
            player.getUniqueId(),
            player.getName(),
            DungeonAuditAction.PLAYER_DEATH,
            "在副本中死亡",
            formatLocation(player.getLocation())
        ));

        statistics.recordDeath();

        // 检查是否所有玩家都死亡
        if (!party.allMembersAlive()) {
            if (state.getRespawnsRemaining() > 0) {
                state.decrementRespawns();
                player.sendMessage(messages.format("dungeon.status.respawn_available")
                    .replace("{count}", String.valueOf(state.getRespawnsRemaining())));
            } else {
                // 副本失败
                failDungeon(instance, DungeonCompletionResult.FAILED);
            }
        }
    }

    @Override
    public void respawnPlayer(Player player) {
        Optional<DungeonInstance> instanceOpt = getInstanceByPlayer(player.getUniqueId());
        if (instanceOpt.isEmpty()) return;

        DungeonInstance instance = instanceOpt.get();
        DungeonParty party = instance.getParty();
        if (party == null) return;

        PlayerDungeonState state = party.getPlayerState(player.getUniqueId());
        if (state == null) return;

        // 复活玩家
        state.handleRespawn();

        // 设置玩家状态
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);

        // 传送到当前房间起点
        instance.getCurrentRoom().ifPresent(currentRoom -> {
            player.teleport(getRoomSpawnLocation(instance, currentRoom));
        });

        // 记录审计日志
        repository.logAction(instance.getInstanceId(), DungeonAuditEntry.create(
            instance.getInstanceId(),
            player.getUniqueId(),
            player.getName(),
            DungeonAuditAction.PLAYER_RESPAWN,
            "复活"
        ));
    }

    @Override
    public void checkDungeonCompletion(UUID instanceId) {
        Optional<DungeonInstance> instanceOpt = getInstance(instanceId);
        if (instanceOpt.isEmpty()) return;

        DungeonInstance instance = instanceOpt.get();
        DungeonDefinition definition = instance.getDefinition();

        // 检查所有房间是否完成
        boolean allRoomsCleared = definition.rooms().stream()
            .allMatch(room -> {
                DungeonRoomProgress progress = instance.getRoomProgress(room.id());
                return progress != null && progress.status() == RoomStatus.CLEARED;
            });

        if (allRoomsCleared) {
            completeDungeon(instance);
        }
    }

    @Override
    public void onRoomCleared(UUID instanceId, String roomId) {
        Optional<DungeonInstance> instanceOpt = getInstance(instanceId);
        if (instanceOpt.isEmpty()) return;

        DungeonInstance instance = instanceOpt.get();
        DungeonRoom room = instance.getDefinition().rooms().stream()
            .filter(r -> r.id().equals(roomId))
            .findFirst()
            .orElse(null);

        if (room == null) return;

        // 更新房间进度
        DungeonRoomProgress currentProgress = instance.getRoomProgress(roomId);
        if (currentProgress != null) {
            instance.updateRoomProgress(roomId, currentProgress.cleared());
        }

        // 记录审计日志
        for (UUID playerId : instance.getPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                repository.logAction(instanceId, DungeonAuditEntry.create(
                    instanceId,
                    playerId,
                    player.getName(),
                    DungeonAuditAction.ROOM_CLEARED,
                    "清除房间: " + room.displayName()
                ));
            }
        }

        statistics.recordRoomCleared();

        // 打开下一扇门
        instance.getNextRoom().ifPresentOrElse(
            nextRoom -> {
                instance.setCurrentRoomId(nextRoom.id());
                broadcastToInstance(instance, messages.format("dungeon.room.unlock")
                    .replace("{room}", nextRoom.displayName()));
            },
            () -> completeDungeon(instance)
        );
    }

    @Override
    public void onBossDefeated(UUID instanceId, String bossId) {
        Optional<DungeonInstance> instanceOpt = getInstance(instanceId);
        if (instanceOpt.isEmpty()) return;

        DungeonInstance instance = instanceOpt.get();

        // 记录审计日志
        for (UUID playerId : instance.getPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                repository.logAction(instanceId, DungeonAuditEntry.create(
                    instanceId,
                    playerId,
                    player.getName(),
                    DungeonAuditAction.BOSS_DEFEATED,
                    "击败BOSS: " + bossId
                ));
            }
        }

        statistics.recordBossDefeated();

        // 完成房间
        onRoomCleared(instanceId, bossId);
    }

    // ==================== 管理功能 ====================

    @Override
    public void reload() {
        config.reload();
        repository.load();
    }

    @Override
    public void save() {
        repository.save();
    }

    @Override
    public String getSummary() {
        return String.format("副本系统: %d 活跃实例, %d 队伍",
            activeInstances.size(), activeParties.size());
    }

    @Override
    public DungeonStatistics getStatistics() {
        return statistics;
    }

    // ==================== 国家相关 ====================

    @Override
    public boolean canNationEnterDungeon(NationId nationId, String dungeonId) {
        // 可以扩展为检查国家科技、等级等
        return true;
    }

    @Override
    public boolean nationEnterDungeon(NationId nationId, String dungeonId, List<Player> members) {
        Optional<DungeonDefinition> defOpt = getDungeonById(dungeonId);
        if (defOpt.isEmpty()) return false;

        DungeonDefinition definition = defOpt.get();

        // 检查人数
        if (members.size() < definition.minPlayers() || members.size() > definition.maxPlayers()) {
            return false;
        }

        // 创建副本实例
        Optional<DungeonInstance> instanceOpt = createInstance(dungeonId, members);
        if (instanceOpt.isEmpty()) return false;

        DungeonInstance instance = instanceOpt.get();
        instance.setNationId(UUID.fromString(nationId.value().toString()));

        return true;
    }

    @Override
    public List<DungeonCompletionRecord> getNationRecords(NationId nationId) {
        return repository.getNationRecords(UUID.fromString(nationId.value().toString()));
    }

    // ==================== 私有方法 ====================

    private void completeDungeon(DungeonInstance instance) {
        // 计算奖励
        for (UUID playerId : instance.getPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;

            DungeonParty party = instance.getParty();
            PlayerDungeonState state = party != null ? party.getPlayerState(playerId) : null;
            int deaths = state != null ? state.getRoomDeaths() : 0;

            // 分发奖励
            rewardService.distributeRewards(player, instance.getDefinition(), deaths);

            // 记录完成
            DungeonCompletionRecord record = DungeonCompletionRecord.success(
                instance.getDungeonId(),
                instance.getDefinition().difficulty(),
                playerId,
                instance.getNationId(),
                instance.getElapsedSeconds(),
                deaths,
                instance.getDefinition().rewards()
            );
            repository.addCompletionRecord(record);

            // 记录审计日志
            repository.logAction(instance.getInstanceId(), DungeonAuditEntry.create(
                instance.getInstanceId(),
                playerId,
                player.getName(),
                DungeonAuditAction.INSTANCE_COMPLETED,
                "完成副本"
            ));
        }

        // 广播完成消息
        broadcastToInstance(instance, messages.format("dungeon.status.completed")
            .replace("{dungeon}", instance.getDefinition().name()));

        // 延迟关闭副本
        scheduler.runSyncLater(() -> {
            closeInstance(instance.getInstanceId());
        }, 60 * 20L); // 60秒后关闭

        statistics.recordCompletion();
    }

    private void failDungeon(DungeonInstance instance, DungeonCompletionResult result) {
        // 广播失败消息
        broadcastToInstance(instance, messages.format("dungeon.status.failed")
            .replace("{dungeon}", instance.getDefinition().name()));

        // 记录失败
        for (UUID playerId : instance.getPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                DungeonCompletionRecord record = DungeonCompletionRecord.failed(
                    instance.getDungeonId(),
                    instance.getDefinition().difficulty(),
                    playerId,
                    instance.getNationId(),
                    instance.getElapsedSeconds(),
                    result
                );
                repository.addCompletionRecord(record);

                // 记录审计日志
                repository.logAction(instance.getInstanceId(), DungeonAuditEntry.create(
                    instance.getInstanceId(),
                    playerId,
                    player.getName(),
                    DungeonAuditAction.INSTANCE_FAILED,
                    "副本失败: " + result.getDisplayName()
                ));
            }
        }

        // 延迟关闭副本
        scheduler.runSyncLater(() -> {
            closeInstance(instance.getInstanceId());
        }, 20 * 20L); // 20秒后关闭

        statistics.recordFailure();
    }

    private void broadcastToInstance(DungeonInstance instance, String message) {
        for (UUID playerId : instance.getPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private Location getRoomSpawnLocation(DungeonInstance instance, DungeonRoom room) {
        World world = Bukkit.getWorld(instance.getWorldName());
        if (world == null) {
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }

        // 默认位置
        return new Location(world, 0, 64, 0);
    }

    private String formatLocation(Location loc) {
        return String.format("%s,%.1f,%.1f,%.1f",
            loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    private void scheduleAutoSave() {
        long interval = config.getAutoSaveInterval() * 20L; // 转换为tick
        autoSaveTask = scheduler.runSyncTimer(() -> {
            save();
        }, interval, interval);
    }

    private void scheduleCleanup() {
        long checkInterval = 5 * 60 * 20L; // 每5分钟检查一次
        cleanupTask = scheduler.runSyncTimer(() -> {
            // 清理超时副本
            long timeoutMillis = config.getCleanupTimeoutMinutes() * 60 * 1000L;
            long now = System.currentTimeMillis();

            for (DungeonInstance instance : new ArrayList<>(activeInstances.values())) {
                if (now - instance.getStartTime().toEpochMilli() > timeoutMillis) {
                    failDungeon(instance, DungeonCompletionResult.TIMEOUT);
                }
            }

            // 清理过期冷却
            for (Map<String, Long> cooldowns : playerCooldowns.values()) {
                cooldowns.entrySet().removeIf(entry -> {
                    Optional<DungeonDefinition> defOpt = getDungeonById(entry.getKey());
                    if (defOpt.isEmpty()) return true;
                    DungeonConfig.DifficultySettings settings = config.getDifficultySettings(defOpt.get().difficulty());
                    return System.currentTimeMillis() - entry.getValue() > settings.cooldownMinutes() * 60 * 1000L;
                });
            }
        }, 60 * 20L, checkInterval); // 延迟1分钟后开始
    }

    /**
     * 获取配置
     */
    public DungeonConfig getConfig() {
        return config;
    }

    /**
     * 获取世界管理器
     */
    public DungeonWorldManager getWorldManager() {
        return worldManager;
    }

    /**
     * 获取仓库
     */
    public DungeonRepository getRepository() {
        return repository;
    }
}
