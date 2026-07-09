package dev.starcore.starcore.module.blueprint;

import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * 编辑会话接口
 * 管理操作历史和撤销/重做
 */
public interface EditSession {

    /**
     * 获取会话ID
     */
    String getSessionId();

    /**
     * 获取玩家ID
     */
    UUID getPlayerId();

    /**
     * 获取玩家名称
     */
    String getPlayerName();

    /**
     * 获取当前世界
     */
    World getWorld();

    /**
     * 获取选区
     */
    RegionSelection getSelection();

    /**
     * 设置选区
     */
    void setSelection(RegionSelection selection);

    /**
     * 获取当前蓝图
     */
    Blueprint getBlueprint();

    /**
     * 获取操作历史
     */
    List<EditOperation> getHistory();

    /**
     * 获取当前历史位置
     */
    int getHistoryPosition();

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
     */
    boolean undo();

    /**
     * 重做上一次撤销
     */
    boolean redo();

    /**
     * 添加操作到历史
     */
    void addOperation(EditOperation operation);

    /**
     * 清空历史
     */
    void clearHistory();

    /**
     * 设置会话状态
     */
    void setState(SessionState state);

    /**
     * 获取会话状态
     */
    SessionState getState();

    /**
     * 开始会话
     */
    void begin();

    /**
     * 提交会话
     */
    void commit();

    /**
     * 回滚会话
     */
    void rollback();

    /**
     * 关闭会话
     */
    void close();

    /**
     * 获取剩余撤销次数
     */
    int getRemainingUndos();

    /**
     * 设置最大撤销次数
     */
    void setMaxUndos(int max);

    /**
     * 获取会话开始时间
     */
    long getStartTime();

    /**
     * 会话状态
     */
    enum SessionState {
        IDLE,           // 空闲
        SELECTING,      // 正在选择
        EDITING,        // 正在编辑
        PASTING,        // 正在粘贴
        COMMITTED,      // 已提交
        CLOSED          // 已关闭
    }

    /**
     * 编辑操作
     */
    record EditOperation(
        OperationType type,
        long timestamp,
        BlockVector3 position,
        List<BlueprintBlock> before,
        List<BlueprintBlock> after,
        String description
    ) {
        public static EditOperation create(OperationType type, BlockVector3 position,
                                           List<BlueprintBlock> before, List<BlueprintBlock> after,
                                           String description) {
            return new EditOperation(type, System.currentTimeMillis(), position, before, after, description);
        }

        public static EditOperation paste(BlockVector3 position, List<BlueprintBlock> before,
                                          List<BlueprintBlock> after) {
            return create(OperationType.PASTE, position, before, after, "粘贴蓝图");
        }

        public static EditOperation fill(BlockVector3 position, List<BlueprintBlock> before,
                                         List<BlueprintBlock> after) {
            return create(OperationType.FILL, position, before, after, "填充方块");
        }

        public static EditOperation set(BlockVector3 position, BlueprintBlock before,
                                        BlueprintBlock after) {
            return create(OperationType.SET, position,
                before != null ? List.of(before) : List.of(),
                after != null ? List.of(after) : List.of(),
                "设置方块");
        }

        public static EditOperation undo() {
            return new EditOperation(OperationType.UNDO, System.currentTimeMillis(), null, List.of(), List.of(), "撤销");
        }

        public static EditOperation redo() {
            return new EditOperation(OperationType.REDO, System.currentTimeMillis(), null, List.of(), List.of(), "重做");
        }
    }

    /**
     * 操作类型
     */
    enum OperationType {
        SET,            // 设置单个方块
        FILL,           // 填充区域
        PASTE,          // 粘贴蓝图
        DELETE,         // 删除区域
        REPLACE,        // 替换方块
        STACK,          // 堆叠复制
        UNDO,           // 撤销
        REDO            // 重做
    }
}
