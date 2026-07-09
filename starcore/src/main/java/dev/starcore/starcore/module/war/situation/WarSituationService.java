package dev.starcore.starcore.module.war.situation;

import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.module.war.WarSnapshot;
import dev.starcore.starcore.war.Battlefield;
import dev.starcore.starcore.war.BattlefieldService;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 战况服务
 * 收集和管理战场实时数据
 */
public final class WarSituationService {

    private final Plugin plugin;
    private final WarService warService;
    private final ArmyService armyService;
    private final NationService nationService;
    private final BattlefieldService battlefieldService;

    // 战况缓存 (warId -> WarSituation)
    private final Map<UUID, WarSituation> situationCache = new ConcurrentHashMap<>();
    // 战斗记录 (warId -> List<WarSituation.BattleRecord>)
    private final Map<UUID, List<WarSituation.BattleRecord>> battleRecords = new ConcurrentHashMap<>();
    // 定时更新服务
    private final ScheduledExecutorService scheduler;

    public WarSituationService(
        Plugin plugin,
        WarService warService,
        ArmyService armyService,
        NationService nationService,
        BattlefieldService battlefieldService
    ) {
        this.plugin = plugin;
        this.warService = warService;
        this.armyService = armyService;
        this.nationService = nationService;
        this.battlefieldService = battlefieldService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        // 每30秒更新一次战况
        startPeriodicUpdate();
    }

    /**
     * 启动定时更新
     */
    private void startPeriodicUpdate() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                updateAllSituations();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to update war situations: " + e.getMessage());
            }
        }, 5, 30, TimeUnit.SECONDS);
    }

    /**
     * 更新所有战况
     */
    private void updateAllSituations() {
        for (WarSnapshot war : warService.activeWars()) {
            try {
                WarSituation situation = generateSituation(war);
                situationCache.put(war.left().value(), situation);
                situationCache.put(war.right().value(), situation);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to update situation for war: " + e.getMessage());
            }
        }
    }

    /**
     * 获取战况快照
     */
    public Optional<WarSituation> getSituation(UUID warId) {
        return Optional.ofNullable(situationCache.get(warId));
    }

    /**
     * 获取国家参与的所有战况
     */
    public List<WarSituation> getSituationsForNation(NationId nationId) {
        return situationCache.values().stream()
            .filter(s -> s.nation1().equals(nationId) || s.nation2().equals(nationId))
            .collect(Collectors.toList());
    }

    /**
     * 获取国家参与的特定战争战况
     */
    public Optional<WarSituation> getSituationForWar(NationId nationId, NationId enemyId) {
        return situationCache.values().stream()
            .filter(s ->
                (s.nation1().equals(nationId) && s.nation2().equals(enemyId)) ||
                (s.nation1().equals(enemyId) && s.nation2().equals(nationId)))
            .findFirst();
    }

    /**
     * 生成战况快照
     */
    public WarSituation generateSituation(WarSnapshot war) {
        NationId nation1 = war.left();
        NationId nation2 = war.right();

        // 获取双方军队
        List<ArmyUnit> armies1 = armyService.getNationArmies(nation1.value());
        List<ArmyUnit> armies2 = armyService.getNationArmies(nation2.value());

        // 计算双方评分
        double score1 = calculateNationScore(armies1);
        double score2 = calculateNationScore(armies2);

        // 获取战场信息
        List<Battlefield> battlefields = battlefieldService.getBattlefieldsOfWar(war.left().value());
        int totalBattles = battleRecords.getOrDefault(war.left().value(), Collections.emptyList()).size();

        // 计算领土变化（简化）
        int territoryChanges = calculateTerritoryChanges(war);

        // 计算伤亡
        int casualties = calculateCasualties(war);
        List<WarSituation.ArmyCasualty> armyCasualties = buildArmyCasualties(armies1, armies2);

        // 获取最近战斗
        List<WarSituation.BattleRecord> recentBattles = battleRecords
            .getOrDefault(war.left().value(), Collections.emptyList())
            .stream()
            .filter(r -> r.timestamp().isAfter(Instant.now().minusSeconds(3600)))
            .collect(Collectors.toList());

        return WarSituation.create(
            war.left().value(),
            nation1,
            nation2,
            totalBattles,
            territoryChanges,
            casualties,
            armyCasualties,
            recentBattles,
            score1,
            score2
        );
    }

    /**
     * 计算国家评分
     */
    private double calculateNationScore(List<ArmyUnit> armies) {
        return armies.stream()
            .filter(ArmyUnit::isAlive)
            .mapToDouble(ArmyUnit::combatRating)
            .sum();
    }

    /**
     * 计算领土变化
     */
    private int calculateTerritoryChanges(WarSnapshot war) {
        // 简化实现，实际应从领地服务获取
        return 0;
    }

    /**
     * 计算总伤亡
     */
    private int calculateCasualties(WarSnapshot war) {
        List<WarSituation.BattleRecord> records = battleRecords.get(war.left().value());
        if (records == null) return 0;

        return records.stream()
            .mapToInt(r -> r.winnerLosses() + r.loserLosses())
            .sum();
    }

    /**
     * 构建军队伤亡列表
     */
    private List<WarSituation.ArmyCasualty> buildArmyCasualties(List<ArmyUnit> armies1, List<ArmyUnit> armies2) {
        List<WarSituation.ArmyCasualty> casualties = new ArrayList<>();

        for (ArmyUnit army : armies1) {
            String name = army.type().name() + "-" + army.id().toString().substring(0, 4);
            NationId nationId = new NationId(army.nationId());
            casualties.add(new WarSituation.ArmyCasualty(
                army.id(), name, nationId,
                0, army.soldiers(), Instant.now()
            ));
        }

        for (ArmyUnit army : armies2) {
            String name = army.type().name() + "-" + army.id().toString().substring(0, 4);
            NationId nationId = new NationId(army.nationId());
            casualties.add(new WarSituation.ArmyCasualty(
                army.id(), name, nationId,
                0, army.soldiers(), Instant.now()
            ));
        }

        return casualties;
    }

    /**
     * 记录战斗
     */
    public void recordBattle(
        UUID warId,
        String battlefieldName,
        NationId winner,
        NationId loser,
        int winnerLosses,
        int loserLosses
    ) {
        WarSituation.BattleRecord record = new WarSituation.BattleRecord(
            UUID.randomUUID(),
            battlefieldName,
            winner,
            loser,
            winnerLosses,
            loserLosses,
            Instant.now()
        );

        battleRecords.computeIfAbsent(warId, k -> new ArrayList<>()).add(record);

        // 清理超过24小时的记录
        Instant cutoff = Instant.now().minusSeconds(86400);
        battleRecords.computeIfAbsent(warId, k -> new ArrayList<>())
            .removeIf(r -> r.timestamp().isBefore(cutoff));
    }

    /**
     * 获取战况摘要
     */
    public String getSummary(NationId nationId) {
        List<WarSituation> situations = getSituationsForNation(nationId);
        if (situations.isEmpty()) {
            return "暂无战况";
        }

        StringBuilder sb = new StringBuilder();
        for (WarSituation s : situations) {
            String enemy = s.nation1().equals(nationId)
                ? nationService.nationById(s.nation2()).map(Nation::name).orElse("未知")
                : nationService.nationById(s.nation1()).map(Nation::name).orElse("未知");

            sb.append(String.format("%s vs %s: %s\n",
                nationId, enemy, s.intensity().displayName()));
        }
        return sb.toString();
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}