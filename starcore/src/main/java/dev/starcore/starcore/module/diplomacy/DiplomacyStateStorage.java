package dev.starcore.starcore.module.diplomacy;

import java.util.Properties;

interface DiplomacyStateStorage {
    Properties load();

    void save(Properties properties);

    default void saveAsync(Properties properties) {
        save(properties);
    }
}
