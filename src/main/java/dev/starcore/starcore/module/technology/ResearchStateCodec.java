package dev.starcore.starcore.module.technology;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * 研究进度编解码器
 * 负责 ResearchProgress 和 activeResearch Map 的序列化/反序列化
 */
final class ResearchStateCodec {
    private static final String KEY_PREFIX = "research.";
    private static final String NATION_COUNT = KEY_PREFIX + "nationCount";
    private static final String NATION_ENTRY = KEY_PREFIX + "nation.";
    private static final String RESEARCH_ENTRY = ".research.";
    private static final String FIELD_KEY = ".key";
    private static final String FIELD_START = ".start";
    private static final String FIELD_ESTIMATED = ".estimated";
    private static final String FIELD_TOTAL = ".total";
    private static final String FIELD_REMAINING = ".remaining";

    private ResearchStateCodec() {
    }

    // ========== ResearchProgress 序列化 ==========

    /**
     * 将 ResearchProgress 转换为 Properties 格式
     * 注意：不包含 BukkitTask，需要在反序列化后重新创建
     */
    static Properties toProperties(ResearchProgress progress) {
        Properties props = new Properties();
        props.setProperty("key", progress.technologyKey());
        props.setProperty("start", String.valueOf(progress.startTime().toEpochMilli()));
        props.setProperty("estimated", String.valueOf(progress.estimatedCompletion().toEpochMilli()));
        props.setProperty("total", String.valueOf(progress.totalTicks()));
        props.setProperty("remaining", String.valueOf(progress.remainingTicks()));
        return props;
    }

    /**
     * 从 Properties 反序列化 ResearchProgress
     * @param nationId 国家ID
     * @param techKey 技术键
     * @param props Properties 对象（已定位到正确的 key 前缀）
     * @return ResearchProgress 或 null（如果数据不完整）
     */
    static ResearchProgress fromProperties(NationId nationId, String techKey, Properties props) {
        try {
            String startStr = props.getProperty("start");
            String estimatedStr = props.getProperty("estimated");
            String totalStr = props.getProperty("total");
            String remainingStr = props.getProperty("remaining");

            if (startStr == null || estimatedStr == null || totalStr == null || remainingStr == null) {
                return null;
            }

            Instant startTime = Instant.ofEpochMilli(Long.parseLong(startStr));
            Instant estimatedCompletion = Instant.ofEpochMilli(Long.parseLong(estimatedStr));
            int totalTicks = Integer.parseInt(totalStr);
            int remainingTicks = Integer.parseInt(remainingStr);

            // BukkitTask 在反序列化时为 null，需要由调用者重新调度
            return new ResearchProgress(
                techKey,
                startTime,
                estimatedCompletion,
                totalTicks,
                remainingTicks,
                null
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ========== activeResearch Map 序列化 ==========

    /**
     * 将 activeResearch Map 序列化为 Properties
     */
    static Properties toProperties(Map<NationId, Map<String, ResearchProgress>> activeResearch) {
        Properties properties = new Properties();

        if (activeResearch == null || activeResearch.isEmpty()) {
            properties.setProperty(NATION_COUNT, "0");
            return properties;
        }

        // 收集所有国家的活跃研究
        int nationIndex = 0;
        for (Map.Entry<NationId, Map<String, ResearchProgress>> nationEntry : activeResearch.entrySet()) {
            NationId nationId = nationEntry.getKey();
            Map<String, ResearchProgress> researchMap = nationEntry.getValue();

            if (nationId == null || researchMap == null || researchMap.isEmpty()) {
                continue;
            }

            String nationPrefix = NATION_ENTRY + nationIndex + ".";
            properties.setProperty(nationPrefix + "nationId", nationId.toString());

            int researchIndex = 0;
            for (Map.Entry<String, ResearchProgress> researchEntry : researchMap.entrySet()) {
                String techKey = researchEntry.getKey();
                ResearchProgress progress = researchEntry.getValue();

                if (techKey == null || progress == null) {
                    continue;
                }

                String researchPrefix = nationPrefix + RESEARCH_ENTRY + researchIndex;
                properties.setProperty(researchPrefix + FIELD_KEY, techKey);
                properties.setProperty(researchPrefix + FIELD_START,
                    String.valueOf(progress.startTime().toEpochMilli()));
                properties.setProperty(researchPrefix + FIELD_ESTIMATED,
                    String.valueOf(progress.estimatedCompletion().toEpochMilli()));
                properties.setProperty(researchPrefix + FIELD_TOTAL,
                    String.valueOf(progress.totalTicks()));
                properties.setProperty(researchPrefix + FIELD_REMAINING,
                    String.valueOf(progress.remainingTicks()));

                researchIndex++;
            }
            properties.setProperty(nationPrefix + "researchCount", String.valueOf(researchIndex));
            nationIndex++;
        }

        properties.setProperty(NATION_COUNT, String.valueOf(nationIndex));
        return properties;
    }

    /**
     * 从 Properties 反序列化为 activeResearch Map
     * @param props Properties 对象
     * @return Map<NationId, Map<String, ResearchProgress>>
     */
    static Map<NationId, Map<String, ResearchProgress>> fromProperties(Properties props) {
        Map<NationId, Map<String, ResearchProgress>> activeResearch = new LinkedHashMap<>();

        if (props == null || props.isEmpty()) {
            return activeResearch;
        }

        int nationCount = parseInt(props.getProperty(NATION_COUNT), 0);

        for (int nationIndex = 0; nationIndex < nationCount; nationIndex++) {
            String nationPrefix = NATION_ENTRY + nationIndex + ".";
            String nationIdStr = props.getProperty(nationPrefix + "nationId");
            NationId nationId = parseNationId(nationIdStr);

            if (nationId == null) {
                continue;
            }

            int researchCount = parseInt(props.getProperty(nationPrefix + "researchCount"), 0);
            Map<String, ResearchProgress> researchMap = new LinkedHashMap<>();

            for (int researchIndex = 0; researchIndex < researchCount; researchIndex++) {
                String researchPrefix = nationPrefix + RESEARCH_ENTRY + researchIndex;
                String techKey = props.getProperty(researchPrefix + FIELD_KEY);

                if (techKey == null) {
                    continue;
                }

                // 提取该研究的 Properties 子集
                Properties researchProps = new Properties();
                researchProps.setProperty("key", techKey);
                researchProps.setProperty("start", props.getProperty(researchPrefix + FIELD_START, ""));
                researchProps.setProperty("estimated", props.getProperty(researchPrefix + FIELD_ESTIMATED, ""));
                researchProps.setProperty("total", props.getProperty(researchPrefix + FIELD_TOTAL, "0"));
                researchProps.setProperty("remaining", props.getProperty(researchPrefix + FIELD_REMAINING, "0"));

                ResearchProgress progress = fromProperties(nationId, normalizeTechKey(techKey), researchProps);
                if (progress != null) {
                    researchMap.put(normalizeTechKey(techKey), progress);
                }
            }

            if (!researchMap.isEmpty()) {
                activeResearch.put(nationId, researchMap);
            }
        }

        return activeResearch;
    }

    // ========== 辅助方法 ==========

    static String normalizeTechKey(String technologyKey) {
        if (technologyKey == null) {
            return "";
        }
        return technologyKey.trim().toLowerCase(Locale.ROOT);
    }

    private static NationId parseNationId(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new NationId(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
