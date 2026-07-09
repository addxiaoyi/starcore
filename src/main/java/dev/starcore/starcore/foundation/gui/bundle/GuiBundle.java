package dev.starcore.starcore.foundation.gui.bundle;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI Bundle - 可嵌套的GUI集合
 *
 * 支持多个GUI在一个界面中切换
 */
public final class GuiBundle {

    private final String id;
    private final Component title;
    private final Map<String, GuiPanel> panels = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerPanel = new ConcurrentHashMap<>();

    public GuiBundle(String id, Component title) {
        this.id = id;
        this.title = title;
    }

    public GuiBundle addPanel(String panelId, GuiPanel panel) {
        panels.put(panelId, panel);
        return this;
    }

    public void show(Player player, String panelId) {
        GuiPanel panel = panels.get(panelId);
        if (panel != null) {
            playerPanel.put(player.getUniqueId(), panelId);
            panel.open(player);
        }
    }

    public void switchPanel(Player player, String panelId) {
        GuiPanel panel = panels.get(panelId);
        if (panel != null) {
            playerPanel.put(player.getUniqueId(), panelId);
            panel.refresh(player);
        }
    }

    public Optional<GuiPanel> getCurrentPanel(Player player) {
        return Optional.ofNullable(playerPanel.get(player.getUniqueId()))
            .flatMap(id -> Optional.ofNullable(panels.get(id)));
    }

    public String getId() { return id; }
    public Component getTitle() { return title; }
    public Map<String, GuiPanel> getPanels() { return Collections.unmodifiableMap(panels); }

    public interface GuiPanel {
        void open(Player player);
        void refresh(Player player);
    }
}
