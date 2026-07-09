package dev.starcore.starcore.nation.relation;

import java.util.*;

/**
 * Nation关系数据
 * 存储单个Nation的所有关系
 */
public class NationRelations {

    private final UUID nationId;

    // 盟友Nation
    private final Set<UUID> allies = new HashSet<>();

    // 敌对Nation
    private final Set<UUID> enemies = new HashSet<>();

    public NationRelations(UUID nationId) {
        this.nationId = nationId;
    }

    /**
     * 添加盟友
     */
    public void addAlly(UUID allyId) {
        if (!allyId.equals(nationId)) {
            allies.add(allyId);
            enemies.remove(allyId); // 移除敌对
        }
    }

    /**
     * 移除盟友
     */
    public void removeAlly(UUID allyId) {
        allies.remove(allyId);
    }

    /**
     * 添加敌对
     */
    public void addEnemy(UUID enemyId) {
        if (!enemyId.equals(nationId)) {
            enemies.add(enemyId);
            allies.remove(enemyId); // 移除盟友
        }
    }

    /**
     * 移除敌对
     */
    public void removeEnemy(UUID enemyId) {
        enemies.remove(enemyId);
    }

    /**
     * 是否为盟友
     */
    public boolean isAlly(UUID nationId) {
        return allies.contains(nationId);
    }

    /**
     * 是否为敌对
     */
    public boolean isEnemy(UUID nationId) {
        return enemies.contains(nationId);
    }

    /**
     * 获取所有盟友
     */
    public Set<UUID> getAllies() {
        return Collections.unmodifiableSet(allies);
    }

    /**
     * 获取所有敌对
     */
    public Set<UUID> getEnemies() {
        return Collections.unmodifiableSet(enemies);
    }

    /**
     * 获取盟友数量
     */
    public int getAllyCount() {
        return allies.size();
    }

    /**
     * 获取敌对数量
     */
    public int getEnemyCount() {
        return enemies.size();
    }

    public UUID getNationId() {
        return nationId;
    }

    @Override
    public String toString() {
        return String.format(
            "NationRelations[nation=%s, allies=%d, enemies=%d]",
            nationId, allies.size(), enemies.size()
        );
    }
}
