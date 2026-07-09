package dev.starcore.starcore.webmap.ssl;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * SSL 证书信息
 */
public record CertificateInfo(
    String domain,
    String subject,
    String issueDate,
    String expiryDate,
    Path certFile,
    Path keyFile
) {
    /**
     * 检查证书是否即将过期（30天内）
     */
    public boolean isExpiringSoon() {
        try {
            LocalDateTime expiry = parseDate(expiryDate);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime thirtyDaysLater = now.plusDays(30);
            return expiry.isBefore(thirtyDaysLater);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查证书是否已过期
     */
    public boolean isExpired() {
        try {
            LocalDateTime expiry = parseDate(expiryDate);
            return expiry.isBefore(LocalDateTime.now());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取剩余天数
     */
    public long daysRemaining() {
        try {
            LocalDateTime expiry = parseDate(expiryDate);
            LocalDateTime now = LocalDateTime.now();
            return java.time.Duration.between(now, expiry).toDays();
        } catch (Exception e) {
            return 0;
        }
    }

    private LocalDateTime parseDate(String dateStr) {
        // OpenSSL 日期格式: "Jan 1 00:00:00 2026 GMT"
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d HH:mm:ss yyyy z", java.util.Locale.ENGLISH);
        return LocalDateTime.parse(dateStr, formatter);
    }

    @Override
    public String toString() {
        return String.format("Certificate[domain=%s, expires=%s, days=%d]",
            domain, expiryDate, daysRemaining());
    }
}
