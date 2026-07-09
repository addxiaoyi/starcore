package dev.starcore.starcore.war;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Location;

import java.time.Instant;
import java.util.*;

/**
 * 战场
 * 代表战争中的一个战场区域
 */
public final class Battlefield {
    private final UUID id;
    private final UUID warId;
    private final String name;
    private final Location center;
    private final int radius;
    private NationId controller;            // 当前控制方
    private final Set<UUID> strongholdIds;  // 战场内的据点
    private final Set<UUID> activeArmies;   // 战场内的军队
    private final Instant createdAt;
    private Instant lastBattleAt;
    private BattlefieldStatus status;

    public Battlefield(
        UUID id,
        UUID warId,
        String name,
        Location center,
        int radius,
        NationId controller,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.warId = Objects.requireNonNull(warId, "warId");
        this.name = Objects.requireNonNull(name, "name");
        this.center = Objects.requireNonNull(center, "center");
        this.radius = radius;
        this.controller = controller;
        this.strongholdIds = new HashSet<>();
        this.activeArmies = new HashSet<>();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = BattlefieldStatus.CALM;
    }

    public UUID id() {
        return id;
    }

    public UUID warId() {
        return warId;
    }

    public String name() {
        return name;
    }

    public Location center() {
        return center;
    }

    public int radius() {
        return radius;
    }

    public NationId controller() {
        return controller;
    }

    public Set<UUID> strongholdIds() {
        return Collections.unmodifiableSet(strongholdIds);
    }

    public Set<UUID> activeArmies() {
        return Collections.unmodifiableSet(activeArmies);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastBattleAt() {
        return lastBattleAt;
    }

    public BattlefieldStatus status() {
        return status;
    }

    /**
     * 改变控制方
     */
    public void changeController(NationId newController) {
        this.controller = Objects.requireNonNull(newController, "newController");
    }

    /**
     * 添加据点
     */
    public void addStronghold(UUID strongholdId) {
        strongholdIds.add(strongholdId);
    }

    /**
     * 移除据点
     */
    public void removeStronghold(UUID strongholdId) {
        strongholdIds.remove(strongholdId);
    }

    /**
     * 军队进入战场
     */
    public void armyEnter(UUID armyId) {
        activeArmies.add(armyId);
        updateStatus();
    }

    /**
     * 军队离开战场
     */
    public void armyLeave(UUID armyId) {
        activeArmies.remove(armyId);
        updateStatus();
    }

    /**
     * 记录战斗
     */
    public void recordBattle() {
        this.lastBattleAt = Instant.now();
        this.status = BattlefieldStatus.INTENSE;
    }

    /**
     * 检查位置是否在战场内
     */
    public boolean contains(Location location) {
        if (!location.getWorld().equals(center.getWorld())) {
            return false;
        }
        return location.distance(center) <= radius;
    }

    /**
     * 获取战场重要度（用于AI决策）
     */
    public int strategicValue() {
        int value = 0;
        value += strongholdIds.size() * 20;  // 据点数量
        value += activeArmies.size() * 10;    // 军队数量
        if (controller != null) {
            value += 30;  // 有控制方的战场更重要
        }
        return value;
    }

    /**
     * 更新战场状态
     */
    private void updateStatus() {
        if (activeArmies.isEmpty()) {
            this.status = BattlefieldStatus.CALM;
        } else if (activeArmies.size() < 3) {
            this.status = BattlefieldStatus.SKIRMISH;
        } else {
            this.status = BattlefieldStatus.INTENSE;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Battlefield other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Battlefield{id=%s, name='%s', controller=%s, status=%s, armies=%d, strongholds=%d}",
            id, name, controller, status, activeArmies.size(), strongholdIds.size());
    }

    /**
     * 战场状态
     */
    public enum BattlefieldStatus {
        CALM("平静"),           // 无战斗
        SKIRMISH("小规模交火"),  // 小规模战斗
        INTENSE("激烈交战");     // 大规模战斗

        private final String displayName;

        BattlefieldStatus(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}
