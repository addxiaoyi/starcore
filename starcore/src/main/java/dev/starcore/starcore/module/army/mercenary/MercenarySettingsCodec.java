package dev.starcore.starcore.module.army.mercenary;

import java.util.Set;
import java.util.UUID;

/**
 * 雇佣兵设置编解码器
 * 用于持久化存储雇佣兵设置
 */
public final class MercenarySettingsCodec {

    private MercenarySettingsCodec() {
        // 工具类
    }

    /**
     * 编码设置对象为字符串
     */
    public static String encode(MercenarySettings settings) {
        StringBuilder sb = new StringBuilder();
        sb.append("available=").append(settings.isAvailable());
        sb.append("|minDays=").append(settings.minContractDays());
        sb.append("|maxDays=").append(settings.maxContractDays());
        sb.append("|minSalary=").append(settings.minSalaryPerDay());

        if (!settings.getPreferredTypes().isEmpty()) {
            sb.append("|types=");
            settings.getPreferredTypes().forEach(type -> sb.append(type.key()).append(","));
            sb.deleteCharAt(sb.length() - 1); // 删除最后一个逗号
        }

        return sb.toString();
    }

    /**
     * 从字符串解码设置对象
     */
    public static MercenarySettings decode(UUID playerId, String data) {
        boolean available = false;
        int minDays = 1;
        int maxDays = 30;
        int minSalary = 100;
        Set<MercenaryType> preferredTypes = new java.util.HashSet<>();

        String[] parts = data.split("\\|");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;

            String key = kv[0].trim();
            String value = kv[1].trim();

            switch (key) {
                case "available" -> available = Boolean.parseBoolean(value);
                case "minDays" -> minDays = Integer.parseInt(value);
                case "maxDays" -> maxDays = Integer.parseInt(value);
                case "minSalary" -> minSalary = Integer.parseInt(value);
                case "types" -> {
                    String[] typeKeys = value.split(",");
                    for (String typeKey : typeKeys) {
                        try {
                            preferredTypes.add(MercenaryType.fromKey(typeKey.trim()));
                        } catch (IllegalArgumentException ignored) {
                            // 忽略未知类型
                        }
                    }
                }
            }
        }

        return new MercenarySettings(playerId, available, minDays, maxDays, minSalary, preferredTypes);
    }
}