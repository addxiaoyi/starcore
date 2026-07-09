package dev.starcore.starcore.region;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 玩家进入区域事件
 * 当玩家进入王国区域或生物群系时触发
 */
public class RegionEnterEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();

    private final RegionType regionType;
    private final String regionName;
    private final String displayName;

    public RegionEnterEvent(@NotNull Player player, RegionType regionType, String regionName, String displayName) {
        super(player);
        this.regionType = regionType;
        this.regionName = regionName;
        this.displayName = displayName;
    }

    /**
     * 获取区域类型
     */
    public RegionType getRegionType() {
        return regionType;
    }

    /**
     * 获取区域标识名称
     */
    public String getRegionName() {
        return regionName;
    }

    /**
     * 获取区域显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    /**
     * 区域类型枚举
     */
    public enum RegionType {
        /** 王国区域 */
        KINGDOM,
        /** 生物群系 */
        BIOME,
        /** 领土区域 */
        TERRITORY,
        /** 子区域 */
        SUB_REGION,
        /** 自定义区域 */
        CUSTOM
    }
}
