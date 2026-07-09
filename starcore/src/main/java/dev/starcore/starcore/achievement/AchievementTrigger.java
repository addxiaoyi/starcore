package dev.starcore.starcore.achievement;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 成就触发器
 * 参考Minecraft原版成就系统
 */
public final class AchievementTrigger {
    private final TriggerType type;
    private final Map<String, Object> conditions;

    public AchievementTrigger(TriggerType type, Map<String, Object> conditions) {
        this.type = type;
        this.conditions = new ConcurrentHashMap<>(conditions);
    }

    /**
     * 成就触发类型
     * 完整参考 Minecraft Wiki 的触发器列表
     */
    public enum TriggerType {
        // ========== 基础触发器 ==========
        IMPOSSIBLE,                    // 不可能完成（用于根成就）
        TICK,                         // 每tick触发

        // ========== 玩家动作 ==========
        PLAYER_HURT_ENTITY,           // 伤害实体
        PLAYER_KILLED_ENTITY,         // 击杀实体
        ENTITY_HURT_PLAYER,           // 被实体伤害
        ENTITY_KILLED_PLAYER,         // 被实体击杀

        // ========== 物品相关 ==========
        INVENTORY_CHANGED,            // 背包变化
        ITEM_USED_ON_BLOCK,          // 物品用于方块
        ITEM_DURABILITY_CHANGED,      // 物品耐久度变化
        ENCHANTED_ITEM,               // 附魔物品
        FILLED_BUCKET,                // 装满桶

        // ========== 方块相关 ==========
        PLACED_BLOCK,                 // 放置方块
        BREAK_BLOCK,                  // 破坏方块
        ENTER_BLOCK,                  // 进入方块（如传送门）

        // ========== 交互相关 ==========
        VILLAGER_TRADE,               // 村民交易
        BRED_ANIMALS,                 // 繁殖动物
        TAME_ANIMAL,                  // 驯服动物
        FISHING_ROD_HOOKED,           // 钓鱼竿钩住

        // ========== 位置相关 ==========
        LOCATION,                     // 到达位置
        SLEPT_IN_BED,                // 在床上睡觉
        LEVITATION,                   // 漂浮状态

        // ========== 状态效果 ==========
        EFFECTS_CHANGED,              // 效果变化
        USED_ENDER_EYE,              // 使用末影之眼
        USED_TOTEM,                   // 使用不死图腾

        // ========== 生物相关 ==========
        SUMMONED_ENTITY,              // 召唤实体
        SHOT_CROSSBOW,                // 射弩
        KILLED_BY_CROSSBOW,           // 被弩击杀
        HERO_OF_THE_VILLAGE,          // 村庄英雄

        // ========== 维度相关 ==========
        CHANGED_DIMENSION,            // 改变维度
        NETHER_TRAVEL,                // 下界旅行

        // ========== 战斗相关 ==========
        PLAYER_GENERATES_CONTAINER_LOOT, // 生成容器战利品
        THROW_ITEM_HIT_ENTITY,        // 投掷物击中实体
        FALL_FROM_HEIGHT,             // 从高处坠落
        RIDE_ENTITY_IN_LAVA,          // 在岩浆中骑实体
        LIGHTNING_STRIKE,             // 闪电击中

        // ========== 食物相关 ==========
        CONSUME_ITEM,                 // 消耗物品
        ALLAY_DROP_ITEM_ON_BLOCK,    // 悦灵放置物品
        AVOID_VIBRATION,              // 避免振动

        // ========== 1.19+ ==========
        KILL_MOB_NEAR_SCULK_CATALYST, // 在幽匿催发体附近击杀生物

        // ========== 1.20+ ==========
        RECIPE_CRAFTED,               // 合成配方
        CRAFTER_RECIPE_CRAFTED,       // 使用合成器合成

        // ========== 1.21+ ==========
        FALL_AFTER_EXPLOSION,         // 爆炸后坠落
        ANY_BLOCK_USE,                // 使用任意方块

        // ========== STARCORE 自定义 ==========
        CUSTOM_KILL_STREAK,           // 连杀
        CUSTOM_DUEL_WIN,              // 决斗胜利
        CUSTOM_MONEY_EARNED,          // 赚取金钱
        CUSTOM_GUILD_CREATED,         // 创建公会
        CUSTOM_FRIEND_ADDED,          // 添加好友
        CUSTOM_DAILY_CHECKIN,         // 每日签到
        CUSTOM_QUEST_COMPLETED        // 完成任务
    }

    /**
     * 触发器构建器
     */
    public static class Builder {
        private final TriggerType type;
        private final Map<String, Object> conditions = new ConcurrentHashMap<>();

        public Builder(TriggerType type) {
            this.type = type;
        }

        // ========== 实体条件 ==========

        /**
         * 实体类型
         */
        public Builder entity(EntityType entityType) {
            conditions.put("entity", entityType.name());
            return this;
        }

        /**
         * 实体类型列表
         */
        public Builder entities(EntityType... types) {
            List<String> list = new ArrayList<>();
            for (EntityType type : types) {
                list.add(type.name());
            }
            conditions.put("entity", list);
            return this;
        }

        // ========== 物品条件 ==========

        /**
         * 物品类型
         */
        public Builder item(Material material) {
            conditions.put("item", material.name());
            return this;
        }

        /**
         * 物品列表
         */
        public Builder items(Material... materials) {
            List<String> list = new ArrayList<>();
            for (Material mat : materials) {
                list.add(mat.name());
            }
            conditions.put("item", list);
            return this;
        }

        /**
         * 物品数量
         */
        public Builder itemCount(int min, int max) {
            conditions.put("item_count_min", min);
            conditions.put("item_count_max", max);
            return this;
        }

        // ========== 方块条件 ==========

        /**
         * 方块类型
         */
        public Builder block(Material material) {
            conditions.put("block", material.name());
            return this;
        }

        /**
         * 方块列表
         */
        public Builder blocks(Material... materials) {
            List<String> list = new ArrayList<>();
            for (Material mat : materials) {
                list.add(mat.name());
            }
            conditions.put("block", list);
            return this;
        }

        // ========== 位置条件 ==========

        /**
         * 世界
         */
        public Builder world(String world) {
            conditions.put("world", world);
            return this;
        }

        /**
         * 生物群系
         */
        public Builder biome(String biome) {
            conditions.put("biome", biome);
            return this;
        }

        /**
         * Y轴范围
         */
        public Builder yLevel(int min, int max) {
            conditions.put("y_min", min);
            conditions.put("y_max", max);
            return this;
        }

        // ========== 数值条件 ==========

        /**
         * 击杀数量
         */
        public Builder killCount(int count) {
            conditions.put("kill_count", count);
            return this;
        }

        /**
         * 伤害值
         */
        public Builder damage(double min, double max) {
            conditions.put("damage_min", min);
            conditions.put("damage_max", max);
            return this;
        }

        /**
         * 距离
         */
        public Builder distance(double min, double max) {
            conditions.put("distance_min", min);
            conditions.put("distance_max", max);
            return this;
        }

        // ========== 状态条件 ==========

        /**
         * 药水效果
         */
        public Builder effect(String effect) {
            conditions.put("effect", effect);
            return this;
        }

        /**
         * 是否在火中
         */
        public Builder onFire(boolean onFire) {
            conditions.put("on_fire", onFire);
            return this;
        }

        /**
         * 是否潜行
         */
        public Builder sneaking(boolean sneaking) {
            conditions.put("sneaking", sneaking);
            return this;
        }

        // ========== 自定义条件 ==========

        /**
         * 自定义条件
         */
        public Builder custom(String key, Object value) {
            conditions.put(key, value);
            return this;
        }

        /**
         * 构建触发器
         */
        public AchievementTrigger build() {
            return new AchievementTrigger(type, conditions);
        }
    }

    // Getters
    public TriggerType getType() {
        return type;
    }

    public Map<String, Object> getConditions() {
        return new HashMap<>(conditions);
    }

    public Object getCondition(String key) {
        return conditions.get(key);
    }

    public boolean hasCondition(String key) {
        return conditions.containsKey(key);
    }
}
