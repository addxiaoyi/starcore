package dev.starcore.starcore.core.platform;

public final class PaperPlatformAdapter implements PlatformAdapter {
    private static final SemanticVersion MIN_PLANNED = new SemanticVersion(1, 21, 9);
    private static final SemanticVersion VALIDATED_TARGET = new SemanticVersion(1, 21, 11);

    private final String minecraftVersion;
    private final String apiVersion;
    private final SemanticVersion parsedMinecraftVersion;

    public PaperPlatformAdapter(String minecraftVersion, String apiVersion) {
        this.minecraftVersion = minecraftVersion;
        this.apiVersion = apiVersion;
        this.parsedMinecraftVersion = SemanticVersion.parse(minecraftVersion);
    }

    @Override
    public String platformName() {
        return "Paper";
    }

    @Override
    public String minecraftVersion() {
        return minecraftVersion;
    }

    @Override
    public String apiVersion() {
        return apiVersion;
    }

    @Override
    public CompatibilityStatus compatibilityStatus() {
        if (parsedMinecraftVersion.compareTo(VALIDATED_TARGET) == 0) {
            return CompatibilityStatus.SUPPORTED;
        }
        if (parsedMinecraftVersion.compareTo(MIN_PLANNED) >= 0) {
            return CompatibilityStatus.PLANNED;
        }
        return CompatibilityStatus.UNSUPPORTED;
    }

    @Override
    public String supportSummary() {
        return switch (compatibilityStatus()) {
            case SUPPORTED -> "validated on " + VALIDATED_TARGET + " (API " + apiVersion + ")";
            case PLANNED -> "adapter skeleton ready for 1.21.9+; runtime validation pending (API " + apiVersion + ")";
            case UNSUPPORTED -> "outside current STARCORE compatibility window (API " + apiVersion + ")";
        };
    }
}
