package dev.starcore.starcore.core.platform;

import org.bukkit.plugin.java.JavaPlugin;

public final class PlatformAdapterFactory {
    private PlatformAdapterFactory() {
    }

    public static PlatformAdapter detect(JavaPlugin plugin) {
        return new PaperPlatformAdapter(
            plugin.getServer().getMinecraftVersion(),
            plugin.getServer().getBukkitVersion()
        );
    }
}
