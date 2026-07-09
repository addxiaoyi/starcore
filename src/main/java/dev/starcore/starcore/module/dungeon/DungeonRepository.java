package dev.starcore.starcore.module.dungeon;
import java.util.Optional;

import dev.starcore.starcore.core.persistence.PersistenceService;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * 副本数据仓库
 * 负责副本数据的持久化
 */
public class DungeonRepository {
    private final JavaPlugin plugin;
    private final PersistenceService persistenceService;
    private final Logger logger;

    // 数据存储
    private final Map<UUID, List<DungeonAuditEntry>> auditLogs = new ConcurrentHashMap<>();
    private final Map<UUID, DungeonCompletionRecord> completionRecords = new ConcurrentHashMap<>();
    private final Map<UUID, List<DungeonCompletionRecord>> playerRecords = new ConcurrentHashMap<>();
    private final Map<UUID, List<DungeonCompletionRecord>> nationRecords = new ConcurrentHashMap<>();

    public DungeonRepository(JavaPlugin plugin, PersistenceService persistenceService) {
        this.plugin = plugin;
        this.persistenceService = persistenceService;
        this.logger = plugin.getLogger();
    }

    /**
     * 加载数据
     */
    public void load() {
        loadCompletionRecords();
        loadAuditLogs();
        logger.info("副本数据仓库已加载");
    }

    /**
     * 保存数据
     */
    public void save() {
        saveCompletionRecords();
        saveAuditLogs();
    }

    /**
     * 异步保存数据
     */
    public void saveAsync() {
        // 直接调用save方法，持久化服务会处理异步
        save();
    }

    /**
     * 记录审计日志
     */
    public void logAction(UUID instanceId, DungeonAuditEntry entry) {
        auditLogs.computeIfAbsent(instanceId, k -> new ArrayList<>()).add(entry);

        // 限制内存中的日志数量
        List<DungeonAuditEntry> logs = auditLogs.get(instanceId);
        if (logs.size() > 1000) {
            logs.subList(0, 500).clear();
        }
    }

    /**
     * 获取审计日志
     */
    public List<DungeonAuditEntry> getAuditLogs(UUID instanceId) {
        return List.copyOf(auditLogs.getOrDefault(instanceId, List.of()));
    }

    /**
     * 添加完成记录
     */
    public void addCompletionRecord(DungeonCompletionRecord record) {
        completionRecords.put(record.recordId(), record);

        // 按玩家分组
        playerRecords.computeIfAbsent(record.playerId(), k -> new ArrayList<>()).add(record);

        // 按国家分组
        if (record.nationId() != null) {
            nationRecords.computeIfAbsent(record.nationId(), k -> new ArrayList<>()).add(record);
        }
    }

    /**
     * 获取玩家的完成记录
     */
    public List<DungeonCompletionRecord> getPlayerHistory(UUID playerId) {
        List<DungeonCompletionRecord> records = playerRecords.get(playerId);
        if (records == null) return List.of();

        // 按时间倒序
        return records.stream()
            .sorted((a, b) -> Long.compare(b.completedAt().toEpochMilli(), a.completedAt().toEpochMilli()))
            .toList();
    }

    /**
     * 获取国家的完成记录
     */
    public List<DungeonCompletionRecord> getNationRecords(UUID nationId) {
        List<DungeonCompletionRecord> records = nationRecords.get(nationId);
        if (records == null) return List.of();

        return records.stream()
            .sorted((a, b) -> Long.compare(b.completedAt().toEpochMilli(), a.completedAt().toEpochMilli()))
            .toList();
    }

    /**
     * 获取玩家进度
     */
    public Optional<DungeonProgress> getProgress(UUID playerId, UUID instanceId) {
        // 从内存中查找或创建新进度
        List<DungeonCompletionRecord> history = getPlayerHistory(playerId);
        if (!history.isEmpty()) {
            DungeonCompletionRecord last = history.get(0);
            DungeonProgress progress = new DungeonProgress(playerId, instanceId, last.dungeonId());
            progress.setResult(last.result());
            return Optional.of(progress);
        }
        return Optional.empty();
    }

    /**
     * 获取玩家的首次通关记录
     */
    public boolean hasFirstClear(UUID playerId, String dungeonId) {
        return playerRecords.getOrDefault(playerId, List.of()).stream()
            .anyMatch(r -> r.dungeonId().equals(dungeonId) && r.isSuccess() && r.firstClear());
    }

    /**
     * 获取玩家的通关次数
     */
    public long getCompletionCount(UUID playerId, String dungeonId) {
        return playerRecords.getOrDefault(playerId, List.of()).stream()
            .filter(r -> r.dungeonId().equals(dungeonId) && r.isSuccess())
            .count();
    }

    /**
     * 获取副本统计
     */
    public DungeonStatistics getStatistics() {
        DungeonStatistics stats = new DungeonStatistics();

        for (DungeonCompletionRecord record : completionRecords.values()) {
            if (record.isSuccess()) {
                stats.recordCompletion();
                stats.addPlayTime(record.durationSeconds());
                if (record.rewards() != null) {
                    stats.addGoldEarned(record.rewards().gold());
                }
            } else {
                stats.recordFailure();
            }
            if (!record.isSuccess()) {
                stats.recordDeath();
            }
        }

        return stats;
    }

    // ==================== 文件持久化 ====================

    private void loadCompletionRecords() {
        try {
            Path path = persistenceService.namespacePath("dungeon");
            Path file = path.resolve("completions.json");

            if (Files.exists(file)) {
                String content = Files.readString(file);
                // 简单的JSON解析（实际应该使用Jackson或Gson）
                parseCompletionRecords(content);
            }
        } catch (Exception e) {
            logger.warning("加载完成记录失败: " + e.getMessage());
        }
    }

    private void saveCompletionRecords() {
        try {
            Path path = persistenceService.namespacePath("dungeon");
            Path file = path.resolve("completions.json");

            String content = serializeCompletionRecords();
            Files.writeString(file, content);
        } catch (Exception e) {
            logger.warning("保存完成记录失败: " + e.getMessage());
        }
    }

    private void loadAuditLogs() {
        try {
            Path path = persistenceService.namespacePath("dungeon");
            Path dir = path.resolve("audit");

            if (Files.exists(dir)) {
                try (Stream<Path> files = Files.list(dir)) {
                    files.filter(f -> f.toString().endsWith(".log"))
                        .forEach(this::loadAuditLogFile);
                }
            }
        } catch (Exception e) {
            logger.warning("加载审计日志失败: " + e.getMessage());
        }
    }

    private void loadAuditLogFile(Path file) {
        try {
            String content = Files.readString(file);
            // 解析日志
            String[] lines = content.split("\n");
            // 使用文件名的UUID作为instanceId（文件名就是playerId或logKey）
            UUID logKey = null;
            try {
                String fileName = file.getFileName().toString().replace(".log", "");
                logKey = UUID.fromString(fileName);
            } catch (Exception e) {
                logKey = UUID.randomUUID();
            }
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                // 解析并存储
                parseAuditEntry(line, logKey);
            }
        } catch (Exception e) {
            logger.warning("加载审计日志文件失败: " + file.getFileName() + " - " + e.getMessage());
        }
    }

    private void saveAuditLogs() {
        try {
            Path path = persistenceService.namespacePath("dungeon");
            Path dir = path.resolve("audit");
            Files.createDirectories(dir);

            for (Map.Entry<UUID, List<DungeonAuditEntry>> entry : auditLogs.entrySet()) {
                Path file = dir.resolve(entry.getKey().toString() + ".log");
                StringBuilder content = new StringBuilder();

                for (DungeonAuditEntry auditEntry : entry.getValue()) {
                    content.append(serializeAuditEntry(auditEntry)).append("\n");
                }

                Files.writeString(file, content.toString());
            }
        } catch (Exception e) {
            logger.warning("保存审计日志失败: " + e.getMessage());
        }
    }

    // 序列化完成记录为JSON
    private String serializeCompletionRecords() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (DungeonCompletionRecord record : completionRecords.values()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\n  {");
            sb.append("\"id\":\"").append(record.recordId()).append("\",");
            sb.append("\"dungeonId\":\"").append(escapeJson(record.dungeonId())).append("\",");
            sb.append("\"playerId\":\"").append(record.playerId()).append("\",");
            sb.append("\"nationId\":").append(record.nationId() != null ? "\"" + record.nationId() + "\"" : "null").append(",");
            sb.append("\"completedAt\":").append(record.completedAt().toEpochMilli()).append(",");
            sb.append("\"success\":").append(record.isSuccess()).append(",");
            sb.append("\"firstClear\":").append(record.firstClear()).append(",");
            sb.append("\"durationSeconds\":").append(record.durationSeconds()).append(",");
            sb.append("\"result\":\"").append(escapeJson(record.result() != null ? record.result().name() : "")).append("\",");
            sb.append("\"rewards\":");
            if (record.rewards() != null) {
                sb.append("{");
                sb.append("\"gold\":").append(record.rewards().gold()).append(",");
                sb.append("\"experience\":").append(record.rewards().experience()).append(",");
                sb.append("\"items\":\"").append(escapeJson(record.rewards().items() != null ? record.rewards().items().stream()
                    .map(item -> item.material() + "x" + item.amount())
                    .reduce((a, b) -> a + "," + b).orElse("") : "")).append("\"");
                sb.append("}");
            } else {
                sb.append("null");
            }
            sb.append("}");
        }
        sb.append("\n]");
        return sb.toString();
    }

    // 转义JSON字符串中的特殊字符
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private void parseCompletionRecords(String content) {
        if (content == null || content.isBlank()) return;

        try {
            // 解析JSON数组
            content = content.trim();
            if (!content.startsWith("[") || !content.endsWith("]")) return;

            String arrayContent = content.substring(1, content.length() - 1).trim();
            if (arrayContent.isEmpty()) return;

            // 分割数组元素（简单的分割，不处理嵌套对象）
            List<String> items = splitJsonArray(arrayContent);

            for (String item : items) {
                DungeonCompletionRecord record = parseCompletionRecord(item.trim());
                if (record != null) {
                    completionRecords.put(record.recordId(), record);
                    // 重建索引
                    playerRecords.computeIfAbsent(record.playerId(), k -> new ArrayList<>()).add(record);
                    if (record.nationId() != null) {
                        nationRecords.computeIfAbsent(record.nationId(), k -> new ArrayList<>()).add(record);
                    }
                }
            }
            logger.info("已加载 " + completionRecords.size() + " 条完成记录");
        } catch (Exception e) {
            logger.warning("解析完成记录失败: " + e.getMessage());
        }
    }

    // 简单的JSON数组分割
    private List<String> splitJsonArray(String content) {
        List<String> items = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        int start = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"' && (i == 0 || content.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0 && content.charAt(i + 1) == ',') {
                        items.add(content.substring(start, i + 1));
                        start = i + 2;
                    }
                }
            }
        }
        // 添加最后一个元素
        String last = content.substring(start).trim();
        if (!last.isEmpty() && !last.equals(",")) {
            items.add(last.replaceFirst(",$", ""));
        }
        return items;
    }

    // 解析单个完成记录
    private DungeonCompletionRecord parseCompletionRecord(String json) {
        try {
            UUID recordId = null;
            String dungeonId = "";
            UUID playerId = null;
            UUID nationId = null;
            Instant completedAt = Instant.now();
            boolean success = true;
            boolean firstClear = false;
            long durationSeconds = 0;
            DungeonCompletionResult result = DungeonCompletionResult.SUCCESS;
            DungeonRewards rewards = null;

            // 提取字段值
            recordId = extractUuid(json, "id", recordId);
            dungeonId = extractString(json, "dungeonId", dungeonId);
            playerId = extractUuid(json, "playerId", playerId);
            nationId = extractUuidOrNull(json, "nationId");
            completedAt = extractInstant(json, "completedAt", completedAt);
            success = extractBoolean(json, "success", success);
            firstClear = extractBoolean(json, "firstClear", firstClear);
            durationSeconds = extractLong(json, "durationSeconds", durationSeconds);
            String resultStr = extractString(json, "result", "");
            if (!resultStr.isEmpty()) {
                try {
                    result = DungeonCompletionResult.valueOf(resultStr);
                } catch (IllegalArgumentException ignored) {
                    // 静默跳过，保持数据兼容
                }
            }
            rewards = extractRewards(json);

            if (recordId == null || playerId == null || dungeonId.isEmpty()) {
                return null;
            }

            // 尝试获取难度，如果没有则使用默认值
            String difficultyStr = extractString(json, "difficulty", "NORMAL");
            DungeonDifficulty difficulty;
            try {
                difficulty = DungeonDifficulty.valueOf(difficultyStr);
            } catch (IllegalArgumentException e) {
                difficulty = DungeonDifficulty.NORMAL;
            }

            return new DungeonCompletionRecord(
                recordId, dungeonId, difficulty, playerId, nationId,
                completedAt, durationSeconds, 0, 0, result, rewards, firstClear
            );
        } catch (Exception e) {
            logger.warning("解析记录失败: " + e.getMessage());
            return null;
        }
    }

    private String extractString(String json, String key, String defaultValue) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) {
            pattern = "\"" + key + "\":";
            start = json.indexOf(pattern);
            if (start == -1) return defaultValue;
            start += pattern.length();
            if (json.charAt(start) == '"') {
                start++;
                int end = json.indexOf("\"", start);
                if (end == -1) return defaultValue;
                return unescapeJson(json.substring(start, end));
            }
            return defaultValue;
        }
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return defaultValue;
        return unescapeJson(json.substring(start, end));
    }

    private UUID extractUuid(String json, String key, UUID defaultValue) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return defaultValue;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return defaultValue;
        try {
            return UUID.fromString(json.substring(start, end));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private UUID extractUuidOrNull(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();

        // 跳过空格
        while (start < json.length() && json.charAt(start) == ' ') start++;

        // 检查是否为null
        if (json.substring(start).startsWith("null") || json.substring(start).startsWith("Null")) {
            return null;
        }
        if (json.charAt(start) == '"') {
            start++;
            int end = json.indexOf("\"", start);
            if (end == -1) return null;
            try {
                return UUID.fromString(json.substring(start, end));
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private long extractLong(String json, String key, long defaultValue) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return defaultValue;
        start += pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Long.parseLong(json.substring(start, end).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private boolean extractBoolean(String json, String key, boolean defaultValue) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return defaultValue;
        start += pattern.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && Character.isLetter(json.charAt(end))) end++;
        String value = json.substring(start, end).trim();
        return value.equalsIgnoreCase("true");
    }

    private Instant extractInstant(String json, String key, Instant defaultValue) {
        long epochMilli = extractLong(json, key, -1);
        if (epochMilli > 0) {
            return Instant.ofEpochMilli(epochMilli);
        }
        return defaultValue;
    }

    private DungeonRewards extractRewards(String json) {
        int rewardsStart = json.indexOf("\"rewards\":");
        if (rewardsStart == -1) return null;
        rewardsStart += 10;
        while (rewardsStart < json.length() && json.charAt(rewardsStart) == ' ') rewardsStart++;

        if (json.substring(rewardsStart).startsWith("null") || json.substring(rewardsStart).startsWith("Null")) {
            return null;
        }

        if (json.charAt(rewardsStart) != '{') return null;

        int depth = 0;
        int start = rewardsStart;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    String rewardsJson = json.substring(start, i + 1);
                    int gold = (int) extractLong(rewardsJson, "gold", 0);
                    int experience = (int) extractLong(rewardsJson, "experience", 0);
                    // 创建空的奖励列表和映射（简化处理）
                    return new DungeonRewards(experience, gold, List.of(), Map.of(), List.of(), 1.0);
                }
            }
        }
        return null;
    }

    private String unescapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\n", "\n")
                  .replace("\\r", "\r")
                  .replace("\\t", "\t")
                  .replace("\\\"", "\"")
                  .replace("\\\\", "\\");
    }

    private String serializeAuditEntry(DungeonAuditEntry entry) {
        return String.format("%s|%s|%s|%s|%s|%d|%s",
            entry.entryId(),
            entry.playerId(),
            entry.playerName(),
            entry.action(),
            entry.details(),
            entry.timestamp().toEpochMilli(),
            entry.worldLocation() != null ? entry.worldLocation() : ""
        );
    }

    private void parseAuditEntry(String line, UUID instanceId) {
        if (line == null || line.isBlank()) return;

        try {
            // 格式: entryId|playerId|playerName|action|details|timestamp|worldLocation
            String[] parts = line.split("\\|", -1);
            if (parts.length < 6) {
                logger.warning("审计日志格式错误: " + line);
                return;
            }

            UUID entryId;
            try {
                entryId = UUID.fromString(parts[0].trim());
            } catch (Exception e) {
                entryId = UUID.randomUUID();
            }

            UUID playerId;
            try {
                playerId = UUID.fromString(parts[1].trim());
            } catch (Exception e) {
                playerId = UUID.randomUUID();
            }

            String playerName = parts[2].trim();
            String actionStr = parts[3].trim();
            String details = parts[4].trim();

            Instant timestamp;
            try {
                timestamp = Instant.ofEpochMilli(Long.parseLong(parts[5].trim()));
            } catch (Exception e) {
                timestamp = Instant.now();
            }

            String worldLocation = parts.length > 6 ? parts[6].trim() : "";

            // 尝试解析 action
            DungeonAuditAction action;
            try {
                action = DungeonAuditAction.valueOf(actionStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                action = DungeonAuditAction.UNKNOWN;
            }

            DungeonAuditEntry entry = new DungeonAuditEntry(
                entryId, instanceId, playerId, playerName, action, details, timestamp, worldLocation, Map.of()
            );

            // 根据玩家ID或副本ID存储
            UUID logKey = playerId; // 使用玩家ID作为日志键
            auditLogs.computeIfAbsent(logKey, k -> new ArrayList<>()).add(entry);
        } catch (Exception e) {
            logger.warning("解析审计日志失败: " + e.getMessage());
        }
    }

    /**
     * 清理过期数据
     */
    public void cleanupOldData(int retentionDays) {
        long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60 * 60 * 1000);

        // 清理过期的完成记录
        completionRecords.entrySet().removeIf(entry -> {
            DungeonCompletionRecord record = entry.getValue();
            return record.completedAt().toEpochMilli() < cutoffTime;
        });

        // 清理过期的审计日志
        auditLogs.entrySet().removeIf(entry -> {
            List<DungeonAuditEntry> logs = entry.getValue();
            logs.removeIf(log -> log.timestamp().toEpochMilli() < cutoffTime);
            return logs.isEmpty();
        });
    }
}
