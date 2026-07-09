package dev.starcore.starcore.event.random.effect;

import dev.starcore.starcore.event.random.EventEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 广播效果
 * 向所有玩家或特定范围广播消息
 */
public class BroadcastEffect implements EventEffect {

    private final String message;
    private final String prefix;

    public BroadcastEffect(String message) {
        this(message, "§6[事件]§r ");
    }

    public BroadcastEffect(String message, String prefix) {
        this.message = message;
        this.prefix = prefix;
    }

    @Override
    public boolean apply(Player player, Location location) {
        if (message == null || message.isEmpty()) {
            return false;
        }

        String fullMessage = prefix + message;

        // 使用 Adventure API 发送彩色消息
        Component component = Component.text(fullMessage, NamedTextColor.YELLOW);
        Bukkit.getServer().sendMessage(component);

        return true;
    }

    @Override
    public String getType() {
        return "broadcast";
    }

    @Override
    public String getDescription() {
        return "BroadcastEffect{message='" + message + "'}";
    }

    /**
     * 向指定半径内的玩家广播
     */
    public boolean applyToRadius(Player player, Location location, int radius) {
        if (message == null || message.isEmpty() || location == null) {
            return false;
        }

        String fullMessage = prefix + message;
        Component component = Component.text(fullMessage, NamedTextColor.YELLOW);

        for (Player p : location.getWorld().getPlayers()) {
            if (p.getLocation().distance(location) <= radius) {
                p.sendMessage(component);
            }
        }

        return true;
    }

    @Override
    public String toString() {
        return "BroadcastEffect{message='" + message + "'}";
    }
}