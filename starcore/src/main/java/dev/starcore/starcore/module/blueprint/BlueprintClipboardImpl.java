package dev.starcore.starcore.module.blueprint;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.*;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 剪贴板实现类
 */
public class BlueprintClipboardImpl implements BlueprintClipboard {
    private static final int MAX_HISTORY = 50;

    private final String id;
    private final UUID ownerId;
    private final String ownerName;

    private List<BlueprintBlock> blocks;
    private RegionSelection sourceRegion;
    private BlueprintPalette palette;
    private int[] packedBlocks;
    private int width, height, depth;

    // 撤销/重做栈
    private final Deque<ClipboardOperation> undoStack = new ArrayDeque<>();
    private final Deque<ClipboardOperation> redoStack = new ArrayDeque<>();

    // 变换状态
    private int rotation; // 0, 1, 2, 3 (90度增量)
    private String mirrorAxis;

    public BlueprintClipboardImpl(UUID ownerId, String ownerName) {
        this.id = UUID.randomUUID().toString();
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.blocks = new ArrayList<>();
        this.palette = new BlueprintPalette();
        this.rotation = 0;
        this.mirrorAxis = null;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public UUID getOwnerId() {
        return ownerId;
    }

    @Override
    public String getOwnerName() {
        return ownerName;
    }

    @Override
    public List<BlueprintBlock> getBlocks() {
        if (blocks == null) return Collections.emptyList();
        return Collections.unmodifiableList(applyTransforms());
    }

    private List<BlueprintBlock> applyTransforms() {
        if (blocks == null || blocks.isEmpty()) return Collections.emptyList();

        List<BlueprintBlock> result = new ArrayList<>();

        for (BlueprintBlock block : blocks) {
            int x = block.getRelativeX();
            int y = block.getRelativeY();
            int z = block.getRelativeZ();
            byte data = block.getData();

            // 应用旋转
            for (int i = 0; i < rotation; i++) {
                int temp = x;
                x = -z;
                z = temp;
                data = rotateBlockDataForMaterial(block.getMaterial(), data);
            }

            // 应用镜像
            if (mirrorAxis != null) {
                switch (mirrorAxis.toLowerCase()) {
                    case "x":
                        x = -x - 1;
                        break;
                    case "z":
                        z = -z - 1;
                        break;
                    case "xz":
                        x = -x - 1;
                        z = -z - 1;
                        break;
                }
            }

            result.add(new BlueprintBlock(x, y, z, block.getMaterialName(), data,
                block.getBlockState(), block.getTileEntityData()));
        }

        return result;
    }

    private byte rotateBlockData(byte data) {
        // 简化的数据旋转（保留向后兼容）
        return data;
    }

    /**
     * 旋转方块数据值（包含材质信息）
     * @param material 方块材质
     * @param data 原始数据值
     * @return 旋转后的数据值
     */
    private byte rotateBlockDataForMaterial(Material material, byte data) {
        if (material == null) return data;

        String name = material.name();

        // 楼梯旋转：东南西北顺时针旋转，保持高度位
        if (name.contains("STAIRS") && !name.contains("CHISELED") && !name.contains("SMOOTH")) {
            int direction = data & 0x7;
            boolean top = (data & 0x8) != 0;

            // 楼梯方向: 0=东北低, 1=东南低, 2=西南低, 3=西北低, 4=东北高, 5=东南高, 6=西南高, 7=西北高
            // 旋转：东北→东南→西南→西北→东北
            int[] stairRotation = {1, 2, 3, 0, 5, 6, 7, 4};

            if (direction >= 0 && direction < stairRotation.length) {
                int newDir = stairRotation[direction];
                return (byte) (newDir | (top ? 0x8 : 0));
            }
        }

        // 活塞臂、粘性活塞
        if (name.contains("PISTON_ARM") || (name.contains("PISTON") && !name.contains("HEAD"))) {
            // 活塞方向: 0=下, 1=上, 2=北, 3=南, 4=西, 5=东
            // 旋转时只旋转水平方向：东(5)→南(3)→西(4)→北(2)→东(5)
            int dir = data & 0x7;
            if (dir >= 2 && dir <= 5) {
                int[] pistonRotation = {0, 1, 3, 5, 2, 4}; // 下,上,北,南,西,东
                return (byte) pistonRotation[dir];
            }
            return data;
        }

        // 按钮
        if (name.contains("BUTTON")) {
            // 按钮方向: 0=下, 1=上, 2=北, 3=南, 4=西, 5=东
            int dir = data & 0x7;
            if (dir >= 2 && dir <= 5) {
                int[] buttonRotation = {0, 1, 3, 5, 2, 4};
                return (byte) buttonRotation[dir];
            }
            return data;
        }

        // 拉杆
        if (name.contains("LEVER")) {
            // 拉杆方向: 0=下,1=上,2=北,3=南,4=西,5=东,6=东(壁挂),7=西(壁挂),8=南(壁挂),9=北(壁挂)
            int dir = data & 0xF;
            if (dir >= 2 && dir <= 9) {
                int[] leverRotation = {0, 1, 3, 5, 2, 4, 8, 9, 6, 7};
                return (byte) leverRotation[dir];
            }
            return data;
        }

        // 栅栏门
        if (name.contains("FENCE_GATE")) {
            // 栅栏门方向: 0=南, 1=西, 2=北, 3=东
            int dir = data & 0x3;
            int[] gateRotation = {3, 0, 1, 2}; // 南→东→北→西→南
            return (byte) ((data & 0xFC) | gateRotation[dir]);
        }

        // 铁门
        if (name.contains("IRON_DOOR")) {
            // 铁门方向: 0=东, 1=南, 2=西, 3=北
            int dir = data & 0x3;
            int[] doorRotation = {1, 2, 3, 0}; // 东→南→西→北→东
            return (byte) ((data & 0xFC) | doorRotation[dir]);
        }

        // 桦木/深色橡木/丛林/金合欢木门
        if (name.contains("_DOOR") && !name.contains("IRON")) {
            // 木门方向: 0=东, 1=南, 2=西, 3=北
            int dir = data & 0x3;
            int[] doorRotation = {1, 2, 3, 0};
            return (byte) ((data & 0xFC) | doorRotation[dir]);
        }

        // 梯子
        if (name.contains("LADDER")) {
            // 梯子方向: 2=北, 3=南, 4=西, 5=东
            int dir = data & 0x7;
            if (dir >= 2 && dir <= 5) {
                int[] ladderRotation = {0, 1, 5, 4, 3, 2}; // 下,上,北,南,西,东
                return (byte) ladderRotation[dir];
            }
            return data;
        }

        // 地狱门框架 (Nether Portal)
        if (name.contains("NETHER_PORTAL") || name.contains("PORTAL")) {
            // 方向: 0=未使用, 1=未使用, 2=X轴(东西), 3=Z轴(南北)
            int dir = data & 0x3;
            if (dir == 2) return (byte) 3; // X→Z
            if (dir == 3) return (byte) 2; // Z→X
            return data;
        }

        // 末地传送门框架 (End Portal Frame)
        if (name.contains("END_PORTAL_FRAME")) {
            // 方向: 0=南, 1=西, 2=北, 3=东
            int dir = data & 0x3;
            int[] endRotation = {3, 0, 1, 2}; // 南→东→北→西→南
            return (byte) ((data & 0xFC) | endRotation[dir]);
        }

        // 红石比较器 (Comparator)
        if (name.contains("COMPARATOR")) {
            // 方向: 0=南, 1=西, 2=北, 3=东
            int dir = data & 0x3;
            int[] compRotation = {3, 0, 1, 2};
            return (byte) ((data & 0xFC) | compRotation[dir]);
        }

        // 红石中继器 (Repeater)
        if (name.contains("REPEATER")) {
            // 方向: 0=南, 1=西, 2=北, 3=东
            int dir = data & 0x3;
            int[] repRotation = {3, 0, 1, 2};
            return (byte) ((data & 0xFC) | repRotation[dir]);
        }

        // 旗帜 (Wall Banner / Standing Banner)
        if (name.contains("BANNER")) {
            // 方向: 0=南, 1=西, 2=北, 3=东
            int dir = data & 0x3;
            int[] bannerRotation = {3, 0, 1, 2};
            return (byte) ((data & 0xFC) | bannerRotation[dir]);
        }

        // 头颅 (Wall Skull / Skull)
        if (name.contains("SKULL")) {
            // 方向: 0=地板, 1=天花板, 2=北, 3=南, 4=西, 5=东
            int dir = data & 0x7;
            if (dir >= 2 && dir <= 5) {
                int[] skullRotation = {0, 1, 3, 5, 2, 4};
                return (byte) skullRotation[dir];
            }
            return data;
        }

        // 蛋糕 (Cake)
        if (material == Material.CAKE) {
            // 方向无关，保持原值
            return data;
        }

        // 睡莲 (Lily Pad)
        if (material == Material.LILY_PAD) {
            // 方向: 0-7 表示方向
            return data;
        }

        // 煤炭块/钻石块等不需要旋转
        // 默认返回原值
        return data;
    }

    @Override
    public int getBlockCount() {
        return blocks != null ? blocks.size() : 0;
    }

    @Override
    public RegionSelection getSourceRegion() {
        return sourceRegion;
    }

    @Override
    public BlueprintPalette getPalette() {
        return palette;
    }

    @Override
    public List<ClipboardOperation> getHistory() {
        List<ClipboardOperation> history = new ArrayList<>();
        history.addAll(undoStack);
        return history;
    }

    @Override
    public int getHistoryIndex() {
        return undoStack.size();
    }

    @Override
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    @Override
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    @Override
    public Optional<ClipboardOperation> undo() {
        if (!canUndo()) {
            return Optional.empty();
        }

        ClipboardOperation operation = undoStack.pop();
        redoStack.push(operation);

        // 恢复原始数据
        if (operation.previousBlocks() != null && !operation.previousBlocks().isEmpty()) {
            this.blocks = new ArrayList<>(operation.previousBlocks());
        }

        return Optional.of(operation);
    }

    @Override
    public Optional<ClipboardOperation> redo() {
        if (!canRedo()) {
            return Optional.empty();
        }

        ClipboardOperation operation = redoStack.pop();
        undoStack.push(operation);

        // 重新应用操作
        return Optional.of(operation);
    }

    @Override
    public void addToHistory(ClipboardOperation operation) {
        undoStack.push(operation);
        redoStack.clear();

        // 限制历史大小
        while (undoStack.size() > MAX_HISTORY) {
            undoStack.removeLast();
        }
    }

    @Override
    public void clearHistory() {
        undoStack.clear();
        redoStack.clear();
    }

    @Override
    public int getUndoStackSize() {
        return undoStack.size();
    }

    @Override
    public int getRedoStackSize() {
        return redoStack.size();
    }

    @Override
    public void copyFromRegion(RegionSelection region) {
        if (region == null) return;

        List<BlueprintBlock> newBlocks = new ArrayList<>();
        World world = Bukkit.getWorld(region.getWorldName());

        if (world == null) return;

        BlockVector3 min = region.getMinPoint();

        for (BlockVector3 pos : region.getBlocks()) {
            Block block = world.getBlockAt(pos.getX(), pos.getY(), pos.getZ());
            BlueprintBlock bpBlock = BlueprintBlock.fromBlock(block);

            // 转换为相对坐标
            newBlocks.add(new BlueprintBlock(
                pos.getX() - min.getX(),
                pos.getY() - min.getY(),
                pos.getZ() - min.getZ(),
                bpBlock.getMaterialName(),
                bpBlock.getData(),
                bpBlock.getBlockState(),
                bpBlock.getTileEntityData()
            ));

            palette.registerMaterial(bpBlock.getMaterialName());
        }

        this.blocks = newBlocks;
        this.sourceRegion = region;
        this.width = region.getWidth();
        this.height = region.getHeight();
        this.depth = region.getDepth();
        this.rotation = 0;
        this.mirrorAxis = null;

        addToHistory(ClipboardOperation.copy(
            min,
            "Copied " + newBlocks.size() + " blocks from " + region.getType()
        ));
    }

    @Override
    public dev.starcore.starcore.module.blueprint.BlueprintTypes.PasteResult pasteToWorld(World world, BlockVector3 origin, boolean includeAir) {
        long startTime = System.currentTimeMillis();
        int placed = 0;
        int skipped = 0;

        List<BlueprintBlock> transformed = applyTransforms();
        if (transformed.isEmpty()) {
            return new dev.starcore.starcore.module.blueprint.BlueprintTypes.PasteResult(
                0, 0, System.currentTimeMillis() - startTime, false, "No blocks to paste");
        }

        // 记录原世界数据用于撤销
        List<OriginalBlockState> originalStates = new ArrayList<>();

        // 计算变换后的边界
        int minX = transformed.stream().mapToInt(BlueprintBlock::getRelativeX).min().orElse(0);
        int minY = transformed.stream().mapToInt(BlueprintBlock::getRelativeY).min().orElse(0);
        int minZ = transformed.stream().mapToInt(BlueprintBlock::getRelativeZ).min().orElse(0);

        for (BlueprintBlock block : transformed) {
            try {
                Material mat = block.getMaterial();

                // 计算目标位置
                int targetX = origin.getX() + (block.getRelativeX() - minX);
                int targetY = origin.getY() + (block.getRelativeY() - minY);
                int targetZ = origin.getZ() + (block.getRelativeZ() - minZ);

                Block targetBlock = world.getBlockAt(targetX, targetY, targetZ);

                // 跳过空气
                if (!includeAir && (mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR)) {
                    skipped++;
                    continue;
                }

                // 记录原始方块状态用于撤销
                if (placed < 10000) { // 只记录前10000个用于撤销
                    originalStates.add(new OriginalBlockState(
                        targetX, targetY, targetZ,
                        targetBlock.getType(),
                        targetBlock.getBlockData()
                    ));
                }

                // 设置方块
                targetBlock.setType(mat, false);

                // 设置方块数据
                try {
                    if (block.hasBlockState()) {
                        org.bukkit.block.data.BlockData blockData = Bukkit.createBlockData(mat);
                        targetBlock.setBlockData(blockData, false);
                    }
                } catch (Exception e) {
                    // 使用默认方块数据
                }

                // 恢复方块实体数据
                if (block.hasTileEntityData()) {
                    applyTileEntityData(targetBlock, block);
                }

                placed++;
            } catch (Exception e) {
                skipped++;
            }
        }

        // 保存撤销信息
        if (!originalStates.isEmpty()) {
            addToHistory(ClipboardOperation.paste(
                BlockVector3.at(origin.getX(), origin.getY(), origin.getZ()),
                transformed,
                "Pasted " + placed + " blocks"
            ));
        }

        long timeMs = System.currentTimeMillis() - startTime;
        String message = String.format("Placed %d blocks, skipped %d (%.2f seconds)",
            placed, skipped, timeMs / 1000.0);
        return new dev.starcore.starcore.module.blueprint.BlueprintTypes.PasteResult(
            placed, skipped, timeMs, true, message);
    }

    /**
     * 应用方块实体数据
     */
    private void applyTileEntityData(Block block, BlueprintBlock bpBlock) {
        BlockState state = block.getState();
        String tileData = bpBlock.getTileEntityData();

        if (tileData == null) return;

        try {
            if (tileData.startsWith("SIGN:")) {
                if (state instanceof org.bukkit.block.Sign sign) {
                    String[] lines = tileData.substring(5).split("\\|", 4);
                    for (int i = 0; i < Math.min(lines.length, 4); i++) {
                        String text = org.bukkit.ChatColor.translateAlternateColorCodes('&', lines[i]);
                        sign.line(i, net.kyori.adventure.text.Component.text(text));
                    }
                    sign.update();
                }
            } else if (tileData.startsWith("CHEST:") || tileData.startsWith("CONTAINER:")) {
                if (state instanceof org.bukkit.block.Chest chest) {
                    applyChestItems(chest, tileData.substring(tileData.indexOf(":") + 1));
                } else if (state instanceof org.bukkit.block.Container container) {
                    applyChestItems(container, tileData.substring(tileData.indexOf(":") + 1));
                }
            }
        } catch (Exception e) {
            // 忽略应用实体数据时的错误
        }
    }

    private void applyChestItems(org.bukkit.block.Chest chest, String data) {
        if (data == null || data.isEmpty()) return;
        try {
            String[] items = data.split(";");
            for (String item : items) {
                if (item.isEmpty()) continue;
                String[] parts = item.split(":");
                if (parts.length >= 2) {
                    int slot = Integer.parseInt(parts[0]);
                    Material mat = Material.valueOf(parts[1]);
                    int amount = parts.length >= 3 ? Integer.parseInt(parts[2]) : 1;
                    org.bukkit.inventory.ItemStack stack = new org.bukkit.inventory.ItemStack(mat, amount);
                    chest.getInventory().setItem(slot, stack);
                }
            }
            chest.update();
        } catch (Exception ignored) {
        }
    }

    private void applyChestItems(org.bukkit.block.Container container, String data) {
        applyChestItems((org.bukkit.block.Chest) (org.bukkit.block.Chest.class.cast(container)), data);
    }

    /**
     * 原始方块状态（用于撤销）
     */
    private record OriginalBlockState(int x, int y, int z, Material material, org.bukkit.block.data.BlockData data) {}

    private BlockVector3 getSourceMinPoint(List<BlueprintBlock> blocks) {
        if (blocks.isEmpty()) return BlockVector3.at(0, 0, 0);

        int minX = blocks.stream().mapToInt(BlueprintBlock::getRelativeX).min().orElse(0);
        int minY = blocks.stream().mapToInt(BlueprintBlock::getRelativeY).min().orElse(0);
        int minZ = blocks.stream().mapToInt(BlueprintBlock::getRelativeZ).min().orElse(0);

        return BlockVector3.at(minX, minY, minZ);
    }

    @Override
    public void rotate(int times) {
        if (blocks == null || blocks.isEmpty()) return;

        this.rotation = (rotation + times) % 4;
        addToHistory(ClipboardOperation.rotate(times,
            "Rotated clipboard " + (times * 90) + " degrees"));
    }

    @Override
    public void mirror(String axis) {
        if (blocks == null || blocks.isEmpty()) return;

        this.mirrorAxis = axis;
        addToHistory(ClipboardOperation.mirror(axis,
            "Mirrored clipboard on " + axis + " axis"));
    }

    @Override
    public void clear() {
        this.blocks = new ArrayList<>();
        this.sourceRegion = null;
        this.palette = new BlueprintPalette();
        this.packedBlocks = null;
        this.rotation = 0;
        this.mirrorAxis = null;
        clearHistory();
    }

    /**
     * 获取当前旋转角度
     */
    public int getRotation() {
        return rotation;
    }

    /**
     * 获取当前镜像轴
     */
    public String getMirrorAxis() {
        return mirrorAxis;
    }

    @Override
    public String toString() {
        return "BlueprintClipboardImpl{" +
               "owner=" + ownerName +
               ", blocks=" + getBlockCount() +
               ", rotation=" + (rotation * 90) + "deg" +
               ", mirror=" + mirrorAxis +
               '}';
    }
}
