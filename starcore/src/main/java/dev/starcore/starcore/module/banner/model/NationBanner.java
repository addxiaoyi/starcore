package dev.starcore.starcore.module.banner.model;

import java.util.UUID;

/**
 * Represents a nation's banner design
 */
public record NationBanner(
    UUID nationId,
    String pattern,
    String baseColor,
    String patternColor,
    long createdAt,
    long updatedAt
) {
    public NationBanner {
        if (nationId == null) {
            throw new IllegalArgumentException("nationId cannot be null");
        }
        if (pattern == null) {
            pattern = "plain";
        }
        if (baseColor == null) {
            baseColor = "WHITE";
        }
        if (patternColor == null) {
            patternColor = "BLACK";
        }
    }

    /**
     * Create a default banner for a new nation
     */
    public static NationBanner createDefault(UUID nationId) {
        long now = System.currentTimeMillis();
        return new NationBanner(nationId, "plain", "WHITE", "BLACK", now, now);
    }

    /**
     * Create a banner with custom design
     */
    public static NationBanner create(UUID nationId, String pattern, String baseColor, String patternColor) {
        long now = System.currentTimeMillis();
        return new NationBanner(nationId, pattern, baseColor, patternColor, now, now);
    }

    /**
     * Get a display name for the banner pattern
     */
    public String getPatternDisplayName() {
        return switch (pattern) {
            case "plain" -> "纯色";
            case "stripe" -> "条纹";
            case "cross" -> "十字";
            case "diagonal" -> "斜纹";
            case "border" -> "边框";
            case "flower" -> "花纹";
            case "gradient" -> "渐变";
            case "triangle" -> "三角";
            default -> pattern;
        };
    }

    /**
     * Check if this is a default/plain banner
     */
    public boolean isDefault() {
        return "plain".equals(pattern);
    }
}
