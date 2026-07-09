package dev.starcore.starcore.zone;

import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * 经济区服务接口
 */
public interface ZoneService {

    // ==================== 基础操作 ====================

    /**
     * 获取所有经济区
     */
    Collection<ZoneSnapshot> zones();

    /**
     * 获取国家所有经济区
     */
    Collection<ZoneSnapshot> zonesOf(NationId nationId);

    /**
     * 根据ID获取经济区
     */
    Optional<ZoneSnapshot> zoneById(UUID zoneId);

    /**
     * 创建经济区
     */
    ZoneSnapshot createZone(NationId nationId, String name, ZoneType type);

    /**
     * 删除经济区
     */
    boolean deleteZone(UUID zoneId);

    /**
     * 更新经济区
     */
    void updateZone(Zone zone);

    /**
     * 获取国家经济区数量上限
     */
    int zoneLimitFor(NationId nationId);

    /**
     * 获取国家当前经济区数量
     */
    int zoneCountFor(NationId nationId);

    // ==================== 税收加成 ====================

    /**
     * 获取国家总税收加成
     */
    double getTotalTaxBonus(NationId nationId);

    /**
     * 获取国家总产出加成
     */
    double getTotalProductionBonus(NationId nationId);

    /**
     * 计算应用加成后的税收
     */
    BigDecimal calculateTaxWithBonus(NationId nationId, BigDecimal baseTax);

    /**
     * 计算应用加成后的产出
     */
    BigDecimal calculateProductionWithBonus(NationId nationId, BigDecimal baseProduction);

    // ==================== 升级/特效 ====================

    /**
     * 升级经济区
     */
    boolean upgradeZone(UUID zoneId);

    /**
     * 添加特效
     */
    boolean addEffect(UUID zoneId, ZoneEffect effect);

    /**
     * 移除特效
     */
    boolean removeEffect(UUID zoneId, ZoneEffect effect);

    /**
     * 重置特效
     */
    boolean clearEffects(UUID zoneId);

    /**
     * 获取经济区特效列表
     */
    Collection<ZoneEffect> getEffects(UUID zoneId);

    // ==================== 状态 ====================

    /**
     * 启用经济区
     */
    void enableZone(UUID zoneId);

    /**
     * 停用经济区
     */
    void disableZone(UUID zoneId);

    /**
     * 检查经济区是否存在
     */
    boolean zoneExists(UUID zoneId);

    /**
     * 获取经济系统摘要
     */
    String summary();
}
