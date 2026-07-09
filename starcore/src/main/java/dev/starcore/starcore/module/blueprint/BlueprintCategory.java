package dev.starcore.starcore.module.blueprint;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * 蓝图分类接口
 * 支持分组管理蓝图
 */
public interface BlueprintCategory {

    /**
     * 获取分类ID
     */
    String getId();

    /**
     * 获取分类名称
     */
    String getName();

    /**
     * 获取分类描述
     */
    String getDescription();

    /**
     * 获取分类图标
     */
    String getIcon();

    /**
     * 获取分类颜色
     */
    String getColor();

    /**
     * 获取分类中的蓝图ID列表
     */
    List<String> getBlueprintIds();

    /**
     * 获取分类中的蓝图数量
     */
    int getBlueprintCount();

    /**
     * 获取父分类ID
     */
    String getParentId();

    /**
     * 获取子分类
     */
    List<String> getChildIds();

    /**
     * 获取分类排序权重
     */
    int getSortOrder();

    /**
     * 检查是否公开
     */
    boolean isPublic();

    /**
     * 获取可访问的玩家/国家
     */
    List<UUID> getAllowedPlayers();

    /**
     * 获取可访问的国家
     */
    List<String> getAllowedNations();

    /**
     * 获取分类创建时间
     */
    long getCreatedTime();

    /**
     * 检查蓝图是否属于此分类
     */
    boolean containsBlueprint(String blueprintId);

    /**
     * 添加蓝图到分类
     */
    void addBlueprint(String blueprintId);

    /**
     * 从分类移除蓝图
     */
    void removeBlueprint(String blueprintId);
}
