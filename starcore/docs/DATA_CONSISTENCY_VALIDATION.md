# 数据一致性验证工具

本文档介绍如何使用数据一致性验证工具来验证 Properties 文件和 SQL 数据库中的数据完全一致。

## 概述

数据一致性验证工具用于确保从 Properties 迁移到 SQL 后数据不丢失、不损坏。该工具会：

- 比对 Properties 和 SQL 中的所有模块数据
- 检查键值对数量是否一致
- 检查每个键的值是否完全匹配
- 生成详细的验证报告

## 支持的模块

- `nation` - 国家状态 (nations.properties)
- `diplomacy` - 外交关系 (relations.properties)
- `policy` - 政策状态 (policies.properties)
- `resource` - 资源状态 (resources.properties)
- `technology` - 科技状态 (technologies.properties)
- `treasury` - 国库状态 (treasuries.properties)
- `war` - 战争状态 (wars.properties)
- `officer` - 官员状态 (officers.properties)
- `event` - 事件状态 (events.properties)
- `resolution` - 决议状态 (resolutions.properties)

## 使用方法

### 方式一：使用验证脚本（推荐）

#### Linux/macOS

```bash
# 给脚本添加执行权限
chmod +x scripts/validate-data-consistency.sh

# 验证所有模块
./scripts/validate-data-consistency.sh \
  --db-url jdbc:mysql://localhost:3306/starcore \
  --db-user root \
  --db-password password

# 验证指定模块
./scripts/validate-data-consistency.sh \
  --db-url jdbc:mysql://localhost:3306/starcore \
  --db-user root \
  --db-password password \
  --modules nation,diplomacy,policy

# SQLite 示例
./scripts/validate-data-consistency.sh \
  --db-url jdbc:sqlite:./plugins/starcore/starcore.db \
  --db-user "" \
  --db-password ""
```

#### Windows

```cmd
REM 验证所有模块
scripts\validate-data-consistency.bat ^
  --db-url jdbc:mysql://localhost:3306/starcore ^
  --db-user root ^
  --db-password password

REM 验证指定模块
scripts\validate-data-consistency.bat ^
  --db-url jdbc:mysql://localhost:3306/starcore ^
  --db-user root ^
  --db-password password ^
  --modules nation,diplomacy,policy
```

### 方式二：直接使用 Java 命令

```bash
# 编译项目
mvn clean package

# 运行验证工具
java -cp target/starcore-0.1.0-SNAPSHOT.jar \
  dev.starcore.starcore.core.database.DataConsistencyValidatorCommand \
  --data-dir ./plugins/starcore \
  --db-url jdbc:mysql://localhost:3306/starcore \
  --db-user root \
  --db-password password \
  --modules nation,diplomacy \
  --output my-report.md
```

## 命令行参数

| 参数 | 说明 | 必需 | 默认值 |
|------|------|------|--------|
| `--data-dir` | StarCore 数据目录路径 | 否 | `./plugins/starcore` |
| `--db-url` | 数据库 JDBC URL | 是 | 无 |
| `--db-user` | 数据库用户名 | 是 | 无 |
| `--db-password` | 数据库密码 | 是 | 无 |
| `--modules` | 要验证的模块（逗号分隔） | 否 | 全部模块 |
| `--output` | 输出报告文件路径 | 否 | 自动生成时间戳文件名 |
| `--jar` | JAR 文件路径 | 否 | `target/starcore-0.1.0-SNAPSHOT.jar` |
| `--help, -h` | 显示帮助信息 | 否 | - |

## 验证报告

验证完成后会生成一个 Markdown 格式的报告文件，包含：

### 1. 总览

显示所有模块的验证状态统计：

```
## 总览

| 状态 | 数量 |
|------|------|
| 通过 | 8 |
| 失败 | 2 |
| 错误 | 0 |
| **总计** | **10** |
```

### 2. 详细结果

每个模块的详细验证结果：

#### 通过的模块

```markdown
### nation 模块

**状态**: 通过

- Properties 键数量: 150
- SQL 键数量: 150
- 数据完全一致
```

#### 失败的模块

```markdown
### diplomacy 模块

**状态**: 失败

- Properties 键数量: 100
- SQL 键数量: 98

#### 仅在 Properties 中存在的键 (2)

- `relation.china.usa.status`
- `relation.china.russia.trade`

#### 仅在 SQL 中存在的键 (0)

#### 值不一致的键 (3)

**`relation.usa.japan.alliance`**
- Properties: `true`
- SQL: `false`

**`relation.france.germany.trade_level`**
- Properties: `5`
- SQL: `4`
```

## 报告解读

### 验证状态说明

- **通过**: Properties 和 SQL 数据完全一致，键数量相同，所有值都匹配
- **失败**: 发现数据不一致，可能是：
  - 键数量不同
  - 存在仅在 Properties 或 SQL 中的键
  - 相同键的值不一致
- **错误**: 验证过程出错，例如文件读取失败、数据库连接失败等

### 数据不一致的常见原因

#### 1. 仅在 Properties 中存在的键

**原因**：
- SQL 迁移时丢失了某些数据
- Properties 文件在迁移后继续被修改

**解决方案**：
```bash
# 重新执行数据迁移，确保所有数据都被迁移
# 或手动将缺失的数据添加到 SQL 数据库
```

#### 2. 仅在 SQL 中存在的键

**原因**：
- SQL 数据库在迁移后继续被修改
- Properties 文件已过时

**解决方案**：
```bash
# 如果 SQL 是正确的数据源，可以忽略此差异
# 如果需要保持一致，从 SQL 导出数据到 Properties
```

#### 3. 值不一致

**原因**：
- 数据在迁移过程中被修改
- 类型转换错误
- 编码问题

**解决方案**：
```bash
# 检查数据是否正确
# 确定哪个数据源是正确的
# 手动修正错误的数据
```

## 最佳实践

### 1. 迁移前验证

在执行数据迁移前，先验证当前环境：

```bash
# 确保 Properties 文件存在且完整
ls -la plugins/starcore/starcore/

# 备份 Properties 文件
cp -r plugins/starcore/starcore/ plugins/starcore/starcore.backup/
```

### 2. 迁移后立即验证

```bash
# 执行迁移
# (服务器启动时自动迁移)

# 停止服务器
# 运行验证工具
./scripts/validate-data-consistency.sh \
  --db-url jdbc:mysql://localhost:3306/starcore \
  --db-user root \
  --db-password password
```

### 3. 定期验证

```bash
# 建议每周或每次重大更新后验证一次
# 可以设置为 cron 任务
0 2 * * 0 /path/to/scripts/validate-data-consistency.sh \
  --db-url jdbc:mysql://localhost:3306/starcore \
  --db-user root \
  --db-password password \
  --output /path/to/reports/weekly-validation-$(date +\%Y\%m\%d).md
```

### 4. 保存验证报告

```bash
# 将报告保存到指定目录
mkdir -p validation-reports

./scripts/validate-data-consistency.sh \
  --db-url jdbc:mysql://localhost:3306/starcore \
  --db-user root \
  --db-password password \
  --output validation-reports/report-$(date +%Y%m%d-%H%M%S).md
```

## 故障排查

### 问题 1: JAR 文件不存在

```
[错误] JAR 文件不存在: target/starcore-0.1.0-SNAPSHOT.jar
```

**解决方案**：
```bash
# 编译项目
mvn clean package
```

### 问题 2: 数据库连接失败

```
[错误] 无法连接到数据库
```

**解决方案**：
- 检查数据库 URL 是否正确
- 确认数据库服务正在运行
- 验证用户名和密码
- 检查网络连接和防火墙设置

```bash
# 测试数据库连接
mysql -h localhost -u root -p starcore
```

### 问题 3: 数据目录不存在

```
[错误] 数据目录不存在: ./plugins/starcore
```

**解决方案**：
```bash
# 使用正确的数据目录路径
./scripts/validate-data-consistency.sh \
  --data-dir /correct/path/to/plugins/starcore \
  --db-url jdbc:mysql://localhost:3306/starcore \
  --db-user root \
  --db-password password
```

### 问题 4: 表不存在

```
[错误] Table 'starcore.starcore_nation_state' doesn't exist
```

**解决方案**：
- 确保数据库迁移已执行
- 启动服务器一次以创建表结构
- 或手动运行迁移脚本

## 示例场景

### 场景 1: 首次迁移验证

```bash
# 1. 备份 Properties 文件
cp -r plugins/starcore/starcore/ plugins/starcore/starcore.backup/

# 2. 启动服务器（触发自动迁移）
# 启动 Minecraft 服务器...

# 3. 停止服务器

# 4. 运行验证
./scripts/validate-data-consistency.sh \
  --db-url jdbc:mysql://localhost:3306/starcore \
  --db-user root \
  --db-password password \
  --output migration-validation-$(date +%Y%m%d).md

# 5. 检查报告
cat migration-validation-*.md
```

### 场景 2: 验证特定模块

```bash
# 只验证核心模块
./scripts/validate-data-consistency.sh \
  --db-url jdbc:mysql://localhost:3306/starcore \
  --db-user root \
  --db-password password \
  --modules nation,diplomacy,war
```

### 场景 3: SQLite 数据库验证

```bash
# SQLite 不需要用户名密码
./scripts/validate-data-consistency.sh \
  --db-url jdbc:sqlite:./plugins/starcore/starcore.db \
  --db-user "" \
  --db-password ""
```

## 相关文档

- [数据库迁移指南](../DATABASE_MIGRATION.md)
- [SQL 存储架构](../SQL_STORAGE_ARCHITECTURE.md)
- [故障排查指南](../TROUBLESHOOTING.md)

## 技术细节

### 验证算法

1. **加载数据**
   - 从 Properties 文件加载所有键值对
   - 从 SQL 表加载所有键值对

2. **键集合比对**
   - 计算仅在 Properties 中存在的键
   - 计算仅在 SQL 中存在的键

3. **值比对**
   - 对于共同的键，逐一比对值
   - 使用 `Objects.equals()` 进行精确匹配

4. **生成报告**
   - 汇总所有差异
   - 生成 Markdown 格式报告

### 性能考虑

- 验证工具在内存中加载所有数据
- 对于大型数据集（>100万条记录），可能需要增加 JVM 堆内存：

```bash
java -Xmx2G -cp target/starcore-0.1.0-SNAPSHOT.jar \
  dev.starcore.starcore.core.database.DataConsistencyValidatorCommand \
  --data-dir ./plugins/starcore \
  --db-url jdbc:mysql://localhost:3306/starcore \
  --db-user root \
  --db-password password
```

### 安全注意事项

- 命令行中的密码可能被其他用户看到
- 建议使用环境变量或配置文件存储敏感信息：

```bash
# 使用环境变量
export DB_PASSWORD="your_password"

./scripts/validate-data-consistency.sh \
  --db-url jdbc:mysql://localhost:3306/starcore \
  --db-user root \
  --db-password "$DB_PASSWORD"
```
