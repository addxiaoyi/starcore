package dev.starcore.starcore.core.platform;

public interface PlatformAdapter {
    String platformName();

    String minecraftVersion();

    String apiVersion();

    CompatibilityStatus compatibilityStatus();

    String supportSummary();
}
