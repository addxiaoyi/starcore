# STARCORE 高性能集成方案

**目标：** 打造支持10,000+ NPC的高性能系统  
**兼容性：** Paper 1.21+ / Folia Ready  
**时间：** 2026-06-15  
**状态：** 🚀 高性能架构

---

## 🎯 核心目标

### 性能要求

- ✅ 支持 10,000+ NPC 不卡
- ✅ TPS 保持 19.8+
- ✅ 严格异步化
- ✅ Folia Ready（未来兼容）
- ✅ 线程安全设计

---

## 🤖 NPC 系统集成

### 推荐方案：FancyNpcs（最佳性能）

**选择理由：**
- ✅ 纯 Packet 实现
- ✅ 轻量级，优化极好
- ✅ Paper/Folia 专用
- ✅ MIT 开源
- ✅ 支持大量 NPC

**GitHub：** https://github.com/FancyMcPlugins/FancyNpcs  
**Modrinth：** https://modrinth.com/plugin/fancynpcs

### 集成方式

```yaml
# plugin.yml
softdepend: [FancyNpcs]
```

```java
// NPCIntegration.java
public class NPCIntegration {
    private final Plugin plugin;
    
    public void createNPC(Location loc, String name) {
        if (!isFancyNpcsAvailable()) {
            return;
        }
        
        // 异步创建 NPC
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // FancyNpcs API 调用
            // 创建 Packet-based NPC
        });
    }
    
    private boolean isFancyNpcsAvailable() {
        return plugin.getServer().getPluginManager()
            .getPlugin("FancyNpcs") != null;
    }
}
```

### 备选方案：NpcLib（自定义开发）

**选择理由：**
- ✅ 最高性能
- ✅ 异步设计
- ✅ 完全可控
- ✅ 适合深度集成

**GitHub：** https://github.com/juliarn/npc-lib

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.github.juliarn</groupId>
    <artifactId>npc-lib</artifactId>
    <version>3.0.0</version>
</dependency>
```

---

## 🔧 高性能优化策略

### 1. 严格异步化

**核心原则：**
```java
// ❌ 错误：主线程阻塞
public void loadData(Player player) {
    String data = database.query(...); // 阻塞主线程！
    player.sendMessage(data);
}

// ✅ 正确：异步加载
public void loadData(Player player) {
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        // 异步执行
        String data = database.query(...);
        
        // 回到主线程处理
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.sendMessage(data);
        });
    });
}
```

### 2. Paper/Folia 兼容异步

**RegionScheduler（推荐）：**
```java
// Paper 1.21+ / Folia Ready
public void teleportPlayer(Player player, Location location) {
    // 在目标区域的线程执行
    Bukkit.getRegionScheduler().run(plugin, location, task -> {
        player.teleport(location);
    });
}

// 全局异步任务
public void globalAsyncTask() {
    Bukkit.getAsyncScheduler().runNow(plugin, task -> {
        // 异步执行，不依赖特定区域
        heavyComputation();
    });
}
```

### 3. 线程安全数据结构

**必须使用：**
```java
// ✅ 线程安全
private final ConcurrentHashMap<UUID, PlayerData> playerData = 
    new ConcurrentHashMap<>();

// ✅ 线程安全
private final CopyOnWriteArrayList<Listener> listeners = 
    new CopyOnWriteArrayList<>();

// ✅ 原子操作
private final AtomicInteger counter = new AtomicInteger(0);

// ❌ 不安全
private final HashMap<UUID, PlayerData> playerData = new HashMap<>();
```

### 4. NPC 性能优化

**策略：**

```java
public class OptimizedNPCManager {
    // 只在玩家附近渲染
    private static final int RENDER_DISTANCE = 32; // 区块
    
    // 分区块管理
    private final Map<Long, Set<NPC>> chunkNPCs = 
        new ConcurrentHashMap<>();
    
    // 异步皮肤加载
    private final LoadingCache<String, Skin> skinCache = 
        Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .buildAsync((key, executor) -> loadSkin(key));
    
    public void updateNPCsForPlayer(Player player) {
        Location loc = player.getLocation();
        
        // 异步计算可见 NPC
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Set<NPC> visibleNPCs = getVisibleNPCs(loc, RENDER_DISTANCE);
            
            // 主线程显示
            Bukkit.getScheduler().runTask(plugin, () -> {
                showNPCs(player, visibleNPCs);
            });
        });
    }
    
    private Set<NPC> getVisibleNPCs(Location center, int distance) {
        Set<NPC> result = new HashSet<>();
        
        int chunkX = center.getBlockX() >> 4;
        int chunkZ = center.getBlockZ() >> 4;
        
        for (int dx = -distance; dx <= distance; dx++) {
            for (int dz = -distance; dz <= distance; dz++) {
                long chunkKey = getChunkKey(chunkX + dx, chunkZ + dz);
                Set<NPC> npcs = chunkNPCs.get(chunkKey);
                if (npcs != null) {
                    result.addAll(npcs);
                }
            }
        }
        
        return result;
    }
    
    private long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
```

---

## 🎨 ItemsAdder / CraftEngine 集成

### 可选依赖配置

```yaml
# plugin.yml
softdepend: [ItemsAdder, CraftEngine, FancyNpcs]
```

### 动态检测和适配

```java
public class CustomItemIntegration {
    private boolean itemsAdderAvailable = false;
    private boolean craftEngineAvailable = false;
    
    public void init() {
        PluginManager pm = plugin.getServer().getPluginManager();
        
        itemsAdderAvailable = pm.getPlugin("ItemsAdder") != null;
        craftEngineAvailable = pm.getPlugin("CraftEngine") != null;
        
        if (itemsAdderAvailable) {
            plugin.getLogger().info("✅ ItemsAdder 集成已启用");
        }
        
        if (craftEngineAvailable) {
            plugin.getLogger().info("✅ CraftEngine 集成已启用");
        }
    }
    
    public ItemStack getCustomItem(String itemId) {
        // 优先 ItemsAdder
        if (itemsAdderAvailable) {
            return getItemsAdderItem(itemId);
        }
        
        // 其次 CraftEngine
        if (craftEngineAvailable) {
            return getCraftEngineItem(itemId);
        }
        
        // 默认原版物品
        return getVanillaItem(itemId);
    }
}
```

---

## 🚀 Folia Ready 架构

### 标记 Folia 支持

```yaml
# paper-plugin.yml (推荐)
name: STARCORE
main: dev.starcore.starcore.StarCorePlugin
api-version: '1.21'
folia-supported: true

# 或 plugin.yml
folia-supported: true
```

### Folia 兼容代码

```java
public class FoliaCompatibleService {
    private final Plugin plugin;
    
    /**
     * 在实体所在区域执行任务
     */
    public void runAtEntity(Entity entity, Runnable task) {
        if (isFolia()) {
            entity.getScheduler().run(plugin, t -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    
    /**
     * 在位置所在区域执行任务
     */
    public void runAtLocation(Location location, Runnable task) {
        if (isFolia()) {
            Bukkit.getRegionScheduler().run(plugin, location, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    
    /**
     * 异步任务（兼容）
     */
    public void runAsync(Runnable task) {
        if (isFolia()) {
            Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }
    
    /**
     * 全局任务
     */
    public void runGlobal(Runnable task) {
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    
    private boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
```

---

## 📊 性能监控

### Spark 集成

```java
public class PerformanceMonitor {
    /**
     * 监控热点方法
     */
    @Profiled // Spark 注解
    public void expensiveOperation() {
        // 自动被 Spark 监控
    }
    
    /**
     * 自定义性能统计
     */
    public void trackPerformance() {
        long start = System.nanoTime();
        
        try {
            heavyTask();
        } finally {
            long duration = System.nanoTime() - start;
            if (duration > 50_000_000) { // 50ms
                plugin.getLogger().warning(
                    "Slow operation: " + duration / 1_000_000 + "ms"
                );
            }
        }
    }
}
```

---

## 🏗️ 完整架构

### 模块结构

```
starcore/
├── core/
│   └── scheduler/
│       ├── FoliaCompatScheduler.java ⭐
│       └── AsyncTaskExecutor.java
├── npc/                           ⭐新增
│   ├── NPCManager.java
│   ├── OptimizedNPCRenderer.java
│   └── integration/
│       ├── FancyNpcsIntegration.java
│       └── NpcLibIntegration.java
├── items/                         ⭐新增
│   └── integration/
│       ├── ItemsAdderIntegration.java
│       └── CraftEngineIntegration.java
└── performance/
    ├── PerformanceMonitor.java
    └── ChunkManager.java
```

---

## 📋 依赖配置

### pom.xml

```xml
<dependencies>
    <!-- Paper API -->
    <dependency>
        <groupId>io.papermc.paper</groupId>
        <artifactId>paper-api</artifactId>
        <version>1.21-R0.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
    
    <!-- NPC Lib (可选) -->
    <dependency>
        <groupId>com.github.juliarn</groupId>
        <artifactId>npc-lib</artifactId>
        <version>3.0.0</version>
        <scope>compile</scope>
        <optional>true</optional>
    </dependency>
    
    <!-- Caffeine (缓存) -->
    <dependency>
        <groupId>com.github.ben-manes.caffeine</groupId>
        <artifactId>caffeine</artifactId>
        <version>3.1.8</version>
    </dependency>
</dependencies>
```

### plugin.yml

```yaml
name: STARCORE
version: '${project.version}'
main: dev.starcore.starcore.StarCorePlugin
api-version: '1.21'
folia-supported: true
load: STARTUP

softdepend:
  - Vault
  - PlaceholderAPI
  - LuckPerms
  - FancyNpcs
  - ItemsAdder
  - CraftEngine
  - ProtocolLib
```

---

## 🎯 性能目标

### 基准测试

| 场景 | 目标 | 说明 |
|------|------|------|
| 10,000 NPC | TPS 19.8+ | Packet方式 |
| 1000 玩家 | TPS 19.5+ | 异步处理 |
| 数据库查询 | <30ms | 连接池 |
| GUI 打开 | <5ms | 缓存 |
| NPC 渲染 | <1ms | 只渲染可见 |

---

## 🔍 性能分析工具

### 推荐工具

1. **Spark** - 实时性能分析
   ```
   /spark profiler
   /spark heapdump
   ```

2. **Chunky** - 预生成世界
   ```
   /chunky world <world>
   /chunky radius 1000
   /chunky start
   ```

3. **WarmRoast** - CPU分析
   - 深度分析瓶颈

---

## 📝 最佳实践清单

### ✅ 必做

- ✅ 所有重操作异步化
- ✅ 使用 ConcurrentHashMap
- ✅ NPC 使用 Packet 方式
- ✅ 只渲染可见 NPC
- ✅ 异步皮肤加载
- ✅ 数据库连接池
- ✅ 缓存常用数据
- ✅ 定期清理无用数据

### ❌ 禁止

- ❌ 主线程数据库查询
- ❌ 主线程文件 I/O
- ❌ 使用非线程安全集合
- ❌ 创建真实实体 NPC
- ❌ 高频率的世界修改
- ❌ 未限制的实体生成

---

## 🚀 实施计划

### Phase 1: 基础架构（1天）

1. ✅ FoliaCompatScheduler
2. ✅ 线程安全数据结构
3. ✅ 异步任务框架

### Phase 2: NPC集成（2天）

4. ✅ FancyNpcs 集成
5. ✅ 优化渲染系统
6. ✅ 区块管理

### Phase 3: 性能优化（1天）

7. ✅ 缓存系统
8. ✅ 性能监控
9. ✅ 压力测试

---

**文档版本：** 1.0  
**创建时间：** 2026-06-15  
**状态：** 🚀 高性能就绪
