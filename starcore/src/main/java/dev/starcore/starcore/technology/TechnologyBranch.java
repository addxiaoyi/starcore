package dev.starcore.starcore.technology;

/**
 * Represents the different branches of technological development.
 * <p>
 * Each branch represents a specialized path of research that provides different
 * strategic advantages. Nations can focus on specific branches or balance across
 * all three to achieve different playstyles.
 * </p>
 *
 * @author StarCore Development Team
 * @since 1.0.0
 */
public enum TechnologyBranch {
    /**
     * Military Branch - Technologies focused on warfare and defense.
     * <p>
     * Provides advantages in combat, military unit production, fortifications,
     * and strategic warfare capabilities. Essential for expansionist nations.
     * </p>
     * <p>
     * Example technologies: Advanced Weaponry, Military Tactics, Siege Engineering
     * </p>
     */
    MILITARY("military", "军事", "Warfare and defense technologies"),

    /**
     * Economic Branch - Technologies focused on production and trade.
     * <p>
     * Improves resource gathering, manufacturing efficiency, trade capabilities,
     * and economic infrastructure. Critical for wealthy, trade-oriented nations.
     * </p>
     * <p>
     * Example technologies: Industrial Production, Banking Systems, Trade Routes
     * </p>
     */
    ECONOMIC("economic", "经济", "Production and trade technologies"),

    /**
     * Culture Branch - Technologies focused on science, culture, and society.
     * <p>
     * Enhances research speed, cultural influence, population happiness, and
     * diplomatic capabilities. Important for cultural and diplomatic victories.
     * </p>
     * <p>
     * Example technologies: Scientific Method, Cultural Heritage, Education Systems
     * </p>
     */
    CULTURE("culture", "文化", "Science and cultural technologies");

    private final String id;
    private final String displayName;
    private final String description;

    /**
     * Constructs a new TechnologyBranch.
     *
     * @param id          the unique identifier for this branch
     * @param displayName the localized display name
     * @param description a brief description of the branch's focus
     */
    TechnologyBranch(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Gets the unique identifier for this branch.
     *
     * @return the branch ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the localized display name for this branch.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the description of this branch's focus.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Finds a TechnologyBranch by its ID.
     *
     * @param id the branch ID to search for
     * @return the matching TechnologyBranch, or null if not found
     */
    public static TechnologyBranch fromId(String id) {
        if (id == null) {
            return null;
        }
        for (TechnologyBranch branch : values()) {
            if (branch.id.equalsIgnoreCase(id)) {
                return branch;
            }
        }
        return null;
    }
}
