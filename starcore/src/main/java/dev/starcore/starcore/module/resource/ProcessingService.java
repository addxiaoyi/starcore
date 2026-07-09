package dev.starcore.starcore.module.resource;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.model.Factory;
import dev.starcore.starcore.module.resource.model.ProcessingRecipe;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 资源加工服务
 * 管理工厂和资源加工
 */
public interface ProcessingService {
    /**
     * 获取加工配方
     *
     * @param recipeId 配方ID
     * @return 配方
     */
    Optional<ProcessingRecipe> getRecipe(String recipeId);

    /**
     * 获取所有配方
     *
     * @return 配方列表
     */
    Collection<ProcessingRecipe> getAllRecipes();

    /**
     * 根据产出资源查找配方
     *
     * @param outputResourceId 产出资源ID
     * @return 配方列表
     */
    Collection<ProcessingRecipe> getRecipesByOutput(String outputResourceId);

    /**
     * 根据输入资源查找配方
     *
     * @param inputResourceId 输入资源ID
     * @return 配方列表
     */
    Collection<ProcessingRecipe> getRecipesByInput(String inputResourceId);

    /**
     * 建造工厂
     *
     * @param ownerNationId 所有者国家ID
     * @param factoryName 工厂名称
     * @param type 工厂类型
     * @return 工厂
     */
    Factory buildFactory(NationId ownerNationId, String factoryName, Factory.FactoryType type);

    /**
     * 获取工厂
     *
     * @param factoryId 工厂ID
     * @return 工厂
     */
    Optional<Factory> getFactory(UUID factoryId);

    /**
     * 获取国家的所有工厂
     *
     * @param nationId 国家ID
     * @return 工厂列表
     */
    Collection<Factory> getFactories(NationId nationId);

    /**
     * 升级工厂
     *
     * @param factoryId 工厂ID
     * @return 是否成功升级
     */
    boolean upgradeFactory(UUID factoryId);

    /**
     * 设置工厂运营状态
     *
     * @param factoryId 工厂ID
     * @param operational 是否运营
     * @return 是否成功设置
     */
    boolean setFactoryOperational(UUID factoryId, boolean operational);

    /**
     * 开始加工
     *
     * @param factoryId 工厂ID
     * @param recipeId 配方ID
     * @param batches 批次数
     * @return 是否成功开始
     */
    boolean startProcessing(UUID factoryId, String recipeId, int batches);

    /**
     * 检查加工是否完成
     *
     * @param factoryId 工厂ID
     * @return 是否完成
     */
    boolean isProcessingComplete(UUID factoryId);

    /**
     * 完成加工并收集产品
     *
     * @param factoryId 工厂ID
     * @return 产出的资源（资源ID -> 数量）
     */
    Optional<Map<String, Long>> completeProcessing(UUID factoryId);

    /**
     * 自动加工
     * 使用可用资源自动执行配方
     *
     * @param nationId 国家ID
     * @param recipeId 配方ID
     * @param batches 批次数
     * @return 是否成功加工
     */
    boolean autoProcess(NationId nationId, String recipeId, int batches);

    /**
     * 计算加工成本
     *
     * @param recipeId 配方ID
     * @param batches 批次数
     * @return 成本（材料价值）
     */
    double calculateProcessingCost(String recipeId, int batches);

    /**
     * 计算加工收益
     *
     * @param recipeId 配方ID
     * @param batches 批次数
     * @return 收益（产品价值 - 材料价值）
     */
    double calculateProcessingProfit(String recipeId, int batches);

    /**
     * 刷新所有工厂状态
     * 检查并完成已完成的加工任务
     */
    void refreshFactories();

    /**
     * 注册加工配方
     *
     * @param recipe 配方
     */
    void registerRecipe(ProcessingRecipe recipe);
}
