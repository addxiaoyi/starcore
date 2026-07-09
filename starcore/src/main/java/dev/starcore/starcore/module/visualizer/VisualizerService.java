package dev.starcore.starcore.module.visualizer;

import org.bukkit.entity.Player;

/**
 * 交互可视化服务接口 - InteractionVisualizer 集成
 */
public interface VisualizerService {

    /**
     * 检查模块是否启用
     */
    boolean isEnabled();

    /**
     * 刷新玩家的可视化
     */
    void refreshPlayer(Player player);

    /**
     * 清理玩家的可视化
     */
    void cleanup();

    /**
     * 获取模块摘要
     */
    String summary();
}
