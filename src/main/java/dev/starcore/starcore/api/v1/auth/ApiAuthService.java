package dev.starcore.starcore.api.v1.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API 认证服务
 * 提供 API Key 和签名认证机制
 */
public final class ApiAuthService {

    public static final String HEADER_API_KEY = "X-API-Key";
    public static final String HEADER_SIGNATURE = "X-Signature";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_NONCE = "X-Nonce";

    private static final String SIGNATURE_ALGORITHM = "HmacSHA256";
    private static final Duration SIGNATURE_TTL = Duration.ofMinutes(5);
    private static final Duration API_KEY_TTL = Duration.ofHours(24);

    private final String signingSecret;
    private final Map<String, ApiKeyInfo> apiKeys = new ConcurrentHashMap<>();
    private final Map<String, Long> nonceCache = new ConcurrentHashMap<>();

    public ApiAuthService(String signingSecret) {
        this.signingSecret = signingSecret != null ? signingSecret : generateFallbackSecret();
    }

    /**
     * 生成 API Key
     */
    public String generateApiKey(UUID playerId, String playerName, ApiKeyPermission... permissions) {
        String key = generateRandomKey();
        Instant now = Instant.now();
        apiKeys.put(key, new ApiKeyInfo(
            key,
            playerId,
            playerName,
            now,
            now.plus(API_KEY_TTL),
            java.util.Arrays.asList(permissions)
        ));
        return key;
    }

    /**
     * 验证 API Key
     */
    public Optional<ApiKeyInfo> validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        ApiKeyInfo info = apiKeys.get(apiKey);
        if (info == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(info.expiresAt())) {
            apiKeys.remove(apiKey);
            return Optional.empty();
        }
        return Optional.of(info);
    }

    /**
     * 撤销 API Key
     */
    public boolean revokeApiKey(String apiKey) {
        return apiKeys.remove(apiKey) != null;
    }

    /**
     * 验证请求签名
     */
    public SignatureValidationResult validateSignature(
        String method,
        String path,
        String queryString,
        String timestamp,
        String nonce,
        String signature
    ) {
        if (timestamp == null || nonce == null || signature == null) {
            return SignatureValidationResult.missing("Missing signature headers");
        }

        // 验证时间戳
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return SignatureValidationResult.invalid("Invalid timestamp format");
        }

        Instant requestTime = Instant.ofEpochSecond(ts);
        Instant now = Instant.now();

        if (Duration.between(requestTime, now).abs().compareTo(SIGNATURE_TTL) > 0) {
            return SignatureValidationResult.expired("Request timestamp expired");
        }

        // 验证 nonce（防止重放攻击）
        if (!validateNonce(nonce)) {
            return SignatureValidationResult.rejected("Nonce already used");
        }

        // 验证签名
        String expectedSignature = computeSignature(method, path, queryString, timestamp, nonce);
        if (!MessageDigest.isEqual(
            expectedSignature.getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8)
        )) {
            return SignatureValidationResult.invalid("Invalid signature");
        }

        return SignatureValidationResult.valid();
    }

    /**
     * 计算请求签名
     */
    public String computeSignature(String method, String path, String queryString, String timestamp, String nonce) {
        String payload = method.toUpperCase() + "\n" +
                        path + "\n" +
                        (queryString != null ? queryString : "") + "\n" +
                        timestamp + "\n" +
                        nonce;
        return hmacSha256Hex(payload, signingSecret);
    }

    /**
     * 生成签名所需的请求头
     */
    public SignatureHeaders generateSignatureHeaders(String method, String path, String queryString) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = generateNonce();
        String signature = computeSignature(method, path, queryString, timestamp, nonce);
        return new SignatureHeaders(timestamp, nonce, signature);
    }

    private boolean validateNonce(String nonce) {
        if (nonce == null || nonce.isBlank()) {
            return false;
        }
        synchronized (nonceCache) {
            if (nonceCache.containsKey(nonce)) {
                return false;
            }
            nonceCache.put(nonce, System.currentTimeMillis());
            // 清理过期 nonce
            long cutoff = System.currentTimeMillis() - SIGNATURE_TTL.toMillis() * 2;
            nonceCache.entrySet().removeIf(e -> e.getValue() < cutoff);
        }
        return true;
    }

    private String hmacSha256Hex(String value, String secret) {
        try {
            Mac mac = Mac.getInstance(SIGNATURE_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), SIGNATURE_ALGORITHM));
            byte[] hash = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private String generateRandomKey() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateNonce() {
        return UUID.randomUUID().toString();
    }

    private String generateFallbackSecret() {
        return UUID.randomUUID().toString() + UUID.randomUUID().toString();
    }

    /**
     * API Key 信息
     */
    public record ApiKeyInfo(
        String key,
        UUID playerId,
        String playerName,
        Instant createdAt,
        Instant expiresAt,
        java.util.List<ApiKeyPermission> permissions
    ) {
        public boolean hasPermission(ApiKeyPermission permission) {
            return permissions.contains(permission);
        }
    }

    /**
     * API Key 权限
     */
    public enum ApiKeyPermission {
        READ_NATIONS,
        WRITE_NATIONS,
        READ_PLAYERS,
        READ_TERRITORIES,
        READ_STATS,
        READ_FINANCE,
        WRITE_FINANCE,
        ADMIN
    }

    /**
     * 签名验证结果
     */
    public record SignatureValidationResult(
        boolean isValid,
        String error
    ) {
        public static SignatureValidationResult valid() {
            return new SignatureValidationResult(true, null);
        }

        public static SignatureValidationResult missing(String error) {
            return new SignatureValidationResult(false, error);
        }

        public static SignatureValidationResult invalid(String error) {
            return new SignatureValidationResult(false, error);
        }

        public static SignatureValidationResult expired(String error) {
            return new SignatureValidationResult(false, error);
        }

        public static SignatureValidationResult rejected(String error) {
            return new SignatureValidationResult(false, error);
        }
    }

    /**
     * 签名请求头
     */
    public record SignatureHeaders(
        String timestamp,
        String nonce,
        String signature
    ) {}
}
