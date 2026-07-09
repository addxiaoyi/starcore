# STARCORE 性能优化快速参考卡

## 🎯 性能监控

### 基本用法
```java
// 获取服务
PerformanceMetricsService metrics = context.serviceRegistry()
    .require(PerformanceMetricsService.class);

// 方式1：自动计时（推荐）
try (var timer = metrics.startTimer("operation.name")) {
    doWork();
}

// 方式2：手动记录
long start = System.nanoTime();
doWork();
metrics.recordOperation("operation.name", System.nanoTime() - start);

// 数据库查询
metrics.recordDatabaseQuery("nation.load", durationMillis);

// 缓存统计
metrics.recordCacheHit("nations");
metrics.recordCacheMiss("nations");
```

### 命令
```bash
/sc debug performance           # 查看性能快照
/sc debug metrics reset         # 重置统计
/sc debug metrics enable        # 启用收集
/sc debug metrics disable       # 禁用收集
```

---

## 💾 缓存管理

### 基本用法
```java
// 获取 CacheManager
CacheManager manager = context.serviceRegistry()
    .require(CacheManager.class);

// 创建缓存
Cache<UUID, Nation> cache = manager.getOrCreate("nations",
    CacheConfig.builder()
        .maxSize(5000)                              // 最大条目数
        .expireAfterAccess(Duration.ofMinutes(10))  // 访问后过期
        .expireAfterWrite(Duration.ofMinutes(30))   // 写入后过期
        .weakKeys()                                 // 弱引用键
        .softValues()                               // 软引用值
        .disableStats()                             // 禁用统计（可选）
        .build());

// 读取
Optional<Nation> nation = cache.get(uuid);

// 读取或加载
Nation nation = cache.get(uuid, this::loadFromDatabase);

// 写入
cache.put(uuid, nation);

// 失效
cache.invalidate(uuid);           // 单个
cache.invalidateAll();            // 全部
```

### 命令
```bash
/sc debug cache                 # 查看所有缓存
/sc debug cache nations         # 查看特定缓存详情
```

---

## 📊 在模块中集成

### NationModule 示例
```java
public class NationModule implements StarCoreModule {
    private Cache<UUID, Nation> nationCache;
    private PerformanceMetricsService metrics;

    @Override
    public void enable(StarCoreContext context) {
        // 初始化服务
        this.metrics = context.serviceRegistry()
            .require(PerformanceMetricsService.class);
        
        CacheManager cacheManager = context.serviceRegistry()
            .require(CacheManager.class);
        
        this.nationCache = cacheManager.getOrCreate("nations",
            CacheConfig.builder()
                .maxSize(5000)
                .expireAfterAccess(Duration.ofMinutes(10))
                .build());
    }

    public Nation createNation(String name, UUID founder) {
        try (var timer = metrics.startTimer("nation.create")) {
            Nation nation = new Nation(UUID.randomUUID(), name, founder);
            saveToDatabase(nation);
            nationCache.put(nation.id(), nation);
            return nation;
        }
    }

    public Optional<Nation> getNation(UUID id) {
        // 先查缓存
        Optional<Nation> cached = nationCache.get(id);
        if (cached.isPresent()) {
            metrics.recordCacheHit("nations");
            return cached;
        }
        
        // 缓存未命中，从数据库加载
        metrics.recordCacheMiss("nations");
        try (var timer = metrics.startTimer("db.nation.load")) {
            Nation nation = loadFromDatabase(id);
            if (nation != null) {
                nationCache.put(id, nation);
            }
            return Optional.ofNullable(nation);
        }
    }
}
```

---

## ⚡ 性能优化检查清单

### ✅ 必做项
- [ ] 为所有数据库操作添加性能监控
- [ ] 缓存频繁查询的数据（玩家、国家、领地）
- [ ] 监听器添加 `ignoreCancelled = true`
- [ ] 高频事件避免重计算
- [ ] 使用 UUID 作为 Map 键（不用 Player 对象）

### ✅ 推荐项
- [ ] 异步处理文件 I/O 和网络操作
- [ ] 定期清理过期缓存条目
- [ ] 使用连接池管理数据库连接
- [ ] 限制实体、粒子生成数量
- [ ] 批量操作使用事务

### ❌ 避免项
- [ ] 在主线程执行数据库查询
- [ ] 在 PlayerMoveEvent 中进行复杂计算
- [ ] 缓存永不过期且无大小限制
- [ ] 静态持有 Player 对象引用
- [ ] 忘记注销监听器和任务

---

## 🎯 性能目标

| 指标 | 目标值 | 检查方法 |
|------|--------|----------|
| TPS | > 19.5 | `/tps` |
| 缓存命中率 | > 90% | `/sc debug cache` |
| 数据库查询 | < 10ms | `/sc debug performance` |
| 事件处理 | < 5ms | `/sc debug performance` |
| 内存占用 | < 200MB (200玩家) | `/spark heapsummary` |

---

## 🔍 性能问题排查

### 问题：TPS 下降
```bash
# 1. 查看性能快照
/sc debug performance

# 2. 查找最慢的操作
# 查看输出中 avg/max 最大的操作

# 3. 使用 Spark profiler
/spark profiler start
# 等待30秒
/spark profiler stop
```

### 问题：内存占用过高
```bash
# 1. 查看缓存大小
/sc debug cache

# 2. 检查缓存命中率
# 命中率低说明缓存配置不当

# 3. 使用 Spark 查看堆内存
/spark heapsummary
```

### 问题：数据库慢
```bash
# 1. 查看数据库查询统计
/sc debug performance

# 2. 查找 db.* 开头的操作
# 平均耗时 > 50ms 需要优化

# 3. 检查连接池配置
# config.yml -> database.hikari.*
```

---

## 📖 命名约定

### 性能监控操作名
```
模块.动作                    例如: nation.create
event.事件名                 例如: event.player.join
db.表名.操作                 例如: db.nation.load
cache.缓存名.操作            例如: cache.nations.evict
```

### 缓存名称
```
复数名词                     例如: nations, territories, players
模块前缀                     例如: policy.active, war.ongoing
```

---

## 🚀 快速开始

**1. 在你的模块中添加性能监控：**
```java
PerformanceMetricsService metrics = context.serviceRegistry()
    .require(PerformanceMetricsService.class);

try (var timer = metrics.startTimer("mymodule.operation")) {
    // 你的代码
}
```

**2. 添加缓存：**
```java
CacheManager manager = context.serviceRegistry()
    .require(CacheManager.class);
Cache<UUID, Data> cache = manager.getOrCreate("mydata",
    CacheConfig.defaults());
```

**3. 检查结果：**
```bash
/sc debug performance
/sc debug cache
```

---

## 📚 更多信息

- 完整文档：`docs/PERFORMANCE_ENHANCEMENT_SUMMARY.md`
- 架构设计：`docs/ARCHITECTURE_REVIEW_AND_ENHANCEMENT_PLAN.md`
- API 文档：JavaDoc 注释

**遇到问题？** 使用 `/sc debug` 命令系列获取实时诊断信息。
