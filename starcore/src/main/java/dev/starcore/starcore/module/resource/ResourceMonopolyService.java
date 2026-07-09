package dev.starcore.starcore.module.resource;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.model.ResourceMonopoly;

import java.util.Collection;
import java.util.Optional;

/**
 * 资源垄断服务
 * 管理国家对资源的垄断和市场控制
 */
public interface ResourceMonopolyService {
    /**
     * 计算国家在特定资源上的市场份额
     *
     * @param nationId 国家ID
     * @param resourceId 资源ID
     * @return 市场份额（0.0-1.0）
     */
    double calculateMarketShare(NationId nationId, String resourceId);

    /**
     * 检查国家是否垄断某资源
     *
     * @param nationId 国家ID
     * @param resourceId 资源ID
     * @return 是否垄断（市场份额>50%）
     */
    boolean hasMonopoly(NationId nationId, String resourceId);

    /**
     * 获取资源垄断信息
     *
     * @param nationId 国家ID
     * @param resourceId 资源ID
     * @return 垄断信息（如果存在）
     */
    Optional<ResourceMonopoly> getMonopoly(NationId nationId, String resourceId);

    /**
     * 获取国家的所有垄断资源
     *
     * @param nationId 国家ID
     * @return 垄断列表
     */
    Collection<ResourceMonopoly> getMonopolies(NationId nationId);

    /**
     * 获取某资源的所有垄断者
     *
     * @param resourceId 资源ID
     * @return 垄断列表
     */
    Collection<ResourceMonopoly> getMonopoliesForResource(String resourceId);

    /**
     * 创建或更新垄断记录
     *
     * @param nationId 国家ID
     * @param resourceId 资源ID
     * @return 垄断信息
     */
    ResourceMonopoly updateMonopoly(NationId nationId, String resourceId);

    /**
     * 计算垄断收益
     *
     * @param nationId 国家ID
     * @param resourceId 资源ID
     * @return 每日收益
     */
    double calculateMonopolyRevenue(NationId nationId, String resourceId);

    /**
     * 刷新所有垄断数据
     * 更新市场份额和收益
     */
    void refreshMonopolies();

    /**
     * 移除垄断记录（当市场份额低于阈值时）
     *
     * @param nationId 国家ID
     * @param resourceId 资源ID
     * @return 是否成功移除
     */
    boolean removeMonopoly(NationId nationId, String resourceId);

    /**
     * 获取资源的主导国家
     *
     * @param resourceId 资源ID
     * @return 主导国家ID（如果存在）
     */
    Optional<NationId> getDominantNation(String resourceId);
}
