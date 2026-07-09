package dev.starcore.starcore.module.blueprint;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 剪贴板接口
 * 存储选区方块数据，提供撤销/重做支持
 */
public interface BlueprintClipboard {

    /**
     * 获取剪贴板ID
     */
    String getId();

    /**
     * 获取所有者ID
     */
    UUID getOwnerId();

    /**
     * 获取所有者名称
     */
    String getOwnerName();

    /**
     * 获取剪贴板中的方块列表
     */
    List<BlueprintBlock> getBlocks();

    /**
     * 获取方块数量
     */
    int getBlockCount();

    /**
     * 获取原区域
     */
    RegionSelection getSourceRegion();

    /**
     * 获取调色板
     */
    BlueprintPalette getPalette();

    /**
     * 获取操作历史
     */
    List<ClipboardOperation> getHistory();

    /**
     * 获取当前历史索引
     */
    int getHistoryIndex();

    /**
     * 是否可以撤销
     */
    boolean canUndo();

    /**
     * 是否可以重做
     */
    boolean canRedo();

    /**
     * 撤销上一次操作
     * @return 操作记录，如果栈为空则返回 Optional.empty()
     */
    Optional<ClipboardOperation> undo();

    /**
     * 重做上一次撤销
     * @return 操作记录，如果栈为空则返回 Optional.empty()
     */
    Optional<ClipboardOperation> redo();

    /**
     * 添加操作到历史
     */
    void addToHistory(ClipboardOperation operation);

    /**
     * 清空历史
     */
    void clearHistory();

    /**
     * 获取撤销栈大小
     */
    int getUndoStackSize();

    /**
     * 获取重做栈大小
     */
    int getRedoStackSize();

    /**
     * 从世界复制选区到剪贴板
     */
    void copyFromRegion(RegionSelection region);

    /**
     * 粘贴剪贴板内容到世界
     */
    BlueprintTypes.PasteResult pasteToWorld(org.bukkit.World world, BlockVector3 origin, boolean includeAir);

    /**
     * 旋转剪贴板
     */
    void rotate(int times);

    /**
     * 镜像剪贴板
     */
    void mirror(String axis);

    /**
     * 清空剪贴板
     */
    void clear();

    /**
     * 操作类型
     */
    enum OperationType {
        COPY,
        PASTE,
        ROTATE,
        MIRROR,
        CLEAR,
        UNDO,
        REDO
    }

    /**
     * 剪贴板操作记录
     */
    record ClipboardOperation(
        OperationType type,
        long timestamp,
        BlockVector3 position,
        List<BlueprintBlock> previousBlocks,
        String description
    ) {
        public static ClipboardOperation copy(BlockVector3 position, String description) {
            return new ClipboardOperation(OperationType.COPY, System.currentTimeMillis(), position, List.of(), description);
        }

        public static ClipboardOperation paste(BlockVector3 position, List<BlueprintBlock> replaced, String description) {
            return new ClipboardOperation(OperationType.PASTE, System.currentTimeMillis(), position, replaced, description);
        }

        public static ClipboardOperation rotate(int times, String description) {
            return new ClipboardOperation(OperationType.ROTATE, System.currentTimeMillis(), null, List.of(), description);
        }

        public static ClipboardOperation mirror(String axis, String description) {
            return new ClipboardOperation(OperationType.MIRROR, System.currentTimeMillis(), null, List.of(), description);
        }
    }
}
