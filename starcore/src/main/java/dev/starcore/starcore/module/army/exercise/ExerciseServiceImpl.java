package dev.starcore.starcore.module.army.exercise;

import java.util.concurrent.ThreadLocalRandom;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.*;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 战争演习服务实现
 * 提供军事演习的完整功能实现
 */
public final class ExerciseServiceImpl implements ExerciseService {

    private static final String PERSISTENCE_NAMESPACE = "exercise";
    private static final String EXERCISE_STATE_FILE = "exercises.dat";

    private final Plugin plugin;
    private final NationService nationService;
    private final MessageService messages;
    private final ExerciseConfig config;

    // 所有演习
    private final Map<UUID, Exercise> exercises = new ConcurrentHashMap<>();
    // 活跃演习ID列表（用于快速查询）
    private final List<UUID> activeExerciseIds = new CopyOnWriteArrayList<>();
    // 战斗冷却记录
    private final Map<String, Long> battleCooldowns = new ConcurrentHashMap<>();
    // 演习结果记录
    private final List<ExerciseResult> completedResults = new CopyOnWriteArrayList<>();

    // 调度任务ID
    private int cleanupTaskId = -1;

    public ExerciseServiceImpl(
        Plugin plugin,
        NationService nationService,
        MessageService messages,
        ExerciseConfig config
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.messages = messages;
        this.config = config;

        loadExercises();
        startScheduledTasks();
    }

    // ==================== ExerciseService Implementation ====================

    @Override
    public Exercise createExercise(UUID organizerId, String name, ExerciseType exerciseType) {
        // 验证组织者
        if (!isValidOrganizer(organizerId)) {
            throw new IllegalStateException("exercise.error.invalid-organizer");
        }

        // 检查是否有未完成的组织者演习
        List<Exercise> organizerExercises = getExercisesByOrganizer(organizerId);
        long activeCount = organizerExercises.stream()
            .filter(e -> !e.state().isTerminal())
            .count();
        if (activeCount >= 3) {
            throw new IllegalStateException("exercise.error.max-organizer-exercises");
        }

        // 创建演习
        Exercise exercise = Exercise.create(name, organizerId, exerciseType, config);
        exercises.put(exercise.id(), exercise);

        persistExercise(exercise);
        return exercise;
    }

    @Override
    public boolean startExercise(UUID exerciseId) {
        Exercise exercise = exercises.get(exerciseId);
        if (exercise == null) {
            return false;
        }

        if (!exercise.state().canStart()) {
            return false;
        }

        if (!exercise.hasEnoughParticipants()) {
            return false;
        }

        // 根据演习类型设置阵营
        if (exercise.type().requiresTwoSides()) {
            assignSides(exercise);
        }

        exercise.setState(ExerciseState.IN_PROGRESS);
        activeExerciseIds.add(exerciseId);
        persistExercise(exercise);

        return true;
    }

    @Override
    public ExerciseResult endExercise(UUID exerciseId, String reason) {
        Exercise exercise = exercises.get(exerciseId);
        if (exercise == null) {
            return null;
        }

        // 生成结果
        ExerciseResult result = buildResult(exercise, reason);
        completedResults.add(result);

        // 更新状态
        exercise.setState(ExerciseState.COMPLETED);
        activeExerciseIds.remove(exerciseId);
        persistExercise(exercise);

        // 应用奖励/惩罚
        if (config.rewardsEnabled()) {
            applyRewards(result);
        }

        return result;
    }

    @Override
    public boolean joinExercise(UUID exerciseId, UUID nationId, int soldierCount) {
        Exercise exercise = exercises.get(exerciseId);
        if (exercise == null) {
            return false;
        }

        if (!exercise.state().canJoin()) {
            return false;
        }

        if (exercise.isFull()) {
            return false;
        }

        if (exercise.getParticipant(nationId).isPresent()) {
            return false;
        }

        // 验证士兵数量
        if (soldierCount < config.minSoldiersPerParticipant()) {
            return false;
        }
        if (soldierCount > config.maxSoldiersPerParticipant()) {
            return false;
        }

        // 验证国家
        Nation nation = nationService.nationById(new NationId(nationId)).orElse(null);
        if (nation == null) {
            return false;
        }

        // 确定角色
        ExerciseRole role = determineRole(exercise, nation);

        // 添加参与者
        ExerciseParticipant participant = ExerciseParticipant.create(
            nationId,
            nation.name(),
            soldierCount,
            role
        );
        exercise.addParticipant(participant);

        // 如果状态还是 PREPARING，改为 WAITING
        if (exercise.state() == ExerciseState.PREPARING) {
            exercise.setState(ExerciseState.WAITING);
        }

        persistExercise(exercise);
        return true;
    }

    @Override
    public boolean leaveExercise(UUID exerciseId, UUID nationId) {
        Exercise exercise = exercises.get(exerciseId);
        if (exercise == null) {
            return false;
        }

        if (!exercise.state().canJoin()) {
            return false;
        }

        boolean removed = exercise.removeParticipant(nationId);
        if (removed) {
            // 如果参与者太少，改为 PREPARING
            if (exercise.participantCount() < config.minParticipants()) {
                exercise.setState(ExerciseState.PREPARING);
            }
            persistExercise(exercise);
        }
        return removed;
    }

    @Override
    public Optional<Exercise> getExercise(UUID exerciseId) {
        return Optional.ofNullable(exercises.get(exerciseId));
    }

    @Override
    public List<Exercise> getExercisesByNation(UUID nationId) {
        return exercises.values().stream()
            .filter(e -> e.getNationIds().contains(nationId))
            .toList();
    }

    @Override
    public List<Exercise> getActiveExercises() {
        return activeExerciseIds.stream()
            .map(exercises::get)
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public List<Exercise> getPendingExercises() {
        return exercises.values().stream()
            .filter(e -> e.state() == ExerciseState.PREPARING || e.state() == ExerciseState.WAITING)
            .toList();
    }

    @Override
    public List<Exercise> getExercisesByOrganizer(UUID organizerId) {
        return exercises.values().stream()
            .filter(e -> e.organizerId().equals(organizerId))
            .toList();
    }

    @Override
    public void updateExerciseState(UUID exerciseId, ExerciseState newState) {
        Exercise exercise = exercises.get(exerciseId);
        if (exercise == null) {
            return;
        }

        exercise.setState(newState);
        if (newState.isTerminal()) {
            activeExerciseIds.remove(exerciseId);
        } else if (newState.isActive()) {
            activeExerciseIds.add(exerciseId);
        }

        persistExercise(exercise);
    }

    @Override
    public ExerciseBattleResult processBattle(UUID exerciseId, UUID attackerNationId, UUID defenderNationId) {
        Exercise exercise = exercises.get(exerciseId);
        if (exercise == null || !exercise.state().isActive()) {
            return null;
        }

        // 检查冷却
        String cooldownKey = cooldownKey(attackerNationId, defenderNationId);
        long lastBattle = battleCooldowns.getOrDefault(cooldownKey, 0L);
        if (System.currentTimeMillis() - lastBattle < 60000) { // 1分钟冷却
            return null;
        }

        Optional<ExerciseParticipant> attackerOpt = exercise.getParticipant(attackerNationId);
        Optional<ExerciseParticipant> defenderOpt = exercise.getParticipant(defenderNationId);
        if (attackerOpt.isEmpty() || defenderOpt.isEmpty()) {
            return null;
        }

        ExerciseParticipant attacker = attackerOpt.get();
        ExerciseParticipant defender = defenderOpt.get();

        // 检查是否可以战斗
        if (!attacker.role().canFightWith(defender.role())) {
            return null;
        }

        // 计算战斗
        double attackerPower = attacker.combatPower();
        double defenderPower = defender.combatPower();
        double totalPower = attackerPower + defenderPower;

        if (totalPower <= 0) {
            return null;
        }

        // 随机性因素
        double attackerChance = attackerPower / totalPower;
        double roll = ThreadLocalRandom.current().nextDouble();

        // 计算伤亡
        double baseDamage = 0.2; // 基础伤害比例
        double attackerLossRate = baseDamage * (1 - attackerChance) * (1 + ThreadLocalRandom.current().nextDouble() * 0.3);
        double defenderLossRate = baseDamage * attackerChance * (1 + ThreadLocalRandom.current().nextDouble() * 0.3);

        int attackerLosses = (int) (attacker.effectiveSoldiers() * attackerLossRate);
        int defenderLosses = (int) (defender.effectiveSoldiers() * defenderLossRate);

        // 应用伤亡
        attacker.addCasualties(attackerLosses);
        defender.addCasualties(defenderLosses);
        attacker.addKills(defenderLosses);
        defender.addKills(attackerLosses);

        // 确定胜负
        UUID winnerId = null;
        String winnerName = null;
        double attackerMoraleChange = 0;
        double defenderMoraleChange = 0;

        if (roll < attackerChance * 0.6) {
            // 攻击方胜利
            winnerId = attackerNationId;
            winnerName = attacker.nationName();
            attackerMoraleChange = config.moraleGainPerVictory();
            defenderMoraleChange = -config.moralePenaltyForDefeat();
        } else if (roll > 0.4 + attackerChance * 0.6) {
            // 防守方胜利
            winnerId = defenderNationId;
            winnerName = defender.nationName();
            attackerMoraleChange = -config.moralePenaltyForDefeat();
            defenderMoraleChange = config.moraleGainPerVictory();
        }

        // 应用士气变化
        attacker.changeMorale(attackerMoraleChange);
        defender.changeMorale(defenderMoraleChange);

        // 更新演习统计
        exercise.incrementTotalBattles();
        if (winnerId != null) {
            if (winnerId.equals(exercise.redSideId().orElse(null))) {
                exercise.incrementRedSideWins();
            } else if (winnerId.equals(exercise.blueSideId().orElse(null))) {
                exercise.incrementBlueSideWins();
            }
        }

        // 创建战斗结果
        ExerciseBattleResult result = ExerciseBattleResult.create(
            exerciseId,
            attackerNationId,
            attacker.nationName(),
            defenderNationId,
            defender.nationName(),
            attackerLosses,
            defenderLosses,
            winnerId,
            winnerName,
            defenderLosses,
            attackerLosses,
            attackerMoraleChange,
            defenderMoraleChange
        );

        // 设置冷却
        battleCooldowns.put(cooldownKey, System.currentTimeMillis());

        // 检查是否应该结束演习
        checkExerciseTermination(exercise);

        persistExercise(exercise);
        return result;
    }

    @Override
    public String checkJoinEligibility(UUID exerciseId, UUID nationId) {
        Exercise exercise = exercises.get(exerciseId);
        if (exercise == null) {
            return "exercise.error.not-found";
        }

        if (!exercise.state().canJoin()) {
            return "exercise.error.cannot-join";
        }

        if (exercise.isFull()) {
            return "exercise.error.full";
        }

        if (exercise.getParticipant(nationId).isPresent()) {
            return "exercise.error.already-joined";
        }

        Nation nation = nationService.nationById(new NationId(nationId)).orElse(null);
        if (nation == null) {
            return "exercise.error.nation-not-found";
        }

        return "exercise.success.can-join";
    }

    @Override
    public ExerciseConfig getConfig() {
        return config;
    }

    // ==================== Private Methods ====================

    private boolean isValidOrganizer(UUID organizerId) {
        return nationService.nationById(new NationId(organizerId)).isPresent();
    }

    private void assignSides(Exercise exercise) {
        List<ExerciseParticipant> participants = exercise.participants();
        if (participants.size() < 2) {
            return;
        }

        // 随机分配阵营
        Collections.shuffle(participants);
        ExerciseParticipant first = participants.get(0);
        ExerciseParticipant second = participants.get(1);

        first.setRole(ExerciseRole.ATTACKER);
        second.setRole(ExerciseRole.DEFENDER);

        exercise.setRedSideId(first.nationId());
        exercise.setBlueSideId(second.nationId());
    }

    private ExerciseRole determineRole(Exercise exercise, Nation nation) {
        if (!exercise.type().requiresTwoSides()) {
            return ExerciseRole.FREE;
        }

        // 攻防演习时，第一个加入的是进攻方
        int idx = exercise.participantCount();
        return idx % 2 == 0 ? ExerciseRole.ATTACKER : ExerciseRole.DEFENDER;
    }

    private void checkExerciseTermination(Exercise exercise) {
        // 检查是否有足够的参与者
        long activeParticipants = exercise.participants().stream()
            .filter(ExerciseParticipant::isActive)
            .count();

        if (activeParticipants < config.minParticipants()) {
            exercise.setState(ExerciseState.COMPLETED);
            activeExerciseIds.remove(exercise.id());
            return;
        }

        // 检查是否有阵营全灭
        if (exercise.type().requiresTwoSides()) {
            Optional<UUID> redId = exercise.redSideId();
            Optional<UUID> blueId = exercise.blueSideId();

            if (redId.isPresent()) {
                exercise.getParticipant(redId.get()).ifPresent(red -> {
                    if (red.effectiveSoldiers() <= 0) {
                        exercise.setState(ExerciseState.COMPLETED);
                        activeExerciseIds.remove(exercise.id());
                    }
                });
            }

            if (blueId.isPresent()) {
                exercise.getParticipant(blueId.get()).ifPresent(blue -> {
                    if (blue.effectiveSoldiers() <= 0) {
                        exercise.setState(ExerciseState.COMPLETED);
                        activeExerciseIds.remove(exercise.id());
                    }
                });
            }
        }
    }

    private ExerciseResult buildResult(Exercise exercise, String reason) {
        List<ExerciseParticipant> participants = exercise.participants();

        // 计算排名
        List<ExerciseParticipant> sorted = participants.stream()
            .sorted((a, b) -> {
                int rank = Integer.compare(b.kills(), a.kills());
                if (rank != 0) return rank;
                return Double.compare(b.combatPower(), a.combatPower());
            })
            .toList();

        // 确定胜利者
        UUID winnerId = null;
        String winnerName = null;
        if (exercise.type().requiresTwoSides()) {
            Optional<UUID> redId = exercise.redSideId();
            Optional<UUID> blueId = exercise.blueSideId();

            if (redId.isPresent() && blueId.isPresent()) {
                Optional<ExerciseParticipant> redOpt = exercise.getParticipant(redId.get());
                Optional<ExerciseParticipant> blueOpt = exercise.getParticipant(blueId.get());

                if (redOpt.isPresent() && blueOpt.isPresent()) {
                    ExerciseParticipant red = redOpt.get();
                    ExerciseParticipant blue = blueOpt.get();
                    double redScore = red.combatPower() + red.kills() * 10;
                    double blueScore = blue.combatPower() + blue.kills() * 10;

                    if (redScore > blueScore) {
                        winnerId = redId.get();
                        winnerName = red.nationName();
                    } else if (blueScore > redScore) {
                        winnerId = blueId.get();
                        winnerName = blue.nationName();
                    }
                }
            }
        } else if (!sorted.isEmpty()) {
            winnerId = sorted.get(0).nationId();
            winnerName = sorted.get(0).nationName();
        }

        // 构建参与者结果
        List<ExerciseResult.ParticipantResult> results = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            ExerciseParticipant p = sorted.get(i);
            results.add(new ExerciseResult.ParticipantResult(
                p.nationId(),
                p.nationName(),
                p.role(),
                p.soldierCount(),
                p.casualties(),
                p.kills(),
                0, // battles
                0, // wins (simplified)
                0, // losses
                p.morale(),
                config.experienceGainPerParticipant(),
                i + 1
            ));
        }

        return ExerciseResult.builder()
            .exerciseId(exercise.id())
            .exerciseName(exercise.name())
            .type(exercise.type())
            .endState(exercise.state())
            .endReason(reason)
            .startedAt(exercise.startedAt() != null ? exercise.startedAt() : Instant.now())
            .endedAt(Instant.now())
            .totalBattles(exercise.totalBattles())
            .addParticipantResults(results)  // Use the new method for multiple results
            .winner(winnerId, winnerName)
            .build();
    }

    private void applyRewards(ExerciseResult result) {
        for (ExerciseResult.ParticipantResult pr : result.participantResults()) {
            // 添加国家经验
            nationService.addExperience(new NationId(pr.nationId()), (long) pr.experienceGained());
        }
    }

    private String cooldownKey(UUID a, UUID b) {
        return a.toString() + "_" + b.toString();
    }

    // ==================== Persistence ====================

    private void loadExercises() {
        // 从持久化加载演习数据（简化版实现）
        // 实际实现应从数据库或文件加载
    }

    private void persistExercise(Exercise exercise) {
        // 保存到持久化存储
    }

    private void saveAllExercises() {
        for (Exercise exercise : exercises.values()) {
            persistExercise(exercise);
        }
    }

    // ==================== Scheduled Tasks ====================

    private void startScheduledTasks() {
        // 每分钟检查超时演习
        cleanupTaskId = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::checkTimeouts,
            20L * 60,
            20L * 60
        ).getTaskId();
    }

    private void checkTimeouts() {
        Instant now = Instant.now();
        for (UUID exerciseId : activeExerciseIds) {
            Exercise exercise = exercises.get(exerciseId);
            if (exercise == null) continue;

            if (exercise.startedAt() != null) {
                long minutesElapsed = java.time.Duration.between(exercise.startedAt(), now).toMinutes();
                if (minutesElapsed >= config.maxDurationMinutes()) {
                    endExercise(exerciseId, "时间到自动结束");
                }
            }
        }

        // 清理过期冷却
        battleCooldowns.entrySet().removeIf(entry ->
            System.currentTimeMillis() - entry.getValue() > 300000
        );
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        if (cleanupTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(cleanupTaskId);
            cleanupTaskId = -1;
        }
        saveAllExercises();
    }

    /**
     * 获取最近完成的结果
     */
    public List<ExerciseResult> getRecentResults(int limit) {
        if (completedResults.size() <= limit) {
            return List.copyOf(completedResults);
        }
        return completedResults.subList(completedResults.size() - limit, completedResults.size());
    }
}
