# ===============================================
# STARCORE 性能优化完整指南
# ===============================================
# 本文档包含所有性能优化配置和使用方法
# ===============================================

# ===============================================
# 第一部分：缓存系统
# ===============================================

## 1.1 缓存管理器使用

```java
// 创建缓存
SmartCache<UUID, PlayerData> playerCache = CacheManager.<UUID, PlayerData>builder()
    .maximumSize(1000)                    // 最大缓存1000个
    .expireAfterWrite(30, TimeUnit.MINUTES) // 写入30分钟后过期
    .expireAfterAccess(10, TimeUnit.MINUTES) // 访问10分钟后过期
    .recordStats()                         // 记录统计信息
    .build();

// 使用缓存
PlayerData data = playerCache.get(playerId, id -> {
    // 缓存未命中时加载数据
    return loadPlayerDataFromDatabase(id);
});

// 手动设置缓存
playerCache.put(playerId, data);

// 获取统计信息
CacheStats stats = playerCache.getStats();
System.out.println("命中率: " + stats.hitRate());
System.out.println("未命中数: " + stats.missCount());
```

## 1.2 推荐缓存配置

```yaml
# 玩家数据缓存
player-data:
  maximum-size: 1000
  expire-after-write: 30m
  expire-after-access: 10m

# PvP统计缓存
pvp-stats:
  maximum-size: 500
  expire-after-write: 5m
  expire-after-access: 2m

# 经济数据缓存
economy:
  maximum-size: 2000
  expire-after-write: 60m
  expire-after-access: 30m

# 公会数据缓存
guild:
  maximum-size: 100
  expire-after-write: 120m
  expire-after-access: 60m
```

# ===============================================
# 第二部分：异步任务系统
# ===============================================

## 2.1 异步任务管理器使用

```java
// 创建异步管理器
AsyncTaskManager asyncManager = new AsyncTaskManager(8); // 8个线程

// 异步执行任务（无返回值）
asyncManager.runAsync(() -> {
    // 执行耗时操作
    savePlayerDataToDatabase(playerData);
});

// 异步执行任务（有返回值）
CompletableFuture<PlayerData> future = asyncManager.supplyAsync(() -> {
    return loadPlayerDataFromDatabase(playerId);
});

// 获取结果
future.thenAccept(data -> {
    // 在主线程处理结果
    Bukkit.getScheduler().runTask(plugin, () -> {
        applyPlayerData(player, data);
    });
});

// 延迟执行
asyncManager.runLater(() -> {
    // 5秒后执行
    sendDelayedMessage(player);
}, 5, TimeUnit.SECONDS);

// 定时重复执行
asyncManager.runRepeating(() -> {
    // 每分钟执行一次
    autoSaveAllData();
}, 0, 1, TimeUnit.MINUTES);

// 批量执行
asyncManager.runBatch(
    () -> savePlayerData(player1),
    () -> savePlayerData(player2),
    () -> savePlayerData(player3)
);
```

## 2.2 异步任务最佳实践

```java
// ✅ 正确：异步查询数据库
asyncManager.supplyAsync(() -> {
    return database.query("SELECT * FROM players WHERE id = ?", playerId);
}).thenAccept(data -> {
    // 主线程处理
    Bukkit.getScheduler().runTask(plugin, () -> {
        player.sendMessage("数据加载完成！");
    });
});

// ❌ 错误：在异步线程调用Bukkit API
asyncManager.runAsync(() -> {
    player.sendMessage("错误！"); // 不安全！
    player.teleport(location);    // 会崩溃！
});

// ✅ 正确：异步计算，主线程应用
asyncManager.supplyAsync(() -> {
    // 异步计算
    return calculateComplexData();
}).thenAccept(result -> {
    // 主线程应用
    Bukkit.getScheduler().runTask(plugin, () -> {
        applyResult(result);
    });
});
```

# ===============================================
# 第三部分：性能监控系统
# ===============================================

## 3.1 性能监控器使用

```java
// 创建监控器
PerformanceMonitor monitor = new PerformanceMonitor();

// 方法1：手动记录
long start = System.currentTimeMillis();
performOperation();
long duration = System.currentTimeMillis() - start;
monitor.recordOperation("operation_name", duration);

// 方法2：使用计时器（推荐）
try (PerformanceMonitor.Timer timer = monitor.startTimer("database_query")) {
    // 执行操作
    queryDatabase();
} // 自动记录时间

// 获取统计信息
PerformanceMetric metric = monitor.getMetric("database_query");
System.out.println("平均耗时: " + metric.getAvgTime() + "ms");
System.out.println("最小耗时: " + metric.getMinTime() + "ms");
System.out.println("最大耗时: " + metric.getMaxTime() + "ms");
System.out.println("执行次数: " + metric.getCount());

// 全局统计
GlobalStats stats = monitor.getGlobalStats();
System.out.println("总操作数: " + stats.totalOperations());
System.out.println("总耗时: " + stats.totalTime() + "ms");
System.out.println("平均耗时: " + stats.avgTime() + "ms");
```

## 3.2 监控关键操作

```java
// 监控数据库查询
try (var timer = monitor.startTimer("db_query_player")) {
    loadPlayerData(playerId);
}

// 监控决斗匹配
try (var timer = monitor.startTimer("duel_matchmaking")) {
    findDuelOpponent(player);
}

// 监控成就检查
try (var timer = monitor.startTimer("achievement_check")) {
    checkAchievements(player);
}

// 监控GUI打开
try (var timer = monitor.startTimer("gui_open")) {
    openMainMenu(player);
}
```

# ===============================================
# 第四部分：数据库连接池
# ===============================================

## 4.1 数据库连接池配置

```java
// 创建配置
DatabaseConfig config = new DatabaseConfig();
config.type = DatabaseType.MYSQL;
config.host = "localhost";
config.port = 3306;
config.database = "starcore";
config.username = "root";
config.password = "password";

// 连接池设置
config.maxPoolSize = 10;          // 最大连接数
config.minIdle = 5;               // 最小空闲连接
config.connectionTimeout = 30000; // 连接超时30秒

// 创建连接池
DatabasePool pool = new DatabasePool(config, asyncManager);
```

## 4.2 异步数据库操作

```java
// 异步查询
pool.queryAsync(
    "SELECT * FROM players WHERE uuid = ?",
    rs -> {
        if (rs.next()) {
            return new PlayerData(
                rs.getString("uuid"),
                rs.getString("name"),
                rs.getInt("kills")
            );
        }
        return null;
    },
    playerId.toString()
).thenAccept(data -> {
    // 处理结果
    if (data != null) {
        applyPlayerData(player, data);
    }
});

// 异步更新
pool.updateAsync(
    "UPDATE players SET kills = ? WHERE uuid = ?",
    kills,
    playerId.toString()
).thenAccept(rowsAffected -> {
    System.out.println("更新了 " + rowsAffected + " 行");
});

// 批量操作
Object[][] batchData = {
    {kills1, playerId1.toString()},
    {kills2, playerId2.toString()},
    {kills3, playerId3.toString()}
};

pool.batchAsync(
    "UPDATE players SET kills = ? WHERE uuid = ?",
    batchData
).thenAccept(results -> {
    System.out.println("批量更新完成");
});

// 事务操作
pool.transactionAsync(conn -> {
    // 在事务中执行多个操作
    try (PreparedStatement stmt1 = conn.prepareStatement("UPDATE ...")) {
        stmt1.executeUpdate();
    }
    try (PreparedStatement stmt2 = conn.prepareStatement("INSERT ...")) {
        stmt2.executeUpdate();
    }
    return true;
}).thenAccept(success -> {
    System.out.println("事务完成: " + success);
});
```

## 4.3 连接池监控

```java
// 获取连接池统计
PoolStats stats = pool.getStats();
System.out.println("活跃连接: " + stats.activeConnections());
System.out.println("空闲连接: " + stats.idleConnections());
System.out.println("总连接数: " + stats.totalConnections());
System.out.println("等待线程: " + stats.waitingThreads());

// 定期监控
asyncManager.runRepeating(() -> {
    PoolStats s = pool.getStats();
    if (s.waitingThreads() > 0) {
        System.out.println("警告：有线程在等待数据库连接！");
    }
}, 0, 1, TimeUnit.MINUTES);
```

# ===============================================
# 第五部分：对象池
# ===============================================

## 5.1 对象池使用

```java
// 创建对象池
ObjectPool<StringBuilder> stringBuilderPool = new ObjectPool<>(
    new ObjectPool.ObjectFactory<StringBuilder>() {
        @Override
        public StringBuilder create() {
            return new StringBuilder(256);
        }

        @Override
        public void reset(StringBuilder sb) {
            sb.setLength(0); // 重置为空
        }
    },
    100 // 最大100个对象
);

// 使用对象池
StringBuilder sb = stringBuilderPool.acquire();
try {
    sb.append("Hello ");
    sb.append("World");
    String result = sb.toString();
} finally {
    stringBuilderPool.release(sb); // 归还对象
}

// 获取统计
ObjectPool.PoolStats stats = stringBuilderPool.getStats();
System.out.println("复用率: " + stats.getReuseRate() + "%");
```

## 5.2 自定义对象池

```java
// 为自定义对象创建对象池
public class PlayerDataPool extends ObjectPool<PlayerData> {
    public PlayerDataPool(int maxSize) {
        super(new ObjectFactory<PlayerData>() {
            @Override
            public PlayerData create() {
                return new PlayerData();
            }

            @Override
            public void reset(PlayerData data) {
                data.clear();
            }
        }, maxSize);
    }
}
```

# ===============================================
# 第六部分：性能优化配置
# ===============================================

## 6.1 推荐配置（小型服务器 <50人）

```yaml
performance:
  # 线程池大小
  thread-pool-size: 4

  # 缓存配置
  cache:
    player-data-size: 100
    pvp-stats-size: 100
    economy-size: 200

  # 数据库连接池
  database:
    max-pool-size: 5
    min-idle: 2

  # 自动保存间隔（秒）
  auto-save-interval: 300
```

## 6.2 推荐配置（中型服务器 50-200人）

```yaml
performance:
  thread-pool-size: 8

  cache:
    player-data-size: 500
    pvp-stats-size: 300
    economy-size: 1000

  database:
    max-pool-size: 10
    min-idle: 5

  auto-save-interval: 180
```

## 6.3 推荐配置（大型服务器 >200人）

```yaml
performance:
  thread-pool-size: 16

  cache:
    player-data-size: 2000
    pvp-stats-size: 1000
    economy-size: 5000

  database:
    max-pool-size: 20
    min-idle: 10

  auto-save-interval: 120
```

# ===============================================
# 第七部分：性能优化技巧
# ===============================================

## 7.1 数据库优化

```sql
-- 创建索引
CREATE INDEX idx_player_uuid ON players(uuid);
CREATE INDEX idx_kills ON pvp_stats(kills);
CREATE INDEX idx_guild_id ON guild_members(guild_id);

-- 使用批量操作
-- ✅ 好
INSERT INTO players VALUES (?, ?), (?, ?), (?, ?);

-- ❌ 差
INSERT INTO players VALUES (?, ?);
INSERT INTO players VALUES (?, ?);
INSERT INTO players VALUES (?, ?);
```

## 7.2 缓存策略

```java
// ✅ 好：使用缓存
PlayerData data = cache.get(playerId, id -> {
    return database.loadPlayerData(id);
});

// ❌ 差：每次都查数据库
PlayerData data = database.loadPlayerData(playerId);

// ✅ 好：批量加载
List<UUID> playerIds = getOnlinePlayers();
Map<UUID, PlayerData> dataMap = database.batchLoadPlayerData(playerIds);
for (Map.Entry<UUID, PlayerData> entry : dataMap.entrySet()) {
    cache.put(entry.getKey(), entry.getValue());
}
```

## 7.3 内存优化

```java
// ✅ 好：使用 UUID 作为 Map key
Map<UUID, Player> players = new HashMap<>();

// ❌ 差：使用 Player 对象作为 key（会导致内存泄漏）
Map<Player, Data> data = new HashMap<>();

// ✅ 好：及时清理不用的数据
@EventHandler
public void onPlayerQuit(PlayerQuitEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    cache.remove(playerId);
}

// ✅ 好：使用对象池
StringBuilder sb = pool.acquire();
try {
    // 使用
} finally {
    pool.release(sb);
}
```

## 7.4 事件监听优化

```java
// ✅ 好：使用 EventPriority 和 ignoreCancelled
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void onPlayerKill(EntityDeathEvent event) {
    // 处理
}

// ✅ 好：避免在高频事件中做重计算
@EventHandler
public void onPlayerMove(PlayerMoveEvent event) {
    // ❌ 差：每次移动都计算
    // calculateNearbyPlayers(event.getPlayer());

    // ✅ 好：只在方块变化时计算
    if (event.getFrom().getBlock().equals(event.getTo().getBlock())) {
        return;
    }
    calculateNearbyPlayers(event.getPlayer());
}
```

# ===============================================
# 第八部分：性能监控命令
# ===============================================

## 8.1 建议添加的监控命令

```java
// /starcore performance
- 显示性能统计
- TPS、内存使用
- 线程池状态
- 缓存命中率
- 数据库连接池状态

// /starcore metrics <操作名>
- 显示特定操作的性能指标

// /starcore cache stats
- 显示所有缓存统计

// /starcore cache clear <缓存名>
- 清空指定缓存

// /starcore pool stats
- 显示对象池统计
```

# ===============================================
# 配置文件结束
# ===============================================
# 按照本指南优化，可以大幅提升插件性能！
# ===============================================
