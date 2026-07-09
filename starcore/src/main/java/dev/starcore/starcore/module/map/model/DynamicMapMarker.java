package dev.starcore.starcore.module.map.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 动态地图标记模型
 * 具有时效性和动态行为的标记
 */
public class DynamicMapMarker {
    private final String id;
    private final String name;
    private final String world;
    private final double x;
    private final double z;
    private final MapMarkerCategory category;
    private final String icon;
    private final String color;
    private final String ownerId;
    private final String nationId;
    private final Instant expiresAt;
    private final Duration duration;
    private final int priority;
    private final boolean pulse;
    private final String pulseColor;
    private final Map<String, String> metadata;

    private DynamicMapMarker(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.name = builder.name;
        this.world = builder.world;
        this.x = builder.x;
        this.z = builder.z;
        this.category = builder.category;
        this.icon = builder.icon != null ? builder.icon : builder.category.getIconKey();
        this.color = builder.color != null ? builder.color : "#EF4444";
        this.ownerId = builder.ownerId;
        this.nationId = builder.nationId;
        this.expiresAt = builder.expiresAt;
        this.duration = builder.duration;
        this.priority = builder.priority;
        this.pulse = builder.pulse;
        this.pulseColor = builder.pulseColor != null ? builder.pulseColor : "#EF4444";
        this.metadata = Map.copyOf(builder.metadata);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getZ() {
        return z;
    }

    public MapMarkerCategory getCategory() {
        return category;
    }

    public String getIcon() {
        return icon;
    }

    public String getColor() {
        return color;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getNationId() {
        return nationId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Duration getDuration() {
        return duration;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isPulsing() {
        return pulse;
    }

    public String getPulseColor() {
        return pulseColor;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * 检查标记是否已过期
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * 获取剩余时间
     */
    public Duration getRemainingTime() {
        if (expiresAt == null) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * 获取剩余百分比 (0.0 - 1.0)
     */
    public double getRemainingPercentage() {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return 1.0;
        }
        Duration remaining = getRemainingTime();
        return Math.max(0.0, Math.min(1.0, (double) remaining.toMillis() / duration.toMillis()));
    }

    /**
     * 转换为通用MapMarker
     */
    public MapMarker toMapMarker() {
        Map<String, String> meta = new java.util.HashMap<>(metadata);
        meta.put("markerType", "dynamic");
        meta.put("ownerId", ownerId != null ? ownerId : "");
        meta.put("nationId", nationId != null ? nationId : "");
        meta.put("category", category.name());
        meta.put("priority", String.valueOf(priority));
        meta.put("pulse", String.valueOf(pulse));
        meta.put("pulseColor", pulseColor);
        meta.put("expired", String.valueOf(isExpired()));
        if (expiresAt != null) {
            meta.put("expiresAt", expiresAt.toString());
        }
        if (duration != null) {
            meta.put("remainingSeconds", String.valueOf(getRemainingTime().toSeconds()));
            meta.put("remainingPercentage", String.valueOf(getRemainingPercentage()));
        }

        return new MapMarker(
            "dynamic:" + id,
            name,
            world,
            x,
            z,
            icon,
            dev.starcore.starcore.module.map.MapMarkerService.wrapMetadata(meta)
        );
    }

    /**
     * 创建战争区域标记
     */
    public static DynamicMapMarker warZone(String name, String world, double x, double z,
                                            String nationId, String enemyNationId,
                                            Duration duration) {
        return builder()
            .name(name)
            .world(world)
            .position(x, z)
            .category(MapMarkerCategory.DYNAMIC_WAR)
            .color("#DC2626")
            .nationId(nationId)
            .duration(duration)
            .priority(100)
            .pulse(true)
            .pulseColor("#EF4444")
            .metadata(Map.of(
                "enemyNationId", enemyNationId != null ? enemyNationId : "",
                "warType", "territory"
            ))
            .build();
    }

    /**
     * 创建PVP区域标记
     */
    public static DynamicMapMarker pvpZone(String name, String world, double x, double z,
                                           Duration duration) {
        return builder()
            .name(name)
            .world(world)
            .position(x, z)
            .category(MapMarkerCategory.DYNAMIC_PVP)
            .color("#F97316")
            .duration(duration)
            .priority(50)
            .pulse(true)
            .pulseColor("#FB923C")
            .build();
    }

    /**
     * 创建安全区域标记
     */
    public static DynamicMapMarker safeZone(String name, String world, double x, double z) {
        return builder()
            .name(name)
            .world(world)
            .position(x, z)
            .category(MapMarkerCategory.DYNAMIC_SAFE)
            .color("#22C55E")
            .duration(Duration.ofHours(24))
            .priority(10)
            .pulse(false)
            .build();
    }

    /**
     * 创建Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String world;
        private double x;
        private double z;
        private MapMarkerCategory category = MapMarkerCategory.DYNAMIC_WAR;
        private String icon;
        private String color;
        private String ownerId;
        private String nationId;
        private Instant expiresAt;
        private Duration duration = Duration.ofHours(1);
        private int priority = 0;
        private boolean pulse = true;
        private String pulseColor;
        private Map<String, String> metadata = Map.of();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder world(String world) {
            this.world = world;
            return this;
        }

        public Builder position(double x, double z) {
            this.x = x;
            this.z = z;
            return this;
        }

        public Builder category(MapMarkerCategory category) {
            this.category = category;
            return this;
        }

        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public Builder color(String color) {
            this.color = color;
            return this;
        }

        public Builder ownerId(String ownerId) {
            this.ownerId = ownerId;
            return this;
        }

        public Builder nationId(String nationId) {
            this.nationId = nationId;
            return this;
        }

        public Builder duration(Duration duration) {
            this.duration = duration;
            this.expiresAt = Instant.now().plus(duration);
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            if (duration != null) {
                this.duration = Duration.between(Instant.now(), expiresAt);
            }
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder pulse(boolean pulse) {
            this.pulse = pulse;
            return this;
        }

        public Builder pulseColor(String pulseColor) {
            this.pulseColor = pulseColor;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public DynamicMapMarker build() {
            return new DynamicMapMarker(this);
        }
    }
}
