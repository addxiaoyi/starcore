package dev.starcore.starcore.module.blueprint;
import java.util.Optional;

import org.bukkit.Material;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 方块调色板
 * 减少重复存储 Material 字符串以优化存储空间
 */
public class BlueprintPalette implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, Integer> materialToId = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<String> idToMaterial = new CopyOnWriteArrayList<>();
    private final Map<Short, String> dataToState = new ConcurrentHashMap<>();

    public BlueprintPalette() {
        // 预注册空气方块
        registerMaterial("AIR");
        registerMaterial("CAVE_AIR");
        registerMaterial("VOID_AIR");
    }

    /**
     * 注册一个材质，返回分配的ID
     * 使用同步块保证线程安全
     */
    public int registerMaterial(String material) {
        String upperMat = material.toUpperCase();
        Integer existing = materialToId.get(upperMat);
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            // 双重检查
            existing = materialToId.get(upperMat);
            if (existing != null) {
                return existing;
            }
            int id = idToMaterial.size();
            idToMaterial.add(upperMat);
            materialToId.put(upperMat, id);
            return id;
        }
    }

    /**
     * 获取材质的ID
     */
    public int getMaterialId(String material) {
        Integer id = materialToId.get(material.toUpperCase());
        return id != null ? id : registerMaterial(material);
    }

    /**
     * 通过ID获取材质名称
     */
    public String getMaterialById(int id) {
        if (id >= 0 && id < idToMaterial.size()) {
            return idToMaterial.get(id);
        }
        return "AIR";
    }

    /**
     * 注册方块状态
     */
    public short registerBlockState(String state) {
        int hash = state.hashCode();
        Optional<Short> existing = findExistingState(state, hash);
        if (existing.isPresent()) {
            return existing.get();
        }
        short id = (short) dataToState.size();
        dataToState.put(id, state);
        return id;
    }

    private Optional<Short> findExistingState(String state, int hash) {
        for (Map.Entry<Short, String> entry : dataToState.entrySet()) {
            if (entry.getValue().hashCode() == hash && entry.getValue().equals(state)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /**
     * 获取方块状态
     */
    public String getBlockState(short id) {
        return dataToState.get(id);
    }

    /**
     * 获取唯一材质数量
     */
    public int getUniqueMaterialCount() {
        return idToMaterial.size();
    }

    /**
     * 获取方块状态数量
     */
    public int getBlockStateCount() {
        return dataToState.size();
    }

    /**
     * 获取所有注册的材质
     */
    public Collection<String> getAllMaterials() {
        return Collections.unmodifiableCollection(idToMaterial);
    }

    /**
     * 创建压缩的块数据数组
     * 返回格式：[paletteId1, paletteId1, paletteId2, ...]
     */
    public int[] createPackedBlockArray(List<BlueprintBlock> blocks, int width, int height, int length) {
        int[] packed = new int[width * height * length];
        Arrays.fill(packed, 0); // 默认空气

        for (BlueprintBlock block : blocks) {
            int relX = block.getRelativeX();
            int relY = block.getRelativeY();
            int relZ = block.getRelativeZ();

            if (relX >= 0 && relX < width && relY >= 0 && relY < height && relZ >= 0 && relZ < length) {
                int index = relY * width * length + relZ * width + relX;
                int paletteId = getMaterialId(block.getMaterialName());
                packed[index] = paletteId;
            }
        }

        return packed;
    }

    /**
     * 从压缩数组恢复方块列表
     */
    public List<BlueprintBlock> unpackBlocks(int[] packed, int width, int height, int length) {
        List<BlueprintBlock> blocks = new ArrayList<>();

        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int index = y * width * length + z * width + x;
                    int paletteId = packed[index];
                    String material = getMaterialById(paletteId);

                    if (!"AIR".equals(material) && !"CAVE_AIR".equals(material) && !"VOID_AIR".equals(material)) {
                        Material mat = Material.valueOf(material);
                        blocks.add(new BlueprintBlock(x, y, z, mat, (byte) 0));
                    }
                }
            }
        }

        return blocks;
    }

    /**
     * 计算压缩比
     */
    public double getCompressionRatio(List<BlueprintBlock> blocks) {
        if (blocks.isEmpty()) return 1.0;

        // 原始大小（假设每个方块需要存储完整材质名，约20字节）
        long originalSize = blocks.size() * 20L;

        // 压缩后大小
        long compressedSize = idToMaterial.size() * 20L + packedSize(blocks);

        return originalSize > 0 ? (double) compressedSize / originalSize : 1.0;
    }

    private long packedSize(List<BlueprintBlock> blocks) {
        // 估算：每个方块1-2字节（palette ID）+ 调色板大小
        return blocks.size() * 2L + idToMaterial.size() * 20L;
    }

    @Override
    public String toString() {
        return "BlueprintPalette{materials=" + idToMaterial.size() + ", states=" + dataToState.size() + "}";
    }
}
