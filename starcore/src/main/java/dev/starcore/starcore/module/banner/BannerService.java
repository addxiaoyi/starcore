package dev.starcore.starcore.module.banner;

import dev.starcore.starcore.module.banner.model.NationBanner;
import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for nation banner management
 */
public interface BannerService {

    /**
     * Get banner for a nation
     * @param nationId Nation ID
     * @return Optional containing the nation's banner if it exists
     */
    default Optional<NationBanner> getBanner(NationId nationId) {
        return getBannerByUUID(nationId.value());
    }

    /**
     * Get banner for a nation by UUID
     * @param nationId Nation UUID
     * @return Optional containing the nation's banner if it exists
     */
    Optional<NationBanner> getBannerByUUID(UUID nationId);

    /**
     * Create a default banner for a new nation
     * @param nationId Nation ID
     * @return The created banner
     */
    NationBanner createDefaultBanner(UUID nationId);

    /**
     * Update banner pattern
     * @param nationId Nation ID
     * @param pattern New pattern key
     * @return Updated banner
     */
    NationBanner updatePattern(UUID nationId, String pattern);

    /**
     * Update banner base color
     * @param nationId Nation ID
     * @param baseColor New base color key
     * @return Updated banner
     */
    NationBanner updateBaseColor(UUID nationId, String baseColor);

    /**
     * Update banner pattern color
     * @param nationId Nation ID
     * @param patternColor New pattern color key
     * @return Updated banner
     */
    NationBanner updatePatternColor(UUID nationId, String patternColor);

    /**
     * Update full banner design
     * @param nationId Nation ID
     * @param pattern Pattern key
     * @param baseColor Base color key
     * @param patternColor Pattern color key
     * @return Updated banner
     */
    NationBanner updateDesign(UUID nationId, String pattern, String baseColor, String patternColor);

    /**
     * Reset banner to default
     * @param nationId Nation ID
     * @return Reset banner
     */
    NationBanner resetToDefault(UUID nationId);

    /**
     * Get cost for changing to a specific pattern
     * @param pattern Pattern key
     * @return Cost in currency units
     */
    double getPatternCost(String pattern);

    /**
     * Get all banners
     * @return Collection of all nation banners
     */
    Collection<NationBanner> getAllBanners();

    /**
     * Delete banner for a nation
     * @param nationId Nation ID
     * @return true if deleted successfully
     */
    boolean deleteBanner(UUID nationId);

    /**
     * Check if a nation has a custom banner
     * @param nationId Nation ID
     * @return true if has custom banner
     */
    boolean hasCustomBanner(UUID nationId);

    /**
     * Save all banners to storage
     */
    void save();

    /**
     * Get module summary
     * @return Summary string
     */
    String summary();
}
