package dev.starcore.starcore.module.map.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 自定义地图标记模型
 * 玩家可以创建的自定义标记
 */
public class CustomMapMarker {
    private final String id;
    private final UUID ownerId;
    private final String name;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final MapMarkerCategory category;
    private final String icon;
    private final String color;
    private final String description;
    private final boolean pinned;
    private final boolean visibleToNation;
    private final boolean visibleToAll;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Map<String, String> metadata;

    private CustomMapMarker(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.ownerId = builder.ownerId;
        this.name = builder.name;
        this.world = builder.world;
        this.x = builder.x;
        this.y = builder.y;
        this.z = builder.z;
        this.category = builder.category;
        this.icon = builder.icon != null ? builder.icon : builder.category.getIconKey();
        this.color = builder.color != null ? builder.color : "#3B82F6";
        this.description = builder.description != null ? builder.description : "";
        this.pinned = builder.pinned;
        this.visibleToNation = builder.visibleToNation;
        this.visibleToAll = builder.visibleToAll;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : Instant.now();
        this.metadata = Map.copyOf(builder.metadata);
    }

    public String getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
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

    public double getY() {
        return y;
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

    public String getDescription() {
        return description;
    }

    public boolean isPinned() {
        return pinned;
    }

    public boolean isVisibleToNation() {
        return visibleToNation;
    }

    public boolean isVisibleToAll() {
        return visibleToAll;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * 转换为通用MapMarker
     */
    public MapMarker toMapMarker() {
        Map<String, String> meta = new java.util.HashMap<>(metadata);
        meta.put("ownerId", ownerId.toString());
        meta.put("category", category.name());
        meta.put("description", description);
        meta.put("pinned", String.valueOf(pinned));
        meta.put("visibleToNation", String.valueOf(visibleToNation));
        meta.put("visibleToAll", String.valueOf(visibleToAll));
        meta.put("createdAt", createdAt.toString());

        return new MapMarker(
            "custom:" + id,
            name,
            world,
            x,
            z,
            icon,
            dev.starcore.starcore.module.map.MapMarkerService.wrapMetadata(meta)
        );
    }

    /**
     * 创建Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private UUID ownerId;
        private String name;
        private String world;
        private double x;
        private double y;
        private double z;
        private MapMarkerCategory category = MapMarkerCategory.CUSTOM_PLAYER;
        private String icon;
        private String color;
        private String description;
        private boolean pinned = false;
        private boolean visibleToNation = false;
        private boolean visibleToAll = false;
        private Instant createdAt;
        private Instant updatedAt;
        private Map<String, String> metadata = Map.of();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder ownerId(UUID ownerId) {
            this.ownerId = ownerId;
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

        public Builder position(double x, double y, double z) {
            this.x = x;
            this.y = y;
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

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder pinned(boolean pinned) {
            this.pinned = pinned;
            return this;
        }

        public Builder visibleToNation(boolean visible) {
            this.visibleToNation = visible;
            return this;
        }

        public Builder visibleToAll(boolean visible) {
            this.visibleToAll = visible;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public CustomMapMarker build() {
            return new CustomMapMarker(this);
        }
    }
}
