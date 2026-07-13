package dev.starcore.starcore.module.resource;
import java.util.Optional;

import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.module.resource.model.ResourcePrice;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 资源价格服务实现
 */
public class SimpleResourcePriceService implements ResourcePriceService {
    private final Map<String, ResourcePrice> prices;
    // audit B-096: priceHistory 每次 refreshAllPrices 会覆盖前值，只保留"最近一份"价格；
    //   ResourcePrice 内部 priceChangePercentage 也只对 base vs current 作比较，未存历史区间。
    //   若需可靠的价格趋势，应在此处改为保留多份（如 Deque+容量上限）或在 ResourcePrice
    //   内构造历史数组。当前 getPreviousPrice 仅返回最近一份，调用方需谨慎。
    private final Map<String, Double> priceHistory;
    private final AtomicBoolean initialized;
    private final List<String> defaultResources;

    public SimpleResourcePriceService() {
        this.prices = new ConcurrentHashMap<>();
        this.priceHistory = new ConcurrentHashMap<>();
        this.initialized = new AtomicBoolean(false);
        this.defaultResources = List.of("food", "timber", "ore", "oil", "rare_metal");
    }

    /**
     * 初始化默认资源价格
     */
    public void initializeDefaultPrices() {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }

        // 初始化各资源的基准价格（可根据游戏平衡调整）
        initializePrice("food", 10.0, 100.0, 100.0);
        initializePrice("timber", 15.0, 100.0, 100.0);
        initializePrice("ore", 25.0, 100.0, 100.0);
        initializePrice("oil", 40.0, 100.0, 100.0);
        initializePrice("rare_metal", 80.0, 100.0, 100.0);
    }

    /**
     * 设置调度器用于定时刷新
     */
    public void setScheduler(StarCoreScheduler scheduler) {
        if (scheduler == null) {
            return;
        }

        // 每5分钟刷新一次价格
        scheduler.runSyncTimer(() -> refreshAllPrices(), 5 * 60 * 20L, 5 * 60 * 20L);
    }

    @Override
    public Optional<ResourcePrice> getPrice(String resourceId) {
        return Optional.ofNullable(prices.get(resourceId));
    }

    @Override
    public Collection<ResourcePrice> getAllPrices() {
        return new ArrayList<>(prices.values());
    }

    @Override
    public double getCurrentPrice(String resourceId) {
        return getPrice(resourceId)
                .map(ResourcePrice::currentPrice)
                .orElse(0.0);
    }

    @Override
    public void updateSupply(String resourceId, double supply) {
        prices.computeIfAbsent(resourceId, id -> new ResourcePrice(id, 100.0))
                .setSupply(supply);
    }

    @Override
    public void updateDemand(String resourceId, double demand) {
        prices.computeIfAbsent(resourceId, id -> new ResourcePrice(id, 100.0))
                .setDemand(demand);
    }

    @Override
    public void addSupply(String resourceId, double amount) {
        prices.computeIfAbsent(resourceId, id -> new ResourcePrice(id, 100.0))
                .addSupply(amount);
    }

    @Override
    public void addDemand(String resourceId, double amount) {
        prices.computeIfAbsent(resourceId, id -> new ResourcePrice(id, 100.0))
                .addDemand(amount);
    }

    @Override
    public void refreshAllPrices() {
        // 市场稳定机制：逐渐将供需比例向1.0靠拢，防止价格剧烈波动
        double stabilityFactor = 0.95; // 每次调整95%，留5%波动空间

        for (ResourcePrice price : prices.values()) {
            // 保存当前价格到历史记录
            priceHistory.put(price.resourceId(), price.currentPrice());

            // audit B-094: 防止 supply==0 时除零产生 NaN/Infinity 污染后续计算
            double supply = price.supply();
            double demand = price.demand();
            if (supply <= 0.0) {
                // supply 已被 setSupply 强制为 Math.max(0.001, ...)，理论不会到 0；
                // 此处兜底防范外部 setSupply(0) 绕过或并发竞争引起的退化
                if (price.currentPrice() <= 0.0) {
                    price.setCurrentPrice(price.basePrice());
                }
                continue;
            }

            // 计算供需比
            double ratio = demand / supply;

            // 如果供需比偏离1.0太远，进行市场干预
            if (ratio > 1.5) {
                // 严重短缺 - 临时增加供应模拟（通过调整供需比例）
                double adjustment = 1.0 + (ratio - 1.0) * 0.1;
                price.setSupply(supply * adjustment);
            } else if (ratio < 0.5) {
                // 严重过剩 - 临时减少供应模拟
                // 设计决策：供过于求时减少供应，配合 targetPrice = basePrice * ratio（ratio<1 拉低价格）
                // 价格走低间接造成 addSupply 减少。若需价格向上修正，改为增加买方需求
                double adjustment = 1.0 - (1.0 - ratio) * 0.1;
                price.setSupply(supply * adjustment);
            }

            // 应用稳定性因子，逐渐回归平衡
            double currentPrice = price.currentPrice();
            double targetPrice = price.basePrice() * Math.min(2.0, Math.max(0.5, ratio));
            double stablePrice = currentPrice * stabilityFactor + targetPrice * (1.0 - stabilityFactor);
            price.setCurrentPrice(stablePrice);
        }
    }

    @Override
    public double getPriceTrend(String resourceId) {
        return getPrice(resourceId)
                .map(ResourcePrice::priceChangePercentage)
                .orElse(0.0);
    }

    @Override
    public ResourcePrice.MarketState getMarketState(String resourceId) {
        return getPrice(resourceId)
                .map(ResourcePrice::marketState)
                .orElse(ResourcePrice.MarketState.BALANCED);
    }

    @Override
    public void setBasePrice(String resourceId, double basePrice) {
        ResourcePrice price = prices.get(resourceId);
        if (price == null) {
            prices.put(resourceId, new ResourcePrice(resourceId, basePrice));
            priceHistory.put(resourceId, basePrice);
        } else {
            // audit B-097: ResourcePrice.basePrice 是 final 字段不能改写，
            //   因此 setBasePrice 在已存在 ResourcePrice 时无法真正修改基础价。
            //   为让调用方期望的"基础价变更"语义生效，这里用新 basePrice
            //   重建 ResourcePrice 并保留原 supply/demand，再触发一次基于新基础价的 currentPrice 重算。
            double oldSupply = price.supply();
            double oldDemand = price.demand();
            ResourcePrice rebuilt = new ResourcePrice(resourceId, Math.max(0.0, basePrice));
            rebuilt.setSupply(oldSupply);
            rebuilt.setDemand(oldDemand);
            prices.put(resourceId, rebuilt);
            priceHistory.put(resourceId, basePrice);
        }
    }

    @Override
    public void simulateFluctuation(String resourceId, double fluctuationPercentage) {
        Optional<ResourcePrice> priceOpt = getPrice(resourceId);
        if (priceOpt.isEmpty()) {
            return;
        }

        ResourcePrice price = priceOpt.get();
        double currentPrice = price.currentPrice();
        double adjustment = currentPrice * (fluctuationPercentage / 100.0);
        double newPrice = currentPrice + adjustment;

        price.setCurrentPrice(Math.max(0.0, newPrice));
    }

    /**
     * 初始化资源价格
     * @param resourceId 资源ID
     * @param basePrice 基础价格
     * @param initialSupply 初始供应量
     * @param initialDemand 初始需求量
     */
    @Override
    public void initializePrice(String resourceId, double basePrice, double initialSupply, double initialDemand) {
        ResourcePrice price = new ResourcePrice(resourceId, Math.max(0.01, basePrice));
        price.setSupply(Math.max(0.001, initialSupply));
        price.setDemand(Math.max(0.001, initialDemand));
        price.setCurrentPrice(basePrice);
        prices.put(resourceId, price);
        priceHistory.put(resourceId, basePrice);
    }

    /**
     * 获取价格历史
     */
    public double getPreviousPrice(String resourceId) {
        return priceHistory.getOrDefault(resourceId, 0.0);
    }

    /**
     * 获取所有已注册的资源ID
     */
    public Collection<String> getRegisteredResources() {
        return new ArrayList<>(prices.keySet());
    }

    /**
     * 检查资源价格是否已初始化
     */
    public boolean isInitialized(String resourceId) {
        return prices.containsKey(resourceId);
    }
}
