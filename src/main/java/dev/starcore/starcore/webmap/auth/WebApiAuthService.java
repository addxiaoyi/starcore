package dev.starcore.starcore.webmap.auth;

import org.bukkit.plugin.Plugin;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Web API 认证服务
 * 支持 JWT Token 和 API Key 两种认证方式
 */
public final class WebApiAuthService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long TOKEN_EXPIRY_SECONDS = 3600; // 1小时
    private static final int MAX_API_KEYS = 100;

    private final Plugin plugin;
    private final Logger logger;
    private final String jwtSecret;

    // API Key 存储
    private final Map<String, ApiKeyInfo> apiKeys = new ConcurrentHashMap<>();

    // Token 黑名单（已撤销的 token）
    private final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();

    // 速率限制
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    // IP 白名单
    private final Set<String> ipWhitelist = ConcurrentHashMap.newKeySet();

    public WebApiAuthService(Plugin plugin, String jwtSecret) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.jwtSecret = jwtSecret;
    }

    /**
     * 启动服务
     */
    public void start() {
        logger.info("✅ Web API 认证服务已启动");
        logger.info("认证方式: JWT Token + API Key");
    }

    /**
     * 生成 JWT Token
     */
    public String generateJwtToken(String userId, Map<String, String> claims) {
        try {
            long now = Instant.now().getEpochSecond();
            long exp = now + TOKEN_EXPIRY_SECONDS;

            // 构建 JWT Header
            String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

            // 构建 JWT Payload
            StringBuilder payload = new StringBuilder("{");
            payload.append("\"sub\":\"").append(userId).append("\",");
            payload.append("\"iat\":").append(now).append(",");
            payload.append("\"exp\":").append(exp);

            if (claims != null && !claims.isEmpty()) {
                claims.forEach((key, value) ->
                    payload.append(",\"").append(key).append("\":\"").append(value).append("\"")
                );
            }
            payload.append("}");

            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));

            // 计算签名
            String data = header + "." + encodedPayload;
            String signature = hmacSha256(data, jwtSecret);

            return data + "." + signature;

        } catch (Exception e) {
            logger.warning("生成 JWT Token 失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 验证 JWT Token
     */
    public TokenValidationResult validateJwtToken(String token) {
        if (token == null || token.isEmpty()) {
            return new TokenValidationResult(false, "Token 为空", null);
        }

        // 检查是否在黑名单中
        if (revokedTokens.contains(token)) {
            return new TokenValidationResult(false, "Token 已被撤销", null);
        }

        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return new TokenValidationResult(false, "Token 格式错误", null);
            }

            String header = parts[0];
            String payload = parts[1];
            String signature = parts[2];

            // 验证签名
            String expectedSignature = hmacSha256(header + "." + payload, jwtSecret);
            if (!signature.equals(expectedSignature)) {
                return new TokenValidationResult(false, "签名验证失败", null);
            }

            // 解析 Payload
            String decodedPayload = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
            Map<String, Object> claims = parseJsonSimple(decodedPayload);

            // 检查过期时间
            Long exp = (Long) claims.get("exp");
            if (exp != null && Instant.now().getEpochSecond() > exp) {
                return new TokenValidationResult(false, "Token 已过期", null);
            }

            String userId = (String) claims.get("sub");
            return new TokenValidationResult(true, "验证成功", userId);

        } catch (Exception e) {
            logger.warning("验证 JWT Token 失败: " + e.getMessage());
            return new TokenValidationResult(false, "Token 验证异常", null);
        }
    }

    /**
     * 撤销 Token
     */
    public void revokeToken(String token) {
        revokedTokens.add(token);
        logger.info("Token 已被撤销");
    }

    /**
     * 生成 API Key
     */
    public String generateApiKey(String owner, Set<String> permissions) {
        if (apiKeys.size() >= MAX_API_KEYS) {
            logger.warning("API Key 数量已达上限: " + MAX_API_KEYS);
            return null;
        }

        // 生成随机 API Key (32字节 = 64字符十六进制)
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        String apiKey = "sk_" + bytesToHex(randomBytes);

        // 保存 API Key 信息
        ApiKeyInfo info = new ApiKeyInfo(
            apiKey,
            owner,
            Instant.now(),
            null,
            permissions,
            true
        );
        apiKeys.put(apiKey, info);

        logger.info("生成 API Key: " + apiKey.substring(0, 10) + "... (Owner: " + owner + ")");
        return apiKey;
    }

    /**
     * 验证 API Key
     */
    public ApiKeyValidationResult validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return new ApiKeyValidationResult(false, "API Key 为空", null);
        }

        ApiKeyInfo info = apiKeys.get(apiKey);
        if (info == null) {
            return new ApiKeyValidationResult(false, "API Key 不存在", null);
        }

        if (!info.active()) {
            return new ApiKeyValidationResult(false, "API Key 已禁用", null);
        }

        // 检查过期时间
        if (info.expiresAt() != null && Instant.now().isAfter(info.expiresAt())) {
            return new ApiKeyValidationResult(false, "API Key 已过期", null);
        }

        return new ApiKeyValidationResult(true, "验证成功", info);
    }

    /**
     * 撤销 API Key
     */
    public boolean revokeApiKey(String apiKey) {
        ApiKeyInfo info = apiKeys.get(apiKey);
        if (info == null) {
            return false;
        }

        ApiKeyInfo updated = new ApiKeyInfo(
            info.apiKey(),
            info.owner(),
            info.createdAt(),
            info.expiresAt(),
            info.permissions(),
            false
        );
        apiKeys.put(apiKey, updated);

        logger.info("API Key 已撤销: " + apiKey.substring(0, 10) + "...");
        return true;
    }

    /**
     * 检查速率限制
     */
    public boolean checkRateLimit(String identifier, int maxRequests, long windowSeconds) {
        RateLimiter limiter = rateLimiters.computeIfAbsent(identifier,
            k -> new RateLimiter(maxRequests, windowSeconds));

        return limiter.allowRequest();
    }

    /**
     * 添加 IP 到白名单
     */
    public void addToIpWhitelist(String ipAddress) {
        ipWhitelist.add(ipAddress);
        logger.info("IP 已添加到白名单: " + ipAddress);
    }

    /**
     * 从白名单移除 IP
     */
    public void removeFromIpWhitelist(String ipAddress) {
        ipWhitelist.remove(ipAddress);
        logger.info("IP 已从白名单移除: " + ipAddress);
    }

    /**
     * 检查 IP 是否在白名单中
     */
    public boolean isIpWhitelisted(String ipAddress) {
        return ipWhitelist.isEmpty() || ipWhitelist.contains(ipAddress);
    }

    /**
     * 获取所有 API Keys
     */
    public List<ApiKeyInfo> listApiKeys() {
        return new ArrayList<>(apiKeys.values());
    }

    /**
     * 清理过期的 Token 和 API Keys
     */
    public void cleanupExpired() {
        // 清理过期的 API Keys
        long now = Instant.now().getEpochSecond();
        apiKeys.entrySet().removeIf(entry -> {
            ApiKeyInfo info = entry.getValue();
            return info.expiresAt() != null && info.expiresAt().getEpochSecond() < now;
        });

        logger.info("已清理过期的认证信息");
    }

    /**
     * HMAC-SHA256 签名
     */
    private String hmacSha256(String data, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    /**
     * 简单的 JSON 解析（仅用于 JWT Payload）
     */
    private Map<String, Object> parseJsonSimple(String json) {
        Map<String, Object> map = new HashMap<>();
        json = json.replaceAll("[{}]", "");
        String[] pairs = json.split(",");

        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replaceAll("\"", "");
                String value = kv[1].trim().replaceAll("\"", "");

                // 尝试解析为数字
                try {
                    map.put(key, Long.parseLong(value));
                } catch (NumberFormatException e) {
                    map.put(key, value);
                }
            }
        }
        return map;
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 速率限制器
     */
    private static class RateLimiter {
        private final int maxRequests;
        private final long windowMillis;
        private final Queue<Long> requestTimestamps = new LinkedList<>();

        public RateLimiter(int maxRequests, long windowSeconds) {
            this.maxRequests = maxRequests;
            this.windowMillis = windowSeconds * 1000;
        }

        public synchronized boolean allowRequest() {
            long now = System.currentTimeMillis();

            // 移除超出时间窗口的请求
            while (!requestTimestamps.isEmpty() && now - requestTimestamps.peek() > windowMillis) {
                requestTimestamps.poll();
            }

            // 检查是否超过限制
            if (requestTimestamps.size() >= maxRequests) {
                return false;
            }

            // 记录当前请求
            requestTimestamps.offer(now);
            return true;
        }
    }
}
