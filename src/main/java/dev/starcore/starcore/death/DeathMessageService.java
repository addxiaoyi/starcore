package dev.starcore.starcore.death;

import dev.starcore.starcore.foundation.util.RandomProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 死亡消息服务
 * 完全可自定义的死亡提示
 */
public final class DeathMessageService {
    // 死亡消息模板 - 使用线程安全的 ConcurrentHashMap 和 CopyOnWriteArrayList
    private final Map<DeathCause, List<String>> deathMessages = new ConcurrentHashMap<>();

    // 是否启用自定义死亡消息
    private boolean enabled = true;

    public DeathMessageService() {
        loadDefaultMessages();
    }

    /**
     * 加载默认死亡消息
     */
    private void loadDefaultMessages() {
        // ========== PvP死亡 ==========
        addMessages(DeathCause.PVP, Arrays.asList(
            "{victim} 被 {killer} 击败了",
            "{victim} 在与 {killer} 的战斗中阵亡",
            "{killer} 终结了 {victim}",
            "{victim} 被 {killer} 送回了重生点",
            "{killer} 证明了自己比 {victim} 更强",
            "{victim} 成为了 {killer} 的手下败将",
            "{killer} 让 {victim} 见识到了真正的实力",
            "{victim} 的连杀被 {killer} 终结了"
        ));

        // ========== 坠落死亡 ==========
        addMessages(DeathCause.FALL, Arrays.asList(
            "{victim} 从高处坠落身亡",
            "{victim} 忘记了方块的重要性",
            "{victim} 体验了一次自由落体",
            "{victim} 落地成盒",
            "{victim} 发现重力确实存在",
            "{victim} 摔成了肉酱",
            "{victim} 尝试飞行失败了"
        ));

        // ========== 岩浆死亡 ==========
        addMessages(DeathCause.LAVA, Arrays.asList(
            "{victim} 在岩浆中游泳",
            "{victim} 发现岩浆很烫",
            "{victim} 被岩浆吞噬了",
            "{victim} 变成了烤肉",
            "{victim} 试图在岩浆中泡澡"
        ));

        // ========== 溺水 ==========
        addMessages(DeathCause.DROWNING, Arrays.asList(
            "{victim} 溺水了",
            "{victim} 忘记了如何游泳",
            "{victim} 在水下待太久了",
            "{victim} 需要学习潜水",
            "{victim} 被水淹没了"
        ));

        // ========== 爆炸 ==========
        addMessages(DeathCause.EXPLOSION, Arrays.asList(
            "{victim} 被炸飞了",
            "{victim} 体验了一次爆炸艺术",
            "{victim} 被TNT送上了天",
            "{victim} 成为了爆炸的牺牲品",
            "轰！{victim} 消失了"
        ));

        // ========== 苦力怕 ==========
        addMessages(DeathCause.CREEPER, Arrays.asList(
            "{victim} 被苦力怕炸飞了",
            "{victim} 听到了嘶嘶声...",
            "苦力怕：那是我送给 {victim} 的礼物！",
            "{victim} 被绿色的朋友拥抱了",
            "{victim} 没有及时逃离苦力怕"
        ));

        // ========== 虚空 ==========
        addMessages(DeathCause.VOID, Arrays.asList(
            "{victim} 坠入了虚空",
            "{victim} 发现了世界的边界",
            "{victim} 消失在虚空中",
            "{victim} 前往了异次元",
            "{victim} 离开了这个世界"
        ));

        // ========== 火焰 ==========
        addMessages(DeathCause.FIRE, Arrays.asList(
            "{victim} 被烧成了灰烬",
            "{victim} 走进火焰里了",
            "{victim} 在火中挣扎",
            "{victim} 变成了烤肉",
            "{victim} 被火焰吞噬"
        ));

        // ========== 饥饿 ==========
        addMessages(DeathCause.STARVATION, Arrays.asList(
            "{victim} 饿死了",
            "{victim} 忘记吃饭了",
            "{victim} 应该带些食物",
            "{victim} 死于饥饿",
            "{victim} 需要学会做饭"
        ));

        // ========== 凋零 ==========
        addMessages(DeathCause.WITHER, Arrays.asList(
            "{victim} 被凋零效果杀死",
            "{victim} 凋零成灰了",
            "{victim} 被凋零之力吞噬",
            "{victim} 抵挡不住凋零的力量"
        ));

        // ========== 其他实体 ==========
        addMessages(DeathCause.ENTITY, Arrays.asList(
            "{victim} 被 {killer} 杀死了",
            "{victim} 被 {killer} 击败",
            "{killer} 终结了 {victim}",
            "{victim} 成为了 {killer} 的猎物"
        ));

        // ========== 未知原因 ==========
        addMessages(DeathCause.UNKNOWN, Arrays.asList(
            "{victim} 死了",
            "{victim} 莫名其妙地死了",
            "{victim} 的死因不明",
            "{victim} 以某种方式死亡了"
        ));
    }

    /**
     * 添加死亡消息模板
     */
    public void addMessages(DeathCause cause, List<String> messages) {
        deathMessages.put(cause, new CopyOnWriteArrayList<>(messages));
    }

    /**
     * 添加单条死亡消息
     */
    public void addMessage(DeathCause cause, String message) {
        deathMessages.computeIfAbsent(cause, k -> new CopyOnWriteArrayList<>()).add(message);
    }

    /**
     * 处理玩家死亡
     */
    public Component handleDeath(PlayerDeathEvent event) {
        if (!enabled) {
            return event.deathMessage();
        }

        Player victim = event.getEntity();
        Entity killer = victim.getKiller();

        // 判断死亡原因
        DeathCause cause = determineCause(event);

        // 获取死亡消息模板
        List<String> messages = deathMessages.get(cause);
        if (messages == null || messages.isEmpty()) {
            messages = deathMessages.get(DeathCause.UNKNOWN);
        }

        // 随机选择一条消息
        String template = messages.get(RandomProvider.nextInt(messages.size()));

        // 替换占位符
        String message = template
            .replace("{victim}", victim.getName())
            .replace("{killer}", killer != null ? killer.getName() : "未知");

        return Component.text(message);
    }

    /**
     * 判断死亡原因
     */
    private DeathCause determineCause(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Entity killer = victim.getKiller();

        // PvP
        if (killer instanceof Player) {
            return DeathCause.PVP;
        }

        // 检查最后伤害原因
        var lastDamage = victim.getLastDamageCause();
        if (lastDamage == null) {
            return DeathCause.UNKNOWN;
        }

        return switch (lastDamage.getCause()) {
            case FALL -> DeathCause.FALL;
            case LAVA -> DeathCause.LAVA;
            case DROWNING -> DeathCause.DROWNING;
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> {
                if (killer != null && killer.getType().name().equals("CREEPER")) {
                    yield DeathCause.CREEPER;
                }
                yield DeathCause.EXPLOSION;
            }
            case VOID -> DeathCause.VOID;
            case FIRE, FIRE_TICK -> DeathCause.FIRE;
            case STARVATION -> DeathCause.STARVATION;
            case WITHER -> DeathCause.WITHER;
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> DeathCause.ENTITY;
            default -> DeathCause.UNKNOWN;
        };
    }

    /**
     * 死亡原因枚举
     */
    public enum DeathCause {
        PVP,           // 玩家击杀
        FALL,          // 坠落
        LAVA,          // 岩浆
        DROWNING,      // 溺水
        EXPLOSION,     // 爆炸
        CREEPER,       // 苦力怕
        VOID,          // 虚空
        FIRE,          // 火焰
        STARVATION,    // 饥饿
        WITHER,        // 凋零
        ENTITY,        // 其他实体
        UNKNOWN        // 未知
    }

    /**
     * 设置是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 从配置加载死亡消息
     */
    public void loadFromConfig(Map<String, List<String>> config) {
        deathMessages.clear();
        for (Map.Entry<String, List<String>> entry : config.entrySet()) {
            try {
                DeathCause cause = DeathCause.valueOf(entry.getKey().toUpperCase());
                addMessages(cause, entry.getValue());
            } catch (IllegalArgumentException e) {
                // 忽略未知的死亡原因
            }
        }
    }

    /**
     * 获取所有死亡消息
     */
    public Map<DeathCause, List<String>> getAllMessages() {
        Map<DeathCause, List<String>> result = new HashMap<>();
        for (Map.Entry<DeathCause, List<String>> entry : deathMessages.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }
}
