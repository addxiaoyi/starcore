package dev.starcore.starcore.storage;

import dev.starcore.starcore.util.ColorCodes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 升级配方
 * 定义仓库升级所需的条件和材料
 */
public class UpgradeRecipe {
    private final int fromLevel;
    private final int toLevel;
    private final BigDecimal moneyCost;
    private final Map<String, Integer> materialRequirements;
    private final long upgradeTimeSeconds;
    private final List<String> requirements;

    /**
     * 构造函数
     * @param fromLevel 起始等级
     * @param toLevel 目标等级
     * @param moneyCost 金钱消耗
     * @param materialRequirements 材料需求（材料类型 -> 数量）
     * @param upgradeTimeSeconds 升级时间（秒）
     */
    public UpgradeRecipe(int fromLevel, int toLevel, BigDecimal moneyCost,
                         Map<String, Integer> materialRequirements, long upgradeTimeSeconds) {
        this.fromLevel = fromLevel;
        this.toLevel = toLevel;
        this.moneyCost = moneyCost != null ? moneyCost : BigDecimal.ZERO;
        this.materialRequirements = materialRequirements != null ? new HashMap<>(materialRequirements) : new HashMap<>();
        this.upgradeTimeSeconds = upgradeTimeSeconds;
        this.requirements = new ArrayList<>();
    }

    /**
     * 从WarehouseLevel创建升级配方
     * @param currentLevel 当前等级配置
     * @param nextLevel 下一等级配置
     * @return 升级配方
     */
    public static UpgradeRecipe fromLevels(WarehouseLevel currentLevel, WarehouseLevel nextLevel) {
        return new UpgradeRecipe(
                currentLevel.getLevel(),
                nextLevel.getLevel(),
                nextLevel.getUpgradeCost(),
                nextLevel.getRequiredMaterials(),
                nextLevel.getUpgradeTimeSeconds()
        );
    }

    /**
     * 获取起始等级
     * @return 从哪个等级升级
     */
    public int getFromLevel() {
        return fromLevel;
    }

    /**
     * 获取目标等级
     * @return 升级到哪个等级
     */
    public int getToLevel() {
        return toLevel;
    }

    /**
     * 获取金钱消耗
     * @return 所需金钱
     */
    public BigDecimal getMoneyCost() {
        return moneyCost;
    }

    /**
     * 获取材料需求
     * @return 材料类型到数量的映射
     */
    public Map<String, Integer> getMaterialRequirements() {
        return new HashMap<>(materialRequirements);
    }

    /**
     * 获取升级时间
     * @return 升级所需时间（秒）
     */
    public long getUpgradeTimeSeconds() {
        return upgradeTimeSeconds;
    }

    /**
     * 获取升级时间（格式化）
     * @return 格式化的时间字符串（如"5分钟"）
     */
    public String getFormattedUpgradeTime() {
        if (upgradeTimeSeconds < 60) {
            return upgradeTimeSeconds + "秒";
        } else if (upgradeTimeSeconds < 3600) {
            return (upgradeTimeSeconds / 60) + "分钟";
        } else {
            long hours = upgradeTimeSeconds / 3600;
            long minutes = (upgradeTimeSeconds % 3600) / 60;
            if (minutes > 0) {
                return hours + "小时" + minutes + "分钟";
            }
            return hours + "小时";
        }
    }

    /**
     * 获取额外需求
     * @return 额外需求列表（如"需要国家等级3"）
     */
    public List<String> getRequirements() {
        return new ArrayList<>(requirements);
    }

    /**
     * 添加额外需求
     * @param requirement 需求描述
     */
    public void addRequirement(String requirement) {
        if (requirement != null && !requirement.isEmpty()) {
            this.requirements.add(requirement);
        }
    }

    /**
     * 检查是否有金钱消耗
     * @return true如果需要金钱
     */
    public boolean hasMoneyCost() {
        return moneyCost.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 检查是否有材料需求
     * @return true如果需要材料
     */
    public boolean hasMaterialRequirements() {
        return !materialRequirements.isEmpty();
    }

    /**
     * 检查是否需要时间
     * @return true如果需要等待时间
     */
    public boolean hasUpgradeTime() {
        return upgradeTimeSeconds > 0;
    }

    /**
     * 检查是否有额外需求
     * @return true如果有额外需求
     */
    public boolean hasRequirements() {
        return !requirements.isEmpty();
    }

    /**
     * 获取特定材料的需求数量
     * @param materialType 材料类型
     * @return 需要的数量，如果不需要该材料则返回0
     */
    public int getMaterialAmount(String materialType) {
        return materialRequirements.getOrDefault(materialType, 0);
    }

    /**
     * 计算总材料种类数
     * @return 需要多少种不同的材料
     */
    public int getMaterialTypeCount() {
        return materialRequirements.size();
    }

    /**
     * 获取配方摘要
     * @return 配方的简要描述
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("等级 ").append(fromLevel).append(" -> ").append(toLevel);

        List<String> costs = new ArrayList<>();
        if (hasMoneyCost()) {
            costs.add(moneyCost + "金币");
        }
        if (hasMaterialRequirements()) {
            costs.add(materialRequirements.size() + "种材料");
        }
        if (hasUpgradeTime()) {
            costs.add(getFormattedUpgradeTime());
        }

        if (!costs.isEmpty()) {
            sb.append(" (").append(String.join(", ", costs)).append(")");
        }

        return sb.toString();
    }

    /**
     * 获取详细描述
     * @return 配方的详细描述列表
     */
    public List<String> getDetailedDescription() {
        List<String> description = new ArrayList<>();
        description.add("§6升级配方: §e等级 " + fromLevel + " -> " + toLevel);
        description.add("");

        if (hasMoneyCost()) {
            description.add("§a金钱: §f" + moneyCost);
        }

        if (hasMaterialRequirements()) {
            description.add("§a材料需求:");
            for (Map.Entry<String, Integer> entry : materialRequirements.entrySet()) {
                description.add("  §7- §f" + entry.getKey() + " x" + entry.getValue());
            }
        }

        if (hasUpgradeTime()) {
            description.add("§a升级时间: §f" + getFormattedUpgradeTime());
        }

        if (hasRequirements()) {
            description.add("§a额外要求:");
            for (String req : requirements) {
                description.add("  §7- §f" + req);
            }
        }

        return description;
    }

    @Override
    public String toString() {
        return "UpgradeRecipe{" +
                "from=" + fromLevel +
                ", to=" + toLevel +
                ", money=" + moneyCost +
                ", materials=" + materialRequirements.size() +
                ", time=" + upgradeTimeSeconds + "s" +
                ", requirements=" + requirements.size() +
                '}';
    }
}
