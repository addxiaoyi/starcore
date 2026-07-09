package dev.starcore.starcore.territory;

import java.util.Collection;
import java.util.Optional;

/**
 * 领土存储接口
 * 定义领土和子区域的持久化操作
 */
public interface TerritoryStorage {

    /**
     * 加载所有领土
     * @return 领土集合
     */
    Collection<Territory> loadTerritories();

    /**
     * 保存所有领土
     * @param territories 领土集合
     */
    void saveTerritories(Collection<Territory> territories);

    /**
     * 加载所有子区域
     * @return 子区域集合
     */
    Collection<SubRegion> loadSubRegions();

    /**
     * 保存所有子区域
     * @param subRegions 子区域集合
     */
    void saveSubRegions(Collection<SubRegion> subRegions);

    /**
     * 异步保存所有数据
     */
    default void saveAllAsync(Collection<Territory> territories, Collection<SubRegion> subRegions) {
        saveTerritories(territories);
        saveSubRegions(subRegions);
    }

    /**
     * 增量保存单个领土（审计 A-033/A-034：避免全量写盘）
     */
    default void saveTerritoryIncremental(Territory territory) {
        saveTerritories(java.util.Collections.singletonList(territory));
    }

    /**
     * 检查是否使用 SQL 模式
     */
    boolean isUsingSql();
}
