# 数据一致性验证工具 - 快速开始

快速开始使用数据一致性验证工具。

## 创建的文件

1. **验证工具核心类**
   - `src/main/java/dev/starcore/starcore/core/database/DataConsistencyValidator.java`
   - 完整的验证逻辑实现

2. **命令行工具**
   - `src/main/java/dev/starcore/starcore/core/database/DataConsistencyValidatorCommand.java`
   - 可独立运行的命令行工具

3. **验证脚本**
   - `scripts/validate-data-consistency.sh` (Linux/macOS)
   - `scripts/validate-data-consistency.bat` (Windows)

4. **使用文档**
   - `docs/DATA_CONSISTENCY_VALIDATION.md`
   - 完整的使用指南和故障排查

5. **单元测试**
   - `src/test/java/dev/starcore/starcore/core/database/DataConsistencyValidatorTest.java`

## 快速使用

### 1. 编译项目

```bash
mvn clean package -DskipTests
```

### 2. 运行验证

#### Linux/macOS

```bash
./scripts/validate-data-consistency.sh \
  --db-url jdbc:mysql://localhost:3306/starcore \
  --db-user root \
  --db-password your_password
```

#### Windows

```cmd
scripts\validate-data-consistency.bat ^
  --db-url jdbc:mysql://localhost:3306/starcore ^
  --db-user root ^
  --db-password your_password
```

#### SQLite 示例

```bash
./scripts/validate-data-consistency.sh \
  --db-url jdbc:sqlite:./plugins/starcore/starcore.db \
  --db-user "" \
  --db-password ""
```

### 3. 查看报告

验证完成后会生成报告文件：`data-consistency-report-<timestamp>.md`

## 工具特性

### 1. 完整的模块支持

验证以下 10 个模块：
- nation (国家)
- diplomacy (外交)
- policy (政策)
- resource (资源)
- technology (科技)
- treasury (国库)
- war (战争)
- officer (官员)
- event (事件)
- resolution (决议)

### 2. 多维度比对

- **键数量比对**: 检查 Properties 和 SQL 的键数量
- **键存在性比对**: 找出仅在一方存在的键
- **值一致性比对**: 逐一比对相同键的值

### 3. 详细报告

生成包含以下内容的 Markdown 报告：
- 总览统计
- 每个模块的详细结果
- 差异明细（仅在 Properties/SQL 中的键、值不一致的键）

### 4. 独立运行

工具可独立运行，无需启动 Minecraft 服务器：
- 直接读取 Properties 文件
- 直接连接数据库
- 生成报告文件

## 命令行参数

| 参数 | 说明 | 必需 | 默认值 |
|------|------|------|--------|
| `--data-dir` | StarCore 数据目录 | 否 | `./plugins/starcore` |
| `--db-url` | 数据库 JDBC URL | 是 | - |
| `--db-user` | 数据库用户名 | 是 | - |
| `--db-password` | 数据库密码 | 是 | - |
| `--modules` | 验证模块列表（逗号分隔） | 否 | 全部 |
| `--output` | 输出报告文件 | 否 | 自动生成 |

## 验证示例

### 验证所有模块

```bash
./scripts/validate-data-consistency.sh \
  --db-url jdbc:mysql://localhost:3306/starcore \
  --db-user root \
  --db-password password
```

### 验证指定模块

```bash
./scripts/validate-data-consistency.sh \
  --db-url jdbc:mysql://localhost:3306/starcore \
  --db-user root \
  --db-password password \
  --modules nation,diplomacy,war
```

### 自定义输出路径

```bash
./scripts/validate-data-consistency.sh \
  --db-url jdbc:mysql://localhost:3306/starcore \
  --db-user root \
  --db-password password \
  --output reports/validation-$(date +%Y%m%d).md
```

## 报告示例

```markdown
# 数据一致性验证报告

**生成时间**: 2026-06-18T08:00:00Z

---

## 总览

| 状态 | 数量 |
|------|------|
| 通过 | 8 |
| 失败 | 2 |
| 错误 | 0 |
| **总计** | **10** |

---

## 详细结果

### nation 模块

**状态**: 通过

- Properties 键数量: 150
- SQL 键数量: 150
- 数据完全一致

---

### diplomacy 模块

**状态**: 失败

- Properties 键数量: 100
- SQL 键数量: 98

#### 仅在 Properties 中存在的键 (2)

- `relation.china.usa.status`
- `relation.china.russia.trade`
```

## 故障排查

### JAR 文件不存在

```bash
# 编译项目
mvn clean package -DskipTests
```

### 数据库连接失败

检查：
1. 数据库 URL 是否正确
2. 用户名密码是否正确
3. 数据库服务是否运行
4. 网络连接是否正常

### 数据目录不存在

使用正确的数据目录路径：

```bash
./scripts/validate-data-consistency.sh \
  --data-dir /correct/path/to/plugins/starcore \
  --db-url jdbc:mysql://localhost:3306/starcore \
  --db-user root \
  --db-password password
```

## 下一步

查看完整文档了解更多：
- [完整使用指南](DATA_CONSISTENCY_VALIDATION.md)
- [报告解读说明](DATA_CONSISTENCY_VALIDATION.md#报告解读)
- [最佳实践](DATA_CONSISTENCY_VALIDATION.md#最佳实践)
