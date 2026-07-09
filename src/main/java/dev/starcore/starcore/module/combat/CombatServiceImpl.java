package dev.starcore.starcore.module.combat;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Optional;

import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.combat.config.CombatConfig;
import dev.starcore.starcore.module.combat.model.*;
import dev.starcore.starcore.module.combat.model.CombatSession.CombatEndReason;
import dev.starcore.starcore.module.combat.model.CombatSession.CombatSessionState;
import dev.starcore.starcore.module.combat.model.CombatSession.CombatSessionType;
import dev.starcore.starcore.module.combat.model.CombatTag.CombatTagType;
import dev.starcore.starcore.module.combat.storage.CombatStorage;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.WarService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 战斗服务实现 - 核心战斗系统实现
 *
 * 实现 CombatService 接口，提供完整的战斗功能：
 * - 战斗标记和状态追踪
 * - 战场管理（国家对抗战场）
 * - 伤害计算系统
 * - Buff效果系统
 * - 战斗统计和历史
 */
public final class CombatServiceImpl implements CombatService {
    private final org.bukkit.plugin.Plugin plugin;
    private final CombatConfig config;
    private final CombatStorage storage;
    private final StarCoreScheduler scheduler;
    private final Optional<NationService> nationService;
    private final Optional<DiplomacyService> diplomacyService;
    private final Optional<WarService> warService;
    private final Optional<ArmyService> armyService;

    // 玩家战斗状态映射
    private final ConcurrentMap<UUID, PlayerCombatState> playerStates;
    // 战斗会话映射
    private final ConcurrentMap<UUID, CombatSession> combatSessions;
    // 玩家-会话映射
    private final ConcurrentMap<UUID, UUID> playerSessions;
    // 战场映射
    private final ConcurrentMap<UUID, Battlefield> battlefields;
    // 战场位置索引
    private final ConcurrentMap<String, UUID> battlefieldLocationIndex;
    // 国家-战场映射
    private final ConcurrentMap<NationId, Set<UUID>> nationBattlefields;

    // Buff追踪
    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Buff>> playerBuffs;

    // 玩家PVP状态追踪（每个玩家的个人PVP开关）
    private final ConcurrentMap<UUID, Boolean> playerPvpEnabled;

    // 武器伤害配置
    private final Map<String, Double> weaponDamageBonus;

    // 定时任务
    private volatile BukkitTask cleanupTask;
    private volatile BukkitTask saveTask;
    private volatile BukkitTask scoreboardTask;
    private volatile BukkitTask buffUpdateTask;

    // 事件回调
    private final List<Consumer<CombatTagEvent>> tagEventListeners;
    private final List<Consumer<CombatSession>> sessionEventListeners;

    public CombatServiceImpl(
        org.bukkit.plugin.Plugin plugin,
        CombatConfig config,
        CombatStorage storage,
        StarCoreScheduler scheduler,
        Optional<NationService> nationService,
        Optional<DiplomacyService> diplomacyService,
        Optional<WarService> warService,
        Optional<ArmyService> armyService
    ) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        this.scheduler = scheduler;
        this.nationService = nationService;
        this.diplomacyService = diplomacyService;
        this.warService = warService;
        this.armyService = armyService;

        this.playerStates = new ConcurrentHashMap<>();
        this.combatSessions = new ConcurrentHashMap<>();
        this.playerSessions = new ConcurrentHashMap<>();
        this.battlefields = new ConcurrentHashMap<>();
        this.battlefieldLocationIndex = new ConcurrentHashMap<>();
        this.nationBattlefields = new ConcurrentHashMap<>();
        this.playerBuffs = new ConcurrentHashMap<>();
        this.playerPvpEnabled = new ConcurrentHashMap<>();

        this.weaponDamageBonus = new HashMap<>();
        initializeWeaponBonus();

        this.tagEventListeners = new ArrayList<>();
        this.sessionEventListeners = new ArrayList<>();

        initialize();
    }

    /**
     * 初始化武器伤害加成配置
     */
    private void initializeWeaponBonus() {
        // 近战武器
        weaponDamageBonus.put("WOODEN_SWORD", 1.0);
        weaponDamageBonus.put("GOLDEN_SWORD", 1.0);
        weaponDamageBonus.put("STONE_SWORD", 1.5);
        weaponDamageBonus.put("IRON_SWORD", 2.0);
        weaponDamageBonus.put("DIAMOND_SWORD", 3.0);
        weaponDamageBonus.put("NETHERITE_SWORD", 3.5);

        // 斧头
        weaponDamageBonus.put("WOODEN_AXE", 1.5);
        weaponDamageBonus.put("GOLDEN_AXE", 1.5);
        weaponDamageBonus.put("STONE_AXE", 2.0);
        weaponDamageBonus.put("IRON_AXE", 2.5);
        weaponDamageBonus.put("DIAMOND_AXE", 3.5);
        weaponDamageBonus.put("NETHERITE_AXE", 4.0);

        // 锄头（不常用但配置）
        weaponDamageBonus.put("WOODEN_HOE", 0.5);
        weaponDamageBonus.put("GOLDEN_HOE", 0.5);
        weaponDamageBonus.put("STONE_HOE", 1.0);
        weaponDamageBonus.put("IRON_HOE", 1.5);
        weaponDamageBonus.put("DIAMOND_HOE", 2.0);
        weaponDamageBonus.put("NETHERITE_HOE", 2.5);

        // 弓
        weaponDamageBonus.put("BOW", 2.5);
        weaponDamageBonus.put("CROSSBOW", 3.0);

        // 三叉戟
        weaponDamageBonus.put("TRIDENT", 2.5);
    }

    private void initialize() {
        // 加载持久化的战斗状态
        loadPlayerStates();

        // 加载玩家PVP状态
        loadPlayerPvpStates();

        // 启动定时任务
        startCleanupTask();
        startSaveTask();
        startBuffUpdateTask();

        plugin.getLogger().info("CombatServiceImpl initialized.");
    }

    /**
     * 加载所有玩家PVP状态
     */
    private void loadPlayerPvpStates() {
        Map<UUID, Boolean> pvpStates = storage.loadAllPlayerPvpStates();
        playerPvpEnabled.putAll(pvpStates);
        plugin.getLogger().info("Loaded " + pvpStates.size() + " player PVP states.");
    }

    /**
     * 启动清理任务 - 清理过期的战斗标记和会话
     */
    private void startCleanupTask() {
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();

            // 清理过期的战斗标记
            cleanupExpiredTags(now);

            // 清理超时的战斗会话
            cleanupExpiredSessions(now);

            // 清理空的战场
            cleanupEmptyBattlefields();

        }, 20L * 10, 20L * 10); // 每10秒检查一次
    }

    /**
     * 启动保存任务 - 定期保存战斗状态
     */
    private void startSaveTask() {
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            saveAll();
        }, 20L * 60 * 5, 20L * 60 * 5); // 每5分钟保存一次
    }

    /**
     * 启动Buff更新任务
     */
    private void startBuffUpdateTask() {
        buffUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // 清理过期Buff
            for (UUID playerId : playerBuffs.keySet()) {
                Buff.cleanupExpiredBuffs(playerId);
            }
        }, 20L, 20L); // 每秒检查
    }

    private void cleanupExpiredTags(long now) {
        for (PlayerCombatState state : playerStates.values()) {
            Optional<CombatTag> tagOpt = state.combatTag();
            if (tagOpt.isPresent()) {
                CombatTag t = tagOpt.get();
                if (t.isExpired()) {
                    long sinceLastDamage = now - state.lastDamageTime();
                    if (sinceLastDamage >= config.getTagTimeout()) {
                        if (!state.isInCombat() || sinceLastDamage >= config.getCombatLogoutTimeout()) {
                            state.exitCombat();
                            fireTagExpiredEvent(state.playerId(), t);
                        }
                    }
                }
            }
        }
    }

    private void cleanupExpiredSessions(long now) {
        for (CombatSession session : combatSessions.values()) {
            if (session.state() == CombatSessionState.ACTIVE) {
                long inactiveTime = now - session.lastDamageTime();
                if (inactiveTime >= config.getSessionTimeout() * 1000L) {
                    session.end(CombatEndReason.TIMEOUT);
                    removeSession(session.sessionId());
                }
            }
        }
    }

    private void cleanupEmptyBattlefields() {
        battlefields.entrySet().removeIf(entry -> {
            Battlefield bf = entry.getValue();
            return !bf.isActive() && bf.getParticipantCount() == 0;
        });
    }

    // ==================== 战斗标记管理 ====================

    @Override
    public CombatTag tagPlayer(UUID playerId, UUID taggerId, CombatTagType type) {
        return tagPlayer(playerId, taggerId, type, config.getTagTimeout());
    }

    @Override
    public CombatTag tagPlayer(UUID playerId, UUID taggerId, CombatTagType type, long timeoutMs) {
        PlayerCombatState state = getOrCreatePlayerState(playerId);

        CombatTag tag = new CombatTag(playerId, taggerId, timeoutMs, type);
        state.setCombatTag(tag);
        state.setInCombat(true);
        state.setLastDamageTime(System.currentTimeMillis());

        fireTagAppliedEvent(playerId, tag);

        return tag;
    }

    @Override
    public void untagPlayer(UUID playerId) {
        PlayerCombatState state = playerStates.get(playerId);
        if (state != null) {
            CombatTag oldTag = state.combatTag().orElse(null);
            state.exitCombat();

            if (oldTag != null) {
                fireTagExpiredEvent(playerId, oldTag);
            }
        }
    }

    @Override
    public boolean isTagged(UUID playerId) {
        PlayerCombatState state = playerStates.get(playerId);
        return state != null && state.combatTag().isPresent() && !state.combatTag().get().isExpired();
    }

    @Override
    public boolean isInCombat(UUID playerId) {
        PlayerCombatState state = playerStates.get(playerId);
        if (state == null) return false;

        if (state.combatTag().isPresent() && !state.combatTag().get().isExpired()) {
            return true;
        }

        UUID sessionId = playerSessions.get(playerId);
        if (sessionId != null) {
            CombatSession session = combatSessions.get(sessionId);
            return session != null && session.state() == CombatSessionState.ACTIVE;
        }

        return false;
    }

    @Override
    public Optional<PlayerCombatState> getPlayerState(UUID playerId) {
        return Optional.ofNullable(playerStates.get(playerId));
    }

    public PlayerCombatState getOrCreatePlayerState(UUID playerId) {
        return playerStates.computeIfAbsent(playerId, PlayerCombatState::new);
    }

    // ==================== 伤害计算 ====================

    @Override
    public double calculateDamage(Player attacker, Player defender, String weapon) {
        // 基础伤害（使用攻击者当前手持武器）
        ItemStack weaponItem = attacker.getInventory().getItemInMainHand();
        Material weaponMaterial = weaponItem.getType();

        // 获取武器基础伤害
        double baseDamage = 1.0;
        String weaponName = weaponMaterial.name();
        if (weaponDamageBonus.containsKey(weaponName)) {
            baseDamage = weaponDamageBonus.get(weaponName);
        }

        // 计算属性加成
        double strengthBonus = 1.0;
        Optional<Buff> attackBoost = Optional.ofNullable(
            Buff.getBuff(attacker.getUniqueId(), Buff.BuffType.ATTACK_BOOST)
        );
        if (attackBoost.isPresent()) {
            double boost = attackBoost.get().effectValue() / 100.0;
            strengthBonus += boost;
        }

        // 检查虚弱效果
        Optional<Buff> weakness = Optional.ofNullable(
            Buff.getBuff(attacker.getUniqueId(), Buff.BuffType.WEAKNESS)
        );
        if (weakness.isPresent()) {
            double penalty = weakness.get().effectValue() / 100.0;
            strengthBonus -= penalty;
        }

        // 暴击计算
        boolean isCritical = false;
        Optional<Buff> critBoost = Optional.ofNullable(
            Buff.getBuff(attacker.getUniqueId(), Buff.BuffType.CRITICAL_BOOST)
        );
        double critChance = 0.05; // 默认5%暴击率
        if (critBoost.isPresent()) {
            critChance += critBoost.get().effectValue() / 100.0;
        }
        if (ThreadLocalRandom.current().nextDouble() < critChance) {
            isCritical = true;
            strengthBonus *= 1.5; // 暴击伤害150%
        }

        // 防御减伤计算
        double defenseReduction = 0.0;
        double armorValue = defender.getAttribute(Attribute.ARMOR).getValue();

        Optional<Buff> armorBoost = Optional.ofNullable(
            Buff.getBuff(defender.getUniqueId(), Buff.BuffType.ARMOR_BOOST)
        );
        if (armorBoost.isPresent()) {
            armorValue += armorBoost.get().effectValue();
        }

        // 护甲减伤公式 (简化版)
        defenseReduction = Math.min(0.8, armorValue / (armorValue + 20.0));

        // 抗性减伤
        Optional<Buff> resistance = Optional.ofNullable(
            Buff.getBuff(defender.getUniqueId(), Buff.BuffType.RESISTANCE)
        );
        if (resistance.isPresent()) {
            defenseReduction += Math.min(0.2, resistance.get().effectValue() / 100.0);
        }

        // 最终伤害
        double finalDamage = baseDamage * strengthBonus * (1.0 - defenseReduction);

        // 最低伤害为1
        return Math.max(1.0, finalDamage);
    }

    @Override
    public void recordDamage(Player attacker, Player victim, double damage) {
        UUID attackerId = attacker.getUniqueId();
        UUID victimId = victim.getUniqueId();

        // 相互标记
        tagPlayer(victimId, attackerId, CombatTagType.PLAYER);
        tagPlayer(attackerId, victimId, CombatTagType.PLAYER);

        // 获取或创建战斗会话
        CombatSession session = getOrCreateCombatSession(
            attackerId, victimId, attacker.getLocation(), CombatSessionType.FREE_PVP
        );
        session.recordDamage(attackerId, victimId, (int) damage);

        // 更新玩家状态
        PlayerCombatState attackerState = getOrCreatePlayerState(attackerId);
        PlayerCombatState victimState = getOrCreatePlayerState(victimId);
        attackerState.addDamageDealt((int) damage);
        victimState.addDamageTaken((int) damage);
    }

    @Override
    public void recordKill(UUID killerId, UUID victimId, CombatEndReason reason) {
        PlayerCombatState killerState = getOrCreatePlayerState(killerId);
        PlayerCombatState victimState = getOrCreatePlayerState(victimId);

        killerState.addKill();
        killerState.setLastVictimId(victimId);

        victimState.addDeath();
        victimState.setLastKillerId(killerId);
        victimState.exitCombat();

        UUID sessionId = playerSessions.get(victimId);
        if (sessionId != null) {
            CombatSession session = combatSessions.get(sessionId);
            if (session != null) {
                session.recordKill(killerId, victimId, reason);
                session.end(reason);
                fireSessionEndedEvent(session);
                removeSession(sessionId);
            }
        }

        untagPlayer(victimId);

        // 清理被杀者的Buff
        Buff.clearPlayerBuffs(victimId);
    }

    // ==================== Buff系统 ====================

    @Override
    public Buff applyBuff(UUID target, Buff.BuffType buffType, long duration) {
        Buff buff = new Buff(target, buffType, duration, getDefaultBuffValue(buffType), null);
        playerBuffs.computeIfAbsent(target, k -> new ConcurrentHashMap<>())
                  .put(buff.buffId(), buff);
        return buff;
    }

    @Override
    public void removeBuff(UUID target, Buff.BuffType buffType) {
        ConcurrentMap<UUID, Buff> buffs = playerBuffs.get(target);
        if (buffs != null) {
            buffs.values().stream()
                .filter(b -> b.type() == buffType)
                .findFirst()
                .ifPresent(Buff::remove);
        }
    }

    @Override
    public Collection<Buff> getActiveBuffs(UUID playerId) {
        ConcurrentMap<UUID, Buff> buffs = playerBuffs.get(playerId);
        if (buffs == null) return Collections.emptyList();
        return buffs.values().stream()
            .filter(b -> b.isActive() && !b.isExpired())
            .toList();
    }

    @Override
    public boolean hasBuff(UUID playerId, Buff.BuffType buffType) {
        return Buff.getBuff(playerId, buffType) != null;
    }

    private double getDefaultBuffValue(Buff.BuffType type) {
        return switch (type) {
            case ATTACK_BOOST, DEFENSE_BOOST, ARMOR_BOOST -> 25.0;
            case CRITICAL_BOOST, RESISTANCE -> 15.0;
            case SPEED_BOOST -> 20.0;
            case REGENERATION -> 2.0;
            case POISON, WITHER -> 1.0;
            case WEAKNESS, SLOWNESS -> 30.0;
            case INVULNERABILITY -> 3.0;
            default -> 10.0;
        };
    }

    // ==================== 战斗会话管理 ====================

    public CombatSession getOrCreateCombatSession(UUID attackerId, UUID defenderId, Location location, CombatSessionType type) {
        UUID existingSessionId = findExistingSession(attackerId, defenderId);
        if (existingSessionId != null) {
            CombatSession existing = combatSessions.get(existingSessionId);
            if (existing != null && existing.state() == CombatSessionState.ACTIVE) {
                return existing;
            }
        }

        CombatSession session = new CombatSession(attackerId, defenderId, location, type);
        combatSessions.put(session.sessionId(), session);
        playerSessions.put(attackerId, session.sessionId());
        playerSessions.put(defenderId, session.sessionId());

        getOrCreatePlayerState(attackerId).setCurrentSessionId(session.sessionId());
        getOrCreatePlayerState(defenderId).setCurrentSessionId(session.sessionId());

        fireSessionStartedEvent(session);

        return session;
    }

    private UUID findExistingSession(UUID player1, UUID player2) {
        UUID session1 = playerSessions.get(player1);
        if (session1 != null) {
            CombatSession session = combatSessions.get(session1);
            if (session != null && session.isParticipant(player2)) {
                return session1;
            }
            // session 不为空但参与者不匹配或会话已非活跃状态
            plugin.getLogger().fine("Session found for player1 but not active or participant mismatch: " +
                "player1=" + player1 + ", player2=" + player2 +
                ", sessionId=" + session1 + ", state=" + session.state());
        } else {
            // 防御性编程：找不到会话时的调试日志
            plugin.getLogger().finer("No existing combat session found for players: " +
                "player1=" + player1 + ", player2=" + player2);
        }
        return null;
    }

    @Override
    public Optional<CombatSession> getPlayerSession(UUID playerId) {
        UUID sessionId = playerSessions.get(playerId);
        if (sessionId == null) return Optional.empty();
        return Optional.ofNullable(combatSessions.get(sessionId));
    }

    private void removeSession(UUID sessionId) {
        CombatSession session = combatSessions.remove(sessionId);
        if (session != null) {
            playerSessions.remove(session.attackerId());
            playerSessions.remove(session.defenderId());

            PlayerCombatState attackerState = playerStates.get(session.attackerId());
            PlayerCombatState defenderState = playerStates.get(session.defenderId());
            if (attackerState != null) attackerState.clearCurrentSession();
            if (defenderState != null) defenderState.clearCurrentSession();

            session.participants().forEach(this::untagPlayer);
        }
    }

    @Override
    public Collection<CombatSession> getActiveSessions() {
        return combatSessions.values().stream()
            .filter(s -> s.state() == CombatSessionState.ACTIVE)
            .toList();
    }

    // ==================== 战场管理 ====================

    @Override
    public Battlefield createBattlefield(String name, NationId nation1, NationId nation2,
                                        Location center, double radius, Battlefield.BattlefieldType type) {
        Battlefield battlefield = new Battlefield(name, center, radius, type);
        battlefield.setNation1(nation1);
        battlefield.setNation2(nation2);
        battlefields.put(battlefield.battlefieldId(), battlefield);

        // 添加到国家战场映射
        if (nation1 != null) {
            nationBattlefields.computeIfAbsent(nation1, k -> ConcurrentHashMap.newKeySet())
                             .add(battlefield.battlefieldId());
        }
        if (nation2 != null) {
            nationBattlefields.computeIfAbsent(nation2, k -> ConcurrentHashMap.newKeySet())
                             .add(battlefield.battlefieldId());
        }

        String key = getLocationKey(center);
        battlefieldLocationIndex.put(key, battlefield.battlefieldId());

        plugin.getLogger().info("Battlefield created: " + name + " (Nation1: " + nation1 + " vs Nation2: " + nation2 + ")");

        return battlefield;
    }

    @Override
    public Battlefield createBattlefield(String name, Location center, double radius, Battlefield.BattlefieldType type) {
        return createBattlefield(name, null, null, center, radius, type);
    }

    @Override
    public boolean joinBattlefield(UUID battlefieldId, NationId nationId) {
        Battlefield battlefield = battlefields.get(battlefieldId);
        if (battlefield == null) return false;

        // 检查国家是否有资格加入
        if (battlefield.getNation1() != null && battlefield.getNation1().value().equals(nationId.value())) {
            battlefield.setFaction1Nation(nationId);
            return true;
        }
        if (battlefield.getNation2() != null && battlefield.getNation2().value().equals(nationId.value())) {
            battlefield.setFaction2Nation(nationId);
            return true;
        }

        return false;
    }

    @Override
    public void addPlayerToBattlefield(UUID playerId, Battlefield battlefield) {
        battlefield.addParticipant(playerId);
        getOrCreatePlayerState(playerId);
    }

    @Override
    public void removePlayerFromBattlefield(UUID playerId, Battlefield battlefield) {
        battlefield.removeParticipant(playerId);

        if (battlefield.getParticipantCount() == 0 && battlefield.combatSessions().isEmpty()) {
            battlefield.end();
        }
    }

    @Override
    public boolean startBattle(UUID battlefieldId) {
        Battlefield battlefield = battlefields.get(battlefieldId);
        if (battlefield == null) return false;

        if (!battlefield.isActive()) {
            plugin.getLogger().warning("Cannot start battle - battlefield not ready: " + battlefieldId);
            return false;
        }

        battlefield.startBattle();
        plugin.getLogger().info("Battle started on battlefield: " + battlefield.name());
        return true;
    }

    @Override
    public Battlefield.BattlefieldSummary endBattle(UUID battlefieldId, NationId winner) {
        Battlefield battlefield = battlefields.get(battlefieldId);
        if (battlefield == null) {
            plugin.getLogger().warning("Attempted to end battle on non-existent battlefield: " + battlefieldId);
            return null;
        }

        battlefield.end();
        battlefield.setWinner(winner);

        // 清理所有参与者状态
        for (UUID playerId : battlefield.participants()) {
            untagPlayer(playerId);
            Buff.clearPlayerBuffs(playerId);
        }

        plugin.getLogger().info("Battle ended on battlefield: " + battlefield.name() +
            " - Winner: " + (winner != null ? winner.value() : "Draw"));

        return battlefield.getSummary();
    }

    @Override
    public Optional<Battlefield.BattlefieldSummary> getBattlefieldStatus(UUID battlefieldId) {
        Battlefield battlefield = battlefields.get(battlefieldId);
        if (battlefield == null) {
            return Optional.empty();
        }
        return Optional.of(battlefield.getSummary());
    }

    @Override
    public Optional<Battlefield> getBattlefieldAt(Location location) {
        String key = getLocationKey(location);
        UUID battlefieldId = battlefieldLocationIndex.get(key);
        if (battlefieldId != null) {
            Battlefield bf = battlefields.get(battlefieldId);
            if (bf != null && bf.isInBattlefield(location)) {
                return Optional.of(bf);
            }
        }

        for (Battlefield bf : battlefields.values()) {
            if (bf.isInBattlefield(location)) {
                return Optional.of(bf);
            }
        }

        return Optional.empty();
    }

    @Override
    public Collection<Battlefield> getActiveBattlefields() {
        return battlefields.values().stream()
            .filter(Battlefield::isActive)
            .toList();
    }

    @Override
    public Collection<Battlefield> getAllBattlefields() {
        return Collections.unmodifiableCollection(battlefields.values());
    }

    private String getLocationKey(Location location) {
        int bx = location.getBlockX();
        int bz = location.getBlockZ();
        return location.getWorld().getName() + ":" + (bx / 16) + ":" + (bz / 16);
    }

    // ==================== 外交关系检查 ====================

    public boolean shouldCombatTag(UUID player1, UUID player2) {
        // 检查目标玩家的PVP状态（如果关闭则不允许攻击）
        if (!isPlayerPvpEnabled(player2)) {
            return false;
        }

        if (!config.isPvPEnabled()) {
            return false;
        }

        if (!config.isPvPEnabledInSafeZones() && isInSafeZone(player1) && isInSafeZone(player2)) {
            return false;
        }

        if (nationService.isPresent() && diplomacyService.isPresent()) {
            Optional<Nation> nation1 = nationService.get().getNationByMember(player1);
            Optional<Nation> nation2 = nationService.get().getNationByMember(player2);

            if (nation1.isPresent() && nation2.isPresent()) {
                NationId id1 = nation1.get().id();
                NationId id2 = nation2.get().id();

                if (id1.value().equals(id2.value())) {
                    return config.isFriendlyFireEnabled();
                }

                DiplomacyRelation relation = diplomacyService.get().relationBetween(id1, id2);
                return relation != DiplomacyRelation.ALLIED && relation != DiplomacyRelation.FRIENDLY;
            }
        }

        return true;
    }

    private boolean isInSafeZone(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return false;

        org.bukkit.Location location = player.getLocation();
        String worldName = location.getWorld().getName();
        double px = location.getX();
        double pz = location.getZ();

        // 检查配置的CombatZone
        for (CombatConfig.CombatZone zone : config.getCombatZones().values()) {
            if (zone.isInside(worldName, px, pz)) {
                if (zone.type() == CombatConfig.CombatZoneType.SAFE_ZONE) {
                    return true;
                }
            }
        }

        return false;
    }

    // ==================== 事件监听 ====================

    public void addTagEventListener(Consumer<CombatTagEvent> listener) {
        tagEventListeners.add(listener);
    }

    public void addSessionEventListener(Consumer<CombatSession> listener) {
        sessionEventListeners.add(listener);
    }

    private void fireTagAppliedEvent(UUID playerId, CombatTag tag) {
        CombatTagEvent event = new CombatTagEvent(playerId, tag, CombatTagEvent.EventType.APPLIED);
        tagEventListeners.forEach(l -> l.accept(event));
    }

    private void fireTagExpiredEvent(UUID playerId, CombatTag tag) {
        CombatTagEvent event = new CombatTagEvent(playerId, tag, CombatTagEvent.EventType.EXPIRED);
        tagEventListeners.forEach(l -> l.accept(event));
    }

    private void fireSessionStartedEvent(CombatSession session) {
        sessionEventListeners.forEach(l -> l.accept(session));
    }

    private void fireSessionEndedEvent(CombatSession session) {
        sessionEventListeners.forEach(l -> l.accept(session));
    }

    /**
     * 战斗标记事件
     */
    public record CombatTagEvent(UUID playerId, CombatTag tag, EventType type) {
        public enum EventType {
            APPLIED,
            EXPIRED,
            REMOVED
        }
    }

    // ==================== 持久化 ====================

    private void loadPlayerStates() {
        storage.loadPlayerStates().forEach(snapshot -> {
            PlayerCombatState state = new PlayerCombatState(snapshot.playerId());
            if (snapshot.inCombat()) {
                state.setInCombat(true);
                state.setCombatStartTime(snapshot.combatStartTime());
            }
            state.setLastDamageTime(snapshot.lastDamageTime());

            if (snapshot.lastTaggerId() != null) {
                CombatTag tag = new CombatTag(
                    snapshot.playerId(),
                    snapshot.lastTaggerId(),
                    snapshot.tagTimeout(),
                    CombatTagType.valueOf(snapshot.tagType())
                );
                state.setCombatTag(tag);
            }

            playerStates.put(snapshot.playerId(), state);
        });
    }

    // ==================== 持久化 ====================

    @Override
    public CombatStorage getStorage() {
        return storage;
    }

    @Override
    public List<CombatStorage.CombatHistoryRecord> getCombatHistory(UUID playerId, int limit) {
        return storage.getPlayerCombatHistory(playerId, limit);
    }

    @Override
    public void saveAll() {
        List<PlayerCombatState.CombatStateSnapshot> snapshots = playerStates.values().stream()
            .map(PlayerCombatState::toSnapshot)
            .toList();

        storage.savePlayerStates(snapshots);
    }

    @Override
    public void savePlayerState(UUID playerId) {
        PlayerCombatState state = playerStates.get(playerId);
        if (state != null) {
            storage.savePlayerState(state.toSnapshot());
        }
    }

    @Override
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (scoreboardTask != null) {
            scoreboardTask.cancel();
        }
        if (buffUpdateTask != null) {
            buffUpdateTask.cancel();
        }

        saveAll();

        plugin.getLogger().info("CombatServiceImpl shutdown complete.");
    }

    @Override
    public CombatConfig getConfig() {
        return config;
    }

    @Override
    public CombatConfig getCombatConfig() {
        return config;
    }

    @Override
    public Collection<PlayerCombatState> getAllPlayerStates() {
        return Collections.unmodifiableCollection(playerStates.values());
    }

    // ==================== 玩家PVP开关 ====================

    @Override
    public boolean togglePlayerPvp(UUID playerId) {
        // 默认开启PVP
        boolean currentState = playerPvpEnabled.getOrDefault(playerId, true);
        boolean newState = !currentState;
        playerPvpEnabled.put(playerId, newState);

        // 保存到存储
        storage.savePlayerPvpState(playerId, newState);

        plugin.getLogger().info("Player " + playerId + " PVP toggled to: " + newState);
        return newState;
    }

    @Override
    public boolean isPlayerPvpEnabled(UUID playerId) {
        // 如果没有设置记录，默认为true（开启）
        return playerPvpEnabled.getOrDefault(playerId, true);
    }

    @Override
    public void setPlayerPvpEnabled(UUID playerId, boolean enabled) {
        playerPvpEnabled.put(playerId, enabled);
        storage.savePlayerPvpState(playerId, enabled);
        plugin.getLogger().info("Player " + playerId + " PVP set to: " + enabled);
    }

    @Override
    public CombatStats getStats() {
        AtomicInteger activeSessions = new AtomicInteger(0);
        AtomicInteger totalParticipants = new AtomicInteger(0);

        combatSessions.values().stream()
            .filter(s -> s.state() == CombatSessionState.ACTIVE)
            .forEach(s -> {
                activeSessions.incrementAndGet();
                totalParticipants.addAndGet(s.participants().size());
            });

        return new CombatStats(
            playerStates.size(),
            combatSessions.size(),
            activeSessions.get(),
            totalParticipants.get(),
            battlefields.size()
        );
    }
}
