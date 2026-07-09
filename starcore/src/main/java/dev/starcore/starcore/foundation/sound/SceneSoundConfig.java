package dev.starcore.starcore.foundation.sound;

import dev.starcore.starcore.foundation.sound.SceneSoundService.SoundScene;
import dev.starcore.starcore.foundation.sound.SceneSoundService.SoundSceneGroup;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * 场景化音效配置管理器
 * 处理音效配置的加载、保存和热重载
 */
public final class SceneSoundConfig {

    private final JavaPlugin plugin;
    private final File configFile;
    private FileConfiguration config;

    // 配置文件路径
    private static final String CONFIG_FILE_NAME = "scene-sounds.yml";

    // 配置路径前缀
    private static final String PREFIX_ENABLED = "enabled";
    private static final String PREFIX_VOLUME = "volume";
    private static final String PREFIX_PITCH = "pitch";
    private static final String PREFIX_SOUND = "sound";
    private static final String PREFIX_PLAYER_PREFS = "player-preferences";

    public SceneSoundConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE_NAME);
    }

    /**
     * 加载配置
     */
    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource(CONFIG_FILE_NAME, false);
        }
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    /**
     * 保存配置
     */
    public void save() {
        plugin.saveConfig();
    }

    /**
     * 检查音效系统是否启用
     */
    public boolean isEnabled() {
        return config.getBoolean("enabled", true);
    }

    /**
     * 设置音效系统启用状态
     */
    public void setEnabled(boolean enabled) {
        config.set("enabled", enabled);
        save();
    }

    /**
     * 获取场景音效配置
     */
    public SoundConfig getSoundConfig(SoundScene scene) {
        String path = "sounds." + scene.getConfigKey();
        return new SoundConfig(
            config.getBoolean(path + "." + PREFIX_ENABLED, true),
            config.getString(path + "." + PREFIX_SOUND, scene.getDefaultSound()),
            (float) config.getDouble(path + "." + PREFIX_VOLUME, scene.getDefaultVolume()),
            (float) config.getDouble(path + "." + PREFIX_PITCH, scene.getDefaultPitch())
        );
    }

    /**
     * 设置场景音效配置
     */
    public void setSoundConfig(SoundScene scene, SoundConfig soundConfig) {
        String path = "sounds." + scene.getConfigKey();
        config.set(path + "." + PREFIX_ENABLED, soundConfig.enabled());
        config.set(path + "." + PREFIX_SOUND, soundConfig.sound());
        config.set(path + "." + PREFIX_VOLUME, soundConfig.volume());
        config.set(path + "." + PREFIX_PITCH, soundConfig.pitch());
        save();
    }

    /**
     * 重置场景音效为默认值
     */
    public void resetSoundConfig(SoundScene scene) {
        String path = "sounds." + scene.getConfigKey();
        config.set(path, null);
        save();
    }

    /**
     * 获取分组音效配置
     */
    public GroupSoundConfig getGroupConfig(SoundSceneGroup group) {
        String path = "groups." + group.name().toLowerCase();
        return new GroupSoundConfig(
            config.getBoolean(path + "." + PREFIX_ENABLED, true),
            (float) config.getDouble(path + "." + PREFIX_VOLUME, 1.0)
        );
    }

    /**
     * 设置分组音效配置
     */
    public void setGroupConfig(SoundSceneGroup group, GroupSoundConfig groupConfig) {
        String path = "groups." + group.name().toLowerCase();
        config.set(path + "." + PREFIX_ENABLED, groupConfig.enabled());
        config.set(path + "." + PREFIX_VOLUME, groupConfig.volume());
        save();
    }

    /**
     * 获取全局默认音量
     */
    public float getDefaultVolume() {
        return (float) config.getDouble("defaults.volume", 1.0);
    }

    /**
     * 设置全局默认音量
     */
    public void setDefaultVolume(float volume) {
        config.set("defaults.volume", Math.max(0f, Math.min(2f, volume)));
        save();
    }

    /**
     * 获取全局默认音调
     */
    public float getDefaultPitch() {
        return (float) config.getDouble("defaults.pitch", 1.0);
    }

    /**
     * 设置全局默认音调
     */
    public void setDefaultPitch(float pitch) {
        config.set("defaults.pitch", Math.max(0.5f, Math.min(2f, pitch)));
        save();
    }

    /**
     * 获取玩家偏好
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPlayerPreferences(UUID playerId) {
        String path = PREFIX_PLAYER_PREFS + "." + playerId.toString();
        Map<String, Object> prefs = config.getConfigurationSection(path).getValues(false);
        return prefs != null ? prefs : new HashMap<>();
    }

    /**
     * 保存玩家偏好
     */
    public void savePlayerPreferences(UUID playerId, Map<String, Object> prefs) {
        String path = PREFIX_PLAYER_PREFS + "." + playerId.toString();
        for (Map.Entry<String, Object> entry : prefs.entrySet()) {
            config.set(path + "." + entry.getKey(), entry.getValue());
        }
        save();
    }

    /**
     * 删除玩家偏好
     */
    public void removePlayerPreferences(UUID playerId) {
        String path = PREFIX_PLAYER_PREFS + "." + playerId.toString();
        config.set(path, null);
        save();
    }

    /**
     * 生成默认配置文件内容
     */
    public String generateDefaultConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ===============================================\n");
        sb.append("# StarCore 场景化音效配置文件\n");
        sb.append("# ===============================================\n\n");

        sb.append("# 是否启用场景化音效系统\n");
        sb.append("enabled: true\n\n");

        sb.append("# 全局默认值\n");
        sb.append("defaults:\n");
        sb.append("  # 默认音量 (0.0 - 2.0)\n");
        sb.append("  volume: 1.0\n");
        sb.append("  # 默认音调 (0.5 - 2.0)\n");
        sb.append("  pitch: 1.0\n\n");

        sb.append("# 场景音效配置\n");
        sb.append("# 格式：\n");
        sb.append("#   enabled: true/false - 是否启用\n");
        sb.append("#   sound: SOUND_NAME - 音效名称 (使用 Bukkit Sound 枚举名)\n");
        sb.append("#   volume: 0.0-2.0 - 音量\n");
        sb.append("#   pitch: 0.5-2.0 - 音调\n\n");

        for (SoundSceneGroup group : SoundSceneGroup.values()) {
            sb.append("  # ").append(group.name()).append(" 分组\n");
            for (SoundScene scene : group.getScenes()) {
                sb.append("  ").append(scene.getConfigKey()).append(":\n");
                sb.append("    enabled: true\n");
                sb.append("    sound: ").append(scene.getDefaultSound()).append("\n");
                sb.append("    volume: ").append(scene.getDefaultVolume()).append("\n");
                sb.append("    pitch: ").append(scene.getDefaultPitch()).append("\n\n");
            }
        }

        return sb.toString();
    }

    /**
     * 音效配置记录
     */
    public record SoundConfig(boolean enabled, String sound, float volume, float pitch) {}

    /**
    * 分组音效配置记录
    */
    public record GroupSoundConfig(boolean enabled, float volume) {}

    /**
     * 创建默认配置文件
     */
    public void createDefaultConfig() {
        if (!configFile.exists()) {
            try {
                plugin.saveResource(CONFIG_FILE_NAME, false);
            } catch (Exception e) {
                // 如果资源文件不存在，手动创建
                plugin.saveResource("config.yml", false);
            }
        }
    }

    /**
     * 获取配置文件的绝对路径
     */
    public String getConfigFilePath() {
        return configFile.getAbsolutePath();
    }

    /**
     * 检查配置文件是否存在
     */
    public boolean configExists() {
        return configFile.exists();
    }

    /**
     * 获取所有已配置的玩家ID
     */
    @SuppressWarnings("unchecked")
    public Set<UUID> getConfiguredPlayerIds() {
        Set<UUID> ids = new HashSet<>();
        ConfigurationSection section = config.getConfigurationSection(PREFIX_PLAYER_PREFS);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    ids.add(UUID.fromString(key));
                } catch (IllegalArgumentException ignored) {
                    // 忽略无效的UUID
                }
            }
        }
        return ids;
    }

    /**
     * 导出配置到指定文件
     */
    public void exportToFile(File file) {
        try {
            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write(generateDefaultConfig());
            writer.close();
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("无法导出音效配置: " + e.getMessage());
        }
    }
}
