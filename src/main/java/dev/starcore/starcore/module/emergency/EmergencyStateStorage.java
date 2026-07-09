package dev.starcore.starcore.module.emergency;

import java.util.Properties;

/**
 * 紧急状态存储接口
 */
public interface EmergencyStateStorage {
    /**
     * 加载紧急状态
     */
    Properties load();

    /**
     * 保存紧急状态
     */
    void save(Properties properties);

    /**
     * 关闭存储
     */
    void close();
}
