package dev.starcore.starcore.module.satellite;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 卫星国关系数据模型
 */
public class SatelliteState {
    private final NationId suzerainId;
    private final NationId satelliteId;
    private final SatelliteRelation relation;
    private final Instant establishedAt;
    private double tributeRate;
    private boolean active;
    private String establishedReason;

    public SatelliteState(
        NationId suzerainId,
        NationId satelliteId,
        SatelliteRelation relation,
        Instant establishedAt
    ) {
        this.suzerainId = Objects.requireNonNull(suzerainId, "suzerainId");
        this.satelliteId = Objects.requireNonNull(satelliteId, "satelliteId");
        this.relation = Objects.requireNonNull(relation, "relation");
        this.establishedAt = establishedAt != null ? establishedAt : Instant.now();
        this.tributeRate = relation.defaultTributeRate();
        this.active = true;
        this.establishedReason = "";
    }

    public NationId suzerainId() {
        return suzerainId;
    }

    public NationId satelliteId() {
        return satelliteId;
    }

    public SatelliteRelation relation() {
        return relation;
    }

    public Instant establishedAt() {
        return establishedAt;
    }

    public double tributeRate() {
        return tributeRate;
    }

    public void setTributeRate(double tributeRate) {
        this.tributeRate = Math.max(0.0, Math.min(relation.maxTributeRate(), tributeRate));
    }

    public boolean active() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String establishedReason() {
        return establishedReason;
    }

    public void setEstablishedReason(String reason) {
        this.establishedReason = reason != null ? reason : "";
    }

    /**
     * 检查该关系是否允许独立
     */
    public boolean canDeclareIndependence() {
        return relation.canDeclareIndependence() && active;
    }

    /**
     * 检查该关系是否允许独立宣战
     */
    public boolean allowsIndependentWar() {
        return relation.allowsIndependentDiplomacy();
    }

    /**
     * 检查该关系是否需要宗主国批准
     */
    public boolean requiresApprovalForWar() {
        return relation.requiresSuzerainApprovalForWar();
    }

    /**
     * 获取关系持续时间（天）
     */
    public long durationDays() {
        return (Instant.now().getEpochSecond() - establishedAt.getEpochSecond()) / 86400;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SatelliteState that = (SatelliteState) o;
        return suzerainId.equals(that.suzerainId) && satelliteId.equals(that.satelliteId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(suzerainId, satelliteId);
    }

    @Override
    public String toString() {
        return "SatelliteState{" +
            "suzerain=" + suzerainId +
            ", satellite=" + satelliteId +
            ", relation=" + relation +
            ", active=" + active +
            '}';
    }
}
