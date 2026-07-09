package dev.starcore.starcore.module.resource.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 资源禁运
 * 对特定国家实施资源禁运
 */
public final class ResourceEmbargo {
    private final UUID embargoId;
    private final NationId initiatorNationId;
    private final Set<NationId> targetNationIds;
    private final String resourceId;
    private final Instant startTime;
    private final Instant expiryTime;
    private final String reason;
    private boolean active;

    public ResourceEmbargo(UUID embargoId, NationId initiatorNationId, Set<NationId> targetNationIds,
                           String resourceId, Instant startTime, Instant expiryTime, String reason) {
        this.embargoId = Objects.requireNonNull(embargoId, "embargoId");
        this.initiatorNationId = Objects.requireNonNull(initiatorNationId, "initiatorNationId");
        this.targetNationIds = new HashSet<>(targetNationIds);
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.expiryTime = expiryTime;
        this.reason = reason != null ? reason : "未说明";
        this.active = true;
    }

    /**
     * 获取禁运ID
     */
    public UUID embargoId() {
        return embargoId;
    }

    /**
     * 获取发起国ID
     */
    public NationId initiatorNationId() {
        return initiatorNationId;
    }

    /**
     * 获取目标国家ID集合
     */
    public Set<NationId> targetNationIds() {
        return Collections.unmodifiableSet(targetNationIds);
    }

    /**
     * 获取资源ID
     */
    public String resourceId() {
        return resourceId;
    }

    /**
     * 获取开始时间
     */
    public Instant startTime() {
        return startTime;
    }

    /**
     * 获取到期时间（可能为null表示无限期）
     */
    public Instant expiryTime() {
        return expiryTime;
    }

    /**
     * 获取原因
     */
    public String reason() {
        return reason;
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
     * 添加目标国家
     */
    public void addTargetNation(NationId nationId) {
        targetNationIds.add(nationId);
    }

    /**
     * 移除目标国家
     */
    public void removeTargetNation(NationId nationId) {
        targetNationIds.remove(nationId);
    }

    /**
     * 检查是否禁运特定国家
     */
    public boolean isEmbargoed(NationId nationId) {
        return active && targetNationIds.contains(nationId) && !isExpired();
    }

    /**
     * 检查是否过期
     */
    public boolean isExpired() {
        return expiryTime != null && Instant.now().isAfter(expiryTime);
    }

    /**
     * 检查是否有效（激活且未过期）
     */
    public boolean isEffective() {
        return active && !isExpired();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceEmbargo that = (ResourceEmbargo) o;
        return embargoId.equals(that.embargoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(embargoId);
    }
}
