package dev.starcore.starcore.module.war.reparations;

import dev.starcore.starcore.war.WarReparation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 赔款状态编解码器
 * 用于将 WarReparation 序列化/反序列化为 Properties 格式
 */
public final class ReparationsStateCodec {

    private ReparationsStateCodec() {
        // 工具类
    }

    /**
     * 编码赔款记录为字符串
     * 格式: id|treatyId|payerId|receiverId|totalAmount|paidAmount|totalInstallments|paidInstallments|startDate|lastPaymentDate|status
     */
    public static String encode(WarReparation reparation) {
        StringBuilder sb = new StringBuilder();
        sb.append("id=").append(reparation.id()).append("|");
        sb.append("treatyId=").append(reparation.treatyId()).append("|");
        sb.append("payerId=").append(reparation.payerId()).append("|");
        sb.append("receiverId=").append(reparation.receiverId()).append("|");
        sb.append("totalAmount=").append(reparation.totalAmount().toPlainString()).append("|");
        sb.append("paidAmount=").append(reparation.paidAmount().toPlainString()).append("|");
        sb.append("totalInstallments=").append(reparation.totalInstallments()).append("|");
        sb.append("paidInstallments=").append(reparation.paidInstallments()).append("|");
        sb.append("startDate=").append(reparation.startDate().toEpochMilli()).append("|");
        sb.append("lastPaymentDate=").append(
            reparation.lastPaymentDate() != null ? reparation.lastPaymentDate().toEpochMilli() : "0").append("|");
        sb.append("status=").append(reparation.status().name());
        return sb.toString();
    }

    /**
     * 解码字符串为赔款记录
     */
    public static WarReparation decode(String data) {
        String[] parts = data.split("\\|");
        if (parts.length < 11) {
            throw new IllegalArgumentException("Invalid reparation data: " + data);
        }

        UUID id = UUID.fromString(extractValue(parts[0], "id"));
        UUID treatyId = UUID.fromString(extractValue(parts[1], "treatyId"));
        UUID payerId = UUID.fromString(extractValue(parts[2], "payerId"));
        UUID receiverId = UUID.fromString(extractValue(parts[3], "receiverId"));
        BigDecimal totalAmount = new BigDecimal(extractValue(parts[4], "totalAmount"));
        BigDecimal paidAmount = new BigDecimal(extractValue(parts[5], "paidAmount"));
        int totalInstallments = Integer.parseInt(extractValue(parts[6], "totalInstallments"));
        int paidInstallments = Integer.parseInt(extractValue(parts[7], "paidInstallments"));
        Instant startDate = Instant.ofEpochMilli(Long.parseLong(extractValue(parts[8], "startDate")));
        String lastPaymentDateStr = extractValue(parts[9], "lastPaymentDate");
        Instant lastPaymentDate = "0".equals(lastPaymentDateStr) ? null : Instant.ofEpochMilli(Long.parseLong(lastPaymentDateStr));
        WarReparation.ReparationStatus status = WarReparation.ReparationStatus.valueOf(extractValue(parts[10], "status"));

        // 使用内部构造函数恢复完整状态
        return new WarReparation(
            id, treatyId, payerId, receiverId, totalAmount, totalInstallments, startDate,
            paidAmount, paidInstallments, lastPaymentDate, status
        );
    }

    private static String extractValue(String part, String fieldName) {
        if (!part.startsWith(fieldName + "=")) {
            throw new IllegalArgumentException("Expected field " + fieldName + " but got: " + part);
        }
        return part.substring(fieldName.length() + 1);
    }
}