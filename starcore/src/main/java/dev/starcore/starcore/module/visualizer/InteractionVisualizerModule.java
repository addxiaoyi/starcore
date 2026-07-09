package dev.starcore.starcore.module.visualizer;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StarCore InteractionVisualizer 集成模块
 *
 * 使用反射调用 InteractionVisualizer API 显示:
 * - 国家领地信息
 * - 资源区块提示
 * - 商店/NPC 交互信息
 * - 战争状态显示
 *
 * 依赖: 需要安装 InteractionVisualizer 插件
 */
public final class InteractionVisualizerModule implements StarCoreModule {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "interaction_visualizer",
        "交互可视化",
        ModuleLayer.FEATURE,
        List.of(),
        List.of(),
        "StarCore Integration for InteractionVisualizer - 显示领地/资源/商店信息"
    );

    private Plugin plugin;
    private boolean enabled;
    private boolean ivAvailable;

    // 反射方法缓存
    private final Map<String, Method> ivMethods = new HashMap<>();
    private Object ivInstance;

    // 玩家状态管理
    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();

    // 玩家待处理更新
    private final Map<UUID, List<Runnable>> pendingUpdates = new HashMap<>();

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();

        // 检查 InteractionVisualizer 是否安装
        if (!checkIVAvailable()) {
            plugin.getLogger().warning("InteractionVisualizer 未安装! 交互可视化模块已禁用。");
            plugin.getLogger().info("请从 https://www.spigotmc.org/resources/ 搜索 InteractionVisualizer 下载安装");
            this.enabled = false;
            this.ivAvailable = false;
            return;
        }

        this.enabled = true;
        this.ivAvailable = true;

        // 注册反射方法
        setupReflection();

        // 注册事件监听器
        registerListener();

        plugin.getLogger().info("✅ InteractionVisualizer 集成已启用!");
        plugin.getLogger().info("   - 国家领地信息显示");
        plugin.getLogger().info("   - 资源区块提示");
        plugin.getLogger().info("   - 商店交互信息");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 注销事件监听器
        unregisterListener();

        if (ivAvailable) {
            try {
                // 尝试注销 IV 模块
                Method unregisterModule = ivMethods.get("unregisterModule");
                if (unregisterModule != null && ivInstance != null) {
                    unregisterModule.invoke(ivInstance);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("注销 IV 模块时出错: " + e.getMessage());
            }
        }

        pendingUpdates.clear();
        activePlayers.clear();
        plugin.getLogger().info("InteractionVisualizer 集成已禁用");
    }

    private boolean checkIVAvailable() {
        Plugin iv = Bukkit.getPluginManager().getPlugin("InteractionVisualizer");
        return iv != null && iv.isEnabled();
    }

    private void setupReflection() {
        try {
            Class<?> apiClass = Class.forName("com.loohp.interactionvisualizer.api.InteractionVisualizerAPI");
            ivInstance = apiClass.getMethod("getInstance").invoke(null);

            // 缓存常用方法
            cacheMethod("isEnabled", apiClass, "isEnabled");
            cacheMethod("registerModule", apiClass, "registerModule", Class.forName("com.loohp.interactionvisualizer.api.modules.Module"));
            cacheMethod("unregisterModule", apiClass, "unregisterModule", Class.forName("com.loohp.interactionvisualizer.api.modules.Module"));

            plugin.getLogger().info("IV API 反射初始化成功");
        } catch (Exception e) {
            plugin.getLogger().warning("IV API 反射初始化失败: " + e.getMessage());
            this.ivAvailable = false;
        }
    }

    private void cacheMethod(String key, Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            ivMethods.put(key, clazz.getMethod(methodName, paramTypes));
        } catch (Exception e) {
            plugin.getLogger().warning("无法缓存方法 " + methodName + ": " + e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isIVAvailable() {
        return ivAvailable;
    }

    void registerListener() {
        if (listener == null) {
            listener = new VisualizerListener(this);
            plugin.getServer().getPluginManager().registerEvents(listener, (org.bukkit.plugin.java.JavaPlugin) plugin);
        }
    }

    void unregisterListener() {
        if (listener != null) {
            listener.unregister();
            listener = null;
        }
    }

    private VisualizerListener listener;

    // ===== 公开 API =====

    /**
     * 玩家加入时调用
     */
    public void onPlayerJoin(Player player) {
        activePlayers.add(player.getUniqueId());
        plugin.getLogger().fine("StarCore: 玩家 " + player.getName() + " 已加入 IV 集成");
    }

    /**
     * 玩家离开时调用
     */
    public void onPlayerQuit(Player player) {
        activePlayers.remove(player.getUniqueId());
    }

    /**
     * 方块交互时调用
     */
    public void onBlockInteract(Player player, Block block) {
        // 获取该方块的 StarCore 相关条目
        VisualizerEntry.fromBlock(block).ifPresent(entry -> {
            if (enabled) {
                plugin.getLogger().fine("Player " + player.getName() + " interacted with " + entry.key());
            }
        });
    }

    /**
     * 放置方块时调用
     */
    public void onBlockPlace(Player player, Block block) {
        // 可以在这里触发领地检查等逻辑
    }

    /**
     * 破坏方块时调用
     */
    public void onBlockBreak(Player player, Block block) {
        // 清理相关的 IV 显示
    }

    /**
     * 获取领地信息的行
     */
    public List<String> getTerritoryLines(Block block, Player player) {
        List<String> lines = new ArrayList<>();
        // TODO: 接入 NationService 获取真实数据
        lines.add("§6§l⛏ 领地信息");
        lines.add("§e国家: 无");
        lines.add("§7点击查看详情");
        return lines;
    }

    /**
     * 获取资源区块信息
     */
    public List<String> getResourceLines(Material type, Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("§b§l⛏ " + getResourceName(type));
        return lines;
    }

    private String getResourceName(Material type) {
        if (type == null) return "资源";
        return switch (type) {
            case DIAMOND_ORE -> "钻石矿";
            case GOLD_ORE -> "金矿";
            case IRON_ORE -> "铁矿";
            case COAL_ORE -> "煤矿";
            case COPPER_ORE -> "铜矿";
            case EMERALD_ORE -> "绿宝石矿";
            case LAPIS_ORE -> "青金石矿";
            case REDSTONE_ORE -> "红石矿";
            case NETHER_GOLD_ORE -> "下界金矿";
            case DEEPSLATE_DIAMOND_ORE -> "深层钻石矿";
            case DEEPSLATE_GOLD_ORE -> "深层金矿";
            case DEEPSLATE_IRON_ORE -> "深层铁矿";
            case DEEPSLATE_COPPER_ORE -> "深层铜矿";
            case DEEPSLATE_EMERALD_ORE -> "深层绿宝石矿";
            case DEEPSLATE_LAPIS_ORE -> "深层青金石矿";
            case DEEPSLATE_REDSTONE_ORE -> "深层红石矿";
            case OAK_LOG, BIRCH_LOG, SPRUCE_LOG, JUNGLE_LOG, DARK_OAK_LOG, ACACIA_LOG -> "木材";
            default -> "资源";
        };
    }

    public String summary() {
        return "InteractionVisualizer " + (ivAvailable ? "已连接" : "未安装") +
               ", " + activePlayers.size() + " 个活跃玩家";
    }
}
