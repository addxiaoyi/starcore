package dev.starcore.starcore.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.*;
import java.util.logging.Logger;

/**
 * 任务持久化管理器
 * 负责序列化/反序列化任务数据并管理保存/加载操作
 */
public class QuestPersistenceHandler {

    private static final Gson GSON = new GsonBuilder().create();

    private final QuestStateStorage stateStorage;
    private final Logger logger;

    public QuestPersistenceHandler(QuestStateStorage stateStorage, Logger logger) {
        this.stateStorage = stateStorage;
        this.logger = logger;
    }

    /**
     * 保存玩家任务数据
     * @param playerQuests 玩家任务映射
     */
    public void savePlayerQuests(Map<UUID, PlayerQuest> playerQuests) {
        Properties properties = new Properties();

        for (Map.Entry<UUID, PlayerQuest> entry : playerQuests.entrySet()) {
            String prefix = "pq." + entry.getKey().toString() + ".";
            PlayerQuest pq = entry.getValue();

            // 序列化进行中任务
            Map<String, Quest> activeQuests = pq.getActiveQuests();
            if (!activeQuests.isEmpty()) {
                JsonArray activeArray = new JsonArray();
                for (Map.Entry<String, Quest> questEntry : activeQuests.entrySet()) {
                    activeArray.add(serializeQuest(questEntry.getValue()));
                }
                properties.setProperty(prefix + "active", GSON.toJson(activeArray));
            }

            // 序列化开始时间
            Map<String, Long> startTimes = pq.getQuestStartTimes();
            if (!startTimes.isEmpty()) {
                StringBuilder startTimesStr = new StringBuilder();
                for (Map.Entry<String, Long> stEntry : startTimes.entrySet()) {
                    if (startTimesStr.length() > 0) startTimesStr.append(";");
                    startTimesStr.append(stEntry.getKey()).append(":").append(stEntry.getValue());
                }
                properties.setProperty(prefix + "startTimes", startTimesStr.toString());
            }

            // 序列化已完成任务
            Map<String, Long> completedQuests = pq.getCompletedQuests();
            if (!completedQuests.isEmpty()) {
                StringBuilder completedStr = new StringBuilder();
                for (Map.Entry<String, Long> compEntry : completedQuests.entrySet()) {
                    if (completedStr.length() > 0) completedStr.append(";");
                    completedStr.append(compEntry.getKey()).append(":").append(compEntry.getValue());
                }
                properties.setProperty(prefix + "completed", completedStr.toString());
            }

            // 序列化失败任务
            Map<String, Long> failedQuests = pq.getFailedQuests();
            if (!failedQuests.isEmpty()) {
                StringBuilder failedStr = new StringBuilder();
                for (Map.Entry<String, Long> failEntry : failedQuests.entrySet()) {
                    if (failedStr.length() > 0) failedStr.append(";");
                    failedStr.append(failEntry.getKey()).append(":").append(failEntry.getValue());
                }
                properties.setProperty(prefix + "failed", failedStr.toString());
            }

            // 序列化冷却时间
            Map<String, Long> cooldowns = pq.getQuestCooldowns();
            if (!cooldowns.isEmpty()) {
                StringBuilder cooldownStr = new StringBuilder();
                for (Map.Entry<String, Long> cdEntry : cooldowns.entrySet()) {
                    if (cooldownStr.length() > 0) cooldownStr.append(";");
                    cooldownStr.append(cdEntry.getKey()).append(":").append(cdEntry.getValue());
                }
                properties.setProperty(prefix + "cooldowns", cooldownStr.toString());
            }

            // 序列化完成次数
            Map<String, Integer> completionCounts = pq.getQuestCompletionCounts();
            if (!completionCounts.isEmpty()) {
                StringBuilder countsStr = new StringBuilder();
                for (Map.Entry<String, Integer> countEntry : completionCounts.entrySet()) {
                    if (countsStr.length() > 0) countsStr.append(";");
                    countsStr.append(countEntry.getKey()).append(":").append(countEntry.getValue());
                }
                properties.setProperty(prefix + "counts", countsStr.toString());
            }
        }

        stateStorage.save(properties);
    }

    /**
     * 异步保存玩家任务数据
     */
    public void savePlayerQuestsAsync(Map<UUID, PlayerQuest> playerQuests) {
        Properties properties = new Properties();

        for (Map.Entry<UUID, PlayerQuest> entry : playerQuests.entrySet()) {
            String prefix = "pq." + entry.getKey().toString() + ".";
            PlayerQuest pq = entry.getValue();

            JsonArray activeArray = new JsonArray();
            for (Map.Entry<String, Quest> questEntry : pq.getActiveQuests().entrySet()) {
                activeArray.add(serializeQuest(questEntry.getValue()));
            }
            if (activeArray.size() > 0) {
                properties.setProperty(prefix + "active", GSON.toJson(activeArray));
            }

            Map<String, Long> startTimes = pq.getQuestStartTimes();
            if (!startTimes.isEmpty()) {
                StringBuilder startTimesStr = new StringBuilder();
                for (Map.Entry<String, Long> stEntry : startTimes.entrySet()) {
                    if (startTimesStr.length() > 0) startTimesStr.append(";");
                    startTimesStr.append(stEntry.getKey()).append(":").append(stEntry.getValue());
                }
                properties.setProperty(prefix + "startTimes", startTimesStr.toString());
            }

            Map<String, Long> completedQuests = pq.getCompletedQuests();
            if (!completedQuests.isEmpty()) {
                StringBuilder completedStr = new StringBuilder();
                for (Map.Entry<String, Long> compEntry : completedQuests.entrySet()) {
                    if (completedStr.length() > 0) completedStr.append(";");
                    completedStr.append(compEntry.getKey()).append(":").append(compEntry.getValue());
                }
                properties.setProperty(prefix + "completed", completedStr.toString());
            }

            Map<String, Long> failedQuests = pq.getFailedQuests();
            if (!failedQuests.isEmpty()) {
                StringBuilder failedStr = new StringBuilder();
                for (Map.Entry<String, Long> failEntry : failedQuests.entrySet()) {
                    if (failedStr.length() > 0) failedStr.append(";");
                    failedStr.append(failEntry.getKey()).append(":").append(failEntry.getValue());
                }
                properties.setProperty(prefix + "failed", failedStr.toString());
            }

            Map<String, Long> cooldowns = pq.getQuestCooldowns();
            if (!cooldowns.isEmpty()) {
                StringBuilder cooldownStr = new StringBuilder();
                for (Map.Entry<String, Long> cdEntry : cooldowns.entrySet()) {
                    if (cooldownStr.length() > 0) cooldownStr.append(";");
                    cooldownStr.append(cdEntry.getKey()).append(":").append(cdEntry.getValue());
                }
                properties.setProperty(prefix + "cooldowns", cooldownStr.toString());
            }

            Map<String, Integer> completionCounts = pq.getQuestCompletionCounts();
            if (!completionCounts.isEmpty()) {
                StringBuilder countsStr = new StringBuilder();
                for (Map.Entry<String, Integer> countEntry : completionCounts.entrySet()) {
                    if (countsStr.length() > 0) countsStr.append(";");
                    countsStr.append(countEntry.getKey()).append(":").append(countEntry.getValue());
                }
                properties.setProperty(prefix + "counts", countsStr.toString());
            }
        }

        stateStorage.saveAsync(properties);
    }

    /**
     * 加载玩家任务数据
     * @param playerQuests 玩家任务映射（将被填充）
     * @param questRegistry 任务注册表（用于恢复任务实例）
     */
    public void loadPlayerQuests(Map<UUID, PlayerQuest> playerQuests, Map<String, Quest> questRegistry) {
        Properties properties = stateStorage.load();
        Map<String, String> playerQuestData = new HashMap<>();

        // 提取玩家任务数据
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("pq.")) {
                String playerKey = key.substring(3);
                int dotIndex = playerKey.indexOf('.');
                if (dotIndex > 0) {
                    String playerId = playerKey.substring(0, dotIndex);
                    String dataKey = playerKey.substring(dotIndex + 1);
                    playerQuestData.put(playerId + "." + dataKey, properties.getProperty(key));
                }
            }
        }

        // 按玩家分组并恢复数据
        Map<String, Map<String, String>> playerDataMap = new HashMap<>();
        for (Map.Entry<String, String> entry : playerQuestData.entrySet()) {
            String playerId = entry.getKey();
            int dotIndex = playerId.indexOf('.');
            if (dotIndex > 0) {
                String playerUUID = playerId.substring(0, dotIndex);
                String dataKey = playerId.substring(dotIndex + 1);
                playerDataMap.computeIfAbsent(playerUUID, k -> new HashMap<>())
                    .put(dataKey, entry.getValue());
            }
        }

        for (Map.Entry<String, Map<String, String>> playerEntry : playerDataMap.entrySet()) {
            UUID playerId;
            try {
                playerId = UUID.fromString(playerEntry.getKey());
            } catch (IllegalArgumentException e) {
                continue;
            }

            PlayerQuest pq = new PlayerQuest(playerId);
            Map<String, String> data = playerEntry.getValue();

            // 恢复进行中任务
            String activeJson = data.get("active");
            if (activeJson != null && !activeJson.isEmpty()) {
                try {
                    JsonArray activeArray = JsonParser.parseString(activeJson).getAsJsonArray();
                    for (int i = 0; i < activeArray.size(); i++) {
                        Quest quest = deserializeQuest(activeArray.get(i).getAsJsonObject(), questRegistry);
                        if (quest != null) {
                            pq.acceptQuest(quest);
                        }
                    }
                } catch (Exception e) {
                    logger.warning("[QuestPersistence] 加载玩家 " + playerId + " 任务数据失败: " + e.getMessage());
                }
            }

            // 恢复开始时间
            String startTimesStr = data.get("startTimes");
            if (startTimesStr != null && !startTimesStr.isEmpty()) {
                // 恢复逻辑在 acceptQuest 中处理
            }

            // 恢复已完成任务
            String completedStr = data.get("completed");
            if (completedStr != null && !completedStr.isEmpty()) {
                for (String entry : completedStr.split(";")) {
                    String[] parts = entry.split(":");
                    if (parts.length >= 2) {
                        try {
                            // 手动设置已完成状态（绕过 activeQuests 检查）
                            String questId = parts[0];
                            long completionTime = Long.parseLong(parts[1]);
                            // 通过反射设置（如果 PlayerQuest 提供相应方法）
                            pq.markQuestCompleted(questId, completionTime);
                        } catch (Exception e) {
                            logger.warning("[QuestPersistence] 恢复完成任务失败: " + e.getMessage());
                        }
                    }
                }
            }

            // 恢复冷却时间
            String cooldownsStr = data.get("cooldowns");
            if (cooldownsStr != null && !cooldownsStr.isEmpty()) {
                for (String entry : cooldownsStr.split(";")) {
                    String[] parts = entry.split(":");
                    if (parts.length >= 2) {
                        try {
                            long cooldownEnd = Long.parseLong(parts[1]);
                            // 通过反射设置冷却时间
                            pq.setQuestCooldown(parts[0], cooldownEnd);
                        } catch (Exception e) {
                            // 忽略
                        }
                    }
                }
            }

            // 恢复完成次数
            String countsStr = data.get("counts");
            if (countsStr != null && !countsStr.isEmpty()) {
                for (String entry : countsStr.split(";")) {
                    String[] parts = entry.split(":");
                    if (parts.length >= 2) {
                        try {
                            int count = Integer.parseInt(parts[1]);
                            pq.setQuestCompletionCount(parts[0], count);
                        } catch (Exception e) {
                            // 忽略
                        }
                    }
                }
            }

            playerQuests.put(playerId, pq);
        }
    }

    /**
     * 保存每日任务数据
     */
    public void saveDailyQuests(Map<UUID, List<Quest>> playerDailyQuests, Map<UUID, Long> lastRefreshTime) {
        Properties properties = new Properties();

        // 保存每日任务
        for (Map.Entry<UUID, List<Quest>> entry : playerDailyQuests.entrySet()) {
            String prefix = "dq." + entry.getKey().toString();
            List<Quest> quests = entry.getValue();

            if (!quests.isEmpty()) {
                JsonArray questArray = new JsonArray();
                for (Quest quest : quests) {
                    questArray.add(serializeQuest(quest));
                }
                properties.setProperty(prefix + ".quests", GSON.toJson(questArray));
            }
        }

        // 保存刷新时间
        StringBuilder refreshStr = new StringBuilder();
        for (Map.Entry<UUID, Long> entry : lastRefreshTime.entrySet()) {
            if (refreshStr.length() > 0) refreshStr.append(";");
            refreshStr.append(entry.getKey().toString()).append(":").append(entry.getValue());
        }
        if (refreshStr.length() > 0) {
            properties.setProperty("dq.refresh_times", refreshStr.toString());
        }

        stateStorage.save(properties);
    }

    /**
     * 异步保存每日任务数据
     */
    public void saveDailyQuestsAsync(Map<UUID, List<Quest>> playerDailyQuests, Map<UUID, Long> lastRefreshTime) {
        Properties properties = new Properties();

        for (Map.Entry<UUID, List<Quest>> entry : playerDailyQuests.entrySet()) {
            String prefix = "dq." + entry.getKey().toString();
            List<Quest> quests = entry.getValue();

            if (!quests.isEmpty()) {
                JsonArray questArray = new JsonArray();
                for (Quest quest : quests) {
                    questArray.add(serializeQuest(quest));
                }
                properties.setProperty(prefix + ".quests", GSON.toJson(questArray));
            }
        }

        StringBuilder refreshStr = new StringBuilder();
        for (Map.Entry<UUID, Long> entry : lastRefreshTime.entrySet()) {
            if (refreshStr.length() > 0) refreshStr.append(";");
            refreshStr.append(entry.getKey().toString()).append(":").append(entry.getValue());
        }
        if (refreshStr.length() > 0) {
            properties.setProperty("dq.refresh_times", refreshStr.toString());
        }

        stateStorage.saveAsync(properties);
    }

    /**
     * 加载每日任务数据
     */
    public void loadDailyQuests(Map<UUID, List<Quest>> playerDailyQuests, Map<UUID, Long> lastRefreshTime, Map<String, Quest> questRegistry) {
        Properties properties = stateStorage.load();

        // 加载每日任务
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("dq.") && key.endsWith(".quests")) {
                String playerId = key.substring(3, key.length() - 7);
                try {
                    UUID uuid = UUID.fromString(playerId);
                    String json = properties.getProperty(key);
                    JsonArray questArray = JsonParser.parseString(json).getAsJsonArray();

                    List<Quest> quests = new ArrayList<>();
                    for (int i = 0; i < questArray.size(); i++) {
                        Quest quest = deserializeQuest(questArray.get(i).getAsJsonObject(), questRegistry);
                        if (quest != null) {
                            quests.add(quest);
                        }
                    }
                    playerDailyQuests.put(uuid, quests);
                } catch (Exception e) {
                    logger.warning("[QuestPersistence] 加载每日任务失败: " + e.getMessage());
                }
            }
        }

        // 加载刷新时间
        String refreshStr = properties.getProperty("dq.refresh_times");
        if (refreshStr != null && !refreshStr.isEmpty()) {
            for (String entry : refreshStr.split(";")) {
                String[] parts = entry.split(":");
                if (parts.length >= 2) {
                    try {
                        UUID uuid = UUID.fromString(parts[0]);
                        long time = Long.parseLong(parts[1]);
                        lastRefreshTime.put(uuid, time);
                    } catch (Exception e) {
                        // 忽略
                    }
                }
            }
        }
    }

    /**
     * 保存委托数据
     */
    public void saveCommissions(Map<String, Commission> activeCommissions,
                                 Map<UUID, List<String>> playerCommissions,
                                 Map<UUID, List<String>> acceptedCommissions) {
        Properties properties = new Properties();

        // 保存活跃委托
        JsonArray commissionsArray = new JsonArray();
        for (Commission commission : activeCommissions.values()) {
            commissionsArray.add(serializeCommission(commission));
        }
        if (commissionsArray.size() > 0) {
            properties.setProperty("cm.active", GSON.toJson(commissionsArray));
        }

        // 保存玩家发布的委托
        StringBuilder publisherStr = new StringBuilder();
        for (Map.Entry<UUID, List<String>> entry : playerCommissions.entrySet()) {
            String value = entry.getKey().toString() + "=" + String.join(",", entry.getValue());
            if (publisherStr.length() > 0) publisherStr.append(";");
            publisherStr.append(value);
        }
        if (publisherStr.length() > 0) {
            properties.setProperty("cm.publishers", publisherStr.toString());
        }

        // 保存玩家接取的委托
        StringBuilder acceptorStr = new StringBuilder();
        for (Map.Entry<UUID, List<String>> entry : acceptedCommissions.entrySet()) {
            String value = entry.getKey().toString() + "=" + String.join(",", entry.getValue());
            if (acceptorStr.length() > 0) acceptorStr.append(";");
            acceptorStr.append(value);
        }
        if (acceptorStr.length() > 0) {
            properties.setProperty("cm.acceptors", acceptorStr.toString());
        }

        stateStorage.save(properties);
    }

    /**
     * 异步保存委托数据（包含排行榜）
     */
    public void saveCommissionsAsync(Map<String, Commission> activeCommissions,
                                      Map<UUID, List<String>> playerCommissions,
                                      Map<UUID, List<String>> acceptedCommissions,
                                      Map<UUID, CommissionService.CommissionStats> leaderboard) {
        Properties properties = new Properties();

        JsonArray commissionsArray = new JsonArray();
        for (Commission commission : activeCommissions.values()) {
            commissionsArray.add(serializeCommission(commission));
        }
        if (commissionsArray.size() > 0) {
            properties.setProperty("cm.active", GSON.toJson(commissionsArray));
        }

        StringBuilder publisherStr = new StringBuilder();
        for (Map.Entry<UUID, List<String>> entry : playerCommissions.entrySet()) {
            String value = entry.getKey().toString() + "=" + String.join(",", entry.getValue());
            if (publisherStr.length() > 0) publisherStr.append(";");
            publisherStr.append(value);
        }
        if (publisherStr.length() > 0) {
            properties.setProperty("cm.publishers", publisherStr.toString());
        }

        StringBuilder acceptorStr = new StringBuilder();
        for (Map.Entry<UUID, List<String>> entry : acceptedCommissions.entrySet()) {
            String value = entry.getKey().toString() + "=" + String.join(",", entry.getValue());
            if (acceptorStr.length() > 0) acceptorStr.append(";");
            acceptorStr.append(value);
        }
        if (acceptorStr.length() > 0) {
            properties.setProperty("cm.acceptors", acceptorStr.toString());
        }

        // 保存排行榜数据
        for (Map.Entry<UUID, CommissionService.CommissionStats> entry : leaderboard.entrySet()) {
            String prefix = "cl." + entry.getKey().toString() + ".";
            CommissionService.CommissionStats stats = entry.getValue();
            properties.setProperty(prefix + "name", stats.getPlayerName());
            properties.setProperty(prefix + "completed", String.valueOf(stats.getTotalCompleted()));
            properties.setProperty(prefix + "earned", String.valueOf(stats.getTotalEarned()));
            properties.setProperty(prefix + "streak", String.valueOf(stats.getCurrentStreak()));
            properties.setProperty(prefix + "lastTime", String.valueOf(stats.getLastCompletedTime()));
        }

        stateStorage.saveAsync(properties);
    }

    // ========== 委托排行榜相关 ==========

    /**
     * 保存委托排行榜数据
     */
    public void saveCommissionLeaderboard(Map<UUID, CommissionService.CommissionStats> leaderboard) {
        Properties properties = new Properties();

        for (Map.Entry<UUID, CommissionService.CommissionStats> entry : leaderboard.entrySet()) {
            String prefix = "cl." + entry.getKey().toString() + ".";
            CommissionService.CommissionStats stats = entry.getValue();

            properties.setProperty(prefix + "name", stats.getPlayerName());
            properties.setProperty(prefix + "completed", String.valueOf(stats.getTotalCompleted()));
            properties.setProperty(prefix + "earned", String.valueOf(stats.getTotalEarned()));
            properties.setProperty(prefix + "streak", String.valueOf(stats.getCurrentStreak()));
            properties.setProperty(prefix + "longestStreak", String.valueOf(stats.getLongestStreak()));
            properties.setProperty(prefix + "weekly", String.valueOf(stats.getWeeklyCompleted()));
            properties.setProperty(prefix + "monthly", String.valueOf(stats.getMonthlyCompleted()));
            properties.setProperty(prefix + "lastTime", String.valueOf(stats.getLastCompletedTime()));
        }

        stateStorage.save(properties);
    }

    /**
     * 异步保存委托排行榜数据
     */
    public void saveCommissionLeaderboardAsync(Map<UUID, CommissionService.CommissionStats> leaderboard) {
        Properties properties = new Properties();

        for (Map.Entry<UUID, CommissionService.CommissionStats> entry : leaderboard.entrySet()) {
            String prefix = "cl." + entry.getKey().toString() + ".";
            CommissionService.CommissionStats stats = entry.getValue();

            properties.setProperty(prefix + "name", stats.getPlayerName());
            properties.setProperty(prefix + "completed", String.valueOf(stats.getTotalCompleted()));
            properties.setProperty(prefix + "earned", String.valueOf(stats.getTotalEarned()));
            properties.setProperty(prefix + "streak", String.valueOf(stats.getCurrentStreak()));
            properties.setProperty(prefix + "longestStreak", String.valueOf(stats.getLongestStreak()));
            properties.setProperty(prefix + "weekly", String.valueOf(stats.getWeeklyCompleted()));
            properties.setProperty(prefix + "monthly", String.valueOf(stats.getMonthlyCompleted()));
            properties.setProperty(prefix + "lastTime", String.valueOf(stats.getLastCompletedTime()));
        }

        stateStorage.saveAsync(properties);
    }

    /**
     * 加载委托排行榜数据
     */
    public void loadCommissionLeaderboard(Map<UUID, CommissionService.CommissionStats> leaderboard) {
        Properties properties = stateStorage.load();

        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("cl.")) {
                String fullKey = key.substring(3);
                int dotIndex = fullKey.indexOf('.');
                if (dotIndex > 0) {
                    String playerIdStr = fullKey.substring(0, dotIndex);

                    try {
                        UUID playerId = UUID.fromString(playerIdStr);
                        leaderboard.computeIfAbsent(playerId, k -> new CommissionService.CommissionStats(playerId));
                    } catch (IllegalArgumentException e) {
                        // 无效的UUID格式，跳过
                    }
                }
            }
        }
    }

    /**
     * 加载委托数据
     */
    public void loadCommissions(Map<String, Commission> activeCommissions,
                                 Map<UUID, List<String>> playerCommissions,
                                 Map<UUID, List<String>> acceptedCommissions) {
        Properties properties = stateStorage.load();

        // 加载活跃委托
        String activeJson = properties.getProperty("cm.active");
        if (activeJson != null && !activeJson.isEmpty()) {
            try {
                JsonArray commissionsArray = JsonParser.parseString(activeJson).getAsJsonArray();
                for (int i = 0; i < commissionsArray.size(); i++) {
                    Commission commission = deserializeCommission(commissionsArray.get(i).getAsJsonObject());
                    if (commission != null && commission.getId() != null) {
                        activeCommissions.put(commission.getId(), commission);
                    }
                }
            } catch (Exception e) {
                logger.warning("[QuestPersistence] 加载委托数据失败: " + e.getMessage());
            }
        }

        // 加载发布者映射
        String publishersStr = properties.getProperty("cm.publishers");
        if (publishersStr != null && !publishersStr.isEmpty()) {
            for (String entry : publishersStr.split(";")) {
                String[] parts = entry.split("=");
                if (parts.length >= 2) {
                    try {
                        UUID uuid = UUID.fromString(parts[0]);
                        List<String> commissionIds = Arrays.asList(parts[1].split(","));
                        playerCommissions.put(uuid, new ArrayList<>(commissionIds));
                    } catch (Exception e) {
                        // 忽略
                    }
                }
            }
        }

        // 加载接取者映射
        String acceptorsStr = properties.getProperty("cm.acceptors");
        if (acceptorsStr != null && !acceptorsStr.isEmpty()) {
            for (String entry : acceptorsStr.split(";")) {
                String[] parts = entry.split("=");
                if (parts.length >= 2) {
                    try {
                        UUID uuid = UUID.fromString(parts[0]);
                        List<String> commissionIds = Arrays.asList(parts[1].split(","));
                        acceptedCommissions.put(uuid, new ArrayList<>(commissionIds));
                    } catch (Exception e) {
                        // 忽略
                    }
                }
            }
        }
    }

    /**
     * 序列化任务对象
     */
    private JsonObject serializeQuest(Quest quest) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", quest.getId());
        obj.addProperty("name", quest.getName());
        obj.addProperty("type", quest.getType().name());
        obj.addProperty("difficulty", quest.getDifficulty().name());

        // 序列化目标进度
        JsonArray objectivesArray = new JsonArray();
        for (QuestObjective obj2 : quest.getObjectives()) {
            JsonObject obj2Json = new JsonObject();
            obj2Json.addProperty("id", obj2.getId());
            obj2Json.addProperty("progress", obj2.getCurrentProgress());
            obj2Json.addProperty("type", obj2.getType().name());
            objectivesArray.add(obj2Json);
        }
        obj.add("objectives", objectivesArray);

        return obj;
    }

    /**
     * 反序列化任务对象
     */
    private Quest deserializeQuest(JsonObject obj, Map<String, Quest> questRegistry) {
        String questId = obj.get("id").getAsString();

        // 从注册表获取任务模板
        Quest template = questRegistry.get(questId);
        if (template == null) {
            return null;
        }

        // 创建任务实例
        Quest instance = template.createInstance();

        // 恢复目标进度
        if (obj.has("objectives")) {
            JsonArray objectivesArray = obj.getAsJsonArray("objectives");
            List<QuestObjective> objectives = instance.getObjectives();

            for (int i = 0; i < objectivesArray.size() && i < objectives.size(); i++) {
                JsonObject obj2Json = objectivesArray.get(i).getAsJsonObject();
                int progress = obj2Json.has("progress") ? obj2Json.get("progress").getAsInt() : 0;
                objectives.get(i).setProgress(progress);
            }
        }

        return instance;
    }

    /**
     * 序列化委托对象
     */
    private JsonObject serializeCommission(Commission commission) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", commission.getId());
        obj.addProperty("publisherId", commission.getPublisherId() != null ? commission.getPublisherId().toString() : "");
        obj.addProperty("publisherName", commission.getPublisherName());
        obj.addProperty("title", commission.getTitle());
        obj.addProperty("description", commission.getDescription());
        obj.addProperty("reward", commission.getReward());
        obj.addProperty("minLevel", commission.getMinLevel());
        obj.addProperty("publishTime", commission.getPublishTime());
        obj.addProperty("expireTime", commission.getExpireTime());
        obj.addProperty("acceptorId", commission.getAcceptorId() != null ? commission.getAcceptorId().toString() : "");
        obj.addProperty("acceptTime", commission.getAcceptTime());
        obj.addProperty("completed", commission.isCompleted());
        obj.addProperty("completeTime", commission.getCompleteTime());
        obj.addProperty("type", commission.getType().name());
        obj.addProperty("difficulty", commission.getDifficulty().name());
        obj.addProperty("category", commission.getCategory());

        // 新增字段
        obj.addProperty("targetEntity", commission.getTargetEntity() != null ? commission.getTargetEntity() : "");
        obj.addProperty("targetAmount", commission.getTargetAmount());
        obj.addProperty("currentProgress", commission.getCurrentProgress());
        obj.addProperty("targetLocation", commission.getTargetLocation() != null ? commission.getTargetLocation() : "");
        obj.addProperty("targetItem", commission.getTargetItem() != null ? commission.getTargetItem() : "");
        obj.addProperty("publisherConfirmed", commission.isPublisherConfirmed());
        obj.addProperty("acceptorNotified", commission.isAcceptorNotified());

        JsonArray requirementsArray = new JsonArray();
        for (String req : commission.getRequirements()) {
            requirementsArray.add(req);
        }
        obj.add("requirements", requirementsArray);

        return obj;
    }

    /**
     * 反序列化委托对象
     */
    private Commission deserializeCommission(JsonObject obj) {
        try {
            Commission commission = new Commission(
                obj.get("title").getAsString(),
                obj.get("description").getAsString(),
                obj.get("reward").getAsDouble()
            );

            commission.setId(obj.get("id").getAsString());

            String publisherIdStr = obj.get("publisherId").getAsString();
            if (!publisherIdStr.isEmpty()) {
                commission.setPublisherId(UUID.fromString(publisherIdStr));
            }
            commission.setPublisherName(obj.get("publisherName").getAsString());
            commission.setPublishTime(obj.get("publishTime").getAsLong());
            commission.setExpireTime(obj.get("expireTime").getAsLong());

            String acceptorIdStr = obj.get("acceptorId").getAsString();
            if (!acceptorIdStr.isEmpty()) {
                commission.setAcceptorId(UUID.fromString(acceptorIdStr));
            }
            commission.setAcceptTime(obj.get("acceptTime").getAsLong());
            commission.setCompleted(obj.get("completed").getAsBoolean());
            commission.setCompleteTime(obj.get("completeTime").getAsLong());

            if (obj.has("type")) {
                commission.setType(Commission.CommissionType.valueOf(obj.get("type").getAsString()));
            }
            if (obj.has("difficulty")) {
                commission.setDifficulty(QuestDifficulty.valueOf(obj.get("difficulty").getAsString()));
            }
            if (obj.has("category")) {
                commission.setCategory(obj.get("category").getAsString());
            }

            if (obj.has("requirements")) {
                JsonArray requirementsArray = obj.getAsJsonArray("requirements");
                for (int i = 0; i < requirementsArray.size(); i++) {
                    commission.addRequirement(requirementsArray.get(i).getAsString());
                }
            }

            // 恢复新增字段
            if (obj.has("targetEntity") && !obj.get("targetEntity").getAsString().isEmpty()) {
                commission.setTargetEntity(obj.get("targetEntity").getAsString());
            }
            if (obj.has("targetAmount")) {
                commission.setTargetAmount(obj.get("targetAmount").getAsInt());
            }
            if (obj.has("currentProgress")) {
                commission.setCurrentProgress(obj.get("currentProgress").getAsInt());
            }
            if (obj.has("targetLocation") && !obj.get("targetLocation").getAsString().isEmpty()) {
                commission.setTargetLocation(obj.get("targetLocation").getAsString());
            }
            if (obj.has("targetItem") && !obj.get("targetItem").getAsString().isEmpty()) {
                commission.setTargetItem(obj.get("targetItem").getAsString());
            }
            if (obj.has("publisherConfirmed")) {
                commission.setPublisherConfirmed(obj.get("publisherConfirmed").getAsBoolean());
            }
            if (obj.has("acceptorNotified")) {
                commission.setAcceptorNotified(obj.get("acceptorNotified").getAsBoolean());
            }

            return commission;
        } catch (Exception e) {
            logger.warning("[QuestPersistence] 反序列化委托失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 同步保存所有数据
     */
    public void saveAll() {
        // 这个方法会被 QuestService 等调用时填充实际数据
    }
}
