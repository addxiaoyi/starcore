package dev.starcore.starcore.module.resource;

import dev.starcore.starcore.module.resource.model.ResourcePrice;

import java.util.Collection;
import java.util.Optional;

/**
 * 资源价格服务
 * 管理资源价格和市场供需
 */
public interface ResourcePriceService {
    /**
     * 获取资源价格信息
     *
     * @param resourceId 资源ID
     * @return 价格信息
     */
    Optional<ResourcePrice> getPrice(String resourceId);

    /**
     * 获取所有资源价格
     *
     * @return 价格列表
     */
    Collection<ResourcePrice> getAllPrices();

    /**
     * 获取资源当前价格
     *
     * @param resourceId 资源ID
     * @return 当前价格
     */
    double getCurrentPrice(String resourceId);

    /**
     * 更新资源供应量
     *
     * @param resourceId 资源ID
     * @param supply 供应量
     */
    void updateSupply(String resourceId, double supply);

    /**
     * 更新资源需求量
     *
     * @param resourceId 资源ID
     * @param demand 需求量
     */
    void updateDemand(String resourceId, double demand);

    /**
     * 增加供应
     *
     * @param resourceId 资源ID
     * @param amount 增加量
     */
    void addSupply(String resourceId, double amount);

    /**
     * 增加需求
     *
     * @param resourceId 资源ID
     * @param amount 增加量
     */
    void addDemand(String resourceId, double amount);

    /**
     * 刷新所有资源价格
     * 根据供需关系重新计算价格
     */
    void refreshAllPrices();

    /**
     * 获取价格趋势
     *
     * @param resourceId 资源ID
     * @return 价格变化百分比
     */
    double getPriceTrend(String resourceId);

    /**
     * 获取市场状态
     *
     * @param resourceId 资源ID
     * @return 市场状态
     */
    ResourcePrice.MarketState getMarketState(String resourceId);

    /**
     * 设置基础价格
     *
     * @param resourceId 资源ID
     * @param basePrice 基础价格
     */
    void setBasePrice(String resourceId, double basePrice);

    /**
     * 模拟价格波动
     * 根据随机事件影响价格
     *
     * @param resourceId 资源ID
     * @param fluctuationPercentage 波动百分比 (-50 到 +50)
     */
    void simulateFluctuation(String resourceId, double fluctuationPercentage);

    /**
     * 初始化资源价格
     *
     * @param resourceId 资源ID
     * @param basePrice 基础价格
     * @param initialSupply 初始供应量
     * @param initialDemand 初始需求量
     */
    void initializePrice(String resourceId, double basePrice, double initialSupply, double initialDemand);
}
