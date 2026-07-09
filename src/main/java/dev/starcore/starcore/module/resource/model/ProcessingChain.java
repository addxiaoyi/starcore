package dev.starcore.starcore.module.resource.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 加工产业链
 * 描述从原材料到成品的完整加工链
 */
public final class ProcessingChain {
    private final String chainId;
    private final String chainName;
    private final List<String> recipeSequence;
    private final String finalProductId;
    private final int totalSteps;

    public ProcessingChain(String chainId, String chainName, List<String> recipeSequence,
                           String finalProductId) {
        this.chainId = Objects.requireNonNull(chainId, "chainId");
        this.chainName = Objects.requireNonNull(chainName, "chainName");
        this.recipeSequence = Collections.unmodifiableList(new ArrayList<>(recipeSequence));
        this.finalProductId = Objects.requireNonNull(finalProductId, "finalProductId");
        this.totalSteps = recipeSequence.size();
    }

    /**
     * 获取产业链ID
     */
    public String chainId() {
        return chainId;
    }

    /**
     * 获取产业链名称
     */
    public String chainName() {
        return chainName;
    }

    /**
     * 获取配方序列
     */
    public List<String> recipeSequence() {
        return recipeSequence;
    }

    /**
     * 获取最终产品ID
     */
    public String finalProductId() {
        return finalProductId;
    }

    /**
     * 获取总步骤数
     */
    public int totalSteps() {
        return totalSteps;
    }

    /**
     * 获取指定步骤的配方ID
     */
    public String getRecipeAtStep(int step) {
        if (step < 0 || step >= recipeSequence.size()) {
            return null;
        }
        return recipeSequence.get(step);
    }

    /**
     * 获取配方在产业链中的步骤
     */
    public int getStepOfRecipe(String recipeId) {
        return recipeSequence.indexOf(recipeId);
    }

    /**
     * 检查配方是否属于该产业链
     */
    public boolean containsRecipe(String recipeId) {
        return recipeSequence.contains(recipeId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessingChain that = (ProcessingChain) o;
        return chainId.equals(that.chainId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chainId);
    }

    @Override
    public String toString() {
        return "ProcessingChain{" +
                "chainId='" + chainId + '\'' +
                ", chainName='" + chainName + '\'' +
                ", steps=" + totalSteps +
                ", finalProduct='" + finalProductId + '\'' +
                '}';
    }
}
