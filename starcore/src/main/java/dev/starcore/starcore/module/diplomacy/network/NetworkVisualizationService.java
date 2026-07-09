package dev.starcore.starcore.module.diplomacy.network;

import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelationSnapshot;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.diplomacy.alliance.AllianceService;

import java.util.*;
import java.util.logging.Logger;

/**
 * 关系网络可视化服务
 *
 * 生成外交关系网络图并提供布局算法
 */
public class NetworkVisualizationService {

    private static final Logger logger = Logger.getLogger(NetworkVisualizationService.class.getName());
    private final NationService nationService;
    private final DiplomacyService diplomacyService;
    private final AllianceService allianceService;

    // 可视化配置
    private static final double NODE_SPACING = 3.0;  // 节点间距
    private static final double FORCE_STRENGTH = 0.1; // 力导向强度

    public NetworkVisualizationService(
            NationService nationService,
            DiplomacyService diplomacyService,
            AllianceService allianceService
    ) {
        this.nationService = nationService;
        this.diplomacyService = diplomacyService;
        this.allianceService = allianceService;
    }

    /**
     * 构建当前外交关系网络图
     */
    public DiplomacyGraph buildGraph() {
        DiplomacyGraph graph = new DiplomacyGraph();

        // 添加所有国家节点
        Collection<Nation> nations = nationService.nations();
        for (Nation nation : nations) {
            graph.addNode(nation.id(), nation.name());
        }

        // 添加外交关系边
        for (Nation nation : nations) {
            NationId nationId = nation.id();

            // 获取该国家的所有外交关系
            Collection<DiplomacyRelationSnapshot> relations = diplomacyService.relationsOf(nationId);

            for (DiplomacyRelationSnapshot snapshot : relations) {
                NationId otherId = snapshot.target();

                // 只添加单向边（避免重复）
                if (nationId.toString().compareTo(otherId.toString()) < 0) {
                    graph.addEdge(nationId, otherId, snapshot.relation());
                }
            }

            // 添加联盟关系（来自 AllianceService）
            try {
                Collection<NationId> allies = allianceService.getAllies(nationId);
                for (NationId allyId : allies) {
                    // 检查是否已经有边
                    if (graph.getRelation(nationId, allyId).isEmpty()) {
                        graph.addEdge(nationId, allyId, DiplomacyRelation.ALLIED);
                    }
                }
            } catch (Exception e) {
                logger.warning("AllianceService unavailable: " + e.getMessage());
            }
        }

        // 计算布局
        calculateLayout(graph);

        // 计算影响力分数
        calculateInfluenceScores(graph);

        return graph;
    }

    /**
     * 构建玩家国家的局部关系网络（包含二级关系）
     */
    public DiplomacyGraph buildLocalGraph(NationId centerNationId, int depth) {
        DiplomacyGraph graph = new DiplomacyGraph();

        Optional<Nation> centerNation = nationService.nationById(centerNationId);
        if (centerNation.isEmpty()) {
            return graph;
        }

        // 添加中心节点
        graph.addNode(centerNationId, centerNation.get().name());

        // BFS 获取相关国家
        Set<NationId> visited = new HashSet<>();
        visited.add(centerNationId);
        Queue<NationId> queue = new LinkedList<>();
        queue.add(centerNationId);

        int currentDepth = 0;
        while (!queue.isEmpty() && currentDepth < depth) {
            int levelSize = queue.size();
            for (int i = 0; i < levelSize; i++) {
                NationId current = queue.poll();

                // 获取直接关系
                Collection<DiplomacyRelationSnapshot> relations = diplomacyService.relationsOf(current);
                for (DiplomacyRelationSnapshot snapshot : relations) {
                    NationId otherId = snapshot.target();

                    if (!visited.contains(otherId)) {
                        visited.add(otherId);
                        Optional<Nation> otherNation = nationService.nationById(otherId);
                        otherNation.ifPresent(n -> graph.addNode(otherId, n.name()));
                        graph.addEdge(current, otherId, snapshot.relation());
                        queue.add(otherId);
                    } else {
                        // 已访问，但可能没有边
                        if (graph.getRelation(current, otherId).isEmpty()) {
                            graph.addEdge(current, otherId, snapshot.relation());
                        }
                    }
                }

                // 添加联盟关系
                try {
                    Collection<NationId> allies = allianceService.getAllies(current);
                    for (NationId allyId : allies) {
                        if (!visited.contains(allyId)) {
                            visited.add(allyId);
                            Optional<Nation> ally = nationService.nationById(allyId);
                            ally.ifPresent(n -> graph.addNode(allyId, n.name()));
                        }
                        if (graph.getRelation(current, allyId).isEmpty()) {
                            graph.addEdge(current, allyId, DiplomacyRelation.ALLIED);
                        }
                        if (!visited.contains(allyId)) {
                            queue.add(allyId);
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Failed to get allies for nation " + current + ": " + e.getMessage());
                }
                        // 静默跳过，保持数据兼容
            }
            currentDepth++;
        }

        // 计算局部布局（以中心节点为中心）
        calculateRadialLayout(graph, centerNationId);

        return graph;
    }

    /**
     * 力导向布局算法
     */
    private void calculateLayout(DiplomacyGraph graph) {
        Collection<DiplomacyGraph.NationNode> nodes = graph.getNodes();
        int nodeCount = nodes.size();

        if (nodeCount == 0) return;

        // 初始化位置（圆形布局）
        double radius = Math.max(5, nodeCount * 0.8);
        int i = 0;
        for (DiplomacyGraph.NationNode node : nodes) {
            double angle = 2 * Math.PI * i / nodeCount;
            node.setPosition(radius * Math.cos(angle), radius * Math.sin(angle));
            i++;
        }

        // 力导向迭代
        int iterations = 50;
        for (int iter = 0; iter < iterations; iter++) {
            // 计算节点受力
            Map<DiplomacyGraph.NationNode, double[]> forces = new HashMap<>();
            for (DiplomacyGraph.NationNode node : nodes) {
                forces.put(node, new double[]{0, 0});
            }

            // 排斥力（所有节点互斥）
            for (DiplomacyGraph.NationNode node1 : nodes) {
                for (DiplomacyGraph.NationNode node2 : nodes) {
                    if (node1.equals(node2)) continue;

                    double dx = node2.x() - node1.x();
                    double dy = node2.y() - node1.y();
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist < 0.1) dist = 0.1;

                    double force = -NODE_SPACING * 2 / (dist * dist);
                    double fx = force * dx / dist;
                    double fy = force * dy / dist;

                    forces.get(node1)[0] += fx;
                    forces.get(node1)[1] += fy;
                }
            }

            // 吸引力（连接的节点互相吸引）
            for (DiplomacyGraph.RelationEdge edge : graph.getEdges()) {
                DiplomacyGraph.NationNode node1 = graph.getNode(edge.from());
                DiplomacyGraph.NationNode node2 = graph.getNode(edge.to());
                if (node1 == null || node2 == null) continue;

                double dx = node2.x() - node1.x();
                double dy = node2.y() - node1.y();
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < 0.1) dist = 0.1;

                // 盟国强吸引，敌国弱吸引
                double attractionMultiplier = edge.relation() == DiplomacyRelation.ALLIED ? 1.5 :
                                             edge.relation() == DiplomacyRelation.WAR ? 0.3 : 0.8;

                double force = attractionMultiplier * FORCE_STRENGTH * dist;
                double fx = force * dx / dist;
                double fy = force * dy / dist;

                forces.get(node1)[0] += fx;
                forces.get(node1)[1] += fy;
                forces.get(node2)[0] -= fx;
                forces.get(node2)[1] -= fy;
            }

            // 应用力
            double cooling = 1.0 - (double) iter / iterations; // 逐渐冷却
            for (DiplomacyGraph.NationNode node : nodes) {
                double[] force = forces.get(node);
                double maxMove = NODE_SPACING * cooling;
                double dx = Math.max(-maxMove, Math.min(maxMove, force[0]));
                double dy = Math.max(-maxMove, Math.min(maxMove, force[1]));
                node.setPosition(node.x() + dx, node.y() + dy);
            }
        }
    }

    /**
     * 径向布局（以某个国家为中心）
     */
    private void calculateRadialLayout(DiplomacyGraph graph, NationId centerId) {
        DiplomacyGraph.NationNode center = graph.getNode(centerId);
        if (center == null) {
            calculateLayout(graph);
            return;
        }

        // 中心节点在原点
        center.setPosition(0, 0);

        // 获取其他节点
        List<DiplomacyGraph.NationNode> otherNodes = graph.getNodes().stream()
            .filter(n -> !n.id().equals(centerId))
            .toList();

        if (otherNodes.isEmpty()) return;

        // 按关系分组
        Map<DiplomacyRelation, List<DiplomacyGraph.NationNode>> grouped = new HashMap<>();
        for (DiplomacyGraph.NationNode node : otherNodes) {
            Optional<DiplomacyRelation> rel = graph.getRelation(centerId, node.id());
            DiplomacyRelation relation = rel.orElse(DiplomacyRelation.NEUTRAL);
            grouped.computeIfAbsent(relation, k -> new ArrayList<>()).add(node);
        }

        // 径向排列
        double[] angles = { -Math.PI/2, Math.PI/2, 0, Math.PI }; // 上、下、右、左
        String[] angleNames = { "NORTH", "SOUTH", "EAST", "WEST" };

        int idx = 0;
        for (Map.Entry<DiplomacyRelation, List<DiplomacyGraph.NationNode>> entry : grouped.entrySet()) {
            DiplomacyRelation rel = entry.getKey();
            List<DiplomacyGraph.NationNode> nodes = entry.getValue();

            // 确定角度
            double baseAngle = switch (rel) {
                case ALLIED -> -Math.PI / 2;  // 顶部（盟国）
                case WAR -> Math.PI / 2;      // 底部（敌国）
                default -> idx * Math.PI / 2; // 其他均匀分布
            };

            double radius = switch (rel) {
                case ALLIED -> 2.5;   // 盟国较近
                case WAR -> 2.5;      // 敌国较近
                case NEUTRAL -> 4.0;   // 中立较远
                default -> 3.5;
            };

            for (int i = 0; i < nodes.size(); i++) {
                double angle = baseAngle + (i - (nodes.size() - 1) / 2.0) * 0.5;
                nodes.get(i).setPosition(radius * Math.cos(angle), radius * Math.sin(angle));
            }

            idx++;
        }
    }

    /**
     * 计算影响力分数
     */
    private void calculateInfluenceScores(DiplomacyGraph graph) {
        for (DiplomacyGraph.NationNode node : graph.getNodes()) {
            int influence = 0;

            // 盟国加成
            influence += graph.getAllyCount(node.id()) * 10;

            // 敌国减分（较少）
            influence -= graph.getEnemyCount(node.id()) * 5;

            // 基础分
            influence += 50;

            // 获取联盟服务的信息（如果有）
            try {
                int allyCount = allianceService.getAllies(node.id()).size();
                influence += allyCount * 15;
            } catch (Exception e) {
                logger.warning("Failed to calculate alliance influence for nation " + node.id() + ": " + e.getMessage());
            }
                        // 静默跳过，保持数据兼容

            node.setInfluence(Math.max(0, influence));
        }
    }

    /**
     * 生成 ASCII 艺术关系网络（用于控制台显示）
     */
    public String generateAsciiGraph(DiplomacyGraph graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("§6=== 外交关系网络 ===§r\n\n");

        if (graph.getNodeCount() == 0) {
            return sb.append("§7暂无国家数据§r\n").toString();
        }

        // 按影响力排序
        List<DiplomacyGraph.NationNode> sortedNodes = graph.getNodes().stream()
            .sorted(Comparator.comparingInt(DiplomacyGraph.NationNode::influence).reversed())
            .toList();

        // 显示图例
        sb.append("§7图例: §a— 联盟 §c— 战争 §7— 中立§r\n");
        sb.append("=".repeat(40)).append("\n\n");

        // 打印每个国家及其关系
        for (DiplomacyGraph.NationNode node : sortedNodes) {
            sb.append("§e").append(node.name()).append(" §7[").append(node.influence()).append("]§r\n");

            List<DiplomacyGraph.RelationEdge> edges = graph.getEdgesOf(node.id());
            for (DiplomacyGraph.RelationEdge edge : edges) {
                NationId otherId = edge.other(node.id());
                String otherName = graph.getNationName(otherId);

                String relSymbol = switch (edge.relation()) {
                    case ALLIED -> "§a  + 联盟";
                    case WAR -> "§c  x 战争";
                    case CEASE_FIRE -> "§e  ~ 停战";
                    case HOSTILE -> "§c  ! 敌对";
                    case FRIENDLY -> "§a  ~ 友好";
                    default -> "§7  - 中立";
                };

                sb.append(relSymbol).append("§r: §f").append(otherName).append("\n");
            }

            sb.append("\n");
        }

        // 统计信息
        sb.append("=".repeat(40)).append("\n");
        sb.append("§7国家总数: §f").append(graph.getNodeCount()).append("\n");
        sb.append("§7关系总数: §f").append(graph.getEdgeCount()).append("\n");

        return sb.toString();
    }

    /**
     * 生成简单的文本关系图
     */
    public String generateSimpleTextGraph(NationId centerNationId) {
        StringBuilder sb = new StringBuilder();
        sb.append("§6=== 关系网络 ===§r\n\n");

        Optional<Nation> centerOpt = nationService.nationById(centerNationId);
        if (centerOpt.isEmpty()) {
            return sb.append("§c找不到指定国家§r\n").toString();
        }

        Nation center = centerOpt.get();
        sb.append("§b中心: §f").append(center.name()).append("\n");
        sb.append("-".repeat(30)).append("\n\n");

        // 获取直接关系
        Collection<DiplomacyRelationSnapshot> relations = diplomacyService.relationsOf(centerNationId);

        // 分类
        List<DiplomacyRelationSnapshot> allies = new ArrayList<>();
        List<DiplomacyRelationSnapshot> enemies = new ArrayList<>();
        List<DiplomacyRelationSnapshot> others = new ArrayList<>();

        for (DiplomacyRelationSnapshot snapshot : relations) {
            switch (snapshot.relation()) {
                case ALLIED -> allies.add(snapshot);
                case WAR -> enemies.add(snapshot);
                default -> others.add(snapshot);
            }
        }

        // 盟国
        if (!allies.isEmpty()) {
            sb.append("§a盟国 (").append(allies.size()).append(")§r\n");
            for (DiplomacyRelationSnapshot s : allies) {
                String name = nationService.nationById(s.target())
                    .map(Nation::name).orElse("未知");
                sb.append("  §a+ ").append(name).append("\n");
            }
            sb.append("\n");
        }

        // 敌国
        if (!enemies.isEmpty()) {
            sb.append("§c敌国 (").append(enemies.size()).append(")§r\n");
            for (DiplomacyRelationSnapshot s : enemies) {
                String name = nationService.nationById(s.target())
                    .map(Nation::name).orElse("未知");
                sb.append("  §cx ").append(name).append("\n");
            }
            sb.append("\n");
        }

        // 其他关系
        if (!others.isEmpty()) {
            sb.append("§7其他 (").append(others.size()).append(")§r\n");
            for (DiplomacyRelationSnapshot s : others) {
                String name = nationService.nationById(s.target())
                    .map(Nation::name).orElse("未知");
                sb.append("  §7- ").append(name).append(" (").append(s.relation().displayName()).append(")\n");
            }
            sb.append("\n");
        }

        if (relations.isEmpty()) {
            sb.append("§7暂无外交关系§r\n");
        }

        return sb.toString();
    }
}