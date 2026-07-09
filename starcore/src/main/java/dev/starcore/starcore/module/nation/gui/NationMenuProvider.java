package dev.starcore.starcore.module.nation.gui;

import dev.starcore.starcore.module.nation.model.Nation;
import org.bukkit.entity.Player;

/**
 * 国家管理菜单提供者接口
 * 支持多种实现：NightCore Dialog、ProtocolLib Anvil、原生箱子等
 */
public interface NationMenuProvider {

    /**
     * 打开国家管理主菜单
     * @param player 玩家
     */
    void openMainMenu(Player player);

    /**
     * 打开子菜单
     * @param player 玩家
     * @param nation 国家（从主菜单传入，调用 openMainMenu 会丢失）
     * @param submenuId 子菜单标识符
     */
    void openSubMenu(Player player, Nation nation, String submenuId);

    /**
     * 获取提供者类型名称
     * @return 类型名称（用于日志和调试）
     */
    String getProviderType();

    /**
     * 检查此提供者是否可用（依赖是否存在）
     * @return 是否可用
     */
    boolean isAvailable();
}
