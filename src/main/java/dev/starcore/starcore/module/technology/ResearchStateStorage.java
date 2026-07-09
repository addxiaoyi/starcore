package dev.starcore.starcore.module.technology;

import java.util.Properties;

/**
 * 研究进度状态存储接口
 * 定义研究进度数据的持久化操作
 */
interface ResearchStateStorage {
    Properties load();

    void save(Properties properties);

    default void saveAsync(Properties properties) {
        save(properties);
    }
}
