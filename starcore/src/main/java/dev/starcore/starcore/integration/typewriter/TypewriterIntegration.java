package dev.starcore.starcore.integration.typewriter;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

/**
 * Typewriter 集成
 * 支持任务系统、对话系统、剧情系统
 *
 * Typewriter 是一个强大的任务和对话插件
 * 官网: https://github.com/gabber235/Typewriter
 */
public final class TypewriterIntegration {
    private final Plugin plugin;
    private boolean enabled = false;

    // Typewriter API 引用
    private Object typewriterPlugin;

    public TypewriterIntegration(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化集成
     */
    public boolean init() {
        try {
            // 检测 Typewriter 插件
            Plugin typewriter = plugin.getServer().getPluginManager().getPlugin("Typewriter");
            if (typewriter == null) {
                plugin.getLogger().info("⚠️ Typewriter 未安装");
                return false;
            }

            if (!plugin.getServer().getPluginManager().isPluginEnabled("Typewriter")) {
                plugin.getLogger().warning("⚠️ Typewriter 已安装但未启用");
                return false;
            }

            // 检查 API 类是否存在
            try {
                Class.forName("me.gabber235.typewriter.Typewriter");
                Class.forName("me.gabber235.typewriter.entry.Entry");
                Class.forName("me.gabber235.typewriter.entry.Quest");
            } catch (ClassNotFoundException e) {
                plugin.getLogger().warning("⚠️ Typewriter API 未找到（可能版本不兼容）");
                return false;
            }

            this.typewriterPlugin = typewriter;
            this.enabled = true;
            plugin.getLogger().info("✅ Typewriter 集成已启用");
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("⚠️ Typewriter 集成初始化失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 触发对话
     */
    public boolean triggerDialogue(Player player, String dialogueId) {
        if (!enabled || player == null || dialogueId == null) {
            return false;
        }

        try {
            // 使用 UUID 代替玩家名防止命令注入
            plugin.getServer().dispatchCommand(
                plugin.getServer().getConsoleSender(),
                "tw trigger " + player.getUniqueId() + " " + dialogueId
            );
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("触发对话失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 开始任务
     */
    public boolean startQuest(Player player, String questId) {
        if (!enabled || player == null || questId == null) {
            return false;
        }

        try {
            // 使用 UUID 代替玩家名防止命令注入
            plugin.getServer().dispatchCommand(
                plugin.getServer().getConsoleSender(),
                "tw quest start " + player.getUniqueId() + " " + questId
            );
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("开始任务失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 完成任务
     */
    public boolean completeQuest(Player player, String questId) {
        if (!enabled || player == null || questId == null) {
            return false;
        }

        try {
            plugin.getServer().dispatchCommand(
                plugin.getServer().getConsoleSender(),
                "tw quest complete " + player.getUniqueId() + " " + questId
            );
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("完成任务失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查任务状态
     */
    public boolean hasCompletedQuest(Player player, String questId) {
        if (!enabled || player == null || questId == null) {
            return false;
        }

        try {
            // 这里需要实际的 Typewriter API
            // 简化实现：通过权限或标签检查
            return player.hasPermission("typewriter.quest." + questId + ".completed");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查任务是否进行中
     */
    public boolean isQuestActive(Player player, String questId) {
        if (!enabled || player == null || questId == null) {
            return false;
        }

        try {
            return player.hasPermission("typewriter.quest." + questId + ".active");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 触发事件
     */
    public boolean triggerEvent(Player player, String eventId) {
        if (!enabled || player == null || eventId == null) {
            return false;
        }

        try {
            plugin.getServer().dispatchCommand(
                plugin.getServer().getConsoleSender(),
                "tw event " + player.getUniqueId() + " " + eventId
            );
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("触发事件失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 设置任务目标进度
     */
    public boolean setQuestProgress(Player player, String questId, String objectiveId, int progress) {
        if (!enabled || player == null) {
            return false;
        }

        try {
            plugin.getServer().dispatchCommand(
                plugin.getServer().getConsoleSender(),
                String.format("tw quest progress %s %s %s %d",
                    player.getUniqueId(), questId, objectiveId, progress)
            );
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("设置任务进度失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 播放剧情
     */
    public boolean playCinematic(Player player, String cinematicId) {
        if (!enabled || player == null || cinematicId == null) {
            return false;
        }

        try {
            plugin.getServer().dispatchCommand(
                plugin.getServer().getConsoleSender(),
                "tw cinematic " + player.getUniqueId() + " " + cinematicId
            );
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("播放剧情失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 显示标题消息（Typewriter 样式）
     */
    public boolean showTitle(Player player, String title, String subtitle) {
        if (!enabled || player == null) {
            return false;
        }

        try {
            player.sendTitle(title, subtitle, 10, 70, 20);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 添加任务标记
     */
    public boolean addQuestMarker(Player player, String questId, org.bukkit.Location location) {
        if (!enabled || player == null || location == null) {
            return false;
        }

        try {
            // Typewriter 的任务标记功能
            // 实际实现取决于 API
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 移除任务标记
     */
    public boolean removeQuestMarker(Player player, String questId) {
        if (!enabled || player == null) {
            return false;
        }

        try {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 重载 Typewriter
     */
    public boolean reload() {
        if (!enabled) {
            return false;
        }

        try {
            plugin.getServer().dispatchCommand(
                plugin.getServer().getConsoleSender(),
                "tw reload"
            );
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("重载 Typewriter 失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 是否已启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 获取 Typewriter 插件实例
     */
    public Optional<Plugin> getTypewriterPlugin() {
        return Optional.ofNullable((Plugin) typewriterPlugin);
    }
}
