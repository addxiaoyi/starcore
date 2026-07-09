# 动态物价系统 - 详细设计方案

## 概述
服务器经济有动态物价系统，根据全服交易量自动调整。稀有资源供不应求时涨价，过剩时跌价。玩家可观察市场趋势进行投机获利。

---

## 1. 数据库设计

### 市场数据表: market_prices
```sql
CREATE TABLE market_prices (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_id VARCHAR(64) NOT NULL,                 -- 物品ID (material或custom)
    item_type ENUM('MATERIAL', 'CRAFTED', 'RARE', 'RESOURCE', 'CONSUMABLE') NOT NULL,
    
    -- 价格数据
    current_price BIGINT NOT NULL,                -- 当前价格
    base_price BIGINT NOT NULL,                   -- 基础价格
    min_price BIGINT NOT NULL,                    -- 最低价
    max_price BIGINT NOT NULL,                    -- 最高价
    
    -- 波动数据
    volatility DECIMAL(5,4) DEFAULT 0.1000,       -- 波动系数 0-1
    price_change_24h INT DEFAULT 0,               -- 24小时价格变化
    price_change_7d INT DEFAULT 0,               -- 7天价格变化
    
    -- 市场数据
    total_volume_24h BIGINT DEFAULT 0,            -- 24小时成交量
    total_volume_7d BIGINT DEFAULT 0,             -- 7天成交量
    transaction_count_24h INT DEFAULT 0,          -- 24小时交易次数
    unique_buyers_24h INT DEFAULT 0,              -- 24小时买家数
    unique_sellers_24h INT DEFAULT 0,             -- 24小时卖家数
    
    -- 市场情绪
    market_sentiment DECIMAL(3,2) DEFAULT 0.50,   -- 市场情绪 0-1
    demand_index DECIMAL(5,2) DEFAULT 1.00,       -- 需求指数
    supply_index DECIMAL(5,2) DEFAULT 1.00,       -- 供应指数
    
    -- 时间戳
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_price_change TIMESTAMP,
    
    UNIQUE KEY uk_item (item_id),
    INDEX idx_type (item_type),
    INDEX idx_change (price_change_24h),
    INDEX idx_volume (total_volume_24h DESC)
);
```

### 交易记录表: market_transactions
```sql
CREATE TABLE market_transactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_id CHAR(36) NOT NULL UNIQUE,
    
    -- 交易信息
    item_id VARCHAR(64) NOT NULL,
    price_per_unit BIGINT NOT NULL,
    quantity INT NOT NULL,
    total_amount BIGINT NOT NULL,
    
    -- 交易双方
    buyer_uuid CHAR(36),
    seller_uuid CHAR(36),
    buyer_nation_id CHAR(36),
    seller_nation_id CHAR(36),
    
    -- 交易类型
    transaction_type ENUM('SALE', 'AUCTION', 'TRADE', 'CONTRACT', 'SPECULATE') NOT NULL,
    
    -- 位置
    location_x INT,
    location_y INT,
    location_z INT,
    world VARCHAR(64),
    
    -- 时间戳
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_item (item_id),
    INDEX idx_time (timestamp DESC),
    INDEX idx_buyer (buyer_uuid),
    INDEX idx_seller (seller_uuid),
    INDEX idx_nation (buyer_nation_id, seller_nation_id)
);
```

### 期货市场表: futures_contracts
```sql
CREATE TABLE futures_contracts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    contract_id CHAR(36) NOT NULL UNIQUE,
    
    -- 合约信息
    item_id VARCHAR(64) NOT NULL,
    contract_type ENUM('LONG', 'SHORT') NOT NULL, -- 做多/做空
    position_price BIGINT NOT NULL,               -- 建仓价格
    quantity INT NOT NULL,                        -- 合约数量
    
    -- 合约期限
    open_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    close_time TIMESTAMP NOT NULL,                -- 到期时间
    settlement_price BIGINT,                      -- 结算价格
    
    -- 状态
    status ENUM('OPEN', 'CLOSED', 'SETTLED', 'CANCELLED') DEFAULT 'OPEN',
    
    -- 交易者
    player_uuid CHAR(36) NOT NULL,
    nation_id CHAR(36),
    
    -- 盈亏
    profit_loss BIGINT DEFAULT 0,
    margin_deposit BIGINT NOT NULL,               -- 保证金
    
    INDEX idx_player (player_uuid),
    INDEX idx_item (item_id),
    INDEX idx_status (status),
    INDEX idx_close_time (close_time)
);
```

### 市场预测表: market_predictions
```sql
CREATE TABLE market_predictions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    prediction_id CHAR(36) NOT NULL UNIQUE,
    
    player_uuid CHAR(36) NOT NULL,
    item_id VARCHAR(64) NOT NULL,
    predicted_price BIGINT NOT NULL,              -- 预测价格
    prediction_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    target_time TIMESTAMP NOT NULL,               -- 预测目标时间
    target_price BIGINT NOT NULL,                 -- 届时实际价格
    
    -- 结果
    is_correct BOOLEAN,
    accuracy DECIMAL(5,2),                        -- 准确度百分比
    reward BIGINT DEFAULT 0,                      -- 奖励
    
    INDEX idx_player (player_uuid),
    INDEX idx_item (item_id),
    INDEX idx_time (prediction_time DESC)
);
```

---

## 2. 核心类设计

### 接口: MarketService
```java
package dev.starcore.starcore.module.economy.market;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface MarketService {
    
    // ===== 价格查询 =====
    /**
     * 获取物品当前价格
     */
    MarketPrice getPrice(String itemId);
    
    /**
     * 获取价格趋势
     */
    PriceTrend getTrend(String itemId, TrendPeriod period);
    
    /**
     * 获取市场价格列表
     */
    List<MarketPrice> getAllPrices();
    
    /**
     * 获取涨幅榜/跌幅榜
     */
    List<MarketPrice> getTopMovers(int limit, boolean ascending);
    
    // ===== 交易 =====
    /**
     * 执行交易
     */
    TransactionResult executeTrade(Player buyer, Player seller, 
                                   String itemId, int quantity, long unitPrice);
    
    /**
     * 记录市场交易
     */
    void recordTransaction(String itemId, int quantity, long price, 
                          UUID buyerId, UUID sellerId, TransactionType type);
    
    /**
     * 获取玩家交易历史
     */
    List<Transaction> getPlayerTransactions(UUID playerId, int limit);
    
    // ===== 价格更新 =====
    /**
     * 更新市场价格
     * 由定时任务调用
     */
    void updatePrices();
    
    /**
     * 手动调整价格(管理员)
     */
    void setPrice(String itemId, long price, String reason);
    
    // ===== 期货市场 =====
    /**
     * 开仓(做多/做空)
     */
    FuturesContract openPosition(UUID playerId, String itemId, 
                                 ContractType type, int quantity, long price);
    
    /**
     * 平仓
     */
    FuturesSettlement closePosition(UUID contractId, long currentPrice);
    
    /**
     * 获取玩家持仓
     */
    List<FuturesContract> getPlayerPositions(UUID playerId);
    
    /**
     * 结算到期合约
     */
    void settleExpiredContracts();
    
    // ===== 市场分析 =====
    /**
     * 获取市场报告
     */
    MarketReport generateReport(String itemId, int days);
    
    /**
     * 预测价格走势
     */
    PriceForecast forecast(String itemId, int hoursAhead);
    
    /**
     * 获取经济指标
     */
    EconomicIndicators getIndicators();
}
```

### 模型类
```java
// MarketPrice.java
public record MarketPrice(
    String itemId,
    ItemType itemType,
    long currentPrice,
    long basePrice,
    long minPrice,
    long maxPrice,
    double volatility,
    int priceChange24h,
    int priceChange7d,
    long volume24h,
    long volume7d,
    double demandIndex,
    double supplyIndex,
    double sentiment,
    long lastUpdated
) {
    public double getPriceRatio() {
        return (double) currentPrice / basePrice;
    }
    
    public PriceDirection getDirection() {
        if (priceChange24h > 0) return PriceDirection.UP;
        if (priceChange24h < 0) return PriceDirection.DOWN;
        return PriceDirection.STABLE;
    }
}

// PriceTrend.java
public record PriceTrend(
    String itemId,
    TrendPeriod period,
    List<TrendPoint> points,
    TrendDirection direction,
    double momentum,          // 动量
    double volatility
) {}

// Transaction.java
public record Transaction(
    UUID transactionId,
    String itemId,
    long pricePerUnit,
    int quantity,
    long totalAmount,
    UUID buyerId,
    UUID sellerId,
    TransactionType type,
    long timestamp
) {}

// MarketReport.java
public record MarketReport(
    String itemId,
    int periodDays,
    double priceChange,
    double volumeChange,
    double avgPrice,
    double peakPrice,
    double troughPrice,
    int totalTransactions,
    List<String> majorEvents,
    String recommendation
) {}
```

### 核心实现: MarketServiceImpl
```java
public class MarketServiceImpl implements MarketService, Runnable {
    
    private final JavaPlugin plugin;
    private final DatabaseService databaseService;
    private final EconomyService economyService;
    private final StarCoreScheduler scheduler;
    
    // 配置
    private MarketConfig config;
    
    // 内存缓存
    private final Map<String, MarketPrice> priceCache = new ConcurrentHashMap<>();
    private final Map<String, Deque<TrendPoint>> trendHistory = new ConcurrentHashMap<>();
    
    // 价格更新间隔(分钟)
    private static final int UPDATE_INTERVAL = 5;
    
    @Override
    public void onEnable() {
        // 启动价格更新定时任务
        scheduler.runTaskTimer(
            this, 
            20L * 60 * UPDATE_INTERVAL,  // 初始延迟1分钟
            20L * 60 * UPDATE_INTERVAL   // 之后每5分钟
        );
        
        // 加载价格数据到缓存
        loadPricesToCache();
    }
    
    @Override
    public void run() {
        // 价格更新任务
        updatePrices();
        settleExpiredContracts();
    }
    
    @Override
    public void updatePrices() {
        List<String> allItems = getAllTradeableItems();
        
        for (String itemId : allItems) {
            MarketPrice current = getPrice(itemId);
            if (current == null) continue;
            
            // 计算新的供需指数
            double newDemandIndex = calculateDemandIndex(itemId);
            double newSupplyIndex = calculateSupplyIndex(itemId);
            
            // 计算新价格
            long newPrice = calculateNewPrice(current, newDemandIndex, newSupplyIndex);
            
            // 应用波动
            newPrice = applyVolatility(newPrice, current.volatility());
            
            // 确保价格在范围内
            newPrice = Math.max(current.minPrice(), Math.min(current.maxPrice(), newPrice));
            
            // 更新缓存和数据库
            MarketPrice updated = new MarketPrice(
                itemId,
                current.itemType(),
                newPrice,
                current.basePrice(),
                current.minPrice(),
                current.maxPrice(),
                current.volatility(),
                (int)((newPrice - current.currentPrice()) * 100 / current.currentPrice()),
                current.priceChange7d(), // 简化计算
                current.volume24h(),
                current.volume7d(),
                newDemandIndex,
                newSupplyIndex,
                calculateSentiment(newPrice, current.basePrice()),
                System.currentTimeMillis()
            );
            
            priceCache.put(itemId, updated);
            savePrice(updated);
            
            // 更新趋势历史
            addTrendPoint(itemId, new TrendPoint(newPrice, System.currentTimeMillis()));
        }
    }
    
    private long calculateNewPrice(MarketPrice current, double demandIndex, double supplyIndex) {
        // 供需比率
        double supplyDemandRatio = demandIndex / supplyIndex;
        
        // 价格弹性
        double elasticity = current.volatility() * 2 + 0.5; // 0.5-2.5
        
        // 新价格 = 当前价格 * (需求/供应)^弹性
        double priceChangeFactor = Math.pow(supplyDemandRatio, elasticity);
        
        // 加入时间衰减因素(防止价格永远上涨/下跌)
        double timeDecay = 0.98; // 每次更新向基础价格回归2%
        double adjustedFactor = priceChangeFactor * timeDecay + (1 - timeDecay);
        
        long newPrice = (long)(current.currentPrice() * adjustedFactor);
        
        return newPrice;
    }
    
    private long applyVolatility(long price, double volatility) {
        // 根据波动系数添加随机扰动
        double maxFluctuation = volatility * 0.1; // 最大10%波动
        double randomFactor = 1 + (Math.random() * 2 - 1) * maxFluctuation;
        return (long)(price * randomFactor);
    }
    
    private double calculateDemandIndex(String itemId) {
        // 基于最近成交量计算需求指数
        long volume24h = getVolume24h(itemId);
        long avgVolume = getAverageVolume(itemId, 7); // 7天平均
        
        if (avgVolume == 0) return 1.0;
        
        // 成交量增加表示需求增加
        double volumeFactor = (double) volume24h / avgVolume;
        
        // 考虑价格变化趋势
        MarketPrice current = getPrice(itemId);
        double priceTrendFactor = current.priceChange24h() > 0 ? 1.1 : 0.9;
        
        return volumeFactor * priceTrendFactor;
    }
    
    private double calculateSupplyIndex(String itemId) {
        // 基于在线玩家数量和新产出计算供应指数
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        long productionRate = getProductionRate(itemId, onlinePlayers);
        
        // 基础供应系数
        double baseSupply = 1.0 + (productionRate / 1000.0);
        
        // 库存因素
        double inventoryFactor = getInventoryPressure(itemId);
        
        return baseSupply * inventoryFactor;
    }
    
    private double calculateSentiment(long currentPrice, long basePrice) {
        // 市场情绪基于当前价格与基础价格的偏离
        double ratio = (double) currentPrice / basePrice;
        
        if (ratio > 1.5) return 0.9;  // 价格过高，情绪过热
        if (ratio > 1.2) return 0.7;  // 价格偏高
        if (ratio < 0.5) return 0.1;  // 价格过低，情绪恐慌
        if (ratio < 0.8) return 0.3;  // 价格偏低
        
        return 0.5; // 价格正常
    }
    
    @Override
    public TransactionResult executeTrade(Player buyer, Player seller,
                                          String itemId, int quantity, long unitPrice) {
        long totalCost = unitPrice * quantity;
        
        // 检查买家余额
        if (!economyService.withdraw(buyer, totalCost)) {
            return new TransactionResult(false, "余额不足", 0);
        }
        
        // 检查卖家物品
        ItemStack item = parseItem(itemId, quantity);
        if (!seller.getInventory().containsAtLeast(item)) {
            economyService.deposit(buyer, totalCost); // 退款
            return new TransactionResult(false, "卖家物品不足", 0);
        }
        
        // 执行交易
        seller.getInventory().removeItem(item);
        buyer.getInventory().addItem(item);
        economyService.deposit(seller, totalCost);
        
        // 记录交易
        recordTransaction(itemId, quantity, unitPrice, 
                        buyer.getUniqueId(), seller.getUniqueId(), TransactionType.TRADE);
        
        return new TransactionResult(true, "交易成功", totalCost);
    }
    
    @Override
    public void recordTransaction(String itemId, int quantity, long price,
                                  UUID buyerId, UUID sellerId, TransactionType type) {
        Transaction transaction = new Transaction(
            UUID.randomUUID(),
            itemId,
            price,
            quantity,
            price * quantity,
            buyerId,
            sellerId,
            type,
            System.currentTimeMillis()
        );
        
        saveTransaction(transaction);
        
        // 更新缓存的成交量
        MarketPrice cached = priceCache.get(itemId);
        if (cached != null) {
            priceCache.put(itemId, new MarketPrice(
                cached.itemId(),
                cached.itemType(),
                cached.currentPrice(),
                cached.basePrice(),
                cached.minPrice(),
                cached.maxPrice(),
                cached.volatility(),
                cached.priceChange24h(),
                cached.priceChange7d(),
                cached.volume24h() + quantity,
                cached.volume7d() + quantity,
                cached.demandIndex(),
                cached.supplyIndex(),
                cached.sentiment(),
                System.currentTimeMillis()
            ));
        }
    }
    
    // ===== 期货市场 =====
    @Override
    public FuturesContract openPosition(UUID playerId, String itemId,
                                        ContractType type, int quantity, long price) {
        // 计算保证金(合约价值的10%)
        long contractValue = price * quantity;
        long margin = contractValue / 10;
        
        // 检查玩家余额
        if (!economyService.has(playerId, margin)) {
            throw new IllegalArgumentException("保证金不足");
        }
        
        // 冻结保证金
        economyService.withdraw(playerId, margin);
        
        // 创建合约
        FuturesContract contract = new FuturesContract(
            UUID.randomUUID(),
            itemId,
            type,
            price,
            quantity,
            System.currentTimeMillis(),
            System.currentTimeMillis() + config.futuresContractDuration() * 3600000L,
            null,
            FuturesStatus.OPEN,
            playerId,
            null,
            0,
            margin
        );
        
        saveContract(contract);
        return contract;
    }
    
    @Override
    public FuturesSettlement closePosition(UUID contractId, long currentPrice) {
        FuturesContract contract = getContract(contractId)
            .orElseThrow(() -> new IllegalArgumentException("合约不存在"));
        
        if (contract.status() != FuturesStatus.OPEN) {
            throw new IllegalStateException("合约已平仓");
        }
        
        // 计算盈亏
        long priceDiff = currentPrice - contract.positionPrice();
        long profitLoss;
        
        if (contract.contractType() == ContractType.LONG) {
            // 做多: 价格涨赚钱
            profitLoss = priceDiff * contract.quantity();
        } else {
            // 做空: 价格跌赚钱
            profitLoss = -priceDiff * contract.quantity();
        }
        
        // 返还保证金+盈亏
        long totalReturn = contract.marginDeposit() + profitLoss;
        if (totalReturn > 0) {
            economyService.deposit(contract.playerUuid(), totalReturn);
        }
        
        // 更新合约状态
        FuturesContract settled = new FuturesContract(
            contract.contractId(),
            contract.itemId(),
            contract.contractType(),
            contract.positionPrice(),
            contract.quantity(),
            contract.openTime(),
            contract.closeTime(),
            currentPrice,
            FuturesStatus.SETTLED,
            contract.playerUuid(),
            contract.nationId(),
            profitLoss,
            contract.marginDeposit()
        );
        saveContract(settled);
        
        // 发布事件
        eventBus.publish(new FuturesPositionClosedEvent(settled, profitLoss));
        
        return new FuturesSettlement(settled, profitLoss, profitLoss > 0);
    }
    
    // ===== 价格预测 =====
    @Override
    public PriceForecast forecast(String itemId, int hoursAhead) {
        List<TrendPoint> history = trendHistory.get(itemId);
        if (history == null || history.size() < 10) {
            return new PriceForecast(itemId, hoursAhead, 0, 0, "数据不足");
        }
        
        // 简单线性回归预测
        double[] prices = history.stream()
            .mapToDouble(TrendPoint::price)
            .toArray();
        
        // 计算趋势
        double trend = calculateLinearTrend(prices);
        
        // 预测未来价格
        double currentPrice = prices[prices.length - 1];
        double forecastPrice = currentPrice + (trend * hoursAhead / 60.0 * UPDATE_INTERVAL);
        
        // 计算置信度
        double confidence = calculateConfidence(history);
        
        String recommendation = forecastPrice > currentPrice * 1.1 ? "看涨" :
                               forecastPrice < currentPrice * 0.9 ? "看跌" : "观望";
        
        return new PriceForecast(
            itemId,
            hoursAhead,
            (long) forecastPrice,
            confidence,
            recommendation
        );
    }
    
    private double calculateLinearTrend(double[] prices) {
        int n = prices.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += prices[i];
            sumXY += i * prices[i];
            sumX2 += i * i;
        }
        
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        return slope;
    }
    
    private double calculateConfidence(List<TrendPoint> history) {
        // 基于历史数据的连贯性计算置信度
        double variance = calculateVariance(
            history.stream().mapToDouble(TrendPoint::price).toArray()
        );
        
        // 方差越小，置信度越高
        double avgPrice = history.stream()
            .mapToDouble(TrendPoint::price)
            .average().orElse(0);
        
        if (avgPrice == 0) return 0;
        
        double cv = Math.sqrt(variance) / avgPrice; // 变异系数
        return Math.max(0, Math.min(100, 100 - cv * 100));
    }
}
```

---

## 3. 命令设计

### /market 命令
```
/market price <物品>        - 查看物品当前价格
/market trend <物品> [1d|7d|30d] - 查看价格趋势
/market top                 - 查看涨幅榜
/market bottom              - 查看跌幅榜
/market volume <物品>       - 查看成交量
/market history <物品>      - 查看价格历史
/market forecast <物品> [小时] - 价格预测
/market report <物品>       - 生成市场报告

# 交易命令
/market buy <物品> <数量> <价格> - 发起买入订单
/market sell <物品> <数量> <价格> - 发起卖出订单
/market orders              - 查看我的挂单
/market cancel <订单ID>     - 取消订单

# 期货命令
/futures list               - 查看期货合约列表
/futures positions          - 查看我的持仓
/futures open <物品> <多|空> <数量> <价格> - 开仓
/futures close <合约ID>     - 平仓
/futures settle             - 手动结算到期合约

# 预测命令
/predict <物品> <预测价格> <时间> - 价格预测(押注)
```

### MarketCommand.java
```java
public class MarketCommand implements CommandExecutor {
    
    private final MarketService marketService;
    private final ItemParser itemParser;
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        String sub = args[0].toLowerCase();
        
        return switch (sub) {
            case "price" -> handlePrice(sender, args);
            case "trend" -> handleTrend(sender, args);
            case "top", "bottom" -> handleMovers(sender, sub);
            case "volume" -> handleVolume(sender, args);
            case "forecast" -> handleForecast(sender, args);
            case "report" -> handleReport(sender, args);
            case "buy", "sell" -> handleOrder(sender, sub, args);
            case "orders" -> handleOrders(sender);
            default -> { showHelp(sender); yield true; }
        };
    }
    
    private void handlePrice(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /market price <物品>", NamedTextColor.YELLOW));
            return;
        }
        
        String itemId = itemParser.parse(args[1]);
        MarketPrice price = marketService.getPrice(itemId);
        
        if (price == null) {
            sender.sendMessage(Component.text("未找到物品: " + args[1], NamedTextColor.RED));
            return;
        }
        
        sender.sendMessage(Component.text("===== 市场行情 =====", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("物品: " + itemId, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("当前价格: " + formatPrice(price.currentPrice()), 
            price.getDirection() == PriceDirection.UP ? NamedTextColor.GREEN :
            price.getDirection() == PriceDirection.DOWN ? NamedTextColor.RED : NamedTextColor.WHITE));
        sender.sendMessage(Component.text("基础价格: " + formatPrice(price.basePrice()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("24h涨跌: " + formatChange(price.priceChange24h()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("7d涨跌: " + formatChange(price.priceChange7d()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("成交量(24h): " + price.volume24h(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("需求指数: " + String.format("%.2f", price.demandIndex()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("供应指数: " + String.format("%.2f", price.supplyIndex()), NamedTextColor.GRAY));
    }
    
    private void handleTrend(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /market trend <物品> [1d|7d|30d]", NamedTextColor.YELLOW));
            return;
        }
        
        String itemId = itemParser.parse(args[1]);
        TrendPeriod period = args.length > 2 ? TrendPeriod.valueOf(args[2].toUpperCase()) : TrendPeriod.DAY;
        
        PriceTrend trend = marketService.getTrend(itemId, period);
        
        sender.sendMessage(Component.text("===== 价格趋势 =====", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("物品: " + itemId, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("趋势方向: " + trend.direction().name(), 
            trend.direction() == TrendDirection.UP ? NamedTextColor.GREEN :
            trend.direction() == TrendDirection.DOWN ? NamedTextColor.RED : NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("动量: " + String.format("%.2f", trend.momentum()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("波动性: " + String.format("%.2f", trend.volatility()), NamedTextColor.GRAY));
        
        // 显示图表
        String chart = generateAsciiChart(trend.points(), 20);
        sender.sendMessage(Component.text(chart, NamedTextColor.AQUA));
    }
    
    private String generateAsciiChart(List<TrendPoint> points, int width) {
        if (points.isEmpty()) return "数据不足";
        
        double min = points.stream().mapToDouble(TrendPoint::price).min().orElse(0);
        double max = points.stream().mapToDouble(TrendPoint::price).max().orElse(100);
        double range = max - min;
        
        StringBuilder chart = new StringBuilder("|");
        for (TrendPoint point : points) {
            int barLength = range > 0 ? (int)((point.price() - min) / range * (width - 2)) : width / 2;
            chart.append("█".repeat(barLength));
            chart.append(" ");
        }
        chart.append("|");
        
        return chart.toString();
    }
}
```

---

## 4. 配置文件

### config/market.yml
```yaml
# 市场系统配置

# 价格更新
price-update:
  # 更新间隔(分钟)
  interval: 5
  # 是否启用动态价格
  enabled: true
  # 价格向基础价格回归速度
  regression-speed: 0.02

# 价格限制
price-limits:
  # 最低价格(基础价格的百分比)
  min-ratio: 0.1
  # 最高价格(基础价格的倍数)
  max-ratio: 10.0
  # 每日最大波动幅度
  daily-fluctuation-limit: 0.5

# 基础价格配置
base-prices:
  DIAMOND: 1000
  GOLD_INGOT: 100
  IRON_INGOT: 50
  COAL: 20
  EMERALD: 800
  NETHERITE_INGOT: 10000

# 波动系数(0-1)
volatility:
  DIAMOND: 0.15
  GOLD_INGOT: 0.10
  IRON_INGOT: 0.05
  COAL: 0.08
  EMERALD: 0.20
  NETHERITE_INGOT: 0.25

# 期货市场
futures:
  # 是否启用
  enabled: true
  # 合约时长(小时)
  contract-duration: 24
  # 保证金比例
  margin-ratio: 0.1
  # 最大持仓数量
  max-positions: 10
  # 最小合约价值
  min-contract-value: 10000

# 预测系统
prediction:
  # 是否启用
  enabled: true
  # 预测正确奖励
  reward-on-correct: 1000
  # 预测失败惩罚
  penalty-on-wrong: 500
  # 最长预测时间(小时)
  max-prediction-hours: 72

# 事件影响
events:
  # 战争对资源价格影响
  war-resource-multiplier: 1.5
  # 节日活动加成
  festival-boost: 1.2
  # 服务器活动期间
  server-event-boost: 1.3

# 市场公告
announcements:
  # 大幅波动时广播
  broadcast-on-major-move: true
  # 波动阈值(百分比)
  major-move-threshold: 20
```

---

## 5. 事件系统

```java
// PriceChangedEvent - 价格变动时触发
public record PriceChangedEvent(
    String itemId,
    long oldPrice,
    long newPrice,
    double changePercent,
    PriceDirection direction
) {}

// MarketTrendShiftedEvent - 市场趋势转变时触发
public record MarketTrendShiftedEvent(
    String itemId,
    TrendDirection oldTrend,
    TrendDirection newTrend,
    double momentumChange
) {}

// FuturesPositionClosedEvent - 期货平仓时触发
public record FuturesPositionClosedEvent(
    FuturesContract contract,
    long profitLoss
) {}

// MarketAnomalyEvent - 市场异常时触发
public record MarketAnomalyEvent(
    String itemId,
    String anomalyType,    // "SPIKE", "CRASH", "MANIPULATION"
    long price,
    double deviationFromNormal
) {}
```

### 监听器
```java
public class MarketListener implements Listener {
    
    @EventHandler
    public void onPriceChange(MarketChangedEvent event) {
        MarketPrice price = event.getPrice();
        
        // 大幅波动公告
        if (getConfig().announcements().broadcastOnMajorMove() &&
            Math.abs(price.priceChange24h()) >= getConfig().announcements().majorMoveThreshold()) {
            
            String direction = price.priceChange24h() > 0 ? "暴涨" : "暴跌";
            String message = String.format(
                "[市场] %s %s %d%%！当前价格: %d",
                price.itemId(),
                direction,
                Math.abs(price.priceChange24h()),
                price.currentPrice()
            );
            Bukkit.broadcast(Component.text(message, NamedTextColor.YELLOW));
        }
    }
    
    @EventHandler
    public void onWarStart(WarStartedEvent event) {
        // 战争开始，资源价格上涨
        List<String> warResources = List.of("DIAMOND", "IRON_INGOT", "GOLD_INGOT");
        for (String item : warResources) {
            marketService.setPrice(item, 
                (long)(marketService.getPrice(item).currentPrice() * 
                       getConfig().events().warResourceMultiplier()),
                "战争爆发"
            );
        }
    }
    
    @EventHandler
    public void onFuturesClose(FuturesPositionClosedEvent event) {
        if (event.profitLoss() > 0) {
            Player player = Bukkit.getPlayer(event.contract().playerUuid());
            if (player != null) {
                player.sendMessage(Component.text()
                    .append(Component.text("[期货] ", NamedTextColor.GREEN))
                    .append(Component.text("平仓盈利: +" + event.profitLoss(), NamedTextColor.GOLD))
                );
            }
        }
    }
}
```

---

## 6. 测试用例

```java
class MarketServiceTest {
    
    @Test
    void testPriceUpdate_IncreasingDemand() {
        // Given: 需求增加
        MarketPrice current = new MarketPrice(
            "DIAMOND", ItemType.RESOURCE, 1000, 1000, 100, 10000,
            0.15, 0, 0, 100, 1000, 1.5, 1.0, 0.5, System.currentTimeMillis()
        );
        when(getVolume24h("DIAMOND")).thenReturn(200L); // 需求翻倍
        
        // When
        marketService.updatePrices();
        
        // Then
        MarketPrice updated = marketService.getPrice("DIAMOND");
        assertTrue(updated.currentPrice() > current.currentPrice());
    }
    
    @Test
    void testFuturesOpenPosition() {
        // Given
        UUID playerId = UUID.randomUUID();
        when(economyService.has(eq(playerId), anyLong())).thenReturn(true);
        
        // When
        FuturesContract contract = marketService.openPosition(
            playerId, "DIAMOND", ContractType.LONG, 10, 1000
        );
        
        // Then
        assertNotNull(contract);
        assertEquals(ContractType.LONG, contract.contractType());
        assertEquals(10000, contract.marginDeposit()); // 1000*10*0.1
    }
    
    @Test
    void testFuturesSettlement_Profit() {
        // Given: 做多合约，价格上涨
        UUID playerId = UUID.randomUUID();
        FuturesContract contract = createTestContract(playerId, ContractType.LONG, 1000, 10);
        
        // When: 价格涨到1200
        FuturesSettlement result = marketService.closePosition(
            contract.contractId(), 1200
        );
        
        // Then: 盈利2000 (200*10)
        assertTrue(result.isProfit());
        assertEquals(2000, result.profitLoss());
    }
    
    @Test
    void testFuturesSettlement_Loss() {
        // Given: 做空合约，价格上涨
        UUID playerId = UUID.randomUUID();
        FuturesContract contract = createTestContract(playerId, ContractType.SHORT, 1000, 10);
        
        // When: 价格涨到1200
        FuturesSettlement result = marketService.closePosition(
            contract.contractId(), 1200
        );
        
        // Then: 亏损2000
        assertFalse(result.isProfit());
        assertEquals(-2000, result.profitLoss());
    }
}
```

---

## 7. 实施计划

### Phase 1: 基础市场 (2天)
- 数据库表
- 价格查询和更新
- 基本交易记录

### Phase 2: 动态价格引擎 (2天)
- 价格计算算法
- 供需指数计算
- 波动控制

### Phase 3: 期货市场 (2天)
- 合约系统
- 开仓平仓
- 结算逻辑

### Phase 4: 预测系统 (1天)
- 价格预测
- 排行榜
- 奖励惩罚

### 总工时: 约 7-8 人天