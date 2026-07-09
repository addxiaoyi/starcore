package dev.starcore.starcore.zone;

import java.util.Properties;

/**
 * 经济区状态存储接口
 */
public interface ZoneStateStorage {
    /**
     * 加载状态
     */
    Properties load();

    /**
     * 同步保存状态
     */
    void save(Properties properties);

    /**
     * 异步保存状态
     */
    void saveAsync(Properties properties);
}
