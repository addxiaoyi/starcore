package dev.starcore.starcore.module.city;

import dev.starcore.starcore.module.city.model.City;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * 城市状态存储接口
 */
public interface CityStateStorage {

    /**
     * 加载所有城市
     */
    Collection<City> loadAll();

    /**
     * 保存所有城市
     */
    void saveAll(Collection<City> cities);

    /**
     * 保存单个城市
     */
    void save(City city);

    /**
     * 删除城市
     */
    void delete(UUID cityId);

    /**
     * 获取指定城市
     */
    Optional<City> find(UUID cityId);
}
