package dev.starcore.starcore.module.blueprint;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 蓝图核心接口
 * 定义蓝图的保存、加载、粘贴等操作
 */
public interface Blueprint {

    /**
     * 获取蓝图ID
     */
    String getId();

    /**
     * 获取蓝图名称
     */
    String getName();

    /**
     * 设置蓝图名称
     */
    void setName(String name);

    /**
     * 获取蓝图描述
     */
    String getDescription();

    /**
     * 设置蓝图描述
     */
    void setDescription(String description);

    /**
     * 获取蓝图作者
     */
    UUID getAuthorId();

    /**
     * 获取蓝图作者名称
     */
    String getAuthorName();

    /**
     * 获取蓝图分类
     */
    String getCategory();

    /**
     * 设置蓝图分类
     */
    void setCategory(String category);

    /**
     * 获取创建时间戳
     */
    long getCreatedTime();

    /**
     * 获取最后修改时间戳
     */
    long getModifiedTime();

    /**
     * 获取所属国家ID（如果有）
     */
    String getNationId();

    /**
     * 获取所属玩家ID
     */
    UUID getOwnerId();

    /**
     * 获取蓝图版本
     */
    int getVersion();

    /**
     * 获取蓝图的区域选择
     */
    RegionSelection getRegion();

    /**
     * 获取蓝图的方块列表
     */
    List<BlueprintBlock> getBlocks();

    /**
     * 获取蓝图的调色板
     */
    BlueprintPalette getPalette();

    /**
     * 获取方块总数
     */
    int getBlockCount();

    /**
     * 获取方块数量统计
     */
    BlueprintTypes.BlueprintStats getStats();

    /**
     * 保存蓝图到文件
     */
    void save() throws BlueprintTypes.BlueprintException;

    /**
     * 异步保存蓝图
     */
    CompletableFuture<Void> saveAsync();

    /**
     * 从世界粘贴蓝图
     * @param world 目标世界
     * @param origin 粘贴原点（左下角）
     * @param air 是否替换空气方块
     * @param entities 是否包含实体
     */
    BlueprintTypes.PasteResult paste(World world, BlockVector3 origin, boolean air, boolean entities);

    /**
     * 异步粘贴蓝图
     */
    CompletableFuture<BlueprintTypes.PasteResult> pasteAsync(World world, BlockVector3 origin, boolean air, boolean entities);

    /**
     * 旋转蓝图
     * @param times 旋转次数（90度每次）
     */
    Blueprint rotate(int times);

    /**
     * 镜像蓝图
     * @param axis 镜像轴 (x, y, z)
     */
    Blueprint mirror(String axis);

    /**
     * 获取蓝图数据大小（字节）
     */
    long getDataSize();

    /**
     * 检查蓝图是否为空
     */
    boolean isEmpty();

    /**
     * 验证蓝图完整性
     */
    boolean isValid();

    /**
     * 获取蓝图元数据
     */
    BlueprintTypes.BlueprintMetadata getMetadata();

    /**
     * 创建蓝图副本
     */
    Blueprint copy();
}
