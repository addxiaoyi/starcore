package dev.starcore.starcore.war;
import java.util.Optional;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 战场服务
 * 管理战场、据点和补给线
 */
public final class BattlefieldService {
    private final Plugin plugin;
    private final WarServiceImpl warService;
    private final Logger logger;

    // 战场
    private final ConcurrentHashMap<UUID, Battlefield> battlefields = new ConcurrentHashMap<>();
    // 战争的战场索引
    private final ConcurrentHashMap<UUID, Set<UUID>> warBattlefields = new ConcurrentHashMap<>();
    // 据点
    private final ConcurrentHashMap<UUID, Stronghold> strongholds = new ConcurrentHashMap<>();
    // 补给线
    private final ConcurrentHashMap<UUID, SupplyLine> supplyLines = new ConcurrentHashMap<>();

    public BattlefieldService(
        Plugin plugin,
        WarServiceImpl warService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.warService = Objects.requireNonNull(warService, "warService");
        this.logger = plugin.getLogger();
    }

    /**
     * 创建战场
     */
    public Battlefield createBattlefield(
        UUID warId,
        String name,
        Location center,
        int radius,
        NationId initialController
    ) {
        Objects.requireNonNull(warId, "warId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(center, "center");

        Battlefield battlefield = new Battlefield(
            UUID.randomUUID(),
            warId,
            name,
            center,
            radius,
            initialController,
            Instant.now()
        );

        battlefields.put(battlefield.id(), battlefield);
        warBattlefields.computeIfAbsent(warId, k -> ConcurrentHashMap.newKeySet())
            .add(battlefield.id());

        logger.info(String.format("Battlefield created: %s for war %s", name, warId));

        return battlefield;
    }

    /**
     * 获取战场
     */
    public Optional<Battlefield> getBattlefield(UUID battlefieldId) {
        return Optional.ofNullable(battlefields.get(battlefieldId));
    }

    /**
     * 获取战争的所有战场
     */
    public List<Battlefield> getBattlefieldsOfWar(UUID warId) {
        Set<UUID> ids = warBattlefields.getOrDefault(warId, Collections.emptySet());
        return ids.stream()
            .map(battlefields::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 根据位置查找战场
     */
    public Optional<Battlefield> findBattlefieldAt(Location location) {
        return battlefields.values().stream()
            .filter(bf -> bf.contains(location))
            .findFirst();
    }

    /**
     * 创建据点
     */
    public Stronghold createStronghold(
        UUID battlefieldId,
        String name,
        Location location,
        Stronghold.StrongholdType type,
        NationId owner,
        int defenseValue
    ) {
        Objects.requireNonNull(battlefieldId, "battlefieldId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");

        Battlefield battlefield = battlefields.get(battlefieldId);
        if (battlefield == null) {
            throw new IllegalArgumentException("Battlefield not found");
        }

        Stronghold stronghold = new Stronghold(
            UUID.randomUUID(),
            battlefieldId,
            name,
            location,
            type,
            owner,
            defenseValue,
            Instant.now()
        );

        strongholds.put(stronghold.id(), stronghold);
        battlefield.addStronghold(stronghold.id());

        logger.info(String.format("Stronghold created: %s (%s) in battlefield %s",
            name, type, battlefield.name()));

        return stronghold;
    }

    /**
     * 占领据点
     */
    public void captureStronghold(UUID strongholdId, NationId newOwner) {
        Stronghold stronghold = strongholds.get(strongholdId);
        if (stronghold == null) {
            throw new IllegalArgumentException("Stronghold not found");
        }

        NationId oldOwner = stronghold.owner();
        stronghold.capture(newOwner);

        // 更新战争积分
        Battlefield battlefield = battlefields.get(stronghold.battlefieldId());
        if (battlefield != null) {
            War war = warService.getWar(battlefield.warId()).orElse(null);
            if (war != null) {
                int points = stronghold.strategicValue();
                warService.addWarScore(battlefield.warId(), newOwner, points, "Captured stronghold: " + stronghold.name());

                logger.info(String.format("Stronghold captured: %s by %s (from %s), +%d war score",
                    stronghold.name(), newOwner, oldOwner, points));
            }
        }
    }

    /**
     * 获取据点
     */
    public Optional<Stronghold> getStronghold(UUID strongholdId) {
        return Optional.ofNullable(strongholds.get(strongholdId));
    }

    /**
     * 获取战场的所有据点
     */
    public List<Stronghold> getStrongholdsInBattlefield(UUID battlefieldId) {
        Battlefield battlefield = battlefields.get(battlefieldId);
        if (battlefield == null) {
            return Collections.emptyList();
        }

        return battlefield.strongholdIds().stream()
            .map(strongholds::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 获取国家的据点
     */
    public List<Stronghold> getStrongholdsOwnedBy(NationId nationId) {
        return strongholds.values().stream()
            .filter(s -> nationId.equals(s.owner()))
            .collect(Collectors.toList());
    }

    /**
     * 创建补给线
     */
    public SupplyLine createSupplyLine(
        UUID warId,
        UUID battlefieldId,
        String name,
        Location start,
        Location end,
        int capacity
    ) {
        Objects.requireNonNull(warId, "warId");
        Objects.requireNonNull(battlefieldId, "battlefieldId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");

        SupplyLine supplyLine = new SupplyLine(
            UUID.randomUUID(),
            warId,
            battlefieldId,
            name,
            start,
            end,
            capacity,
            Instant.now()
        );

        supplyLines.put(supplyLine.id(), supplyLine);

        logger.info(String.format("Supply line created: %s (capacity: %d)", name, capacity));

        return supplyLine;
    }

    /**
     * 获取补给线
     */
    public Optional<SupplyLine> getSupplyLine(UUID supplyLineId) {
        return Optional.ofNullable(supplyLines.get(supplyLineId));
    }

    /**
     * 获取战场的补给线
     */
    public List<SupplyLine> getSupplyLinesInBattlefield(UUID battlefieldId) {
        return supplyLines.values().stream()
            .filter(sl -> sl.battlefieldId().equals(battlefieldId))
            .collect(Collectors.toList());
    }

    /**
     * 中断补给线
     */
    public void disruptSupplyLine(UUID supplyLineId) {
        SupplyLine supplyLine = supplyLines.get(supplyLineId);
        if (supplyLine == null) {
            throw new IllegalArgumentException("Supply line not found");
        }

        supplyLine.disrupt();
        logger.info(String.format("Supply line disrupted: %s", supplyLine.name()));
    }

    /**
     * 修复补给线
     */
    public void repairSupplyLine(UUID supplyLineId) {
        SupplyLine supplyLine = supplyLines.get(supplyLineId);
        if (supplyLine == null) {
            throw new IllegalArgumentException("Supply line not found");
        }

        supplyLine.repair();
        logger.info(String.format("Supply line repaired: %s", supplyLine.name()));
    }

    /**
     * 获取位置附近的补给线
     */
    public List<SupplyLine> getSupplyLinesNear(Location location, double radius) {
        return supplyLines.values().stream()
            .filter(sl -> sl.isOnRoute(location))
            .collect(Collectors.toList());
    }

    /**
     * 军队进入战场
     */
    public void armyEnterBattlefield(UUID battlefieldId, UUID armyId) {
        Battlefield battlefield = battlefields.get(battlefieldId);
        if (battlefield == null) {
            throw new IllegalArgumentException("Battlefield not found");
        }

        battlefield.armyEnter(armyId);
    }

    /**
     * 军队离开战场
     */
    public void armyLeaveBattlefield(UUID battlefieldId, UUID armyId) {
        Battlefield battlefield = battlefields.get(battlefieldId);
        if (battlefield == null) {
            return;
        }

        battlefield.armyLeave(armyId);
    }

    /**
     * 记录战斗
     */
    public void recordBattle(UUID battlefieldId) {
        Battlefield battlefield = battlefields.get(battlefieldId);
        if (battlefield == null) {
            return;
        }

        battlefield.recordBattle();
    }

    /**
     * 改变战场控制方
     */
    public void changeBattlefieldControl(UUID battlefieldId, NationId newController) {
        Battlefield battlefield = battlefields.get(battlefieldId);
        if (battlefield == null) {
            throw new IllegalArgumentException("Battlefield not found");
        }

        NationId oldController = battlefield.controller();
        battlefield.changeController(newController);

        // 更新战争积分
        War war = warService.getWar(battlefield.warId()).orElse(null);
        if (war != null) {
            int points = battlefield.strategicValue();
            warService.addWarScore(battlefield.warId(), newController, points, "Captured battlefield: " + battlefield.name());

            logger.info(String.format("Battlefield control changed: %s from %s to %s, +%d war score",
                battlefield.name(), oldController, newController, points));
        }
    }

    /**
     * 获取统计信息
     */
    public String summary() {
        return String.format("Battlefields: %d, Strongholds: %d, Supply lines: %d",
            battlefields.size(), strongholds.size(), supplyLines.size());
    }
}
