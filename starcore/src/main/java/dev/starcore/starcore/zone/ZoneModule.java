package dev.starcore.starcore.zone;
import java.util.Optional;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.config.ConfigurationService;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.zone.command.ZoneCommand;
import dev.starcore.starcore.zone.gui.ZoneGuiListener;
import dev.starcore.starcore.zone.listener.ZoneEffectListener;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/**
 * 经济区模块
 * 提供经济区创建、税收加成、产出计算、特效管理和GUI功能
 */
public final class ZoneModule implements StarCoreModule, ZoneService {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "zone",
        "经济区系统",
        ModuleLayer.MODULE,
        List.of("nation", "treasury"),
        List.of(ZoneService.class),
        "Economic zone management with tax bonuses, production bonuses, and effects."
    );

    private static final int SCALE = 2;
    private static final int DEFAULT_ZONE_LIMIT = 5;
    private static final int LEVEL_BONUS_LIMIT = 10;

    private final ConcurrentMap<UUID, Zone> zones = new ConcurrentHashMap<>();
    private final ConcurrentMap<NationId, List<UUID>> nationZones = new ConcurrentHashMap<>();

    private StarCoreContext context;
    private ConfigurationService configuration;
    private InternalEconomyService economyService;
    private MessageService messages;
    private TreasuryService treasuryService;
    private NationService nationService;
    private ZoneStateStorage stateStorage;
    private ZoneEffectListener effectListener;
    private ZoneGuiListener guiListener;
    private ZoneCommand zoneCommand;
    private BukkitTask effectTask;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.context = context;
        this.configuration = context.configuration();
        this.economyService = context.economyService();
        this.messages = context.serviceRegistry().find(MessageService.class).orElse(null);
        this.treasuryService = context.serviceRegistry().find(TreasuryService.class).orElse(null);
        this.nationService = context.serviceRegistry().find(NationService.class).orElse(null);

        context.persistenceService().ensureNamespace(metadata().id());
        this.stateStorage = new DatabaseAwareZoneStateStorage(
            metadata().id(),
            context.databaseService(),
            context.persistenceService(),
            context.plugin().getLogger()
        );

        // 加载状态
        loadState();

        // 注册特效监听器
        this.effectListener = new ZoneEffectListener(this);
        Bukkit.getPluginManager().registerEvents(effectListener, context.plugin());

        // 注册GUI监听器
        this.guiListener = new ZoneGuiListener(this, messages);
        Bukkit.getPluginManager().registerEvents(guiListener, context.plugin());

        // 注册命令
        this.zoneCommand = new ZoneCommand(this, nationService, messages);
        context.plugin().getCommand("zone").setExecutor(zoneCommand);
        context.plugin().getCommand("zone").setTabCompleter(zoneCommand);

        // 启动特效刷新任务
        startEffectRefreshTask();

        context.plugin().getLogger().info("STARCORE Zone module enabled. Loaded " + zones.size() + " zones.");
    }

    @Override
    public void disable(StarCoreContext context) {
        stopEffectRefreshTask();
        flushState();
        this.context = null;
    }

    // ==================== 基础操作 ====================

    @Override
    public Collection<ZoneSnapshot> zones() {
        return zones.values().stream()
            .map(Zone::snapshot)
            .toList();
    }

    @Override
    public Collection<ZoneSnapshot> zonesOf(NationId nationId) {
        List<UUID> zoneIds = nationZones.get(nationId);
        if (zoneIds == null) {
            return List.of();
        }
        return zoneIds.stream()
            .map(zones::get)
            .filter(Objects::nonNull)
            .map(Zone::snapshot)
            .toList();
    }

    @Override
    public Optional<ZoneSnapshot> zoneById(UUID zoneId) {
        return Optional.ofNullable(zones.get(zoneId))
            .map(Zone::snapshot);
    }

    @Override
    public ZoneSnapshot createZone(NationId nationId, String name, ZoneType type) {
        Objects.requireNonNull(nationId, "nationId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");

        // 检查上限
        if (zoneCountFor(nationId) >= zoneLimitFor(nationId)) {
            throw new IllegalStateException("Zone limit reached for nation: " + nationId);
        }

        UUID id = UUID.randomUUID();
        Zone zone = new Zone(id, nationId, name, type);

        // 初始化加成
        zone.setTaxBonus(type.getTaxBonusPerLevel());
        zone.setProductionBonus(type.getProductionBonusPerLevel());

        // 保存
        zones.put(id, zone);
        nationZones.computeIfAbsent(nationId, k -> new ArrayList<>()).add(id);
        saveState();

        return zone.snapshot();
    }

    @Override
    public boolean deleteZone(UUID zoneId) {
        Zone zone = zones.remove(zoneId);
        if (zone == null) {
            return false;
        }

        // 从国家列表移除
        List<UUID> nationZoneList = nationZones.get(zone.nationId());
        if (nationZoneList != null) {
            nationZoneList.remove(zoneId);
            if (nationZoneList.isEmpty()) {
                nationZones.remove(zone.nationId());
            }
        }

        saveState();
        return true;
    }

    @Override
    public void updateZone(Zone zone) {
        Objects.requireNonNull(zone, "zone");
        zones.put(zone.id(), zone);
        saveState();
    }

    @Override
    public int zoneLimitFor(NationId nationId) {
        // 基础上限 + 国家等级加成
        int baseLimit = DEFAULT_ZONE_LIMIT;
        Optional<Nation> nation = nationService.nationById(nationId);
        if (nation.isPresent()) {
            // 基于国家经验值计算等级 (每1000经验=1级)
            int nationLevel = (int) (nation.get().experience() / 1000);
            baseLimit += Math.min(nationLevel, LEVEL_BONUS_LIMIT);
        }
        return baseLimit;
    }

    @Override
    public int zoneCountFor(NationId nationId) {
        List<UUID> list = nationZones.get(nationId);
        return list == null ? 0 : list.size();
    }

    // ==================== 税收加成 ====================

    @Override
    public double getTotalTaxBonus(NationId nationId) {
        return zonesOf(nationId).stream()
            .filter(ZoneSnapshot::active)
            .mapToDouble(ZoneSnapshot::taxBonus)
            .sum();
    }

    @Override
    public double getTotalProductionBonus(NationId nationId) {
        return zonesOf(nationId).stream()
            .filter(ZoneSnapshot::active)
            .mapToDouble(ZoneSnapshot::productionBonus)
            .sum();
    }

    @Override
    public BigDecimal calculateTaxWithBonus(NationId nationId, BigDecimal baseTax) {
        if (baseTax == null || baseTax.signum() <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.DOWN);
        }
        double bonus = 1.0 + getTotalTaxBonus(nationId);
        return baseTax.multiply(BigDecimal.valueOf(bonus)).setScale(SCALE, RoundingMode.DOWN);
    }

    @Override
    public BigDecimal calculateProductionWithBonus(NationId nationId, BigDecimal baseProduction) {
        if (baseProduction == null || baseProduction.signum() <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.DOWN);
        }
        double bonus = 1.0 + getTotalProductionBonus(nationId);
        return baseProduction.multiply(BigDecimal.valueOf(bonus)).setScale(SCALE, RoundingMode.DOWN);
    }

    // ==================== 升级/特效 ====================

    @Override
    public boolean upgradeZone(UUID zoneId) {
        Zone zone = zones.get(zoneId);
        if (zone == null || !zone.canUpgrade()) {
            return false;
        }

        // 检查升级费用
        BigDecimal upgradeCost = calculateUpgradeCost(zone);
        if (treasuryService != null) {
            if (!treasuryService.withdraw(zone.nationId(), upgradeCost)) {
                return false;
            }
        } else {
            // 如果没有TreasuryService，直接升级
            context.plugin().getLogger().warning("TreasuryService not available for zone upgrade");
        }

        zone.upgrade();
        updateZone(zone);
        return true;
    }

    private BigDecimal calculateUpgradeCost(Zone zone) {
        double baseCost = zone.getType().getBuildCost();
        double levelMultiplier = Math.pow(1.5, zone.getLevel());
        return BigDecimal.valueOf(baseCost * levelMultiplier).setScale(SCALE, RoundingMode.DOWN);
    }

    @Override
    public boolean addEffect(UUID zoneId, ZoneEffect effect) {
        Zone zone = zones.get(zoneId);
        if (zone == null || effect == null) {
            return false;
        }

        if (zone.getEffects().contains(effect)) {
            return false;
        }

        zone.addEffect(effect);
        updateZone(zone);
        return true;
    }

    @Override
    public boolean removeEffect(UUID zoneId, ZoneEffect effect) {
        Zone zone = zones.get(zoneId);
        if (zone == null || effect == null) {
            return false;
        }

        if (!zone.getEffects().contains(effect)) {
            return false;
        }

        zone.removeEffect(effect);
        updateZone(zone);
        return true;
    }

    @Override
    public boolean clearEffects(UUID zoneId) {
        Zone zone = zones.get(zoneId);
        if (zone == null) {
            return false;
        }

        zone.clearEffects();
        updateZone(zone);
        return true;
    }

    @Override
    public Collection<ZoneEffect> getEffects(UUID zoneId) {
        Zone zone = zones.get(zoneId);
        if (zone == null) {
            return List.of();
        }
        return List.copyOf(zone.getEffects());
    }

    // ==================== 状态 ====================

    @Override
    public void enableZone(UUID zoneId) {
        Zone zone = zones.get(zoneId);
        if (zone != null) {
            zone.setActive(true);
            updateZone(zone);
        }
    }

    @Override
    public void disableZone(UUID zoneId) {
        Zone zone = zones.get(zoneId);
        if (zone != null) {
            zone.setActive(false);
            updateZone(zone);
        }
    }

    @Override
    public boolean zoneExists(UUID zoneId) {
        return zones.containsKey(zoneId);
    }

    @Override
    public String summary() {
        int total = zones.size();
        long active = zones.values().stream().filter(Zone::isActive).count();
        return String.format("ZoneModule[total=%d, active=%d]", total, active);
    }

    // ==================== 特效刷新任务 ====================

    private void startEffectRefreshTask() {
        stopEffectRefreshTask();
        // 每分钟刷新一次特效
        this.effectTask = Bukkit.getScheduler().runTaskTimer(
            context.plugin(),
            () -> refreshEffects(),
            1200L,  // 延迟1分钟
            1200L   // 周期1分钟
        );
    }

    private void stopEffectRefreshTask() {
        if (effectTask != null) {
            effectTask.cancel();
            effectTask = null;
        }
    }

    private void refreshEffects() {
        // 刷新活跃经济区特效
        zones.values().stream()
            .filter(Zone::isActive)
            .forEach(this::applyEffects);
    }

    private void applyEffects(Zone zone) {
        // 根据特效类型应用不同效果
        for (ZoneEffect effect : zone.getEffects()) {
            try {
                applyEffect(zone, effect);
            } catch (Exception e) {
                context.plugin().getLogger().log(Level.WARNING,
                    "Failed to apply effect " + effect + " to zone " + zone.id(), e);
            }
        }
    }

    private void applyEffect(Zone zone, ZoneEffect effect) {
        // 特效应用逻辑（由监听器处理）
        switch (effect) {
            case LUCK_AURA, SPEED_AURA, XP_BOOST -> {
                // 玩家进入经济区时应用
            }
            case DEFENSE_BONUS, PEACE_ZONE, PROTECTION_SHIELD -> {
                // 领地保护增强
            }
            default -> {
                // 其他被动效果
            }
        }
    }

    // ==================== 持久化 ====================

    private void saveState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.saveAsync(ZoneStateCodec.toProperties(zones));
    }

    private void flushState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.save(ZoneStateCodec.toProperties(zones));
    }

    private void loadState() {
        zones.clear();
        nationZones.clear();
        Map<UUID, Zone> loaded = stateStorage == null
            ? Map.of()
            : ZoneStateCodec.fromProperties(stateStorage.load());
        zones.putAll(loaded);
        // 重建国家-经济区索引
        for (Zone zone : zones.values()) {
            nationZones.computeIfAbsent(zone.nationId(), k -> new ArrayList<>()).add(zone.id());
        }
    }

    // ==================== 内部工具方法 ====================

    public Optional<Zone> getZone(UUID zoneId) {
        return Optional.ofNullable(zones.get(zoneId));
    }

    public Optional<Zone> getZoneByNation(NationId nationId, UUID zoneId) {
        Zone zone = zones.get(zoneId);
        if (zone != null && zone.nationId().equals(nationId)) {
            return Optional.of(zone);
        }
        return Optional.empty();
    }

    public Collection<Zone> getActiveZones() {
        return zones.values().stream()
            .filter(Zone::isActive)
            .toList();
    }

    /**
     * 获取国家服务
     */
    public NationService getNationService() {
        return nationService;
    }
}
