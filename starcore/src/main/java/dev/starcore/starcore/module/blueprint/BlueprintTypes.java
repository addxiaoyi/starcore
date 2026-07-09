package dev.starcore.starcore.module.blueprint;

import java.io.Serializable;
import java.util.UUID;

/**
 * 蓝图公共数据类型
 * 这些类型在多个接口之间共享
 */
public final class BlueprintTypes {

    private BlueprintTypes() {}

    /**
     * 蓝图元数据
     */
    public static final class BlueprintMetadata implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String id;
        private final String name;
        private final String description;
        private final UUID authorId;
        private final String authorName;
        private final String category;
        private final long createdTime;
        private final long modifiedTime;
        private final int version;
        private final String nationId;
        private final UUID ownerId;
        private final String formatVersion;
        private final int blockCount;
        private final boolean isPublic;
        private final boolean isShared;

        public BlueprintMetadata(
            String id,
            String name,
            String description,
            UUID authorId,
            String authorName,
            String category,
            long createdTime,
            long modifiedTime,
            int version,
            String nationId,
            UUID ownerId,
            String formatVersion
        ) {
            this(id, name, description, authorId, authorName, category, createdTime, modifiedTime,
                 version, nationId, ownerId, formatVersion, 0, false, false);
        }

        public BlueprintMetadata(
            String id,
            String name,
            String description,
            UUID authorId,
            String authorName,
            String category,
            long createdTime,
            long modifiedTime,
            int version,
            String nationId,
            UUID ownerId,
            String formatVersion,
            int blockCount,
            boolean isPublic,
            boolean isShared
        ) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.authorId = authorId;
            this.authorName = authorName;
            this.category = category;
            this.createdTime = createdTime;
            this.modifiedTime = modifiedTime;
            this.version = version;
            this.nationId = nationId;
            this.ownerId = ownerId;
            this.formatVersion = formatVersion;
            this.blockCount = blockCount;
            this.isPublic = isPublic;
            this.isShared = isShared;
        }

        public String id() { return id; }
        public String name() { return name; }
        public String description() { return description; }
        public UUID authorId() { return authorId; }
        public String authorName() { return authorName; }
        public String category() { return category; }
        public long createdTime() { return createdTime; }
        public long modifiedTime() { return modifiedTime; }
        public int version() { return version; }
        public String nationId() { return nationId; }
        public UUID ownerId() { return ownerId; }
        public String formatVersion() { return formatVersion; }
        public int blockCount() { return blockCount; }
        public boolean isPublic() { return isPublic; }
        public boolean isShared() { return isShared; }
    }

    /**
     * 粘贴结果
     */
    public static final class PasteResult {
        private final int blocksPlaced;
        private final int blocksSkipped;
        private final long timeMs;
        private final boolean success;
        private final String errorMessage;

        public PasteResult(int blocksPlaced, int blocksSkipped, long timeMs, boolean success, String errorMessage) {
            this.blocksPlaced = blocksPlaced;
            this.blocksSkipped = blocksSkipped;
            this.timeMs = timeMs;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static PasteResult success(int placed, int skipped, long timeMs) {
            return new PasteResult(placed, skipped, timeMs, true, null);
        }

        public static PasteResult failure(String error, long timeMs) {
            return new PasteResult(0, 0, timeMs, false, error);
        }

        public int blocksPlaced() { return blocksPlaced; }
        public int blocksSkipped() { return blocksSkipped; }
        public long timeMs() { return timeMs; }
        public boolean success() { return success; }
        public String errorMessage() { return errorMessage; }
    }

    /**
     * 蓝图统计信息
     */
    public static final class BlueprintStats {
        private final int totalBlocks;
        private final int airBlocks;
        private final int solidBlocks;
        private final int uniqueMaterials;
        private final int width;
        private final int height;
        private final int depth;
        private final long dataSize;

        public BlueprintStats(
            int totalBlocks,
            int airBlocks,
            int solidBlocks,
            int uniqueMaterials,
            int width,
            int height,
            int depth,
            long dataSize
        ) {
            this.totalBlocks = totalBlocks;
            this.airBlocks = airBlocks;
            this.solidBlocks = solidBlocks;
            this.uniqueMaterials = uniqueMaterials;
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.dataSize = dataSize;
        }

        public static BlueprintStats empty() {
            return new BlueprintStats(0, 0, 0, 0, 0, 0, 0, 0);
        }

        public int totalBlocks() { return totalBlocks; }
        public int airBlocks() { return airBlocks; }
        public int solidBlocks() { return solidBlocks; }
        public int uniqueMaterials() { return uniqueMaterials; }
        public int width() { return width; }
        public int height() { return height; }
        public int depth() { return depth; }
        public long dataSize() { return dataSize; }
    }

    /**
     * 服务统计
     */
    public static final class ServiceStats {
        private final int totalBlueprints;
        private final int totalCategories;
        private final int playerBlueprints;
        private final int nationBlueprints;
        private final long totalDataSize;

        public ServiceStats(
            int totalBlueprints,
            int totalCategories,
            int playerBlueprints,
            int nationBlueprints,
            long totalDataSize
        ) {
            this.totalBlueprints = totalBlueprints;
            this.totalCategories = totalCategories;
            this.playerBlueprints = playerBlueprints;
            this.nationBlueprints = nationBlueprints;
            this.totalDataSize = totalDataSize;
        }

        public int totalBlueprints() { return totalBlueprints; }
        public int totalCategories() { return totalCategories; }
        public int playerBlueprints() { return playerBlueprints; }
        public int nationBlueprints() { return nationBlueprints; }
        public long totalDataSize() { return totalDataSize; }
    }

    /**
     * 蓝图异常
     */
    public static class BlueprintException extends RuntimeException {
        public BlueprintException(String message) {
            super(message);
        }

        public BlueprintException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
