package dev.starcore.starcore.module.blueprint;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 编辑会话实现类
 */
public class EditSessionImpl implements EditSession {
    private static final int DEFAULT_MAX_UNDOS = 50;

    private final String sessionId;
    private final UUID playerId;
    private final String playerName;
    private final World world;
    private final long startTime;

    private RegionSelection selection;
    private Blueprint blueprint;
    private SessionState state;

    private final List<EditOperation> history = new ArrayList<>();
    private int historyPosition = -1;
    private int maxUndos = DEFAULT_MAX_UNDOS;

    // 每个玩家的会话缓存
    private static final Map<UUID, EditSessionImpl> sessions = new ConcurrentHashMap<>();

    public EditSessionImpl(Player player, World world) {
        this.sessionId = UUID.randomUUID().toString();
        this.playerId = player.getUniqueId();
        this.playerName = player.getName();
        this.world = world;
        this.startTime = System.currentTimeMillis();
        this.state = SessionState.IDLE;
    }

    public static EditSessionImpl getOrCreate(Player player) {
        EditSessionImpl session = sessions.get(player.getUniqueId());
        if (session == null) {
            session = new EditSessionImpl(player, player.getWorld());
            sessions.put(player.getUniqueId(), session);
        }
        return session;
    }

    public static Optional<EditSessionImpl> get(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public static void close(UUID playerId) {
        sessions.remove(playerId);
    }

    public static void closeAll() {
        sessions.clear();
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public UUID getPlayerId() {
        return playerId;
    }

    @Override
    public String getPlayerName() {
        return playerName;
    }

    @Override
    public World getWorld() {
        return world;
    }

    @Override
    public RegionSelection getSelection() {
        return selection;
    }

    @Override
    public void setSelection(RegionSelection selection) {
        this.selection = selection;
        this.state = selection != null ? SessionState.SELECTING : SessionState.IDLE;
    }

    @Override
    public Blueprint getBlueprint() {
        return blueprint;
    }

    @Override
    public List<EditOperation> getHistory() {
        return Collections.unmodifiableList(history);
    }

    @Override
    public int getHistoryPosition() {
        return historyPosition;
    }

    @Override
    public boolean canUndo() {
        return historyPosition >= 0 && historyPosition < history.size();
    }

    @Override
    public boolean canRedo() {
        return historyPosition < history.size() - 1;
    }

    @Override
    public boolean undo() {
        if (!canUndo()) {
            return false;
        }

        EditOperation op = history.get(historyPosition);
        historyPosition--;

        // 执行撤销
        if (op.type() == OperationType.PASTE || op.type() == OperationType.SET) {
            // 恢复原始方块
            for (BlueprintBlock block : op.before()) {
                restoreBlock(block);
            }
        }

        addOperation(EditOperation.undo());
        return true;
    }

    @Override
    public boolean redo() {
        if (!canRedo()) {
            return false;
        }

        historyPosition++;

        EditOperation op = history.get(historyPosition);

        // 执行重做
        if (op.type() == OperationType.PASTE || op.type() == OperationType.SET) {
            // 应用新方块
            for (BlueprintBlock block : op.after()) {
                restoreBlock(block);
            }
        }

        addOperation(EditOperation.redo());
        return true;
    }

    private void restoreBlock(BlueprintBlock block) {
        Block target = world.getBlockAt(block.getX(), block.getY(), block.getZ());
        target.setType(block.getMaterial(), false);
        // 使用 BlockData 更新方块状态
        org.bukkit.block.data.BlockData data = target.getBlockData();
        target.setBlockData(data, false);
    }

    @Override
    public void addOperation(EditOperation operation) {
        // 移除重做历史
        if (historyPosition < history.size() - 1) {
            history.subList(historyPosition + 1, history.size()).clear();
        }

        history.add(operation);
        historyPosition = history.size() - 1;

        // 限制历史大小
        while (history.size() > maxUndos) {
            history.remove(0);
            historyPosition--;
        }
    }

    @Override
    public void clearHistory() {
        history.clear();
        historyPosition = -1;
    }

    @Override
    public void setState(SessionState state) {
        this.state = state;
    }

    @Override
    public SessionState getState() {
        return state;
    }

    @Override
    public void begin() {
        this.state = SessionState.EDITING;
    }

    @Override
    public void commit() {
        this.state = SessionState.COMMITTED;
    }

    @Override
    public void rollback() {
        // 回滚所有操作
        while (canUndo()) {
            undo();
        }
        this.state = SessionState.IDLE;
    }

    @Override
    public void close() {
        close(playerId);
    }

    @Override
    public int getRemainingUndos() {
        return historyPosition + 1;
    }

    @Override
    public void setMaxUndos(int max) {
        this.maxUndos = max;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    // ========== 便捷方法 ==========

    /**
     * 设置方块
     */
    public void setBlock(int x, int y, int z, Material material) {
        if (state != SessionState.EDITING) {
            begin();
        }

        Block target = world.getBlockAt(x, y, z);
        BlueprintBlock before = BlueprintBlock.fromBlock(target);
        BlueprintBlock after = new BlueprintBlock(x, y, z, material, (byte) 0);

        target.setType(material, false);

        addOperation(EditOperation.set(BlockVector3.at(x, y, z), before, after));
    }

    /**
     * 填充区域
     */
    public void fill(RegionSelection region, Material material) {
        if (state != SessionState.EDITING) {
            begin();
        }

        List<BlueprintBlock> before = new ArrayList<>();
        List<BlueprintBlock> after = new ArrayList<>();

        for (BlockVector3 pos : region.getBlocks()) {
            Block block = world.getBlockAt(pos.getX(), pos.getY(), pos.getZ());
            before.add(BlueprintBlock.fromBlock(block));
            after.add(new BlueprintBlock(pos.getX(), pos.getY(), pos.getZ(), material, (byte) 0));

            block.setType(material, false);
        }

        addOperation(EditOperation.fill(region.getCenter(), before, after));
    }

    /**
     * 粘贴蓝图
     */
    public BlueprintTypes.PasteResult pasteBlueprint(BlockVector3 origin, boolean includeAir) {
        if (blueprint == null) {
            return BlueprintTypes.PasteResult.failure("No blueprint loaded", 0);
        }

        this.state = SessionState.PASTING;

        List<BlueprintBlock> before = new ArrayList<>();
        List<BlueprintBlock> after = new ArrayList<>();

        // 记录原始方块
        for (BlueprintBlock block : blueprint.getBlocks()) {
            int x = block.getX() + origin.getX();
            int y = block.getY() + origin.getY();
            int z = block.getZ() + origin.getZ();
            Block target = world.getBlockAt(x, y, z);
            before.add(BlueprintBlock.fromBlock(target));
        }

        // 执行粘贴
        BlueprintTypes.PasteResult result = blueprint.paste(world, origin, includeAir, false);

        if (result.success()) {
            // 记录新方块
            for (BlueprintBlock block : blueprint.getBlocks()) {
                int x = block.getX() + origin.getX();
                int y = block.getY() + origin.getY();
                int z = block.getZ() + origin.getZ();
                Block target = world.getBlockAt(x, y, z);
                after.add(BlueprintBlock.fromBlock(target));
            }

            addOperation(EditOperation.paste(origin, before, after));
            this.state = SessionState.EDITING;
        }

        return result;
    }

    @Override
    public String toString() {
        return "EditSessionImpl{" +
               "player=" + playerName +
               ", state=" + state +
               ", history=" + history.size() +
               ", selection=" + (selection != null ? selection.getType() : "none") +
               '}';
    }
}
