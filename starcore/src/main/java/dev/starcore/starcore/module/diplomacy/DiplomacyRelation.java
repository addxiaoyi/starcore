package dev.starcore.starcore.module.diplomacy;

public enum DiplomacyRelation {
    NEUTRAL("Neutral"),
    FRIENDLY("Friendly"),
    ALLIED("Allied"),
    HOSTILE("Hostile"),
    WAR("War"),
    CEASE_FIRE("Cease Fire"),
    VASSAL("Vassal");

    private final String displayName;

    DiplomacyRelation(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
