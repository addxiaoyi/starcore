package dev.starcore.starcore.module.diplomacy.network;

import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.nation.model.NationId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;

/**
 * 外交关系网络图
 *
 * 提供国家间外交关系的图形化数据表示
 */
public class DiplomacyGraph {

    // 节点 - 代表国家
    private final Map<NationId, NationNode> nodes = new ConcurrentHashMap<>();

    // 边 - 代表外交关系
    private final Set<RelationEdge> edges = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // 国家名称缓存
    private final Map<NationId, String> nationNames = new ConcurrentHashMap<>();

    /**
     * 添加国家节点
     */
    public void addNode(NationId nationId, String name) {
        nodes.putIfAbsent(nationId, new NationNode(nationId, name));
        nationNames.put(nationId, name);
    }

    /**
     * 添加外交关系边
     */
    public void addEdge(NationId from, NationId to, DiplomacyRelation relation) {
        // 确保节点存在
        addNodeIfAbsent(from);
        addNodeIfAbsent(to);

        edges.add(new RelationEdge(from, to, relation));
    }

    /**
     * 添加关系（如果节点不存在则自动创建）
     */
    private void addNodeIfAbsent(NationId nationId) {
        if (!nodes.containsKey(nationId)) {
            String name = nationNames.getOrDefault(nationId, "未知国家");
            nodes.putIfAbsent(nationId, new NationNode(nationId, name));
        }
    }

    /**
     * 获取所有国家节点
     */
    public Collection<NationNode> getNodes() {
        return nodes.values();
    }

    /**
     * 获取所有关系边
     */
    public Set<RelationEdge> getEdges() {
        return edges;
    }

    /**
     * 获取国家节点
     */
    public NationNode getNode(NationId nationId) {
        return nodes.get(nationId);
    }

    /**
     * 获取两个国家间的关系
     */
    public Optional<DiplomacyRelation> getRelation(NationId from, NationId to) {
        return edges.stream()
            .filter(e -> e.matches(from, to))
            .findFirst()
            .map(RelationEdge::relation);
    }

    /**
     * 获取国家的所有关系边
     */
    public List<RelationEdge> getEdgesOf(NationId nationId) {
        return edges.stream()
            .filter(e -> e.involves(nationId))
            .toList();
    }

    /**
     * 获取国家的盟国数量
     */
    public int getAllyCount(NationId nationId) {
        return (int) edges.stream()
            .filter(e -> e.involves(nationId) && e.relation() == DiplomacyRelation.ALLIED)
            .count();
    }

    /**
     * 获取国家的敌国数量
     */
    public int getEnemyCount(NationId nationId) {
        return (int) edges.stream()
            .filter(e -> e.involves(nationId) && e.relation() == DiplomacyRelation.WAR)
            .count();
    }

    /**
     * 获取参与最多外交关系的国家
     */
    public Optional<NationNode> getMostConnectedNation() {
        return nodes.values().stream()
            .max(Comparator.comparingInt(n -> getEdgesOf(n.id()).size()));
    }

    /**
     * 获取总节点数
     */
    public int getNodeCount() {
        return nodes.size();
    }

    /**
     * 获取总边数
     */
    public int getEdgeCount() {
        return edges.size();
    }

    /**
     * 获取国家名称
     */
    public String getNationName(NationId nationId) {
        return nationNames.getOrDefault(nationId, "未知");
    }

    // ==================== 数据模型 ====================

    /**
     * 国家节点
     */
    public static class NationNode {
        private final NationId id;
        private final String name;

        // 可视化布局位置（可选）
        private double x, y;

        // 影响力分数（用于布局）
        private int influence;

        public NationNode(NationId id, String name) {
            this.id = id;
            this.name = name;
        }

        public NationId id() { return id; }
        public String name() { return name; }
        public double x() { return x; }
        public double y() { return y; }
        public int influence() { return influence; }

        public void setPosition(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public void setInfluence(int influence) {
            this.influence = influence;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NationNode that = (NationNode) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    /**
     * 关系边
     */
    public static class RelationEdge {
        private final NationId from;
        private final NationId to;
        private final DiplomacyRelation relation;

        public RelationEdge(NationId from, NationId to, DiplomacyRelation relation) {
            this.from = from;
            this.to = to;
            this.relation = relation;
        }

        public NationId from() { return from; }
        public NationId to() { return to; }
        public DiplomacyRelation relation() { return relation; }

        public boolean involves(NationId nationId) {
            return from.equals(nationId) || to.equals(nationId);
        }

        public boolean matches(NationId a, NationId b) {
            return (from.equals(a) && to.equals(b)) || (from.equals(b) && to.equals(a));
        }

        public NationId other(NationId nationId) {
            if (from.equals(nationId)) return to;
            if (to.equals(nationId)) return from;
            throw new IllegalArgumentException("Nation ID not part of this edge");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RelationEdge that = (RelationEdge) o;
            return Objects.equals(from, that.from) && Objects.equals(to, that.to);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to);
        }
    }
}