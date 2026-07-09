package dev.starcore.starcore.module.visualizer;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

final class VisualizerText {
    private VisualizerText() {
    }

    static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    static String materialName(Material material) {
        if (material == null) {
            return "Unknown";
        }
        String raw = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder builder = new StringBuilder(raw.length());
        boolean upper = true;
        for (char c : raw.toCharArray()) {
            if (upper && Character.isLetter(c)) {
                builder.append(Character.toUpperCase(c));
                upper = false;
            } else {
                builder.append(c);
            }
            if (c == ' ') {
                upper = true;
            }
        }
        return builder.toString();
    }

    static String itemName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "Empty";
        }
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta != null && meta.hasDisplayName()) {
            return ChatColor.stripColor(meta.getDisplayName());
        }
        return materialName(item.getType());
    }

    static String progressBar(double progress, int length, String filledColor, String emptyColor, String character) {
        int safeLength = Math.clamp(length, 1, 40);
        double clamped = Math.clamp(progress, 0.0D, 1.0D);
        int filled = (int) Math.round(clamped * safeLength);
        StringBuilder builder = new StringBuilder(safeLength * 2);
        builder.append(filledColor);
        for (int i = 0; i < filled; i++) {
            builder.append(character);
        }
        builder.append(emptyColor);
        for (int i = filled; i < safeLength; i++) {
            builder.append(character);
        }
        return color(builder.toString());
    }
}

