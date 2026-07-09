# 存储层抽象使用指南

## 概述

`AbstractModuleStateStorage` 是 StarCore 提供的存储层抽象基类，用于消除各模块存储代码的重复。

## 设计理念

**双模式存储架构**：
1. **Properties 文件存储**（传统模式）- 简单、快速、适合小型服务器
2. **SQL 数据库存储**（生产模式）- 高性能、可扩展、适合大型服务器

**自动化特性**：
- 数据库失败时自动降级到文件存储
- Properties → SQL 自动迁移
- 异步保存支持
- 表结构自动创建

---

## 使用示例

### 步骤 1: 继承抽象基类

```java
package dev.starcore.starcore.module.example;

import dev.starcore.starcore.core.storage.AbstractModuleStateStorage;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

final class SqlExampleStateStorage extends AbstractModuleStateStorage {

    private static final String TABLE_NAME = "starcore_example_state";
    private static final String FILE_NAME = "example.properties";

    SqlExampleStateStorage(
            String namespace,
            DatabaseService databaseService,
            PersistenceService persistenceService,
            Logger logger) {
        super(namespace, databaseService, persistenceService, logger);
    }

    @Override
    protected String getTableName() {
        return TABLE_NAME;
    }

    @Override
    protected String getFileName() {
        return FILE_NAME;
    }

    @Override
    protected void ensureTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS starcore_example_state (
                    property_key VARCHAR(255) NOT NULL PRIMARY KEY,
                    property_value TEXT NOT NULL
                )
                """);
        }
    }

    @Override
    protected Properties loadFromDatabase(Connection connection) throws SQLException {
        Properties properties = new Properties();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT property_key, property_value
            FROM starcore_example_state
            ORDER BY property_key
            """);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                properties.setProperty(
                    rows.getString("property_key"),
                    rows.getString("property_value")
                );
            }
        }
        return properties;
    }

    @Override
    protected void writeSnapshot(Connection connection, Properties properties) throws SQLException {
        // 清空旧数据
        try (Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM starcore_example_state");
        }

        // 批量插入新数据
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO starcore_example_state (property_key, property_value)
            VALUES (?, ?)
            """)) {
            for (String key : properties.stringPropertyNames()) {
                statement.setString(1, key);
                statement.setString(2, properties.getProperty(key));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
```

### 步骤 2: 创建存储接口

```java
package dev.starcore.starcore.module.example;

import java.util.Properties;

interface ExampleStateStorage {
    Properties load();
    void save(Properties properties);
    void saveAsync(Properties properties);
}
```

### 步骤 3: 包装为接口实现

```java
package dev.starcore.starcore.module.example;

import java.util.Properties;

final class ExampleStateStorageImpl implements ExampleStateStorage {

    private final AbstractModuleStateStorage delegate;

    ExampleStateStorageImpl(AbstractModuleStateStorage delegate) {
        this.delegate = delegate;
    }

    @Override
    public Properties load() {
        return delegate.load();
    }

    @Override
    public void save(Properties properties) {
        delegate.save(properties);
    }

    @Override
    public void saveAsync(Properties properties) {
        delegate.saveAsync(properties);
    }
}
```

### 步骤 4: 在模块中使用

```java
package dev.starcore.starcore.module.example;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.StarCoreModule;

import java.util.Properties;

public class ExampleModule implements StarCoreModule {

    private ExampleStateStorage storage;

    @Override
    public void enable(StarCoreContext context) {
        // 初始化存储
        this.storage = new ExampleStateStorageImpl(
            new SqlExampleStateStorage(
                "example",
                context.databaseService(),
                context.persistenceService(),
                context.plugin().getLogger()
            )
        );

        // 加载状态
        Properties state = storage.load();
        System.out.println("Loaded state: " + state);

        // 保存状态
        state.setProperty("key", "value");
        storage.saveAsync(state);
    }

    @Override
    public void disable(StarCoreContext context) {
        // 清理资源
    }
}
```

---

## 工作原理

### 智能加载流程

```
1. 尝试从 SQL 加载
   ↓ 成功？
   ✅ 返回 SQL 数据

   ↓ SQL 为空？
2. 检查 Properties 文件
   ↓ 有数据？
   ✅ 自动迁移到 SQL
   ✅ 返回 Properties 数据

   ↓ SQL 失败？
3. 降级到 Properties
   ✅ 返回 Properties 数据
```

### 智能保存流程

```
数据库启用？
   ↓ 是
   尝试保存到 SQL
   ↓ 成功？
   ✅ 完成

   ↓ 失败？
   降级到 Properties
   ✅ 完成

   ↓ 否
   保存到 Properties
   ✅ 完成
```

---

## 优势

### ✅ 消除代码重复

**之前**：每个模块需要 3 个类（~300 行代码）
- `PersistenceXxxStateStorage.java`
- `SqlXxxStateStorage.java`
- `DatabaseAwareXxxStateStorage.java`

**现在**：每个模块只需 1 个类（~80 行代码）
- `SqlXxxStateStorage.java` extends `AbstractModuleStateStorage`

**节省**：36 个模块 × 220 行 = **7,920 行代码消除**

### ✅ 统一错误处理

所有模块共享相同的降级逻辑和错误恢复策略。

### ✅ 更容易维护

修改存储逻辑只需更新基类，所有模块自动受益。

### ✅ 性能优化

- 异步保存支持（不阻塞主线程）
- 批量写入优化
- 连接池管理

---

## 迁移现有模块

### 迁移清单

```
☐ Nation Module
☐ Diplomacy Module
☐ Policy Module
☐ Resource Module
☐ Technology Module
☐ Treasury Module
☐ War Module
☐ Officer Module
☐ Event Module
☐ Resolution Module
☐ Government Module
☐ Map Module
```

### 迁移步骤

1. 复制现有的 `SqlXxxStateStorage.java`
2. 修改继承：`extends AbstractModuleStateStorage`
3. 实现抽象方法：
   - `getTableName()`
   - `getFileName()`
   - `ensureTable(Connection)`
   - `loadFromDatabase(Connection)`
   - `writeSnapshot(Connection, Properties)`
4. 删除重复代码（load/save 逻辑）
5. 运行测试验证

---

## 配置

存储模式由 `config.yml` 控制：

```yaml
database:
  enabled: true        # true = SQL 模式，false = Properties 模式
  type: sqlite         # sqlite | mysql
  fail-fast: false     # 数据库失败时是否停止启动
```

---

## 测试

### 单元测试示例

```java
@Test
void testAutoMigration() {
    // 准备 Properties 数据
    Properties legacy = new Properties();
    legacy.setProperty("key1", "value1");
    persistenceService.saveProperties("test", "test.properties", legacy);

    // 首次加载应该触发迁移
    Properties loaded = storage.load();
    assertEquals("value1", loaded.getProperty("key1"));

    // 验证 SQL 中已有数据
    try (Connection conn = dataSource.getConnection()) {
        ResultSet rs = conn.createStatement().executeQuery(
            "SELECT COUNT(*) FROM starcore_test_state"
        );
        rs.next();
        assertTrue(rs.getInt(1) > 0, "数据应该已迁移到 SQL");
    }
}
```

---

## 常见问题

### Q: 如何强制使用 Properties 模式？

```yaml
# config.yml
database:
  enabled: false
```

### Q: 数据库失败后会丢失数据吗？

不会。系统会自动降级到 Properties 文件存储，数据安全保存。

### Q: 如何手动触发迁移？

```java
// 强制从 Properties 迁移到 SQL
Properties data = storage.loadFromProperties();
storage.forceSqlSave(data);
```

### Q: 异步保存会有并发问题吗？

不会。`AbstractModuleStateStorage` 使用锁机制确保异步保存的顺序执行。

---

## 相关文档

- [数据库迁移指南](DATABASE_MIGRATION.md)
- [Flyway 集成说明](FLYWAY_INTEGRATION.md)
- [存储层性能优化](STORAGE_PERFORMANCE.md)
