package dev.starcore.starcore.foundation.animation;

import dev.starcore.starcore.foundation.sound.BukkitSoundResolver;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 声音反馈管理器
 * 提供各种游戏音效反馈
 */
public final class SoundFeedbackManager {
    private final Plugin plugin;

    // 声音类型枚举
    public enum SoundType {
        // UI 声音
        CLICK("UI_BUTTON_CLICK", 0.4f, 1.2f),
        OPEN("BLOCK_CHEST_OPEN", 0.5f, 1.0f),
        CLOSE("BLOCK_CHEST_CLOSE", 0.5f, 1.0f),
        HOVER("BLOCK_STONE_BUTTON_CLICK_ON", 0.2f, 1.5f),
        SELECT("UI_CARTOGRAPHY_TABLE_TAKE_RESULT", 0.4f, 1.3f),

        // 导航声音
        NEXT("UI_LOOM_SELECT_PATTERN", 0.3f, 1.2f),
        PREVIOUS("UI_LOOM_SELECT_PATTERN", 0.3f, 0.9f),
        BACK("BLOCK_NOTE_BLOCK_BASS", 0.3f, 0.8f),

        // 成功/失败
        SUCCESS("ENTITY_EXPERIENCE_ORB_PICKUP", 0.5f, 1.2f),
        FAILURE("ENTITY_VILLAGER_NO", 0.5f, 0.8f),
        WARNING("BLOCK_NOTE_BLOCK_BASS", 0.5f, 0.7f),

        // 奖励相关
        REWARD("ENTITY_PLAYER_LEVELUP", 0.5f, 1.3f),
        RARE_FIND("UI_TOAST_CHALLENGE_COMPLETE", 0.8f, 1.0f),
        ACHIEVEMENT("ENTITY_PLAYER_LEVELUP", 0.6f, 1.5f),

        // 物品相关
        PICKUP("ENTITY_ITEM_PICKUP", 0.4f, 1.2f),
        DROP("ENTITY_ITEM_PICKUP", 0.3f, 0.8f),
        EQUIP("ENTITY_ARMOR_STAND_HIT", 0.4f, 1.1f),
        USE("ENTITY_SPLASH_POTION_BREAK", 0.4f, 1.0f),

        // 交互相关
        ACCEPT("BLOCK_NOTE_BLOCK_PLING", 0.5f, 1.5f),
        REJECT("BLOCK_NOTE_BLOCK_BANJO", 0.4f, 0.7f),
        CONFIRM("BLOCK_BEACON_ACTIVATE", 0.5f, 1.2f),

        // 动画音效
        TRANSITION("BLOCK_NOTE_BLOCK_PLING", 0.3f, 1.0f),
        TELEPORT("ENTITY_ENDERMAN_TELEPORT", 0.5f, 1.0f),
        EXPLOSION("ENTITY_GENERIC_EXPLODE", 0.7f, 1.0f),
        MAGIC("ENTITY_EVOCATION_ILLAGER_CAST_SPELL", 0.5f, 1.3f),

        // 通知声音
        MESSAGE("BLOCK_NOTE_BLOCK_HAT", 0.3f, 1.0f),
        ALERT("BLOCK_NOTE_BLOCK_BELL", 0.6f, 1.2f),
        NOTIFICATION("ENTITY_VILLAGER_CELEBRATE", 0.4f, 1.0f),

        // 错误声音
        ERROR("ENTITY_VILLAGER_NO", 0.5f, 0.7f),
        DENIED("ENTITY_ZOMBIE_VILLAGER_CURE", 0.4f, 0.8f),

        // 菜单专属
        MENU_OPEN("BLOCK_COMPARATOR_CLICK", 0.4f, 1.1f),
        MENU_CLOSE("BLOCK_COMPARATOR_CLICK", 0.3f, 0.9f),
        MENU_SELECT("UI_STONECUTTER_SELECT_RECIPE", 0.4f, 1.2f),
        MENU_CANCEL("BLOCK_NOTE_BLOCK_GUITAR", 0.3f, 0.8f),

        // 国家/战争相关
        WAR_DECLARE("ENTITY_WITHER_SPAWN", 0.7f, 0.8f),
        WAR_END("ENTITY_WITHER_DEATH", 0.6f, 1.0f),
        VICTORY("UI_VICTORY_SCREEN_WIN", 0.6f, 1.2f),
        DEFEAT("ENTITY_WITHER_DEATH", 0.5f, 0.6f);

        private final String soundName;
        private final float defaultVolume;
        private final float defaultPitch;

        SoundType(String soundName, float defaultVolume, float defaultPitch) {
            this.soundName = soundName;
            this.defaultVolume = defaultVolume;
            this.defaultPitch = defaultPitch;
        }

        public String getSoundName() { return soundName; }
        public float getDefaultVolume() { return defaultVolume; }
        public float getDefaultPitch() { return defaultPitch; }
    }

    // 玩家音量/音调偏好 - 使用ConcurrentHashMap保证线程安全
    private final Map<UUID, Float> volumePreferences = new ConcurrentHashMap<>();
    private final Map<UUID, Float> pitchPreferences = new ConcurrentHashMap<>();

    public SoundFeedbackManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 播放指定类型的音效
     */
    public void play(Player player, SoundType type) {
        play(player, type, type.getDefaultVolume(), type.getDefaultPitch());
    }

    /**
     * 播放指定类型和音量的音效
     */
    public void play(Player player, SoundType type, float volume) {
        play(player, type, volume, type.getDefaultPitch());
    }

    /**
     * 播放指定类型的音效（自定义音量音调）
     */
    public void play(Player player, SoundType type, float volume, float pitch) {
        if (player == null || !player.isOnline()) return;

        Sound sound = getSound(type.getSoundName());
        if (sound == null) return;

        // 应用玩家偏好
        UUID playerId = player.getUniqueId();
        float finalVolume = volume;
        float finalPitch = pitch;

        if (volumePreferences.containsKey(playerId)) {
            finalVolume = volume * volumePreferences.get(playerId);
        }
        if (pitchPreferences.containsKey(playerId)) {
            finalPitch = pitch * pitchPreferences.get(playerId);
        }

        player.playSound(player.getLocation(), sound, finalVolume, finalPitch);
    }

    private Sound getSound(String soundName) {
        return BukkitSoundResolver.resolve(soundName).orElse(null);
    }

    /**
     * 播放音效到目标位置
     */
    public void playAt(SoundType type, org.bukkit.Location location, float volume, float pitch) {
        if (location == null || location.getWorld() == null) return;

        Sound sound = getSound(type.getSoundName());
        if (sound == null) return;
        location.getWorld().playSound(location, sound, volume, pitch);
    }

    /**
     * 播放UI音效组合
     */
    public void playUiSound(Player player) {
        play(player, SoundType.CLICK);
    }

    /**
     * 播放成功反馈
     */
    public void playSuccess(Player player) {
        play(player, SoundType.SUCCESS);
    }

    /**
     * 播放失败反馈
     */
    public void playFailure(Player player) {
        play(player, SoundType.FAILURE);
    }

    /**
     * 播放奖励音效
     */
    public void playReward(Player player) {
        play(player, SoundType.REWARD);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            play(player, SoundType.ACHIEVEMENT);
        }, 10L);
    }

    /**
     * 播放稀有物品发现音效
     */
    public void playRareFind(Player player) {
        play(player, SoundType.RARE_FIND, 0.8f, 1.0f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            play(player, SoundType.MAGIC, 0.4f, 1.5f);
        }, 5L);
    }

    /**
     * 播放菜单打开音效
     */
    public void playMenuOpen(Player player) {
        play(player, SoundType.MENU_OPEN);
    }

    /**
     * 播放菜单关闭音效
     */
    public void playMenuClose(Player player) {
        play(player, SoundType.MENU_CLOSE);
    }

    /**
     * 播放菜单选择音效
     */
    public void playMenuSelect(Player player) {
        play(player, SoundType.MENU_SELECT);
    }

    /**
     * 播放菜单取消音效
     */
    public void playMenuCancel(Player player) {
        play(player, SoundType.MENU_CANCEL);
    }

    /**
     * 播放确认音效
     */
    public void playConfirm(Player player) {
        play(player, SoundType.CONFIRM);
    }

    /**
     * 播放拒绝音效
     */
    public void playReject(Player player) {
        play(player, SoundType.REJECT);
    }

    /**
     * 播放警告音效
     */
    public void playWarning(Player player) {
        play(player, SoundType.WARNING);
    }

    /**
     * 播放导航音效
     */
    public void playNavigate(Player player) {
        play(player, SoundType.NEXT);
    }

    /**
     * 播放选中音效
     */
    public void playSelect(Player player) {
        play(player, SoundType.SELECT);
    }

    /**
     * 播放通知音效
     */
    public void playNotification(Player player) {
        play(player, SoundType.NOTIFICATION);
    }

    /**
     * 播放过渡动画音效
     */
    public void playTransition(Player player) {
        play(player, SoundType.TRANSITION);
    }

    /**
     * 播放传送音效
     */
    public void playTeleport(Player player) {
        play(player, SoundType.TELEPORT);
    }

    /**
     * 播放战争相关音效
     */
    public void playWarDeclare(Player player) {
        play(player, SoundType.WAR_DECLARE, 0.8f, 0.8f);
    }

    /**
     * 播放胜利音效
     */
    public void playVictory(Player player) {
        play(player, SoundType.VICTORY, 0.7f, 1.2f);
    }

    /**
     * 播放失败音效
     */
    public void playDefeat(Player player) {
        play(player, SoundType.DEFEAT);
    }

    /**
     * 设置玩家音量偏好
     */
    public void setVolumePreference(Player player, float volume) {
        volumePreferences.put(player.getUniqueId(), Math.max(0f, Math.min(2f, volume)));
    }

    /**
     * 设置玩家音调偏好
     */
    public void setPitchPreference(Player player, float pitch) {
        pitchPreferences.put(player.getUniqueId(), Math.max(0.5f, Math.min(2f, pitch)));
    }

    /**
     * 获取玩家音量偏好
     */
    public float getVolumePreference(Player player) {
        return volumePreferences.getOrDefault(player.getUniqueId(), 1.0f);
    }

    /**
     * 获取玩家音调偏好
     */
    public float getPitchPreference(Player player) {
        return pitchPreferences.getOrDefault(player.getUniqueId(), 1.0f);
    }

    /**
     * 重置玩家音效偏好
     */
    public void resetPreferences(Player player) {
        UUID playerId = player.getUniqueId();
        volumePreferences.remove(playerId);
        pitchPreferences.remove(playerId);
    }
}
