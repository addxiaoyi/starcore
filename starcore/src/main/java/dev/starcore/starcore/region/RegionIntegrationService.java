package dev.starcore.starcore.region;

import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.foundation.territory.model.TerritoryClaim;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.territory.SubRegion;
import dev.starcore.starcore.territory.SubRegionService;
import dev.starcore.starcore.territory.Territory;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

/**
 * 区域集成服务
 * 整合领土、子区域、王国（国家区块领地）等不同类型的区域检测
 *
 * 架构说明:
 * - 使用 Foundation 的 Chunk 级别 TerritoryService 作为区域检测的核心来源
 * - nationService.claimAt(world, chunkX, chunkZ) 用于快速判断玩家所在国家/领土
 * - SubRegionService 用于更精细的子区域检测
 *
 * 重要: 所有区域检测都基于 Chunk 级别，与 TerritoryProtectionListener 保持一致
 */
public class RegionIntegrationService {
    private final Plugin plugin;

    // Foundation Chunk 级别服务 - 用于王国/国家检测（与 TerritoryProtectionListener 一致）
    private final TerritoryService foundationTerritoryService;

    // 坐标级别服务 - 用于子区域和精细领土检测
    private final dev.starcore.starcore.territory.TerritoryService coordinateTerritoryService;
    private final SubRegionService subRegionService;

    // 王国（国家）服务通过服务注册表延迟获取，因为 NationModule 在区域模块之后才启用
    private final ServiceRegistry serviceRegistry;
    private NationService cachedNationService;

    public RegionIntegrationService(Plugin plugin,
                                   TerritoryService territoryService,
                                   dev.starcore.starcore.territory.TerritoryService coordinateTerritoryService,
                                   SubRegionService subRegionService,
                                   ServiceRegistry serviceRegistry) {
        this.plugin = plugin;
        this.foundationTerritoryService = territoryService;
        this.coordinateTerritoryService = coordinateTerritoryService;
        this.subRegionService = subRegionService;
        this.serviceRegistry = serviceRegistry;
    }

    /**
     * 延迟获取 NationService（王国服务）
     * NationModule 在基础服务之后才注册，因此首次使用时才解析并缓存
     */
    private NationService nationService() {
        if (cachedNationService == null && serviceRegistry != null) {
            cachedNationService = serviceRegistry.find(NationService.class).orElse(null);
        }
        return cachedNationService;
    }

    /**
     * 检测玩家当前位置的区域信息
     * 返回优先级最高的区域标识
     *
     * 优先级：子区域 > 领土 > 王国 > 生物群系
     */
    public Optional<RegionInfo> detectRegion(Player player, Location location) {
        // 1. 检查子区域（精细控制）
        Optional<RegionInfo> subRegionInfo = checkSubRegion(location);
        if (subRegionInfo.isPresent()) {
            return subRegionInfo;
        }

        // 2. 检查领土（使用坐标级别服务）
        Optional<RegionInfo> territoryInfo = checkTerritory(location);
        if (territoryInfo.isPresent()) {
            return territoryInfo;
        }

        // 3. 检查王国（国家区块领地）- 使用 Foundation Chunk 级别服务
        Optional<RegionInfo> kingdomInfo = checkKingdom(location);
        if (kingdomInfo.isPresent()) {
            return kingdomInfo;
        }

        // 4. 默认返回生物群系
        return Optional.of(checkBiome(location));
    }

    /**
     * 检查子区域
     */
    private Optional<RegionInfo> checkSubRegion(Location location) {
        // 首先需要找到父领土
        Territory parentTerritory = coordinateTerritoryService.getTerritoryAt(location);
        if (parentTerritory == null) {
            return Optional.empty();
        }

        // 获取该领土下的子区域
        SubRegion subRegion = subRegionService.getSubRegionAt(location, parentTerritory.getId());

        if (subRegion == null) {
            return Optional.empty();
        }

        String regionKey = "subregion:" + subRegion.getId().toString();
        String displayName = subRegion.getName();

        return Optional.of(new RegionInfo(
            regionKey,
            displayName,
            RegionEnterEvent.RegionType.SUB_REGION,
            "特殊区域"
        ));
    }

    /**
     * 检查领土
     */
    private Optional<RegionInfo> checkTerritory(Location location) {
        Territory territory = coordinateTerritoryService.getTerritoryAt(location);

        if (territory == null) {
            return Optional.empty();
        }

        String regionKey = "territory:" + territory.getId().toString();
        String displayName = territory.getName();

        // 获取国家名称作为副标题
        String subtitle;
        if (territory.getNationId() != null) {
            // 使用 resolveNation 方法获取国家名称
            Optional<Nation> nationOpt = resolveNation(nationService(), territory.getNationId().toString());
            String nationName = nationOpt.map(Nation::name).orElse("未知国家");
            subtitle = "所属国家: " + nationName;
        } else {
            subtitle = "独立领土";
        }

        return Optional.of(new RegionInfo(
            regionKey,
            displayName,
            RegionEnterEvent.RegionType.TERRITORY,
            subtitle
        ));
    }

    /**
     * 检查王国区域（国家拥有的区块领地）
     * 通过 Foundation TerritoryService 查询当前 Chunk 的归属，解析出所属国家作为王国标题
     *
     * 注意: 这里使用 Foundation 的 Chunk 级别服务，与 TerritoryProtectionListener 保持一致
     */
    private Optional<RegionInfo> checkKingdom(Location location) {
        if (location.getWorld() == null) {
            return Optional.empty();
        }

        // 由方块坐标推导区块坐标（右移 4 位，避免触发区块加载）
        ChunkCoordinate coordinate = new ChunkCoordinate(
            location.getWorld().getName(),
            location.getBlockX() >> 4,
            location.getBlockZ() >> 4
        );

        // 使用 Foundation 的 Chunk 级别 TerritoryService
        Optional<TerritoryClaim> claimOpt = foundationTerritoryService.claimAt(coordinate);
        if (claimOpt.isEmpty()) {
            return Optional.empty();
        }

        // 获取 NationService 来解析国家信息
        NationService nationService = nationService();
        if (nationService == null) {
            return Optional.empty();
        }

        // claim.ownerId() 存储的是 NationId 的 UUID 字符串
        String ownerId = claimOpt.get().ownerId();
        Optional<Nation> nationOpt = resolveNation(nationService, ownerId);
        if (nationOpt.isEmpty()) {
            return Optional.empty();
        }

        Nation nation = nationOpt.get();
        String regionKey = "kingdom:" + nation.id().toString();
        String displayName = nation.name();
        String subtitle = "王国领域 · " + nation.kind().name();

        return Optional.of(new RegionInfo(
            regionKey,
            displayName,
            RegionEnterEvent.RegionType.KINGDOM,
            subtitle
        ));
    }

    /**
     * 将区块归属的 ownerId 解析为国家对象
     */
    private Optional<Nation> resolveNation(NationService nationService, String ownerId) {
        try {
            return nationService.nationById(new NationId(UUID.fromString(ownerId)));
        } catch (IllegalArgumentException ex) {
            // 兼容历史数据：ownerId 可能不是标准 UUID，退而按名称解析
            return nationService.nationByName(ownerId);
        }
    }

    /**
     * 检查生物群系
     */
    private RegionInfo checkBiome(Location location) {
        String biome = location.getBlock().getBiome().getKey().getKey();
        String regionKey = "biome:" + biome;
        String displayName = formatBiomeName(biome);

        return new RegionInfo(
            regionKey,
            displayName,
            RegionEnterEvent.RegionType.BIOME,
            "生物群系"
        );
    }

    /**
     * 格式化生物群系名称
     */
    private String formatBiomeName(String biome) {
        // 生物群系中文翻译映射
        String translated = getBiomeTranslation(biome);
        if (translated != null) {
            return translated;
        }

        // 默认格式化：将下划线替换为空格，首字母大写
        String[] parts = biome.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() > 0) {
                result.append(" ");
            }
            result.append(part.substring(0, 1).toUpperCase())
                  .append(part.substring(1).toLowerCase());
        }
        return result.toString();
    }

    /**
     * 获取生物群系中文翻译
     */
    private String getBiomeTranslation(String biome) {
        // 常见生物群系的中文翻译
        return switch (biome.toLowerCase()) {
            case "plains" -> "平原";
            case "forest" -> "森林";
            case "desert" -> "沙漠";
            case "mountains" -> "山地";
            case "ocean" -> "海洋";
            case "river" -> "河流";
            case "swamp" -> "沼泽";
            case "jungle" -> "丛林";
            case "taiga" -> "针叶林";
            case "savanna" -> "热带草原";
            case "beach" -> "海滩";
            case "snowy_plains" -> "雪原";
            case "snowy_taiga" -> "积雪针叶林";
            case "ice_spikes" -> "冰刺平原";
            case "mushroom_fields" -> "蘑菇岛";
            case "badlands" -> "恶地";
            case "deep_ocean" -> "深海";
            case "deep_dark" -> "深暗之域";
            case "cherry_grove" -> "樱花树林";
            case "mangrove_swamp" -> "红树林沼泽";
            case "bamboo_jungle" -> "竹林";
            case "dark_forest" -> "黑森林";
            case "birch_forest" -> "桦木森林";
            case "flower_forest" -> "繁花森林";
            case "sunflower_plains" -> "向日葵平原";
            case "windswept_hills" -> "风袭丘陵";
            case "windswept_forest" -> "风袭森林";
            case "windswept_gravelly_hills" -> "风袭砂砾丘陵";
            case "windswept_savanna" -> "风袭热带草原";
            case "meadow" -> "草甸";
            case "grove" -> "雪林";
            case "snowy_slopes" -> "积雪山坡";
            case "frozen_peaks" -> "冰封山峰";
            case "jagged_peaks" -> "尖峭山峰";
            case "stony_peaks" -> "裸岩山峰";
            case "dripstone_caves" -> "溶洞";
            case "lush_caves" -> "繁茂洞穴";
            case "nether_wastes" -> "下界荒地";
            case "crimson_forest" -> "绯红森林";
            case "warped_forest" -> "诡异森林";
            case "soul_sand_valley" -> "灵魂沙峡谷";
            case "basalt_deltas" -> "玄武岩三角洲";
            case "the_end" -> "末地";
            case "end_highlands" -> "末地高地";
            case "end_midlands" -> "末地中岛";
            case "small_end_islands" -> "末地小岛";
            case "end_barrens" -> "末地荒地";
            default -> null;
        };
    }

    /**
     * 区域信息类
     */
    public static class RegionInfo {
        private final String regionKey;
        private final String displayName;
        private final RegionEnterEvent.RegionType type;
        private final String subtitle;

        public RegionInfo(String regionKey, String displayName,
                         RegionEnterEvent.RegionType type, String subtitle) {
            this.regionKey = regionKey;
            this.displayName = displayName;
            this.type = type;
            this.subtitle = subtitle;
        }

        public String getRegionKey() {
            return regionKey;
        }

        public String getDisplayName() {
            return displayName;
        }

        public RegionEnterEvent.RegionType getType() {
            return type;
        }

        public String getSubtitle() {
            return subtitle;
        }
    }
}
