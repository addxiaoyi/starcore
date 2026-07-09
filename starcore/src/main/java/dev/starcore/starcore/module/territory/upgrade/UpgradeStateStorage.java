package dev.starcore.starcore.module.territory.upgrade;

import java.util.Map;

/**
 * Storage interface for upgrade progress persistence.
 * 升级进度持久化存储接口
 */
public interface UpgradeStateStorage {

    /**
     * Save upgrade state asynchronously.
     */
    void saveAsync(Map<String, String> state);

    /**
     * Save upgrade state synchronously.
     */
    void save(Map<String, String> state);

    /**
     * Load upgrade state.
     */
    Map<String, String> load();
}
