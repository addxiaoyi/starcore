package dev.starcore.starcore.war;

import org.bukkit.Location;

import java.time.Instant;
import java.util.*;

/**
 * 补给线
 * 连接后方与前线的补给通道
 */
public final class SupplyLine {
    private final UUID id;
    private final UUID warId;
    private final UUID battlefieldId;
    private final String name;
    private final Location start;
    private final Location end;
    private int capacity;               // 补给容量
    private int currentSupply;          // 当前补给量
    private double efficiency;          // 效率 (0-1)
    private boolean disrupted;          // 是否被中断
    private final Instant createdAt;
    private Instant lastSupplyAt;
    private SupplyLineStatus status;

    public SupplyLine(
        UUID id,
        UUID warId,
        UUID battlefieldId,
        String name,
        Location start,
        Location end,
        int capacity,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.warId = Objects.requireNonNull(warId, "warId");
        this.battlefieldId = Objects.requireNonNull(battlefieldId, "battlefieldId");
        this.name = Objects.requireNonNull(name, "name");
        this.start = Objects.requireNonNull(start, "start");
        this.end = Objects.requireNonNull(end, "end");
        this.capacity = capacity;
        this.currentSupply = 0;
        this.efficiency = 1.0;
        this.disrupted = false;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = SupplyLineStatus.ACTIVE;
    }

    public UUID id() {
        return id;
    }

    public UUID warId() {
        return warId;
    }

    public UUID battlefieldId() {
        return battlefieldId;
    }

    public String name() {
        return name;
    }

    public Location start() {
        return start;
    }

    public Location end() {
        return end;
    }

    public int capacity() {
        return capacity;
    }

    public int currentSupply() {
        return currentSupply;
    }

    public double efficiency() {
        return efficiency;
    }

    public boolean isDisrupted() {
        return disrupted;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastSupplyAt() {
        return lastSupplyAt;
    }

    public SupplyLineStatus status() {
        return status;
    }

    /**
     * 补充补给
     */
    public int addSupply(int amount) {
        if (disrupted || status != SupplyLineStatus.ACTIVE) {
            return 0;
        }

        int actualAmount = (int) (amount * efficiency);
        int space = capacity - currentSupply;
        int added = Math.min(actualAmount, space);

        this.currentSupply += added;
        this.lastSupplyAt = Instant.now();

        return added;
    }

    /**
     * 消耗补给
     */
    public int consumeSupply(int amount) {
        int consumed = Math.min(amount, currentSupply);
        this.currentSupply -= consumed;
        return consumed;
    }

    /**
     * 中断补给线
     */
    public void disrupt() {
        this.disrupted = true;
        this.status = SupplyLineStatus.DISRUPTED;
        this.efficiency = Math.max(0.0, efficiency - 0.3);
    }

    /**
     * 修复补给线
     */
    public void repair() {
        this.disrupted = false;
        this.status = SupplyLineStatus.ACTIVE;
        this.efficiency = Math.min(1.0, efficiency + 0.2);
    }

    /**
     * 提高效率
     */
    public void improveEfficiency(double amount) {
        this.efficiency = Math.min(1.0, efficiency + amount);
    }

    /**
     * 降低效率
     */
    public void degradeEfficiency(double amount) {
        this.efficiency = Math.max(0.0, efficiency - amount);
        if (efficiency < 0.3) {
            this.status = SupplyLineStatus.DEGRADED;
        }
    }

    /**
     * 摧毁补给线
     */
    public void destroy() {
        this.status = SupplyLineStatus.DESTROYED;
        this.disrupted = true;
        this.efficiency = 0.0;
        this.currentSupply = 0;
    }

    /**
     * 获取补给线长度（格）
     */
    public double length() {
        if (!start.getWorld().equals(end.getWorld())) {
            return Double.MAX_VALUE;
        }
        return start.distance(end);
    }

    /**
     * 计算有效补给能力
     */
    public int effectiveCapacity() {
        if (disrupted || status == SupplyLineStatus.DESTROYED) {
            return 0;
        }
        return (int) (capacity * efficiency);
    }

    /**
     * 检查位置是否在补给线上
     */
    public boolean isOnRoute(Location location) {
        if (!location.getWorld().equals(start.getWorld())) {
            return false;
        }

        // 简化检查：点到线段的距离
        double distToStart = location.distance(start);
        double distToEnd = location.distance(end);
        double lineLength = length();

        // 如果点到两端的距离之和约等于线段长度，则在线段上
        return Math.abs((distToStart + distToEnd) - lineLength) < 50; // 50格容差
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SupplyLine other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("SupplyLine{id=%s, name='%s', status=%s, supply=%d/%d, efficiency=%.2f}",
            id, name, status, currentSupply, capacity, efficiency);
    }

    /**
     * 补给线状态
     */
    public enum SupplyLineStatus {
        ACTIVE("运作中"),
        DISRUPTED("中断"),
        DEGRADED("效率降低"),
        DESTROYED("已摧毁");

        private final String displayName;

        SupplyLineStatus(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}
