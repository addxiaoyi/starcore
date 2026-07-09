package dev.starcore.starcore.module.visualizer;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class VisualizerDisplayManager {
    private final Plugin plugin;
    private final NamespacedKey displayKey;
    private final Map<String, ActiveDisplay> displays = new HashMap<>();
    private long tick;

    VisualizerDisplayManager(Plugin plugin) {
        this.plugin = plugin;
        this.displayKey = new NamespacedKey(plugin, "interaction_visualizer_display");
    }

    int activeDisplayCount() {
        return displays.size();
    }

    void nextTick() {
        tick++;
    }

    boolean canCreate(VisualizerConfig config) {
        return displays.size() < config.maxDisplaysGlobal();
    }

    void render(VisualizerBlockKey key, Location baseLocation, VisualizerRenderSnapshot snapshot, VisualizerConfig config, boolean renderHologram, boolean renderItems) {
        if (key == null || baseLocation == null || snapshot == null || baseLocation.getWorld() == null) {
            return;
        }
        ActiveDisplay active = displays.get(key.stableKey());
        if (active == null) {
            if (!canCreate(config)) {
                return;
            }
            active = new ActiveDisplay(key.stableKey());
            displays.put(key.stableKey(), active);
        }
        active.lastSeenTick = tick;
        if (snapshot.contentHash().equals(active.contentHash) && sameBlock(active.baseLocation, baseLocation)) {
            return;
        }
        active.baseLocation = baseLocation.clone();
        active.contentHash = snapshot.contentHash();
        if (renderHologram) {
            renderText(active, baseLocation, snapshot, config);
        } else if (active.text != null) {
            active.text.remove();
            active.text = null;
        }
        if (renderItems) {
            renderItems(active, baseLocation, snapshot, config);
        } else {
            for (ItemDisplay item : active.items) {
                if (item != null) {
                    item.remove();
                }
            }
            active.items.clear();
        }
    }

    void remove(VisualizerBlockKey key) {
        if (key == null) {
            return;
        }
        ActiveDisplay active = displays.remove(key.stableKey());
        if (active != null) {
            active.remove();
        }
    }

    void cleanupStale(long maxAgeTicks) {
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, ActiveDisplay> entry : displays.entrySet()) {
            ActiveDisplay display = entry.getValue();
            if (tick - display.lastSeenTick > maxAgeTicks || !display.valid()) {
                stale.add(entry.getKey());
            }
        }
        for (String key : stale) {
            ActiveDisplay display = displays.remove(key);
            if (display != null) {
                display.remove();
            }
        }
    }

    void removeAll() {
        displays.values().forEach(ActiveDisplay::remove);
        displays.clear();
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(displayKey, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
    }

    private void renderText(ActiveDisplay active, Location baseLocation, VisualizerRenderSnapshot snapshot, VisualizerConfig config) {
        Location textLocation = baseLocation.clone().add(0.5D, 1.35D, 0.5D);
        if (active.text == null || !active.text.isValid()) {
            active.text = textLocation.getWorld().spawn(textLocation, TextDisplay.class, display -> {
                mark(display, active.key);
                display.setPersistent(false);
                display.setBillboard(Display.Billboard.CENTER);
                display.setViewRange(config.renderDistance());
                display.setShadowed(true);
                display.setSeeThrough(false);
                display.setDefaultBackground(false);
                display.setLineWidth(180);
            });
        }
        active.text.teleport(textLocation);
        active.text.setViewRange(config.renderDistance());
        active.text.setText(String.join("\n", snapshot.lines()));
    }

    private void renderItems(ActiveDisplay active, Location baseLocation, VisualizerRenderSnapshot snapshot, VisualizerConfig config) {
        List<ItemStack> items = snapshot.items().stream().limit(4).toList();
        while (active.items.size() > items.size()) {
            ItemDisplay removed = active.items.remove(active.items.size() - 1);
            if (removed != null) {
                removed.remove();
            }
        }
        for (int i = 0; i < items.size(); i++) {
            Location itemLocation = itemLocation(baseLocation, i, items.size());
            ItemDisplay display;
            if (i >= active.items.size() || active.items.get(i) == null || !active.items.get(i).isValid()) {
                display = itemLocation.getWorld().spawn(itemLocation, ItemDisplay.class, spawned -> {
                    mark(spawned, active.key);
                    spawned.setPersistent(false);
                    spawned.setBillboard(Display.Billboard.CENTER);
                    spawned.setViewRange(config.renderDistance());
                    spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
                });
                if (i >= active.items.size()) {
                    active.items.add(display);
                } else {
                    active.items.set(i, display);
                }
            } else {
                display = active.items.get(i);
            }
            display.teleport(itemLocation);
            display.setViewRange(config.renderDistance());
            display.setItemStack(items.get(i));
        }
    }

    private Location itemLocation(Location baseLocation, int index, int count) {
        double offset = switch (count) {
            case 1 -> 0.0D;
            case 2 -> index == 0 ? -0.25D : 0.25D;
            default -> -0.35D + (index * 0.25D);
        };
        return baseLocation.clone().add(0.5D + offset, 1.02D, 0.5D);
    }

    private void mark(Entity entity, String key) {
        entity.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, key);
    }

    private boolean sameBlock(Location left, Location right) {
        return left != null
            && right != null
            && left.getWorld() != null
            && right.getWorld() != null
            && left.getWorld().equals(right.getWorld())
            && left.getBlockX() == right.getBlockX()
            && left.getBlockY() == right.getBlockY()
            && left.getBlockZ() == right.getBlockZ();
    }

    private final class ActiveDisplay {
        private final String key;
        private TextDisplay text;
        private final List<ItemDisplay> items = new ArrayList<>();
        private String contentHash = "";
        private long lastSeenTick;
        private Location baseLocation;

        private ActiveDisplay(String key) {
            this.key = key;
        }

        private boolean valid() {
            return (text != null && text.isValid()) || items.stream().anyMatch(item -> item != null && item.isValid());
        }

        private void remove() {
            if (text != null) {
                text.remove();
            }
            for (ItemDisplay item : items) {
                if (item != null) {
                    item.remove();
                }
            }
            items.clear();
        }
    }
}
