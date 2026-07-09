package dev.starcore.starcore.quest;

import dev.starcore.starcore.core.persistence.PersistenceService;

import java.util.Objects;
import java.util.Properties;

/**
 * 基于 PersistenceService 的任务状态存储实现
 * 使用 Properties 文件持久化任务数据
 */
final class PersistenceQuestStateStorage implements QuestStateStorage {
    private static final String PLAYER_QUEST_FILE = "quests.properties";
    private static final String DAILY_QUEST_FILE = "daily_quests.properties";
    private static final String COMMISSION_FILE = "commissions.properties";

    private final String namespace;
    private final PersistenceService persistenceService;

    PersistenceQuestStateStorage(String namespace, PersistenceService persistenceService) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService");
    }

    @Override
    public Properties load() {
        // 合并所有文件的数据
        Properties merged = new Properties();
        Properties playerQuests = persistenceService.loadProperties(namespace, PLAYER_QUEST_FILE);
        Properties dailyQuests = persistenceService.loadProperties(namespace, DAILY_QUEST_FILE);
        Properties commissions = persistenceService.loadProperties(namespace, COMMISSION_FILE);

        // 添加前缀以区分不同类型的数据
        for (String key : playerQuests.stringPropertyNames()) {
            merged.setProperty("pq." + key, playerQuests.getProperty(key));
        }
        for (String key : dailyQuests.stringPropertyNames()) {
            merged.setProperty("dq." + key, dailyQuests.getProperty(key));
        }
        for (String key : commissions.stringPropertyNames()) {
            merged.setProperty("cm." + key, commissions.getProperty(key));
        }

        return merged;
    }

    @Override
    public void save(Properties properties) {
        Properties playerQuests = new Properties();
        Properties dailyQuests = new Properties();
        Properties commissions = new Properties();

        // 按前缀分离数据
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            if (key.startsWith("pq.")) {
                playerQuests.setProperty(key.substring(3), value);
            } else if (key.startsWith("dq.")) {
                dailyQuests.setProperty(key.substring(3), value);
            } else if (key.startsWith("cm.")) {
                commissions.setProperty(key.substring(3), value);
            }
        }

        persistenceService.saveProperties(namespace, PLAYER_QUEST_FILE, playerQuests);
        persistenceService.saveProperties(namespace, DAILY_QUEST_FILE, dailyQuests);
        persistenceService.saveProperties(namespace, COMMISSION_FILE, commissions);
    }

    @Override
    public void saveAsync(Properties properties) {
        Properties playerQuests = new Properties();
        Properties dailyQuests = new Properties();
        Properties commissions = new Properties();

        // 按前缀分离数据
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            if (key.startsWith("pq.")) {
                playerQuests.setProperty(key.substring(3), value);
            } else if (key.startsWith("dq.")) {
                dailyQuests.setProperty(key.substring(3), value);
            } else if (key.startsWith("cm.")) {
                commissions.setProperty(key.substring(3), value);
            }
        }

        persistenceService.savePropertiesAsync(namespace, PLAYER_QUEST_FILE, playerQuests);
        persistenceService.savePropertiesAsync(namespace, DAILY_QUEST_FILE, dailyQuests);
        persistenceService.savePropertiesAsync(namespace, COMMISSION_FILE, commissions);
    }
}
