package dev.starcore.starcore.foundation.tooltip;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 物品提示提供者接口
 * 实现此接口来为特定类型的物品提供自定义提示
 */
public interface TooltipProvider {

    /**
     * 检查此提供者是否可以处理给定的上下文
     *
     * @param context 提示上下文
     * @return true 如果此提供者可以处理
     */
    boolean canHandle(@NotNull TooltipContext context);

    /**
     * 为物品生成提示组件列表
     *
     * @param context 提示上下文
     * @return 提示组件列表，null表示使用默认提示
     */
    @Nullable
    List<Component> provide(@NotNull TooltipContext context);

    /**
     * 获取快捷栏提示（简短文本，用于ActionBar）
     *
     * @param context 提示上下文
     * @return 快捷栏提示文本，null表示使用默认行为
     */
    @Nullable
    default String getHotbarHint(@NotNull TooltipContext context) {
        return null;
    }

    /**
     * 是否独占处理
     * 如果为true，则其他提供者的提示将被忽略
     *
     * @return true表示独占处理
     */
    default boolean isExclusive() {
        return false;
    }

    /**
     * 获取提供者的优先级
     * 优先级越高的提供者越先被检查
     *
     * @return 优先级（默认50，范围0-100）
     */
    default int getPriority() {
        return 50;
    }

    /**
     * 获取提供者ID
     *
     * @return 提供者唯一标识
     */
    @NotNull
    String getId();

    /**
     * 获取提供者名称
     *
     * @return 显示名称
     */
    @NotNull
    String getName();
}
