package dev.starcore.starcore.module.map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MapAccessManager {
    private static final String VIEWER_SIGNATURE_ALGORITHM = "HmacSHA256";

    // E-074: boundViewerAccessByAddress 永不自动清理仅依赖手动调用,
    // 但 MapModule 的广播任务会周期性调用 cleanupExpiredBoundViewerAccesses,
    // 避免过期 MapViewerAccess 永久驻留。补充:惰性访问时也移除过期条目。
    private final Map<String, MapViewerAccess> boundViewerAccessByAddress = new ConcurrentHashMap<>();

    String viewerSignature(UUID viewerId, boolean fullAccess, long expiresAtEpochSecond, String secret) {
        return hmacSha256Hex(viewerId + ":" + (fullAccess ? "full" : "allied") + ':' + expiresAtEpochSecond, secret);
    }

    MapViewerAccess resolveSignedAccess(
        Map<String, String> params,
        boolean secretConfigured,
        String secret,
        long nowEpochSecond
    ) {
        String viewer = params.get("viewer");
        String access = params.get("access");
        String expiresAt = params.get("exp");
        String signature = params.get("sig");
        if (viewer == null || viewer.isBlank() || access == null || access.isBlank() || expiresAt == null || expiresAt.isBlank() || signature == null || signature.isBlank()) {
            throw new IllegalArgumentException("Missing viewer access parameters");
        }
        if (!secretConfigured) {
            throw new IllegalArgumentException("Map access secret is not configured");
        }
        UUID viewerId;
        try {
            viewerId = UUID.fromString(viewer);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid viewer");
        }
        boolean fullAccess = "full".equalsIgnoreCase(access);
        if (!fullAccess && !"allied".equalsIgnoreCase(access)) {
            throw new IllegalArgumentException("Invalid access scope");
        }
        long expiresAtEpochSecond;
        try {
            expiresAtEpochSecond = Long.parseLong(expiresAt);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid access expiry");
        }
        if (expiresAtEpochSecond <= 0L) {
            throw new IllegalArgumentException("Invalid access expiry");
        }
        if (nowEpochSecond > expiresAtEpochSecond) {
            throw new IllegalArgumentException("Viewer access expired");
        }
        String expected = viewerSignature(viewerId, fullAccess, expiresAtEpochSecond, secret);
        // E-073: 在用 MessageDigest.isEqual 之前先校验签名为 64 位 hex 字符串,
        // 短于或长于 expected 长度的直接拒绝,且仅接受 hex 字符(避免非 hex 字符触发字节差异比较)。
        // MessageDigest.isEqual 已是恒定时间比较,但提前长度检查可让恶意输入快速失败。
        String actual = signature.trim().toLowerCase(Locale.ROOT);
        if (!isHex(actual) || actual.length() != expected.length()) {
            throw new IllegalArgumentException("Invalid viewer signature");
        }
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid viewer signature");
        }
        return new MapViewerAccess(viewerId, fullAccess, expiresAtEpochSecond, "signed");
    }

    // E-073: 校验字符串仅含 hex 字符
    private static boolean isHex(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    boolean hasSignedAccessParameters(Map<String, String> params) {
        return hasValue(params.get("viewer"))
            || hasValue(params.get("access"))
            || hasValue(params.get("exp"))
            || hasValue(params.get("sig"));
    }

    boolean bindViewerAccess(UUID viewerId, boolean fullAccess, String remoteAddress, long expiresAtEpochSecond) {
        String normalizedAddress = normalizeRemoteAddress(remoteAddress);
        if (viewerId == null || normalizedAddress == null) {
            return false;
        }
        boundViewerAccessByAddress.put(normalizedAddress, new MapViewerAccess(viewerId, fullAccess, expiresAtEpochSecond, "ip"));
        return true;
    }

    Optional<MapViewerAccess> resolveBoundViewerAccess(String remoteAddress, long nowEpochSecond) {
        String normalizedAddress = normalizeRemoteAddress(remoteAddress);
        if (normalizedAddress == null) {
            return Optional.empty();
        }
        MapViewerAccess access = boundViewerAccessByAddress.get(normalizedAddress);
        if (access == null) {
            return Optional.empty();
        }
        if (access.isExpiredAt(nowEpochSecond)) {
            boundViewerAccessByAddress.remove(normalizedAddress, access);
            return Optional.empty();
        }
        return Optional.of(access);
    }

    void cleanupExpiredBoundViewerAccesses(long epochSecond) {
        boundViewerAccessByAddress.entrySet().removeIf(entry -> entry.getValue().isExpiredAt(epochSecond));
    }

    String resolveClientAddress(String connectionAddress, String forwardedFor, List<String> trustedProxies) {
        // E-075: 原 isTrustedProxy 仅支持精确 IP 匹配,无 CIDR 网段支持,
        // 攻击者若能从受信代理网段访问,可设任意 X-Forwarded-For 伪造源 IP 绕过 IP 绑定 access。
        // 当前缓解:require trustedProxies 显式配置非 0.0.0.0/0 (生产应限定到具体代理 IP 列表);
        // 支持 CIDR (a.b.c.d/24) 匹配受信代理网段,且 normalizeRemoteAddress 区分 IPv4/IPv6。
        // 调用方 ops 必须将 trustedProxies 限定到实际反向代理 IP / 子网,避免开放伪造。
        String normalizedConnectionAddress = normalizeRemoteAddress(connectionAddress);
        if (normalizedConnectionAddress == null) {
            return null;
        }
        if (!isTrustedProxy(normalizedConnectionAddress, trustedProxies)) {
            return normalizedConnectionAddress;
        }
        String forwardedClientAddress = firstForwardedAddress(forwardedFor);
        return forwardedClientAddress == null ? normalizedConnectionAddress : forwardedClientAddress;
    }

    String normalizeRemoteAddress(String remoteAddress) {
        if (remoteAddress == null) {
            return null;
        }
        String normalized = remoteAddress.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("[") && normalized.contains("]")) {
            return normalized.substring(1, normalized.indexOf(']'));
        }
        int firstColon = normalized.indexOf(':');
        if (firstColon > 0 && firstColon == normalized.lastIndexOf(':') && normalized.substring(0, firstColon).contains(".")) {
            return normalized.substring(0, firstColon);
        }
        return normalized;
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isTrustedProxy(String connectionAddress, List<String> trustedProxies) {
        if (trustedProxies == null || trustedProxies.isEmpty()) {
            return false;
        }
        // E-075: 用 CIDR 匹配支持子网,但也保留精确 IP 匹配。拒绝 0.0.0.0/0 和 ::/0 这种全开放 CIDR,
        // 防止 ops 误配导致接受任意源伪造。
        for (String entry : trustedProxies) {
            if (entry == null || entry.isBlank()) continue;
            String normalized = normalizeRemoteAddress(entry);
            if (normalized == null) continue;
            if (normalized.equalsIgnoreCase(connectionAddress)) {
                return true;
            }
            // 尝试 CIDR 匹配 (entry 形如 "a.b.c.d/24")
            if (entry.contains("/")) {
                String[] parts = entry.split("/", 2);
                if (parts.length != 2) continue;
                String cidrBase = normalizeRemoteAddress(parts[0]);
                int cidrMask;
                try {
                    cidrMask = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException nfe) {
                    continue;
                }
                // 拒绝全开放网段
                if (cidrMask == 0) continue;
                if (cidrBase != null && matchesCidr(connectionAddress, cidrBase, cidrMask)) {
                    return true;
                }
            }
        }
        return false;
    }

    // E-075: 实现简化版 IPv4 / IPv6 CIDR 匹配。仅支持纯 IPv4 base 和 IPv6 base。
    private static boolean matchesCidr(String addr, String cidrBase, int maskBits) {
        try {
            byte[] addrBytes = java.net.InetAddress.getByName(addr).getAddress();
            byte[] baseBytes = java.net.InetAddress.getByName(cidrBase).getAddress();
            if (addrBytes.length != baseBytes.length) return false;
            if (maskBits < 0 || maskBits > addrBytes.length * 8) return false;
            int fullBytes = maskBits / 8;
            int partialBits = maskBits % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (addrBytes[i] != baseBytes[i]) return false;
            }
            if (partialBits > 0 && fullBytes < addrBytes.length) {
                int mask = 0xFF << (8 - partialBits) & 0xFF;
                if ((addrBytes[fullBytes] & mask) != (baseBytes[fullBytes] & mask)) return false;
            }
            return true;
        } catch (java.net.UnknownHostException e) {
            return false;
        }
    }

    private String firstForwardedAddress(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return null;
        }
        for (String part : forwardedFor.split(",")) {
            String address = normalizeRemoteAddress(part);
            if (address != null) {
                return address;
            }
        }
        return null;
    }

    private String hmacSha256Hex(String value, String secret) {
        try {
            Mac mac = Mac.getInstance(VIEWER_SIGNATURE_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), VIEWER_SIGNATURE_ALGORITHM));
            byte[] hash = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte part : hash) {
                builder.append(String.format("%02x", part));
            }
            return builder.toString();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", exception);
        }
    }
}
