# STARCORE 开发者指南

**版本:** 0.1.0-SNAPSHOT  
**更新日期:** 2026-06-24

---

## 目录

- [项目概述](#项目概述)
- [项目结构](#项目结构)
- [核心概念](#核心概念)
- [模块开发](#模块开发)
- [服务注册](#服务注册)
- [事件系统](#事件系统)
- [数据库使用](#数据库使用)
- [API 集成](#api-集成)
- [命令开发](#命令开发)
- [GUI 开发](#gui-开发)
- [测试指南](#测试指南)
- [构建发布](#构建发布)

---

## 项目概述

STARCORE 是一个 Paper/Nukkit 原生的国家战略模拟插件，采用模块化架构。

### 技术栈

| 技术 | 版本 |
|------|------|
| Java | 21+ |
| Paper API | 1.21.11+ |
| Maven | 3.8+ |
| HikariCP | 数据库连接池 |
| Flyway | 数据库迁移 |

### 项目规模

- **类数量:** 241+
- **测试用例:** 355+
- **完成度:** 85%+

---

## 项目结构

```
src/main/java/dev/starcore/starcore/
├── StarCorePlugin.java           # 插件主类
│
├── api/                          # 公开 API
│   ├── StarCoreApi.java          # 主 API 接口
│   ├── StarCoreApiProvider.java   # API 提供者
│   └── v1/                       # REST API v1
│       ├── RestApiServer.java
│       ├── dto/                  # 数据传输对象
│       ├── endpoint/             # API 端点
│       └── auth/                 # 认证服务
│
├── core/                         # 核心框架
│   ├── module/                   # 模块系统
│   │   ├── ModuleManager.java
│   │   ├── ModuleDescriptor.java
│   │   └── StarCoreModule.java
│   ├── database/                # 数据库服务
│   ├── event/                    # 事件总线
│   ├── scheduler/                # 任务调度
│   └── service/                  # 核心服务
│
├── module/                       # 业务模块
│   ├── nation/                   # 国家系统
│   │   ├── NationModule.java
│   │   ├── NationService.java
│   │   ├── NationServiceImpl.java
│   │   └── Nation.java           # 数据模型
│   ├── government/               # 政体系统
│   ├── diplomacy/                # 外交系统
│   ├── war/                      # 战争系统
│   ├── policy/                   # 国策系统
│   ├── technology/               # 科技系统
│   ├── resource/                # 资源系统
│   ├── treasury/                 # 金库系统
│   ├── officer/                 # 官员系统
│   ├── resolution/               # 决议系统
│   ├── map/                      # 地图系统
│   └── event/                    # 事件系统
│
├── foundation/                   # 基础设施
│   ├── player/                  # 玩家数据
│   ├── territory/                # 领土
│   ├── economy/                  # 经济
│   ├── epoch/                   # 纪元
│   └── timesync/                # 时间同步
│
└── utils/                        # 工具类
```

---

## 核心概念

### 模块系统

STARCORE 采用模块化架构，每个功能都是一个独立的模块。

```
模块生命周期:
enable() -> onEnable() -> onDisable() -> disable()
```

### 服务定位器

使用 `StarCoreContext` 作为中央容器，通过 `ServiceRegistry` 获取服务。

```java
// 获取服务示例
StarCoreContext ctx = StarCoreContext.getInstance();
NationService nationService = ctx.getService(NationService.class);
```

### 事件驱动

模块间通过 `StarCoreEventBus` 进行通信，实现松耦合。

---

## 模块开发

### 创建新模块

1. 创建模块类，实现 `StarCoreModule` 接口
2. 使用 `@ModuleMetadata` 注解标注元数据
3. 在模块中注册服务和监听器

```java
package dev.starcore.starcore.module.example;

import dev.starcore.starcore.core.module.ModuleDescriptor;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.StarCoreContext;
import org.jetbrains.annotations.NotNull;

@ModuleMetadata(
    id = "example",
    name = "Example Module",
    version = "1.0.0",
    description = "An example module",
    authors = {"Developer"}
)
public class ExampleModule implements StarCoreModule {
    
    private ExampleService exampleService;
    
    @Override
    public void enable(@NotNull StarCoreContext context) {
        // 创建服务实例
        exampleService = new ExampleServiceImpl();
        
        // 注册服务
        context.getServiceRegistry().register(ExampleService.class, exampleService);
        
        // 注册命令
        // 注册监听器
    }
    
    @Override
    public void disable(@NotNull StarCoreContext context) {
        // 清理资源
        if (exampleService != null) {
            context.getServiceRegistry().unregister(ExampleService.class);
        }
    }
}
```

### 模块元数据注解

```java
@ModuleMetadata(
    id = "module-id",           // 唯一标识符
    name = "Module Name",       // 显示名称
    version = "1.0.0",         // 版本号
    description = "Description", // 描述
    authors = {"Author1", "Author2"},  // 作者
    dependencies = {}           // 依赖模块
)
```

---

## 服务注册

### 定义服务接口

```java
package dev.starcore.starcore.module.example;

public interface ExampleService {
    String getMessage();
    boolean doAction(String param);
}
```

### 实现服务

```java
package dev.starcore.starcore.module.example;

public class ExampleServiceImpl implements ExampleService {
    
    @Override
    public String getMessage() {
        return "Hello from ExampleService!";
    }
    
    @Override
    public boolean doAction(String param) {
        // 实现逻辑
        return true;
    }
}
```

### 注册服务

在模块的 `enable()` 方法中注册：

```java
@Override
public void enable(@NotNull StarCoreContext context) {
    ExampleServiceImpl service = new ExampleServiceImpl();
    context.getServiceRegistry().register(ExampleService.class, service);
}
```

### 通过 API 获取服务

```java
// 其他插件可以通过 StarCoreApi 获取服务
RegisteredServiceProvider<StarCoreApi> provider = 
    Bukkit.getServicesManager().getRegistration(StarCoreApi.class);

if (provider != null) {
    StarCoreApi api = provider.getProvider();
    api.exampleService().ifPresent(service -> {
        String msg = service.getMessage();
    });
}
```

---

## 事件系统

### 定义事件

```java
package dev.starcore.starcore.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ExampleEvent extends Event {
    
    private static final HandlerList handlers = new HandlerList();
    private final String message;
    
    public ExampleEvent(String message) {
        this.message = message;
    }
    
    public static HandlerList getHandlerList() {
        return handlers;
    }
    
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
    
    public String getMessage() {
        return message;
    }
}
```

### 发布事件

```java
// 通过事件总线发布
StarCoreContext ctx = StarCoreContext.getInstance();
ctx.getEventBus().publish(new ExampleEvent("test message"));

// 或通过 Bukkit 发布
getServer().getPluginManager().callEvent(new ExampleEvent("test"));
```

### 监听事件

```java
package dev.starcore.starcore.listeners;

import dev.starcore.starcore.events.ExampleEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ExampleListener implements Listener {
    
    @EventHandler
    public void onExampleEvent(ExampleEvent event) {
        // 处理事件
        getLogger().info("Received: " + event.getMessage());
    }
}
```

### 注册监听器

```java
@Override
public void enable(@NotNull StarCoreContext context) {
    // 注册监听器
    context.getEventBus().register(new ExampleListener());
    
    // 或通过 Bukkit
    getServer().getPluginManager().registerEvents(
        new ExampleListener(), this);
}
```

---

## 数据库使用

### 使用 Flyway 迁移

在 `src/main/resources/db/migration/` 创建迁移文件：

```sql
-- V1__example_table.sql
CREATE TABLE IF NOT EXISTS example_table (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    value DOUBLE NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_example_name ON example_table(name);
```

### 使用 Repository

```java
package dev.starcore.starcore.module.example.repository;

import dev.starcore.starcore.core.database.AbstractRepository;
import dev.starcore.starcore.module.example.ExampleEntity;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public class ExampleRepository extends AbstractRepository {
    
    public ExampleRepository(DataSource dataSource) {
        super(dataSource);
    }
    
    public void save(ExampleEntity entity) {
        String sql = """
            INSERT INTO example_table (id, name, value)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE name = VALUES(name), value = VALUES(value)
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, entity.getId());
            stmt.setString(2, entity.getName());
            stmt.setDouble(3, entity.getValue());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save entity", e);
        }
    }
    
    public Optional<ExampleEntity> findById(String id) {
        String sql = "SELECT * FROM example_table WHERE id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find entity", e);
        }
    }
    
    public List<ExampleEntity> findAll() {
        String sql = "SELECT * FROM example_table";
        List<ExampleEntity> results = new ArrayList<>();
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                results.add(mapResultSet(rs));
            }
            return results;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all entities", e);
        }
    }
    
    private ExampleEntity mapResultSet(ResultSet rs) throws SQLException {
        ExampleEntity entity = new ExampleEntity();
        entity.setId(rs.getString("id"));
        entity.setName(rs.getString("name"));
        entity.setValue(rs.getDouble("value"));
        return entity;
    }
}
```

### 异步数据库操作

```java
// 在 StarCoreScheduler 中执行
StarCoreContext ctx = StarCoreContext.getInstance();
ctx.getScheduler().runAsync(() -> {
    repository.save(entity);
});
```

---

## API 集成

### 集成 StarCore API

```java
package com.example.plugin;

import dev.starcore.starcore.api.StarCoreApi;
import dev.starcore.starcore.module.treasury.TreasuryRewardService;
import org.bukkit.plugin.java.JavaPlugin;

public class MyRPGPlugin extends JavaPlugin {
    
    private StarCoreApi starCoreApi;
    
    @Override
    public void onEnable() {
        // 获取 StarCore API
        var provider = getServer().getServicesManager()
            .getRegistration(StarCoreApi.class);
        
        if (provider != null) {
            starCoreApi = provider.getProvider();
            getLogger().info("StarCore API connected: " + starCoreApi.version());
        } else {
            getLogger().warning("StarCore not found!");
        }
    }
    
    public void onQuestComplete(org.bukkit.entity.Player player, String questName) {
        if (starCoreApi == null) return;
        
        // 使用 TreasuryRewardService 添加任务奖励
        starCoreApi.treasuryRewardService().ifPresent(service -> {
            java.util.UUID nationId = getPlayerNationId(player);
            if (nationId != null) {
                service.addReward(
                    nationId,
                    1000.0,
                    "quest",
                    "完成任务: " + questName
                );
                player.sendMessage("任务完成! 国家金库获得 1000 金币奖励!");
            }
        });
    }
}
```

### plugin.yml 配置

```yaml
name: MyRPGPlugin
version: 1.0.0
main: com.example.plugin.MyRPGPlugin
api-version: '1.21'
softdepend:
  - StarCore
```

### 权限配置

```yaml
# 为 StarCore 命令添加权限
permissions:
  myrpgplugin.quest.complete:
    description: Complete quests
    default: true
```

---

## 命令开发

### 使用brigade框架

STARCORE 使用 Paper 的 brigadier 命令框架。

```java
package dev.starcore.starcore.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ExampleCommand {
    
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        LiteralCommandNode<CommandSource> root = dispatcher.register(
            LiteralArgumentBuilder.literal("example")
                .requires(source -> source.hasPermission("starcore.example"))
                .then(LiteralArgumentBuilder.literal("info")
                    .executes(context -> {
                        context.getSource().sendMessage("Example command info");
                        return 1;
                    })
                )
                .then(LiteralArgumentBuilder.literal("action")
                    .then(ArgumentCommandNode.argument(
                        "player", StringArgumentType.word()
                    ).executes(context -> {
                        String playerName = StringArgumentType.getString(context, "player");
                        context.getSource().sendMessage("Action on: " + playerName);
                        return 1;
                    }))
                )
        );
    }
}
```

### Tab 补全

```java
.then(LiteralArgumentBuilder.literal("action")
    .then(ArgumentCommandNode.argument(
        "player", StringArgumentType.word()
    ).suggests((context, builder) -> {
        // 提供玩家名补全
        for (Player player : context.getSource().getServer().getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(
                builder.getRemaining().toLowerCase())) {
                builder.suggest(player.getName());
            }
        }
        return builder.buildFuture();
    }).executes(context -> {
        // 执行命令
        return 1;
    }))
)
```

---

## GUI 开发

### 使用 TriumphGUI

STARCORE 支持 TriumphGUI 库创建箱子界面。

```java
package dev.starcore.starcore.gui;

import dev.starcore.starcore.gui.components.GUIButton;
import dev.starcore.starcore.gui.components.GUIHolder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ExampleGUI extends GUIHolder {
    
    public ExampleGUI() {
        super("Example GUI", 27);  // 3 行
        
        // 设置按钮
        setButton(11, createButton(
            Material.DIAMOND,
            "Action 1",
            "Click to perform action 1"
        ), (player, event) -> {
            // 处理点击
            player.sendMessage("Action 1 triggered!");
        });
        
        setButton(13, createButton(
            Material.GOLD_INGOT,
            "Action 2",
            "Click to perform action 2"
        ), (player, event) -> {
            player.sendMessage("Action 2 triggered!");
        });
        
        setButton(15, createButton(
            Material.BARRIER,
            "Close",
            "Click to close"
        ), (player, event) -> {
            player.closeInventory();
        });
    }
    
    private ItemStack createButton(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        org.bukkit.inventory.ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(java.util.Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }
}
```

### 打开 GUI

```java
// 在命令或事件中打开
ExampleGUI gui = new ExampleGUI();
gui.open(player);
```

---

## 测试指南

### 运行测试

```bash
mvn test
```

### 添加单元测试

```java
package dev.starcore.starcore.module.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExampleServiceTest {
    
    @Test
    void testGetMessage() {
        ExampleService service = new ExampleServiceImpl();
        assertNotNull(service.getMessage());
        assertFalse(service.getMessage().isEmpty());
    }
    
    @Test
    void testDoAction() {
        ExampleService service = new ExampleServiceImpl();
        assertTrue(service.doAction("test"));
        assertFalse(service.doAction(""));
    }
}
```

### 添加集成测试

```java
package dev.starcore.starcore.module.example;

import dev.starcore.starcore.core.StarCorePlugin;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ExampleIntegrationTest {
    
    @Mock
    private StarCorePlugin plugin;
    
    @Test
    void testClaimTool() {
        // 测试圈地工具逻辑
        ItemStack item = new ItemStack(Material.GOLDEN_SHOVEL);
        assertEquals(Material.GOLDEN_SHOVEL, item.getType());
    }
}
```

---

## 构建发布

### 构建 JAR

```bash
mvn clean package
```

产物位置: `target/starcore-0.1.0-SNAPSHOT.jar`

### 发布检查清单

1. 运行测试: `mvn test`
2. 构建 JAR: `mvn package`
3. 检查 JAR 大小和内容
4. 更新版本号
5. 更新 CHANGELOG
6. 创建 Git tag

### 版本号规范

使用语义化版本 (Semantic Versioning):

```
主版本.次版本.修订版本[-预发布版本]

示例:
- 0.1.0-SNAPSHOT  (开发版)
- 1.0.0           (正式版)
- 1.0.1           (补丁版)
- 2.0.0-alpha     (预发布版)
```

---

## 代码规范

### 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | PascalCase | `NationService` |
| 方法名 | camelCase | `getPlayerNation()` |
| 常量 | UPPER_SNAKE_CASE | `MAX_MEMBERS` |
| 包名 | lowercase | `dev.starcore.starcore.module` |
| 配置键 | kebab-case | `enable-feature` |

### 代码模板

```java
package dev.starcore.starcore.module.example;

import org.jetbrains.annotations.NotNull;

/**
 * Example service implementation.
 *
 * @author Developer
 * @since 1.0.0
 */
public class ExampleServiceImpl implements ExampleService {
    
    private static final int DEFAULT_VALUE = 100;
    
    @Override
    public @NotNull String getMessage() {
        return "Hello, StarCore!";
    }
    
    @Override
    public boolean doAction(@NotNull String param) {
        if (param == null || param.isEmpty()) {
            return false;
        }
        // Implementation
        return true;
    }
}
```

---

## 常见问题

### Q: 如何添加新的配置项？

1. 在 `config.yml` 中添加配置
2. 在对应的配置类中读取
3. 提供配置重载支持

### Q: 如何添加新的命令？

1. 创建命令类
2. 在模块的 `enable()` 中注册
3. 添加对应的中文语言文本

### Q: 如何处理异步操作？

使用 `StarCoreScheduler`:

```java
ctx.getScheduler().runAsync(() -> {
    // 异步执行
});
```

### Q: 如何发布到 Maven Central？

1. 配置 `pom.xml` 中的 distributionManagement
2. 创建 GPG 密钥
3. 运行 `mvn clean deploy`

---

## 资源链接

- [Paper API 文档](https://jd.papermc.io/paper/1.21/)
- [Mojang Brigade 文档](https://github.com/Mojang/brigadier)
- [HikariCP 文档](https://github.com/brettwooldridge/HikariCP)
- [Flyway 文档](https://flywaydb.org/documentation/)

---

## 贡献指南

欢迎贡献代码！

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

---

**文档版本:** v1.0  
**最后更新:** 2026-06-24  
**维护者:** StarCore Team
