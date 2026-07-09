package dev.starcore.starcore.event.random.trigger;

import dev.starcore.starcore.event.random.EventTrigger;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.time.LocalTime;

/**
 * 时间触发器
 * 根据游戏时间或现实时间触发事件
 */
public class TimeTrigger implements EventTrigger {

    private final TimeType timeType;
    private final int startTime;
    private final int endTime;

    public TimeTrigger(TimeType timeType, int startTime, int endTime) {
        this.timeType = timeType;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    @Override
    public boolean check(Player player, Location location) {
        int currentTime;

        switch (timeType) {
            case GAME_TIME:
                // Minecraft游戏时间（0-24000）
                if (location != null && location.getWorld() != null) {
                    long worldTime = location.getWorld().getTime();
                    currentTime = (int) (worldTime % 24000);
                } else {
                    return false;
                }
                break;

            case REAL_TIME_HOUR:
                // 现实时间（小时）
                currentTime = LocalTime.now().getHour();
                break;

            case REAL_TIME_MINUTE:
                // 现实时间（分钟）
                currentTime = LocalTime.now().getHour() * 60 + LocalTime.now().getMinute();
                break;

            default:
                return false;
        }

        // 检查时间范围
        if (startTime <= endTime) {
            return currentTime >= startTime && currentTime <= endTime;
        } else {
            // 跨越午夜的情况
            return currentTime >= startTime || currentTime <= endTime;
        }
    }

    @Override
    public String getType() {
        return "TIME";
    }

    @Override
    public String getDescription() {
        return String.format("时间触发器 [类型=%s, 时间=%d-%d]", timeType, startTime, endTime);
    }

    public enum TimeType {
        GAME_TIME,          // 游戏时间
        REAL_TIME_HOUR,     // 现实时间（小时）
        REAL_TIME_MINUTE    // 现实时间（分钟）
    }
}
