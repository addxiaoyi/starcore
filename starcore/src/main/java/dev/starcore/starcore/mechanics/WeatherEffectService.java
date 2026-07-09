package dev.starcore.starcore.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * 天气效果服务
 * 管理天气对游戏的影响
 */
public class WeatherEffectService implements Listener {

    private final JavaPlugin plugin;
    private final Map<String, WeatherEffect> worldWeather; // 每个世界的当前天气效果

    private boolean enabled = true;

    public WeatherEffectService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.worldWeather = new HashMap<>();
    }

    /**
     * 初始化服务
     */
    public void initialize() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // 初始化所有世界的天气
        for (World world : Bukkit.getWorlds()) {
            updateWeatherEffect(world);
        }

        // 定时应用天气效果
        Bukkit.getScheduler().runTaskTimer(plugin, this::applyWeatherEffects, 200L, 200L);

        plugin.getLogger().info("天气效果系统已启用");
    }

    /**
     * 更新世界的天气效果
     */
    private void updateWeatherEffect(World world) {
        WeatherEffect.WeatherType weatherType = WeatherEffect.WeatherType.fromBukkit(world);
        WeatherEffect effect = new WeatherEffect(weatherType);
        worldWeather.put(world.getName(), effect);
    }

    /**
     * 应用天气效果到所有玩家
     */
    private void applyWeatherEffects() {
        if (!enabled) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            WeatherEffect effect = worldWeather.get(world.getName());

            if (effect != null) {
                effect.applyToPlayer(player);
            }
        }
    }

    /**
     * 天气变化事件
     */
    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        if (!enabled) return;

        World world = event.getWorld();

        // 延迟更新，确保天气状态已改变
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateWeatherEffect(world);

            WeatherEffect effect = worldWeather.get(world.getName());
            if (effect != null) {
                // 通知该世界的所有玩家
                for (Player player : world.getPlayers()) {
                    player.sendMessage("§6[天气系统] §e天气已变化");
                    player.sendMessage(effect.getDescription());
                }
            }
        }, 5L);
    }

    /**
     * 雷暴变化事件
     */
    @EventHandler
    public void onThunderChange(ThunderChangeEvent event) {
        if (!enabled) return;

        World world = event.getWorld();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateWeatherEffect(world);

            WeatherEffect effect = worldWeather.get(world.getName());
            if (effect != null && effect.getWeatherType() == WeatherEffect.WeatherType.THUNDERSTORM) {
                // 雷暴天气特殊警告
                for (Player player : world.getPlayers()) {
                    player.sendMessage("§c§l[警告] §6雷暴来袭！请寻找安全的避难所！");
                }
            }
        }, 5L);
    }

    /**
     * 获取世界当前天气效果
     */
    public WeatherEffect getWeatherEffect(World world) {
        return worldWeather.get(world.getName());
    }

    /**
     * 手动设置世界天气
     */
    public void setWeather(World world, WeatherEffect.WeatherType weatherType) {
        switch (weatherType) {
            case CLEAR:
                world.setStorm(false);
                world.setThundering(false);
                break;
            case RAIN:
                world.setStorm(true);
                world.setThundering(false);
                break;
            case THUNDERSTORM:
                world.setStorm(true);
                world.setThundering(true);
                break;
            case SNOW:
                world.setStorm(true);
                world.setThundering(false);
                // 雪天需要在寒冷生物群系
                break;
        }

        updateWeatherEffect(world);
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        worldWeather.clear();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
