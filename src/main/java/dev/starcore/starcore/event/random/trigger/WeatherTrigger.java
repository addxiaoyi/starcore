package dev.starcore.starcore.event.random.trigger;

import dev.starcore.starcore.event.random.EventTrigger;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * 天气触发器
 * 根据世界天气状态触发事件
 */
public class WeatherTrigger implements EventTrigger {

    private final WeatherType weatherType;

    public WeatherTrigger(WeatherType weatherType) {
        this.weatherType = weatherType;
    }

    @Override
    public boolean check(Player player, Location location) {
        World world;

        if (location != null && location.getWorld() != null) {
            world = location.getWorld();
        } else if (player != null && player.getWorld() != null) {
            world = player.getWorld();
        } else {
            return false;
        }

        switch (weatherType) {
            case CLEAR:
                return !world.hasStorm() && !world.isThundering();
            case RAIN:
                return world.hasStorm() && !world.isThundering();
            case THUNDER:
                return world.isThundering();
            case ANY_STORM:
                return world.hasStorm() || world.isThundering();
            default:
                return false;
        }
    }

    @Override
    public String getType() {
        return "WEATHER";
    }

    @Override
    public String getDescription() {
        return String.format("天气触发器 [天气=%s]", weatherType);
    }

    public enum WeatherType {
        CLEAR,      // 晴天
        RAIN,       // 下雨
        THUNDER,    // 雷暴
        ANY_STORM   // 任何风暴
    }
}
