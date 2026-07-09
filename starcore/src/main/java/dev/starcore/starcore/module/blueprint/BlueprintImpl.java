package dev.starcore.starcore.module.blueprint;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 蓝图实现类
 */
public class BlueprintImpl implements Blueprint, Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String description;
    private UUID authorId;
    private String authorName;
    private String category;
    private long createdTime;
    private long modifiedTime;
    private String nationId;
    private UUID ownerId;
    private int version;

    private transient RegionSelection region;
    private transient List<BlueprintBlock> blocks;
    private transient BlueprintPalette palette;

    // 文件路径
    private transient Path dataFile;

    // 压缩格式存储
    private int[] packedBlocks; // 压缩后的块数据
    private int width;
    private int height;
    private int depth;

    public BlueprintImpl(String id, String name, UUID authorId, String authorName,
                         RegionSelection region, List<BlueprintBlock> blocks) {
        this.id = id;
        this.name = name;
        this.authorId = authorId;
        this.authorName = authorName;
        this.region = region;
        this.blocks = new ArrayList<>(blocks);
        this.palette = new BlueprintPalette();
        this.createdTime = System.currentTimeMillis();
        this.modifiedTime = createdTime;
        this.version = 1;
        this.category = "default";

        // 注册所有材质到调色板
        for (BlueprintBlock block : blocks) {
            palette.registerMaterial(block.getMaterialName());
        }

        // 压缩数据
        packBlocks();
    }

    private void packBlocks() {
        if (region == null || blocks == null) return;

        this.width = region.getWidth();
        this.height = region.getHeight();
        this.depth = region.getDepth();

        packedBlocks = palette.createPackedBlockArray(blocks, width, height, depth);
    }

    private void unpackBlocks() {
        if (packedBlocks == null || palette == null) return;
        this.blocks = palette.unpackBlocks(packedBlocks, width, height, depth);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
        this.modifiedTime = System.currentTimeMillis();
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
        this.modifiedTime = System.currentTimeMillis();
    }

    @Override
    public UUID getAuthorId() {
        return authorId;
    }

    @Override
    public String getAuthorName() {
        return authorName;
    }

    @Override
    public String getCategory() {
        return category;
    }

    @Override
    public void setCategory(String category) {
        this.category = category;
        this.modifiedTime = System.currentTimeMillis();
    }

    @Override
    public long getCreatedTime() {
        return createdTime;
    }

    @Override
    public long getModifiedTime() {
        return modifiedTime;
    }

    @Override
    public String getNationId() {
        return nationId;
    }

    public void setNationId(String nationId) {
        this.nationId = nationId;
    }

    @Override
    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    // 公开和分享状态（用于元数据）
    private boolean isPublic;
    private boolean isShared;

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setShared(boolean isShared) {
        this.isShared = isShared;
    }

    public boolean isShared() {
        return isShared;
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public RegionSelection getRegion() {
        return region;
    }

    @Override
    public List<BlueprintBlock> getBlocks() {
        if (blocks == null && packedBlocks != null) {
            unpackBlocks();
        }
        return blocks != null ? Collections.unmodifiableList(blocks) : Collections.emptyList();
    }

    @Override
    public BlueprintPalette getPalette() {
        return palette;
    }

    @Override
    public int getBlockCount() {
        return blocks != null ? blocks.size() : (packedBlocks != null ? packedBlocks.length : 0);
    }

    @Override
    public dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintStats getStats() {
        if (blocks == null && packedBlocks != null) {
            unpackBlocks();
        }

        int total = getBlockCount();
        int air = 0;
        int solid = 0;
        Set<String> materials = new HashSet<>();

        if (blocks != null) {
            for (BlueprintBlock block : blocks) {
                String mat = block.getMaterialName();
                materials.add(mat);
                if ("AIR".equals(mat) || "CAVE_AIR".equals(mat) || "VOID_AIR".equals(mat)) {
                    air++;
                } else {
                    solid++;
                }
            }
        }

        return new BlueprintTypes.BlueprintStats(
            total, air, solid, materials.size(),
            width, height, depth,
            getDataSize()
        );
    }

    @Override
    public void save() throws dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintException {
        if (dataFile == null) {
            throw new dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintException("Data file path not set");
        }
        try {
            packBlocks(); // 确保数据已压缩
            serializeToFile(dataFile);
        } catch (IOException e) {
            throw new dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintException("Failed to save blueprint: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<Void> saveAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                save();
            } catch (dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public dev.starcore.starcore.module.blueprint.BlueprintTypes.PasteResult paste(World world, BlockVector3 origin, boolean air, boolean entities) {
        long startTime = System.currentTimeMillis();
        int placed = 0;
        int skipped = 0;

        if (blocks == null && packedBlocks != null) {
            unpackBlocks();
        }

        if (blocks == null || blocks.isEmpty()) {
            return dev.starcore.starcore.module.blueprint.BlueprintTypes.PasteResult.failure("No blocks to paste", 0);
        }

        BlockVector3 origin2 = getRegion() != null ? getRegion().getMinPoint() : BlockVector3.at(0, 0, 0);

        for (BlueprintBlock block : blocks) {
            try {
                Material mat = block.getMaterial();
                if (!air && (mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR)) {
                    skipped++;
                    continue;
                }

                int offsetX = block.getRelativeX() - origin2.getX();
                int offsetY = block.getRelativeY() - origin2.getY();
                int offsetZ = block.getRelativeZ() - origin2.getZ();

                if (block.applyToWorld(world, origin.getX() - origin2.getX() + offsetX,
                        origin.getY() - origin2.getY() + offsetY,
                        origin.getZ() - origin2.getZ() + offsetZ)) {
                    placed++;
                }
            } catch (Exception e) {
                skipped++;
            }
        }

        long timeMs = System.currentTimeMillis() - startTime;
        return dev.starcore.starcore.module.blueprint.BlueprintTypes.PasteResult.success(placed, skipped, timeMs);
    }

    @Override
    public CompletableFuture<dev.starcore.starcore.module.blueprint.BlueprintTypes.PasteResult> pasteAsync(World world, BlockVector3 origin, boolean air, boolean entities) {
        return CompletableFuture.supplyAsync(() -> paste(world, origin, air, entities));
    }

    @Override
    public Blueprint rotate(int times) {
        if (blocks == null && packedBlocks != null) {
            unpackBlocks();
        }
        if (blocks == null) return this;

        times = ((times % 4) + 4) % 4;
        if (times == 0) return this;

        List<BlueprintBlock> rotated = new ArrayList<>();

        for (BlueprintBlock block : blocks) {
            int x = block.getRelativeX();
            int y = block.getRelativeY();
            int z = block.getRelativeZ();

            int newX, newZ;
            for (int i = 0; i < times; i++) {
                int temp = x;
                x = -z;
                z = temp;
            }

            rotated.add(new BlueprintBlock(x, y, z, block.getMaterialName(),
                rotateData(block.getData(), times), block.getBlockState(), block.getTileEntityData()));
        }

        BlueprintImpl copy = new BlueprintImpl(id + "_rotated", name + " (Rotated)",
            authorId, authorName, null, rotated);
        copy.category = this.category;
        copy.description = this.description;
        copy.nationId = this.nationId;
        copy.ownerId = this.ownerId;

        return copy;
    }

    private byte rotateData(byte data, int times) {
        // 提取翻转位 (bit 3) 和朝向位 (bits 0-2)
        int flipped = (data & 0x08) >>> 3;
        int facing = data & 0x07;

        // 楼梯/台阶等方块的朝向旋转映射 (90度顺时针)
        // 0: 东(+X), 1: 西(-X), 2: 南(+Z), 3: 北(-Z)
        // 4: 上升东北, 5: 上升西北, 6: 上升西南, 7: 上升东南
        int[] rotationMap = {2, 3, 0, 1, 6, 7, 4, 5};

        for (int i = 0; i < times; i++) {
            facing = rotationMap[facing];
        }

        return (byte) ((flipped << 3) | facing);
    }

    @Override
    public Blueprint mirror(String axis) {
        if (blocks == null && packedBlocks != null) {
            unpackBlocks();
        }
        if (blocks == null) return this;

        List<BlueprintBlock> mirrored = new ArrayList<>();

        int maxX = blocks.stream().mapToInt(BlueprintBlock::getRelativeX).max().orElse(0);
        int maxZ = blocks.stream().mapToInt(BlueprintBlock::getRelativeZ).max().orElse(0);

        for (BlueprintBlock block : blocks) {
            int x = block.getRelativeX();
            int z = block.getRelativeZ();

            switch (axis.toLowerCase()) {
                case "x":
                    x = maxX - x;
                    break;
                case "z":
                    z = maxZ - z;
                    break;
                case "xz":
                    x = maxX - x;
                    z = maxZ - z;
                    break;
            }

            mirrored.add(new BlueprintBlock(x, block.getRelativeY(), z, block.getMaterialName(),
                block.getData(), block.getBlockState(), block.getTileEntityData()));
        }

        BlueprintImpl copy = new BlueprintImpl(id + "_mirrored", name + " (Mirrored)",
            authorId, authorName, null, mirrored);
        copy.category = this.category;
        copy.description = this.description;
        copy.nationId = this.nationId;
        copy.ownerId = this.ownerId;

        return copy;
    }

    @Override
    public long getDataSize() {
        if (packedBlocks != null) {
            return packedBlocks.length * 4L + palette.getUniqueMaterialCount() * 20L;
        }
        return blocks != null ? blocks.size() * 50L : 0;
    }

    @Override
    public boolean isEmpty() {
        return getBlockCount() == 0;
    }

    @Override
    public boolean isValid() {
        return id != null && name != null && authorId != null;
    }

    @Override
    public dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintMetadata getMetadata() {
        return new dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintMetadata(
            id, name, description, authorId, authorName, category,
            createdTime, modifiedTime, version, nationId, ownerId, "1.0",
            getBlockCount(), isPublic, isShared
        );
    }

    @Override
    public Blueprint copy() {
        if (blocks == null && packedBlocks != null) {
            unpackBlocks();
        }

        BlueprintImpl copy = new BlueprintImpl(
            id + "_copy_" + System.currentTimeMillis(),
            name + " (Copy)",
            authorId, authorName,
            region, blocks != null ? new ArrayList<>(blocks) : null
        );
        copy.category = this.category;
        copy.description = this.description;
        copy.nationId = this.nationId;
        copy.ownerId = this.ownerId;
        copy.palette = this.palette;

        return copy;
    }

    public void setDataFile(Path path) {
        this.dataFile = path;
    }

    public Path getDataFile() {
        return dataFile;
    }

    public void setRegion(RegionSelection region) {
        this.region = region;
    }

    // ========== 序列化方法 ==========

    private void serializeToFile(Path file) throws IOException {
        Files.createDirectories(file.getParent());

        try (DataOutputStream out = new DataOutputStream(
                new GZIPOutputStream(Files.newOutputStream(file)))) {

            // 写入头部
            out.writeUTF("BLUEPRINT");
            out.writeInt(1); // 版本

            // 写入元数据
            out.writeUTF(id);
            out.writeUTF(name);
            out.writeUTF(description != null ? description : "");
            out.writeUTF(authorId.toString());
            out.writeUTF(authorName);
            out.writeUTF(category != null ? category : "default");
            out.writeLong(createdTime);
            out.writeLong(modifiedTime);
            out.writeUTF(nationId != null ? nationId : "");
            out.writeUTF(ownerId != null ? ownerId.toString() : "");
            out.writeInt(version);

            // 写入区域信息
            if (region != null) {
                out.writeUTF(region.getType());
                out.writeUTF(region.getWorldName());
                out.writeInt(region.getWidth());
                out.writeInt(region.getHeight());
                out.writeInt(region.getDepth());
            } else {
                out.writeUTF("NONE");
            }

            // 写入调色板
            out.writeInt(palette.getUniqueMaterialCount());
            for (String mat : palette.getAllMaterials()) {
                out.writeUTF(mat);
            }

            // 写入压缩数据
            out.writeInt(width);
            out.writeInt(height);
            out.writeInt(depth);
            out.writeInt(packedBlocks != null ? packedBlocks.length : 0);

            if (packedBlocks != null) {
                for (int block : packedBlocks) {
                    out.writeInt(block);
                }
            }

            out.flush();
        }
    }

    public static BlueprintImpl deserializeFromFile(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new GZIPInputStream(Files.newInputStream(file)))) {

            // 读取头部
            String header = in.readUTF();
            if (!"BLUEPRINT".equals(header)) {
                throw new IOException("Invalid blueprint file format");
            }

            int version = in.readInt();

            // 读取元数据
            String id = in.readUTF();
            String name = in.readUTF();
            String description = in.readUTF();
            UUID authorId = UUID.fromString(in.readUTF());
            String authorName = in.readUTF();
            String category = in.readUTF();
            long createdTime = in.readLong();
            long modifiedTime = in.readLong();
            String nationId = in.readUTF();
            String ownerIdStr = in.readUTF();
            int versionNum = in.readInt();

            // 读取区域信息（简化处理）
            String regionType = in.readUTF();
            String worldName = "";
            int width = 0, height = 0, depth = 0;
            if (!"NONE".equals(regionType)) {
                worldName = in.readUTF();
                width = in.readInt();
                height = in.readInt();
                depth = in.readInt();
            }

            // 读取调色板
            int paletteSize = in.readInt();
            BlueprintPalette palette = new BlueprintPalette();
            for (int i = 0; i < paletteSize; i++) {
                palette.registerMaterial(in.readUTF());
            }

            // 读取压缩数据
            width = in.readInt();
            height = in.readInt();
            depth = in.readInt();
            int blockCount = in.readInt();
            int[] packedBlocks = new int[blockCount];
            for (int i = 0; i < blockCount; i++) {
                packedBlocks[i] = in.readInt();
            }

            // 创建蓝图对象
            BlueprintImpl blueprint = new BlueprintImpl(
                id, name, authorId, authorName, null, null
            );
            blueprint.description = description.isEmpty() ? null : description;
            blueprint.category = category;
            blueprint.createdTime = createdTime;
            blueprint.modifiedTime = modifiedTime;
            blueprint.nationId = nationId.isEmpty() ? null : nationId;
            blueprint.ownerId = ownerIdStr.isEmpty() ? null : UUID.fromString(ownerIdStr);
            blueprint.version = versionNum;
            blueprint.palette = palette;
            blueprint.packedBlocks = packedBlocks;
            blueprint.width = width;
            blueprint.height = height;
            blueprint.depth = depth;
            blueprint.dataFile = file;

            return blueprint;
        }
    }

    @Override
    public String toString() {
        return "BlueprintImpl{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", author='" + authorName + '\'' +
               ", blocks=" + getBlockCount() +
               '}';
    }
}
