package dev.starcore.starcore.module.blueprint;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 蓝图服务接口
 * 管理蓝图创建/保存/加载/粘贴
 */
public interface BlueprintService {

    // ========== 蓝图管理 ==========

    /**
     * 创建新蓝图
     */
    Blueprint createBlueprint(Player player, RegionSelection region, String name, String description);

    /**
     * 保存蓝图
     */
    void saveBlueprint(Blueprint blueprint) throws BlueprintTypes.BlueprintException;

    /**
     * 异步保存蓝图
     */
    CompletableFuture<Void> saveBlueprintAsync(Blueprint blueprint);

    /**
     * 加载蓝图
     */
    Optional<Blueprint> loadBlueprint(String blueprintId);

    /**
     * 异步加载蓝图
     */
    CompletableFuture<Optional<Blueprint>> loadBlueprintAsync(String blueprintId);

    /**
     * 删除蓝图
     */
    boolean deleteBlueprint(String blueprintId);

    /**
     * 重命名蓝图
     */
    boolean renameBlueprint(String blueprintId, String newName);

    /**
     * 复制蓝图
     */
    Blueprint copyBlueprint(String blueprintId, String newName);

    // ========== 蓝图查询 ==========

    /**
     * 获取玩家的所有蓝图
     */
    List<BlueprintTypes.BlueprintMetadata> getPlayerBlueprints(UUID playerId);

    /**
     * 获取国家的所有蓝图
     */
    List<BlueprintTypes.BlueprintMetadata> getNationBlueprints(String nationId);

    /**
     * 获取所有公开蓝图
     */
    List<BlueprintTypes.BlueprintMetadata> getPublicBlueprints();

    /**
     * 获取分类中的蓝图
     */
    List<BlueprintTypes.BlueprintMetadata> getBlueprintsByCategory(String categoryId);

    /**
     * 搜索蓝图
     */
    List<BlueprintTypes.BlueprintMetadata> searchBlueprints(String query);

    /**
     * 按名称查找蓝图
     */
    Optional<Blueprint> findBlueprintByName(String name);

    // ========== 分类管理 ==========

    /**
     * 获取所有分类
     */
    List<BlueprintCategory> getAllCategories();

    /**
     * 创建分类
     */
    BlueprintCategory createCategory(String name, String description, String icon, String color);

    /**
     * 删除分类
     */
    boolean deleteCategory(String categoryId);

    /**
     * 更新分类
     */
    boolean updateCategory(String categoryId, String name, String description);

    // ========== 剪贴板管理 ==========

    /**
     * 获取玩家的剪贴板
     */
    Optional<BlueprintClipboard> getClipboard(UUID playerId);

    /**
     * 复制选区到剪贴板
     */
    BlueprintClipboard copyToClipboard(Player player, RegionSelection region);

    /**
     * 粘贴剪贴板
     */
    BlueprintTypes.PasteResult pasteClipboard(Player player, Location origin, boolean includeAir);

    /**
     * 清空剪贴板
     */
    void clearClipboard(UUID playerId);

    // ========== 编辑会话 ==========

    /**
     * 获取玩家的编辑会话
     */
    Optional<EditSession> getEditSession(UUID playerId);

    /**
     * 创建编辑会话
     */
    EditSession createEditSession(Player player);

    /**
     * 关闭编辑会话
     */
    void closeEditSession(UUID playerId);

    /**
     * 撤销操作
     */
    boolean undo(UUID playerId);

    /**
     * 重做操作
     */
    boolean redo(UUID playerId);

    // ========== 粘贴操作 ==========

    /**
     * 粘贴蓝图到世界
     */
    BlueprintTypes.PasteResult pasteBlueprint(Player player, String blueprintId, Location origin,
                                         boolean includeAir, boolean entities);

    /**
     * 异步粘贴蓝图
     */
    CompletableFuture<BlueprintTypes.PasteResult> pasteBlueprintAsync(Player player, String blueprintId,
                                                                   Location origin,
                                                                   boolean includeAir, boolean entities);

    /**
     * 批量粘贴蓝图（用于建筑队列）
     */
    CompletableFuture<List<BlueprintTypes.PasteResult>> batchPaste(UUID playerId, List<String> blueprintIds,
                                                              Location origin);

    // ========== 格式兼容 ==========

    /**
     * 导入外部格式蓝图（如 WorldEdit .schematic）
     */
    Optional<Blueprint> importBlueprint(String filePath, String format);

    /**
     * 导出蓝图到外部格式
     */
    boolean exportBlueprint(String blueprintId, String filePath, String format);

    /**
     * 获取支持的导入格式
     */
    List<String> getSupportedImportFormats();

    /**
     * 获取支持的导出格式
     */
    List<String> getSupportedExportFormats();

    // ========== 统计 ==========

    /**
     * 获取服务统计
     */
    BlueprintTypes.ServiceStats getStats();
}
