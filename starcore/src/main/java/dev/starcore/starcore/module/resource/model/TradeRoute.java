package dev.starcore.starcore.module.resource.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 贸易路线
 * 连接两个国家的资源贸易通道
 */
public final class TradeRoute {
    private final UUID routeId;
    private final NationId originNationId;
    private final NationId destinationNationId;
    private final String routeName;
    private final Instant establishedTime;
    private double efficiency;
    private boolean active;
    private long totalTradeVolume;

    public TradeRoute(UUID routeId, NationId originNationId, NationId destinationNationId,
                      String routeName, Instant establishedTime) {
        this.routeId = Objects.requireNonNull(routeId, "routeId");
        this.originNationId = Objects.requireNonNull(originNationId, "originNationId");
        this.destinationNationId = Objects.requireNonNull(destinationNationId, "destinationNationId");
        this.routeName = Objects.requireNonNull(routeName, "routeName");
        this.establishedTime = Objects.requireNonNull(establishedTime, "establishedTime");
        this.efficiency = 1.0;
        this.active = true;
        this.totalTradeVolume = 0;
    }

    /**
     * 获取路线ID
     */
    public UUID routeId() {
        return routeId;
    }

    /**
     * 获取起点国家ID
     */
    public NationId originNationId() {
        return originNationId;
    }

    /**
     * 获取终点国家ID
     */
    public NationId destinationNationId() {
        return destinationNationId;
    }

    /**
     * 获取路线名称
     */
    public String routeName() {
        return routeName;
    }

    /**
     * 获取建立时间
     */
    public Instant establishedTime() {
        return establishedTime;
    }

    /**
     * 获取效率（0.0-2.0）
     */
    public double efficiency() {
        return efficiency;
    }

    /**
     * 设置效率
     */
    public void setEfficiency(double efficiency) {
        this.efficiency = Math.max(0.0, Math.min(2.0, efficiency));
    }

    /**
     * 是否激活
     */
    public boolean isActive() {
        return active;
    }

    /**
     * 设置激活状态
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * 获取总贸易量
     */
    public long totalTradeVolume() {
        return totalTradeVolume;
    }

    /**
     * 增加贸易量
     */
    public void addTradeVolume(long volume) {
        this.totalTradeVolume += Math.max(0, volume);
    }

    /**
     * 计算运输成本倍数
     * 效率越高，成本越低
     */
    public double transportCostMultiplier() {
        return 2.0 - efficiency;
    }

    /**
     * 检查路线是否连接指定国家
     */
    public boolean connects(NationId nationId) {
        return originNationId.equals(nationId) || destinationNationId.equals(nationId);
    }

    /**
     * 检查路线是否连接两个指定国家
     */
    public boolean connectsBoth(NationId nation1, NationId nation2) {
        return (originNationId.equals(nation1) && destinationNationId.equals(nation2)) ||
               (originNationId.equals(nation2) && destinationNationId.equals(nation1));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradeRoute that = (TradeRoute) o;
        return routeId.equals(that.routeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(routeId);
    }

    @Override
    public String toString() {
        return "TradeRoute{" +
                "routeName='" + routeName + '\'' +
                ", origin=" + originNationId +
                ", destination=" + destinationNationId +
                ", efficiency=" + efficiency +
                ", active=" + active +
                '}';
    }
}
