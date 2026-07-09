package dev.starcore.starcore.module.territory.upgrade;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.territory.upgrade.model.UpgradeProgressData;

import java.time.Instant;
import java.util.*;

/**
 * Codec for serializing and deserializing upgrade progress.
 * 升级进度序列化/反序列化编解码器
 */
public class UpgradeStateCodec {

    private static final String SEPARATOR = "|";
    private static final String VALUE_SEPARATOR = "=";
    private static final String LIST_SEPARATOR = ",";
    private static final String NATION_SEPARATOR = ";";

    /**
     * Encode upgrade progress data to properties format.
     */
    public static Map<String, String> toProperties(Map<NationId, UpgradeProgressData> data) {
        Map<String, String> result = new LinkedHashMap<>();

        for (Map.Entry<NationId, UpgradeProgressData> entry : data.entrySet()) {
            String nationKey = entry.getKey().toString();
            UpgradeProgressData progress = entry.getValue();

            // 编码总经验
            result.put(nationKey + ".exp", String.valueOf(progress.totalExp()));
            result.put(nationKey + ".exp_spent", String.valueOf(progress.totalExpSpent()));

            // 编码路径等级
            StringBuilder levelsBuilder = new StringBuilder();
            for (Map.Entry<String, Integer> levelEntry : progress.pathLevels().entrySet()) {
                if (levelsBuilder.length() > 0) {
                    levelsBuilder.append(LIST_SEPARATOR);
                }
                levelsBuilder.append(levelEntry.getKey()).append(VALUE_SEPARATOR).append(levelEntry.getValue());
            }
            result.put(nationKey + ".levels", levelsBuilder.toString());

            // 编码活跃升级
            StringBuilder upgradesBuilder = new StringBuilder();
            for (Map.Entry<String, dev.starcore.starcore.module.territory.upgrade.model.UpgradeProcess> upgradeEntry :
                    progress.activeUpgrades().entrySet()) {
                if (upgradesBuilder.length() > 0) {
                    upgradesBuilder.append(NATION_SEPARATOR);
                }
                dev.starcore.starcore.module.territory.upgrade.model.UpgradeProcess process = upgradeEntry.getValue();
                upgradesBuilder.append(upgradeEntry.getKey())
                    .append(VALUE_SEPARATOR).append(process.targetLevel())
                    .append(VALUE_SEPARATOR).append(process.currentExp())
                    .append(VALUE_SEPARATOR).append(process.targetExp())
                    .append(VALUE_SEPARATOR).append(process.startedAt() != null ? process.startedAt().getEpochSecond() : 0)
                    .append(VALUE_SEPARATOR).append(process.isCompleted());
            }
            result.put(nationKey + ".upgrades", upgradesBuilder.toString());
        }

        return result;
    }

    /**
     * Decode properties to upgrade progress data.
     */
    public static Map<NationId, UpgradeProgressData> fromProperties(Map<String, String> properties, Set<String> validPaths) {
        Map<NationId, UpgradeProgressData> result = new LinkedHashMap<>();

        // 按国家分组键
        Set<String> nationKeys = new HashSet<>();
        for (String key : properties.keySet()) {
            int dotIndex = key.lastIndexOf('.');
            if (dotIndex > 0) {
                nationKeys.add(key.substring(0, dotIndex));
            }
        }

        for (String nationKey : nationKeys) {
            try {
                NationId nationId = NationId.of(UUID.fromString(nationKey));
                UpgradeProgressData data = new UpgradeProgressData(nationId);

                // 解码总经验
                String expStr = properties.get(nationKey + ".exp");
                if (expStr != null) {
                    data.setTotalExp(Integer.parseInt(expStr));
                }

                String expSpentStr = properties.get(nationKey + ".exp_spent");
                if (expSpentStr != null) {
                    data.addExp(0); // 临时设置，后面会正确计算
                }

                // 解码路径等级
                String levelsStr = properties.get(nationKey + ".levels");
                if (levelsStr != null && !levelsStr.isEmpty()) {
                    for (String levelEntry : levelsStr.split(LIST_SEPARATOR)) {
                        String[] parts = levelEntry.split(VALUE_SEPARATOR, 2);
                        if (parts.length == 2) {
                            String pathId = parts[0];
                            int level = Integer.parseInt(parts[1]);
                            if (validPaths.contains(pathId)) {
                                data.setPathLevel(pathId, level);
                            }
                        }
                    }
                }

                // 解码活跃升级
                String upgradesStr = properties.get(nationKey + ".upgrades");
                if (upgradesStr != null && !upgradesStr.isEmpty()) {
                    for (String upgradeEntry : upgradesStr.split(NATION_SEPARATOR)) {
                        String[] parts = upgradeEntry.split(VALUE_SEPARATOR);
                        if (parts.length >= 6) {
                            String pathId = parts[0];
                            int targetLevel = Integer.parseInt(parts[1]);
                            int currentExp = Integer.parseInt(parts[2]);
                            int targetExp = Integer.parseInt(parts[3]);
                            long startedAtEpoch = Long.parseLong(parts[4]);
                            boolean isCompleted = Boolean.parseBoolean(parts[5]);

                            Instant startedAt = startedAtEpoch > 0 ? Instant.ofEpochSecond(startedAtEpoch) : null;

                            if (validPaths.contains(pathId)) {
                                dev.starcore.starcore.module.territory.upgrade.model.UpgradeProcess process =
                                    new dev.starcore.starcore.module.territory.upgrade.model.UpgradeProcess(
                                        pathId, targetLevel, currentExp, targetExp, startedAt, null, isCompleted
                                    );
                                data.addActiveUpgrade(pathId, process);
                            }
                        }
                    }
                }

                result.put(nationId, data);
            } catch (Exception e) {
                // 跳过无效的数据
            }
        }

        return result;
    }

    /**
     * Encode a single nation's progress.
     */
    public static Map<String, String> encodeNation(UpgradeProgressData data) {
        Map<NationId, UpgradeProgressData> allData = new HashMap<>();
        allData.put(data.nationId(), data);
        return toProperties(allData);
    }

    /**
     * Decode a single nation's progress.
     */
    public static UpgradeProgressData decodeNation(Map<String, String> properties, NationId nationId, Set<String> validPaths) {
        Map<NationId, UpgradeProgressData> allData = fromProperties(properties, validPaths);
        return allData.get(nationId);
    }
}
