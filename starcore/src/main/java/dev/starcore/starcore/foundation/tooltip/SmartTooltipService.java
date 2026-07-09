package dev.starcore.starcore.foundation.tooltip;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 智能物品提示服务
 * 提供上下文感知的物品说明生成和快捷栏提示功能
 */
public final class SmartTooltipService {

    private static volatile SmartTooltipService instance;

    private final List<TooltipProvider> providers;
    private final TooltipConfig config;
    private volatile boolean enabled = true;

    private SmartTooltipService() {
        this.providers = new CopyOnWriteArrayList<>();
        this.config = new TooltipConfig();
    }

    /**
     * 获取单例实例
     */
    @NotNull
    public static SmartTooltipService getInstance() {
        if (instance == null) {
            synchronized (SmartTooltipService.class) {
                if (instance == null) {
                    instance = new SmartTooltipService();
                }
            }
        }
        return instance;
    }

    /**
     * 注册提示提供者
     */
    public void registerProvider(@NotNull TooltipProvider provider) {
        if (!providers.contains(provider)) {
            providers.add(provider);
        }
    }

    /**
     * 注销提示提供者
     */
    public void unregisterProvider(@NotNull TooltipProvider provider) {
        providers.remove(provider);
    }

    /**
     * 为玩家生成物品的智能提示
     *
     * @param player 玩家
     * @param item 物品
     * @return 提示组件列表，如果返回null则使用默认提示
     */
    @Nullable
    public List<Component> generateTooltip(@NotNull Player player, @NotNull ItemStack item) {
        if (!enabled || providers.isEmpty()) {
            return null;
        }

        TooltipContext context = new TooltipContext(player, item);
        List<Component> result = new java.util.ArrayList<>();
        boolean handled = false;

        for (TooltipProvider provider : providers) {
            if (provider.canHandle(context)) {
                List<Component> lines = provider.provide(context);
                if (lines != null && !lines.isEmpty()) {
                    result.addAll(lines);
                    handled = true;
                    if (provider.isExclusive()) {
                        break;
                    }
                }
            }
        }

        return handled ? result : null;
    }

    /**
     * 为玩家生成快捷栏物品的提示信息
     * 返回格式化的提示文本，用于ActionBar或BossBar显示
     *
     * @param player 玩家
     * @param item 快捷栏物品
     * @return 简短的提示文本
     */
    @Nullable
    public String generateHotbarHint(@NotNull Player player, @NotNull ItemStack item) {
        if (!enabled || providers.isEmpty()) {
            return null;
        }

        TooltipContext context = new TooltipContext(player, item);

        for (TooltipProvider provider : providers) {
            if (provider.canHandle(context)) {
                String hint = provider.getHotbarHint(context);
                if (hint != null && !hint.isEmpty()) {
                    return hint;
                }
            }
        }

        return null;
    }

    /**
     * 检查物品是否有自定义提示
     */
    public boolean hasCustomTooltip(@NotNull Player player, @NotNull ItemStack item) {
        if (!enabled || providers.isEmpty()) {
            return false;
        }

        TooltipContext context = new TooltipContext(player, item);

        for (TooltipProvider provider : providers) {
            if (provider.canHandle(context)) {
                List<Component> lines = provider.provide(context);
                if (lines != null && !lines.isEmpty()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 启用/禁用服务
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 检查服务是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 获取配置
     */
    @NotNull
    public TooltipConfig getConfig() {
        return config;
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        providers.clear();
        enabled = false;
    }
}
