package dev.starcore.starcore.module.visualizer;

import java.util.EnumMap;
import java.util.EnumSet;

public final class VisualizerPreferences {
    private final boolean defaultDisableAll;
    private final EnumMap<VisualizerDisplayMode, Boolean> modes = new EnumMap<>(VisualizerDisplayMode.class);
    private final EnumSet<VisualizerEntry> enabledEntries = EnumSet.noneOf(VisualizerEntry.class);
    private final EnumSet<VisualizerEntry> disabledEntries = EnumSet.noneOf(VisualizerEntry.class);

    public VisualizerPreferences(boolean defaultDisableAll) {
        this.defaultDisableAll = defaultDisableAll;
    }

    public boolean modeEnabled(VisualizerDisplayMode mode) {
        return modes.getOrDefault(mode, !defaultDisableAll);
    }

    public void setMode(VisualizerDisplayMode mode, boolean enabled) {
        modes.put(mode, enabled);
    }

    public boolean entryEnabled(VisualizerEntry entry) {
        if (defaultDisableAll) {
            return enabledEntries.contains(entry);
        }
        return !disabledEntries.contains(entry);
    }

    public boolean entryExplicitlyDisabled(VisualizerEntry entry) {
        return disabledEntries.contains(entry);
    }

    public void setEntry(VisualizerEntry entry, boolean enabled) {
        if (enabled) {
            disabledEntries.remove(entry);
            enabledEntries.add(entry);
        } else {
            disabledEntries.add(entry);
            enabledEntries.remove(entry);
        }
    }

    EnumMap<VisualizerDisplayMode, Boolean> modes() {
        return new EnumMap<>(modes);
    }

    EnumSet<VisualizerEntry> disabledEntries() {
        return disabledEntries.clone();
    }

    EnumSet<VisualizerEntry> enabledEntries() {
        return enabledEntries.clone();
    }
}
