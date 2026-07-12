package dev.starcore.starcore.module.war;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.NationService;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 战争统计服务
 */
public class WarStatsService {

    private final NationService nationService;
    private final Map<String, WarStats> statsCache = new HashMap<>();

    public WarStatsService(NationService nationService) {
        this.nationService = nationService;
    }

    /**
     * 获取战争统计信息
     */
    public Optional<WarStats> getWarStats(NationId nation1, NationId nation2) {
        String key = createKey(nation1, nation2);
        return Optional.ofNullable(statsCache.get(key));
    }

    /**
     * 获取或创建战争统计
     */
    public WarStats getOrCreateWarStats(WarSnapshot war, NationId requester) {
        String key = createKey(war.left(), war.right());
        return statsCache.computeIfAbsent(key, k -> {
            NationId ally = war.left().equals(requester) ? war.left() : war.right();
            NationId enemy = war.left().equals(requester) ? war.right() : war.left();
            return new WarStats(war, ally, enemy, nationService);
        });
    }

    /**
     * 更新击杀数
     */
    public void recordKill(NationId killer, NationId victim) {
        String key = createKey(killer, victim);
        WarStats stats = statsCache.computeIfAbsent(key, k -> new WarStats(killer, victim, nationService));
        stats.recordKill(killer);
    }

    /**
     * 更新占领数
     */
    public void recordCapture(NationId capturer, NationId lost) {
        String key = createKey(capturer, lost);
        WarStats stats = statsCache.computeIfAbsent(key, k -> new WarStats(capturer, lost, nationService));
        stats.recordCapture(capturer);
    }

    /**
     * 更新战斗次数
     */
    public void recordBattle(NationId nation1, NationId nation2) {
        String key = createKey(nation1, nation2);
        WarStats stats = statsCache.computeIfAbsent(key, k -> new WarStats(nation1, nation2, nationService));
        stats.recordBattle();
    }

    private String createKey(NationId n1, NationId n2) {
        return n1.value().toString() + ":" + n2.value().toString();
    }

    /**
     * 战争统计数据
     */
    public static class WarStats {
        private final NationId allyId;
        private final NationId enemyId;
        private final String allyName;
        private final String enemyName;
        private final Instant declaredAt;
        private final Instant endedAt;

        private int allyKills;
        private int enemyKills;
        private int allyCaptures;
        private int enemyCaptures;
        private int battleCount;

        public WarStats(NationId allyId, NationId enemyId, NationService nationService) {
            this.allyId = allyId;
            this.enemyId = enemyId;
            this.allyName = nationService.nationById(allyId)
                .map(n -> n.name())
                .orElse("Unknown");
            this.enemyName = nationService.nationById(enemyId)
                .map(n -> n.name())
                .orElse("Unknown");
            this.declaredAt = Instant.now();
            this.endedAt = null;
        }

        public WarStats(WarSnapshot war, NationId requester, NationId other, NationService nationService) {
            this.allyId = requester;
            this.enemyId = other;
            this.allyName = nationService.nationById(allyId)
                .map(n -> n.name())
                .orElse("Unknown");
            this.enemyName = nationService.nationById(enemyId)
                .map(n -> n.name())
                .orElse("Unknown");
            this.declaredAt = war.declaredAt();
            this.endedAt = war.endedAt();
        }

        public void recordKill(NationId killer) {
            if (killer.equals(allyId)) {
                allyKills++;
            } else {
                enemyKills++;
            }
        }

        public void recordCapture(NationId capturer) {
            if (capturer.equals(allyId)) {
                allyCaptures++;
            } else {
                enemyCaptures++;
            }
        }

        public void recordBattle() {
            battleCount++;
        }

        public String getAllyName() { return allyName; }
        public String getEnemyName() { return enemyName; }
        public int getAllyKills() { return allyKills; }
        public int getEnemyKills() { return enemyKills; }
        public int getAllyCaptures() { return allyCaptures; }
        public int getEnemyCaptures() { return enemyCaptures; }
        public int getBattleCount() { return battleCount; }

        public Duration getDuration() {
            Instant end = endedAt != null ? endedAt : Instant.now();
            return Duration.between(declaredAt, end);
        }

        public String getStatus() {
            return endedAt != null ? "已结束" : "进行中";
        }
    }
}
