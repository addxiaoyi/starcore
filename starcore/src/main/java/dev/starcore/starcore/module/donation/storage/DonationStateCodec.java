package dev.starcore.starcore.module.donation.storage;

import dev.starcore.starcore.module.donation.DonationService;
import dev.starcore.starcore.module.donation.DonationService.DonationRecord;
import dev.starcore.starcore.module.donation.DonationService.DonationTier;
import dev.starcore.starcore.module.donation.model.DonationData;
import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 献金状态编解码器
 * 用于将献金数据序列化和反序列化
 */
public final class DonationStateCodec {
    private static final String FIELD_SEPARATOR = "|";
    private static final String SUB_SEPARATOR = ";";
    private static final String KV_SEPARATOR = "=";

    /**
     * 编码献金记录为字符串
     */
    public String encodeRecord(DonationRecord record) {
        StringBuilder sb = new StringBuilder();
        sb.append("id=").append(record.id().toString()).append(SUB_SEPARATOR);
        sb.append("playerId=").append(record.playerId().toString()).append(SUB_SEPARATOR);
        sb.append("playerName=").append(escape(record.playerName())).append(SUB_SEPARATOR);
        sb.append("nationId=").append(record.nationId().toString()).append(SUB_SEPARATOR);
        sb.append("nationName=").append(escape(record.nationName())).append(SUB_SEPARATOR);
        sb.append("amount=").append(record.amount().toPlainString()).append(SUB_SEPARATOR);
        sb.append("message=").append(escape(record.message() != null ? record.message() : "")).append(SUB_SEPARATOR);
        sb.append("tierId=").append(record.tier().id()).append(SUB_SEPARATOR);
        sb.append("tierName=").append(escape(record.tier().name())).append(SUB_SEPARATOR);
        sb.append("tierMin=").append(record.tier().minAmount().toPlainString()).append(SUB_SEPARATOR);
        sb.append("tierMax=").append(record.tier().maxAmount().toPlainString()).append(SUB_SEPARATOR);
        sb.append("tierPriority=").append(record.tier().priority()).append(SUB_SEPARATOR);
        sb.append("donatedAt=").append(record.donatedAt().toEpochMilli());
        return sb.toString();
    }

    /**
     * 从字符串解码献金记录
     */
    public DonationRecord decodeRecord(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }

        try {
            String[] parts = encoded.split(SUB_SEPARATOR, -1);
            Map<String, String> fields = new HashMap<>();
            for (String part : parts) {
                int sepIndex = part.indexOf(KV_SEPARATOR);
                if (sepIndex > 0) {
                    fields.put(part.substring(0, sepIndex), part.substring(sepIndex + 1));
                }
            }

            UUID id = UUID.fromString(fields.getOrDefault("id", ""));
            UUID playerId = UUID.fromString(fields.getOrDefault("playerId", ""));
            String playerName = unescape(fields.getOrDefault("playerName", "Unknown"));
            NationId nationId = NationId.fromString(fields.getOrDefault("nationId", ""));
            String nationName = unescape(fields.getOrDefault("nationName", "Unknown"));
            BigDecimal amount = new BigDecimal(fields.getOrDefault("amount", "0"));
            String message = fields.getOrDefault("message", "");
            message = message.isEmpty() ? null : unescape(message);

            String tierId = fields.getOrDefault("tierId", "bronze");
            String tierName = unescape(fields.getOrDefault("tierName", tierId));
            BigDecimal tierMin = new BigDecimal(fields.getOrDefault("tierMin", "0"));
            BigDecimal tierMax = new BigDecimal(fields.getOrDefault("tierMax", "999999999"));
            int tierPriority = Integer.parseInt(fields.getOrDefault("tierPriority", "1"));
            DonationTier tier = new DonationTier(tierId, tierName, tierMin, tierMax, List.of(), Map.of(), tierPriority);

            long donatedAtMs = Long.parseLong(fields.getOrDefault("donatedAt", String.valueOf(System.currentTimeMillis())));
            Instant donatedAt = Instant.ofEpochMilli(donatedAtMs);

            return new DonationRecord(
                id, playerId, playerName, nationId, nationName,
                amount, message, tier, donatedAt
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 编码玩家献金数据
     */
    public String encodePlayerTierData(DonationData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("total=").append(data.totalAmount().toPlainString()).append(SUB_SEPARATOR);
        sb.append("historical=").append(data.historicalAmount().toPlainString()).append(SUB_SEPARATOR);
        sb.append("count=").append(data.donationCount());
        return sb.toString();
    }

    /**
     * 解码玩家献金数据
     */
    public DonationData decodePlayerTierData(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return DonationData.empty();
        }

        try {
            String[] parts = encoded.split(SUB_SEPARATOR, -1);
            BigDecimal total = BigDecimal.ZERO;
            BigDecimal historical = BigDecimal.ZERO;
            int count = 0;

            for (String part : parts) {
                int sepIndex = part.indexOf(KV_SEPARATOR);
                if (sepIndex > 0) {
                    String key = part.substring(0, sepIndex);
                    String value = part.substring(sepIndex + 1);
                    switch (key) {
                        case "total" -> total = new BigDecimal(value);
                        case "historical" -> historical = new BigDecimal(value);
                        case "count" -> count = Integer.parseInt(value);
                    }
                }
            }

            return new DonationData(total, historical, count);
        } catch (Exception e) {
            return DonationData.empty();
        }
    }

    /**
     * 解码玩家等级数据并填充到缓存中
     */
    public void decodePlayerTier(String key, String value,
                                 ConcurrentHashMap<UUID, ConcurrentHashMap<NationId, DonationData>> playerDonations,
                                 ConcurrentHashMap<UUID, BigDecimal> globalPlayerDonations) {
        if (key.startsWith("global:")) {
            // 全局数据
            String playerIdStr = key.substring(7);
            try {
                UUID playerId = UUID.fromString(playerIdStr);
                BigDecimal amount = new BigDecimal(value);
                globalPlayerDonations.put(playerId, amount);
            } catch (Exception ignored) {
            }
        } else {
            // 国家特定数据
            String[] parts = key.split(":", 2);
            if (parts.length == 2) {
                try {
                    UUID playerId = UUID.fromString(parts[0]);
                    NationId nationId = NationId.fromString(parts[1]);
                    DonationData data = decodePlayerTierData(value);

                    playerDonations.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                        .put(nationId, data);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 转义特殊字符
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace(SUB_SEPARATOR, "\\" + SUB_SEPARATOR)
            .replace(FIELD_SEPARATOR, "\\" + FIELD_SEPARATOR)
            .replace(KV_SEPARATOR, "\\" + KV_SEPARATOR);
    }

    /**
     * 反转义特殊字符
     */
    private String unescape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        boolean escaping = false;
        for (char c : value.toCharArray()) {
            if (escaping) {
                result.append(c);
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}