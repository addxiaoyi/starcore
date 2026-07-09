package dev.starcore.starcore.module.weather.listener;

import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.module.weather.WeatherControlService;
import dev.starcore.starcore.module.weather.model.WeatherType;
import dev.starcore.starcore.module.weather.model.WorldWeatherState;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.weather.ThunderChangeEvent;

/**
 * 天气系统事件监听器
 */
public class WeatherListener implements Listener {

    private final WeatherControlService weatherService;
    private final StarCoreEventBus eventBus;

    public WeatherListener(WeatherControlService weatherService, StarCoreEventBus eventBus) {
        this.weatherService = weatherService;
        this.eventBus = eventBus;
    }

    /**
     * 处理玩家切换世界
     */
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World toWorld = player.getWorld();

        // 获取目标世界的天气状态
        WorldWeatherState toState = weatherService.getWorldWeatherState(toWorld.getName());

        if (toState != null && toState.isControlled()) {
            // 向玩家发送当前世界天气信息
            WeatherType weather = toState.getCurrentWeather();
            player.sendMessage(String.format(
                "§6[天气] §e这个世界正在被控制的天气: %s %s",
                weather.getIcon(),
                weather.getDisplayName()
            ));
        }
    }

    /**
     * 处理玩家加入
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        // 获取世界的天气状态
        WorldWeatherState state = weatherService.getWorldWeatherState(world.getName());

        if (state != null) {
            WeatherType weather = state.getCurrentWeather();

            // 发送欢迎天气信息
            if (state.isControlled()) {
                player.sendMessage(String.format(
                    "§6[天气] §7当前世界天气: %s %s §7(受控制)",
                    weather.getIcon(),
                    weather.getDisplayName()
                ));
            }
        }
    }

    /**
     * 处理自然雷暴开始/结束事件
     */
    @EventHandler
    public void onThunderChange(ThunderChangeEvent event) {
        World world = event.getWorld();
        WorldWeatherState state = weatherService.getWorldWeatherState(world.getName());

        if (state != null && state.isControlled()) {
            // 阻止自然的雷暴变化（如果世界被控制）
            if (event.toThunderState()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * 处理自然天气变化事件
     */
    @EventHandler
    public void onNaturalWeatherChange(org.bukkit.event.weather.WeatherChangeEvent event) {
        World world = event.getWorld();
        WorldWeatherState state = weatherService.getWorldWeatherState(world.getName());

        if (state != null && state.isControlled()) {
            // 阻止自然的天气变化（如果世界被控制）
            if (event.toWeatherState()) {
                event.setCancelled(true);
            }
        }
    }
}
