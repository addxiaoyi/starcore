package dev.starcore.starcore.module.nation;

import dev.starcore.starcore.core.config.ConfigurationService;
import dev.starcore.starcore.foundation.territory.model.ChunkClaimSelection;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.module.nation.model.ClaimChunkPrice;
import dev.starcore.starcore.module.nation.model.ClaimPriceBreakdown;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Biome;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ClaimPricingService {
    private static final double DEFAULT_RICHNESS = 1.0D;

    private final ConfigurationService configuration;

    public ClaimPricingService(ConfigurationService configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public ClaimPriceBreakdown price(ChunkClaimSelection selection) {
        Objects.requireNonNull(selection, "selection");
        BigDecimal baseChunkPrice = configuration.claimCost();
        List<ChunkCoordinate> coordinates = selection.coordinates();
        List<ClaimChunkPrice> chunks = new ArrayList<>(coordinates.size());
        BigDecimal total = BigDecimal.ZERO;
        World world = Bukkit.getWorld(selection.world());
        for (ChunkCoordinate coordinate : coordinates) {
            ClaimChunkPrice price = priceChunk(baseChunkPrice, world, coordinate);
            chunks.add(price);
            total = total.add(price.price());
        }
        return new ClaimPriceBreakdown(baseChunkPrice, total.setScale(2, RoundingMode.DOWN), coordinates.size(), chunks);
    }

    private ClaimChunkPrice priceChunk(BigDecimal baseChunkPrice, World world, ChunkCoordinate coordinate) {
        int centerX = coordinate.x() * ChunkClaimSelection.CHUNK_SIZE + 8;
        int centerZ = coordinate.z() * ChunkClaimSelection.CHUNK_SIZE + 8;
        long distanceBlocks = distanceBlocks(centerX, centerZ);
        double distanceMultiplier = distanceMultiplier(distanceBlocks);
        String biomeName = biomeName(world, centerX, centerZ);
        double richness = biomeRichness(biomeName);
        double biomeMultiplier = biomeMultiplier(richness);
        BigDecimal price = baseChunkPrice
            .multiply(BigDecimal.valueOf(distanceMultiplier))
            .multiply(BigDecimal.valueOf(biomeMultiplier))
            .setScale(2, RoundingMode.DOWN);
        return new ClaimChunkPrice(
            coordinate.world(),
            coordinate.x(),
            coordinate.z(),
            biomeName,
            round(richness),
            distanceBlocks,
            round(distanceMultiplier),
            round(biomeMultiplier),
            price
        );
    }

    private long distanceBlocks(int centerX, int centerZ) {
        long dx = Math.round(centerX - configuration.claimPricingWorldCenterX());
        long dz = Math.round(centerZ - configuration.claimPricingWorldCenterZ());
        return Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
    }

    private double distanceMultiplier(long distanceBlocks) {
        if (!configuration.claimPricingDistanceEnabled()) {
            return 1.0D;
        }
        int stepBlocks = configuration.claimPricingDistanceStepBlocks();
        double multiplier = 1.0D + (Math.max(0L, distanceBlocks) / (double) stepBlocks) * configuration.claimPricingDistanceStepMultiplier();
        return Math.clamp(multiplier, 1.0D, configuration.claimPricingDistanceMaxMultiplier());
    }

    private String biomeName(World world, int centerX, int centerZ) {
        if (world == null) {
            return "unknown";
        }
        Biome biome = world.getBiome(centerX, world.getSeaLevel(), centerZ);
        return biome.getKey().getKey().toLowerCase(Locale.ROOT);
    }

    private double biomeRichness(String biomeName) {
        double fallback = estimatedBiomeRichness(biomeName, configuration.claimPricingUnknownBiomeRichness());
        return configuration.claimPricingBiomeRichness(biomeName, fallback);
    }

    private double biomeMultiplier(double richness) {
        if (!configuration.claimPricingBiomeEnabled()) {
            return 1.0D;
        }
        return Math.clamp(richness, configuration.claimPricingBiomeMinMultiplier(), configuration.claimPricingBiomeMaxMultiplier());
    }

    public static double estimatedBiomeRichness(String biomeName, double unknownFallback) {
        String name = biomeName == null ? "" : biomeName.toLowerCase(Locale.ROOT);
        if (name.isBlank() || name.equals("unknown")) {
            return unknownFallback;
        }
        if (name.contains("snow") || name.contains("ice") || name.contains("frozen")) {
            return 0.55D;
        }
        if (name.contains("desert")) {
            return 0.65D;
        }
        if (name.contains("ocean") || name.contains("river") || name.contains("beach")) {
            return 0.70D;
        }
        if (name.contains("badlands")) {
            return 0.85D;
        }
        if (name.contains("savanna")) {
            return 0.90D;
        }
        if (name.contains("plains") || name.contains("meadow")) {
            return 0.95D;
        }
        if (name.contains("taiga") || name.contains("grove")) {
            return 1.05D;
        }
        if (name.contains("forest") || name.contains("cherry")) {
            return 1.15D;
        }
        if (name.contains("swamp") || name.contains("mangrove")) {
            return 1.20D;
        }
        if (name.contains("jungle")) {
            return 1.25D;
        }
        if (name.contains("stony") || name.contains("peak") || name.contains("mountain") || name.contains("slope")) {
            return 1.35D;
        }
        if (name.contains("mushroom")) {
            return 1.40D;
        }
        if (name.contains("nether") || name.contains("basalt") || name.contains("crimson") || name.contains("warped")) {
            return 1.30D;
        }
        if (name.contains("end")) {
            return 1.10D;
        }
        return DEFAULT_RICHNESS;
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}
