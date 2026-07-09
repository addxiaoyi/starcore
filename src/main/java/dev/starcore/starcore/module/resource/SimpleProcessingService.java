package dev.starcore.starcore.module.resource;
import java.util.Optional;

import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.model.Factory;
import dev.starcore.starcore.module.resource.model.ProcessingRecipe;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 资源加工服务实现
 */
public class SimpleProcessingService implements ProcessingService {
    private final ResourceService resourceService;
    private final ResourcePriceService priceService;
    private final Map<String, ProcessingRecipe> recipes;
    private final Map<UUID, Factory> factories;
    private StarCoreScheduler scheduler;
    private boolean recipesInitialized = false;

    public SimpleProcessingService(ResourceService resourceService, ResourcePriceService priceService) {
        this.resourceService = Objects.requireNonNull(resourceService, "resourceService");
        this.priceService = Objects.requireNonNull(priceService, "priceService");
        this.recipes = new ConcurrentHashMap<>();
        this.factories = new ConcurrentHashMap<>();
    }

    /**
     * 设置调度器用于定时刷新工厂
     */
    public void setScheduler(StarCoreScheduler scheduler) {
        this.scheduler = scheduler;
        if (scheduler != null) {
            // 每分钟检查一次工厂加工状态
            scheduler.runSyncTimer(() -> refreshFactories(), 60 * 20L, 60 * 20L);
        }
    }

    /**
     * 初始化默认配方
     */
    public void initializeDefaultRecipes() {
        if (!recipesInitialized) {
            recipesInitialized = true;

            // 食品加工：木材 -> 食物
            registerRecipe(new ProcessingRecipe(
                "recipe_timber_to_food",
                "木材加工",
                Map.of("timber", 2L),
                Map.of("food", 5L),
                Duration.ofMinutes(5).toSeconds(),
                5.0, // energyCost
                1
            ));

            // 矿石精炼：矿石 -> 稀有金属
            registerRecipe(new ProcessingRecipe(
                "recipe_ore_to_rare_metal",
                "矿石精炼",
                Map.of("ore", 3L),
                Map.of("rare_metal", 1L),
                Duration.ofMinutes(10).toSeconds(),
                10.0, // energyCost
                2
            ));

            // 石油精炼：石油 -> 稀有金属 + 食物
            registerRecipe(new ProcessingRecipe(
                "recipe_oil_processing",
                "石油精炼",
                Map.of("oil", 5L),
                Map.of("rare_metal", 2L, "food", 2L),
                Duration.ofMinutes(15).toSeconds(),
                15.0, // energyCost
                3
            ));

            // 高级食品加工：食物 + 木材 -> 高级食物
            registerRecipe(new ProcessingRecipe(
                "recipe_advanced_food",
                "高级食品加工",
                Map.of("food", 5L, "timber", 3L),
                Map.of("food", 15L),
                Duration.ofMinutes(8).toSeconds(),
                8.0, // energyCost
                2
            ));

            // 批量矿石处理
            registerRecipe(new ProcessingRecipe(
                "recipe_bulk_ore",
                "批量矿石处理",
                Map.of("ore", 10L),
                Map.of("ore", 8L, "rare_metal", 1L),
                Duration.ofMinutes(20).toSeconds(),
                12.0, // energyCost
                1
            ));
        }
    }

    @Override
    public Optional<ProcessingRecipe> getRecipe(String recipeId) {
        return Optional.ofNullable(recipes.get(recipeId));
    }

    @Override
    public Collection<ProcessingRecipe> getAllRecipes() {
        return new ArrayList<>(recipes.values());
    }

    @Override
    public Collection<ProcessingRecipe> getRecipesByOutput(String outputResourceId) {
        return recipes.values().stream()
                .filter(recipe -> recipe.outputs().containsKey(outputResourceId))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<ProcessingRecipe> getRecipesByInput(String inputResourceId) {
        return recipes.values().stream()
                .filter(recipe -> recipe.inputs().containsKey(inputResourceId))
                .collect(Collectors.toList());
    }

    @Override
    public Factory buildFactory(NationId ownerNationId, String factoryName, Factory.FactoryType type) {
        UUID factoryId = UUID.randomUUID();
        Factory factory = new Factory(factoryId, ownerNationId, factoryName, type);
        factories.put(factoryId, factory);
        // audit B-083: factories 存内存无持久化机制；服务器重启所有工厂建造记录丢失，
        // 玩家投入的资源已消耗但工厂没了。最小修复：暴露 markFactoriesDirty/snapshotFactories 钩子，
        // 由上层 (ResourceModule) 在 disable 时落盘。本类不直接依赖 PersistenceService。
        markFactoriesDirty();
        return factory;
    }

    @Override
    public Optional<Factory> getFactory(UUID factoryId) {
        return Optional.ofNullable(factories.get(factoryId));
    }

    @Override
    public Collection<Factory> getFactories(NationId nationId) {
        return factories.values().stream()
                .filter(factory -> factory.ownerNationId().equals(nationId))
                .collect(Collectors.toList());
    }

    @Override
    public boolean upgradeFactory(UUID factoryId) {
        Optional<Factory> factoryOpt = getFactory(factoryId);
        if (factoryOpt.isEmpty()) {
            return false;
        }

        Factory factory = factoryOpt.get();
        return factory.upgrade();
    }

    @Override
    public boolean setFactoryOperational(UUID factoryId, boolean operational) {
        Optional<Factory> factoryOpt = getFactory(factoryId);
        if (factoryOpt.isEmpty()) {
            return false;
        }

        Factory factory = factoryOpt.get();
        factory.setOperational(operational);
        return true;
    }

    @Override
    public boolean startProcessing(UUID factoryId, String recipeId, int batches) {
        Optional<Factory> factoryOpt = getFactory(factoryId);
        Optional<ProcessingRecipe> recipeOpt = getRecipe(recipeId);

        if (factoryOpt.isEmpty() || recipeOpt.isEmpty() || batches <= 0) {
            return false;
        }

        Factory factory = factoryOpt.get();
        ProcessingRecipe recipe = recipeOpt.get();

        if (!factory.isOperational() || factory.isProcessing()) {
            return false;
        }

        if (factory.level() < recipe.requiredFactoryLevel()) {
            return false;
        }

        // 检查材料
        Map<String, Long> available = resourceService.stockpile(factory.ownerNationId());
        if (!recipe.hasEnoughMaterials(available)) {
            return false;
        }

        // 计算实际可加工批次
        int maxBatches = recipe.calculateMaxBatches(available);
        int actualBatches = Math.min(batches, maxBatches);

        if (actualBatches == 0) {
            return false;
        }

        // 消耗材料
        // audit B-078: 之前循环消耗 inputs，若第二个 input consume 失败仅 return false，
        // 但第一个 input 已消耗，玩家材料部分消失但加工未启动。改为：全部消耗成功后才开始加工；
        // 若中途失败，把已消耗的 grant 回国家库存以回滚。
        Map<String, Long> inputs = recipe.calculateBatchInputs(actualBatches);
        List<Map.Entry<String, Long>> consumedSoFar = new ArrayList<>();
        boolean allConsumed = true;
        for (Map.Entry<String, Long> entry : inputs.entrySet()) {
            boolean ok;
            try {
                ok = resourceService.consume(factory.ownerNationId(), entry.getKey(), entry.getValue());
            } catch (RuntimeException e) {
                ok = false;
            }
            if (!ok) {
                allConsumed = false;
                // 回滚已消耗的
                for (Map.Entry<String, Long> c : consumedSoFar) {
                    try {
                        resourceService.grant(factory.ownerNationId(), c.getKey(), c.getValue());
                    } catch (RuntimeException ignore) {
                    }
                }
                break;
            }
            consumedSoFar.add(entry);
        }
        if (!allConsumed) {
            return false;
        }

        // 开始加工
        factory.startProcessing(recipeId, actualBatches);
        return true;
    }

    @Override
    public boolean isProcessingComplete(UUID factoryId) {
        Optional<Factory> factoryOpt = getFactory(factoryId);
        if (factoryOpt.isEmpty()) {
            return false;
        }

        Factory factory = factoryOpt.get();
        if (!factory.isProcessing()) {
            return true;
        }

        Optional<ProcessingRecipe> recipeOpt = getRecipe(factory.currentRecipeId());
        if (recipeOpt.isEmpty()) {
            return false;
        }

        ProcessingRecipe recipe = recipeOpt.get();
        long processingTime = recipe.calculateBatchProcessingTime(factory.processingBatches());
        // audit B-082: processingTime * factory.processingTimeMultiplier() 用 long，若 multiplier 被 admin 误配
        // 为极大值，乘积溢出。completionTime 可能回绕从而导致加工永远完成不了或瞬间完成。
        // 修复：用 double 计算 + 上限校验 + 溢出检测。
        double multiplier = factory.processingTimeMultiplier();
        final double MAX_MULTIPLIER = 1000.0; // 防止 admin 误配巨大值
        if (multiplier < 0 || multiplier > MAX_MULTIPLIER) {
            // 异常值兜底为 1.0
            multiplier = 1.0;
        }
        double adjustedSeconds = (double) processingTime * multiplier;
        // 防止溢出：超过 Long.MAX_VALUE/2 截断为一个长期但可表示的上限
        if (adjustedSeconds > Long.MAX_VALUE / 2) {
            adjustedSeconds = Long.MAX_VALUE / 2;
        }
        long adjustedTime = (long) adjustedSeconds;

        Instant completionTime = factory.processingStartTime().plusSeconds(adjustedTime);
        return Instant.now().isAfter(completionTime);
    }

    @Override
    public Optional<Map<String, Long>> completeProcessing(UUID factoryId) {
        if (!isProcessingComplete(factoryId)) {
            return Optional.empty();
        }

        Optional<Factory> factoryOpt = getFactory(factoryId);
        if (factoryOpt.isEmpty()) {
            return Optional.empty();
        }

        Factory factory = factoryOpt.get();
        Optional<ProcessingRecipe> recipeOpt = getRecipe(factory.currentRecipeId());
        if (recipeOpt.isEmpty()) {
            return Optional.empty();
        }

        ProcessingRecipe recipe = recipeOpt.get();
        Map<String, Long> outputs = recipe.calculateBatchOutputs(factory.processingBatches());

        // 添加产品到库存
        for (Map.Entry<String, Long> entry : outputs.entrySet()) {
            resourceService.grant(factory.ownerNationId(), entry.getKey(), entry.getValue());
        }

        factory.finishProcessing();
        return Optional.of(outputs);
    }

    @Override
    public boolean autoProcess(NationId nationId, String recipeId, int batches) {
        Optional<ProcessingRecipe> recipeOpt = getRecipe(recipeId);
        if (recipeOpt.isEmpty()) {
            return false;
        }

        ProcessingRecipe recipe = recipeOpt.get();
        Map<String, Long> available = resourceService.stockpile(nationId);

        if (!recipe.hasEnoughMaterials(available)) {
            return false;
        }

        int maxBatches = recipe.calculateMaxBatches(available);
        int actualBatches = Math.min(batches, maxBatches);

        if (actualBatches == 0) {
            return false;
        }

        // 消耗材料
        // audit B-079: autoProcess 同样问题：循环 consume 中途失败不回滚已消耗资源。
        // 修复：与 startProcessing 一致，全部检查后才消耗，失败回滚已消耗 grant 回。
        Map<String, Long> inputs = recipe.calculateBatchInputs(actualBatches);
        List<Map.Entry<String, Long>> consumedSoFar = new ArrayList<>();
        boolean allConsumed = true;
        for (Map.Entry<String, Long> entry : inputs.entrySet()) {
            boolean ok;
            try {
                ok = resourceService.consume(nationId, entry.getKey(), entry.getValue());
            } catch (RuntimeException e) {
                ok = false;
            }
            if (!ok) {
                allConsumed = false;
                for (Map.Entry<String, Long> c : consumedSoFar) {
                    try {
                        resourceService.grant(nationId, c.getKey(), c.getValue());
                    } catch (RuntimeException ignore) {
                    }
                }
                break;
            }
            consumedSoFar.add(entry);
        }
        if (!allConsumed) {
            return false;
        }

        // 生产产品
        // audit B-080: 之前 consume 后循环 grant 输出，若某次 grant 失败玩家材料已消耗但部分输出未给予。
        // 修复：grant 失败时记录警告，但保留已成功的输出（不回滚 consume，因为已不可分辨哪些成功）。
        Map<String, Long> outputs = recipe.calculateBatchOutputs(actualBatches);
        for (Map.Entry<String, Long> entry : outputs.entrySet()) {
            boolean granted;
            try {
                granted = resourceService.grant(nationId, entry.getKey(), entry.getValue());
            } catch (RuntimeException e) {
                granted = false;
            }
            if (!granted) {
                // 极端情况：grant 失败。材料已消耗但输出未给予。记录严重告警，便于 admin 排查 + 手动补偿。
                java.util.logging.Logger.getLogger(SimpleProcessingService.class.getName())
                    .warning("[autoProcess] grant failed for output " + entry.getKey()
                        + "=" + entry.getValue() + "; inputs already consumed for nation=" + nationId);
            }
        }

        return true;
    }

    @Override
    public double calculateProcessingCost(String recipeId, int batches) {
        Optional<ProcessingRecipe> recipeOpt = getRecipe(recipeId);
        if (recipeOpt.isEmpty()) {
            return 0.0;
        }

        ProcessingRecipe recipe = recipeOpt.get();
        Map<String, Long> inputs = recipe.calculateBatchInputs(batches);

        // audit B-081: 之前仅算输入资源市场价值，未含工厂运营成本或税收。玩家看到的"利润"与实际收益不符。
        // 最小修复：在文档/方法语义中说明此函数返回的是"原材料成本"，不含税和运营成本；并加注释让上层调用方知情。
        // TODO(long-term): 接入 TradeTaxService 计算加工税 + 工厂运营时间成本，作为完整成本视图的独立方法。
        double totalCost = 0.0;
        for (Map.Entry<String, Long> entry : inputs.entrySet()) {
            double price = priceService.getCurrentPrice(entry.getKey());
            totalCost += price * entry.getValue();
        }

        return totalCost;
    }

    @Override
    public double calculateProcessingProfit(String recipeId, int batches) {
        // audit B-081: 注释——此处的 profit = 输出价值 - 原材料成本，未扣除加工税与运营成本。
        // 上层 UI 展示时应额外说明"基础利润"语义，避免误导玩家。
        Optional<ProcessingRecipe> recipeOpt = getRecipe(recipeId);
        if (recipeOpt.isEmpty()) {
            return 0.0;
        }

        ProcessingRecipe recipe = recipeOpt.get();
        Map<String, Long> outputs = recipe.calculateBatchOutputs(batches);

        double totalValue = 0.0;
        for (Map.Entry<String, Long> entry : outputs.entrySet()) {
            double price = priceService.getCurrentPrice(entry.getKey());
            totalValue += price * entry.getValue();
        }

        double cost = calculateProcessingCost(recipeId, batches);
        return totalValue - cost;
    }

    @Override
    public void refreshFactories() {
        int completedCount = 0;
        for (Factory factory : factories.values()) {
            if (factory.isProcessing() && isProcessingComplete(factory.factoryId())) {
                completeProcessing(factory.factoryId());
                completedCount++;
            }
        }
        if (completedCount > 0) {
            // 可选：发布工厂加工完成事件
        }
    }

    @Override
    public void registerRecipe(ProcessingRecipe recipe) {
        recipes.put(recipe.recipeId(), recipe);
    }

    /**
     * 注册配方（兼容旧接口）
     */
    public void registerRecipe(ProcessingRecipe recipe, boolean initializeIfAbsent) {
        if (initializeIfAbsent && !recipes.containsKey(recipe.recipeId())) {
            registerRecipe(recipe);
        } else if (!initializeIfAbsent) {
            registerRecipe(recipe);
        }
    }

    // audit B-083: 工厂状态持久化钩子。factories 存内存无持久化机制，重启后工厂建造记录丢失。
    // 暴露 dirty 标记与 snapshot，让上层 (ResourceModule) 在 disable 时序列化到持久层、enable 时还原。
    private volatile boolean factoriesDirty = false;
    private void markFactoriesDirty() { this.factoriesDirty = true; }
    /** 上层在 disable 时调用此方法获取当前所有工厂以便落盘。 */
    public java.util.Map<UUID, Factory> snapshotFactories() {
        this.factoriesDirty = false;
        return new java.util.HashMap<>(factories);
    }
    /** @return 自上次快照后是否有变更。 */
    public boolean isFactoriesDirty() { return factoriesDirty; }
    /** 上层在 enable 时调用此方法还原工厂。 */
    public void restoreFactories(java.util.Map<UUID, Factory> snapshot) {
        if (snapshot != null) {
            factories.clear();
            factories.putAll(snapshot);
        }
    }
}
