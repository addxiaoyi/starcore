package dev.starcore.starcore.foundation.sound;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 场景化声音反馈系统
 * 提供基于游戏场景的智能音效反馈
 */
public final class SceneSoundService {

    private final Plugin plugin;
    // 玩家偏好设置 - 使用ConcurrentHashMap保证线程安全
    private final Map<UUID, SoundPreferences> playerPreferences = new ConcurrentHashMap<>();

    // 场景定义
    public enum SoundScene {
        // UI交互场景
        UI_CLICK("menu.click", "UI_BUTTON_CLICK", 0.4f, 1.2f),
        UI_HOVER("menu.hover", "BLOCK_STONE_BUTTON_CLICK_ON", 0.2f, 1.5f),
        UI_SELECT("menu.select", "UI_CARTOGRAPHY_TABLE_TAKE_RESULT", 0.4f, 1.3f),
        UI_OPEN("menu.open", "BLOCK_CHEST_OPEN", 0.5f, 1.0f),
        UI_CLOSE("menu.close", "BLOCK_CHEST_CLOSE", 0.5f, 0.9f),
        UI_BACK("menu.back", "BLOCK_NOTE_BLOCK_BASS", 0.3f, 0.8f),
        UI_CONFIRM("menu.confirm", "BLOCK_BEACON_ACTIVATE", 0.5f, 1.2f),
        UI_CANCEL("menu.cancel", "BLOCK_NOTE_BLOCK_GUITAR", 0.3f, 0.8f),

        // 导航场景
        NAV_NEXT("nav.next", "UI_LOOM_SELECT_PATTERN", 0.3f, 1.2f),
        NAV_PREVIOUS("nav.previous", "UI_LOOM_SELECT_PATTERN", 0.3f, 0.9f),

        // 国家场景
        NATION_CREATE("nation.create", "ENTITY_PLAYER_LEVELUP", 0.6f, 1.5f),
        NATION_JOIN("nation.join", "ENTITY_VILLAGER_CELEBRATE", 0.5f, 1.0f),
        NATION_LEAVE("nation.leave", "BLOCK_NOTE_BLOCK_BASS", 0.4f, 0.7f),
        NATION_UPGRADE("nation.upgrade", "UI_TOAST_CHALLENGE_COMPLETE", 0.6f, 1.3f),

        // 领土场景
        TERRITORY_CLAIM("territory.claim", "ENTITY_PLAYER_LEVELUP", 0.5f, 1.2f),
        TERRITORY_LOSE("territory.lose", "ENTITY_WITHER_DEATH", 0.5f, 0.8f),
        TERRITORY_BATTLE("territory.battle", "BLOCK_NOTE_BLOCK_BASS", 0.6f, 1.0f),

        // 资源场景
        RESOURCE_GAIN("resource.gain", "ENTITY_EXPERIENCE_ORB_PICKUP", 0.4f, 1.3f),
        RESOURCE_LOSE("resource.lose", "ENTITY_ITEM_PICKUP", 0.3f, 0.8f),
        RESOURCE_RARE("resource.rare", "UI_TOAST_CHALLENGE_COMPLETE", 0.7f, 1.5f),
        RESOURCE_REFRESH("resource.refresh", "BLOCK_AMETHYST_BLOCK_RESONATE", 0.5f, 1.2f),

        // 外交场景
        DIPLOMACY_PROPOSE("diplomacy.propose", "UI_BUTTON_CLICK", 0.4f, 1.1f),
        DIPLOMACY_ACCEPT("diplomacy.accept", "BLOCK_NOTE_BLOCK_PLING", 0.5f, 1.5f),
        DIPLOMACY_REJECT("diplomacy.reject", "BLOCK_NOTE_BLOCK_BANJO", 0.4f, 0.7f),
        DIPLOMACY_BREAK("diplomacy.break", "ENTITY_ZOMBIE_VILLAGER_CURE", 0.5f, 0.8f),

        // 战争场景
        WAR_DECLARE("war.declare", "ENTITY_WITHER_SPAWN", 0.7f, 0.85f),
        WAR_START("war.start", "ENTITY_GENERIC_EXPLODE", 0.6f, 1.0f),
        WAR_END("war.end", "ENTITY_WITHER_DEATH", 0.5f, 1.0f),
        WAR_VICTORY("war.victory", "UI_VICTORY_SCREEN_WIN", 0.6f, 1.2f),
        WAR_DEFEAT("war.defeat", "ENTITY_WITHER_DEATH", 0.5f, 0.6f),
        WAR_ATTACK("war.attack", "ENTITY_GENERIC_EXPLODE", 0.6f, 1.1f),
        WAR_DEFEND("war.defend", "BLOCK_ANVIL_LAND", 0.5f, 1.0f),

        // 经济场景
        ECONOMY_INCOME("economy.income", "ENTITY_EXPERIENCE_ORB_PICKUP", 0.3f, 1.2f),
        ECONOMY_EXPENSE("economy.expense", "BLOCK_NOTE_BLOCK_HAT", 0.3f, 0.9f),
        ECONOMY_TAX("economy.tax", "UI_BUTTON_CLICK", 0.4f, 1.0f),
        ECONOMY_REWARD("economy.reward", "ENTITY_PLAYER_LEVELUP", 0.5f, 1.3f),

        // 科技场景
        TECH_RESEARCH("tech.research", "BLOCK_RESPAWN_ANCHOR_SET_SPAWN", 0.5f, 1.2f),
        TECH_UNLOCK("tech.unlock", "ENTITY_PLAYER_LEVELUP", 0.6f, 1.4f),
        TECH_COMPLETE("tech.complete", "UI_TOAST_CHALLENGE_COMPLETE", 0.6f, 1.3f),

        // 政策场景
        POLICY_ACTIVATE("policy.activate", "BLOCK_BEACON_ACTIVATE", 0.5f, 1.2f),
        POLICY_DEACTIVATE("policy.deactivate", "BLOCK_BEACON_DEACTIVATE", 0.4f, 1.0f),

        // 官员场景
        OFFICER_APPOINT("officer.appoint", "ENTITY_VILLAGER_CELEBRATE", 0.5f, 1.1f),
        OFFICER_REMOVE("officer.remove", "UI_BUTTON_CLICK", 0.4f, 0.9f),

        // 社交场景
        SOCIAL_FRIEND("social.friend", "ENTITY_HEARTBREAK", 0.4f, 1.2f),
        SOCIAL_MESSAGE("social.message", "BLOCK_NOTE_BLOCK_HAT", 0.2f, 1.0f),
        SOCIAL_GIFT("social.gift", "ENTITY_PLAYER_LEVELUP", 0.5f, 1.2f),

        // 成就场景
        ACHIEVEMENT_UNLOCK("achievement.unlock", "ENTITY_PLAYER_LEVELUP", 0.6f, 1.5f),
        ACHIEVEMENT_RARE("achievement.rare", "UI_TOAST_CHALLENGE_COMPLETE", 0.8f, 1.0f),

        // 物品场景
        ITEM_PICKUP("item.pickup", "ENTITY_ITEM_PICKUP", 0.4f, 1.2f),
        ITEM_DROP("item.drop", "ENTITY_ITEM_PICKUP", 0.3f, 0.8f),
        ITEM_EQUIP("item.equip", "ENTITY_ARMOR_STAND_HIT", 0.4f, 1.1f),
        ITEM_USE("item.use", "ENTITY_SPLASH_POTION_BREAK", 0.4f, 1.0f),
        ITEM_CRAFT("item.craft", "BLOCK_NOTE_BLOCK_PLING", 0.4f, 1.2f),
        ITEM_TRADE("item.trade", "ENTITY_VILLAGER_TRADE", 0.4f, 1.1f),

        // 移动场景
        MOVE_TELEPORT("move.teleport", "ENTITY_ENDERMAN_TELEPORT", 0.5f, 1.0f),
        MOVE_WARP("move.warp", "BLOCK_PORTAL_TRIGGER", 0.5f, 1.1f),
        MOVE_HOME("move.home", "BLOCK_ENCHANTMENT_TABLE_USE", 0.4f, 1.2f),

        // 通知场景
        NOTIFY_INFO("notify.info", "BLOCK_NOTE_BLOCK_HAT", 0.3f, 1.0f),
        NOTIFY_WARNING("notify.warning", "BLOCK_NOTE_BLOCK_BELL", 0.5f, 1.1f),
        NOTIFY_ERROR("notify.error", "ENTITY_VILLAGER_NO", 0.5f, 0.8f),
        NOTIFY_SUCCESS("notify.success", "ENTITY_EXPERIENCE_ORB_PICKUP", 0.4f, 1.2f),
        NOTIFY_URGENT("notify.urgent", "BLOCK_NOTE_BLOCK_BELL", 0.7f, 1.3f),

        // 生物群系场景
        BIOME_RARE("biome.rare", "UI_TOAST_CHALLENGE_COMPLETE", 0.7f, 1.5f),
        BIOME_ENTER("biome.enter", "BLOCK_NOTE_BLOCK_PLING", 0.3f, 1.1f),

        // 特殊场景
        LEVEL_UP("special.levelup", "ENTITY_PLAYER_LEVELUP", 0.7f, 1.4f),
        RARE_FIND("special.rarefind", "ENTITY_EVOCATION_ILLAGER_CAST_SPELL", 0.6f, 1.5f),
        MYSTERY("special.mystery", "ENTITY_EVOCATION_ILLAGER_PREPARE_ATTACK", 0.5f, 1.2f),
        BOSS_APPEAR("special.bossappear", "ENTITY_WITHER_SPAWN", 0.8f, 0.9f),
        BOSS_DEATH("special.bossdeath", "ENTITY_WITHER_DEATH", 0.7f, 1.0f);

        private final String configKey;
        private final String defaultSound;
        private final float defaultVolume;
        private final float defaultPitch;

        SoundScene(String configKey, String defaultSound, float defaultVolume, float defaultPitch) {
            this.configKey = configKey;
            this.defaultSound = defaultSound;
            this.defaultVolume = defaultVolume;
            this.defaultPitch = defaultPitch;
        }

        public String getConfigKey() { return configKey; }
        public String getDefaultSound() { return defaultSound; }
        public float getDefaultVolume() { return defaultVolume; }
        public float getDefaultPitch() { return defaultPitch; }
    }

    // 场景分组 - 用于批量配置
    public enum SoundSceneGroup {
        UI(SoundScene.UI_CLICK, SoundScene.UI_HOVER, SoundScene.UI_SELECT,
           SoundScene.UI_OPEN, SoundScene.UI_CLOSE, SoundScene.UI_BACK,
           SoundScene.UI_CONFIRM, SoundScene.UI_CANCEL),
        NAVIGATION(SoundScene.NAV_NEXT, SoundScene.NAV_PREVIOUS),
        NATION(SoundScene.NATION_CREATE, SoundScene.NATION_JOIN, SoundScene.NATION_LEAVE, SoundScene.NATION_UPGRADE),
        TERRITORY(SoundScene.TERRITORY_CLAIM, SoundScene.TERRITORY_LOSE, SoundScene.TERRITORY_BATTLE),
        RESOURCE(SoundScene.RESOURCE_GAIN, SoundScene.RESOURCE_LOSE, SoundScene.RESOURCE_RARE, SoundScene.RESOURCE_REFRESH),
        DIPLOMACY(SoundScene.DIPLOMACY_PROPOSE, SoundScene.DIPLOMACY_ACCEPT, SoundScene.DIPLOMACY_REJECT, SoundScene.DIPLOMACY_BREAK),
        WAR(SoundScene.WAR_DECLARE, SoundScene.WAR_START, SoundScene.WAR_END,
            SoundScene.WAR_VICTORY, SoundScene.WAR_DEFEAT, SoundScene.WAR_ATTACK, SoundScene.WAR_DEFEND),
        ECONOMY(SoundScene.ECONOMY_INCOME, SoundScene.ECONOMY_EXPENSE, SoundScene.ECONOMY_TAX, SoundScene.ECONOMY_REWARD),
        TECHNOLOGY(SoundScene.TECH_RESEARCH, SoundScene.TECH_UNLOCK, SoundScene.TECH_COMPLETE),
        POLICY(SoundScene.POLICY_ACTIVATE, SoundScene.POLICY_DEACTIVATE),
        OFFICER(SoundScene.OFFICER_APPOINT, SoundScene.OFFICER_REMOVE),
        SOCIAL(SoundScene.SOCIAL_FRIEND, SoundScene.SOCIAL_MESSAGE, SoundScene.SOCIAL_GIFT),
        ACHIEVEMENT(SoundScene.ACHIEVEMENT_UNLOCK, SoundScene.ACHIEVEMENT_RARE),
        ITEM(SoundScene.ITEM_PICKUP, SoundScene.ITEM_DROP, SoundScene.ITEM_EQUIP, SoundScene.ITEM_USE,
             SoundScene.ITEM_CRAFT, SoundScene.ITEM_TRADE),
        MOVEMENT(SoundScene.MOVE_TELEPORT, SoundScene.MOVE_WARP, SoundScene.MOVE_HOME),
        NOTIFICATION(SoundScene.NOTIFY_INFO, SoundScene.NOTIFY_WARNING, SoundScene.NOTIFY_ERROR,
                      SoundScene.NOTIFY_SUCCESS, SoundScene.NOTIFY_URGENT),
        BIOME(SoundScene.BIOME_RARE, SoundScene.BIOME_ENTER),
        SPECIAL(SoundScene.LEVEL_UP, SoundScene.RARE_FIND, SoundScene.MYSTERY,
                SoundScene.BOSS_APPEAR, SoundScene.BOSS_DEATH);

        private final SoundScene[] scenes;

        SoundSceneGroup(SoundScene... scenes) {
            this.scenes = scenes;
        }

        public SoundScene[] getScenes() { return scenes; }
    }

    // 玩家偏好设置
    public static class SoundPreferences {
        private float masterVolume = 1.0f;
        private float uiVolume = 1.0f;
        private float worldVolume = 1.0f;
        private float notificationVolume = 1.0f;
        private float pitchOffset = 0f;
        private boolean muted = false;
        private Set<SoundSceneGroup> mutedGroups = new HashSet<>();
        private Map<String, String> customSounds = new ConcurrentHashMap<>();

        public float getMasterVolume() { return masterVolume; }
        public void setMasterVolume(float volume) { this.masterVolume = Math.max(0f, Math.min(2f, volume)); }

        public float getUiVolume() { return uiVolume; }
        public void setUiVolume(float volume) { this.uiVolume = Math.max(0f, Math.min(2f, volume)); }

        public float getWorldVolume() { return worldVolume; }
        public void setWorldVolume(float volume) { this.worldVolume = Math.max(0f, Math.min(2f, volume)); }

        public float getNotificationVolume() { return notificationVolume; }
        public void setNotificationVolume(float volume) { this.notificationVolume = Math.max(0f, Math.min(2f, volume)); }

        public float getPitchOffset() { return pitchOffset; }
        public void setPitchOffset(float offset) { this.pitchOffset = Math.max(-1f, Math.min(1f, offset)); }

        public boolean isMuted() { return muted; }
        public void setMuted(boolean muted) { this.muted = muted; }

        public Set<SoundSceneGroup> getMutedGroups() { return mutedGroups; }
        public void muteGroup(SoundSceneGroup group) { mutedGroups.add(group); }
        public void unmuteGroup(SoundSceneGroup group) { mutedGroups.remove(group); }

        public Map<String, String> getCustomSounds() { return customSounds; }
        public void setCustomSound(String sceneKey, String soundName) { customSounds.put(sceneKey, soundName); }
        public String getCustomSound(String sceneKey) { return customSounds.get(sceneKey); }
    }

    public SceneSoundService(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 播放场景化音效
     */
    public void playSound(Player player, SoundScene scene) {
        playSound(player, scene, scene.getDefaultVolume(), scene.getDefaultPitch());
    }

    /**
     * 播放场景化音效（自定义音量）
     */
    public void playSound(Player player, SoundScene scene, float volume) {
        playSound(player, scene, volume, scene.getDefaultPitch());
    }

    /**
     * 播放场景化音效（自定义音量音调）
     */
    public void playSound(Player player, SoundScene scene, float volume, float pitch) {
        if (player == null || !player.isOnline()) return;

        SoundPreferences prefs = getPreferences(player);
        if (prefs.isMuted()) return;

        // 检查分组是否静音
        for (SoundSceneGroup group : SoundSceneGroup.values()) {
            for (SoundScene s : group.getScenes()) {
                if (s == scene && prefs.getMutedGroups().contains(group)) {
                    return;
                }
            }
        }

        // 确定最终音量
        float categoryVolume = getCategoryVolume(scene);
        float finalVolume = volume * categoryVolume * prefs.getMasterVolume();
        float finalPitch = pitch + prefs.getPitchOffset();

        // 获取音效名称（优先使用自定义）
        String soundName = prefs.getCustomSound(scene.getConfigKey());
        if (soundName == null) {
            soundName = scene.getDefaultSound();
        }

        Sound sound = parseSound(soundName);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, finalVolume, finalPitch);
        }
    }

    /**
     * 播放场景化音效到指定位置
     */
    public void playSoundAt(SoundScene scene, org.bukkit.Location location, float volume, float pitch) {
        if (location == null || location.getWorld() == null) return;

        Sound sound = parseSound(scene.getDefaultSound());
        if (sound != null) {
            location.getWorld().playSound(location, sound, volume, pitch);
        }
    }

    /**
     * 播放成功提示音
     */
    public void playSuccess(Player player) {
        playSound(player, SoundScene.NOTIFY_SUCCESS);
    }

    /**
     * 播放失败提示音
     */
    public void playFailure(Player player) {
        playSound(player, SoundScene.NOTIFY_ERROR);
    }

    /**
     * 播放警告提示音
     */
    public void playWarning(Player player) {
        playSound(player, SoundScene.NOTIFY_WARNING);
    }

    /**
     * 播放通知提示音
     */
    public void playNotification(Player player) {
        playSound(player, SoundScene.NOTIFY_INFO);
    }

    /**
     * 播放UI点击音效
     */
    public void playClick(Player player) {
        playSound(player, SoundScene.UI_CLICK);
    }

    /**
     * 播放菜单打开音效
     */
    public void playMenuOpen(Player player) {
        playSound(player, SoundScene.UI_OPEN);
    }

    /**
     * 播放菜单关闭音效
     */
    public void playMenuClose(Player player) {
        playSound(player, SoundScene.UI_CLOSE);
    }

    /**
     * 播放菜单选择音效
     */
    public void playMenuSelect(Player player) {
        playSound(player, SoundScene.UI_SELECT);
    }

    /**
     * 播放确认音效
     */
    public void playConfirm(Player player) {
        playSound(player, SoundScene.UI_CONFIRM);
    }

    /**
     * 播放取消音效
     */
    public void playCancel(Player player) {
        playSound(player, SoundScene.UI_CANCEL);
    }

    /**
     * 播放导航音效
     */
    public void playNavigate(Player player, boolean forward) {
        if (forward) {
            playSound(player, SoundScene.NAV_NEXT);
        } else {
            playSound(player, SoundScene.NAV_PREVIOUS);
        }
    }

    /**
     * 播放国家创建音效
     */
    public void playNationCreate(Player player) {
        playSound(player, SoundScene.NATION_CREATE);
    }

    /**
     * 播放领土获取音效
     */
    public void playTerritoryClaim(Player player) {
        playSound(player, SoundScene.TERRITORY_CLAIM);
    }

    /**
     * 播放战争宣战音效
     */
    public void playWarDeclare(Player player) {
        playSound(player, SoundScene.WAR_DECLARE);
    }

    /**
     * 播放战争胜利音效
     */
    public void playWarVictory(Player player) {
        playSound(player, SoundScene.WAR_VICTORY);
        // 延迟播放额外音效增强效果
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            playSound(player, SoundScene.ACHIEVEMENT_UNLOCK, 0.6f, 1.3f);
        }, 15L);
    }

    /**
     * 播放战争失败音效
     */
    public void playWarDefeat(Player player) {
        playSound(player, SoundScene.WAR_DEFEAT);
    }

    /**
     * 播放经济收入音效
     */
    public void playEconomyIncome(Player player) {
        playSound(player, SoundScene.ECONOMY_INCOME);
    }

    /**
     * 播放经济支出音效
     */
    public void playEconomyExpense(Player player) {
        playSound(player, SoundScene.ECONOMY_EXPENSE);
    }

    /**
     * 播放奖励音效
     */
    public void playReward(Player player) {
        playSound(player, SoundScene.ECONOMY_REWARD);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            playSound(player, SoundScene.ACHIEVEMENT_UNLOCK, 0.5f, 1.4f);
        }, 8L);
    }

    /**
     * 播放科技解锁音效
     */
    public void playTechUnlock(Player player) {
        playSound(player, SoundScene.TECH_UNLOCK);
    }

    /**
     * 播放科技完成音效
     */
    public void playTechComplete(Player player) {
        playSound(player, SoundScene.TECH_COMPLETE);
    }

    /**
     * 播放成就解锁音效
     */
    public void playAchievementUnlock(Player player) {
        playSound(player, SoundScene.ACHIEVEMENT_UNLOCK);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            playSound(player, SoundScene.ACHIEVEMENT_RARE, 0.5f, 1.2f);
        }, 12L);
    }

    /**
     * 播放物品拾取音效
     */
    public void playItemPickup(Player player) {
        playSound(player, SoundScene.ITEM_PICKUP);
    }

    /**
     * 播放传送音效
     */
    public void playTeleport(Player player) {
        playSound(player, SoundScene.MOVE_TELEPORT);
    }

    /**
     * 播放传送回家音效
     */
    public void playHome(Player player) {
        playSound(player, SoundScene.MOVE_HOME);
    }

    /**
     * 播放升级音效
     */
    public void playLevelUp(Player player) {
        playSound(player, SoundScene.LEVEL_UP);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            playSound(player, SoundScene.ACHIEVEMENT_UNLOCK, 0.6f, 1.5f);
        }, 10L);
    }

    /**
     * 播放稀有发现音效
     */
    public void playRareFind(Player player) {
        playSound(player, SoundScene.RARE_FIND);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            playSound(player, SoundScene.MYSTERY, 0.4f, 1.4f);
        }, 6L);
    }

    /**
     * 播放外交音效
     */
    public void playDiplomacy(Player player, boolean accepted) {
        if (accepted) {
            playSound(player, SoundScene.DIPLOMACY_ACCEPT);
        } else {
            playSound(player, SoundScene.DIPLOMACY_REJECT);
        }
    }

    /**
     * 播放政策激活音效
     */
    public void playPolicyActivate(Player player) {
        playSound(player, SoundScene.POLICY_ACTIVATE);
    }

    /**
     * 播放官员任命音效
     */
    public void playOfficerAppoint(Player player) {
        playSound(player, SoundScene.OFFICER_APPOINT);
    }

    /**
     * 播放资源刷新音效
     */
    public void playResourceRefresh(Player player) {
        playSound(player, SoundScene.RESOURCE_REFRESH);
    }

    /**
     * 播放战斗音效
     */
    public void playBattle(Player player, boolean isAttacking) {
        if (isAttacking) {
            playSound(player, SoundScene.WAR_ATTACK);
        } else {
            playSound(player, SoundScene.WAR_DEFEND);
        }
    }

    /**
     * 获取玩家音效偏好
     */
    public SoundPreferences getPreferences(Player player) {
        return playerPreferences.computeIfAbsent(
            player.getUniqueId(),
            k -> new SoundPreferences()
        );
    }

    /**
     * 设置玩家主音量
     */
    public void setMasterVolume(Player player, float volume) {
        getPreferences(player).setMasterVolume(volume);
    }

    /**
     * 设置玩家UI音量
     */
    public void setUiVolume(Player player, float volume) {
        getPreferences(player).setUiVolume(volume);
    }

    /**
     * 设置玩家世界音量
     */
    public void setWorldVolume(Player player, float volume) {
        getPreferences(player).setWorldVolume(volume);
    }

    /**
     * 设置玩家通知音量
     */
    public void setNotificationVolume(Player player, float volume) {
        getPreferences(player).setNotificationVolume(volume);
    }

    /**
     * 设置玩家音调偏移
     */
    public void setPitchOffset(Player player, float offset) {
        getPreferences(player).setPitchOffset(offset);
    }

    /**
     * 静音/取消静音
     */
    public void setMuted(Player player, boolean muted) {
        getPreferences(player).setMuted(muted);
    }

    /**
     * 静音音效分组
     */
    public void muteGroup(Player player, SoundSceneGroup group) {
        getPreferences(player).muteGroup(group);
    }

    /**
     * 取消静音音效分组
     */
    public void unmuteGroup(Player player, SoundSceneGroup group) {
        getPreferences(player).unmuteGroup(group);
    }

    /**
     * 设置自定义音效
     */
    public void setCustomSound(Player player, SoundScene scene, String soundName) {
        getPreferences(player).setCustomSound(scene.getConfigKey(), soundName);
    }

    /**
     * 重置玩家音效偏好
     */
    public void resetPreferences(Player player) {
        playerPreferences.remove(player.getUniqueId());
    }

    /**
     * 重置所有玩家音效偏好
     */
    public void resetAllPreferences() {
        playerPreferences.clear();
    }

    /**
     * 根据场景获取分类音量
     */
    private float getCategoryVolume(SoundScene scene) {
        return switch (scene) {
            // UI类
            case UI_CLICK, UI_HOVER, UI_SELECT, UI_OPEN, UI_CLOSE, UI_BACK, UI_CONFIRM, UI_CANCEL -> 1.0f;
            // 导航类
            case NAV_NEXT, NAV_PREVIOUS -> 0.8f;
            // 通知类
            case NOTIFY_INFO, NOTIFY_WARNING, NOTIFY_ERROR, NOTIFY_SUCCESS, NOTIFY_URGENT -> 1.0f;
            // 社交类
            case SOCIAL_FRIEND, SOCIAL_MESSAGE, SOCIAL_GIFT -> 0.7f;
            // 物品类
            case ITEM_PICKUP, ITEM_DROP, ITEM_EQUIP, ITEM_USE, ITEM_CRAFT, ITEM_TRADE -> 0.6f;
            // 移动类
            case MOVE_TELEPORT, MOVE_WARP, MOVE_HOME -> 0.8f;
            // 成就类
            case ACHIEVEMENT_UNLOCK, ACHIEVEMENT_RARE -> 1.0f;
            // 特殊类
            case LEVEL_UP, RARE_FIND, MYSTERY, BOSS_APPEAR, BOSS_DEATH -> 1.0f;
            // 其他分类
            default -> 1.0f;
        };
    }

    /**
     * 解析音效名称
     */
    private Sound parseSound(String soundName) {
        return BukkitSoundResolver.resolve(soundName).orElse(null);
    }

    /**
     * 检查音效是否有效
     */
    public boolean isValidSound(String soundName) {
        return parseSound(soundName) != null;
    }

    /**
     * 获取所有可用音效名称（用于帮助/配置）
     */
    public String[] getAvailableSounds() {
        return BukkitSoundResolver.names()
            .toArray(String[]::new);
    }

    /**
     * 获取场景列表
     */
    public SoundScene[] getAllScenes() {
        return SoundScene.values();
    }

    /**
     * 获取分组列表
     */
    public SoundSceneGroup[] getAllGroups() {
        return SoundSceneGroup.values();
    }
}
