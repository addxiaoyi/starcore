package dev.starcore.starcore.foundation.protection;

public record ExternalProtectionBridgeStatus(
    String bridgeKey,
    String bridgeName,
    String stateKey,
    boolean active,
    String summary
) {
    public ExternalProtectionBridgeStatus {
        bridgeKey = normalize(bridgeKey);
        bridgeName = normalize(bridgeName);
        stateKey = normalize(stateKey);
        summary = normalize(summary);
    }

    public String displayName() {
        if (!bridgeName.isBlank()) {
            return bridgeName;
        }
        if (!bridgeKey.isBlank()) {
            return bridgeKey;
        }
        return "UnknownBridge";
    }

    public String stateDisplayName() {
        return switch (stateKey) {
            case "active" -> "已连接";
            case "disabled" -> "已关闭";
            case "missing" -> "未安装";
            case "error" -> "异常";
            case "unconfigured" -> "未配置";
            default -> active ? "运行中" : "未启用";
        };
    }

    public String displaySummary() {
        return summary.isBlank() ? stateDisplayName() : summary;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
