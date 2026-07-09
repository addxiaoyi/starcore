package dev.starcore.starcore.module.treasury;

import java.util.Properties;

interface TreasuryStateStorage {
    Properties load();

    void save(Properties properties);

    default void saveAsync(Properties properties) {
        save(properties);
    }
}
