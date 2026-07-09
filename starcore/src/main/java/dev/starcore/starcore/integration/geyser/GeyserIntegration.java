package dev.starcore.starcore.integration.geyser;

import dev.starcore.starcore.StarCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Geyser/Floodgate 集成管理器
 * 支持基岩版(Bedrock)玩家识别和跨平台游戏
 */
public class GeyserIntegration {

    private static GeyserIntegration instance;
    private final StarCorePlugin plugin;
    private boolean geyserAvailable = false;
    private boolean floodgateAvailable = false;

    // 反射获取Floodgate API
    private Class<?> floodgateApiClass;
    private java.lang.reflect.Method isBedrockMethod;
    private java.lang.reflect.Method getPlayerTypeMethod;

    public GeyserIntegration(Plugin plugin) {
        this.plugin = (StarCorePlugin) plugin;
        checkGeyserAvailability();
    }

    /**
     * 检查Geyser/Floodgate是否可用
     */
    private void checkGeyserAvailability() {
        // 检查Floodgate
        Plugin floodgate = plugin.getServer().getPluginManager().getPlugin("Floodgate");
        if (floodgate != null && floodgate.isEnabled()) {
            floodgateAvailable = true;
            plugin.getLogger().info("✓ Floodgate 已检测到 - 基岩版支持已启用");
            initFloodgate反射();
        }

        // 检查Geyser
        Plugin geyser = plugin.getServer().getPluginManager().getPlugin("Geyser");
        if (geyser != null && geyser.isEnabled()) {
            geyserAvailable = true;
            plugin.getLogger().info("✓ Geyser 已检测到 - 跨平台支持已启用");
        }
    }

    /**
     * 初始化Floodgate反射调用
     */
    private void initFloodgate反射() {
        try {
            // 尝试获取Floodgate API
            Class<?> managerClass = Class.forName("net.geysermc.geyserapi.FloodgateApi");
            java.lang.reflect.Method getInstance = managerClass.getMethod("getInstance");
            Object apiInstance = getInstance.invoke(null);

            floodgateApiClass = managerClass;
            isBedrockMethod = managerClass.getMethod("isBedrockPlayer", UUID.class);
            getPlayerTypeMethod = managerClass.getMethod("getPlayerType", UUID.class);

            plugin.getLogger().info("✓ Floodgate API 反射初始化成功");
        } catch (Exception e) {
            // 尝试旧版API
            try {
                Class<?> floodgateClass = Class.forName("com.github.william278.floodgateapi.FloodgateApi");
                java.lang.reflect.Method getInstance = floodgateClass.getMethod("getInstance");
                Object apiInstance = getInstance.invoke(null);

                floodgateApiClass = floodgateClass;
                isBedrockMethod = floodgateClass.getMethod("isBedrockPlayer", UUID.class);

                plugin.getLogger().info("✓ Floodgate API (旧版) 反射初始化成功");
            } catch (Exception ex) {
                plugin.getLogger().warning("⚠ Floodgate API 反射初始化失败: " + ex.getMessage());
            }
        }
    }

    /**
     * 检查玩家是否为基岩版玩家
     */
    public boolean isBedrockPlayer(Player player) {
        return isBedrockPlayer(player.getUniqueId());
    }

    /**
     * 检查玩家是否为基岩版玩家
     */
    public boolean isBedrockPlayer(UUID uuid) {
        if (!floodgateAvailable || isBedrockMethod == null) {
            return false;
        }

        try {
            Object result = isBedrockMethod.invoke(null, uuid);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取玩家类型 (JAVA/BEDROCK)
     */
    public String getPlayerType(Player player) {
        return getPlayerType(player.getUniqueId());
    }

    /**
     * 获取玩家类型
     */
    public String getPlayerType(UUID uuid) {
        if (!floodgateAvailable || getPlayerTypeMethod == null) {
            return "JAVA";
        }

        try {
            Object result = getPlayerTypeMethod.invoke(null, uuid);
            return result != null ? result.toString() : "JAVA";
        } catch (Exception e) {
            return "JAVA";
        }
    }

    /**
     * 检查Geyser是否可用
     */
    public boolean isGeyserAvailable() {
        return geyserAvailable;
    }

    /**
     * 检查Floodgate是否可用
     */
    public boolean isFloodgateAvailable() {
        return floodgateAvailable;
    }

    /**
     * 获取基岩版玩家的Xbox ID
     */
    public String getXboxId(Player player) {
        return getXboxId(player.getUniqueId());
    }

    /**
     * 获取基岩版玩家的Xbox ID
     */
    public String getXboxId(UUID uuid) {
        if (!floodgateAvailable) {
            return null;
        }

        try {
            if (floodgateApiClass != null) {
                // 新版API
                java.lang.reflect.Method getXuid = floodgateApiClass.getMethod("getXuid", UUID.class);
                Object xuid = getXuid.invoke(null, uuid);
                return xuid != null ? xuid.toString() : null;
            }
        } catch (Exception e) {
            // 忽略
        }
        return null;
    }

    /**
     * 获取基岩版玩家的设备信息
     */
    public String getDeviceInfo(Player player) {
        if (isBedrockPlayer(player)) {
            return "基岩版 (Bedrock)";
        }
        return "Java版";
    }

    /**
     * 发送基岩版原生表单 (支持更多功能)
     */
    public void sendBedrockForm(Player player, Object form) {
        if (!geyserAvailable) return;

        try {
            // 通过Geyser发送表单
            Class<?> geyserClass = Class.forName("org.geysermc.geyser.GeyserImpl");
            java.lang.reflect.Method getInstance = geyserClass.getMethod("getInstance");
            Object geyser = getInstance.invoke(null);

            java.lang.reflect.Method getSession = geyserClass.getMethod("getSession", UUID.class);
            Object session = getSession.invoke(geyser, player.getUniqueId());

            if (session != null) {
                java.lang.reflect.Method sendForm = session.getClass().getMethod("sendForm", Class.forName("com.nimbusdeditor.form.Form"));
                sendForm.invoke(session, form);
            }
        } catch (Exception e) {
            // Geyser表单发送失败，静默处理
        }
    }

    /**
     * 检查是否应该使用特殊处理 (基岩版限制)
     */
    public boolean needsSpecialHandling(Player player) {
        return isBedrockPlayer(player);
    }
}
