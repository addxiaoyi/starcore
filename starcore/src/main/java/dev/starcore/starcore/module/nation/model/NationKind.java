package dev.starcore.starcore.module.nation.model;

public enum NationKind {
    NATION("国家"),
    CITY_STATE("城邦");

    private final String displayName;

    NationKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
