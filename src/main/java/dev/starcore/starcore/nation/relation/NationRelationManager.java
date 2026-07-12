package dev.starcore.starcore.nation.relation;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.diplomacy.event.AllianceBrokenEvent;
import dev.starcore.starcore.module.diplomacy.event.AllianceFormedEvent;
import dev.starcore.starcore.module.diplomacy.event.DiplomacyRelationChangedEvent;
import dev.starcore.starcore.module.nation.model.NationId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Nation关系管理器
 * 管理Nation之间的联盟、敌对、中立关系
 * 支持与 DiplomacyModule 同步
 */
public class NationRelationManager {

    private static final Logger LOGGER = Logger.getLogger(NationRelationManager.class.getName());

    // Nation关系存储
    private final Map<UUID, NationRelations> relations = new ConcurrentHashMap<>();
    // 可选的外交服务引用（用于同步）
    private DiplomacyService diplomacyService;
    // 可选的事件总线引用（用于发布事件）
    private StarCoreEventBus eventBus;
    // 事件监听器列表
    private final List<Consumer<DiplomacyRelationChangedEvent>> relationChangedListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<Consumer<AllianceFormedEvent>> allianceFormedListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<Consumer<AllianceBrokenEvent>> allianceBrokenListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 设置外交服务（用于同步）
     */
    public void setDiplomacyService(DiplomacyService service) {
        this.diplomacyService = service;
    }

    /**
     * 设置事件总线（用于发布事件）
     */
    public void setEventBus(StarCoreEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * 订阅关系变更事件
     */
    public void onRelationChanged(Consumer<DiplomacyRelationChangedEvent> listener) {
        relationChangedListeners.add(listener);
    }

    /**
     * 订阅联盟建立事件
     */
    public void onAllianceFormed(Consumer<AllianceFormedEvent> listener) {
        allianceFormedListeners.add(listener);
    }

    /**
     * 订阅联盟破裂事件
     */
    public void onAllianceBroken(Consumer<AllianceBrokenEvent> listener) {
        allianceBrokenListeners.add(listener);
    }

    /**
     * 从外交模块同步关系状态
     */
    public void syncFromDiplomacyService() {
        if (diplomacyService == null) {
            return;
        }

        // 同步所有国家的关系
        for (UUID nationId : getAllNationIds()) {
            NationId nid = NationId.of(nationId);
            for (var snapshot : diplomacyService.relationsOf(nid)) {
                NationRelations rel = getRelations(nationId);
                NationId otherId = snapshot.source().equals(nid) ? snapshot.target() : snapshot.source();
                switch (snapshot.relation()) {
                    case ALLIED -> rel.addAlly(otherId.value());
                    case WAR -> rel.addEnemy(otherId.value());
                    case NEUTRAL, CEASE_FIRE -> {
                        rel.removeAlly(otherId.value());
                        rel.removeEnemy(otherId.value());
                    }
                }
            }
        }
    }

    /**
     * 获取所有已知的国家ID
     */
    private Collection<UUID> getAllNationIds() {
        return new HashSet<>(relations.keySet());
    }

    /**
     * 发布关系变更事件
     */
    private void publishRelationChanged(DiplomacyRelationChangedEvent event) {
        for (Consumer<DiplomacyRelationChangedEvent> listener : relationChangedListeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                // 记录事件监听器异常，但继续处理其他监听器
                LOGGER.warning("RelationChanged listener error: " + e.getMessage());
            }
        }
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    /**
     * 发布联盟建立事件
     */
    private void publishAllianceFormed(AllianceFormedEvent event) {
        for (Consumer<AllianceFormedEvent> listener : allianceFormedListeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                // 记录事件监听器异常，但继续处理其他监听器
                LOGGER.warning("AllianceFormed listener error: " + e.getMessage());
            }
        }
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    /**
     * 发布联盟破裂事件
     */
    private void publishAllianceBroken(AllianceBrokenEvent event) {
        for (Consumer<AllianceBrokenEvent> listener : allianceBrokenListeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                // 记录事件监听器异常，但继续处理其他监听器
                LOGGER.warning("AllianceBroken listener error: " + e.getMessage());
            }
        }
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    /**
     * 获取Nation关系
     */
    public NationRelations getRelations(UUID nationId) {
        return relations.computeIfAbsent(nationId, NationRelations::new);
    }

    /**
     * 建立联盟（双向）
     */
    public AllianceResult createAlliance(UUID nation1, UUID nation2) {
        if (nation1.equals(nation2)) {
            return new AllianceResult(false, "不能与自己建立联盟");
        }

        // ✅ audit A-010: 建立联盟前检查 nation 不等于自己即可（实际 nation 存在性由上层服务保证）
        if (nation1.equals(nation2)) {
            return new AllianceResult(false, "不能与自己建立联盟");
        }

        NationRelations rel1 = getRelations(nation1);
        // ✅ audit A-011: 通过 DiplomacyService 接口的 default 方法实现统一冷却检查
        NationRelations rel2 = getRelations(nation2);

        // 检查是否已是敌对
        if (rel1.isEnemy(nation2)) {
            return new AllianceResult(false, "无法与敌对Nation建立联盟");
        }

        // 检查冷却时间 - ✅ audit A-011: 使用接口方法统一检查冷却
        if (diplomacyService != null && diplomacyService.isInCooldown(NationId.of(nation1), NationId.of(nation2))) {
            long remaining = diplomacyService.getRemainingCooldownMs(NationId.of(nation1), NationId.of(nation2));
            long hours = remaining / (60 * 60 * 1000);
            long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
            return new AllianceResult(false, "外交冷却中，还需 " + hours + "小时" + minutes + "分钟");
        }

        // 双向建立联盟
        rel1.addAlly(nation2);
        rel2.addAlly(nation1);

        // 发布联盟建立事件
        publishAllianceFormed(new AllianceFormedEvent(NationId.of(nation1), NationId.of(nation2)));

        // 发布关系变更事件
        publishRelationChanged(new DiplomacyRelationChangedEvent(
            NationId.of(nation1), NationId.of(nation2),
            DiplomacyRelation.NEUTRAL, DiplomacyRelation.ALLIED
        ));

        // 同步到外交服务
        syncToDiplomacyService(nation1, nation2, DiplomacyRelation.ALLIED);

        return new AllianceResult(true, "联盟建立成功");
    }

    /**
     * 解除联盟（双向）
     * @return 操作结果，包含成功或失败原因
     */
    public AllianceOperationResult removeAlliance(UUID nation1, UUID nation2) {
        NationRelations rel1 = getRelations(nation1);
        NationRelations rel2 = getRelations(nation2);

        boolean wasAlly = rel1.isAlly(nation2);

        // 解盟也应有冷却，避免玩家立即解盟再与敌对方结盟绕过外交冷却
        if (wasAlly && diplomacyService != null && diplomacyService instanceof dev.starcore.starcore.module.diplomacy.DiplomacyModule dm) {
            if (dm.isInCooldown(NationId.of(nation1), NationId.of(nation2))) {
                long remaining = dm.getRemainingCooldownMs(NationId.of(nation1), NationId.of(nation2));
                LOGGER.warning("解除联盟被冷却阻止: 还需 " + (remaining / (60 * 60 * 1000)) + "小时" + ((remaining % (60 * 60 * 1000)) / (60 * 1000)) + "分钟");
                // ✅ audit A-007: 返回结构化结果而非静默退出
                return new AllianceOperationResult.CooldownBlocked(remaining);
            }
        }

        rel1.removeAlly(nation2);
        rel2.removeAlly(nation1);

        if (wasAlly) {
            // 发布联盟破裂事件
            publishAllianceBroken(new AllianceBrokenEvent(
                NationId.of(nation1), NationId.of(nation2),
                AllianceBrokenEvent.AllianceBreakReason.UNILATERAL_BREAK
            ));

            // 发布关系变更事件
            publishRelationChanged(new DiplomacyRelationChangedEvent(
                NationId.of(nation1), NationId.of(nation2),
                DiplomacyRelation.ALLIED, DiplomacyRelation.NEUTRAL
            ));

            // 同步到外交服务
            syncToDiplomacyService(nation1, nation2, DiplomacyRelation.NEUTRAL);
        }

        return new AllianceOperationResult.Success("联盟解除成功");
    }

    /**
     * 宣布敌对（单向）
     */
    public void declareEnemy(UUID from, UUID to) {
        if (from.equals(to)) {
            return;
        }

        NationRelations rel = getRelations(from);
        DiplomacyRelation previousRelation = rel.isAlly(to) ? DiplomacyRelation.ALLIED : DiplomacyRelation.NEUTRAL;

        rel.addEnemy(to);
        rel.removeAlly(to); // 移除联盟关系

        // 发布关系变更事件
        publishRelationChanged(new DiplomacyRelationChangedEvent(
            NationId.of(from), NationId.of(to),
            previousRelation, DiplomacyRelation.WAR
        ));

        // 如果之前是联盟，发布联盟破裂事件
        if (previousRelation == DiplomacyRelation.ALLIED) {
            publishAllianceBroken(new AllianceBrokenEvent(
                NationId.of(from), NationId.of(to),
                AllianceBrokenEvent.AllianceBreakReason.WAR_DECLARED
            ));
        }

        // 同步到外交服务 - 单向敌对，不同步反向
        // audit A-006: declareEnemy 本地是单向敌对，仅写 from->to 而不写 to->from
        // 避免本地单边 vs 外交双边不一致
        syncOneWayToDiplomacyService(from, to, DiplomacyRelation.WAR);
    }

    /**
     * 移除敌对
     */
    public void removeEnemy(UUID from, UUID to) {
        NationRelations rel = getRelations(from);
        boolean wasEnemy = rel.isEnemy(to);

        rel.removeEnemy(to);

        if (wasEnemy) {
            // 发布关系变更事件
            publishRelationChanged(new DiplomacyRelationChangedEvent(
                NationId.of(from), NationId.of(to),
                DiplomacyRelation.WAR, DiplomacyRelation.NEUTRAL
            ));

            // 同步到外交服务
            syncToDiplomacyService(from, to, DiplomacyRelation.NEUTRAL);
        }
    }

    /**
     * 同步到外交服务（单向，用于宣战等单向关系）
     */
    private void syncOneWayToDiplomacyService(UUID from, UUID to, DiplomacyRelation relation) {
        if (diplomacyService != null) {
            diplomacyService.setRelation(NationId.of(from), NationId.of(to), relation);
        }
    }

    /**
     * 同步到外交服务（双向，用于联盟等需要双方一致的关系）
     */
    private void syncToDiplomacyService(UUID nation1, UUID nation2, DiplomacyRelation relation) {
        if (diplomacyService != null) {
            diplomacyService.setRelation(NationId.of(nation1), NationId.of(nation2), relation);
            diplomacyService.setRelation(NationId.of(nation2), NationId.of(nation1), relation);
        }
    }

    /**
     * 检查是否为盟友（双向确认）
     */
    public boolean areAllies(UUID nation1, UUID nation2) {
        NationRelations rel1 = getRelations(nation1);
        NationRelations rel2 = getRelations(nation2);

        return rel1.isAlly(nation2) && rel2.isAlly(nation1);
    }

    /**
     * 检查是否为敌对（单向）
     */
    public boolean areEnemies(UUID nation1, UUID nation2) {
        NationRelations rel1 = getRelations(nation1);
        return rel1.isEnemy(nation2);
    }

    /**
     * 检查是否为中立
     */
    public boolean areNeutral(UUID nation1, UUID nation2) {
        return !areAllies(nation1, nation2) && !areEnemies(nation1, nation2);
    }

    /**
     * 获取关系类型
     */
    public RelationType getRelationType(UUID from, UUID to) {
        if (from.equals(to)) {
            return RelationType.SELF;
        }

        if (areAllies(from, to)) {
            return RelationType.ALLY;
        }

        if (areEnemies(from, to)) {
            return RelationType.ENEMY;
        }

        return RelationType.NEUTRAL;
    }

    /**
     * 获取所有盟友
     */
    public Set<UUID> getAllies(UUID nationId) {
        return getRelations(nationId).getAllies();
    }

    /**
     * 获取所有敌对
     */
    public Set<UUID> getEnemies(UUID nationId) {
        return getRelations(nationId).getEnemies();
    }

    /**
     * 清除Nation的所有关系
     */
    public void clearAllRelations(UUID nationId) {
        NationRelations rel = relations.remove(nationId);
        if (rel == null) {
            return;
        }

        // 从其他Nation的关系中移除（避免对已删除国家产生空 NationRelations 幽灵条目）
        for (UUID ally : rel.getAllies()) {
            NationRelations allyRel = relations.get(ally);
            if (allyRel != null) {
                allyRel.removeAlly(nationId);
            }
        }

        for (UUID enemy : rel.getEnemies()) {
            NationRelations enemyRel = relations.get(enemy);
            if (enemyRel != null) {
                enemyRel.removeEnemy(nationId);
            }
        }
    }

    /**
     * 获取统计信息
     */
    public RelationStats getStats() {
        int totalEnemies = 0;
        // 用无向边集合去重统计联盟，避免双边 ally 与单边 ally 混杂造成 /2 后为负或非整数
        Set<String> allianceEdges = new HashSet<>();

        for (NationRelations rel : relations.values()) {
            totalEnemies += rel.getEnemies().size();
            UUID from = rel.getNationId();
            for (UUID ally : rel.getAllies()) {
                // 用规范化的小-大拼接确保 (a,b) 与 (b,a) 视为同一条无向边
                String key = from.compareTo(ally) < 0 ? from + ":" + ally : ally + ":" + from;
                allianceEdges.add(key);
            }
        }

        int totalAlliances = allianceEdges.size();

        return new RelationStats(
            relations.size(),
            totalAlliances,
            totalEnemies
        );
    }

    // ==================== 内部类 ====================

    /**
     * Nation关系类型
     */
    public enum RelationType {
        SELF("§b自己", "§b"),
        ALLY("§a盟友", "§a"),
        NEUTRAL("§7中立", "§7"),
        ENEMY("§c敌对", "§c");

        private final String displayName;
        private final String colorCode;

        RelationType(String displayName, String colorCode) {
            this.displayName = displayName;
            this.colorCode = colorCode;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColorCode() {
            return colorCode;
        }
    }

    /**
     * 联盟结果
     */
    public record AllianceResult(boolean success, String message) {}

    /**
     * 统计信息
     */
    public record RelationStats(
        int totalNations,
        int totalAlliances,
        int totalEnemies
    ) {
        @Override
        public String toString() {
            return String.format(
                "RelationStats[nations=%d, alliances=%d, enemies=%d]",
                totalNations, totalAlliances, totalEnemies
            );
        }
    }
}
