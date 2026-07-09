package dev.starcore.starcore.module.resource.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 加工配方
 * 定义如何将原材料加工成产品
 */
public final class ProcessingRecipe {
    private final String recipeId;
    private final String recipeName;
    private final Map<String, Long> inputs;
    private final Map<String, Long> outputs;
    private final long processingTime;
    private final double energyCost;
    private final int requiredFactoryLevel;

    public ProcessingRecipe(String recipeId, String recipeName, Map<String, Long> inputs,
                            Map<String, Long> outputs, long processingTime, double energyCost,
                            int requiredFactoryLevel) {
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.recipeName = Objects.requireNonNull(recipeName, "recipeName");
        this.inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
        this.outputs = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
        this.processingTime = Math.max(1, processingTime);
        this.energyCost = Math.max(0.0, energyCost);
        this.requiredFactoryLevel = Math.max(1, requiredFactoryLevel);
    }

    /**
     * 获取配方ID
     */
    public String recipeId() {
        return recipeId;
    }

    /**
     * 获取配方名称
     */
    public String recipeName() {
        return recipeName;
    }

    /**
     * 获取输入材料（资源ID -> 数量）
     */
    public Map<String, Long> inputs() {
        return inputs;
    }

    /**
     * 获取输出产品（资源ID -> 数量）
     */
    public Map<String, Long> outputs() {
        return outputs;
    }

    /**
     * 获取加工时间（秒）
     */
    public long processingTime() {
        return processingTime;
    }

    /**
     * 获取能源消耗
     */
    public double energyCost() {
        return energyCost;
    }

    /**
     * 获取所需工厂等级
     */
    public int requiredFactoryLevel() {
        return requiredFactoryLevel;
    }

    /**
     * 检查是否有足够的材料
     */
    public boolean hasEnoughMaterials(Map<String, Long> availableResources) {
        for (Map.Entry<String, Long> entry : inputs.entrySet()) {
            String resourceId = entry.getKey();
            long required = entry.getValue();
            long available = availableResources.getOrDefault(resourceId, 0L);
            if (available < required) {
                return false;
            }
        }
        return true;
    }

    /**
     * 计算可以加工的批次数
     */
    public int calculateMaxBatches(Map<String, Long> availableResources) {
        int maxBatches = Integer.MAX_VALUE;
        for (Map.Entry<String, Long> entry : inputs.entrySet()) {
            String resourceId = entry.getKey();
            long required = entry.getValue();
            long available = availableResources.getOrDefault(resourceId, 0L);
            if (required == 0) continue;
            int batches = (int) (available / required);
            maxBatches = Math.min(maxBatches, batches);
        }
        return maxBatches == Integer.MAX_VALUE ? 0 : maxBatches;
    }

    /**
     * 计算批次输入材料
     */
    public Map<String, Long> calculateBatchInputs(int batches) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : inputs.entrySet()) {
            result.put(entry.getKey(), entry.getValue() * batches);
        }
        return result;
    }

    /**
     * 计算批次输出产品
     */
    public Map<String, Long> calculateBatchOutputs(int batches) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : outputs.entrySet()) {
            result.put(entry.getKey(), entry.getValue() * batches);
        }
        return result;
    }

    /**
     * 计算批次总加工时间
     */
    public long calculateBatchProcessingTime(int batches) {
        return processingTime * batches;
    }

    /**
     * 计算批次总能源消耗
     */
    public double calculateBatchEnergyCost(int batches) {
        return energyCost * batches;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessingRecipe that = (ProcessingRecipe) o;
        return recipeId.equals(that.recipeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipeId);
    }

    @Override
    public String toString() {
        return "ProcessingRecipe{" +
                "recipeId='" + recipeId + '\'' +
                ", recipeName='" + recipeName + '\'' +
                ", inputs=" + inputs.size() +
                ", outputs=" + outputs.size() +
                ", time=" + processingTime +
                '}';
    }
}
