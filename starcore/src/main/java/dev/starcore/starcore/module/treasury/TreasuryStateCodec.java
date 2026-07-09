package dev.starcore.starcore.module.treasury;

import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

final class TreasuryStateCodec {
    private static final int SCALE = 2;

    private TreasuryStateCodec() {
    }

    static Properties toProperties(Map<NationId, BigDecimal> balances) {
        Properties properties = new Properties();
        List<Map.Entry<NationId, BigDecimal>> snapshot = balances.entrySet().stream()
            .filter(entry -> entry.getValue() != null && entry.getValue().signum() != 0)
            .sorted((left, right) -> left.getKey().toString().compareTo(right.getKey().toString()))
            .toList();
        properties.setProperty("count", String.valueOf(snapshot.size()));
        for (int index = 0; index < snapshot.size(); index++) {
            Map.Entry<NationId, BigDecimal> entry = snapshot.get(index);
            String prefix = "treasury." + index + '.';
            properties.setProperty(prefix + "nationId", entry.getKey().toString());
            properties.setProperty(prefix + "balance", normalize(entry.getValue()).toPlainString());
        }
        return properties;
    }

    static Map<NationId, BigDecimal> fromProperties(Properties properties) {
        int count = parseInt(properties.getProperty("count"), 0);
        Map<NationId, BigDecimal> balances = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String prefix = "treasury." + index + '.';
            NationId nationId = parseNationId(properties.getProperty(prefix + "nationId"));
            BigDecimal balance = parseAmount(properties.getProperty(prefix + "balance"));
            if (nationId != null && balance.signum() != 0) {
                balances.put(nationId, balance);
            }
        }
        return Map.copyOf(balances);
    }

    private static BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.DOWN);
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

    private static BigDecimal parseAmount(String value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.DOWN);
        }
        try {
            return normalize(new BigDecimal(value));
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.DOWN);
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
