package dev.starcore.starcore.quest;

import java.util.Properties;

/**
 * 任务状态存储接口
 * 定义任务数据的持久化操作
 */
public interface QuestStateStorage {
    /**
     * 加载所有玩家任务数据
     * @return 包含所有数据的 Properties 对象
     */
    Properties load();

    /**
     * 同步保存数据
     * @param properties 要保存的数据
     */
    void save(Properties properties);

    /**
     * 异步保存数据
     * @param properties 要保存的数据
     */
    void saveAsync(Properties properties);
}
