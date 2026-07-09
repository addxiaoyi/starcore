package dev.starcore.starcore.zone;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.*;
import java.lang.NumberFormatException;
import java.util.logging.Logger;

/**
 * 经济区状态编解码器
 */
public final class ZoneStateCodec {

    private static final Logger LOGGER = Logger.getLogger(ZoneStateCodec.class.getName());
    private static final String KEY_ZONE_IDS = "zone.ids";
    private static final String KEY_ZONE_PREFIX = "zone.";
    private static final String SEPARATOR = ";";
    private static final String FIELD_SEPARATOR = ",";

    private ZoneStateCodec() {}

    /**
     * 将经济区Map转换为Properties
     */
    public static Properties toProperties(Map<UUID, Zone> zones) {
        Properties props = new Properties();

        // 保存所有经济区ID
        List<String> ids = new ArrayList<>();
        for (UUID id : zones.keySet()) {
            ids.add(id.toString());
        }
        props.setProperty(KEY_ZONE_IDS, String.join(SEPARATOR, ids));

        // 保存每个经济区数据
        for (Map.Entry<UUID, Zone> entry : zones.entrySet()) {
            String prefix = KEY_ZONE_PREFIX + entry.getKey().toString() + ".";
            Zone zone = entry.getValue();

            props.setProperty(prefix + "nationId", zone.nationId().toString());
            props.setProperty(prefix + "name", zone.getName());
            props.setProperty(prefix + "type", zone.getType().name());
            props.setProperty(prefix + "taxBonus", String.valueOf(zone.getTaxBonus()));
            props.setProperty(prefix + "productionBonus", String.valueOf(zone.getProductionBonus()));
            props.setProperty(prefix + "level", String.valueOf(zone.getLevel()));
            props.setProperty(prefix + "createdAt", String.valueOf(zone.getCreatedAt()));
            props.setProperty(prefix + "active", String.valueOf(zone.isActive()));

            // 保存特效
            String effectsStr = zone.getEffects().stream()
                .map(ZoneEffect::name)
                .reduce((a, b) -> a + FIELD_SEPARATOR + b)
                .orElse("");
            props.setProperty(prefix + "effects", effectsStr);
        }

        return props;
    }

    /**
     * 从Properties加载经济区Map
     */
    @SuppressWarnings("unchecked")
    public static Map<UUID, Zone> fromProperties(Properties props) {
        Map<UUID, Zone> zones = new LinkedHashMap<>();

        if (props == null || props.isEmpty()) {
            return zones;
        }

        // 加载所有经济区ID
        String idsStr = props.getProperty(KEY_ZONE_IDS, "");
        if (idsStr.isEmpty()) {
            return zones;
        }

        for (String idStr : idsStr.split(SEPARATOR)) {
            try {
                UUID id = UUID.fromString(idStr.trim());
                String prefix = KEY_ZONE_PREFIX + idStr.trim() + ".";

                // 加载基本数据
                NationId nationId = NationId.of(UUID.fromString(props.getProperty(prefix + "nationId")));
                String name = props.getProperty(prefix + "name", "Unnamed Zone");
                ZoneType type = ZoneType.valueOf(props.getProperty(prefix + "type", "COMMERCIAL"));
                double taxBonus = Double.parseDouble(props.getProperty(prefix + "taxBonus", "0"));
                double productionBonus = Double.parseDouble(props.getProperty(prefix + "productionBonus", "0"));
                int level = Integer.parseInt(props.getProperty(prefix + "level", "1"));
                long createdAt = Long.parseLong(props.getProperty(prefix + "createdAt", "0"));
                boolean active = Boolean.parseBoolean(props.getProperty(prefix + "active", "true"));

                // 加载特效
                List<ZoneEffect> effects = new ArrayList<>();
                String effectsStr = props.getProperty(prefix + "effects", "");
                if (!effectsStr.isEmpty()) {
                    for (String effectName : effectsStr.split(FIELD_SEPARATOR)) {
                        try {
                            effects.add(ZoneEffect.valueOf(effectName.trim()));
                        } catch (IllegalArgumentException e) {
                            // 跳过无效的特效名称
                        }
                    }
                }

                // 构建Zone对象
                Zone zone = new Zone(id, nationId, name, type);
                zone.setTaxBonus(taxBonus);
                zone.setProductionBonus(productionBonus);
                zone.setLevel(level);
                zone.setActive(active);
                zone.setEffects(effects);

                zones.put(id, zone);
            } catch (IllegalArgumentException e) {
                LOGGER.fine("跳过无效的经济区数据: " + idStr + " - " + e.getMessage());
            }
        }

        return zones;
    }
}
