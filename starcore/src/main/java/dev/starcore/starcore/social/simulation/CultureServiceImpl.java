package dev.starcore.starcore.social.simulation;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.UUID;

/**
 * 文化服务实现
 */
public class CultureServiceImpl implements CultureService {

    private final JavaPlugin plugin;
    private final NationService nationService;
    private final DatabaseService databaseService;

    // 文化数据缓存
    private final Map<NationId, NationCulture> cultures = new ConcurrentHashMap<>();

    // YAML 持久化文件 (数据库不可用时的降级方案)
    private File dataFile;
    private FileConfiguration dataConfig;

    // 定义的文化特质 - 使用不可变 Map (Map.ofEntries 允许超过10个键值对)
    private static final Map<String, CultureTrait> TRAITS = Map.ofEntries(
        // 艺术类特质
        Map.entry("painting", new CultureTrait("painting", "绘画", "艺术修养深厚", CultureCategory.ART, 1,
            Set.of("charisma+5", "tourism+10%"))),
        Map.entry("music", new CultureTrait("music", "音乐", "音乐传统悠久", CultureCategory.ART, 1,
            Set.of("morale+5", "influence+5"))),
        Map.entry("architecture", new CultureTrait("architecture", "建筑", "建筑艺术精湛", CultureCategory.ART, 2,
            Set.of("defense+10%", "culture+15%"))),
        // 科学类特质
        Map.entry("writing", new CultureTrait("writing", "书写", "文字系统发达", CultureCategory.SCIENCE, 1,
            Set.of("tech_speed+10%", "edu+5%"))),
        Map.entry("mathematics", new CultureTrait("mathematics", "数学", "数学成就辉煌", CultureCategory.SCIENCE, 2,
            Set.of("tech_speed+20%", "build_cost-10%"))),
        Map.entry("engineering", new CultureTrait("engineering", "工程学", "工程技术先进", CultureCategory.SCIENCE, 3,
            Set.of("build_speed+25%", "resource_yield+15%"))),
        // 军事类特质
        Map.entry("martial", new CultureTrait("martial", "尚武", "武德充沛", CultureCategory.MILITARY, 1,
            Set.of("damage+10%", "morale+10%"))),
        Map.entry("fortification", new CultureTrait("fortification", "筑城", "筑城技术精湛", CultureCategory.MILITARY, 2,
            Set.of("defense+25%", "siege_resist+15%"))),
        Map.entry("strategy", new CultureTrait("strategy", "兵法", "兵法造诣深厚", CultureCategory.MILITARY, 3,
            Set.of("army_damage+15%", "army_defense+15%"))),
        // 商业类特质
        Map.entry("trade", new CultureTrait("trade", "商贸", "商业传统繁荣", CultureCategory.TRADE, 1,
            Set.of("trade_income+20%", "relation+5"))),
        Map.entry("banking", new CultureTrait("banking", "银行", "金融体系完善", CultureCategory.TRADE, 2,
            Set.of("trade_income+40%", "loan_interest-10%"))),
        Map.entry("maritime", new CultureTrait("maritime", "航海", "航海技术先进", CultureCategory.TRADE, 3,
            Set.of("sea_trade+50%", "explore_range+30%"))),
        // 宗教类特质
        Map.entry("religious", new CultureTrait("religious", "虔诚", "宗教信仰坚定", CultureCategory.RELIGION, 1,
            Set.of("morale+15%", "conversion+10%"))),
        Map.entry("enlightened", new CultureTrait("enlightened", "开明", "思想启蒙先进", CultureCategory.RELIGION, 2,
            Set.of("innovation+20%", "culture+25%"))),
        // 政治类特质
        Map.entry("democratic", new CultureTrait("democratic", "民主", "民主制度成熟", CultureCategory.POLITICS, 1,
            Set.of("citizen_happiness+20%", "stability+15%"))),
        Map.entry("diplomatic", new CultureTrait("diplomatic", "外交", "外交手段高明", CultureCategory.POLITICS, 2,
            Set.of("relation_gain+30%", "war_exhaustion-20%")))
    );

    public CultureServiceImpl(JavaPlugin plugin, NationService nationService, DatabaseService databaseService) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.databaseService = databaseService;
        initializeDefaultCultures();
    }

    /**
     * 简化构造函数（无 NationService）
     */
    public CultureServiceImpl(JavaPlugin plugin) {
        this.plugin = plugin;
        this.nationService = null;
        this.databaseService = null;
        initializeDefaultCultures();
    }

    private void initializeDefaultCultures() {
        // 如果有 NationService，为所有现有国家初始化文化
        if (nationService != null) {
            nationService.nations().forEach(nation -> {
                if (!cultures.containsKey(nation.id())) {
                    cultures.put(nation.id(), new NationCulture(nation.id(), 0,
                        new EnumMap<>(CultureCategory.class), new ArrayList<>()));
                }
            });
        }
    }

    @Override
    public int getCulturePoints(NationId nationId) {
        return cultures.computeIfAbsent(nationId, this::loadCulture).points();
    }

    /**
     * 获取指定类别的文化点数
     */
    public int getCategoryPoints(NationId nationId, CultureCategory category) {
        NationCulture culture = cultures.computeIfAbsent(nationId, this::loadCulture);
        return culture.categoryPoints().getOrDefault(category, 0);
    }

    /**
     * 获取下一个可解锁的特质
     */
    public CultureTrait getNextUnlockableTrait(NationId nationId) {
        NationCulture culture = cultures.computeIfAbsent(nationId, this::loadCulture);
        int totalPoints = culture.points();

        for (CultureTrait trait : TRAITS.values()) {
            if (!culture.traitIds().contains(trait.id())) {
                // 根据特质等级和类别计算解锁需求
                int requiredPoints = calculateTraitRequirement(trait);
                if (totalPoints >= requiredPoints) {
                    // 检查类别是否匹配
                    int categoryPoints = culture.categoryPoints().getOrDefault(trait.category(), 0);
                    if (categoryPoints >= requiredPoints / 2) {
                        return trait;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 计算特质解锁所需点数
     */
    private int calculateTraitRequirement(CultureTrait trait) {
        // 基础需求 = 特质等级 * 100
        int baseRequirement = trait.level() * 100;
        // 加上已解锁相同类别特质数量的加成
        return baseRequirement;
    }

    /**
     * 计算文化影响力分数
     * 用于国家排名和外交
     */
    public double calculateInfluenceScore(NationId nationId) {
        NationCulture culture = cultures.get(nationId);
        if (culture == null) return 0.0;

        double score = 0;
        // 基础分数 = 总点数
        score += culture.points() * 0.01;

        // 分类加成
        for (Map.Entry<CultureCategory, Integer> entry : culture.categoryPoints().entrySet()) {
            // 每个分类前100点获得25%加成
            int catPoints = entry.getValue();
            score += Math.min(catPoints, 100) * 0.25;
            // 超过100点的部分获得10%加成
            if (catPoints > 100) {
                score += (catPoints - 100) * 0.1;
            }
        }

        // 特质加成
        for (String traitId : culture.traitIds()) {
            CultureTrait trait = TRAITS.get(traitId);
            if (trait != null) {
                score += trait.level() * 10;
            }
        }

        return Math.round(score * 100.0) / 100.0;
    }

    /**
     * 批量计算所有国家的影响力排名
     */
    public List<NationId> getInfluenceRanking() {
        return cultures.entrySet().stream()
            .sorted((e1, e2) -> Double.compare(
                calculateInfluenceScore(e2.getKey()),
                calculateInfluenceScore(e1.getKey())
            ))
            .map(Map.Entry::getKey)
            .toList();
    }

    /**
     * 加载所有文化数据 (供外部调用)
     */
    public void loadCultures() {
        if (nationService != null) {
            nationService.nations().forEach(nation -> {
                loadCulture(nation.id());
            });
        }
        plugin.getLogger().info("已加载 " + cultures.size() + " 个国家的文化数据");
    }

    /**
     * 保存所有文化数据 (供外部调用)
     */
    public void saveCultures() {
        cultures.forEach(this::saveCulture);
        plugin.getLogger().info("已保存 " + cultures.size() + " 个国家的文化数据");
    }

    /**
     * 保存单个国家的文化数据
     * @param nationId 国家ID
     */
    public void saveCulture(NationId nationId) {
        NationCulture culture = cultures.get(nationId);
        if (culture != null) {
            saveCulture(nationId, culture);
        }
    }

    private NationCulture loadCulture(NationId nationId) {
        // 优先从缓存获取
        NationCulture cached = cultures.get(nationId);
        if (cached != null) {
            return cached;
        }

        // 首先尝试从数据库加载
        if (databaseService != null && databaseService.isRunning()) {
            var loaded = loadFromDatabase(nationId);
            if (loaded != null) {
                cultures.put(nationId, loaded);
                return loaded;
            }
        }

        // 尝试从 YAML 文件加载
        var yamlLoaded = loadFromYaml(nationId);
        if (yamlLoaded != null) {
            cultures.put(nationId, yamlLoaded);
            return yamlLoaded;
        }

        // 使用默认值
        Map<CultureCategory, Integer> categoryPoints = new EnumMap<>(CultureCategory.class);
        for (CultureCategory cat : CultureCategory.values()) {
            categoryPoints.put(cat, 0);
        }
        NationCulture defaultCulture = new NationCulture(nationId, 0, categoryPoints, new ArrayList<>());
        cultures.put(nationId, defaultCulture);
        return defaultCulture;
    }

    /**
     * 强制重新加载单个国家的文化数据（绕过缓存）
     */
    public NationCulture forceReloadCulture(NationId nationId) {
        cultures.remove(nationId);
        return loadCulture(nationId);
    }

    /**
     * 重置国家文化数据
     */
    public void resetCulture(NationId nationId) {
        Map<CultureCategory, Integer> categoryPoints = new EnumMap<>(CultureCategory.class);
        for (CultureCategory cat : CultureCategory.values()) {
            categoryPoints.put(cat, 0);
        }
        NationCulture resetCulture = new NationCulture(nationId, 0, categoryPoints, new ArrayList<>());
        cultures.put(nationId, resetCulture);
        saveCulture(nationId, resetCulture);
        plugin.getLogger().info("已重置国家 " + nationId.value() + " 的文化数据");
    }

    /**
     * 从数据库加载文化数据
     */
    private NationCulture loadFromDatabase(NationId nationId) {
        if (databaseService == null || !databaseService.isRunning()) {
            return null;
        }

        return databaseService.dataSource().map(ds -> {
            try (var conn = ds.getConnection();
                 var stmt = conn.prepareStatement(
                     "SELECT points, category_points, trait_ids FROM starcore_culture WHERE nation_id = ?")) {

                stmt.setString(1, nationId.value().toString());
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int points = rs.getInt("points");
                        String categoryPointsStr = rs.getString("category_points");
                        String traitIdsStr = rs.getString("trait_ids");

                        Map<CultureCategory, Integer> categoryPoints = deserializeCategoryPoints(categoryPointsStr);
                        List<String> traitIds = traitIdsStr != null && !traitIdsStr.isEmpty()
                            ? Arrays.asList(traitIdsStr.split(","))
                            : new ArrayList<>();

                        return new NationCulture(nationId, points, categoryPoints, traitIds);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("加载文化数据失败: " + e.getMessage());
            }
            return null;
        }).orElse(null);
    }

    @Override
    public List<CultureTrait> getTraits(NationId nationId) {
        NationCulture culture = cultures.get(nationId);
        if (culture == null) return List.of();

        List<CultureTrait> traits = new ArrayList<>();
        for (String traitId : culture.traitIds()) {
            CultureTrait trait = TRAITS.get(traitId);
            if (trait != null) {
                traits.add(trait);
            }
        }
        return traits;
    }

    @Override
    public void addCulturePoints(NationId nationId, int amount, CultureCategory category) {
        NationCulture culture = cultures.computeIfAbsent(nationId, this::loadCulture);

        int newPoints = culture.points() + amount;
        Map<CultureCategory, Integer> newCategoryPoints = new EnumMap<>(CultureCategory.class);
        newCategoryPoints.putAll(culture.categoryPoints());
        newCategoryPoints.merge(category, amount, Integer::sum);

        List<String> newTraitIds = new ArrayList<>(culture.traitIds());

        // 检查是否解锁新特质
        checkAndUnlockTraits(nationId, newPoints, category, newTraitIds);

        NationCulture newCulture = new NationCulture(nationId, newPoints, newCategoryPoints, newTraitIds);
        cultures.put(nationId, newCulture);

        // 保存
        saveCulture(nationId, newCulture);
    }

    /**
     * 批量添加文化点数（用于周期性结算）
     * @param additions 每个国家要添加的点数的映射
     */
    public void batchAddCulturePoints(Map<NationId, Map<CultureCategory, Integer>> additions) {
        additions.forEach((nationId, categoryPoints) -> {
            categoryPoints.forEach((category, points) -> {
                if (points > 0) {
                    addCulturePoints(nationId, points, category);
                }
            });
        });
    }

    /**
     * 计算每日文化增长量
     * 基于国家活跃度和特质加成
     */
    public int calculateDailyCultureGrowth(NationId nationId) {
        NationCulture culture = cultures.get(nationId);
        if (culture == null) return 0;

        int baseGrowth = 5; // 基础每日增长

        // 特质加成
        int traitBonus = 0;
        for (String traitId : culture.traitIds()) {
            CultureTrait trait = TRAITS.get(traitId);
            if (trait != null) {
                traitBonus += trait.level() * 2;
            }
        }

        // 文化等级加成
        CultureLevel level = getLevel(nationId);
        int levelBonus = switch (level) {
            case BARBARIC -> 0;
            case CIVILIZED -> 5;
            case DEVELOPED -> 15;
            case FLOURISHING -> 30;
            case GOLDEN_AGE -> 50;
            case LEGENDARY -> 100;
        };

        return baseGrowth + traitBonus + levelBonus;
    }

    /**
     * 计算每周文化增长（用于周期性结算）
     */
    public Map<CultureCategory, Integer> calculateWeeklyGrowthBreakdown(NationId nationId) {
        Map<CultureCategory, Integer> breakdown = new EnumMap<>(CultureCategory.class);
        int dailyGrowth = calculateDailyCultureGrowth(nationId);

        // 默认按比例分配到各类别
        int artGrowth = dailyGrowth * 7 / 3;
        int scienceGrowth = dailyGrowth * 7 / 3;
        int tradeGrowth = dailyGrowth * 7 / 3;

        breakdown.put(CultureCategory.ART, artGrowth);
        breakdown.put(CultureCategory.SCIENCE, scienceGrowth);
        breakdown.put(CultureCategory.TRADE, tradeGrowth);
        breakdown.put(CultureCategory.MILITARY, dailyGrowth);
        breakdown.put(CultureCategory.RELIGION, dailyGrowth / 2);
        breakdown.put(CultureCategory.POLITICS, dailyGrowth / 2);

        return breakdown;
    }

    /**
     * 获取文化年度报告
     */
    public CultureAnnualReport generateAnnualReport(NationId nationId) {
        NationCulture culture = cultures.get(nationId);
        if (culture == null) {
            culture = loadCulture(nationId);
        }

        List<CultureTrait> traits = getTraits(nationId);
        CultureLevel level = getLevel(nationId);
        double influenceScore = calculateInfluenceScore(nationId);

        // 计算各分类点数占比
        Map<CultureCategory, Double> categoryPercentages = new EnumMap<>(CultureCategory.class);
        int total = Math.max(1, culture.points());
        for (CultureCategory cat : CultureCategory.values()) {
            int catPoints = culture.categoryPoints().getOrDefault(cat, 0);
            categoryPercentages.put(cat, (double) catPoints / total * 100);
        }

        // 获取下个可解锁特质
        CultureTrait nextTrait = getNextUnlockableTrait(nationId);
        int pointsToNextTrait = 0;
        if (nextTrait != null) {
            pointsToNextTrait = nextTrait.level() * 100 - culture.points();
        }

        return new CultureAnnualReport(
            nationId,
            culture.points(),
            culture.categoryPoints(),
            traits,
            level,
            influenceScore,
            categoryPercentages,
            nextTrait,
            Math.max(0, pointsToNextTrait),
            calculateDailyCultureGrowth(nationId)
        );
    }

    /**
     * 文化年度报告记录
     */
    public record CultureAnnualReport(
        NationId nationId,
        int totalPoints,
        Map<CultureCategory, Integer> categoryPoints,
        List<CultureTrait> traits,
        CultureLevel level,
        double influenceScore,
        Map<CultureCategory, Double> categoryPercentages,
        CultureTrait nextTrait,
        int pointsToNextTrait,
        int dailyGrowth
    ) {
        public String getFormattedReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== ").append(nationId.value()).append(" 文化年度报告 ===\n");
            sb.append("文化总值: ").append(totalPoints).append("\n");
            sb.append("文化等级: ").append(level.getColor()).append(level.getName()).append("\n");
            sb.append("影响力分数: ").append(influenceScore).append("\n");
            sb.append("每日增长: ").append(dailyGrowth).append("\n");
            sb.append("\n已解锁特质 (").append(traits.size()).append("):\n");
            for (CultureTrait trait : traits) {
                sb.append("  - ").append(trait.name()).append(": ").append(trait.description()).append("\n");
            }
            if (nextTrait != null) {
                sb.append("\n下个可解锁: ").append(nextTrait.name());
                sb.append(" (").append(pointsToNextTrait).append(" 点)\n");
            }
            return sb.toString();
        }
    }

    private void checkAndUnlockTraits(NationId nationId, int points, CultureCategory category, List<String> traitIds) {
        // 检查所有可解锁的特质
        for (CultureTrait trait : TRAITS.values()) {
            // 跳过已解锁的特质
            if (traitIds.contains(trait.id())) {
                continue;
            }

            // 检查类别匹配
            if (trait.category() != category) {
                continue;
            }

            // 计算解锁条件
            int requiredPoints = trait.level() * 100; // 等级越高，需要越多
            int categoryPoints = 0; // 需要从该类别获得足够点数

            switch (trait.level()) {
                case 1 -> {
                    // 1级特质: 100点总点数，该类别50点
                    if (points >= 100) {
                        traitIds.add(trait.id());
                    }
                }
                case 2 -> {
                    // 2级特质: 500点总点数，该类别250点
                    if (points >= 500) {
                        traitIds.add(trait.id());
                    }
                }
                case 3 -> {
                    // 3级特质: 2000点总点数，该类别1000点
                    if (points >= 2000) {
                        traitIds.add(trait.id());
                    }
                }
            }
        }
    }

    /**
     * 检查并尝试解锁特质，返回是否解锁了新特质
     */
    public boolean tryUnlockTrait(NationId nationId, String traitId) {
        NationCulture culture = cultures.get(nationId);
        if (culture == null || culture.traitIds().contains(traitId)) {
            return false;
        }

        CultureTrait trait = TRAITS.get(traitId);
        if (trait == null) {
            return false;
        }

        int totalPoints = culture.points();
        int categoryPoints = culture.categoryPoints().getOrDefault(trait.category(), 0);
        int requiredTotal = trait.level() * 100;
        int requiredCategory = requiredTotal / 2;

        // 检查是否满足解锁条件
        if (totalPoints >= requiredTotal && categoryPoints >= requiredCategory) {
            List<String> newTraitIds = new ArrayList<>(culture.traitIds());
            newTraitIds.add(traitId);

            NationCulture newCulture = new NationCulture(
                nationId,
                culture.points(),
                culture.categoryPoints(),
                newTraitIds
            );
            cultures.put(nationId, newCulture);
            saveCulture(nationId, newCulture);
            return true;
        }

        return false;
    }

    /**
     * 获取特质解锁进度
     */
    public double getTraitUnlockProgress(NationId nationId, String traitId) {
        NationCulture culture = cultures.get(nationId);
        if (culture == null) return 0.0;

        if (culture.traitIds().contains(traitId)) {
            return 1.0; // 已解锁
        }

        CultureTrait trait = TRAITS.get(traitId);
        if (trait == null) return 0.0;

        int totalPoints = culture.points();
        int categoryPoints = culture.categoryPoints().getOrDefault(trait.category(), 0);
        int requiredTotal = trait.level() * 100;
        int requiredCategory = requiredTotal / 2;

        double totalProgress = Math.min(1.0, (double) totalPoints / requiredTotal);
        double categoryProgress = Math.min(1.0, (double) categoryPoints / requiredCategory);

        return (totalProgress + categoryProgress) / 2.0;
    }

    private void saveCulture(NationId nationId, NationCulture culture) {
        // 保存到数据库
        if (databaseService != null && databaseService.isRunning()) {
            databaseService.dataSource().ifPresent(ds -> {
                try (var conn = ds.getConnection()) {
                    boolean isSQLite = conn.getMetaData().getDatabaseProductName().equalsIgnoreCase("SQLite");

                    String sql;
                    if (isSQLite) {
                        sql = """
                            INSERT INTO starcore_culture (nation_id, points, category_points, trait_ids, last_updated)
                            VALUES (?, ?, ?, ?, ?)
                            ON CONFLICT(nation_id) DO UPDATE SET
                                points = excluded.points,
                                category_points = excluded.category_points,
                                trait_ids = excluded.trait_ids,
                                last_updated = excluded.last_updated
                            """;
                    } else {
                        sql = """
                            INSERT INTO starcore_culture (nation_id, points, category_points, trait_ids, last_updated)
                            VALUES (?, ?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE
                                points = VALUES(points),
                                category_points = VALUES(category_points),
                                trait_ids = VALUES(trait_ids),
                                last_updated = VALUES(last_updated)
                            """;
                    }

                    try (var stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, nationId.value().toString());
                        stmt.setInt(2, culture.points());
                        stmt.setString(3, serializeCategoryPoints(culture.categoryPoints()));
                        stmt.setString(4, String.join(",", culture.traitIds()));
                        stmt.setLong(5, System.currentTimeMillis());
                        stmt.executeUpdate();
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("保存文化数据到数据库失败: " + e.getMessage());
                    // 数据库保存失败,降级到 YAML 保存
                    saveToYaml(nationId, culture);
                }
            });
        } else {
            // 数据库不可用,保存到 YAML
            saveToYaml(nationId, culture);
        }
    }

    // ==================== YAML 持久化方法 (数据库降级方案) ====================

    /**
     * 从 YAML 文件加载单个国家文化数据
     */
    private NationCulture loadFromYaml(NationId nationId) {
        if (plugin == null) return null;

        File dataDir = new File(plugin.getDataFolder(), "social-simulation");
        if (!dataDir.exists()) dataDir.mkdirs();
        dataFile = new File(dataDir, "culture_data.yml");
        if (!dataFile.exists()) {
            return null;
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        String path = "nations." + nationId.value().toString();
        if (!dataConfig.contains(path)) {
            return null;
        }

        try {
            int points = dataConfig.getInt(path + ".points", 0);
            Map<CultureCategory, Integer> categoryPoints = new EnumMap<>(CultureCategory.class);

            // 加载分类点数
            if (dataConfig.contains(path + ".category_points")) {
                Map<String, Object> catData = dataConfig.getConfigurationSection(path + ".category_points").getValues(false);
                for (Map.Entry<String, Object> entry : catData.entrySet()) {
                    try {
                        CultureCategory cat = CultureCategory.valueOf(entry.getKey());
                        categoryPoints.put(cat, ((Number) entry.getValue()).intValue());
                    } catch (Exception e) {
                        plugin.getLogger().warning("加载文化类别失败: " + e.getMessage());
                    }
                }
            }

            // 加载特质
            List<String> traitIds = dataConfig.getStringList(path + ".trait_ids");

            return new NationCulture(nationId, points, categoryPoints, traitIds);
        } catch (Exception e) {
            plugin.getLogger().warning("从 YAML 加载文化数据失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 保存单个国家文化数据到 YAML 文件
     */
    private void saveToYaml(NationId nationId, NationCulture culture) {
        if (plugin == null) return;

        File dataDir = new File(plugin.getDataFolder(), "social-simulation");
        if (!dataDir.exists()) dataDir.mkdirs();
        dataFile = new File(dataDir, "culture_data.yml");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        String path = "nations." + nationId.value().toString();

        try {
            dataConfig.set(path + ".points", culture.points());

            // 保存分类点数
            for (Map.Entry<CultureCategory, Integer> entry : culture.categoryPoints().entrySet()) {
                dataConfig.set(path + ".category_points." + entry.getKey().name(), entry.getValue());
            }

            // 保存特质
            dataConfig.set(path + ".trait_ids", culture.traitIds());

            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("保存文化数据到 YAML 失败: " + e.getMessage());
        }
    }

    /**
     * 保存所有文化数据到 YAML (批量保存,用于插件关闭时)
     */
    public void saveAllToYaml() {
        if (plugin == null) return;

        File dataDir = new File(plugin.getDataFolder(), "social-simulation");
        if (!dataDir.exists()) dataDir.mkdirs();
        dataFile = new File(dataDir, "culture_data.yml");
        dataConfig = new YamlConfiguration();

        try {
            for (Map.Entry<NationId, NationCulture> entry : cultures.entrySet()) {
                NationId nationId = entry.getKey();
                NationCulture culture = entry.getValue();
                String path = "nations." + nationId.value().toString();

                dataConfig.set(path + ".points", culture.points());

                for (Map.Entry<CultureCategory, Integer> cat : culture.categoryPoints().entrySet()) {
                    dataConfig.set(path + ".category_points." + cat.getKey().name(), cat.getValue());
                }

                dataConfig.set(path + ".trait_ids", culture.traitIds());
            }

            dataConfig.save(dataFile);
            plugin.getLogger().info("文化数据已批量保存到 YAML: " + cultures.size() + " 个国家");
        } catch (IOException e) {
            plugin.getLogger().warning("批量保存文化数据到 YAML 失败: " + e.getMessage());
        }
    }

    /**
     * 从 YAML 加载所有文化数据
     */
    public void loadAllFromYaml() {
        if (plugin == null) return;

        File dataDir = new File(plugin.getDataFolder(), "social-simulation");
        if (!dataDir.exists()) {
            plugin.getLogger().info("social-simulation 目录不存在,跳过文化数据加载");
            return;
        }
        dataFile = new File(dataDir, "culture_data.yml");
        if (!dataFile.exists()) {
            plugin.getLogger().info("文化数据 YAML 文件不存在");
            return;
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        if (!dataConfig.contains("nations")) {
            return;
        }

        try {
            Map<String, Object> nations = dataConfig.getConfigurationSection("nations").getValues(false);
            for (Map.Entry<String, Object> entry : nations.entrySet()) {
                UUID nationUuid = UUID.fromString(entry.getKey());
                NationId nationId = new NationId(nationUuid);

                String path = "nations." + entry.getKey();
                int points = dataConfig.getInt(path + ".points", 0);

                Map<CultureCategory, Integer> categoryPoints = new EnumMap<>(CultureCategory.class);
                if (dataConfig.contains(path + ".category_points")) {
                    Map<String, Object> catData = dataConfig.getConfigurationSection(path + ".category_points").getValues(false);
                    for (Map.Entry<String, Object> cat : catData.entrySet()) {
                        try {
                            CultureCategory catEnum = CultureCategory.valueOf(cat.getKey());
                            categoryPoints.put(catEnum, ((Number) cat.getValue()).intValue());
                        } catch (Exception e) {
                            plugin.getLogger().warning("加载文化类别失败: " + e.getMessage());
                        }
                    }
                }

                List<String> traitIds = dataConfig.getStringList(path + ".trait_ids");

                NationCulture culture = new NationCulture(nationId, points, categoryPoints, traitIds);
                cultures.put(nationId, culture);
            }

            plugin.getLogger().info("从 YAML 加载文化数据: " + cultures.size() + " 个国家");
        } catch (Exception e) {
            plugin.getLogger().warning("从 YAML 加载文化数据失败: " + e.getMessage());
        }
    }

    /**
     * 序列化石文化类别点数
     */
    private String serializeCategoryPoints(Map<CultureCategory, Integer> categoryPoints) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<CultureCategory, Integer> entry : categoryPoints.entrySet()) {
            if (sb.length() > 0) sb.append(";");
            sb.append(entry.getKey().name()).append(":").append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * 反序列化石文化类别点数
     */
    private Map<CultureCategory, Integer> deserializeCategoryPoints(String data) {
        Map<CultureCategory, Integer> result = new EnumMap<>(CultureCategory.class);
        if (data == null || data.isEmpty()) return result;

        for (String pair : data.split(";")) {
            String[] parts = pair.split(":");
            if (parts.length == 2) {
                try {
                    CultureCategory cat = CultureCategory.valueOf(parts[0]);
                    int value = Integer.parseInt(parts[1]);
                    result.put(cat, value);
                } catch (Exception e) {
                    plugin.getLogger().warning("反序列化文化类别失败: " + e.getMessage());
                }
            }
        }
        return result;
    }

    @Override
    public CultureLevel getLevel(NationId nationId) {
        return CultureLevel.fromPoints(getCulturePoints(nationId));
    }

    @Override
    public boolean canSpreadCulture(NationId from, NationId to) {
        int fromPoints = getCulturePoints(from);
        int toPoints = getCulturePoints(to);
        // 高文化国家可以向低文化国家传播
        return fromPoints > toPoints * 1.5;
    }

    @Override
    public void spreadCulture(NationId from, NationId to) {
        if (!canSpreadCulture(from, to)) return;

        int fromPoints = getCulturePoints(from);
        int toPoints = getCulturePoints(to);
        int spread = Math.min(10, (fromPoints - toPoints) / 10);

        // 向较弱文化国家传播文化
        CultureCategory dominantCategory = getDominantCategory(from);
        addCulturePoints(to, spread, dominantCategory);

        // 传播者获得一点影响力
        addCulturePoints(from, 1, CultureCategory.TRADE);
    }

    private CultureCategory getDominantCategory(NationId nationId) {
        NationCulture culture = cultures.get(nationId);
        if (culture == null) return CultureCategory.TRADE;

        return culture.categoryPoints().entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(CultureCategory.TRADE);
    }

    @Override
    public boolean hasCultureConflict(NationId nation1, NationId nation2) {
        return getCultureConflictLevel(nation1, nation2) > 0;
    }

    /**
     * 获取文化冲突强度等级 (0-3)
     * 0: 无冲突
     * 1: 轻微冲突 (可调解)
     * 2: 中度冲突 (外交困难)
     * 3: 严重冲突 (战争边缘)
     */
    public int getCultureConflictLevel(NationId nation1, NationId nation2) {
        List<CultureTrait> traits1 = getTraits(nation1);
        List<CultureTrait> traits2 = getTraits(nation2);

        int conflictLevel = 0;

        // 冲突组合检测
        boolean hasMartial1 = traits1.stream().anyMatch(t -> t.category() == CultureCategory.MILITARY);
        boolean hasMartial2 = traits2.stream().anyMatch(t -> t.category() == CultureCategory.MILITARY);
        boolean hasReligious1 = traits1.stream().anyMatch(t -> t.category() == CultureCategory.RELIGION);
        boolean hasReligious2 = traits2.stream().anyMatch(t -> t.category() == CultureCategory.RELIGION);
        boolean hasTrade1 = traits1.stream().anyMatch(t -> t.category() == CultureCategory.TRADE);
        boolean hasTrade2 = traits2.stream().anyMatch(t -> t.category() == CultureCategory.TRADE);
        boolean hasScience1 = traits1.stream().anyMatch(t -> t.category() == CultureCategory.SCIENCE);
        boolean hasScience2 = traits2.stream().anyMatch(t -> t.category() == CultureCategory.SCIENCE);
        boolean hasPolitics1 = traits1.stream().anyMatch(t -> t.category() == CultureCategory.POLITICS);
        boolean hasPolitics2 = traits2.stream().anyMatch(t -> t.category() == CultureCategory.POLITICS);

        // 军事 vs 宗教 (严重冲突)
        if ((hasMartial1 && hasReligious2) || (hasMartial2 && hasReligious1)) {
            conflictLevel = Math.max(conflictLevel, 3);
        }

        // 军事 vs 商业 (中度冲突)
        if ((hasMartial1 && hasTrade2) || (hasMartial2 && hasTrade1)) {
            conflictLevel = Math.max(conflictLevel, 2);
        }

        // 宗教 vs 科学 (中度冲突)
        if ((hasReligious1 && hasScience2) || (hasReligious2 && hasScience1)) {
            conflictLevel = Math.max(conflictLevel, 2);
        }

        // 政治体制差异 (轻微冲突)
        if ((hasPolitics1 && hasMartial2) || (hasPolitics2 && hasMartial1)) {
            conflictLevel = Math.max(conflictLevel, 1);
        }

        // 特质等级差异造成的冲突
        int martialDiff = Math.abs(
            traits1.stream().filter(t -> t.category() == CultureCategory.MILITARY).mapToInt(CultureTrait::level).sum() -
            traits2.stream().filter(t -> t.category() == CultureCategory.MILITARY).mapToInt(CultureTrait::level).sum()
        );
        if (martialDiff >= 2) {
            conflictLevel = Math.max(conflictLevel, 1);
        }

        return conflictLevel;
    }

    /**
     * 获取文化冲突描述
     */
    public String getCultureConflictDescription(NationId nation1, NationId nation2) {
        List<CultureTrait> traits1 = getTraits(nation1);
        List<CultureTrait> traits2 = getTraits(nation2);

        StringBuilder sb = new StringBuilder();
        int level = getCultureConflictLevel(nation1, nation2);

        if (level == 0) {
            return "无文化冲突";
        }

        boolean hasMartial1 = traits1.stream().anyMatch(t -> t.category() == CultureCategory.MILITARY);
        boolean hasMartial2 = traits2.stream().anyMatch(t -> t.category() == CultureCategory.MILITARY);
        boolean hasReligious1 = traits1.stream().anyMatch(t -> t.category() == CultureCategory.RELIGION);
        boolean hasReligious2 = traits2.stream().anyMatch(t -> t.category() == CultureCategory.RELIGION);
        boolean hasTrade1 = traits1.stream().anyMatch(t -> t.category() == CultureCategory.TRADE);
        boolean hasTrade2 = traits2.stream().anyMatch(t -> t.category() == CultureCategory.TRADE);
        boolean hasScience1 = traits1.stream().anyMatch(t -> t.category() == CultureCategory.SCIENCE);
        boolean hasScience2 = traits2.stream().anyMatch(t -> t.category() == CultureCategory.SCIENCE);

        if (level >= 3) {
            if ((hasMartial1 && hasReligious2) || (hasMartial2 && hasReligious1)) {
                sb.append("军事文化与宗教文化存在根本对立");
            }
        }

        if (level >= 2) {
            if ((hasMartial1 && hasTrade2) || (hasMartial2 && hasTrade1)) {
                if (sb.length() > 0) sb.append("; ");
                sb.append("军事帝国与商业文明的价值观差异");
            }
            if ((hasReligious1 && hasScience2) || (hasReligious2 && hasScience1)) {
                if (sb.length() > 0) sb.append("; ");
                sb.append("宗教信仰与科学理性的意识形态分歧");
            }
        }

        if (level == 1) {
            sb.append("存在潜在的文化差异");
        }

        return sb.toString();
    }

    /**
     * 获取文化兼容性分数 (0-100)
     * 100: 完全兼容
     * 0: 完全对立
     */
    public int getCultureCompatibilityScore(NationId nation1, NationId nation2) {
        int conflictLevel = getCultureConflictLevel(nation1, nation2);
        return Math.max(0, 100 - (conflictLevel * 30));
    }

    /**
     * 检查是否可以通过外交缓解文化冲突
     */
    public boolean canResolveConflict(NationId nation1, NationId nation2) {
        // 文化冲突等级越高越难调解
        return getCultureConflictLevel(nation1, nation2) < 3;
    }

    /**
     * 计算调解文化冲突所需的成本
     */
    public int getConflictResolutionCost(NationId nation1, NationId nation2) {
        int conflictLevel = getCultureConflictLevel(nation1, nation2);
        return conflictLevel * 500; // 每级需要500文化点数来调解
    }

    /**
     * 尝试调解文化冲突
     */
    public boolean attemptConflictResolution(NationId nation1, NationId nation2, int investment) {
        int cost = getConflictResolutionCost(nation1, nation2);
        if (investment < cost) {
            return false;
        }

        // 消耗投资点数，降低冲突等级
        int currentLevel = getCultureConflictLevel(nation1, nation2);
        if (currentLevel > 0) {
            // 这里应该更新持久化的冲突状态
            plugin.getLogger().info("文化冲突调解成功，等级从 " + currentLevel + " 降低");
            return true;
        }
        return false;
    }

    record NationCulture(
        NationId nationId,
        int points,
        Map<CultureCategory, Integer> categoryPoints,
        List<String> traitIds
    ) {}
}
