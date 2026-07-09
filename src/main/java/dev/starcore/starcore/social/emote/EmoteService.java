package dev.starcore.starcore.social.emote;
import java.util.Optional;

import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表情/动作服务核心
 */
public class EmoteService {
    private final Map<String, EmoteDefinition> emotes = new ConcurrentHashMap<>();
    private final Map<UUID, EmoteState> playerStates = new ConcurrentHashMap<>();
    private EmoteAnimationHandler animationHandler;

    public EmoteService() {
        registerDefaultEmotes();
    }

    public void setAnimationHandler(EmoteAnimationHandler handler) {
        this.animationHandler = handler;
    }

    /**
     * 注册默认动作
     */
    private void registerDefaultEmotes() {
        // 问候类
        registerEmote(EmoteDefinition.builder()
            .id("wave").name("挥手").description("向朋友挥手打招呼")
            .animationType("arm").animationData("ARM_RAISE")
            .durationTicks(40).cooldownSeconds(3)
            .category("greeting").build());

        registerEmote(EmoteDefinition.builder()
            .id("bow").name("鞠躬").description("表示尊敬的鞠躬")
            .animationType("pose").animationData("BOW")
            .durationTicks(60).cooldownSeconds(5)
            .category("greeting").build());

        registerEmote(EmoteDefinition.builder()
            .id("salute").name("敬礼").description("军事风格的敬礼")
            .animationType("arm").animationData("SALUTE")
            .durationTicks(35).cooldownSeconds(3)
            .permission("starcore.emote.salute")
            .category("greeting").build());

        // 情感类
        registerEmote(EmoteDefinition.builder()
            .id("laugh").name("大笑").description("开怀大笑")
            .animationType("pose").animationData("LAUGH")
            .durationTicks(50).cooldownSeconds(5)
            .category("emotion").build());

        registerEmote(EmoteDefinition.builder()
            .id("cry").name("哭泣").description("伤心的哭泣")
            .animationType("pose").animationData("CRY")
            .durationTicks(60).cooldownSeconds(10)
            .category("emotion").build());

        registerEmote(EmoteDefinition.builder()
            .id("angry").name("愤怒").description("表达愤怒")
            .animationType("pose").animationData("ANGRY")
            .durationTicks(40).cooldownSeconds(8)
            .category("emotion").build());

        registerEmote(EmoteDefinition.builder()
            .id("clap").name("鼓掌").description("为他人鼓掌")
            .animationType("arm").animationData("CLAP")
            .durationTicks(45).cooldownSeconds(3)
            .category("emotion").build());

        // 社交类
        registerEmote(EmoteDefinition.builder()
            .id("hug").name("拥抱").description("给朋友一个拥抱")
            .animationType("fullbody").animationData("HUG")
            .durationTicks(50).cooldownSeconds(10)
            .requiresTarget(true)
            .category("social").build());

        registerEmote(EmoteDefinition.builder()
            .id("kiss").name("亲吻").description("给心爱的人一个吻")
            .animationType("fullbody").animationData("KISS")
            .durationTicks(40).cooldownSeconds(15)
            .requiresTarget(true)
            .category("social").build());

        registerEmote(EmoteDefinition.builder()
            .id("handshake").name("握手").description("友好地握手")
            .animationType("arm").animationData("HANDSHAKE")
            .durationTicks(35).cooldownSeconds(3)
            .requiresTarget(true)
            .category("social").build());

        registerEmote(EmoteDefinition.builder()
            .id("dance").name("跳舞").description("开心地跳舞")
            .animationType("fullbody").animationData("DANCE")
            .durationTicks(100).cooldownSeconds(15)
            .category("social").build());

        registerEmote(EmoteDefinition.builder()
            .id("sit").name("坐下").description("坐下来休息")
            .animationType("pose").animationData("SIT")
            .durationTicks(200).cooldownSeconds(20)
            .category("social").build());

        // 战斗类
        registerEmote(EmoteDefinition.builder()
            .id("sword").name("拔剑").description("拔出武器展示")
            .animationType("arm").animationData("SWORD")
            .durationTicks(30).cooldownSeconds(5)
            .permission("starcore.emote.sword")
            .category("combat").build());

        registerEmote(EmoteDefinition.builder()
            .id("shield").name("举盾").description("举起盾牌防御")
            .animationType("arm").animationData("SHIELD")
            .durationTicks(40).cooldownSeconds(5)
            .permission("starcore.emote.shield")
            .category("combat").build());

        registerEmote(EmoteDefinition.builder()
            .id("attack").name("攻击姿态").description("展示攻击姿态")
            .animationType("fullbody").animationData("ATTACK")
            .durationTicks(35).cooldownSeconds(3)
            .category("combat").build());

        // 特殊类
        registerEmote(EmoteDefinition.builder()
            .id("point").name("指向").description("指向某个方向")
            .animationType("arm").animationData("POINT")
            .durationTicks(30).cooldownSeconds(2)
            .category("action").build());

        registerEmote(EmoteDefinition.builder()
            .id("thumbsup").name("点赞").description("竖起大拇指表示赞同")
            .animationType("arm").animationData("THUMBSUP")
            .durationTicks(35).cooldownSeconds(3)
            .category("action").build());

        registerEmote(EmoteDefinition.builder()
            .id("facepalm").name("捂脸").description("无奈地捂脸")
            .animationType("arm").animationData("FACEPALM")
            .durationTicks(45).cooldownSeconds(5)
            .category("emotion").build());

        registerEmote(EmoteDefinition.builder()
            .id("sleep").name("睡觉").description("躺下睡觉")
            .animationType("pose").animationData("SLEEP")
            .durationTicks(300).cooldownSeconds(30)
            .category("social").build());

        registerEmote(EmoteDefinition.builder()
            .id("eat").name("吃东西").description("享受美食")
            .animationType("arm").animationData("EAT")
            .durationTicks(50).cooldownSeconds(5)
            .category("action").build());

        registerEmote(EmoteDefinition.builder()
            .id("drink").name("喝东西").description("喝饮料")
            .animationType("arm").animationData("DRINK")
            .durationTicks(40).cooldownSeconds(5)
            .category("action").build());

        registerEmote(EmoteDefinition.builder()
            .id("spin").name("旋转").description("开心地旋转")
            .animationType("fullbody").animationData("SPIN")
            .durationTicks(60).cooldownSeconds(10)
            .category("social").build());
    }

    /**
     * 注册动作
     */
    public void registerEmote(EmoteDefinition emote) {
        if (emote.getId() != null && emote.getName() != null) {
            emotes.put(emote.getId().toLowerCase(), emote);
        }
    }

    /**
     * 注销动作（用于自定义动作删除）
     */
    public void unregisterEmote(String id) {
        if (id != null) {
            emotes.remove(id.toLowerCase());
        }
    }

    /**
     * 获取动作定义
     */
    public Optional<EmoteDefinition> getEmote(String id) {
        return Optional.ofNullable(emotes.get(id.toLowerCase()));
    }

    /**
     * 获取所有动作
     */
    public Collection<EmoteDefinition> getAllEmotes() {
        return emotes.values();
    }

    /**
     * 按分类获取动作
     */
    public List<EmoteDefinition> getEmotesByCategory(String category) {
        return emotes.values().stream()
            .filter(e -> e.getCategory().equalsIgnoreCase(category))
            .toList();
    }

    /**
     * 获取所有分类
     */
    public Set<String> getCategories() {
        return emotes.values().stream()
            .map(EmoteDefinition::getCategory)
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 执行动作
     */
    public EmoteResult executeEmote(Player player, String emoteId, Player target) {
        Optional<EmoteDefinition> emoteOpt = getEmote(emoteId);
        if (emoteOpt.isEmpty()) {
            return EmoteResult.EMOTE_NOT_FOUND;
        }

        EmoteDefinition emote = emoteOpt.get();

        // 检查权限
        if (!emote.getPermission().isEmpty() &&
            !player.hasPermission(emote.getPermission()) &&
            !player.hasPermission("starcore.emote.*")) {
            return EmoteResult.NO_PERMISSION;
        }

        // 检查冷却
        EmoteState state = getOrCreateState(player.getUniqueId());
        if (state.isOnCooldown(emote.getCooldownSeconds())) {
            return EmoteResult.ON_COOLDOWN;
        }

        // 检查是否需要目标
        if (emote.requiresTarget() && target == null) {
            return EmoteResult.TARGET_REQUIRED;
        }

        // 检查目标是否有效
        if (target != null && target.equals(player)) {
            return EmoteResult.INVALID_TARGET;
        }

        // D-043: 加强目标校验：在线 + 同一世界 + 距离限制
        if (target != null) {
            if (!target.isOnline()) {
                return EmoteResult.INVALID_TARGET;
            }
            if (!target.getWorld().equals(player.getWorld())) {
                return EmoteResult.INVALID_TARGET;
            }
            double distance = player.getLocation().distance(target.getLocation());
            if (distance > 32.0) {
                return EmoteResult.INVALID_TARGET;
            }
        }

        // 执行动作
        long currentTime = System.currentTimeMillis();
        state.setCurrentEmote(emoteId, currentTime, target != null ? target.getUniqueId() : null);
        state.updateLastEmoteTime();

        // 触发动画
        if (animationHandler != null) {
            animationHandler.playAnimation(player, emote, target);
        }

        return EmoteResult.SUCCESS;
    }

    /**
     * 获取或创建玩家状态
     */
    public EmoteState getOrCreateState(UUID playerId) {
        return playerStates.computeIfAbsent(playerId, EmoteState::new);
    }

    /**
     * 清除玩家状态
     */
    public void clearState(UUID playerId) {
        playerStates.remove(playerId);
    }

    /**
     * 获取玩家的当前动作状态
     */
    public Optional<EmoteState> getPlayerState(UUID playerId) {
        return Optional.ofNullable(playerStates.get(playerId));
    }

    /**
     * 更新动作状态（用于定时清理）
     */
    public void update() {
        long currentTime = System.currentTimeMillis();
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, EmoteState> entry : playerStates.entrySet()) {
            EmoteState state = entry.getValue();
            if (state.isAnimating() && state.getEmoteStartTime() > 0) {
                // D-045: 使用 emote 的 durationTicks（默认硬编码 10s 兜底）作为最大持续，
                // 避免硬编码截断 sleep/sit 等长动作。
                long maxDurationMs = 10000L;
                if (state.getCurrentEmoteId() != null) {
                    Optional<EmoteDefinition> cur = getEmote(state.getCurrentEmoteId());
                    if (cur.isPresent()) {
                        maxDurationMs = cur.get().getDurationTicks() * 50L;
                    }
                }
                if (currentTime - state.getEmoteStartTime() > maxDurationMs) {
                    state.clearEmote();
                }
            }
        }
    }

    public enum EmoteResult {
        SUCCESS,
        EMOTE_NOT_FOUND,
        NO_PERMISSION,
        ON_COOLDOWN,
        TARGET_REQUIRED,
        INVALID_TARGET,
        PLAYER_NOT_FOUND
    }
}
