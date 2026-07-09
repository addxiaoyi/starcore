package dev.starcore.starcore.technology;

/**
 * Represents the different technological eras in the game progression.
 * <p>
 * Each era represents a distinct phase of technological development, from primitive
 * tools to advanced information systems. Technologies are typically grouped by era
 * to create a natural progression path.
 * </p>
 *
 * @author StarCore Development Team
 * @since 1.0.0
 */
public enum TechnologyEra {
    /**
     * Stone Age - The earliest era of human civilization.
     * <p>
     * Focuses on basic survival tools, primitive agriculture, and early social structures.
     * Technologies include stone tools, fire mastery, and basic hunting techniques.
     * </p>
     */
    STONE_AGE("stone_age", "石器时代", 0),

    /**
     * Iron Age - The era of metal working and early civilization.
     * <p>
     * Introduces metalworking, advanced agriculture, writing systems, and organized warfare.
     * Technologies include bronze/iron working, the wheel, and early military formations.
     * </p>
     */
    IRON_AGE("iron_age", "铁器时代", 1),

    /**
     * Industrial Age - The era of mechanization and mass production.
     * <p>
     * Characterized by steam power, factories, railways, and scientific advancement.
     * Technologies include steam engines, industrial manufacturing, and early chemistry.
     * </p>
     */
    INDUSTRIAL_AGE("industrial_age", "工业时代", 2),

    /**
     * Information Age - The modern era of computing and telecommunications.
     * <p>
     * Focuses on digital technology, automation, information networks, and advanced science.
     * Technologies include computers, satellites, biotechnology, and AI systems.
     * </p>
     */
    INFORMATION_AGE("information_age", "信息时代", 3);

    private final String id;
    private final String displayName;
    private final int order;

    /**
     * Constructs a new TechnologyEra.
     *
     * @param id          the unique identifier for this era
     * @param displayName the localized display name
     * @param order       the chronological order (0 = earliest)
     */
    TechnologyEra(String id, String displayName, int order) {
        this.id = id;
        this.displayName = displayName;
        this.order = order;
    }

    /**
     * Gets the unique identifier for this era.
     *
     * @return the era ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the localized display name for this era.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the chronological order of this era.
     * <p>
     * Lower values represent earlier eras. Useful for comparing technological advancement.
     * </p>
     *
     * @return the order value (0 = earliest)
     */
    public int getOrder() {
        return order;
    }

    /**
     * Checks if this era comes before another era chronologically.
     *
     * @param other the era to compare against
     * @return true if this era is earlier than the other
     */
    public boolean isBefore(TechnologyEra other) {
        return this.order < other.order;
    }

    /**
     * Checks if this era comes after another era chronologically.
     *
     * @param other the era to compare against
     * @return true if this era is later than the other
     */
    public boolean isAfter(TechnologyEra other) {
        return this.order > other.order;
    }

    /**
     * Finds a TechnologyEra by its ID.
     *
     * @param id the era ID to search for
     * @return the matching TechnologyEra, or null if not found
     */
    public static TechnologyEra fromId(String id) {
        if (id == null) {
            return null;
        }
        for (TechnologyEra era : values()) {
            if (era.id.equalsIgnoreCase(id)) {
                return era;
            }
        }
        return null;
    }
}
