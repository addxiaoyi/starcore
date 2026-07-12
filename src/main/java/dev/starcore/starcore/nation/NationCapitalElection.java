package dev.starcore.starcore.nation;

import dev.starcore.starcore.territory.MultiChunkTerritory;

import java.util.*;

/**
 * Nation自动首都选举系统
 * 基于Towny的自动首都选举机制
 *
 * 当首都离开Nation或被占领时自动选举新首都
 */
public class NationCapitalElection {

    /**
     * 选举新首都
     *
     * 选举标准（优先级）：
     * 1. 人口最多的城市
     * 2. 领地最大的城市
     * 3. 创建时间最早的城市
     */
    public static MultiChunkTerritory electNewCapital(Collection<MultiChunkTerritory> territories) {
        if (territories == null || territories.isEmpty()) {
            return null;
        }

        // 只有一个Territory，直接返回
        if (territories.size() == 1) {
            return territories.iterator().next();
        }

        // 计算每个Territory的得分
        List<TerritoryScore> scores = new ArrayList<>();

        for (MultiChunkTerritory territory : territories) {
            int score = calculateTerritoryScore(territory);
            scores.add(new TerritoryScore(territory, score));
        }

        // 按得分降序排序，同分时以创建时间早的优先（确定性 tiebreaker）
        scores.sort(Comparator.comparingInt(TerritoryScore::score).reversed()
                .thenComparingLong(ts -> ts.territory().getCreatedTime()));

        MultiChunkTerritory elected = scores.get(0).territory();
        // ✅ audit A-003: 调用方应使用 CapitalElectedEvent 广播首都变更消息
        //   参见 nation/event/CapitalElectedEvent.java
        return elected;
    }

    /**
     * 计算Territory得分
     */
    private static int calculateTerritoryScore(MultiChunkTerritory territory) {
        int score = 0;

        // 1. Chunk数量（每个Chunk 10分）
        score += territory.getChunkCount() * 10;

        // 2. 领地等级（每级20分）
        score += territory.getLevel() * 20;

        // 3. 领地类型加成
        score += switch (territory.getType()) {
            case CAPITAL -> 100;  // 已是首都类型，优先保留
            case MILITARY -> 50;
            case COMMERCIAL -> 30;
            case RESIDENTIAL -> 20;
            default -> 0;
        };

        // 4. 连通性加成
        if (territory.isConnected()) {
            score += 50;
        }

        // 5. 存在时间加成（每天1分，最多100分）
        long days = (System.currentTimeMillis() - territory.getCreatedTime()) / (1000 * 60 * 60 * 24);
        score += Math.min(days, 100);

        return score;
    }

    /**
     * 检查Nation是否需要选举首都
     */
    public static boolean needsCapitalElection(MultiChunkTerritory currentCapital,
                                               Collection<MultiChunkTerritory> allTerritories) {
        // 没有首都，需要选举
        if (currentCapital == null) {
            return !allTerritories.isEmpty();
        }

        // 首都不在Territory列表中，需要选举
        if (!allTerritories.contains(currentCapital)) {
            return !allTerritories.isEmpty();
        }

        return false;
    }

    /**
     * 执行首都选举并返回结果
     */
    public static CapitalElectionResult performElection(
        MultiChunkTerritory currentCapital,
        Collection<MultiChunkTerritory> allTerritories
    ) {
        // 检查是否需要选举
        if (!needsCapitalElection(currentCapital, allTerritories)) {
            return new CapitalElectionResult(
                false,
                currentCapital,
                null,
                "无需选举"
            );
        }

        // 如果没有Territory，Nation灭亡
        if (allTerritories.isEmpty()) {
            return new CapitalElectionResult(
                true,
                null,
                null,
                "Nation已灭亡（无领地）"
            );
        }

        // 选举新首都
        MultiChunkTerritory newCapital = electNewCapital(allTerritories);

        // 设置新首都类型，并把旧首都类型改回非 CAPITAL 避免同时存在两个 CAPITAL
        if (newCapital != null) {
            newCapital.setType(dev.starcore.starcore.territory.TerritoryType.CAPITAL);
            if (currentCapital != null && !currentCapital.getId().equals(newCapital.getId())) {
                // TODO audit A-002: 旧首都恢复为 RESIDENTIAL，若需保留其它类型语义需调用方接管
                currentCapital.setType(dev.starcore.starcore.territory.TerritoryType.RESIDENTIAL);
            }
        }

        return new CapitalElectionResult(
            true,
            newCapital,
            currentCapital,
            newCapital != null ? "选举成功" : "选举失败"
        );
    }

    /**
     * Territory得分记录
     */
    private record TerritoryScore(MultiChunkTerritory territory, int score) {}

    /**
     * 首都选举结果
     */
    public record CapitalElectionResult(
        boolean electionHappened,
        MultiChunkTerritory newCapital,
        MultiChunkTerritory oldCapital,
        String reason
    ) {
        public boolean isNationDestroyed() {
            return electionHappened && newCapital == null;
        }

        public boolean capitalChanged() {
            return electionHappened &&
                   newCapital != null &&
                   (oldCapital == null || !newCapital.getId().equals(oldCapital.getId()));
        }

        @Override
        public String toString() {
            if (isNationDestroyed()) {
                return "Nation灭亡";
            }

            if (capitalChanged()) {
                String oldName = oldCapital != null ? oldCapital.getName() : "无";
                return String.format(
                    "首都变更: %s -> %s",
                    oldName,
                    newCapital.getName()
                );
            }

            return "首都未变更";
        }
    }
}
