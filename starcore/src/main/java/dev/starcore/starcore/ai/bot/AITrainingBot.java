package dev.starcore.starcore.ai.bot;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * AI训练机器人
 * 模拟真实玩家进行PvP训练
 */
public final class AITrainingBot {
    private final UUID botId;
    private final String botName;
    private final BotDifficulty difficulty;
    private final Player owner;

    // AI状态
    private Location currentLocation;
    private Player target;
    private BotState state;
    private double health;
    private final double maxHealth;

    // 巡逻数据
    private List<Location> patrolPoints;
    private int currentPatrolIndex;

    // 战斗数据
    private int killCount;
    private int deathCount;
    private long sessionStartTime;

    public AITrainingBot(String botName, BotDifficulty difficulty, Player owner) {
        this.botId = UUID.randomUUID();
        this.botName = botName;
        this.difficulty = difficulty;
        this.owner = owner;
        this.state = BotState.IDLE;
        this.sessionStartTime = System.currentTimeMillis();
        this.maxHealth = 20.0;
        this.health = maxHealth;
        this.patrolPoints = new ArrayList<>();
        this.currentPatrolIndex = 0;
    }

    /**
     * AI决策系统
     */
    public void tick() {
        if (target == null || !target.isOnline()) {
            findTarget();
            return;
        }

        switch (state) {
            case IDLE -> transitionToEngagement();
            case ENGAGING -> performCombat();
            case FLEEING -> performFlee();
            case PATROLLING -> performPatrol();
        }
    }

    /**
     * 寻找目标
     */
    private void findTarget() {
        // 优先攻击训练的玩家
        if (owner != null && owner.isOnline()) {
            this.target = owner;
            this.state = BotState.ENGAGING;
        }
    }

    /**
     * 进入战斗状态
     */
    private void transitionToEngagement() {
        if (target != null) {
            state = BotState.ENGAGING;
        }
    }

    /**
     * 执行战斗
     */
    private void performCombat() {
        if (target == null) {
            state = BotState.IDLE;
            return;
        }

        // 根据难度调整行为
        double distance = currentLocation.distance(target.getLocation());

        if (distance > 10) {
            // 追击
            moveTowards(target.getLocation());
        } else if (distance < 3) {
            // 近战攻击
            performMeleeAttack();

            // 根据难度决定是否躲避
            if (shouldDodge()) {
                performDodge();
            }
        } else {
            // 中距离战术
            if (difficulty.reactionTime() < 200) {
                // 高难度：组合技
                performComboAttack();
            } else {
                // 低难度：简单攻击
                performSimpleAttack();
            }
        }

        // 低血量逃跑
        if (shouldFlee()) {
            state = BotState.FLEEING;
        }
    }

    /**
     * 执行逃跑
     */
    private void performFlee() {
        if (target != null) {
            Location fleeTarget = calculateFleeLocation();
            moveTowards(fleeTarget);

            // 距离足够后恢复
            if (currentLocation.distance(target.getLocation()) > 15) {
                state = BotState.ENGAGING;
            }
        }
    }

    /**
     * 执行巡逻
     */
    private void performPatrol() {
        if (patrolPoints.isEmpty()) {
            // 如果没有巡逻点，生成默认巡逻路径
            generateDefaultPatrolPoints();
        }

        if (patrolPoints.isEmpty()) {
            state = BotState.IDLE;
            return;
        }

        // 获取当前巡逻点
        Location targetPoint = patrolPoints.get(currentPatrolIndex);

        // 移动到巡逻点
        if (currentLocation.distance(targetPoint) < 2.0) {
            // 到达巡逻点，前往下一个
            currentPatrolIndex = (currentPatrolIndex + 1) % patrolPoints.size();
        } else {
            moveTowards(targetPoint);
        }

        // 巡逻时检测附近的敌人
        if (target != null && target.isOnline() &&
            currentLocation.distance(target.getLocation()) < 15) {
            state = BotState.ENGAGING;
        }
    }

    /**
     * 生成默认巡逻点
     */
    private void generateDefaultPatrolPoints() {
        if (currentLocation == null) return;

        // 在当前位置周围生成4个巡逻点（正方形）
        int radius = 10;
        patrolPoints.clear();

        patrolPoints.add(currentLocation.clone().add(radius, 0, radius));
        patrolPoints.add(currentLocation.clone().add(-radius, 0, radius));
        patrolPoints.add(currentLocation.clone().add(-radius, 0, -radius));
        patrolPoints.add(currentLocation.clone().add(radius, 0, -radius));
    }

    /**
     * 设置巡逻点
     */
    public void setPatrolPoints(List<Location> points) {
        this.patrolPoints = new ArrayList<>(points);
        this.currentPatrolIndex = 0;
    }

    /**
     * 移动到目标位置（A*寻路）
     */
    private void moveTowards(Location target) {
        if (currentLocation == null || target == null) return;

        // 使用简化的A*算法寻路
        List<Location> path = findPath(currentLocation, target);

        if (!path.isEmpty()) {
            // 移动到路径的下一个点
            Location nextStep = path.get(0);
            this.currentLocation = nextStep;
        } else {
            // 如果找不到路径，直接朝目标移动
            this.currentLocation = target;
        }
    }

    /**
     * A*寻路算法
     * 简化版本，适用于Minecraft环境
     */
    private List<Location> findPath(Location start, Location goal) {
        // 如果距离很近，直接移动
        if (start.distance(goal) < 3.0) {
            return List.of(goal);
        }

        // A*算法数据结构
        PriorityQueue<AStarNode> openSet = new PriorityQueue<>(
            Comparator.comparingDouble(n -> n.fScore)
        );
        Set<Location> closedSet = new HashSet<>();
        Map<Location, Location> cameFrom = new HashMap<>();
        Map<Location, Double> gScore = new HashMap<>();

        // 初始化
        Location startLoc = start.clone();
        gScore.put(startLoc, 0.0);
        openSet.add(new AStarNode(startLoc, 0.0, heuristic(startLoc, goal)));

        int maxIterations = 100; // 防止无限循环
        int iterations = 0;

        while (!openSet.isEmpty() && iterations < maxIterations) {
            iterations++;

            AStarNode current = openSet.poll();
            Location currentLoc = current.location;

            // 到达目标
            if (currentLoc.distance(goal) < 2.0) {
                return reconstructPath(cameFrom, currentLoc);
            }

            closedSet.add(currentLoc);

            // 检查邻居节点（8个方向）
            for (Location neighbor : getNeighbors(currentLoc)) {
                if (closedSet.contains(neighbor)) {
                    continue;
                }

                // 如果方块不可通行，跳过
                if (!isWalkable(neighbor)) {
                    continue;
                }

                double tentativeGScore = gScore.getOrDefault(currentLoc, Double.MAX_VALUE) + 1.0;

                if (tentativeGScore < gScore.getOrDefault(neighbor, Double.MAX_VALUE)) {
                    cameFrom.put(neighbor, currentLoc);
                    gScore.put(neighbor, tentativeGScore);
                    double fScore = tentativeGScore + heuristic(neighbor, goal);

                    openSet.add(new AStarNode(neighbor, tentativeGScore, fScore));
                }
            }
        }

        // 找不到路径，返回直线方向的第一步
        return List.of(getDirectionStep(start, goal));
    }

    /**
     * 启发式函数（曼哈顿距离）
     */
    private double heuristic(Location a, Location b) {
        return Math.abs(a.getX() - b.getX()) +
               Math.abs(a.getY() - b.getY()) +
               Math.abs(a.getZ() - b.getZ());
    }

    /**
     * 获取邻居节点
     */
    private List<Location> getNeighbors(Location loc) {
        List<Location> neighbors = new ArrayList<>();

        // 8个方向（东、西、南、北、东南、东北、西南、西北）
        int[][] directions = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        for (int[] dir : directions) {
            Location neighbor = loc.clone().add(dir[0], 0, dir[1]);
            neighbors.add(neighbor);

            // 如果需要上下移动
            if (!isWalkable(neighbor)) {
                Location up = neighbor.clone().add(0, 1, 0);
                if (isWalkable(up)) {
                    neighbors.add(up);
                }

                Location down = neighbor.clone().add(0, -1, 0);
                if (isWalkable(down)) {
                    neighbors.add(down);
                }
            }
        }

        return neighbors;
    }

    /**
     * 检查位置是否可通行
     */
    private boolean isWalkable(Location loc) {
        if (loc.getWorld() == null) return false;

        Block block = loc.getBlock();
        Block above = loc.clone().add(0, 1, 0).getBlock();

        // 脚下方块必须是固体，上方必须是空气
        return block.getType().isSolid() &&
               (above.getType().isAir() || above.getType() == Material.WATER);
    }

    /**
     * 重建路径
     */
    private List<Location> reconstructPath(Map<Location, Location> cameFrom, Location current) {
        List<Location> path = new ArrayList<>();
        path.add(current);

        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.add(0, current);
        }

        // 移除第一个点（当前位置）
        if (!path.isEmpty()) {
            path.remove(0);
        }

        return path;
    }

    /**
     * 获取朝向目标的直线一步
     */
    private Location getDirectionStep(Location from, Location to) {
        Location step = from.clone();

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();

        // 归一化方向
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length > 0) {
            dx /= length;
            dz /= length;
        }

        step.add(dx, 0, dz);
        return step;
    }

    /**
     * A*节点
     */
    private static class AStarNode {
        Location location;
        double gScore;
        double fScore;

        AStarNode(Location location, double gScore, double fScore) {
            this.location = location;
            this.gScore = gScore;
            this.fScore = fScore;
        }
    }

    /**
     * 执行近战攻击
     */
    private void performMeleeAttack() {
        if (target == null) return;

        // 根据难度计算命中率
        double hitChance = difficulty.accuracy() / 100.0;
        if (ThreadLocalRandom.current().nextDouble() < hitChance) {
            // 模拟攻击
            target.damage(4.0 + ThreadLocalRandom.current().nextDouble() * 2);
        }
    }

    /**
     * 执行简单攻击
     */
    private void performSimpleAttack() {
        performMeleeAttack();
    }

    /**
     * 执行组合攻击
     */
    private void performComboAttack() {
        // 高难度的连续攻击
        performMeleeAttack();

        if (ThreadLocalRandom.current().nextDouble() < 0.3) {
            // 30% 概率追加攻击
            performMeleeAttack();
        }
    }

    /**
     * 执行闪避
     */
    private void performDodge() {
        // 侧向移动
        Location dodgeTarget = currentLocation.clone();
        dodgeTarget.add(
            (ThreadLocalRandom.current().nextDouble() - 0.5) * 2,
            0,
            (ThreadLocalRandom.current().nextDouble() - 0.5) * 2
        );
        moveTowards(dodgeTarget);
    }

    /**
     * 是否应该闪避
     */
    private boolean shouldDodge() {
        double dodgeChance = switch (difficulty) {
            case EASY -> 0.1;
            case NORMAL -> 0.3;
            case HARD -> 0.5;
            case EXPERT -> 0.7;
        };
        return ThreadLocalRandom.current().nextDouble() < dodgeChance;
    }

    /**
     * 是否应该逃跑
     */
    private boolean shouldFlee() {
        // 检查血量
        double healthPercentage = (health / maxHealth) * 100;

        // 根据难度决定逃跑阈值
        double fleeThreshold = switch (difficulty) {
            case EASY -> 50.0;    // 血量低于50%就逃跑
            case NORMAL -> 30.0;  // 血量低于30%就逃跑
            case HARD -> 20.0;    // 血量低于20%就逃跑
            case EXPERT -> 10.0;  // 血量低于10%才逃跑
        };

        return healthPercentage < fleeThreshold;
    }

    /**
     * 受到伤害
     */
    public void takeDamage(double damage) {
        this.health = Math.max(0, this.health - damage);

        // 血量为0时死亡
        if (this.health <= 0) {
            recordDeath();
            respawn();
        }
    }

    /**
     * 恢复血量
     */
    public void heal(double amount) {
        this.health = Math.min(maxHealth, this.health + amount);
    }

    /**
     * 重生
     */
    private void respawn() {
        this.health = maxHealth;
        this.state = BotState.IDLE;
    }

    /**
     * 获取当前血量
     */
    public double getHealth() {
        return health;
    }

    /**
     * 获取血量百分比
     */
    public double getHealthPercentage() {
        return (health / maxHealth) * 100;
    }

    /**
     * 计算逃跑位置
     */
    private Location calculateFleeLocation() {
        if (target == null) return currentLocation;

        // 朝向远离目标的方向
        Location flee = currentLocation.clone();
        flee.subtract(target.getLocation().toVector());
        flee.add(currentLocation.toVector().multiply(2));

        return flee;
    }

    /**
     * 记录击杀
     */
    public void recordKill() {
        killCount++;
    }

    /**
     * 记录死亡
     */
    public void recordDeath() {
        deathCount++;
    }

    /**
     * 生成战斗报告
     */
    public BotReport generateReport() {
        long duration = System.currentTimeMillis() - sessionStartTime;
        return new BotReport(
            botId,
            botName,
            difficulty,
            killCount,
            deathCount,
            duration
        );
    }

    // Getters
    public UUID getBotId() { return botId; }
    public String getBotName() { return botName; }
    public BotDifficulty getDifficulty() { return difficulty; }
    public Player getOwner() { return owner; }
    public BotState getState() { return state; }

    /**
     * 机器人难度
     */
    public enum BotDifficulty {
        EASY(500, 40, "简单"),
        NORMAL(300, 60, "普通"),
        HARD(150, 80, "困难"),
        EXPERT(50, 95, "专家");

        private final int reactionTime;
        private final int accuracy;
        private final String displayName;

        BotDifficulty(int reactionTime, int accuracy, String displayName) {
            this.reactionTime = reactionTime;
            this.accuracy = accuracy;
            this.displayName = displayName;
        }

        public int reactionTime() { return reactionTime; }
        public int accuracy() { return accuracy; }
        public String displayName() { return displayName; }
    }

    /**
     * 机器人状态
     */
    public enum BotState {
        IDLE,       // 待机
        ENGAGING,   // 战斗中
        FLEEING,    // 逃跑
        PATROLLING  // 巡逻
    }

    /**
     * 战斗报告
     */
    public record BotReport(
        UUID botId,
        String botName,
        BotDifficulty difficulty,
        int kills,
        int deaths,
        long durationMs
    ) {
        public double getKDRatio() {
            return deaths == 0 ? kills : (double) kills / deaths;
        }
    }
}
