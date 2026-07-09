package dev.starcore.starcore.foundation.feedback;

import dev.starcore.starcore.foundation.sound.BukkitSoundResolver;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class BukkitInGameFeedbackService implements InGameFeedbackService {
    private final Plugin plugin;
    private final Function<String, InGameFeedbackProfile> profiles;

    public BukkitInGameFeedbackService(Plugin plugin, Function<String, InGameFeedbackProfile> profiles) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    @Override
    public void emit(String eventKey, Player player, Location location) {
        Location target = location;
        if (target == null && player != null) {
            target = player.getLocation();
        }
        if (target == null || target.getWorld() == null) {
            return;
        }
        InGameFeedbackProfile profile = profiles.apply(eventKey);
        if (profile == null || !profile.enabled()) {
            return;
        }
        playSound(player, target, profile);
        spawnParticles(target, profile);
        sendPrompts(player, profile);
        sendBossBar(player, profile);
    }

    private void playSound(Player player, Location location, InGameFeedbackProfile profile) {
        sound(profile.sound()).ifPresent(sound -> {
            if (player != null && player.isOnline()) {
                player.playSound(location, sound, profile.soundVolume(), profile.soundPitch());
                return;
            }
            location.getWorld().playSound(location, sound, profile.soundVolume(), profile.soundPitch());
        });
    }

    private void spawnParticles(Location location, InGameFeedbackProfile profile) {
        if (profile.particleCount() <= 0) {
            return;
        }
        particle(profile.particle()).ifPresent(particle -> location.getWorld().spawnParticle(
            particle,
            location.clone().add(0.0D, profile.particleYOffset(), 0.0D),
            profile.particleCount(),
            profile.particleSpread(),
            profile.particleSpread(),
            profile.particleSpread(),
            0.0D
        ));
    }

    private void sendPrompts(Player player, InGameFeedbackProfile profile) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!profile.actionBar().isBlank()) {
            player.sendActionBar(Component.text(profile.actionBar()));
        }
        if (!profile.title().isBlank() || !profile.subtitle().isBlank()) {
            player.showTitle(Title.title(
                Component.text(profile.title()),
                Component.text(profile.subtitle()),
                Title.Times.times(
                    ticks(profile.titleFadeInTicks()),
                    ticks(profile.titleStayTicks()),
                    ticks(profile.titleFadeOutTicks())
                )
            ));
        }
    }

    private void sendBossBar(Player player, InGameFeedbackProfile profile) {
        if (player == null || !player.isOnline() || profile.bossBar().isBlank()) {
            return;
        }
        BossBar bossBar = BossBar.bossBar(
            Component.text(profile.bossBar()),
            profile.bossBarProgress(),
            bossBarColor(profile.bossBarColor()),
            bossBarOverlay(profile.bossBarOverlay())
        );
        player.showBossBar(bossBar);
        Runnable hide = () -> {
            if (player.isOnline()) {
                player.hideBossBar(bossBar);
            }
        };
        try {
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTaskLater(plugin, hide, profile.bossBarDurationTicks());
            } else {
                hide.run();
            }
        } catch (RuntimeException ignored) {
            hide.run();
        }
    }

    private Duration ticks(int ticks) {
        return Duration.ofMillis(Math.max(0L, ticks) * 50L);
    }

    private BossBar.Color bossBarColor(String key) {
        if (key == null || key.isBlank()) {
            return BossBar.Color.YELLOW;
        }
        try {
            return BossBar.Color.valueOf(key);
        } catch (IllegalArgumentException ignored) {
            return BossBar.Color.YELLOW;
        }
    }

    private BossBar.Overlay bossBarOverlay(String key) {
        if (key == null || key.isBlank()) {
            return BossBar.Overlay.PROGRESS;
        }
        try {
            return BossBar.Overlay.valueOf(key);
        } catch (IllegalArgumentException ignored) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    private Optional<Sound> sound(String key) {
        return BukkitSoundResolver.resolve(key);
    }

    @SuppressWarnings("deprecation")
    private Optional<Particle> particle(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Particle.valueOf(key));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
