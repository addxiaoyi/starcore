package dev.starcore.starcore.event.random;

import dev.starcore.starcore.foundation.sound.BukkitSoundResolver;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * 事件效果接口
 */
public interface EventEffect {

    String getType();

    String getDescription();

    default int getDuration() {
        return 0;
    }

    /**
     * 新系统方法 - apply effects using AdvancedEvent and context
     */
    default void apply(AdvancedEvent event, NationEventContext context) {
        // Default implementation does nothing
    }

    /**
     * 旧系统方法 - apply effects using Player and Location
     */
    default boolean apply(Player player, Location location) {
        // Default implementation does nothing
        return true;
    }

    // ==================== 效果工厂 ====================

    static EventEffect message(String msg) {
        return new EventEffect() {
            public String getType() { return "MESSAGE"; }
            public String getDescription() { return msg; }
            public void apply(AdvancedEvent event, NationEventContext ctx) {
                if (ctx == null) return;
                Bukkit.broadcast(Component.text(event.getRarity().getColoredName() + " " + msg));
            }
            public boolean apply(Player player, Location loc) {
                if (player != null) {
                    player.sendMessage(Component.text(msg));
                }
                return true;
            }
        };
    }

    static EventEffect playerMessage(String playerMsg, String globalMsg) {
        return new EventEffect() {
            public String getType() { return "PLAYER_MESSAGE"; }
            public String getDescription() { return playerMsg; }
            public void apply(AdvancedEvent event, NationEventContext ctx) {
                if (ctx == null) return;
                for (Player p : ctx.getOnlinePlayers()) {
                    p.sendMessage(Component.text(event.getRarity().getColoredName() + " " + playerMsg));
                }
                if (globalMsg != null) {
                    Bukkit.broadcast(Component.text("§7[全服] " + globalMsg));
                }
            }
            public boolean apply(Player player, Location loc) {
                if (player != null) {
                    player.sendMessage(Component.text(playerMsg));
                }
                return true;
            }
        };
    }

    static EventEffect sound(String soundName) {
        return new EventEffect() {
            public String getType() { return "SOUND"; }
            public String getDescription() { return "Play sound: " + soundName; }
            public void apply(AdvancedEvent event, NationEventContext ctx) {
                if (ctx == null) return;
                Sound sound = BukkitSoundResolver.resolve(soundName).orElse(null);
                if (sound == null) return;
                for (Player p : ctx.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
                }
            }
            public boolean apply(Player player, Location loc) {
                if (player != null) {
                    BukkitSoundResolver.resolve(soundName)
                        .ifPresent(sound -> player.playSound(loc, sound, 1.0f, 1.0f));
                }
                return true;
            }
        };
    }

    static EventEffect treasuryChange(int amount) {
        return new EventEffect() {
            public String getType() { return "ECONOMY"; }
            public String getDescription() { return "Treasury " + (amount >= 0 ? "+" : "") + amount; }
            public void apply(AdvancedEvent event, NationEventContext ctx) {
                // Requires TreasuryService
            }
            public boolean apply(Player player, Location loc) {
                return true;
            }
        };
    }

    static EventEffect moraleChange(double amount) {
        return new EventEffect() {
            public String getType() { return "MORALE"; }
            public String getDescription() { return "Morale " + (amount >= 0 ? "+" : "") + amount; }
            public void apply(AdvancedEvent event, NationEventContext ctx) {
                if (ctx == null) return;
                String sign = amount >= 0 ? "+" : "";
                String color = amount >= 0 ? "§a" : "§c";
                for (Player p : ctx.getOnlinePlayers()) {
                    p.sendMessage(Component.text(color + sign + amount + " 士气"));
                }
            }
            public boolean apply(Player player, Location loc) {
                if (player != null) {
                    player.sendMessage(Component.text("§e士气 " + (amount >= 0 ? "+" : "") + amount));
                }
                return true;
            }
        };
    }

    static EventEffect spawnMob(String mobType, int count, int radius) {
        return new EventEffect() {
            public String getType() { return "SPAWN"; }
            public String getDescription() { return "Spawn " + count + "x " + mobType; }
            public void apply(AdvancedEvent event, NationEventContext ctx) {
                // Implementation
            }
            public boolean apply(Player player, Location loc) {
                return true;
            }
        };
    }

    static EventEffect buff(String buffType, int durationSeconds, double value) {
        return new EventEffect() {
            public String getType() { return "BUFF"; }
            public String getDescription() { return buffType + " buff for " + durationSeconds + "s"; }
            public void apply(AdvancedEvent event, NationEventContext ctx) {
                if (ctx == null) return;
                for (Player p : ctx.getOnlinePlayers()) {
                    p.sendMessage(Component.text("§6[Buff] §e" + buffType + " §7持续 " + durationSeconds + "秒"));
                }
            }
            public boolean apply(Player player, Location loc) {
                if (player != null) {
                    player.sendMessage(Component.text("§6[Buff] §e" + buffType + " §7持续 " + durationSeconds + "秒"));
                }
                return true;
            }
        };
    }

    static EventEffect debuff(String debuffType, int durationSeconds, double value) {
        return new EventEffect() {
            public String getType() { return "DEBUFF"; }
            public String getDescription() { return debuffType + " debuff for " + durationSeconds + "s"; }
            public void apply(AdvancedEvent event, NationEventContext ctx) {
                if (ctx == null) return;
                for (Player p : ctx.getOnlinePlayers()) {
                    p.sendMessage(Component.text("§c[Debuff] §c" + debuffType + " §7持续 " + durationSeconds + "秒"));
                }
            }
            public boolean apply(Player player, Location loc) {
                if (player != null) {
                    player.sendMessage(Component.text("§c[Debuff] §c" + debuffType + " §7持续 " + durationSeconds + "秒"));
                }
                return true;
            }
        };
    }

    static EventEffect announce(EventRarity rarity, String message) {
        return new EventEffect() {
            public String getType() { return "ANNOUNCE"; }
            public String getDescription() { return message; }
            public void apply(AdvancedEvent event, NationEventContext ctx) {
                String prefix = rarity.getPrefix();
                Bukkit.broadcast(Component.text(prefix + "[事件] " + message));
            }
            public boolean apply(Player player, Location loc) {
                if (player != null) {
                    player.sendMessage(Component.text("§6[事件] " + message));
                }
                return true;
            }
        };
    }
}
